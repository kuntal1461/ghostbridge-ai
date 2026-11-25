package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AppAiProperties;
import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AppAiStructuredCommand;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;
import com.ghostbridge.service.AppAiService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the AI use cases of the App on top of {@link AiClient}: the provider-neutral coordinator that applies the
 * global limits, model approval, schema validation, retries and telemetry. No provider class is referenced, and no
 * inference logic is duplicated here.
 */
@Service
public class DefaultAppAiService implements AppAiService {
    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final AppAiProperties appProperties;
    private final AiRequestRegistry registry;
    private final List<AiProvider> providers;

    public DefaultAppAiService(AiClient aiClient, AiProperties aiProperties, AppAiProperties appProperties,
                               AiRequestRegistry registry, List<AiProvider> providers) {
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
        this.appProperties = appProperties;
        this.registry = registry;
        this.providers = List.copyOf(providers);
    }

    @Override
    public AiResponse generate(AppAiGenerateCommand command) {
        return aiClient.generate(new AiRequest(command.promptName(), command.promptVersion(), command.variables(),
                command.untrustedContent(), command.modelId(), command.options(), command.requesterKey()));
    }

    @Override
    public AiResponse generateStructured(AppAiStructuredCommand command) {
        String promptName = AiStructuredOutputs.approvedPrompt(appProperties.structuredSchemas(), command.schemaName());
        return AiStructuredOutputs.requireStructured(aiClient.generate(new AiRequest(promptName, null, command.variables(),
                command.untrustedContent(), null, command.options(), command.requesterKey())));
    }

    @Override
    public AiProviderOverview providerStatus() {
        return AiProviderReadiness.overview(aiProperties, providers);
    }

    @Override
    public AiUsageSummary usage(String requesterKey) {
        return AiUsageSummarizer.summarize(registry.recent(aiProperties.historyMaxEntries()), requesterKey);
    }
}
