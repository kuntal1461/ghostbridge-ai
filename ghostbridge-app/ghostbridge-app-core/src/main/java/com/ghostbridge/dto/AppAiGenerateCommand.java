package com.ghostbridge.dto;

import com.ghostbridge.dto.AiGenerationOptions;

import java.util.Map;

/**
 * Application-level request to run one registered prompt. {@code requesterKey} is the trusted key derived from the
 * authentication context; it is never taken from client input.
 */
public record AppAiGenerateCommand(String requesterKey, String promptName, Integer promptVersion, Map<String, String> variables,
                                   String untrustedContent, String modelId, AiGenerationOptions options) {
}
