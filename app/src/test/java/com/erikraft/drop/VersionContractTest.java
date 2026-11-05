package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Keeps the Android package version unchanged until the 10.1.10 release is published. */
public class VersionContractTest {
    @Test
    public void unpublishedAndroidVersionRemainsAt10110() {
        assertEquals("10.1.10", BuildConfig.VERSION_NAME);
    }
}
