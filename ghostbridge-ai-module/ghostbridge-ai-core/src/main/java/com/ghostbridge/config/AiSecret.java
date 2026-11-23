package com.ghostbridge.config;

import com.fasterxml.jackson.annotation.JsonValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * A credential that cannot leak by accident. {@link #toString()}, JSON serialization and therefore any log line,
 * record dump, bind-failure text or configuration report show only a fixed mask. The value is available solely
 * through {@link #reveal()}, which must be called only where the credential is actually sent (never to log it).
 * Spring binds it from a plain string property, for example {@code ghostbridge.ai.ollama.api-key=${OLLAMA_API_KEY}}.
 */
public final class AiSecret {
    private static final String MASK = "****";
    private static final char HEADER_SAFE_MIN = 0x21;
    private static final char HEADER_SAFE_MAX = 0x7E;
    private static final AiSecret EMPTY = new AiSecret("");

    private final String value;

    public AiSecret(String value) {
        this.value = value == null ? "" : value.strip();
    }

    /** Used by Spring's property conversion. */
    public static AiSecret valueOf(String value) {
        return new AiSecret(value);
    }

    public static AiSecret empty() {
        return EMPTY;
    }

    /** Safe presence check; never exposes the value. */
    public boolean isPresent() {
        return !value.isEmpty();
    }

    /**
     * True when the value is non-empty printable ASCII without spaces, i.e. safe to place in an HTTP header. A value
     * with line breaks, control or invisible characters would make the JDK reject the header with an error message
     * that quotes the whole value, so such credentials are refused before they are ever used.
     */
    public boolean isHeaderSafe() {
        return !value.isEmpty() && value.chars().allMatch(c -> c >= HEADER_SAFE_MIN && c <= HEADER_SAFE_MAX);
    }

    /** The credential itself. Call only when building the outbound authorization header. */
    public String reveal() {
        return value;
    }

    @JsonValue
    @Override
    public String toString() {
        return MASK;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AiSecret that && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8), that.value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int hashCode() {
        return AiSecret.class.hashCode();
    }
}
