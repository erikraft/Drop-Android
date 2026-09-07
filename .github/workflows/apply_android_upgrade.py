from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

# Build/version
build = ROOT / 'app/build.gradle'
s = build.read_text(encoding='utf-8')
s = s.replace('versionCode 22', 'versionCode 23', 1).replace('versionName "10.0.5"', 'versionName "10.1.0"', 1)
for dep in [
    "    implementation 'org.briarproject:onionwrapper-android:0.1.4'",
    "    implementation 'org.briarproject:tor-android:0.4.9.11'",
    "    implementation 'org.briarproject:dont-kill-me-lib:0.2.8'",
    "    implementation 'org.nanohttpd:nanohttpd:2.3.1'",
]:
    if dep not in s:
        s = s.replace("    implementation 'com.google.zxing:core:3.5.3'", "    implementation 'com.google.zxing:core:3.5.3'\n" + dep)
# AABs must extract Tor native libraries.
if 'useLegacyPackaging = true' not in s:
    s = s.replace('    lint {', '    packagingOptions {\n        jniLibs {\n            useLegacyPackaging = true\n        }\n    }\n\n    lint {')
build.write_text(s, encoding='utf-8')

# Android manifest: network is already present in the project; add activity and keep camera permission explicit.
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
m = manifest.read_text(encoding='utf-8')
if 'android.permission.CAMERA' not in m:
    m = m.replace('<uses-permission android:name="android.permission.INTERNET" />', '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.CAMERA" />')
if 'OnionTransferActivity' not in m:
    m = m.replace('</application>', '        <activity android:name=".OnionTransferActivity" android:exported="false" />\n    </application>')
manifest.write_text(m, encoding='utf-8')

# Remove the Android-native QR transfer action and add an Onion action.
menu = ROOT / 'app/src/main/res/menu/actionbar.xml'
menu.write_text('''<?xml version="1.0" encoding="utf-8"?>\n<menu xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto">\n    <item\n        android:id="@+id/menu_onion_transfer"\n        android:icon="@drawable/pref_about"\n        android:title="@string/onion_transfer_title"\n        app:showAsAction="ifRoom" />\n    <item\n        android:id="@+id/menu_view_downloads"\n        android:icon="@drawable/ic_download"\n        android:title="@string/view_downloads"\n        app:showAsAction="always" />\n    <item\n        android:id="@+id/menu_settings"\n        android:icon="@drawable/ic_settings"\n        android:title="@string/pref_category_settings"\n        app:showAsAction="ifRoom" />\n</menu>\n''', encoding='utf-8')

strings = ROOT / 'app/src/main/res/values/strings.xml'
st = strings.read_text(encoding='utf-8')
if 'name="onion_transfer_title"' not in st:
    st = st.replace('</resources>', '    <string name="onion_transfer_title">Transferência via Onion (Beta)</string>\n    <string name="onion_transfer_select_files">Selecionar arquivos</string>\n    <string name="onion_transfer_start">Iniciar Onion Service</string>\n    <string name="onion_transfer_stop">Parar</string>\n    <string name="onion_transfer_waiting">Iniciando Tor e publicando Onion Service…</string>\n    <string name="onion_transfer_ready">Compartilhe este endereço Onion:</string>\n    <string name="onion_transfer_copy">Copiar link</string>\n    <string name="onion_transfer_share">Compartilhar</string>\n    <string name="onion_transfer_no_files">Selecione pelo menos um arquivo.</string>\n</resources>')
strings.write_text(st, encoding='utf-8')

# MainActivity: light theme must explicitly disable WebView forced dark mode; onion URLs configure the Tor SOCKS proxy before loading.
main = ROOT / 'app/src/main/java/com/erikraft/drop/MainActivity.java'
ms = main.read_text(encoding='utf-8')
ms = ms.replace('WebSettingsCompat.setForceDark(binding.webview.getSettings(), WebSettingsCompat.FORCE_DARK_ON);', 'WebSettingsCompat.setForceDark(binding.webview.getSettings(), SnapdropApplication.isDarkTheme(this) ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);')
ms = ms.replace('} else if (item.getItemId() == R.id.menu_qr_transfer) {\n            startActivity(new Intent(this, com.erikraft.drop.qr.QRTransferActivity.class));\n            return true;\n', '} else if (item.getItemId() == R.id.menu_onion_transfer) {\n            startActivity(new Intent(this, OnionTransferActivity.class));\n            return true;\n')
start = ms.find('    private void refreshWebsite(final boolean pulled) {')
end = ms.find('    private void refreshWebsite() {', start)
if start < 0 or end < 0:
    raise SystemExit('refreshWebsite method not found')
new_method = '''    private void refreshWebsite(final boolean pulled) {\n        Log.w("ErikrafTdropAndroid", "refresh triggered");\n        if (NetworkUtils.isInternetAvailable() && !transfer.get() && !dialogVisible || forceRefresh) {\n            final Runnable load = () -> {\n                binding.connectivityCard.setVisibility(NetworkUtils.isWifiAvailable() ? View.GONE : View.VISIBLE);\n                binding.webview.loadUrl(baseURL);\n                forceRefresh = false;\n                binding.webview.animate().alpha(0).start();\n            };\n            TorController.configureWebViewProxyForUrl(this, baseURL, load);\n        } else if (transfer.get() || dialogVisible) {\n            binding.pullToRefresh.setRefreshing(false);\n            forceRefresh = pulled;\n        } else {\n            binding.pullToRefresh.setRefreshing(false);\n            state.setCurrentlyLoading(false);\n            showScreenNoConnection(true);\n        }\n    }\n\n'''
ms = ms[:start] + new_method + ms[end:]
# Stop Tor/proxy when the main activity is destroyed.
if 'TorController.shutdown(this);' not in ms:
    ms = ms.replace('        CookieManager.getInstance().flush();\n        super.onDestroy();', '        CookieManager.getInstance().flush();\n        TorController.shutdown(this);\n        super.onDestroy();')
main.write_text(ms, encoding='utf-8')

# Android WebView bridge tweaks: hide duplicate About, request camera from QR controls,
# replace the scanner icon with the requested Material-style SVG, and do not erase native theme state.
init = ROOT / 'app/src/main/assets/init.js'
js = init.read_text(encoding='utf-8')
if 'erikraftHideNativeAbout' not in js:
    js += r'''

// Android WebView: About is exposed by the native action bar, so keep only one entry point.
try {
    if (!window.__erikraftHideNativeAbout) {
        window.__erikraftHideNativeAbout = true;
        const aboutButton = document.querySelector('body > header a[href="#about"]');
        if (aboutButton) aboutButton.style.display = 'none';
    }
} catch (e) { console.error(e); }

// QR controls: ensure a user gesture reaches getUserMedia so the native WebView camera permission dialog is shown.
try {
    const askCamera = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
            stream.getTracks().forEach(track => track.stop());
        } catch (e) { console.warn('Camera permission/request was not granted', e); }
    };
    ['#openQRScanner', '#animated-qr-btn'].forEach(selector => {
        const node = document.querySelector(selector);
        if (node && !node.dataset.erikraftCameraPermissionHooked) {
            node.dataset.erikraftCameraPermissionHooked = 'true';
            node.addEventListener('click', () => { askCamera(); }, { capture: true });
        }
    });
} catch (e) { console.error(e); }

// Replace the QR scanner icon with the requested Material icon path while preserving the existing button behavior.
try {
    const scanner = document.querySelector('#openQRScanner svg');
    if (scanner) {
        scanner.setAttribute('viewBox', '0 -960 960 960');
        scanner.setAttribute('width', '24');
        scanner.setAttribute('height', '24');
        scanner.innerHTML = '<path d="M120-680q-17 0-28.5-11.5T80-720v-120q0-17 11.5-28.5T120-880h120q17 0 28.5 11.5T280-840q0 17-11.5 28.5T240-800h-80v80q0 17-11.5 28.5T120-680Zm0 600q-17 0-28.5-11.5T80-120v-120q0-17 11.5-28.5T120-280q17 0 28.5 11.5T160-240v80h80q17 0 28.5 11.5T280-120q0 17-11.5 28.5T240-80H120Zm600 0q-17 0-28.5-11.5T680-120q0-17 11.5-28.5T720-160h80v-80q0-17 11.5-28.5T840-280q17 0 28.5 11.5T880-240v120q0 17-11.5 28.5T840-80H720Zm91.5-611.5Q800-703 800-720v-80h-80q-17 0-28.5-11.5T680-840q0-17 11.5-28.5T720-880h120q17 0 28.5 11.5T880-840v120q0 17-11.5 28.5T840-680q-17 0-28.5-11.5ZM700-200v-60h60v60h-60Zm0-120v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60-60v-60h60v60h-60Zm120-120v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60-60v-60h60v60h-60Zm40-140q-17 0-28.5-11.5T520-560v-160q0-17 11.5-28.5T560-760h160q17 0 28.5 11.5T760-720v160q0 17-11.5 28.5T720-520H560ZM240-200q-17 0-28.5-11.5T200-240v-160q0-17 11.5-28.5T240-440h160q17 0 28.5 11.5T440-400v160q0 17-11.5 28.5T400-200H240Zm0-320q-17 0-28.5-11.5T200-560v-160q0-17 11.5-28.5T240-760h160q17 0 28.5 11.5T440-720v160q0 17-11.5 28.5T400-520H240Zm20 260h120v-120H260v120Zm0-320h120v-120H260v120Zm320 0h120v-120H580v120Z" fill="currentColor"/>';
    }
} catch (e) { console.error(e); }

// Do not wipe the native light/dark selection. MainActivity controls WebView force-dark explicitly.
try {
    localStorage.removeItem('theme');
} catch (e) { console.error(e); }
'''
init.write_text(js, encoding='utf-8')

# Onion/Tor controller.
tor = ROOT / 'app/src/main/java/com/erikraft/drop/TorController.java'
tor.write_text(r'''package com.erikraft.drop;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManager;
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManagerFactory;
import org.briarproject.onionwrapper.AndroidTorWrapper;
import org.briarproject.onionwrapper.TorWrapper;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small Android-only Tor controller used by Onion mode and .onion WebView instances. */
public final class TorController implements TorWrapper.Observer {
    private static final int SOCKS_PORT = 53054;
    private static final int CONTROL_PORT = 53055;
    private static TorController instance;

    private final Application app;
    private final AndroidTorWrapper tor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Executor mainExecutor;
    private volatile boolean started;
    private volatile boolean connected;
    private volatile Runnable pendingConnected;
    private volatile OnionCallback pendingOnion;

    private TorController(@NonNull Application app) {
        this.app = app;
        AndroidWakeLockManager wakeLockManager = AndroidWakeLockManagerFactory.createAndroidWakeLockManager(app);
        ThreadPoolExecutor ioExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
        mainExecutor = app.getMainExecutor();
        tor = new AndroidTorWrapper(app, wakeLockManager, ioExecutor, mainExecutor, architecture(), app.getDir("tor", Context.MODE_PRIVATE), SOCKS_PORT, CONTROL_PORT);
        tor.setObserver(this);
    }

    public static synchronized TorController get(@NonNull Context context) {
        if (instance == null) instance = new TorController(context.getApplicationContext());
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
        try { return new java.net.URI(url).getHost() != null && new java.net.URI(url).getHost().toLowerCase().endsWith(".onion"); }
        catch (Exception e) { return url.toLowerCase().contains(".onion"); }
    }

    public static void configureWebViewProxyForUrl(@NonNull Context context, String url, @NonNull Runnable afterProxy) {
        if (!isOnionUrl(url) || !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride(context.getMainExecutor(), afterProxy);
            } else {
                afterProxy.run();
            }
            return;
        }
        get(context).start(() -> {
            ProxyConfig config = new ProxyConfig.Builder()
                    .addProxyRule("socks://127.0.0.1:" + SOCKS_PORT, ProxyConfig.MATCH_ALL_SCHEMES)
                    .build();
            ProxyController.getInstance().setProxyOverride(config, context.getMainExecutor(), afterProxy);
        });
    }

    public void start(Runnable callback) {
        if (connected) { mainExecutor.execute(callback); return; }
        pendingConnected = callback;
        if (started) return;
        started = true;
        executor.execute(() -> {
            try {
                tor.start();
                tor.enableNetwork(true);
            } catch (Exception e) {
                started = false;
                android.util.Log.e("ErikrafT-Tor", "Unable to start Tor", e);
            }
        });
    }

    public void publishHiddenService(int localPort, OnionCallback callback) {
        pendingOnion = callback;
        start(() -> executor.execute(() -> {
            try { tor.publishHiddenService(localPort, 80, null); }
            catch (Exception e) { mainExecutor.execute(() -> callback.onError(e)); }
        }));
    }

    @Override public void onState(TorWrapper.TorState state) {
        if (state == TorWrapper.TorState.CONNECTED) {
            connected = true;
            Runnable cb = pendingConnected; pendingConnected = null;
            if (cb != null) mainExecutor.execute(cb);
        } else if (state == TorWrapper.TorState.STOPPED) {
            connected = false; started = false;
        }
    }

    @Override public void onBootstrapPercentage(int percentage) { }

    @Override public void onHsDescriptorUpload(String onion) {
        OnionCallback cb = pendingOnion; pendingOnion = null;
        if (cb != null) mainExecutor.execute(() -> cb.onReady(onion));
    }

    @Override public void onClockSkewDetected(long skewSeconds) { }

    public static synchronized void shutdown(@NonNull Context context) {
        if (instance == null) return;
        try { instance.tor.stop(); } catch (Exception ignored) { }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(context.getMainExecutor(), () -> { });
        }
        instance.executor.shutdownNow();
        instance = null;
    }

    public interface OnionCallback {
        void onReady(String onionAddress);
        void onError(Exception error);
    }
}
''', encoding='utf-8')

# Onion transfer activity: local HTTP server + embedded Tor Onion Service + copy/share/QR.
onion = ROOT / 'app/src/main/java/com/erikraft/drop/OnionTransferActivity.java'
onion.write_text(r'''package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.nanohttpd.protocols.http.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Beta Android-only Onion Share mode. Files stay on-device; Tor exposes the local HTTP server. */
public class OnionTransferActivity extends AppCompatActivity {
    private final List<File> files = new ArrayList<>();
    private ActivityResultLauncher<Intent> picker;
    private OnionHttpServer server;
    private TextView status;
    private TextView address;
    private ImageView qr;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Transferência via Onion (Beta)");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 28, 28, 28);
        TextView title = new TextView(this); title.setText("Transferência via Onion"); title.setTextSize(24); root.addView(title);
        TextView help = new TextView(this); help.setText("O Android inicia Tor, cria um Onion Service temporário e compartilha um endereço .onion. O conteúdo não passa pelo Render/Vercel."); root.addView(help);
        Button pick = new Button(this); pick.setText("Selecionar arquivos"); root.addView(pick);
        status = new TextView(this); status.setText("Nenhum arquivo selecionado."); root.addView(status);
        Button start = new Button(this); start.setText("Iniciar Onion Service"); root.addView(start);
        address = new TextView(this); address.setTextIsSelectable(true); address.setTextSize(16); root.addView(address);
        qr = new ImageView(this); root.addView(qr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420));
        Button copy = new Button(this); copy.setText("Copiar link"); root.addView(copy);
        Button share = new Button(this); share.setText("Compartilhar"); root.addView(share);
        setContentView(root);

        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            files.clear();
            android.content.Intent data = result.getData();
            ClipData clip = data.getClipData();
            try {
                if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) files.add(copyToCache(clip.getItemAt(i).getUri()));
                else if (data.getData() != null) files.add(copyToCache(data.getData()));
                status.setText(files.size() + " arquivo(s) pronto(s) para compartilhar.");
            } catch (IOException e) { Toast.makeText(this, "Falha ao preparar arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        pick.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); i.addCategory(Intent.CATEGORY_OPENABLE); picker.launch(i); });
        start.setOnClickListener(v -> startOnion());
        copy.setOnClickListener(v -> { if (address.getText().length() > 0) { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop Onion", address.getText())); Toast.makeText(this, "Link copiado.", Toast.LENGTH_SHORT).show(); } });
        share.setOnClickListener(v -> { if (address.getText().length() > 0) { Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, address.getText().toString()); startActivity(Intent.createChooser(i, "Compartilhar Onion Service")); } });
    }

    private File copyToCache(Uri uri) throws IOException {
        String name = "file-" + System.currentTimeMillis();
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) { int n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && n >= 0) name = c.getString(n); c.close(); }
        File out = new File(getCacheDir(), name.replaceAll("[^a-zA-Z0-9._-]", "_"));
        try (InputStream in = getContentResolver().openInputStream(uri); java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            if (in == null) throw new IOException("Arquivo não pôde ser aberto");
            byte[] buf = new byte[64 * 1024]; int len; while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
        }
        return out;
    }

    private void startOnion() {
        if (files.isEmpty()) { Toast.makeText(this, "Selecione pelo menos um arquivo.", Toast.LENGTH_LONG).show(); return; }
        try {
            if (server != null) server.stop();
            server = new OnionHttpServer(0, files); server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            status.setText("Servidor local iniciado na porta " + server.getListeningPort() + ". Iniciando Tor…");
            TorController.get(this).publishHiddenService(server.getListeningPort(), new TorController.OnionCallback() {
                @Override public void onReady(String onion) {
                    String link = "http://" + onion + "/"; address.setText(link); status.setText("Onion Service ativo. Compartilhe o link, QR Code ou código."); showQr(link);
                }
                @Override public void onError(Exception error) { status.setText("Falha ao iniciar Onion Service: " + error.getMessage()); }
            });
        } catch (IOException e) { status.setText("Falha no servidor local: " + e.getMessage()); }
    }

    private void showQr(String value) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < 640; x++) for (int y = 0; y < 640; y++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            qr.setImageBitmap(bitmap);
        } catch (Exception e) { Toast.makeText(this, "Não foi possível gerar o QR Code.", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }

    private static final class OnionHttpServer extends NanoHTTPD {
        private final List<File> files;
        OnionHttpServer(int port, List<File> files) { super(port); this.files = new ArrayList<>(files); }
        @Override public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/".equals(uri)) {
                StringBuilder html = new StringBuilder("<html><meta name='viewport' content='width=device-width'><body><h1>ErikrafT Drop™ Onion</h1><p>Arquivos disponíveis:</p><ul>");
                for (int i = 0; i < files.size(); i++) { File f = files.get(i); html.append("<li><a download href='/file/").append(i).append("'>").append(escape(f.getName())).append("</a> (" ).append(f.length()).append(" bytes)</li>"); }
                html.append("</ul></body></html>");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html.toString());
            }
            if (uri.startsWith("/file/")) {
                try {
                    int index = Integer.parseInt(uri.substring(6)); File f = files.get(index);
                    return newChunkedResponse(Response.Status.OK, mime(f.getName()), new FileInputStream(f));
                } catch (Exception e) { return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found"); }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        }
        private static String mime(String name) { String n = name.toLowerCase(); if (n.endsWith(".png")) return "image/png"; if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg"; if (n.endsWith(".pdf")) return "application/pdf"; return "application/octet-stream"; }
        private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    }
}
''', encoding='utf-8')

# Accept .onion in official/custom server selection; the WebView proxy is configured by MainActivity.
onboard = ROOT / 'app/src/main/java/com/erikraft/drop/OnboardingFragment2.java'
os = onboard.read_text(encoding='utf-8')
old = '''            } else if (url.startsWith("http")) {\n                NetworkUtils.checkInstance(this, url, result -> {\n                    if (result) {\n                        newServer(url);\n                    }\n                });\n            } else {'''
new = '''            } else if (url.toLowerCase().contains(".onion")) {\n                String onionUrl = url.contains("://") ? url : "http://" + url;\n                if (!onionUrl.endsWith("/")) onionUrl += "/";\n                newServer(onionUrl);\n            } else if (url.startsWith("http")) {\n                NetworkUtils.checkInstance(this, url, result -> {\n                    if (result) {\n                        newServer(url);\n                    }\n                });\n            } else {'''
os = os.replace(old, new)
os = os.replace('servers.add(new ServerItem("https://pairdrop.net/", getString(R.string.onboarding_server_quaternary_summary), null));', 'servers.add(new ServerItem("http://nozudb2e4jy4betognmnwoxvdu44wvjoqvmwios5ql7mxagqqpnn64ad.onion/", "ErikrafT Drop™ Onion Service (Beta)", "Requer Tor. O PairDrop fica como quinto servidor."));\n        servers.add(new ServerItem("https://pairdrop.net/", getString(R.string.onboarding_server_quaternary_summary), null));')
# Do not discard the onion official server as a custom duplicate.
os = os.replace('&& !url.equals("https://pairdrop.net/")', '&& !url.equals("http://nozudb2e4jy4betognmnwoxvdu44wvjoqvmwios5ql7mxagqqpnn64ad.onion/")\n                    && !url.equals("https://pairdrop.net/")')
onboard.write_text(os, encoding='utf-8')
''