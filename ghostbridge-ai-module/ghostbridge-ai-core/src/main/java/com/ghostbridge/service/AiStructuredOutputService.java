package com.ghostbridge.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Parses and schema-validates model output. Valid JSON alone is never trusted. */
public interface AiStructuredOutputService {

    /** @throws com.ghostbridge.exception.AiException INVALID_MODEL_RESPONSE or OUTPUT_VALIDATION_FAILED; never echoes content */
    JsonNode parseAndValidate(String modelOutput, String schemaName);

    /** Schema text used in structured-output instructions. */
    String schemaText(String schemaName);
}
