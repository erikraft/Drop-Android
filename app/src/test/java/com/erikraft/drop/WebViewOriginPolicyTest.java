package com.erikraft.drop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewOriginPolicyTest {
    @Test
    public void allowsSameOriginForHttpsSelfHostedAndOnionServers() {
        assertTrue(WebViewOriginPolicy.isAllowed("https://drop.example/path/", "https://drop.example/transfer"));
        assertTrue(WebViewOriginPolicy.isAllowed("http://192.168.1.20:3000/", "http://192.168.1.20:3000/server"));
        assertTrue(WebViewOriginPolicy.isAllowed("http://exampleonion.onion/", "http://exampleonion.onion/room"));
    }

    @Test
    public void rejectsDifferentHostSchemeOrPort() {
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "https://evil.example/"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "http://drop.example/"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "https://drop.example:8443/"));
        assertFalse(WebViewOriginPolicy.isAllowed("http://192.168.1.20:3000/", "http://192.168.1.21:3000/"));
    }

    @Test
    public void rejectsNonWebAndMalformedUrls() {
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "javascript:alert(1)"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "file:///etc/passwd"));
        assertFalse(WebViewOriginPolicy.isAllowed("not a url", "https://drop.example/"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "https://user:pass@drop.example/"));
    }
}
