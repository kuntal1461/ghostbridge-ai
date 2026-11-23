package com.ghostbridge.responsevo;

/**
 * The authenticated caller's own usage, computed from the bounded retained request history ({@code scope}
 * RETAINED_HISTORY, at most {@code historyLimit} recent requests of all callers). Token sums count only requests for
 * which the provider reported usage ({@code requestsWithTokenUsage}). Budget data is global and not exposed here.
 */
public record AiUsageResponseVO(
        String scope,
        int historyLimit,
        long requestCount,
        long successCount,
        long failureCount,
        long requestsWithTokenUsage,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        AiRateLimitResponseVO rateLimit) {
}
