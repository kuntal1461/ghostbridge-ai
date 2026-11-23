package com.ghostbridge.advice;

import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiExceptionAdviceTest {
    private final AiExceptionAdvice advice = new AiExceptionAdvice();

    @Test
    void configurationCredentialAndInterruptionReasonsAreNotDisclosed() {
        for (AiErrorReason reason : new AiErrorReason[]{AiErrorReason.MISSING_CREDENTIALS, AiErrorReason.MISSING_CONFIGURATION, AiErrorReason.INTERRUPTED}) {
            ResponseEntity<ProblemDetail> response = advice.handleAi(new AiException(reason, "AI API key is missing for ollama"));
            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(response.getBody().getProperties()).containsEntry("reason", "PROVIDER_UNAVAILABLE").containsEntry("code", "UPSTREAM_UNAVAILABLE");
            assertThat(response.getBody().getDetail()).isEqualTo("AI provider is unavailable").doesNotContain("key");
        }
    }

    @Test
    void otherReasonsAreExposedByNameWithTheirClientSafeMessage() {
        ResponseEntity<ProblemDetail> response = advice.handleAi(new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, "Model returned an empty response"));
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().getProperties()).containsEntry("reason", "INVALID_MODEL_RESPONSE");
        assertThat(response.getBody().getDetail()).isEqualTo("Model returned an empty response");
    }

    @Test
    void retryAfterIsRoundedUpToWholeSecondsAndOnlyPresentWhenKnown() {
        assertThat(advice.handleAi(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ofMillis(1500))).getHeaders().getFirst("Retry-After")).isEqualTo("2");
        assertThat(advice.handleAi(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ofMillis(1))).getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(advice.handleAi(new AiException(AiErrorReason.RATE_LIMITED, "x", Duration.ofSeconds(60))).getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(advice.handleAi(new AiException(AiErrorReason.RATE_LIMITED, "x")).getHeaders()).doesNotContainKey("Retry-After");
        assertThat(advice.handleAi(new AiException(AiErrorReason.PROVIDER_UNAVAILABLE, "x")).getHeaders()).doesNotContainKey("Retry-After");
    }

    @Test
    void budgetExhaustionIsATooManyRequestsWithItsOwnCode() {
        ResponseEntity<ProblemDetail> response = advice.handleAi(new AiException(AiErrorReason.BUDGET_EXHAUSTED, "AI token budget is exhausted", Duration.ofHours(1)));
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().getProperties()).containsEntry("code", "QUOTA_EXCEEDED").containsEntry("reason", "BUDGET_EXHAUSTED");
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
    }
}
