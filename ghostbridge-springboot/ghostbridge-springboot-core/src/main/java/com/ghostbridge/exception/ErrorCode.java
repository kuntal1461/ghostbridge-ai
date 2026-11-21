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
    INTERNAL_ERROR,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    PAYLOAD_TOO_LARGE,
    UPSTREAM_UNAVAILABLE,
    UPSTREAM_TIMEOUT,
    UPSTREAM_ERROR
}
