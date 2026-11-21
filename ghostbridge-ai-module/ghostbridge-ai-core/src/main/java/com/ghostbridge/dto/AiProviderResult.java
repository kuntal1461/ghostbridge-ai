package com.ghostbridge.dto;

/** Raw provider output. Usage must be {@link AiUsage#unknown()} when the provider reports none. */
public record AiProviderResult(String text, AiUsage usage) {

    public AiProviderResult {
        usage = usage == null ? AiUsage.unknown() : usage;
    }
}
