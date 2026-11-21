package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validated AI configuration bound from {@code ghostbridge.ai.*}. No region, model or credential has a default:
 * an unset provider means AI is disabled (there is no silent fallback to the mock provider).
 * Credentials are never properties; providers read them from their own secure source.
 *
 * <p>Semantics worth knowing: {@code timeout} applies <em>per provider attempt</em>, so one request can hold a
 * concurrency slot for up to {@code retry.max-attempts × timeout} plus backoff. {@code redaction-patterns} are
 * operator-supplied regular expressions applied to untrusted input (already bounded by {@code max-input-length});
 * avoid patterns prone to catastrophic backtracking. The token budget is enforced before each call and recorded after
 * it, so concurrent requests can overshoot it by roughly {@code rate-limit.max-concurrent × (input + max-output-tokens)}.
 */
@Validated
@ConfigurationProperties(prefix = GhostBridgeAiLiterals.PROPERTY_PREFIX)
public record AiProperties(
        String provider,
        String modelId,
        @DefaultValue List<String> approvedModelIds,
        @Valid @NotNull @DefaultValue Aws aws,
        @DefaultValue("1024") @Min(1) @Max(32768) int maxOutputTokens,
        @DefaultValue("0.2") @DecimalMin("0.0") @DecimalMax("1.0") double temperature,
        @DefaultValue("30s") @NotNull Duration timeout,
        @DefaultValue("8000") @Min(1) @Max(200000) int maxInputLength,
        @DefaultValue("65536") @Min(1) @Max(1048576) int maxResponseSize,
        @Valid @NotNull @DefaultValue Retry retry,
        @Valid @NotNull @DefaultValue RateLimit rateLimit,
        @Valid @NotNull @DefaultValue Budget budget,
        @Valid @NotNull @DefaultValue Pricing pricing,
        @Valid @NotNull @DefaultValue Mock mock,
        @DefaultValue List<String> redactionPatterns,
        @DefaultValue("200") @Min(1) @Max(10000) int historyMaxEntries) {

    public static final String DEFAULT_MOCK_MODEL = "mock-model";
    private static final Set<String> PROVIDERS = Set.of(GhostBridgeAiLiterals.PROVIDER_MOCK, GhostBridgeAiLiterals.PROVIDER_BEDROCK);

    public AiProperties {
        provider = normalizeProvider(provider);
        modelId = normalize(modelId);
        approvedModelIds = approvedModelIds == null ? List.of() : List.copyOf(approvedModelIds);
        redactionPatterns = redactionPatterns == null ? List.of() : List.copyOf(redactionPatterns);
        requirePositive(timeout, "ghostbridge.ai.timeout");
        if (timeout.compareTo(GhostBridgeAiLiterals.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("ghostbridge.ai.timeout must not exceed " + GhostBridgeAiLiterals.MAX_TIMEOUT);
        }
        validateProvider(provider, modelId, aws);
        if (GhostBridgeAiLiterals.PROVIDER_MOCK.equals(provider) && modelId == null) {
            modelId = DEFAULT_MOCK_MODEL;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeProvider(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }

    private static void validateProvider(String provider, String modelId, Aws aws) {
        if (provider == null) {
            return;
        }
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("ghostbridge.ai.provider must be one of " + PROVIDERS);
        }
        if (GhostBridgeAiLiterals.PROVIDER_BEDROCK.equals(provider)) {
            if (modelId == null) {
                throw new IllegalArgumentException("ghostbridge.ai.model-id is required when provider=bedrock");
            }
            if (aws == null || aws.region() == null) {
                throw new IllegalArgumentException("ghostbridge.ai.aws.region is required when provider=bedrock");
            }
        }
    }

    public boolean enabled() {
        return provider != null;
    }

    /** Provider-specific, non-secret AWS settings. Credentials come from the AWS default credential chain only. */
    public record Aws(String region) {
        public Aws {
            region = normalize(region);
        }
    }

    /** Bounded retry for transient provider failures. {@code maxAttempts} includes the first attempt. */
    public record Retry(
            @DefaultValue("3") @Min(1) @Max(5) int maxAttempts,
            @DefaultValue("200ms") @NotNull Duration initialBackoff,
            @DefaultValue("2s") @NotNull Duration maxBackoff) {
        public Retry {
            requirePositive(initialBackoff, "ghostbridge.ai.retry.initial-backoff");
            requirePositive(maxBackoff, "ghostbridge.ai.retry.max-backoff");
            if (initialBackoff.compareTo(maxBackoff) > 0) {
                throw new IllegalArgumentException("ghostbridge.ai.retry.initial-backoff must not exceed max-backoff");
            }
        }
    }

    /** Request-rate and concurrency limits; excess is rejected, never queued. These are global (secondary) limits. */
    public record RateLimit(
            @DefaultValue("60") @Min(1) @Max(100000) int requestsPerMinute,
            @DefaultValue("4") @Min(1) @Max(256) int maxConcurrent) {
    }

    /** Optional token budget over a fixed window; {@code maxTokens=0} disables it. Counts only provider-reported usage. */
    public record Budget(
            @DefaultValue("0") @Min(0) long maxTokens,
            @DefaultValue("1h") @NotNull Duration window) {
        public Budget {
            requirePositive(window, "ghostbridge.ai.budget.window");
        }

        public boolean enabled() {
            return maxTokens > 0;
        }
    }

    /** Optional price per 1000 tokens. Cost is estimated only when both prices are explicitly configured. */
    public record Pricing(BigDecimal inputPer1kTokens, BigDecimal outputPer1kTokens) {
        public Pricing {
            if (isNegative(inputPer1kTokens) || isNegative(outputPer1kTokens)) {
                throw new IllegalArgumentException("ghostbridge.ai.pricing values must not be negative");
            }
        }

        private static boolean isNegative(BigDecimal value) {
            return value != null && value.signum() < 0;
        }

        public boolean configured() {
            return inputPer1kTokens != null && outputPer1kTokens != null;
        }
    }

    /** Mock provider settings (non-secret). {@code response} overrides the generated deterministic output. */
    public record Mock(String response) {
    }
}
