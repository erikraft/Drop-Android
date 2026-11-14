package com.erikraft.drop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewCompat;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies that a cross-origin POST document does not receive the scoped bridge bootstrap. */
@RunWith(AndroidJUnit4.class)
public class WebViewBridgeIsolationTest {
    private static final String TOKEN = "trusted-origin-test-token";

    @Test
    public void externalPostDocumentCannotAccessNativeBridge() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final ServerSocket trustedServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final ServerSocket externalServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final ExecutorService servers = Executors.newFixedThreadPool(2);
        final CountDownLatch externalRequestReceived = new CountDownLatch(1);
        final CountDownLatch externalPageFinished = new CountDownLatch(1);
        final AtomicReference<String> postedBody = new AtomicReference<>("");
        final AtomicReference<String> externalBridgeState = new AtomicReference<>("");
        final WebView[] webView = new WebView[1];

        final int trustedPort = trustedServer.getLocalPort();
        final int externalPort = externalServer.getLocalPort();
        final String trustedOrigin = "http://127.0.0.1:" + trustedPort;
        final String externalOrigin = "http://127.0.0.1:" + externalPort;
        final String trustedPage = "<!doctype html><html><body>"
                + "<form method=\"POST\" action=\"" + externalOrigin + "/submit\">"
                + "<input name=\"trustedBridge\" value=\"\"></form>"
                + "<script>document.forms[0].elements.trustedBridge.value="
                + "typeof window.ErikrafTdropAndroid;document.forms[0].submit();</script>"
                + "</body></html>";
        final String externalPage = "<!doctype html><html><body><script>"
                + "document.title=typeof window.ErikrafTdropAndroid+'|'"
                + "+window.__ErikrafTdropAndroidNative.invoke('attacker','probe','[]');"
                + "</script>external</body></html>";

        servers.execute(() -> serveOne(trustedServer, trustedPage, null, null));
        servers.execute(() -> serveOne(externalServer, externalPage, postedBody, externalRequestReceived));

        final String bootstrap;
        try (InputStream input = context.getAssets().open("bridge-bootstrap.js")) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            bootstrap = new String(output.toByteArray(), StandardCharsets.UTF_8)
                    .replace("__ERIKRAFT_BRIDGE_TOKEN__", JSONObject.quote(TOKEN));
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final WebView view = new WebView(context);
            webView[0] = view;
            view.getSettings().setJavaScriptEnabled(true);
            view.addJavascriptInterface(new ProbeBridge(), "__ErikrafTdropAndroidNative");
            WebViewCompat.addDocumentStartJavaScript(
                    view,
                    bootstrap,
                    Collections.singleton(trustedOrigin)
            );
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(final WebView currentView, final String url) {
                    if (url.startsWith(externalOrigin)) {
                        currentView.evaluateJavascript("document.title", value -> {
                            externalBridgeState.set(value);
                            externalPageFinished.countDown();
                        });
                    }
                }
            });
            view.loadUrl(trustedOrigin + "/start");
        });

        try {
            assertTrue("Trusted form did not POST to the external origin",
                    externalRequestReceived.await(10, TimeUnit.SECONDS));
            assertTrue("External POST response did not finish loading",
                    externalPageFinished.await(10, TimeUnit.SECONDS));
            assertTrue("Trusted origin did not receive its bridge bootstrap",
                    postedBody.get().contains("trustedBridge=object"));
            assertEquals("\"undefined|\\\"denied\\\"", externalBridgeState.get());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                if (webView[0] != null) {
                    webView[0].stopLoading();
                    webView[0].removeJavascriptInterface("__ErikrafTdropAndroidNative");
                    webView[0].destroy();
                }
            });
            trustedServer.close();
            externalServer.close();
            servers.shutdownNow();
        }
    }

    private static void serveOne(
            final ServerSocket server,
            final String html,
            final AtomicReference<String> requestBody,
            final CountDownLatch requestReceived
    ) {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(5000);
            final InputStream input = socket.getInputStream();
            final String headers = readHeaders(input);
            final int contentLength = parseContentLength(headers);
            final byte[] body = readBody(input, contentLength);
            if (requestBody != null) requestBody.set(new String(body, StandardCharsets.UTF_8));
            if (requestReceived != null) requestReceived.countDown();

            final byte[] responseBody = html.getBytes(StandardCharsets.UTF_8);
            final OutputStream output = socket.getOutputStream();
            final String responseHeaders = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + responseBody.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(responseHeaders.getBytes(StandardCharsets.US_ASCII));
            output.write(responseBody);
            output.flush();
        } catch (IOException e) {
            throw new AssertionError("Local WebView test server failed", e);
        }
    }

    private static String readHeaders(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = 0;
        int current;
        while ((current = input.read()) != -1) {
            output.write(current);
            if (previous == '\r' && current == '\n') {
                final byte[] bytes = output.toByteArray();
                final int length = bytes.length;
                if (length >= 4 && bytes[length - 4] == '\r' && bytes[length - 3] == '\n') {
                    return new String(bytes, StandardCharsets.US_ASCII);
                }
            }
            previous = current;
        }
        throw new IOException("Incomplete HTTP request headers");
    }

    private static int parseContentLength(final String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        return 0;
    }

    private static byte[] readBody(final InputStream input, final int length) throws IOException {
        final byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            final int read = input.read(body, offset, length - offset);
            if (read < 0) throw new IOException("Incomplete HTTP request body");
            offset += read;
        }
        return body;
    }

    private static final class ProbeBridge {
        @JavascriptInterface
        public String invoke(final String token, final String method, final String argsJson) {
            return TOKEN.equals(token) && "probe".equals(method) ? "\"granted\"" : "\"denied\"";
        }
    }
}
