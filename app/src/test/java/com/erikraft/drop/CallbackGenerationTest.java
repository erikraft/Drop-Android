package com.erikraft.drop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallbackGenerationTest {
    @Test
    public void retryInvalidatesCallbacksFromThePreviousWrapper() {
        final CallbackGeneration generations = new CallbackGeneration();
        final long oldWrapper = generations.next();
        final long newWrapper = generations.next();

        assertFalse(generations.isCurrent(oldWrapper));
        assertTrue(generations.isCurrent(newWrapper));
    }

    @Test
    public void shutdownInvalidatesCallbacksAlreadyQueuedOnTheMainThread() {
        final CallbackGeneration generations = new CallbackGeneration();
        final long request = generations.next();
        generations.next();

        assertFalse(generations.isCurrent(request));
    }
}
