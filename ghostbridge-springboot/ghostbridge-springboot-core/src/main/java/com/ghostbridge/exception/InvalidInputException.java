package com.ghostbridge.exception;

/** Caller-supplied input (upload, XML, parameters) was rejected (HTTP 400). */
public class InvalidInputException extends GhostBridgeException {
    public InvalidInputException(String message) {
        super(ErrorCode.INVALID_INPUT, message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(ErrorCode.INVALID_INPUT, message, cause);
    }
}
