package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustRemain10_1_5Code28() {
        assertEquals("10.1.5", BuildConfig.VERSION_NAME);
        assertEquals(28, BuildConfig.VERSION_CODE);
    }
}
