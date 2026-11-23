package com.ghostbridge.requestvo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.validation.MaxUtf8Bytes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of {@code POST /api/v1/ai/generate/structured}. {@code schemaName} must be one of the operator-approved schema
 * identifiers; clients cannot submit schemas or choose prompts. Unknown fields are rejected.
 */
public record AiStructuredGenerateRequestVO(
        @NotBlank @Pattern(regexp = GhostBridgeAiLiterals.REGEX_SCHEMA_NAME) String schemaName,
        @Size(max = GhostBridgeAiLiterals.MAX_VARIABLES)
        Map<@NotBlank @Pattern(regexp = GhostBridgeAiLiterals.REGEX_VARIABLE_NAME) String,
                @NotNull @MaxUtf8Bytes(GhostBridgeAiLiterals.MAX_API_VARIABLE_VALUE_BYTES) String> variables,
        @MaxUtf8Bytes(GhostBridgeAiLiterals.MAX_API_UNTRUSTED_CONTENT_BYTES) String untrustedContent,
        @Valid AiGenerationOptionsRequestVO options) {

    @JsonAnySetter
    @SuppressWarnings("java:S1172") // signature is fixed by Jackson; the name is deliberately never echoed
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown request field");
    }
}
