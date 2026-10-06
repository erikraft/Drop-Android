package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustRemain10_1_8Code31() {
        assertEquals("10.1.8", BuildConfig.VERSION_NAME);
        assertEquals(31, BuildConfig.VERSION_CODE);
    }
}
