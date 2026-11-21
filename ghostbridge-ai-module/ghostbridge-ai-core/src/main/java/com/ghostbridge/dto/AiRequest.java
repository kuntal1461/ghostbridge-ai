package com.ghostbridge.dto;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral generation request. Contains no GhostBridge App, SOAP or migration types.
 * {@code variables} and {@code untrustedContent} are both treated as untrusted data.
 * Construction validates structure and throws {@link AiException} (never a raw runtime exception).
 *
 * @param promptName       name of a registered prompt template
 * @param promptVersion    template version, or null for the latest
 * @param variables        values for the template's declared variables (no null keys or values; bounded count and name length)
 * @param untrustedContent optional external content (documents, WSDL/XML text) passed as delimited data
 * @param modelId          optional model override; must be an approved model
 * @param options          optional generation overrides
 * @param requesterId      optional caller identity used only for metadata ownership. It must be derived by the
 *                         caller from a trusted authentication context, never from client-supplied input
 */
public record AiRequest(
        String promptName,
        Integer promptVersion,
        Map<String, String> variables,
        String untrustedContent,
        String modelId,
        AiGenerationOptions options,
        String requesterId) {

    public AiRequest {
        variables = copyVariables(variables);
        options = options == null ? AiGenerationOptions.defaults() : options;
        validateRequesterId(requesterId);
    }

    private static Map<String, String> copyVariables(Map<String, String> source) {
        if (source == null) {
            return Map.of();
        }
        if (source.size() > GhostBridgeAiLiterals.MAX_VARIABLES) {
            throw new AiException(AiErrorReason.INPUT_TOO_LARGE, "Too many template variables");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new AiException(AiErrorReason.INVALID_INPUT, "Template variables must not contain null names or values");
            }
            if (entry.getKey().length() > GhostBridgeAiLiterals.MAX_VARIABLE_NAME_LENGTH) {
                throw new AiException(AiErrorReason.INPUT_TOO_LARGE, "Template variable name is too long");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void validateRequesterId(String requesterId) {
        if (requesterId == null) {
            return;
        }
        if (requesterId.length() > GhostBridgeAiLiterals.MAX_REQUESTER_ID_LENGTH
                || GhostBridgeAiLiterals.REQUESTER_ID_FORBIDDEN.matcher(requesterId).find()) {
            throw new AiException(AiErrorReason.INVALID_INPUT, "Requester id is invalid");
        }
    }
}
