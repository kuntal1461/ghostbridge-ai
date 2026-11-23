package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiWebProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiRequestStatus;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.mapper.AiApiMapper;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiRateLimitResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.service.AiApiService;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Applies the per-requester limit (G-M4-1) and delegates to the core {@link AiClient}, whose global limits remain
 * active behind it. Knows no provider class: only the {@link AiProvider} SPI is used, for readiness.
 */
public class DefaultAiApiService implements AiApiService {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAiApiService.class);

    private final AiClient aiClient;
    private final AiRequesterLimiter limiter;
    private final AiApiMapper mapper;
    private final AiProperties aiProperties;
    private final AiWebProperties webProperties;
    private final AiRequestRegistry registry;
    private final List<AiProvider> providers;

    public DefaultAiApiService(AiClient aiClient, AiRequesterLimiter limiter, AiApiMapper mapper, AiProperties aiProperties,
                               AiWebProperties webProperties, AiRequestRegistry registry, List<AiProvider> providers) {
        this.aiClient = aiClient;
        this.limiter = limiter;
        this.mapper = mapper;
        this.aiProperties = aiProperties;
        this.webProperties = webProperties;
        this.registry = registry;
        this.providers = List.copyOf(providers);
    }

    @Override
    public AiGenerateResponseVO generate(String requesterKey, AiGenerateRequestVO request) {
        try (AiLimiter.Permit ignored = limiter.acquire(requesterKey)) {
            return mapper.toGenerateResponse(aiClient.generate(mapper.toAiRequest(request, requesterKey)));
        }
    }

    @Override
    public AiStructuredGenerateResponseVO generateStructured(String requesterKey, AiStructuredGenerateRequestVO request) {
        try (AiLimiter.Permit ignored = limiter.acquire(requesterKey)) {
            String promptName = webProperties.structuredSchemas().get(request.schemaName());
            if (promptName == null) {
                throw new AiException(AiErrorReason.INVALID_INPUT, "Schema is not approved");
            }
            AiResponse response = aiClient.generate(mapper.toAiRequest(request, promptName, requesterKey));
            if (response.structured() == null) {
                throw new AiException(AiErrorReason.OUTPUT_VALIDATION_FAILED, "Model output was not valid structured output");
            }
            return mapper.toStructuredResponse(response, request.schemaName());
        }
    }

    @Override
    public AiProviderStatusResponseVO providerStatus() {
        List<String> capabilities = List.of(GhostBridgeAiLiterals.CAPABILITY_TEXT_GENERATION,
                GhostBridgeAiLiterals.CAPABILITY_STRUCTURED_OUTPUT);
        if (!aiProperties.enabled()) {
            return new AiProviderStatusResponseVO(false, GhostBridgeAiLiterals.PROVIDER_STATE_NOT_CONFIGURED, null, null, capabilities);
        }
        String state = isReady() ? GhostBridgeAiLiterals.PROVIDER_STATE_AVAILABLE : GhostBridgeAiLiterals.PROVIDER_STATE_UNAVAILABLE;
        return new AiProviderStatusResponseVO(true, state, aiProperties.provider(), aiProperties.modelId(), capabilities);
    }

    @Override
    public AiUsageResponseVO usage(String requesterKey) {
        int historyLimit = aiProperties.historyMaxEntries();
        UsageTotals totals = new UsageTotals();
        registry.recent(historyLimit).stream()
                .filter(metadata -> requesterKey.equals(metadata.requesterId()))
                .forEach(totals::add);
        AiRequesterLimiter.Status status = limiter.status(requesterKey);
        AiRateLimitResponseVO rateLimit = new AiRateLimitResponseVO(status.limit(), status.remaining(),
                (status.resetIn().toMillis() + 999) / 1000, status.maxConcurrent());
        return new AiUsageResponseVO(GhostBridgeAiLiterals.USAGE_SCOPE_RETAINED_HISTORY, historyLimit, totals.requests,
                totals.successes, totals.failures, totals.withUsage, totals.input, totals.output, totals.input + totals.output, rateLimit);
    }

    /** Readiness comes from the provider SPI only. A failing probe is reported as unavailable, never as an error. */
    private boolean isReady() {
        AiProvider provider = providers.stream().filter(candidate -> candidate.name().equals(aiProperties.provider()))
                .findFirst().orElse(null);
        if (provider == null) {
            return false;
        }
        try {
            AiProviderStatus status = provider.status();
            return status != null && status.ready();
        } catch (RuntimeException e) {
            LOG.warn("ai.provider.status.failed type={}", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Mutable accumulator for one caller's retained-history totals. */
    private static final class UsageTotals {
        private long requests;
        private long successes;
        private long failures;
        private long withUsage;
        private long input;
        private long output;

        private void add(AiRequestMetadata metadata) {
            requests++;
            if (metadata.status() == AiRequestStatus.SUCCESS) {
                successes++;
            } else {
                failures++;
            }
            if (metadata.usage() != null && metadata.usage().totalTokens().isPresent()) {
                withUsage++;
                input += metadata.usage().inputTokens() == null ? 0 : metadata.usage().inputTokens();
                output += metadata.usage().outputTokens() == null ? 0 : metadata.usage().outputTokens();
            }
        }
    }
}
