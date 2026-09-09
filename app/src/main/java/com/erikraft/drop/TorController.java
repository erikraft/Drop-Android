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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Android Tor controller used by Onion mode and .onion WebView instances. */
public final class TorController implements TorWrapper.Observer {
    private static final int SOCKS_PORT = 53054;
    private static final int CONTROL_PORT = 53055;
    private static TorController instance;

    private final Application application;
    private final AndroidTorWrapper tor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.Executor mainExecutor;
    private volatile boolean started;
    private volatile boolean connected;
    private volatile Runnable pendingConnected;
    private volatile OnionCallback pendingOnion;

    private TorController(@NonNull Context context) {
        application = (Application) context.getApplicationContext();
        AndroidWakeLockManager wakeLockManager = AndroidWakeLockManagerFactory.createAndroidWakeLockManager(application);
        ThreadPoolExecutor ioExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
        mainExecutor = command -> new Handler(Looper.getMainLooper()).post(command);
        tor = new AndroidTorWrapper(application, wakeLockManager, ioExecutor, mainExecutor, architecture(),
                application.getDir("tor", Context.MODE_PRIVATE), SOCKS_PORT, CONTROL_PORT);
        tor.setObserver(this);
    }

    public static synchronized TorController get(@NonNull Context context) {
        if (instance == null) instance = new TorController(context);
        return instance;
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
                    .addProxyRule("socks://127.0.0.1:" + SOCKS_PORT, ProxyConfig.MATCH_ALL_SCHEMES)
                    .build();
            ProxyController.getInstance().setProxyOverride(config, controller.mainExecutor, afterProxy);
        });
    }

    public void start(Runnable callback) {
        if (connected) {
            mainExecutor.execute(callback);
            return;
        }
        pendingConnected = callback;
        if (started) return;
        started = true;
        executor.execute(() -> {
            try {
                tor.start();
                tor.enableNetwork(true);
            } catch (Exception e) {
                started = false;
                connected = false;
                Log.e("ErikrafT-Tor", "Unable to start Tor", e);
                failPending(e);
            }
        });
    }

    private void failPending(final Exception error) {
        pendingConnected = null;
        final OnionCallback callback = pendingOnion;
        pendingOnion = null;
        if (callback != null) mainExecutor.execute(() -> callback.onError(error));
    }

    public void publishHiddenService(int localPort, OnionCallback callback) {
        pendingOnion = callback;
        callback.onProgress(0);
        start(() -> executor.execute(() -> {
            try {
                callback.onProgress(80);
                tor.publishHiddenService(localPort, 80, null);
            } catch (Exception e) {
                pendingOnion = null;
                mainExecutor.execute(() -> callback.onError(e));
            }
        }));
    }

    @Override
    public void onState(TorWrapper.TorState state) {
        if (state == TorWrapper.TorState.CONNECTED) {
            connected = true;
            Runnable cb = pendingConnected;
            pendingConnected = null;
            if (cb != null) mainExecutor.execute(cb);
        } else if (state == TorWrapper.TorState.STOPPED) {
            connected = false;
            started = false;
        }
    }

    @Override
    public void onBootstrapPercentage(int percentage) {
        final OnionCallback callback = pendingOnion;
        if (callback != null) mainExecutor.execute(() -> callback.onProgress(Math.max(0, Math.min(100, percentage))));
    }

    @Override
    public void onHsDescriptorUpload(String onion) {
        OnionCallback cb = pendingOnion;
        pendingOnion = null;
        if (cb != null) mainExecutor.execute(() -> {
            cb.onProgress(100);
            cb.onReady(onion);
        });
    }

    @Override public void onClockSkewDetected(long skewSeconds) { }

    public static synchronized void shutdown(@NonNull Context context) {
        if (instance == null) return;
        try { instance.tor.stop(); } catch (Exception ignored) { }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(instance.mainExecutor, () -> { });
        }
        instance.executor.shutdownNow();
        instance = null;
    }

    public interface OnionCallback {
        void onReady(String onionAddress);
        void onError(Exception error);
        default void onProgress(int percentage) { }
    }
}
