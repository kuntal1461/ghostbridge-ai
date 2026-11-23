package com.ghostbridge.service;

import com.ghostbridge.requestvo.AiGenerateRequestVO;
import com.ghostbridge.requestvo.AiStructuredGenerateRequestVO;
import com.ghostbridge.responsevo.AiGenerateResponseVO;
import com.ghostbridge.responsevo.AiProviderStatusResponseVO;
import com.ghostbridge.responsevo.AiStructuredGenerateResponseVO;
import com.ghostbridge.responsevo.AiUsageResponseVO;

/** Use cases behind the AI REST API. The requester key is the trusted key from the authentication context. */
public interface AiApiService {

    /** @throws com.ghostbridge.exception.AiException on limit, input, provider or output failures */
    AiGenerateResponseVO generate(String requesterKey, AiGenerateRequestVO request);

    /** @throws com.ghostbridge.exception.AiException INVALID_INPUT for an unapproved schema; UPSTREAM failures otherwise */
    AiStructuredGenerateResponseVO generateStructured(String requesterKey, AiStructuredGenerateRequestVO request);

    /** Local readiness only; never performs a model call. */
    AiProviderStatusResponseVO providerStatus();

    /** Only the given requester's own usage. */
    AiUsageResponseVO usage(String requesterKey);
}
