package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AppAiStructuredCommand;
import com.ghostbridge.security.AiRequesterLimiter;
import com.ghostbridge.service.AppAiService;
import com.ghostbridge.config.ConditionalOnAiWebSecurity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * The service every caller in the App uses: applies the per-requester limit before delegating to the core use cases, so the
 * policy lives in the service layer and no controller can bypass it. The global limits stay active behind it in AI Core.
 */
@Service
@Primary
@ConditionalOnAiWebSecurity
public class RequesterLimitedAppAiService implements AppAiService {
    private final DefaultAppAiService delegate;
    private final AiRequesterLimiter limiter;

    public RequesterLimitedAppAiService(DefaultAppAiService delegate, AiRequesterLimiter limiter) {
        this.delegate = delegate;
        this.limiter = limiter;
    }

    @Override
    public AiResponse generate(AppAiGenerateCommand command) {
        try (AiLimiter.Permit ignored = limiter.acquire(command.requesterKey())) {
            return delegate.generate(command);
        }
    }

    @Override
    public AiResponse generateStructured(AppAiStructuredCommand command) {
        try (AiLimiter.Permit ignored = limiter.acquire(command.requesterKey())) {
            return delegate.generateStructured(command);
        }
    }

    @Override
    public AiProviderOverview providerStatus() {
        return delegate.providerStatus();
    }

    @Override
    public AiUsageSummary usage(String requesterKey) {
        return delegate.usage(requesterKey);
    }
}
