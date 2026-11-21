package com.ghostbridge.exception;

/**
 * The single AI failure type. The message is returned to API callers and must be client-safe:
 * never prompt text, model output, raw provider messages, credentials or infrastructure details.
 * Provider detail belongs in the {@code cause}, which is never serialized.
 */
public class AiException extends GhostBridgeException {
    private final AiErrorReason reason;

    public AiException(AiErrorReason reason, String message) {
        super(reason.errorCode(), message);
        this.reason = reason;
    }

    public AiException(AiErrorReason reason, String message, Throwable cause) {
        super(reason.errorCode(), message, cause);
        this.reason = reason;
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
