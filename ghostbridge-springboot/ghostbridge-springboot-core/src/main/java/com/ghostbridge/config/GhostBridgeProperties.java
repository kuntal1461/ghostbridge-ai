package com.ghostbridge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Shared, validated configuration contract bound from the {@code ghostbridge.*} namespace.
 * Applications supply environment-specific values; every default here is restrictive.
 */
@Validated
@ConfigurationProperties(prefix = "ghostbridge")
public record GhostBridgeProperties(
        @Valid @NotNull @DefaultValue Cors cors,
        @Valid @NotNull @DefaultValue Security security,
        @Valid @NotNull @DefaultValue HttpClient httpClient,
        @Valid @NotNull @DefaultValue Upload upload) {

    private static final String WILDCARD = "*";

    private static List<String> copyOf(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** CORS policy. No origin is allowed unless listed explicitly. */
    public record Cors(
            @DefaultValue List<String> allowedOrigins,
            @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) List<String> allowedMethods,
            @DefaultValue({"Authorization", "Content-Type", "X-Correlation-Id"}) List<String> allowedHeaders,
            @DefaultValue({"X-Correlation-Id"}) List<String> exposedHeaders,
            @DefaultValue("1h") @NotNull Duration maxAge,
            @DefaultValue("false") boolean allowCredentials) {

        public Cors {
            allowedOrigins = copyOf(allowedOrigins);
            allowedMethods = copyOf(allowedMethods);
            allowedHeaders = copyOf(allowedHeaders);
            exposedHeaders = copyOf(exposedHeaders);
            if (allowCredentials && (allowedOrigins.contains(WILDCARD) || allowedHeaders.contains(WILDCARD))) {
                throw new IllegalArgumentException(
                        "ghostbridge.cors: wildcard origins/headers cannot be combined with allow-credentials");
            }
        }
    }

    /** Spring Security and HTTP hardening. */
    public record Security(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean stateless,
            @DefaultValue List<String> publicPaths,
            @DefaultValue("default-src 'none'; frame-ancestors 'none'") @NotBlank String contentSecurityPolicy,
            @DefaultValue("no-referrer") @NotBlank String referrerPolicy,
            @DefaultValue("true") boolean hstsEnabled,
            @DefaultValue("365d") @NotNull Duration hstsMaxAge,
            @DefaultValue("ACTUATOR_ADMIN") @NotBlank String actuatorRole) {

        public Security {
            publicPaths = copyOf(publicPaths);
            if (publicPaths.stream().anyMatch(path -> "/**".equals(path) || "/*".equals(path) || WILDCARD.equals(path))) {
                throw new IllegalArgumentException("ghostbridge.security.public-paths must not open every path");
            }
        }
    }

    /** Outbound HTTP client conventions. */
    public record HttpClient(
            @DefaultValue("5s") @NotNull Duration connectTimeout,
            @DefaultValue("30s") @NotNull Duration readTimeout) {
    }

    /** Upload and request-body limits. */
    public record Upload(
            @DefaultValue("10MB") @NotNull DataSize maxFileSize,
            @DefaultValue("12MB") @NotNull DataSize maxRequestSize,
            @DefaultValue({"application/xml", "text/xml", "application/wsdl+xml"}) List<String> allowedContentTypes) {

        public Upload {
            allowedContentTypes = copyOf(allowedContentTypes);
        }
    }
}
