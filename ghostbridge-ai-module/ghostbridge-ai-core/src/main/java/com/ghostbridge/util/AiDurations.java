package com.ghostbridge.util;

import java.time.Duration;

/** Duration helpers shared by every entry point that reports a wait in whole seconds. */
public final class AiDurations {
    private static final long MILLIS_PER_SECOND = 1000;

    private AiDurations() {
    }

    /** The value of a {@code Retry-After} header: whole seconds, rounded up, never less than one. */
    public static long retryAfterSeconds(Duration wait) {
        return Math.max(1, ceilSeconds(wait));
    }

    /** Whole seconds, rounded up, so a client that waits this long is never early. */
    public static long ceilSeconds(Duration duration) {
        return (duration.toMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
