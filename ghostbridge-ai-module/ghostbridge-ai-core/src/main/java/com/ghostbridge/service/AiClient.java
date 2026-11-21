package com.ghostbridge.service;

import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiException;

/** Public, provider-neutral contract for AI generation. Applications depend on this interface only. */
public interface AiClient {

    /**
     * Runs one generation request through validation, prompt rendering, the configured provider and output checks.
     *
     * @param request the provider-neutral request; must not be null
     * @return the normalized response, including usage metadata when the provider reported it
     * @throws AiException with a client-safe message when the request is invalid, limited, or the provider fails;
     *                     the {@link com.ghostbridge.exception.AiErrorReason} identifies the cause
     */
    AiResponse generate(AiRequest request);
}
