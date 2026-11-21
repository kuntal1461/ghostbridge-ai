package com.ghostbridge.filter;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests whose declared Content-Length exceeds the configured limit with 413 before any parsing.
 * Requests without a Content-Length (chunked) are not measured here; the servlet container limits apply to them.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private final long maxBytes;
    private final ProblemResponseWriter writer;

    public RequestSizeLimitFilter(long maxBytes, ProblemResponseWriter writer) {
        this.maxBytes = maxBytes;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            writer.write(response, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.INVALID_INPUT, "Request body is too large");
            return;
        }
        chain.doFilter(request, response);
    }
}
