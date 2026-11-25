package com.ghostbridge.security;

import com.ghostbridge.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
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

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
            new AppSecurityProperties.LoginAttempts(3, 30, Duration.ofMinutes(10)), clock);

    @Test
    void aKeyIsBlockedOnlyAtItsThresholdAndForTheRemainderOfTheWindow() {
        limiter.recordFailure("u:a");
        limiter.recordFailure("u:a");
        assertThat(limiter.blockedFor("u:a", 3)).isEmpty();
        limiter.recordFailure("u:a");
        assertThat(limiter.blockedFor("u:a", 3)).hasValue(Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(4));
        assertThat(limiter.blockedFor("u:a", 3)).hasValue(Duration.ofMinutes(6));
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.blockedFor("u:a", 3)).isEmpty();
    }

    @Test
    void theSameFailureCountBlocksAUserNameButNotYetAnAddressWithAHigherThreshold() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a:1.2.3.4");
        }
        assertThat(limiter.blockedFor("a:1.2.3.4", 30)).isEmpty();
        assertThat(limiter.blockedFor("a:1.2.3.4", 3)).isPresent();
    }

    @Test
    void anExpiredWindowStartsAgainAndAResetClearsTheCount() {
        limiter.recordFailure("u:b");
        limiter.recordFailure("u:b");
        clock.advance(Duration.ofMinutes(11));
        limiter.recordFailure("u:b");
        assertThat(limiter.blockedFor("u:b", 3)).isEmpty();
        limiter.recordFailure("u:b");
        limiter.recordFailure("u:b");
        assertThat(limiter.blockedFor("u:b", 3)).isPresent();
        limiter.reset("u:b");
        assertThat(limiter.blockedFor("u:b", 3)).isEmpty();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("u:x");
        }
        assertThat(limiter.blockedFor("u:y", 3)).isEmpty();
    }
}
