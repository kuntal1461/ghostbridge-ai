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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validated AI configuration bound from {@code ghostbridge.ai.*}. No region, model or credential has a default:
 * an unset provider means AI is disabled (there is no silent fallback to the mock provider).
 * Credentials are masked {@link AiSecret}s (or read by the provider from its own secure source), never plain strings.
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
        @Valid @NotNull @DefaultValue Ollama ollama,
        @DefaultValue List<String> redactionPatterns,
        @DefaultValue("200") @Min(1) @Max(10000) int historyMaxEntries) {

    public static final String DEFAULT_MOCK_MODEL = "mock-model";
    private static final Pattern MODEL_ID = Pattern.compile(GhostBridgeAiLiterals.REGEX_MODEL_ID);
    private static final Set<String> PROVIDERS = Set.of(GhostBridgeAiLiterals.PROVIDER_MOCK,
            GhostBridgeAiLiterals.PROVIDER_BEDROCK, GhostBridgeAiLiterals.PROVIDER_OLLAMA);

    public AiProperties {
        provider = normalizeProvider(provider);
        modelId = normalize(modelId);
        approvedModelIds = approvedModelIds == null ? List.of() : List.copyOf(approvedModelIds);
        redactionPatterns = redactionPatterns == null ? List.of() : List.copyOf(redactionPatterns);
        requireTimeout(timeout, GhostBridgeAiLiterals.PROPERTY_PREFIX + ".timeout");
        requireOllamaSelection(provider, ollama);
        modelId = resolveModelId(provider, modelId, ollama);
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

    private static void requireTimeout(Duration value, String name) {
        requirePositive(value, name);
        if (value.compareTo(GhostBridgeAiLiterals.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(name + " must not exceed " + GhostBridgeAiLiterals.MAX_TIMEOUT);
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }

    /** The Ollama mode is never guessed: an undetermined mode fails startup instead of defaulting to the paid cloud. */
    private static void requireOllamaSelection(String provider, Ollama ollama) {
        if (GhostBridgeAiLiterals.PROVIDER_OLLAMA.equals(provider) && ollama != null && !ollama.isSelected()) {
            throw new IllegalArgumentException(Ollama.PREFIX
                    + "local-enabled must be set explicitly: true for a local Ollama, false for Ollama Cloud (paid)");
        }
    }

    /** For the Ollama provider the selected mode's model wins; a different legacy {@code model-id} is a conflict. */
    private static String resolveModelId(String provider, String modelId, Ollama ollama) {
        if (!GhostBridgeAiLiterals.PROVIDER_OLLAMA.equals(provider) || ollama == null) {
            return modelId;
        }
        String selected = ollama.selectedModel();
        if (selected != null && modelId != null && !modelId.equals(selected)) {
            throw new IllegalArgumentException("ghostbridge.ai.model-id conflicts with " + ollama.selectedModelProperty());
        }
        return validModel(selected != null ? selected : modelId, GhostBridgeAiLiterals.PROPERTY_PREFIX + ".model-id");
    }

    private static void validateProvider(String provider, String modelId, Aws aws) {
        if (provider == null) {
            return;
        }
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("ghostbridge.ai.provider must be one of " + PROVIDERS);
        }
        if (GhostBridgeAiLiterals.PROVIDER_OLLAMA.equals(provider) && modelId == null) {
            throw new IllegalArgumentException("ghostbridge.ai.model-id or the selected ghostbridge.ai.ollama.(local|cloud).model is required when provider=ollama");
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

    /**
     * Ollama settings, selected centrally by {@code local-enabled}: {@code true} uses a self-hosted Ollama ({@code local.*},
     * loopback or private network, no credential), {@code false} uses Ollama Cloud ({@code cloud.*}, https, credential
     * required, paid). There is never a fallback from one to the other, and a paid cloud request is never the result of a
     * missing or misspelled setting: when {@code provider=ollama} the mode must be unambiguous. It is taken, in order, from
     * {@code local-enabled} or the legacy {@code mode}; failing both, it is inferred only as <em>local</em>, and only when
     * {@code local.*} settings exist and no cloud setting does. Cloud therefore always needs an explicit
     * {@code local-enabled=false} (or {@code mode=cloud}); anything undetermined fails startup.
     *
     * <p>Compatibility: the earlier flat keys {@code base-url}, {@code api-key}, {@code allowed-hosts} and {@code mode}
     * remain accepted as aliases for the cloud settings; a pair that is set to different values fails startup. The
     * credential ({@code cloud.api-key}, normally mapped from the {@code OLLAMA_API_KEY} environment variable) is an
     * {@link AiSecret}: masked in every string, JSON or log representation and never part of committed configuration.
     * Redirects are never followed by the provider. The flat {@code baseUrl}/{@code apiKey} components are derived: after
     * construction they equal the values in {@code cloud}, and {@code mode} always mirrors {@code localEnabled}.
     */
    public record Ollama(
            Boolean localEnabled,
            String mode,
            String baseUrl,
            @DefaultValue("ollama.com") List<String> allowedHosts,
            AiSecret apiKey,
            @Valid @NotNull @DefaultValue Local local,
            @Valid @NotNull @DefaultValue Cloud cloud,
            @DefaultValue("5s") @NotNull Duration connectTimeout,
            @DefaultValue("262144") @Min(1024) @Max(8388608) int maxRequestBytes,
            @DefaultValue("1048576") @Min(1024) @Max(16777216) int maxResponseBytes) {

        private static final int MIN_BODY_BYTES = 1024;
        private static final String DEFAULT_CLOUD_URL =
                GhostBridgeAiLiterals.URI_SCHEME_HTTPS + "://" + GhostBridgeAiLiterals.OLLAMA_CLOUD_DOMAIN;
        private static final String PREFIX = GhostBridgeAiLiterals.PROPERTY_PREFIX + ".ollama.";
        /** A plain host name or an IPv6 literal in brackets: no wildcard, trailing dot, port or path. */
        private static final Pattern ALLOWED_HOST = Pattern.compile("[a-z0-9]([a-z0-9.-]*[a-z0-9])?|\\[[0-9a-f:.]+]");

        public Ollama {
            // Boxed on purpose: a blank placeholder (for example GHOSTBRIDGE_AI_OLLAMA_LOCAL_ENABLED=) cannot bind to a
            // primitive boolean, and "not set" must stay distinguishable from "false". After construction the value is
            // null only when the mode could not be determined; AiProperties rejects that whenever provider=ollama.
            allowedHosts = lowerCase(allowedHosts);
            local = local == null ? new Local(null, null, null) : local;
            cloud = cloud == null ? new Cloud(null, null, null) : cloud;
            localEnabled = resolveSelection(localEnabled, mode, local, cloudSettingsPresent(baseUrl, apiKey, cloud));
            mode = modeName(localEnabled);
            if (baseUrl != null && baseUrl.isBlank()) {
                throw new IllegalArgumentException(PREFIX + "base-url must not be blank");
            }
            String resolvedUrl = pick(PREFIX + "base-url", PREFIX + "cloud.base-url", normalize(baseUrl), cloud.baseUrl());
            baseUrl = resolvedUrl == null ? DEFAULT_CLOUD_URL : resolvedUrl;
            apiKey = pickKey(apiKey, cloud.apiKey());
            cloud = new Cloud(baseUrl, cloud.model(), apiKey);
            requireTimeout(connectTimeout, PREFIX + "connect-timeout");
            validateDestinations(baseUrl, allowedHosts, local, Boolean.TRUE.equals(localEnabled));
            requireByteLimits(maxRequestBytes, maxResponseBytes);
        }

        private static boolean cloudSettingsPresent(String legacyBaseUrl, AiSecret legacyKey, Cloud cloud) {
            return normalize(legacyBaseUrl) != null || isPresent(legacyKey)
                    || cloud.baseUrl() != null || cloud.model() != null || isPresent(cloud.apiKey());
        }

        private static boolean isPresent(AiSecret secret) {
            return secret != null && secret.isPresent();
        }

        private static void requireByteLimits(int maxRequestBytes, int maxResponseBytes) {
            if (maxRequestBytes < MIN_BODY_BYTES || maxResponseBytes < MIN_BODY_BYTES) {
                throw new IllegalArgumentException(PREFIX + "byte limits must be at least " + MIN_BODY_BYTES);
            }
        }

        /**
         * Both destinations are validated whenever they are present, not only the selected one, so a bad value fails at
         * startup instead of when the flag is flipped later. The default cloud destination is always valid.
         */
        private static void validateDestinations(String cloudUrl, List<String> cloudHosts, Local local, boolean localEnabled) {
            OllamaEndpointValidator.validateCloud(cloudUrl, cloudHosts);
            if (local.baseUrl() != null) {
                OllamaEndpointValidator.validateLocal(local.baseUrl(), local.allowedHosts());
            }
            if (localEnabled && (local.baseUrl() == null || local.model() == null)) {
                throw new IllegalArgumentException(PREFIX + "local.base-url and " + PREFIX + "local.model are required when local-enabled=true");
            }
        }

        /** A copy with the credential replaced (used to apply the environment fallback); all other values are re-validated. */
        public Ollama withApiKey(AiSecret key) {
            return new Ollama(localEnabled, mode, baseUrl, allowedHosts, key,
                    local, new Cloud(cloud.baseUrl(), cloud.model(), key), connectTimeout, maxRequestBytes, maxResponseBytes);
        }

        /** True when the mode has been determined (explicitly or by the unambiguous inference). */
        public boolean isSelected() {
            return localEnabled != null;
        }

        /** True when the credential is needed (cloud mode); local mode never sends one. */
        public boolean credentialRequired() {
            return Boolean.FALSE.equals(localEnabled);
        }

        /** The model of the selected mode, or null when only the legacy {@code ghostbridge.ai.model-id} is set. */
        public String selectedModel() {
            return Boolean.TRUE.equals(localEnabled) ? local.model() : cloud.model();
        }

        /** Property name of the selected model setting, for messages. */
        public String selectedModelProperty() {
            return PREFIX + (Boolean.TRUE.equals(localEnabled) ? "local.model" : "cloud.model");
        }

        /** The validated chat-completions endpoint of the selected mode. Never contains credentials. */
        public URI chatCompletionsUri() {
            if (!isSelected()) {
                throw new IllegalStateException(PREFIX + "local-enabled is not set");
            }
            String selected = Boolean.TRUE.equals(localEnabled) ? local.baseUrl() : baseUrl;
            String base = selected.endsWith("/") ? selected.substring(0, selected.length() - 1) : selected;
            try {
                return new URI(base + GhostBridgeAiLiterals.OLLAMA_CHAT_COMPLETIONS_PATH);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("The configured Ollama base-url is not a valid URI"); // never echo the URL
            }
        }

        /**
         * Local, cloud or undetermined (null). An explicit flag wins; the legacy mode is an explicit choice as well; the
         * two must agree. Without either, only <em>local</em> can be inferred (local settings present, no cloud setting),
         * so a mistyped or missing flag falls into "undetermined" (rejected for provider=ollama) or local, and can never
         * produce a paid cloud call by itself.
         */
        private static Boolean resolveSelection(Boolean flag, String mode, Local local, boolean cloudConfigured) {
            Boolean fromMode = mode == null || mode.isBlank() ? null : parseMode(mode); // a blank placeholder counts as unset
            if (flag != null && fromMode != null && !flag.equals(fromMode)) {
                throw new IllegalArgumentException(PREFIX + "mode must match " + PREFIX + "local-enabled (local or cloud)");
            }
            if (flag != null) {
                return flag;
            }
            if (fromMode != null) {
                return fromMode;
            }
            boolean localConfigured = local.baseUrl() != null || local.model() != null;
            return localConfigured && !cloudConfigured ? Boolean.TRUE : null;
        }

        private static String modeName(Boolean local) {
            if (local == null) {
                return null;
            }
            return local ? GhostBridgeAiLiterals.OLLAMA_MODE_LOCAL : GhostBridgeAiLiterals.OLLAMA_MODE_CLOUD;
        }

        private static Boolean parseMode(String mode) {
            String normalized = mode.trim().toLowerCase(Locale.ROOT);
            if (GhostBridgeAiLiterals.OLLAMA_MODE_LOCAL.equals(normalized)) {
                return Boolean.TRUE;
            }
            if (GhostBridgeAiLiterals.OLLAMA_MODE_CLOUD.equals(normalized)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException(PREFIX + "mode must be local or cloud");
        }

        private static List<String> lowerCase(List<String> hosts) {
            if (hosts == null) {
                return List.of();
            }
            return hosts.stream().map(Ollama::allowedHost).toList();
        }

        private static String allowedHost(String host) {
            String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_HOST.matcher(normalized).matches()) {
                throw new IllegalArgumentException(PREFIX + "allowed-hosts entries must be plain host names or IP literals "
                        + "(no wildcard, trailing dot, port or path)");
            }
            return normalized;
        }

        private static String pick(String legacyName, String currentName, String legacy, String current) {
            if (legacy != null && current != null && !legacy.equals(current)) {
                throw new IllegalArgumentException(legacyName + " conflicts with " + currentName);
            }
            return current != null ? current : legacy;
        }

        private static AiSecret pickKey(AiSecret legacy, AiSecret current) {
            boolean legacyPresent = legacy != null && legacy.isPresent();
            boolean currentPresent = current != null && current.isPresent();
            if (legacyPresent && currentPresent && !legacy.equals(current)) {
                throw new IllegalArgumentException(PREFIX + "api-key conflicts with " + PREFIX + "cloud.api-key");
            }
            if (currentPresent) {
                return current;
            }
            return legacyPresent ? legacy : AiSecret.empty();
        }
    }

    /** Self-hosted Ollama. {@code allowedHosts} lists container or VPN host names the operator approves (http allowed). */
    public record Local(String baseUrl, String model, @DefaultValue List<String> allowedHosts) {
        public Local {
            baseUrl = normalize(baseUrl);
            model = validModel(model, Ollama.PREFIX + "local.model");
            allowedHosts = Ollama.lowerCase(allowedHosts);
        }
    }

    /** Ollama Cloud. Unset values resolve from the legacy flat keys or the defaults. */
    public record Cloud(String baseUrl, String model, AiSecret apiKey) {
        public Cloud {
            baseUrl = normalize(baseUrl);
            model = validModel(model, Ollama.PREFIX + "cloud.model");
        }
    }

    private static String validModel(String model, String property) {
        String normalized = normalize(model);
        if (normalized != null && !MODEL_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(property + " is not a valid model identifier");
        }
        return normalized;
    }
}
