package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import java.time.Clock;

/** Optional fixed-window token budget. Counts only usage the provider actually reported; never estimates. */
public class AiTokenBudget {
    private final AiProperties.Budget settings;
    private final Clock clock;
    private long windowStartMillis;
    private long usedTokens;

    public AiTokenBudget(AiProperties.Budget settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        this.windowStartMillis = clock.millis();
    }

    /** @throws AiException BUDGET_EXHAUSTED when the window's budget is used up */
    public synchronized void checkAvailable() {
        if (!settings.enabled()) {
            return;
        }
        rollWindow();
        if (usedTokens >= settings.maxTokens()) {
            throw new AiException(AiErrorReason.BUDGET_EXHAUSTED, "AI token budget is exhausted");
        }
    }

    /** Adds the provider-reported usage to the current window (unknown usage adds nothing). */
    public synchronized void record(AiUsage usage) {
        if (!settings.enabled()) {
            return;
        }
        rollWindow();
        usedTokens += usage.totalTokens().orElse(0);
    }

    private void rollWindow() {
        long now = clock.millis();
        if (now - windowStartMillis >= settings.window().toMillis()) {
            windowStartMillis = now;
            usedTokens = 0;
        }
    }
}
