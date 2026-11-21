package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiProviderResult;

import java.util.function.Supplier;

/** Applies cost and abuse safeguards around one provider call: rate/concurrency, token budget, bounded retry. */
public class AiExecutionGuard {
    private final AiLimiter limiter;
    private final AiTokenBudget budget;
    private final AiRetryExecutor retry;

    public AiExecutionGuard(AiLimiter limiter, AiTokenBudget budget, AiRetryExecutor retry) {
        this.limiter = limiter;
        this.budget = budget;
        this.retry = retry;
    }

    /** Runs the provider call under the rate/concurrency limit, token budget and bounded retry. */
    public AiProviderResult execute(Supplier<AiProviderResult> providerCall) {
        try (AiLimiter.Permit ignored = limiter.acquire()) {
            budget.checkAvailable();
            AiProviderResult result = retry.execute(providerCall);
            budget.record(result.usage());
            return result;
        }
    }
}
