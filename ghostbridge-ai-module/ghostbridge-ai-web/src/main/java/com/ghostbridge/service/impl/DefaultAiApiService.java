package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiWebProperties;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.mapper.AiApiMapper;
import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.service.AiApiService;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;

import java.util.List;

/**
 * Applies the per-requester limit (G-M4-1) and delegates to the core {@link AiClient}, whose global limits remain
 * active behind it. Knows no provider class: only the {@link AiProvider} SPI is used, for readiness.
 */
public class DefaultAiApiService implements AiApiService {
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
            String promptName = AiStructuredOutputs.approvedPrompt(webProperties.structuredSchemas(), request.schemaName());
            AiResponse response = AiStructuredOutputs.requireStructured(aiClient.generate(mapper.toAiRequest(request, promptName, requesterKey)));
            return mapper.toStructuredResponse(response, request.schemaName());
        }
    }

    @Override
    public AiProviderStatusResponseVO providerStatus() {
        return mapper.toProviderStatus(AiProviderReadiness.overview(aiProperties, providers));
    }

    @Override
    public AiUsageResponseVO usage(String requesterKey) {
        int historyLimit = aiProperties.historyMaxEntries();
        AiUsageSummary totals = AiUsageSummarizer.summarize(registry.recent(historyLimit), requesterKey);
        return mapper.toUsageResponse(totals, historyLimit, limiter.status(requesterKey));
    }
}
