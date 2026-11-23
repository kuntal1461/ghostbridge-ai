package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Validated configuration of the AI REST API ({@code ghostbridge.ai.web.*}). Defaults are restrictive.
 *
 * @param requiredRole      role (without the {@code ROLE_} prefix) every caller of {@code /api/v1/ai/**} must hold
 * @param maxRequestBytes   hard limit of the request body in bytes, enforced on the bytes actually received
 * @param requesterLimit    per-requester limits (the global {@code ghostbridge.ai.rate-limit} still applies on top)
 * @param structuredSchemas approved structured-output schema identifier → registered prompt name; clients can only
 *                          pick an identifier from this operator-controlled list, never submit a schema
 */
@Validated
@ConfigurationProperties(prefix = GhostBridgeAiLiterals.WEB_PROPERTY_PREFIX)
public record AiWebProperties(
        @DefaultValue("AI_USER") @NotBlank @Pattern(regexp = GhostBridgeAiLiterals.REGEX_ROLE_NAME) String requiredRole,
        @DefaultValue("65536") @Min(1024) @Max(1048576) int maxRequestBytes,
        @Valid @NotNull @DefaultValue RequesterLimit requesterLimit,
        Map<String, String> structuredSchemas) {

    private static final Map<String, String> DEFAULT_STRUCTURED_SCHEMAS = Map.of("generic-summary", "generic-structured");
    private static final java.util.regex.Pattern SCHEMA_NAME = java.util.regex.Pattern.compile(GhostBridgeAiLiterals.REGEX_SCHEMA_NAME);
    private static final java.util.regex.Pattern PROMPT_NAME = java.util.regex.Pattern.compile(GhostBridgeAiLiterals.REGEX_IDENTIFIER);

    public AiWebProperties {
        structuredSchemas = structuredSchemas == null || structuredSchemas.isEmpty()
                ? DEFAULT_STRUCTURED_SCHEMAS : validatedSchemas(structuredSchemas);
    }

    private static Map<String, String> validatedSchemas(Map<String, String> source) {
        source.forEach((name, promptName) -> {
            if (name == null || promptName == null || !SCHEMA_NAME.matcher(name).matches() || !PROMPT_NAME.matcher(promptName).matches()) {
                throw new IllegalArgumentException("ghostbridge.ai.web.structured-schemas contains an invalid entry");
            }
        });
        return Map.copyOf(source);
    }

    /** Per-requester fixed one-minute rate window, concurrency cap and the bound on tracked requesters (memory). */
    public record RequesterLimit(
            @DefaultValue("20") @Min(1) @Max(100000) int requestsPerMinute,
            @DefaultValue("2") @Min(1) @Max(256) int maxConcurrent,
            @DefaultValue("10000") @Min(100) @Max(1000000) int maxTrackedRequesters) {
    }
}
