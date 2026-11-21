package com.ghostbridge.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

/** Fails application startup when the {@code prod} profile runs with unsafe settings. */
public class GhostBridgeProductionValidator implements InitializingBean {
    private static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";

    private final GhostBridgeProperties properties;
    private final Environment environment;

    public GhostBridgeProductionValidator(GhostBridgeProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> violations = violations(properties, environment.getProperty(EXPOSURE_PROPERTY, ""));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", violations));
        }
    }

    static List<String> violations(GhostBridgeProperties properties, String actuatorExposure) {
        List<String> violations = new ArrayList<>();
        if (!properties.security().enabled()) {
            violations.add("ghostbridge.security.enabled must be true");
        }
        if (!properties.security().hstsEnabled()) {
            violations.add("ghostbridge.security.hsts-enabled must be true");
        }
        properties.cors().allowedOrigins().stream()
                .filter(origin -> !origin.startsWith("https://"))
                .forEach(origin -> violations.add("CORS origin must use https in prod: " + origin));
        if (actuatorExposure.contains("*")) {
            violations.add(EXPOSURE_PROPERTY + " must not expose all endpoints");
        }
        return violations;
    }
}
