package com.erikraft.drop;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Restricts WebView navigation to the origin selected for the Drop server. */
final class WebViewOriginPolicy {
    private WebViewOriginPolicy() { }

    static boolean isAllowed(String baseUrl, String targetUrl) {
        if (baseUrl == null || targetUrl == null) return false;
        try {
            URI base = new URI(baseUrl);
            URI target = new URI(targetUrl);
            String baseScheme = normalizeScheme(base.getScheme());
            String targetScheme = normalizeScheme(target.getScheme());
            String baseHost = normalizeHost(base.getHost());
            String targetHost = normalizeHost(target.getHost());

            if (baseScheme == null || targetScheme == null
                    || baseHost == null || targetHost == null
                    || base.getUserInfo() != null || target.getUserInfo() != null) {
                return false;
            }

            return baseScheme.equals(targetScheme)
                    && baseHost.equals(targetHost)
                    && effectivePort(base) == effectivePort(target);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    static String getOriginRule(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String scheme = normalizeScheme(uri.getScheme());
            String host = normalizeHost(uri.getHost());
            if (scheme == null || host == null || uri.getUserInfo() != null) return null;

            int port = uri.getPort();
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            String formattedHost = host.indexOf(':') >= 0 && !host.startsWith("[")
                    ? "[" + host + "]" : host;
            return scheme + "://" + formattedHost
                    + (port >= 0 && port != defaultPort ? ":" + port : "");
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeScheme(String scheme) {
        if (scheme == null) return null;
        String normalized = scheme.toLowerCase(Locale.ROOT);
        return "http".equals(normalized) || "https".equals(normalized) ? normalized : null;
    }

    private static String normalizeHost(String host) {
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
