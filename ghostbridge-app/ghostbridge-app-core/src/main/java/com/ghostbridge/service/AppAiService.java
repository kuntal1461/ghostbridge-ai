package com.ghostbridge.service;

import com.ghostbridge.dto.AiProviderOverview;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.dto.AiUsageSummary;
import com.ghostbridge.dto.AppAiGenerateCommand;
import com.ghostbridge.dto.AppAiStructuredCommand;

/** Application use cases for AI. Calls AI Core only (in process); knows no servlet, HTTP or frontend type. */
public interface AppAiService {

    /** @throws com.ghostbridge.exception.AiException on invalid input, limits, provider or output failures */
    AiResponse generate(AppAiGenerateCommand command);

    /** @throws com.ghostbridge.exception.AiException INVALID_INPUT for an unapproved schema */
    AiResponse generateStructured(AppAiStructuredCommand command);

    /** Local readiness only; never performs a model call. */
    AiProviderOverview providerStatus();

    /** Totals of the given requester only, from the bounded retained history. */
    AiUsageSummary usage(String requesterKey);
}
