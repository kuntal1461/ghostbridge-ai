package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiTokenBudgetTest {

    @Test
    void disabledBudgetNeverBlocks() {
        AiTokenBudget budget = new AiTokenBudget(new AiProperties.Budget(0, Duration.ofHours(1)), Clock.systemUTC());
        budget.record(new AiUsage(1_000_000, 1_000_000, null));
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    @Test
    void blocksOnceReportedUsageReachesBudget() {
        AiTokenBudget budget = new AiTokenBudget(new AiProperties.Budget(100, Duration.ofHours(1)), Clock.systemUTC());
        budget.checkAvailable();
        budget.record(new AiUsage(60, 40, null));
        assertThatThrownBy(budget::checkAvailable).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.BUDGET_EXHAUSTED));
    }

    @Test
    void unknownUsageIsNotFabricated() {
        AiTokenBudget budget = new AiTokenBudget(new AiProperties.Budget(10, Duration.ofHours(1)), Clock.systemUTC());
        budget.record(AiUsage.unknown());
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    @Test
    void windowResetsAfterExpiry() {
        Clock[] now = {Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)};
        Clock moving = new Clock() {
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
                return now[0].instant();
            }
        };
        AiTokenBudget budget = new AiTokenBudget(new AiProperties.Budget(10, Duration.ofMinutes(5)), moving);
        budget.record(new AiUsage(10, 0, null));
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(AiException.class);
        now[0] = Clock.fixed(Instant.parse("2026-01-01T00:06:00Z"), ZoneOffset.UTC);
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
    }

    @Test
    void exhaustedBudgetReportsTheRemainingWindowAsRetryAfter() {
        AiTokenBudget budget = new AiTokenBudget(new AiProperties.Budget(10, Duration.ofHours(1)), Clock.systemUTC());
        budget.record(new AiUsage(10, 0, null));
        assertThatThrownBy(budget::checkAvailable).isInstanceOfSatisfying(AiException.class, e -> {
            assertThat(e.getRetryAfter()).isPresent();
            assertThat(e.getRetryAfter().get()).isPositive().isLessThanOrEqualTo(Duration.ofHours(1));
        });
    }
}
