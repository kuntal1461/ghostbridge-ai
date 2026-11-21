package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiGenerationOptions;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiRenderedPrompt;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsage;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiPromptService;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiStructuredOutputService;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates one AI request: validate, resolve provider and model, render the prompt, apply options, invoke the
 * provider under the execution guard, check limits, validate structured output, and record metadata.
 * Independent of any GhostBridge App workflow.
 */
public class DefaultAiCoordinator implements AiClient {
    private final AiProperties properties;
    private final Map<String, AiProvider> providers = new HashMap<>();
    private final AiPromptService prompts;
    private final AiStructuredOutputService structuredOutput;
    private final AiExecutionGuard guard;
    private final AiRequestTelemetry telemetry;
    private final Clock clock;

    public DefaultAiCoordinator(AiProperties properties, List<AiProvider> providerBeans, AiPromptService prompts,
                                AiStructuredOutputService structuredOutput, AiExecutionGuard guard,
                                AiRequestTelemetry telemetry, Clock clock) {
        this.properties = properties;
        for (AiProvider provider : providerBeans) {
            if (providers.putIfAbsent(provider.name(), provider) != null) {
                throw new IllegalStateException("Duplicate AI provider name: " + provider.name());
            }
        }
        this.prompts = prompts;
        this.structuredOutput = structuredOutput;
        this.guard = guard;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    /** {@inheritDoc} Every failure is an {@link AiException}; unexpected runtime errors are wrapped as PROVIDER_FAILURE. */
    @Override
    public AiResponse generate(AiRequest request) {
        if (request == null) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "AI request is required");
        }
        long startNanos = System.nanoTime();
        Trace trace = new Trace(UUID.randomUUID().toString(), request, prompts.promptNames());
        try {
            AiResponse response = process(request, trace, startNanos);
            telemetry.record(trace.metadata(AiRequestStatus.SUCCESS, null, response.usage(), elapsedMillis(startNanos), clock));
            return response;
        } catch (AiException e) {
            telemetry.record(trace.metadata(AiRequestStatus.FAILURE, e.getReason().name(), AiUsage.unknown(),
                    elapsedMillis(startNanos), clock));
            throw e;
        } catch (RuntimeException e) {
            // SPI contract violation or unexpected failure: record it and surface a generic, typed error (cause kept, never serialized).
            AiException wrapped = new AiException(AiErrorReason.PROVIDER_FAILURE, "AI request failed", e);
            telemetry.record(trace.metadata(AiRequestStatus.FAILURE, wrapped.getReason().name(), AiUsage.unknown(),
                    elapsedMillis(startNanos), clock));
            throw wrapped;
        }
    }

    private AiResponse process(AiRequest request, Trace trace, long startNanos) {
        validate(request);
        AiProvider provider = resolveProvider();
        trace.provider = provider.name();
        trace.model = resolveModel(request);
        AiRenderedPrompt prompt = prompts.render(request.promptName(), request.promptVersion(),
                request.variables(), request.untrustedContent());
        trace.promptVersion = prompt.version();
        AiProviderCall call = buildCall(trace.model, prompt, request.options());
        AiProviderResult result = guard.execute(() -> provider.invoke(call));
        String text = checkedText(result);
        JsonNode structured = prompt.schemaName() == null ? null : structuredOutput.parseAndValidate(text, prompt.schemaName());
        // With a schema, only the canonical validated JSON is returned; the raw model text is never exposed alongside it.
        String responseText = structured == null ? text : structured.toString();
        return new AiResponse(trace.requestId, trace.provider, trace.model, responseText, structured,
                withCost(result.usage()), elapsedMillis(startNanos));
    }

    private void validate(AiRequest request) {
        if (request.promptName() == null || request.promptName().isBlank()) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Prompt name is required");
        }
        long inputLength = request.variables().values().stream().mapToLong(String::length).sum()
                + (request.untrustedContent() == null ? 0 : request.untrustedContent().length());
        if (inputLength > properties.maxInputLength()) {
            throw new AiException(AiErrorReason.INPUT_TOO_LARGE, "AI input exceeds the configured maximum length");
        }
        validateOptions(request.options());
    }

    private void validateOptions(AiGenerationOptions options) {
        Integer maxTokens = options.maxOutputTokens();
        if (maxTokens != null && maxTokens < 1) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "maxOutputTokens must be positive");
        }
        if (maxTokens != null && maxTokens > properties.maxOutputTokens()) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "maxOutputTokens exceeds the configured maximum");
        }
        Double temperature = options.temperature();
        if (temperature != null && (temperature < GhostBridgeAiLiterals.MIN_TEMPERATURE || temperature > GhostBridgeAiLiterals.MAX_TEMPERATURE)) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "temperature must be between 0 and 1");
        }
    }

    private AiProvider resolveProvider() {
        AiProvider provider = properties.enabled() ? providers.get(properties.provider()) : null;
        if (provider == null) {
            throw new AiException(AiErrorReason.MISSING_CONFIGURATION, "AI provider is not configured");
        }
        return provider;
    }

    private String resolveModel(AiRequest request) {
        String configured = properties.modelId();
        if (configured == null) { // defensive: AiProperties guarantees a model for every enabled provider
            throw new AiException(AiErrorReason.MISSING_CONFIGURATION, "AI model is not configured");
        }
        String requested = request.modelId();
        if (requested == null || requested.isBlank() || requested.equals(configured)) {
            return configured;
        }
        if (!properties.approvedModelIds().contains(requested)) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Model is not approved");
        }
        return requested;
    }

    private AiProviderCall buildCall(String model, AiRenderedPrompt prompt, AiGenerationOptions options) {
        int maxTokens = options.maxOutputTokens() == null ? properties.maxOutputTokens() : options.maxOutputTokens();
        double temperature = options.temperature() == null ? properties.temperature() : options.temperature();
        return new AiProviderCall(model, prompt.system(), prompt.user(), maxTokens, temperature,
                properties.timeout(), prompt.schemaName() != null);
    }

    private String checkedText(AiProviderResult result) {
        String text = result.text();
        if (text == null || text.isBlank()) {
            throw new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, "Model returned an empty response");
        }
        if (text.length() > properties.maxResponseSize()) {
            throw new AiException(AiErrorReason.RESPONSE_TOO_LARGE, "Model response exceeds the configured maximum size");
        }
        return text;
    }

    /** Cost is estimated only from explicitly configured pricing and provider-reported tokens. */
    private AiUsage withCost(AiUsage usage) {
        AiProperties.Pricing pricing = properties.pricing();
        if (!pricing.configured() || usage.inputTokens() == null || usage.outputTokens() == null) {
            return usage;
        }
        BigDecimal input = pricing.inputPer1kTokens().multiply(BigDecimal.valueOf(usage.inputTokens()));
        BigDecimal output = pricing.outputPer1kTokens().multiply(BigDecimal.valueOf(usage.outputTokens()));
        return usage.withEstimatedCost(input.add(output).divide(GhostBridgeAiLiterals.TOKENS_PER_PRICE_UNIT, GhostBridgeAiLiterals.COST_SCALE, RoundingMode.HALF_UP));
    }

    /** Core must not trust ambient MDC text: anything not matching the shared correlation-id rule is dropped. */
    private static String validCorrelationId(String candidate) {
        return candidate != null && GhostBridgeAiLiterals.VALID_CORRELATION_ID.matcher(candidate).matches() ? candidate : null;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /** Mutable per-request facts gathered while processing, used to build metadata for success and failure alike. */
    private static final class Trace {
        private final String requestId;
        private final AiRequest request;
        private final String promptName;
        private String provider;
        private String model;
        private Integer promptVersion;

        private Trace(String requestId, AiRequest request, Set<String> knownPrompts) {
            this.requestId = requestId;
            this.request = request;
            this.promptName = request.promptName() != null && knownPrompts.contains(request.promptName())
                    ? request.promptName() : GhostBridgeAiLiterals.UNKNOWN_PROMPT;
            this.promptVersion = request.promptVersion();
        }

        private AiRequestMetadata metadata(AiRequestStatus status, String reason, AiUsage usage, long durationMs, Clock clock) {
            return new AiRequestMetadata(requestId, validCorrelationId(MDC.get(GhostBridgeAiLiterals.CORRELATION_MDC_KEY)), request.requesterId(), provider, model,
                    promptName, promptVersion, status, reason, durationMs, usage, clock.instant());
        }
    }
}
