package com.ghostbridge.service;

import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.dto.AiProviderStatus;
import com.ghostbridge.exception.AiException;

/** Adapter SPI implemented once per LLM provider. Only {@code ghostbridge-ai-core} knows providers. */
public interface AiProvider {

    /** Configuration name matched against {@code ghostbridge.ai.provider}. */
    String name();

    /**
     * Invokes the model. Implementations must honour {@link AiProviderCall#timeout()}, map every failure to an
     * {@link AiException} with a generic message (provider detail only as the cause), and never log content.
     */
    AiProviderResult invoke(AiProviderCall call);

    /** Current readiness; the detail text must be safe to show to authenticated operators (no credentials, no ARNs). */
    AiProviderStatus status();
}
