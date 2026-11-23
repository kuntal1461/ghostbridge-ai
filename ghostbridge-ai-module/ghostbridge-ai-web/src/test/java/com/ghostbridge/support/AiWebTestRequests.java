package com.ghostbridge.support;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/** Shared request fixtures. Every call to {@link #uniqueUser()} is a new identity, so tests never share limiter state. */
public final class AiWebTestRequests {
    public static final String GENERATE = "/api/v1/ai/generate";
    public static final String STRUCTURED = "/api/v1/ai/generate/structured";
    public static final String STATUS = "/api/v1/ai/provider/status";
    public static final String USAGE = "/api/v1/ai/usage";
    public static final String VALID_GENERATE = "{\"promptName\":\"generic-structured\",\"variables\":{\"text\":\"hello world\"}}";
    public static final String VALID_STRUCTURED = "{\"schemaName\":\"generic-summary\",\"variables\":{\"text\":\"hello world\"}}";

    private AiWebTestRequests() {
    }

    public static String uniqueUser() {
        return "user-" + UUID.randomUUID();
    }

    public static RequestPostProcessor aiUser(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name).roles("AI_USER");
    }

    public static RequestPostProcessor withoutAiRole(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name).roles("OTHER");
    }
}
