package com.ghostbridge.requestvo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Optional generation overrides. The core re-validates them against the configured maximums. Unknown fields are rejected. */
public record AiGenerationOptionsRequestVO(
        @Min(1) @Max(GhostBridgeAiLiterals.MAX_API_OUTPUT_TOKENS) Integer maxOutputTokens,
        @DecimalMin("0.0") @DecimalMax("1.0") Double temperature) {

    @JsonAnySetter
    @SuppressWarnings("java:S1172") // signature is fixed by Jackson; the name is deliberately never echoed
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown request field");
    }
}
