package com.ghostbridge.exception;

/** A business rule was violated (HTTP 422). */
public class BusinessException extends GhostBridgeException {
    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
