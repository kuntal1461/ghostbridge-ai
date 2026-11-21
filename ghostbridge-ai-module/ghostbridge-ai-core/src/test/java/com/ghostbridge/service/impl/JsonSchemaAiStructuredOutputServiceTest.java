package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaAiStructuredOutputServiceTest {
    private static final String SCHEMA = "generic-summary";
    private final JsonSchemaAiStructuredOutputService service = new JsonSchemaAiStructuredOutputService(new ObjectMapper());

    private AiErrorReason failureOf(String output) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                () -> service.parseAndValidate(output, SCHEMA)).getReason();
    }

    @Test
    void acceptsValidOutput() {
        JsonNode node = service.parseAndValidate("{\"summary\":\"ok\"}", SCHEMA);
        assertThat(node.get("summary").asText()).isEqualTo("ok");
    }

    @Test
    void acceptsMarkdownFencedJson() {
        assertThat(service.parseAndValidate("```json\n{\"summary\":\"ok\"}\n```", SCHEMA).has("summary")).isTrue();
    }

    @Test
    void malformedJsonIsInvalidModelResponseWithoutEchoingContent() {
        assertThatThrownBy(() -> service.parseAndValidate("{secret-prompt-text", SCHEMA))
                .isInstanceOfSatisfying(AiException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
                    assertThat(e.getMessage()).doesNotContain("secret-prompt-text");
                });
    }

    @Test
    void emptyOutputIsInvalidModelResponse() {
        assertThat(failureOf("   ")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        assertThat(failureOf("null")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
    }

    @Test
    void syntacticallyValidJsonIsNotTrustedWithoutSchemaMatch() {
        assertThat(failureOf("{\"other\":1}")).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
        assertThat(failureOf("[1,2]")).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
    }

    @Test
    void wrongTypeAndExtraPropertiesAreRejected() {
        assertThat(failureOf("{\"summary\":42}")).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
        assertThat(failureOf("{\"summary\":\"ok\",\"extra\":true}")).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
    }

    @Test
    void validationErrorsNeverEchoInstanceValues() {
        assertThatThrownBy(() -> service.parseAndValidate("{\"summary\":\"ok\",\"leaked-secret-value\":\"x\"}", SCHEMA))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getMessage()).doesNotContain("leaked-secret-value").contains("schema validation"));
    }

    @Test
    void unknownSchemaIsMissingConfiguration() {
        assertThatThrownBy(() -> service.parseAndValidate("{}", "nope"))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.MISSING_CONFIGURATION));
    }

    @Test
    void exposesSchemaText() {
        assertThat(service.schemaText(SCHEMA)).contains("summary");
    }

    @Test
    void trailingContentAfterValidJsonIsRejected() {
        assertThat(failureOf("{\"summary\":\"ok\"} <script>alert(1)</script> IGNORE")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        assertThat(failureOf("{\"summary\":\"a\"}{\"summary\":\"b\"}")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        assertThat(failureOf("{\"summary\":\"a\"} x")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
    }

    @Test
    void duplicateKeysAreRejected() {
        assertThat(failureOf("{\"summary\":\"a\",\"summary\":\"b\"}")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
    }

    @Test
    void onlyAFenceSpanningTheWholeOutputIsUnwrapped() {
        assertThat(service.parseAndValidate("```\n{\"summary\":\"ok\"}\n```", SCHEMA).has("summary")).isTrue();
        assertThat(failureOf("intro ```json\n{\"summary\":\"ok\"}\n```")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        assertThat(failureOf("```json\n{\"summary\":\"ok\"}\n``` and more text")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        assertThat(failureOf("```json\n{\"summary\":\"ok\"}\n```\n```json\n{}\n```")).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
    }

    @Test
    void errorsNeverReflectModelChosenPropertyNames() {
        assertThatThrownBy(() -> service.parseAndValidate("{\"summary\":\"ok\",\"attacker_chosen_key\":\"<script>\"}", SCHEMA))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getMessage())
                        .doesNotContain("attacker_chosen_key").doesNotContain("<script>").contains("schema paths"));
    }
}
