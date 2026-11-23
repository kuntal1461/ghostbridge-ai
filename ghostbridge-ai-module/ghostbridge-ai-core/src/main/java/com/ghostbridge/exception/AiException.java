package com.ghostbridge.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * The single AI failure type. The message is returned to API callers and must be client-safe:
 * never prompt text, model output, raw provider messages, credentials or infrastructure details.
 * Provider detail belongs in the {@code cause}, which is never serialized.
 */
public class AiException extends GhostBridgeException {
    private final AiErrorReason reason;
    private final transient Duration retryAfter;

    public AiException(AiErrorReason reason, String message) {
        this(reason, message, (Duration) null);
    }

    /**
     * @param retryAfter when the caller may retry, only if the source of the failure actually knows it (for example
     *                   the end of a rate-limit window); null when unknown. A non-positive value is treated as unknown.
     */
    public AiException(AiErrorReason reason, String message, Duration retryAfter) {
        super(reason.errorCode(), message);
        this.reason = reason;
        this.retryAfter = normalize(retryAfter);
    }

    public AiException(AiErrorReason reason, String message, Throwable cause) {
        super(reason.errorCode(), message, cause);
        this.reason = reason;
        this.retryAfter = null;
    }

    private static Duration normalize(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? null : value;
    }

    /** The known time until a retry can succeed; empty when unknown (callers must not invent a value). */
    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** The machine-readable cause; the message itself is already client-safe. */
    public AiErrorReason getReason() {
        return reason;
    }

    /** True when the cause is a transient provider failure that is safe to retry. */
    public boolean isRetryable() {
        return reason.retryable();
    }
}
