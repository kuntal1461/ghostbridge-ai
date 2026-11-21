package com.ghostbridge.exception;

/** Base type for all expected, client-safe GhostBridge failures. The message may be shown to API callers. */
public class GhostBridgeException extends RuntimeException {
    private final ErrorCode errorCode;

    public GhostBridgeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GhostBridgeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
