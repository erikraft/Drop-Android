package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustBe10_1_10Code33() {
        assertEquals("10.1.10", BuildConfig.VERSION_NAME);
        assertEquals(33, BuildConfig.VERSION_CODE);
    }
}
