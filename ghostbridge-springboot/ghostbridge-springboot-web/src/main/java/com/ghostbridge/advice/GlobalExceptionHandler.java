package com.ghostbridge.advice;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.exception.GhostBridgeException;
import com.ghostbridge.filter.CorrelationIdFilter;
import com.ghostbridge.util.ProblemDetailFactory;
import com.ghostbridge.util.SensitiveDataMasker;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * The shared global exception handler, ordered last so an application advice with a higher precedence
 * (for example {@code @Order(Ordered.HIGHEST_PRECEDENCE)}) always wins without ambiguity. Every response is a ProblemDetail with a stable {@code code},
 * a correlation ID and a timestamp. Internal details are never returned; they are logged (masked).
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GhostBridgeException.class)
    ResponseEntity<Object> handleGhostBridge(GhostBridgeException ex, WebRequest request) {
        HttpStatus status = statusFor(ex.getErrorCode());
        ProblemDetail problem = ProblemDetailFactory.create(status, ex.getErrorCode(), ex.getMessage());
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        ProblemDetail problem = validationProblem(errors);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access is denied");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        LOG.error("Unhandled {} correlationId={} message={}", ex.getClass().getName(),
                MDC.get(CorrelationIdFilter.MDC_KEY), SensitiveDataMasker.mask(ex.getMessage()));
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    /** Gives every response (including framework-generated 400/404/405/415, even body-less ones) the shared contract. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        Object payload = response == null ? null : response.getBody();
        if (payload instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetailFactory.PROP_CODE)) {
                ProblemDetailFactory.enrich(problem, defaultCode(ex, statusCode));
            }
            return response;
        }
        if (payload == null) {
            ProblemDetail problem = ProblemDetailFactory.create(statusCode, defaultCode(ex, statusCode), safeDetail(statusCode));
            return ResponseEntity.status(statusCode).headers(headers).body(problem);
        }
        return response;
    }

    private static String safeDetail(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? "Request failed" : resolved.getReasonPhrase();
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetailFactory.create(
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ErrorCode defaultCode(Exception ex, HttpStatusCode status) {
        if (ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_ERROR;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        return status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR, INVALID_INPUT, MALFORMED_REQUEST -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case BUSINESS_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED, QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UPSTREAM_ERROR -> HttpStatus.BAD_GATEWAY;
            case UPSTREAM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
    }
}
