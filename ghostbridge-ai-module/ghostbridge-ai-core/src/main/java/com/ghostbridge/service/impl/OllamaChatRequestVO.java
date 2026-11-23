package com.ghostbridge.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.constants.GhostBridgeAiLiterals;

import java.util.List;

/**
 * Request body for {@code POST /v1/chat/completions}. Provider-internal wire contract built only from an
 * {@link AiProviderCall}; it never carries credentials (the credential travels only in an HTTP header).
 */
record OllamaChatRequestVO(
        String model,
        List<OllamaChatMessageVO> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens,
        boolean stream,
        @JsonProperty("response_format") @JsonInclude(JsonInclude.Include.NON_NULL) ResponseFormatVO responseFormat) {

    /** JSON-mode switch of the OpenAI-compatible API. */
    record ResponseFormatVO(String type) {
    }

    static OllamaChatRequestVO from(AiProviderCall call) {
        ResponseFormatVO format = call.jsonOutput() ? new ResponseFormatVO(GhostBridgeAiLiterals.RESPONSE_FORMAT_JSON_OBJECT) : null;
        return new OllamaChatRequestVO(call.model(),
                List.of(new OllamaChatMessageVO(GhostBridgeAiLiterals.CHAT_ROLE_SYSTEM, call.system()),
                        new OllamaChatMessageVO(GhostBridgeAiLiterals.CHAT_ROLE_USER, call.user())),
                call.temperature(), call.maxOutputTokens(), false, format);
    }
}
