package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiUsageSummary;

import java.util.List;

/** Aggregates the retained history for exactly one requester, so no other caller's activity is ever counted. */
public final class AiUsageSummarizer {
    private AiUsageSummarizer() {
    }

    public static AiUsageSummary summarize(List<AiRequestMetadata> recent, String requesterKey) {
        long requests = 0;
        long successes = 0;
        long withUsage = 0;
        long input = 0;
        long output = 0;
        for (AiRequestMetadata metadata : recent) {
            if (!requesterKey.equals(metadata.requesterId())) {
                continue;
            }
            requests++;
            if (metadata.status() == AiRequestStatus.SUCCESS) {
                successes++;
            }
            if (metadata.usage() != null && metadata.usage().totalTokens().isPresent()) {
                withUsage++;
                input += metadata.usage().inputTokens() == null ? 0 : metadata.usage().inputTokens();
                output += metadata.usage().outputTokens() == null ? 0 : metadata.usage().outputTokens();
            }
        }
        return new AiUsageSummary(requests, successes, requests - successes, withUsage, input, output);
    }
}
