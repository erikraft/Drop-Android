package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustRemain10_1_9Code32() {
        assertEquals("10.1.9", BuildConfig.VERSION_NAME);
        assertEquals(32, BuildConfig.VERSION_CODE);
    }
}
