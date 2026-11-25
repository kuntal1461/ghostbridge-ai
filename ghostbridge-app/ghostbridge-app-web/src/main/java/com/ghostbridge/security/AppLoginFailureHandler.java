package com.ghostbridge.security;

import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * Counts the failure against the user and the client address and answers with one generic 401, whatever the cause
 * (unknown user, wrong password, disabled account), so the response never reveals which user names exist.
 */
public class AppLoginFailureHandler implements AuthenticationFailureHandler {
    private static final String MESSAGE = "Invalid credentials";

    private final ProblemResponseWriter writer;
    private final LoginAttemptLimiter limiter;

    public AppLoginFailureHandler(ProblemResponseWriter writer, LoginAttemptLimiter limiter) {
        this.writer = writer;
        this.limiter = limiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        LoginKeys.of(request).forEach(limiter::recordFailure);
        writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, MESSAGE);
    }
}
