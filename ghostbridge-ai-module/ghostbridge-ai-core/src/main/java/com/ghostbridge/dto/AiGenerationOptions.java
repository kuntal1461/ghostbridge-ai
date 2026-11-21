package com.ghostbridge.dto;

/** Optional per-request generation overrides. Null means "use the configured default"; values are bounded by configuration. */
public record AiGenerationOptions(Integer maxOutputTokens, Double temperature) {

    public static AiGenerationOptions defaults() {
        return new AiGenerationOptions(null, null);
    }
}
