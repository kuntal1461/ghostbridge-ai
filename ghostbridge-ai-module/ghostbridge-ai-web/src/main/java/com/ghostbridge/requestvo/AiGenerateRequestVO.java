package com.ghostbridge.requestvo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.validation.MaxUtf8Bytes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Body of {@code POST /api/v1/ai/generate}. Only a registered prompt template can be invoked (no free-form prompt).
 * There is deliberately no requester field: identity comes from the authentication context, and any unknown field
 * (including a spoofed {@code requesterId}) is rejected with 400.
 */
public record AiGenerateRequestVO(
        @NotBlank @Pattern(regexp = GhostBridgeAiLiterals.REGEX_IDENTIFIER) String promptName,
        @Min(1) @Max(GhostBridgeAiLiterals.MAX_API_PROMPT_VERSION) Integer promptVersion,
        @Size(max = GhostBridgeAiLiterals.MAX_VARIABLES)
        Map<@NotBlank @Pattern(regexp = GhostBridgeAiLiterals.REGEX_VARIABLE_NAME) String,
                @NotNull @MaxUtf8Bytes(GhostBridgeAiLiterals.MAX_API_VARIABLE_VALUE_BYTES) String> variables,
        @MaxUtf8Bytes(GhostBridgeAiLiterals.MAX_API_UNTRUSTED_CONTENT_BYTES) String untrustedContent,
        @Size(max = GhostBridgeAiLiterals.MAX_IDENTIFIER_LENGTH) String modelId,
        @Valid AiGenerationOptionsRequestVO options) {

    @JsonAnySetter
    @SuppressWarnings("java:S1172") // signature is fixed by Jackson; the name is deliberately never echoed
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown request field");
    }
}
