package com.ghostbridge.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiErrorReasonTest {

    @Test
    void everyReasonMapsToItsOwnSharedErrorCode() {
        assertThat(AiErrorReason.INVALID_INPUT.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(AiErrorReason.INPUT_TOO_LARGE.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
        assertThat(AiErrorReason.MISSING_CONFIGURATION.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        assertThat(AiErrorReason.MISSING_CREDENTIALS.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        assertThat(AiErrorReason.PROVIDER_UNAVAILABLE.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        assertThat(AiErrorReason.INTERRUPTED.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        assertThat(AiErrorReason.PROVIDER_TIMEOUT.errorCode()).isEqualTo(ErrorCode.UPSTREAM_TIMEOUT);
        assertThat(AiErrorReason.RATE_LIMITED.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(AiErrorReason.PROVIDER_THROTTLED.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(AiErrorReason.BUDGET_EXHAUSTED.errorCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
        assertThat(AiErrorReason.INVALID_MODEL_RESPONSE.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
        assertThat(AiErrorReason.OUTPUT_VALIDATION_FAILED.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
        assertThat(AiErrorReason.RESPONSE_TOO_LARGE.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
        assertThat(AiErrorReason.PROVIDER_FAILURE.errorCode()).isEqualTo(ErrorCode.UPSTREAM_ERROR);
    }

    @Test
    void limitFailuresDoNotCollapseIntoOneCode() {
        assertThat(java.util.Set.of(AiErrorReason.INVALID_INPUT.errorCode(), AiErrorReason.INPUT_TOO_LARGE.errorCode(),
                AiErrorReason.RATE_LIMITED.errorCode(), AiErrorReason.BUDGET_EXHAUSTED.errorCode(),
                AiErrorReason.RESPONSE_TOO_LARGE.errorCode())).hasSize(5);
    }

    @Test
    void onlyTransientProviderFailuresAreRetryable() {
        for (AiErrorReason reason : AiErrorReason.values()) {
            boolean transientFailure = reason == AiErrorReason.PROVIDER_UNAVAILABLE
                    || reason == AiErrorReason.PROVIDER_TIMEOUT || reason == AiErrorReason.PROVIDER_THROTTLED;
            assertThat(reason.retryable()).as(reason.name()).isEqualTo(transientFailure);
        }
    }

    @Test
    void exceptionCarriesReasonAndSharedCode() {
        AiException e = new AiException(AiErrorReason.RATE_LIMITED, "slow down", new IllegalStateException("provider detail"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(e.getMessage()).isEqualTo("slow down");
        assertThat(e.isRetryable()).isFalse();
    }
}
