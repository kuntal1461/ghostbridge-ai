package com.ghostbridge.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWebPropertiesTest {
    private static final AiWebProperties.RequesterLimit LIMIT = new AiWebProperties.RequesterLimit(20, 2, 10000);

    @Test
    void defaultsAreRestrictiveAndTheDefaultSchemaIsApproved() {
        AiWebProperties properties = new AiWebProperties("AI_USER", 65536, LIMIT, null);
        assertThat(properties.structuredSchemas()).containsExactly(Map.entry("generic-summary", "generic-structured"));
        assertThat(new AiWebProperties("AI_USER", 65536, LIMIT, Map.of()).structuredSchemas()).hasSize(1);
    }

    @Test
    void configuredSchemasReplaceTheDefaultAndAreCopied() {
        AiWebProperties properties = new AiWebProperties("AI_USER", 65536, LIMIT, Map.of("my-schema", "my-prompt"));
        assertThat(properties.structuredSchemas()).containsExactly(Map.entry("my-schema", "my-prompt"));
    }

    @Test
    void invalidSchemaOrPromptNamesAreRejected() {
        assertThatThrownBy(() -> new AiWebProperties("AI_USER", 65536, LIMIT, Map.of("Bad Schema", "p")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiWebProperties("AI_USER", 65536, LIMIT, Map.of("ok", "bad prompt!")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
