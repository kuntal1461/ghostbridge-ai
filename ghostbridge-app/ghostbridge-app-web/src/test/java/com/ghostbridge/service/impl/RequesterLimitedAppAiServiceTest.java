package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiWebProperties;
import com.ghostbridge.dto.AiGenerationOptions;
import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AppAiStructuredCommand;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.security.AiRequesterLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The per-requester limit is enforced by the service itself, so no caller of {@code AppAiService} can bypass it. */
class RequesterLimitedAppAiServiceTest {
    private static final Clock FIXED = Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:30Z"), java.time.ZoneOffset.UTC); // mid-window: no flake at a minute boundary
    private final DefaultAppAiService delegate = mock(DefaultAppAiService.class);
    private final AiRequesterLimiter limiter = new AiRequesterLimiter(new AiWebProperties.RequesterLimit(1, 1, 100), FIXED);
    private final RequesterLimitedAppAiService service = new RequesterLimitedAppAiService(delegate, limiter);

    private static AppAiGenerateCommand generate(String requester) {
        return new AppAiGenerateCommand(requester, "generic-structured", null, Map.of(), null, null, AiGenerationOptions.defaults());
    }

    @Test
    void theSecondRequestInTheWindowIsRejectedBeforeAnyModelCall() {
        service.generate(generate("alice"));
        assertThatThrownBy(() -> service.generate(generate("alice"))).isInstanceOfSatisfying(AiException.class,
                e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.RATE_LIMITED));
        verify(delegate, times(1)).generate(generate("alice"));
    }

    @Test
    void structuredRequestsShareTheSameLimitAndOtherRequestersAreUnaffected() {
        AppAiStructuredCommand structured = new AppAiStructuredCommand("alice", "generic-summary", Map.of(), null, AiGenerationOptions.defaults());
        service.generateStructured(structured);
        assertThatThrownBy(() -> service.generateStructured(structured)).isInstanceOf(AiException.class);
        service.generate(generate("bob"));
        verify(delegate, times(1)).generateStructured(structured);
        verify(delegate, never()).generate(generate("alice"));
    }

    @Test
    void thePermitIsReleasedWhenTheDelegateFails() {
        AiRequesterLimiter single = new AiRequesterLimiter(new AiWebProperties.RequesterLimit(100, 1, 100), FIXED);
        RequesterLimitedAppAiService limited = new RequesterLimitedAppAiService(delegate, single);
        when(delegate.generate(generate("carol"))).thenThrow(new AiException(AiErrorReason.PROVIDER_FAILURE, "boom")).thenReturn(null);
        assertThatThrownBy(() -> limited.generate(generate("carol"))).isInstanceOf(AiException.class);
        limited.generate(generate("carol")); // not rejected for concurrency: the first permit was closed
        verify(delegate, times(2)).generate(generate("carol"));
    }

    @Test
    void statusAndUsageAreNotLimitedAndPassThrough() {
        service.providerStatus();
        service.usage("alice");
        service.usage("alice");
        verify(delegate, times(1)).providerStatus();
        verify(delegate, times(2)).usage("alice");
    }
}
