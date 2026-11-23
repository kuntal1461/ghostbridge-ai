package com.ghostbridge.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authorizes every AI API request before the body is bound or validated, so an authenticated caller without the AI role
 * always gets 403, never a validation message. Independent of method security, which an application can switch off.
 */
public class AiAccessInterceptor implements HandlerInterceptor {
    private final AiRequesterResolver resolver;

    public AiAccessInterceptor(AiRequesterResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        resolver.requireRequester();
        return true;
    }
}
