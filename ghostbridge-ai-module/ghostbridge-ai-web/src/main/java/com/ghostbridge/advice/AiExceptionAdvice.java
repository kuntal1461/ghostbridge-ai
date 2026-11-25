package com.ghostbridge.advice;

import com.ghostbridge.util.AiDurations;
import com.ghostbridge.config.ConditionalOnAiWebSecurity;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.filter.CorrelationIdFilter;
import com.ghostbridge.restcontroller.AiApiEndpoint;
import com.ghostbridge.util.ProblemDetailFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI-specific error mapping, scoped to controllers marked {@link AiApiEndpoint} and ordered ahead of the shared
 * {@code GlobalExceptionHandler}, which still handles everything else. It adds the documented {@code reason}
 * property and {@code Retry-After} (only when the failing component knows it), and reports size violations as 413.
 * Status and code come from the shared mapping; no message ever contains provider detail.
 */
@ConditionalOnAiWebSecurity
@RestControllerAdvice(annotations = AiApiEndpoint.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionAdvice {
    /** Client-facing text; kept here because client messages do not belong in the literals class. */
    private static final String MESSAGE_PROVIDER_UNAVAILABLE = "AI provider is unavailable";
    private static final Logger LOG = LoggerFactory.getLogger(AiExceptionAdvice.class);
    /** Bean Validation codes that mean "too large" rather than "wrong". */
    private static final Set<String> SIZE_CODES = Set.of("Size", "MaxUtf8Bytes");

    @ExceptionHandler(AiException.class)
    ResponseEntity<ProblemDetail> handleAi(AiException ex) {
        HttpStatus status = GlobalExceptionHandler.statusFor(ex.getErrorCode());
        String reason = publicReason(ex.getReason());
        if (status.is5xxServerError()) {
            LOG.warn("ai.request.failed reason={} correlationId={}", reason, MDC.get(CorrelationIdFilter.MDC_KEY));
        }
        ProblemDetail problem = ProblemDetailFactory.create(status, ex.getErrorCode(), clientMessage(ex));
        problem.setProperty(GhostBridgeAiLiterals.PROBLEM_PROPERTY_REASON, reason);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        ex.getRetryAfter().ifPresent(retry -> response.header(GhostBridgeAiLiterals.HEADER_RETRY_AFTER, seconds(retry)));
        return response.body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        boolean tooLarge = ex.getBindingResult().getFieldErrors().stream().anyMatch(e -> SIZE_CODES.contains(e.getCode()));
        HttpStatus status = tooLarge ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        ErrorCode code = tooLarge ? ErrorCode.PAYLOAD_TOO_LARGE : ErrorCode.VALIDATION_ERROR;
        ProblemDetail problem = ProblemDetailFactory.create(status, code, "Request validation failed");
        problem.setProperty(GhostBridgeAiLiterals.PROBLEM_PROPERTY_ERRORS, errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** Configuration, credential and interruption failures are reported alike so none of them is disclosed. */
    private static String publicReason(AiErrorReason reason) {
        return switch (reason) {
            case MISSING_CONFIGURATION, MISSING_CREDENTIALS, INTERRUPTED -> GhostBridgeAiLiterals.PUBLIC_REASON_PROVIDER_UNAVAILABLE;
            default -> reason.name();
        };
    }

    private static String clientMessage(AiException ex) {
        return GhostBridgeAiLiterals.PUBLIC_REASON_PROVIDER_UNAVAILABLE.equals(publicReason(ex.getReason()))
                ? MESSAGE_PROVIDER_UNAVAILABLE : ex.getMessage();
    }

    /** Whole seconds, rounded up and at least 1, so a client never retries early. */
    private static String seconds(Duration retryAfter) {
        return Long.toString(AiDurations.retryAfterSeconds(retryAfter));
    }
}
