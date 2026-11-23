package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

/**
 * The parts of a chat-completions response the core relies on, extracted defensively from the provider JSON.
 * Anything unexpected is an invalid model response; the provider payload is never echoed in an error.
 */
record OllamaChatResponseVO(String text, Integer inputTokens, Integer outputTokens) {

    static OllamaChatResponseVO from(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw invalid();
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (!content.isTextual()) {
            throw invalid();
        }
        JsonNode usage = root.path("usage");
        return new OllamaChatResponseVO(content.asText(), tokens(usage.path("prompt_tokens")), tokens(usage.path("completion_tokens")));
    }

    /** Only non-negative integers count as reported usage; anything else is treated as not reported. */
    private static Integer tokens(JsonNode node) {
        return node.isInt() && node.intValue() >= 0 ? node.intValue() : null;
    }

    private static AiException invalid() {
        return new AiException(AiErrorReason.INVALID_MODEL_RESPONSE, "AI provider returned an unexpected response");
    }
}
