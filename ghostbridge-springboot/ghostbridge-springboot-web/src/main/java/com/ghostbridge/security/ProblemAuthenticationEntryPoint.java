package com.ghostbridge.security;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** Returns 401 as ProblemDetail JSON when authentication is required. */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ProblemResponseWriter writer;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required");
    }
}
