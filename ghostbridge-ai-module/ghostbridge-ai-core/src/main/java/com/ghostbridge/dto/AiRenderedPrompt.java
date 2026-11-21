package com.ghostbridge.dto;

/**
 * A prompt ready for a provider. {@code system} holds only trusted template text; {@code user} holds the
 * rendered template plus delimited untrusted content. {@code schemaName} is non-null when JSON output is required.
 */
public record AiRenderedPrompt(String promptName, int version, String system, String user, String schemaName) {
}
