package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Policy of the App's AI use cases ({@code ghostbridge.app.ai.*}). {@code structuredSchemas} maps an approved schema
 * identifier to the registered prompt that produces it: clients can only choose an identifier from this operator-controlled
 * list, never submit a schema or a prompt name for the structured use case.
 */
@ConfigurationProperties(prefix = GhostBridgeAppLiterals.APP_AI_PROPERTY_PREFIX)
public record AppAiProperties(Map<String, String> structuredSchemas) {
    private static final Pattern SCHEMA_NAME = Pattern.compile(GhostBridgeAiLiterals.REGEX_SCHEMA_NAME);
    private static final Pattern PROMPT_NAME = Pattern.compile(GhostBridgeAiLiterals.REGEX_IDENTIFIER);

    public AppAiProperties {
        if (structuredSchemas == null) { // absent: the default; an explicitly empty map means "no structured schema is approved"
            structuredSchemas = Map.of(GhostBridgeAppLiterals.DEFAULT_STRUCTURED_SCHEMA, GhostBridgeAppLiterals.DEFAULT_STRUCTURED_PROMPT);
        } else {
            structuredSchemas.forEach(AppAiProperties::requireValidEntry);
            structuredSchemas = Map.copyOf(structuredSchemas);
        }
    }

    private static void requireValidEntry(String schema, String prompt) {
        if (schema == null || prompt == null || !SCHEMA_NAME.matcher(schema).matches() || !PROMPT_NAME.matcher(prompt).matches()) {
            throw new IllegalArgumentException(GhostBridgeAppLiterals.APP_AI_PROPERTY_PREFIX + ".structured-schemas contains an invalid entry");
        }
    }
}
