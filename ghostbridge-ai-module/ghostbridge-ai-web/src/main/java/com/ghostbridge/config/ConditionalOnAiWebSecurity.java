package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches only while the shared security chain is enabled ({@code ghostbridge.security.enabled}, default true).
 * Placed on the auto-configuration <em>and</em> on the scannable web components, so an application that scans
 * {@code com.ghostbridge} can never end up with AI endpoints while security is switched off.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(prefix = GhostBridgeAiLiterals.SECURITY_PROPERTY_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnAiWebSecurity {
}
