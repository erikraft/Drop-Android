package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustRemain10_1_7Code30() {
        assertEquals("10.1.7", BuildConfig.VERSION_NAME);
        assertEquals(30, BuildConfig.VERSION_CODE);
    }
}
