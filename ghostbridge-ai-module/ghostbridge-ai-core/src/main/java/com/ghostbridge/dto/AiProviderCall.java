package com.ghostbridge.dto;

import java.time.Duration;

/** Everything a provider adapter needs for one invocation. */
public record AiProviderCall(
        String model,
        String system,
        String user,
        int maxOutputTokens,
        double temperature,
        Duration timeout,
        boolean jsonOutput) {
}
