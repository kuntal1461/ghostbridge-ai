package com.ghostbridge.responsevo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** Result of {@code POST /api/v1/ai/generate}. {@code text} is model output: untrusted, encode it when rendering. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiGenerateResponseVO(
        String requestId,
        String model,
        String text,
        JsonNode structured,
        AiTokenUsageResponseVO usage,
        long durationMs) {
}
