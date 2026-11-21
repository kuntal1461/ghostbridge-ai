package com.ghostbridge.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Authentication integration point. An application registers one or more beans of this type
 * (for example to add {@code oauth2ResourceServer} or a custom authentication filter) and the shared
 * filter chain applies them last, keeping the central hardening intact.
 */
@FunctionalInterface
public interface GhostBridgeHttpSecurityCustomizer {
    void customize(HttpSecurity http) throws Exception;
}
