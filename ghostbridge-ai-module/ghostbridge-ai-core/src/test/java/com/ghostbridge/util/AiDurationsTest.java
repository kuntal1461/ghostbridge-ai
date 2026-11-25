package com.ghostbridge.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiDurationsTest {
    @Test
    void secondsAreRoundedUp() {
        assertThat(AiDurations.ceilSeconds(Duration.ZERO)).isZero();
        assertThat(AiDurations.ceilSeconds(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(AiDurations.ceilSeconds(Duration.ofMillis(1000))).isEqualTo(1);
        assertThat(AiDurations.ceilSeconds(Duration.ofMillis(1001))).isEqualTo(2);
    }

    @Test
    void aRetryAfterValueIsNeverLessThanOneSecond() {
        assertThat(AiDurations.retryAfterSeconds(Duration.ZERO)).isEqualTo(1);
        assertThat(AiDurations.retryAfterSeconds(Duration.ofMillis(1500))).isEqualTo(2);
    }
}
