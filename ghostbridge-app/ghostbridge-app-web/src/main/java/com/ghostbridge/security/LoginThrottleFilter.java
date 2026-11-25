package com.ghostbridge.security;

import com.ghostbridge.util.AiDurations;
import com.ghostbridge.config.AppSecurityProperties;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.constants.GhostBridgeAppLiterals;
import com.ghostbridge.exception.ErrorCode;
import com.ghostbridge.util.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/** Rejects login attempts from a blocked user name or address with 429 and a Retry-After, before any password check. */
public class LoginThrottleFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = GhostBridgeAppLiterals.AUTH_BASE_PATH + GhostBridgeAppLiterals.AUTH_PATH_LOGIN;
    private static final String MESSAGE = "Too many failed sign-in attempts. Try again later.";

    private static final RequestMatcher LOGIN = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_PATH);

    private final LoginAttemptLimiter limiter;
    private final AppSecurityProperties.LoginAttempts settings;
    private final ProblemResponseWriter writer;

    public LoginThrottleFilter(LoginAttemptLimiter limiter, AppSecurityProperties.LoginAttempts settings, ProblemResponseWriter writer) {
        this.limiter = limiter;
        this.settings = settings;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> blocked = LoginKeys.of(request).stream().map(key -> limiter.blockedFor(key, LoginKeys.threshold(key, settings)))
                .flatMap(Optional::stream).max(Duration::compareTo);
        if (blocked.isPresent()) {
            long seconds = AiDurations.retryAfterSeconds(blocked.get());
            response.setHeader(GhostBridgeAiLiterals.HEADER_RETRY_AFTER, Long.toString(seconds));
            writer.write(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
