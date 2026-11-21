package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRetryExecutorTest {
    private final List<Duration> sleeps = new ArrayList<>();
    private final AiProperties.Retry settings = new AiProperties.Retry(3, Duration.ofMillis(100), Duration.ofMillis(250));
    private final AiRetryExecutor executor = new AiRetryExecutor(settings, sleeps::add);

    @Test
    void retriesTransientFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "down");
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void stopsAtMaxAttemptsAndCapsBackoff() {
        AtomicInteger calls = new AtomicInteger();
        AiRetryExecutor wide = new AiRetryExecutor(new AiProperties.Retry(5, Duration.ofMillis(100), Duration.ofMillis(250)), sleeps::add);
        assertThatThrownBy(() -> wide.execute(() -> {
            calls.incrementAndGet();
            throw new AiException(AiErrorReason.PROVIDER_TIMEOUT, "slow");
        })).isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.PROVIDER_TIMEOUT));
        assertThat(calls).hasValue(5);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(250));
    }

    @Test
    void doesNotRetryNonRetryableFailures() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new AiException(AiErrorReason.MISSING_CREDENTIALS, "no creds");
        })).isInstanceOf(AiException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void interruptionRestoresFlagAndFails() {
        AiRetryExecutor interrupted = new AiRetryExecutor(settings, d -> {
            throw new InterruptedException();
        });
        assertThatThrownBy(() -> interrupted.execute(() -> {
            throw new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "down");
        })).isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INTERRUPTED));
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void defaultSleeperConstructorWorks() {
        AiRetryExecutor real = new AiRetryExecutor(new AiProperties.Retry(2, Duration.ofMillis(1), Duration.ofMillis(1)));
        AtomicInteger calls = new AtomicInteger();
        assertThat(real.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new AiException(AiErrorReason.PROVIDER_THROTTLED, "throttled");
            }
            return 1;
        })).isEqualTo(1);
    }
}
