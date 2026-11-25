package com.ghostbridge.config;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches while AI Web's own REST controller is enabled ({@code ghostbridge.ai.web.rest-enabled}, default true). An
 * application that hosts the AI API through its own controller (for example GhostBridge App) sets it to false so the same
 * paths are never mapped twice, while the AI Web security pieces, limiter, mapper and error advice stay available to it.
 *
 * <p>It is placed on {@code AiRestController} itself as well as on the auto-configuration beans: an application whose component
 * scan covers {@code com.ghostbridge} would otherwise register the controller by scanning, whatever the auto-configuration does.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(prefix = GhostBridgeAiLiterals.WEB_PROPERTY_PREFIX, name = GhostBridgeAiLiterals.PROPERTY_REST_ENABLED,
        havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnAiRestApi {
}
