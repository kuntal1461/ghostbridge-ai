package com.ghostbridge.restcontroller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller that serves the AI API contract, so the AI error advice (reason property, Retry-After, 413 for
 * oversize input) applies to it, whichever module hosts it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AiApiEndpoint {
}
