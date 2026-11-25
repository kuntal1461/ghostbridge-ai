package com.ghostbridge.service.impl;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import java.util.Map;

/** The structured-output rules every entry point applies the same way: an approved schema, and a structured answer. */
public final class AiStructuredOutputs {
    private AiStructuredOutputs() {
    }

    /** The prompt behind an approved schema name. @throws AiException INVALID_INPUT when the schema is not approved */
    public static String approvedPrompt(Map<String, String> approvedSchemas, String schemaName) {
        String promptName = approvedSchemas.get(schemaName);
        if (promptName == null) {
            throw new AiException(AiErrorReason.INVALID_INPUT, GhostBridgeAiLiterals.MESSAGE_SCHEMA_NOT_APPROVED);
        }
        return promptName;
    }

    /** @throws AiException OUTPUT_VALIDATION_FAILED when the model produced no structured output */
    public static AiResponse requireStructured(AiResponse response) {
        if (response.structured() == null) {
            throw new AiException(AiErrorReason.OUTPUT_VALIDATION_FAILED, GhostBridgeAiLiterals.MESSAGE_STRUCTURED_MISSING);
        }
        return response;
    }
}
