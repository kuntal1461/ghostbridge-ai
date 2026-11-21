package com.ghostbridge.util;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/** Builds the single error-response contract: RFC 9457 ProblemDetail plus code, correlationId, timestamp. */
public final class ProblemDetailFactory {
    public static final String PROP_CODE = "code";
    public static final String PROP_CORRELATION_ID = "correlationId";
    public static final String PROP_TIMESTAMP = "timestamp";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(HttpStatusCode status, ErrorCode code, String detail) {
        return enrich(ProblemDetail.forStatusAndDetail(status, detail), code);
    }

    public static ProblemDetail enrich(ProblemDetail problem, ErrorCode code) {
        problem.setProperty(PROP_CODE, code.name());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty(PROP_CORRELATION_ID, correlationId);
        }
        problem.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        return problem;
    }
}
