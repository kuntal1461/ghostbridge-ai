package com.ghostbridge.responsevo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** Result of {@code POST /api/v1/ai/generate/structured}: only schema-validated JSON, never raw model text. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiStructuredGenerateResponseVO(
        String requestId,
        String schemaName,
        String model,
        JsonNode structured,
        AiTokenUsageResponseVO usage,
        long durationMs) {
}
