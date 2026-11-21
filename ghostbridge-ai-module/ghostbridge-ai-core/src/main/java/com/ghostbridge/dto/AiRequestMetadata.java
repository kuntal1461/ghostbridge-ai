package com.ghostbridge.dto;

import java.time.Instant;

/** Diagnostic record of one request. Deliberately holds no prompt, variable or response content. */
public record AiRequestMetadata(
        String requestId,
        String correlationId,
        String requesterId,
        String provider,
        String model,
        String promptName,
        Integer promptVersion,
        AiRequestStatus status,
        String errorReason,
        long durationMs,
        AiUsage usage,
        Instant timestamp) {
}
