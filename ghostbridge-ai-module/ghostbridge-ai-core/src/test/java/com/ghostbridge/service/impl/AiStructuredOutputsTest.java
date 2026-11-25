package com.ghostbridge.service.impl;

import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiStructuredOutputsTest {
    @Test
    void anApprovedSchemaResolvesToItsPromptAndAnUnknownOneIsInvalidInput() {
        Map<String, String> approved = Map.of("generic-summary", "generic-structured");
        assertThat(AiStructuredOutputs.approvedPrompt(approved, "generic-summary")).isEqualTo("generic-structured");
        assertThatThrownBy(() -> AiStructuredOutputs.approvedPrompt(approved, "other")).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INVALID_INPUT));
    }

    @Test
    void aMissingStructuredOutputIsAValidationFailure() {
        AiResponse plain = new AiResponse("id", "mock", "model", "text", null, new AiUsage(null, null, null), 1L);
        assertThatThrownBy(() -> AiStructuredOutputs.requireStructured(plain)).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED));
    }
}
