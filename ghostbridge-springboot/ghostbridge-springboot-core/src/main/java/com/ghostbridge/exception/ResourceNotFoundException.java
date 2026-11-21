package com.ghostbridge.exception;

/** A requested resource does not exist (HTTP 404). */
public class ResourceNotFoundException extends GhostBridgeException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
