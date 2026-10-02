package com.erikraft.drop;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseContractTest {
    @Test
    public void androidVersionMustRemain10_1_6Code29() {
        assertEquals("10.1.6", BuildConfig.VERSION_NAME);
        assertEquals(29, BuildConfig.VERSION_CODE);
    }
}
