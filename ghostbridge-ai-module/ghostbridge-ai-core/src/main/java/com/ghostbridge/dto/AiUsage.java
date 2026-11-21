package com.ghostbridge.dto;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Token usage exactly as reported by the provider; absent values stay absent. Negative counts are not valid usage and
 * are treated as unknown, so a faulty provider response can never reduce a consumed budget. Cost is present only when
 * pricing was explicitly configured.
 */
public record AiUsage(Integer inputTokens, Integer outputTokens, BigDecimal estimatedCost) {

    public AiUsage {
        inputTokens = inputTokens != null && inputTokens < 0 ? null : inputTokens;
        outputTokens = outputTokens != null && outputTokens < 0 ? null : outputTokens;
    }

    public static AiUsage unknown() {
        return new AiUsage(null, null, null);
    }

    /** Sum of the reported counts (saturating at {@link Integer#MAX_VALUE}), or empty when nothing was reported. */
    public Optional<Integer> totalTokens() {
        if (inputTokens == null && outputTokens == null) {
            return Optional.empty();
        }
        long total = (long) (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
        return Optional.of((int) Math.min(total, Integer.MAX_VALUE));
    }

    public AiUsage withEstimatedCost(BigDecimal cost) {
        return new AiUsage(inputTokens, outputTokens, cost);
    }
}
