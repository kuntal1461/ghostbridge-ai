package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiLimiterTest {

    /** Mutable clock for window tests. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static void assertRateLimited(AiLimiter limiter) {
        assertThatThrownBy(limiter::acquire).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED));
    }

    @Test
    void rejectsBeyondRequestsPerMinuteAndResetsNextWindow() {
        TestClock clock = new TestClock();
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(2, 10), clock);
        limiter.acquire().close();
        limiter.acquire().close();
        assertRateLimited(limiter);
        clock.advance(Duration.ofMinutes(1));
        limiter.acquire().close();
    }

    @Test
    void rejectsWhenConcurrencyExhaustedAndReleasesOnClose() {
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(100, 1), Clock.systemUTC());
        AiLimiter.Permit held = limiter.acquire();
        assertRateLimited(limiter);
        held.close();
        limiter.acquire().close();
    }

    @Test
    void rateRejectionDoesNotLeakConcurrencySlot() {
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(1, 1), Clock.systemUTC());
        limiter.acquire().close();
        assertRateLimited(limiter);
        assertRateLimited(limiter);
    }

    @Test
    void closingAPermitTwiceReleasesOnlyOneSlot() {
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(100, 1), Clock.systemUTC());
        AiLimiter.Permit first = limiter.acquire();
        first.close();
        first.close();
        AiLimiter.Permit second = limiter.acquire();
        assertRateLimited(limiter);
        second.close();
    }

    @Test
    void neverAdmitsMoreThanMaxConcurrentUnderContention() throws Exception {
        int max = 3;
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(10000, max), Clock.systemUTC());
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger peak = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int n = 0; n < 200; n++) {
                    try (AiLimiter.Permit ignored = limiter.acquire()) {
                        peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        inFlight.decrementAndGet();
                    } catch (AiException expected) {
                        // rejected under contention: fine
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertThat(peak.get()).isLessThanOrEqualTo(max);
        AiLimiter.Permit[] all = new AiLimiter.Permit[max];
        for (int i = 0; i < max; i++) {
            all[i] = limiter.acquire();
        }
        assertRateLimited(limiter);
    }

    @Test
    void rateLimitReportsTheRemainingWindowAsRetryAfter() {
        TestClock clock = new TestClock();
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(1, 10), clock);
        limiter.acquire().close();
        clock.advance(Duration.ofSeconds(20));
        assertThatThrownBy(limiter::acquire).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(40)));
    }

    @Test
    void concurrencyRejectionHasNoRetryAfterBecauseItIsUnknown() {
        AiLimiter limiter = new AiLimiter(new AiProperties.RateLimit(100, 1), Clock.systemUTC());
        AiLimiter.Permit held = limiter.acquire();
        assertThatThrownBy(limiter::acquire).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getRetryAfter()).isEmpty());
        held.close();
    }
}
