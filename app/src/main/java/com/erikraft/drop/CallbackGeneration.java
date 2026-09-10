package com.erikraft.drop;

/**
 * Invalidates asynchronous work belonging to an older controller operation.
 *
 * <p>Tor wrapper observers and Activity callbacks can be delivered after a retry or shutdown.
 * A callback must therefore be associated with the wrapper/request that created it, rather than
 * merely with the controller singleton.</p>
 */
final class CallbackGeneration {
    private long value;

    synchronized long next() {
        return ++value;
    }

    synchronized boolean isCurrent(final long generation) {
        return value == generation;
    }

    synchronized long current() {
        return value;
    }
}
