package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.service.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Readiness from the provider SPI only. A failing probe is reported as not ready, never as an error. */
public final class AiProviderReadiness {
    private static final Logger LOG = LoggerFactory.getLogger(AiProviderReadiness.class);

    private AiProviderReadiness() {
    }

    /** The provider overview of the configured provider: local readiness only, never a model call. */
    public static AiProviderOverview overview(AiProperties properties, List<AiProvider> providers) {
        List<String> capabilities = List.of(GhostBridgeAiLiterals.CAPABILITY_TEXT_GENERATION,
                GhostBridgeAiLiterals.CAPABILITY_STRUCTURED_OUTPUT);
        if (!properties.enabled()) {
            return new AiProviderOverview(false, GhostBridgeAiLiterals.PROVIDER_STATE_NOT_CONFIGURED, null, null, capabilities);
        }
        String state = isReady(providers, properties.provider())
                ? GhostBridgeAiLiterals.PROVIDER_STATE_AVAILABLE : GhostBridgeAiLiterals.PROVIDER_STATE_UNAVAILABLE;
        return new AiProviderOverview(true, state, properties.provider(), properties.modelId(), capabilities);
    }

    public static boolean isReady(List<AiProvider> providers, String providerName) {
        AiProvider provider = providers.stream().filter(candidate -> candidate.name().equals(providerName)).findFirst().orElse(null);
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
}
