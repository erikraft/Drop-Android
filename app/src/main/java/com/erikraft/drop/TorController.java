package com.erikraft.drop;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManager;
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory;
import org.briarproject.onionwrapper.AndroidTorWrapper;
import org.briarproject.onionwrapper.TorWrapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Android Tor controller used by Onion mode and .onion WebView instances. */
public final class TorController {
    private static final String TAG = "ErikrafT-Tor";
    private static final int PORT_ALLOCATION_ATTEMPTS = 8;
    private static TorController instance;

    private final Application application;
    private final AndroidWakeLockManager wakeLockManager;
    private final ThreadPoolExecutor ioExecutor;
    private final java.util.concurrent.Executor mainExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile AndroidTorWrapper tor;
    private volatile int socksPort;
    private volatile int controlPort;
    private final CallbackGeneration torGeneration = new CallbackGeneration();
    private final CallbackGeneration onionGeneration = new CallbackGeneration();
    private volatile boolean started;
    private volatile boolean connected;
    private final List<Runnable> pendingConnected = new ArrayList<>();
    private volatile OnionCallback pendingOnion;

    private TorController(@NonNull Context context) {
        application = (Application) context.getApplicationContext();
        wakeLockManager = AndroidWakeLockManagerFactory.createAndroidWakeLockManager(application);
        ioExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
        mainExecutor = command -> new Handler(Looper.getMainLooper()).post(command);
        recreateTorWrapper();
    }

    public static synchronized TorController get(@NonNull Context context) {
        if (instance == null) instance = new TorController(context);
        return instance;
    }

    /**
     * Allocate two distinct local ports. The sockets are deliberately held open only during
     * discovery because AndroidTorWrapper owns the actual listeners. Startup therefore retries
     * with a fresh pair if another process wins the small allocation race.
     */
    private static int[] findAvailablePortPair() {
        for (int attempt = 1; attempt <= PORT_ALLOCATION_ATTEMPTS; attempt++) {
            try (ServerSocket socks = new ServerSocket(0); ServerSocket control = new ServerSocket(0)) {
                socks.setReuseAddress(false);
                control.setReuseAddress(false);
                int socksPort = socks.getLocalPort();
                int controlPort = control.getLocalPort();
                if (socksPort > 0 && controlPort > 0 && socksPort != controlPort) {
                    return new int[]{socksPort, controlPort};
                }
            } catch (IOException e) {
                if (attempt == PORT_ALLOCATION_ATTEMPTS) {
                    throw new IllegalStateException("Unable to allocate local ports for Tor", e);
                }
            }
        }
        throw new IllegalStateException("Unable to allocate local ports for Tor");
    }

    private synchronized void recreateTorWrapper() {
        int[] ports = findAvailablePortPair();
        socksPort = ports[0];
        controlPort = ports[1];
        final long generation = torGeneration.next();
        AndroidTorWrapper newTor = new AndroidTorWrapper(application, wakeLockManager, ioExecutor, mainExecutor, architecture(),
                application.getDir("tor", Context.MODE_PRIVATE), socksPort, controlPort);
        newTor.setObserver(new TorWrapper.Observer() {
            private boolean isCurrent() {
                return torGeneration.isCurrent(generation);
            }

            @Override
            public void onState(TorWrapper.TorState state) {
                if (isCurrent()) handleState(state);
            }

            @Override
            public void onBootstrapPercentage(int percentage) {
                if (isCurrent()) handleBootstrapPercentage(percentage);
            }

            @Override
            public void onHsDescriptorUpload(String onion) {
                if (isCurrent()) handleHsDescriptorUpload(onion);
            }

            @Override
            public void onClockSkewDetected(long skewSeconds) {
                if (isCurrent()) handleClockSkewDetected(skewSeconds);
            }
        });
        tor = newTor;
    }

    private static String architecture() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.startsWith("x86_64")) return "x86_64_pie";
            if (abi.startsWith("x86")) return "x86_pie";
            if (abi.startsWith("arm64")) return "arm64_pie";
            if (abi.startsWith("armeabi")) return "arm_pie";
        }
        throw new IllegalStateException("Tor is not supported on this CPU architecture");
    }

    public static boolean isOnionUrl(String url) {
        if (url == null) return false;
        try {
            String host = new java.net.URI(url).getHost();
            return host != null && host.toLowerCase().endsWith(".onion");
        } catch (Exception e) {
            return url.toLowerCase().contains(".onion");
        }
    }

    public static void configureWebViewProxyForUrl(@NonNull Context context, String url, @NonNull Runnable afterProxy) {
        TorController controller = get(context);
        if (!isOnionUrl(url) || !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(controller.mainExecutor, afterProxy);
            } else {
                afterProxy.run();
            }
            return;
        }
        controller.start(() -> {
            ProxyConfig config = new ProxyConfig.Builder()
                    .addProxyRule("socks://127.0.0.1:" + controller.socksPort, ProxyConfig.MATCH_ALL_SCHEMES)
                    .build();
            ProxyController.getInstance().setProxyOverride(config, controller.mainExecutor, afterProxy);
        });
    }

    public synchronized void start(Runnable callback) {
        if (connected) {
            mainExecutor.execute(callback);
            return;
        }
        pendingConnected.add(callback);
        if (started) return;
        started = true;
        executor.execute(this::startTorWithRetry);
    }

    private void startTorWithRetry() {
        Exception lastError = null;
        for (int attempt = 1; attempt <= PORT_ALLOCATION_ATTEMPTS; attempt++) {
            try {
                AndroidTorWrapper currentTor = tor;
                currentTor.start();
                currentTor.enableNetwork(true);
                return;
            } catch (Exception e) {
                lastError = e;
                Log.w(TAG, "Tor start failed on attempt " + attempt + "/" + PORT_ALLOCATION_ATTEMPTS + "; reallocating ports", e);
                try {
                    AndroidTorWrapper currentTor = tor;
                    try { currentTor.stop(); } catch (Exception ignored) { }
                    recreateTorWrapper();
                } catch (Exception allocationError) {
                    lastError = allocationError;
                    break;
                }
            }
        }
        started = false;
        connected = false;
        if (lastError == null) lastError = new IllegalStateException("Unable to start Tor");
        failPending(lastError);
    }

    private synchronized void failPending(final Exception error) {
        pendingConnected.clear();
        onionGeneration.next();
        final long failureGeneration = onionGeneration.current();
        final OnionCallback callback = pendingOnion;
        pendingOnion = null;
        if (callback != null) mainExecutor.execute(() -> {
            if (onionGeneration.isCurrent(failureGeneration)) callback.onError(error);
        });
    }

    public synchronized void publishHiddenService(int localPort, OnionCallback callback) {
        final long requestGeneration = onionGeneration.next();
        pendingOnion = callback;
        callback.onProgress(0);
        start(() -> executor.execute(() -> {
            try {
                if (!onionGeneration.isCurrent(requestGeneration)) return;
                callback.onProgress(80);
                final AndroidTorWrapper currentTor = tor;
                currentTor.publishHiddenService(localPort, 80, null);
            } catch (Exception e) {
                if (!onionGeneration.isCurrent(requestGeneration)) return;
                synchronized (TorController.this) {
                    if (onionGeneration.isCurrent(requestGeneration)) pendingOnion = null;
                }
                Log.e(TAG, "Unable to publish Onion service", e);
                mainExecutor.execute(() -> {
                    if (onionGeneration.isCurrent(requestGeneration)) callback.onError(e);
                });
            }
        }));
    }

    private synchronized void handleState(TorWrapper.TorState state) {
        if (state == TorWrapper.TorState.CONNECTED) {
            connected = true;
            final List<Runnable> callbacks = new ArrayList<>(pendingConnected);
            pendingConnected.clear();
            for (Runnable callback : callbacks) mainExecutor.execute(callback);
        } else if (state == TorWrapper.TorState.STOPPED) {
            connected = false;
            started = false;
        }
    }

    private void handleBootstrapPercentage(int percentage) {
        final long requestGeneration = onionGeneration.current();
        final OnionCallback callback = pendingOnion;
        if (callback != null) mainExecutor.execute(() -> {
            if (onionGeneration.isCurrent(requestGeneration)) callback.onProgress(Math.max(0, Math.min(100, percentage)));
        });
    }

    private void handleHsDescriptorUpload(String onion) {
        final long requestGeneration = onionGeneration.current();
        OnionCallback cb = pendingOnion;
        pendingOnion = null;
        if (cb != null) mainExecutor.execute(() -> {
            if (!onionGeneration.isCurrent(requestGeneration)) return;
            cb.onProgress(100);
            cb.onReady(onion);
        });
    }

    private void handleClockSkewDetected(long skewSeconds) { }

    public static synchronized void shutdown(@NonNull Context context) {
        if (instance == null) return;
        instance.torGeneration.next();
        instance.onionGeneration.next();
        instance.pendingConnected.clear();
        instance.pendingOnion = null;
        try { instance.tor.stop(); } catch (Exception ignored) { }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(instance.mainExecutor, () -> { });
        }
        instance.executor.shutdownNow();
        instance.ioExecutor.shutdownNow();
        instance = null;
    }

    public interface OnionCallback {
        void onReady(String onionAddress);
        void onError(Exception error);
        default void onProgress(int percentage) { }
    }
}
