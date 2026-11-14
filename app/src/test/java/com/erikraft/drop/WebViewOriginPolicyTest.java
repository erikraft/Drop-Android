package com.erikraft.drop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
    public void createsExactOriginRulesWithoutPathsOrDefaultPorts() {
        assertEquals("https://drop.example", WebViewOriginPolicy.getOriginRule("https://drop.example/path/"));
        assertEquals("https://drop.example", WebViewOriginPolicy.getOriginRule("https://drop.example:443/"));
        assertEquals("http://drop.example:3000", WebViewOriginPolicy.getOriginRule("http://drop.example:3000/path/"));
        assertEquals("http://exampleonion.onion", WebViewOriginPolicy.getOriginRule("http://exampleonion.onion/"));
    }

    @Test
    public void rejectsInvalidOriginsForDocumentStartInjection() {
        assertNull(WebViewOriginPolicy.getOriginRule("file:///tmp/drop"));
        assertNull(WebViewOriginPolicy.getOriginRule("https://user:pass@drop.example/"));
        assertNull(WebViewOriginPolicy.getOriginRule("not a url"));
        assertNull(WebViewOriginPolicy.getOriginRule(null));
        assertFalse(WebViewOriginPolicy.isAllowed(null, "https://drop.example/"));
    }

    @Test
    public void rejectsNonWebAndMalformedUrls() {
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "javascript:alert(1)"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "file:///etc/passwd"));
        assertFalse(WebViewOriginPolicy.isAllowed("not a url", "https://drop.example/"));
        assertFalse(WebViewOriginPolicy.isAllowed("https://drop.example/", "https://user:pass@drop.example/"));
    }
}
