package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fail-fast request-rate (fixed one-minute window) and concurrency limiter. Excess requests are rejected, never queued. */
public class AiLimiter {
    /** Held for the duration of one provider call; closing it releases the concurrency slot (closing twice is harmless). */
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    private final int requestsPerWindow;
    private final Semaphore concurrency;
    private final Clock clock;
    private long windowStartMillis;
    private int used;

    public AiLimiter(AiProperties.RateLimit limits, Clock clock) {
        this.requestsPerWindow = limits.requestsPerMinute();
        this.concurrency = new Semaphore(limits.maxConcurrent());
        this.clock = clock;
        this.windowStartMillis = clock.millis();
    }

    /** @throws com.ghostbridge.exception.AiException RATE_LIMITED when the rate or concurrency limit is reached */
    public Permit acquire() {
        if (!concurrency.tryAcquire()) {
            throw new AiException(AiErrorReason.RATE_LIMITED, "Too many concurrent AI requests");
        }
        if (!admitWithinRate()) {
            concurrency.release();
            throw new AiException(AiErrorReason.RATE_LIMITED, "AI request rate limit exceeded");
        }
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                concurrency.release();
            }
        };
    }

    private synchronized boolean admitWithinRate() {
        long now = clock.millis();
        if (now - windowStartMillis >= GhostBridgeAiLiterals.RATE_LIMIT_WINDOW.toMillis()) {
            windowStartMillis = now;
            used = 0;
        }
        if (used >= requestsPerWindow) {
            return false;
        }
        used++;
        return true;
    }
}
