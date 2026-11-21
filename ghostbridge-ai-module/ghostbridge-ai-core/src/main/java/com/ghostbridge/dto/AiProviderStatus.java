package com.ghostbridge.dto;

/** Readiness of a provider. {@code detail} is a short, safe description (no credentials, no ARNs). */
public record AiProviderStatus(boolean ready, String detail) {
}
