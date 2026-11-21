package com.ghostbridge.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/** Normalized generation result. {@code structured} is present only when the prompt required and passed schema validation. */
public record AiResponse(
        String requestId,
        String provider,
        String model,
        String text,
        JsonNode structured,
        AiUsage usage,
        long durationMs) {

    public Optional<JsonNode> structuredOutput() {
        return Optional.ofNullable(structured);
    }
}
