package com.ghostbridge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Answers a successful login with the signed-in user (JSON) and clears that user's failure count. */
public class AppLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final ObjectMapper mapper;
    private final LoginAttemptLimiter limiter;
    private final Duration sessionTimeout;

    /** @param sessionTimeout the configured idle lifetime of a signed-in session ({@code server.servlet.session.timeout}) */
    public AppLoginSuccessHandler(ObjectMapper mapper, LoginAttemptLimiter limiter, Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        this.mapper = mapper;
        this.limiter = limiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        limiter.reset(LoginKeys.user(request));
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval((int) sessionTimeout.toSeconds());
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        mapper.writeValue(response.getOutputStream(), AuthResponses.me(authentication));
    }
}
