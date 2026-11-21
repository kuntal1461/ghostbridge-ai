package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GhostBridgeProductionValidatorTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GhostBridgeCoreAutoConfiguration.class));

    @Test
    void prodRejectsHttpOrigin() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("ghostbridge.cors.allowed-origins=http://insecure.example")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodRejectsDisabledSecurity() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("ghostbridge.security.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodRejectsExposingAllActuatorEndpoints() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("management.endpoints.web.exposure.include=*")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodAcceptsSafeConfiguration() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("ghostbridge.cors.allowed-origins=https://app.example")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void localAllowsHttpOrigin() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("ghostbridge.cors.allowed-origins=http://localhost:5173")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
