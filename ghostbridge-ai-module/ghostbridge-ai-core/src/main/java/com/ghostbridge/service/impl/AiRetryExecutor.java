package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Bounded retry with exponential backoff, only for transient provider failures
 * ({@link AiException#isRetryable()}). Generation has no side effects, so retrying it is safe.
 */
public class AiRetryExecutor {

    /** Seam for tests; the default sleeps the current thread. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final AiProperties.Retry settings;
    private final Sleeper sleeper;

    public AiRetryExecutor(AiProperties.Retry settings, Sleeper sleeper) {
        this.settings = settings;
        this.sleeper = sleeper;
    }

    public AiRetryExecutor(AiProperties.Retry settings) {
        this(settings, duration -> Thread.sleep(duration.toMillis()));
    }

    /** Runs {@code action}, retrying retryable {@link AiException}s up to the configured attempts; other failures propagate. */
    public <T> T execute(Supplier<T> action) {
        int attempt = 1;
        while (true) {
            try {
                return action.get();
            } catch (AiException e) {
                if (!e.isRetryable() || attempt >= settings.maxAttempts()) {
                    throw e;
                }
                pause(attempt);
                attempt++;
            }
        }
    }

    private void pause(int attempt) {
        Duration backoff = settings.initialBackoff().multipliedBy(1L << (attempt - 1));
        Duration bounded = backoff.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : backoff;
        try {
            sleeper.sleep(bounded);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorReason.INTERRUPTED, "AI request was interrupted", e);
        }
    }
}
