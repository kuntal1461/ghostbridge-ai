package com.ghostbridge.exception;

/** Stable, machine-readable error codes returned in API error responses. */
public enum ErrorCode {
    VALIDATION_ERROR,
    INVALID_INPUT,
    MALFORMED_REQUEST,
    AUTHENTICATION_REQUIRED,
    ACCESS_DENIED,
    RESOURCE_NOT_FOUND,
    BUSINESS_RULE_VIOLATION,
    INTERNAL_ERROR
}
