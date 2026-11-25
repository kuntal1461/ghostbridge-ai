package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppAiPropertiesTest {
    private static AppAiProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source)).bindOrCreate("ghostbridge.app.ai", AppAiProperties.class);
    }

    @Test
    void theDefaultApprovesOnlyTheGenericSummarySchema() {
        assertThat(bind(Map.of()).structuredSchemas()).containsExactly(Map.entry("generic-summary", "generic-structured"));
    }

    @Test
    void configuredSchemasReplaceTheDefaultAndAreImmutable() {
        AppAiProperties properties = bind(Map.of("ghostbridge.app.ai.structured-schemas.wsdl-summary", "wsdl-prompt"));
        assertThat(properties.structuredSchemas()).containsExactly(Map.entry("wsdl-summary", "wsdl-prompt"));
        assertThatThrownBy(() -> properties.structuredSchemas().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidIdentifiersAreRejected() {
        assertThatThrownBy(() -> bind(Map.of("ghostbridge.app.ai.structured-schemas.BAD NAME", "p"))).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of("ghostbridge.app.ai.structured-schemas.ok-name", "bad prompt!"))).isInstanceOf(BindException.class);
    }

    @Test
    void anEmptyConfigurationValueFailsStartupInsteadOfSilentlyFallingBackToTheDefault() {
        assertThatThrownBy(() -> bind(Map.of("ghostbridge.app.ai.structured-schemas", ""))).isInstanceOf(BindException.class);
    }

    @Test
    void anExplicitlyEmptyMapApprovesNothing() {
        assertThat(new AppAiProperties(Map.of()).structuredSchemas()).isEmpty();
    }
}
