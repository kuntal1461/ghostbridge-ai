package com.ghostbridge.security;

import com.ghostbridge.config.AiWebProperties;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.impl.AiLimiter;
import com.ghostbridge.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequesterLimiterTest {
    private final MutableClock clock = new MutableClock();

    private AiRequesterLimiter limiter(int perMinute, int concurrent, int tracked) {
        return new AiRequesterLimiter(new AiWebProperties.RequesterLimit(perMinute, concurrent, tracked), clock);
    }

    private static void assertRateLimited(AiRequesterLimiter limiter, String key) {
        assertThatThrownBy(() -> limiter.acquire(key)).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED));
    }

    @Test
    void rejectsBeyondTheRateAndReportsTheRemainingWindowAsRetryAfter() {
        AiRequesterLimiter limiter = limiter(2, 10, 100);
        limiter.acquire("a").close();
        limiter.acquire("a").close();
        clock.advance(Duration.ofSeconds(15));
        assertThatThrownBy(() -> limiter.acquire("a")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(45)));
    }

    @Test
    void windowResetsAfterOneMinute() {
        AiRequesterLimiter limiter = limiter(1, 10, 100);
        limiter.acquire("a").close();
        assertRateLimited(limiter, "a");
        clock.advance(Duration.ofMinutes(1));
        limiter.acquire("a").close();
    }

    @Test
    void oneRequesterExhaustingTheirQuotaDoesNotBlockAnother() {
        AiRequesterLimiter limiter = limiter(1, 10, 100);
        limiter.acquire("a").close();
        assertRateLimited(limiter, "a");
        limiter.acquire("b").close();
    }

    @Test
    void concurrencyIsPerRequesterHasNoRetryAfterAndIsReleasedOnClose() {
        AiRequesterLimiter limiter = limiter(100, 1, 100);
        AiLimiter.Permit held = limiter.acquire("a");
        assertThatThrownBy(() -> limiter.acquire("a")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getRetryAfter()).isEmpty());
        limiter.acquire("b").close();
        held.close();
        held.close(); // closing twice must not free a second slot
        AiLimiter.Permit again = limiter.acquire("a");
        assertRateLimited(limiter, "a");
        again.close();
    }

    @Test
    void rejectedConcurrentRequestsDoNotConsumeRate() {
        AiRequesterLimiter limiter = limiter(2, 1, 100);
        AiLimiter.Permit held = limiter.acquire("a");
        assertRateLimited(limiter, "a");
        assertRateLimited(limiter, "a");
        held.close();
        limiter.acquire("a").close(); // second and last admitted request of the window
        assertRateLimited(limiter, "a");
    }

    @Test
    void idleStateExpiresAfterTheWindow() {
        AiRequesterLimiter limiter = limiter(5, 5, 100);
        for (int i = 0; i < 20; i++) {
            limiter.acquire("user-" + i).close();
        }
        assertThat(limiter.trackedRequesters()).isEqualTo(20);
        clock.advance(Duration.ofMinutes(2));
        limiter.acquire("fresh").close();
        assertThat(limiter.trackedRequesters()).isEqualTo(1);
    }

    @Test
    void entriesWithRequestsInFlightAreNeverSwept() {
        AiRequesterLimiter limiter = limiter(5, 5, 100);
        AiLimiter.Permit held = limiter.acquire("busy");
        clock.advance(Duration.ofMinutes(5));
        limiter.acquire("other").close();
        assertThat(limiter.trackedRequesters()).isEqualTo(2);
        held.close();
    }

    @Test
    void trackingIsBoundedAndFailsClosedWithAKnownRetryTime() {
        AiRequesterLimiter limiter = limiter(5, 5, 100);
        for (int i = 0; i < 100; i++) {
            limiter.acquire("user-" + i).close();
        }
        clock.advance(Duration.ofSeconds(10));
        assertThatThrownBy(() -> limiter.acquire("one-too-many")).isInstanceOfSatisfying(AiException.class, e -> {
            assertThat(e.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED);
            assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(50));
        });
        assertThat(limiter.trackedRequesters()).isEqualTo(100);
        limiter.acquire("user-3").close(); // an already tracked requester is still served
        clock.advance(Duration.ofMinutes(1));
        limiter.acquire("one-too-many").close(); // expiry frees room
        assertThat(limiter.trackedRequesters()).isLessThanOrEqualTo(100);
    }

    @Test
    void whenAllEntriesHaveRequestsInFlightNoRetryTimeIsInvented() {
        AiRequesterLimiter limiter = limiter(5, 5, 100);
        List<AiLimiter.Permit> permits = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            permits.add(limiter.acquire("user-" + i));
        }
        assertThatThrownBy(() -> limiter.acquire("new")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getRetryAfter()).isEmpty());
        permits.forEach(AiLimiter.Permit::close);
    }

    @Test
    void statusReportsOwnQuotaWithoutCreatingState() {
        AiRequesterLimiter limiter = limiter(3, 2, 100);
        assertThat(limiter.status("a").remaining()).isEqualTo(3);
        assertThat(limiter.trackedRequesters()).isZero();
        limiter.acquire("a").close();
        AiRequesterLimiter.Status status = limiter.status("a");
        assertThat(status.limit()).isEqualTo(3);
        assertThat(status.remaining()).isEqualTo(2);
        assertThat(status.maxConcurrent()).isEqualTo(2);
        assertThat(status.resetIn()).isEqualTo(Duration.ofMinutes(1));
        assertThat(limiter.status("b").remaining()).isEqualTo(3);
    }

    @Test
    void concurrentCallersNeverExceedTheRateLimit() throws Exception {
        int limit = 25;
        AiRequesterLimiter limiter = limiter(limit, 1000, 100);
        int threads = 16;
        int attemptsPerThread = 10;
        AtomicInteger admitted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        try {
                            limiter.acquire("shared").close();
                            admitted.incrementAndGet();
                        } catch (AiException expected) {
                            assertThat(expected.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(admitted.get()).isEqualTo(limit);
    }

    @Test
    void concurrentCallersNeverExceedTheConcurrencyLimit() throws Exception {
        int maxConcurrent = 3;
        AiRequesterLimiter limiter = limiter(1000, maxConcurrent, 100);
        int threads = 12;
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 50; i++) {
                        try (AiLimiter.Permit ignored = limiter.acquire("shared")) {
                            peak.accumulateAndGet(inside.incrementAndGet(), Math::max);
                            Thread.yield();
                            inside.decrementAndGet();
                        } catch (AiException expected) {
                            assertThat(expected.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(peak.get()).isPositive().isLessThanOrEqualTo(maxConcurrent);
    }
}
