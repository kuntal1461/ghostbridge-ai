package com.ghostbridge.dto;

/**
 * One requester's totals over the retained request history. Token sums count only requests for which the provider
 * reported usage ({@code requestsWithTokenUsage}); nothing is estimated.
 */
public record AiUsageSummary(long requests, long successes, long failures, long requestsWithTokenUsage,
                             long inputTokens, long outputTokens) {

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
