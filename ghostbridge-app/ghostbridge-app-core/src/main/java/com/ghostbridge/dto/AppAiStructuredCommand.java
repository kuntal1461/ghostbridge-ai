package com.ghostbridge.dto;

import com.ghostbridge.dto.AiGenerationOptions;

import java.util.Map;

/** Application-level request for schema-validated output; {@code schemaName} must be an approved identifier. */
public record AppAiStructuredCommand(String requesterKey, String schemaName, Map<String, String> variables,
                                     String untrustedContent, AiGenerationOptions options) {
}
