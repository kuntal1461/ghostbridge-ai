package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.util.AiTextSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Malformed prompt or schema resources must stop startup rather than fail at request time. */
class StartupFailFastTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaAiStructuredOutputService structured = new JsonSchemaAiStructuredOutputService(mapper);

    private void assertPromptFails(String folder, String messagePart) {
        assertThatThrownBy(() -> new ClasspathAiPromptService(mapper, structured, new AiTextSanitizer(List.of()),
                "classpath*:fixtures/prompts/" + folder + "/*.json"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining(messagePart);
    }

    private void assertSchemaFails(String pattern, String messagePart) {
        assertThatThrownBy(() -> new JsonSchemaAiStructuredOutputService(mapper, "classpath*:fixtures/schemas/" + pattern))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(messagePart);
    }

    @Test
    void duplicatePromptNameAndVersion() {
        assertPromptFails("duplicate", "Duplicate prompt");
    }

    @Test
    void unreadablePromptTemplate() {
        assertPromptFails("bad-json", "Invalid prompt template");
    }

    @Test
    void unknownFieldsInPromptTemplateAreRejected() {
        assertPromptFails("unknown-field", "Invalid prompt template");
    }

    @Test
    void systemTextMustNotContainPlaceholders() {
        assertPromptFails("system-placeholder", "must not contain placeholders");
    }

    @Test
    void declaredVariablesMustMatchTemplatePlaceholders() {
        assertPromptFails("variable-mismatch", "declared variables must match");
    }

    @Test
    void promptReferencingUnknownSchema() {
        assertPromptFails("missing-schema", "Output schema is not available");
    }

    @Test
    void blankUserTemplate() {
        assertPromptFails("blank-user", "user template is required");
    }

    @Test
    void duplicateSchemaNamesAreRejected() {
        assertSchemaFails("duplicate/*/*.schema.json", "Duplicate AI schema name");
    }

    @Test
    void remoteSchemaReferencesAreRejected() {
        assertSchemaFails("remote-ref/*.schema.json", "remote documents");
    }

    @Test
    void invalidSchemaFileIsRejected() {
        assertSchemaFails("invalid/*.schema.json", "Invalid AI schema");
    }

    @Test
    void remoteIdDialectAndNonHttpReferencesAreRejected() {
        assertSchemaFails("remote-id/*.schema.json", "remote");
        assertSchemaFails("remote-id-relative-ref/*.schema.json", "remote");
        assertSchemaFails("remote-dynamic-ref/*.schema.json", "remote");
        assertSchemaFails("remote-dialect/*.schema.json", "remote");
        assertSchemaFails("ftp-ref/*.schema.json", "remote");
    }

    @Test
    void localReferencesAndTheStandardDialectAreAllowed() {
        new JsonSchemaAiStructuredOutputService(mapper, "classpath*:fixtures/schemas/local-ok/*.schema.json");    }
}
