package com.ghostbridge.security;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Returns 403 as ProblemDetail JSON when the caller is authenticated but not allowed. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {
    private final ProblemResponseWriter writer;

    public ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writer.write(response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access is denied");
    }
}
