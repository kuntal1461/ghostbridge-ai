package com.ghostbridge.exception;

/**
 * Why an AI operation failed. Each reason carries the shared {@link ErrorCode} it is reported as, so every limit
 * failure has its own contract: invalid input is 400, oversized input 413, rate limit 429, token-budget exhaustion
 * a distinct 429 quota code, and invalid or oversized upstream output 502.
 */
public enum AiErrorReason {
    INVALID_INPUT(ErrorCode.INVALID_INPUT, false),
    INPUT_TOO_LARGE(ErrorCode.PAYLOAD_TOO_LARGE, false),
    MISSING_CONFIGURATION(ErrorCode.UPSTREAM_UNAVAILABLE, false),
    MISSING_CREDENTIALS(ErrorCode.UPSTREAM_UNAVAILABLE, false),
    PROVIDER_UNAVAILABLE(ErrorCode.UPSTREAM_UNAVAILABLE, true),
    PROVIDER_TIMEOUT(ErrorCode.UPSTREAM_TIMEOUT, true),
    PROVIDER_THROTTLED(ErrorCode.RATE_LIMITED, true),
    PROVIDER_FAILURE(ErrorCode.UPSTREAM_ERROR, false),
    INTERRUPTED(ErrorCode.UPSTREAM_UNAVAILABLE, false),
    RATE_LIMITED(ErrorCode.RATE_LIMITED, false),
    BUDGET_EXHAUSTED(ErrorCode.QUOTA_EXCEEDED, false),
    INVALID_MODEL_RESPONSE(ErrorCode.UPSTREAM_ERROR, false),
    OUTPUT_VALIDATION_FAILED(ErrorCode.UPSTREAM_ERROR, false),
    RESPONSE_TOO_LARGE(ErrorCode.UPSTREAM_ERROR, false);

    private final ErrorCode errorCode;
    private final boolean retryable;

    AiErrorReason(ErrorCode errorCode, boolean retryable) {
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /** The shared, client-facing error code (and therefore HTTP status) for this reason. */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** True only for transient provider-side failures that are safe to retry (generation has no side effects). */
    public boolean retryable() {
        return retryable;
    }
}
