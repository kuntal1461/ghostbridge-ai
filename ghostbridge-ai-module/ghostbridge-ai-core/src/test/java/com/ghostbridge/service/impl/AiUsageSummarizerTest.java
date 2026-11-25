package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.service.AiProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageSummarizerTest {
    private static AiRequestMetadata metadata(String requester, AiRequestStatus status, AiUsage usage) {
        return new AiRequestMetadata("id", null, requester, "mock", "m", "p", 1, status, null, 5, usage, Instant.EPOCH);
    }

    @Test
    void onlyTheRequestersOwnRequestsAreCountedAndMissingUsageIsNotEstimated() {
        AiUsageSummary summary = AiUsageSummarizer.summarize(List.of(
                metadata("me", AiRequestStatus.SUCCESS, new AiUsage(10, 4, null)),
                metadata("me", AiRequestStatus.FAILURE, new AiUsage(null, null, null)),
                metadata("me", AiRequestStatus.SUCCESS, null),
                metadata("someone-else", AiRequestStatus.SUCCESS, new AiUsage(1000, 1000, null))), "me");
        assertThat(summary.requests()).isEqualTo(3);
        assertThat(summary.successes()).isEqualTo(2);
        assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.requestsWithTokenUsage()).isEqualTo(1);
        assertThat(summary.inputTokens()).isEqualTo(10);
        assertThat(summary.outputTokens()).isEqualTo(4);
        assertThat(summary.totalTokens()).isEqualTo(14);
    }

    @Test
    void anUnknownRequesterHasAnEmptySummary() {
        assertThat(AiUsageSummarizer.summarize(List.of(metadata("a", AiRequestStatus.SUCCESS, null)), "b"))
                .isEqualTo(new AiUsageSummary(0, 0, 0, 0, 0, 0));
    }

    private static AiProvider provider(String name, boolean ready, boolean failing) {
        return new AiProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AiProviderResult invoke(AiProviderCall call) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AiProviderStatus status() {
                if (failing) {
                    throw new IllegalStateException("probe failed with detail that must not escape");
                }
                return new AiProviderStatus(ready, "detail");
            }
        };
    }

    @Test
    void readinessComesFromTheNamedProviderOnly() {
        assertThat(AiProviderReadiness.isReady(List.of(provider("ollama", true, false)), "ollama")).isTrue();
        assertThat(AiProviderReadiness.isReady(List.of(provider("ollama", false, false)), "ollama")).isFalse();
        assertThat(AiProviderReadiness.isReady(List.of(provider("mock", true, false)), "ollama")).isFalse();
        assertThat(AiProviderReadiness.isReady(List.of(), "ollama")).isFalse();
    }

    @Test
    void aFailingProbeIsReportedAsNotReadyNeverAsAnError() {
        assertThat(AiProviderReadiness.isReady(List.of(provider("ollama", true, true)), "ollama")).isFalse();
    }
}
