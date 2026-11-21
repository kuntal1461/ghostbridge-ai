package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GhostBridgePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GhostBridgeCoreAutoConfiguration.class));

    @Test
    void defaultsAreRestrictive() {
        runner.run(context -> {
            GhostBridgeProperties props = context.getBean(GhostBridgeProperties.class);
            assertThat(props.cors().allowedOrigins()).isEmpty();
            assertThat(props.cors().allowCredentials()).isFalse();
            assertThat(props.security().enabled()).isTrue();
            assertThat(props.security().stateless()).isTrue();
            assertThat(props.security().publicPaths()).isEmpty();
            assertThat(props.security().contentSecurityPolicy()).contains("default-src 'none'");
        });
    }

    @Test
    void bindsConfiguredOrigins() {
        runner.withPropertyValues("ghostbridge.cors.allowed-origins=https://a.example,https://b.example")
                .run(context -> assertThat(context.getBean(GhostBridgeProperties.class).cors().allowedOrigins())
                        .containsExactly("https://a.example", "https://b.example"));
    }

    @Test
    void rejectsWildcardOriginWithCredentials() {
        runner.withPropertyValues("ghostbridge.cors.allowed-origins=*", "ghostbridge.cors.allow-credentials=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsPublicPathThatOpensEverything() {
        runner.withPropertyValues("ghostbridge.security.public-paths=/**")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankContentSecurityPolicy() {
        runner.withPropertyValues("ghostbridge.security.content-security-policy= ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nullListsFromProgrammaticConstructionBecomeEmpty() {
        var cors = new GhostBridgeProperties.Cors(null, null, null, null, java.time.Duration.ofHours(1), false);
        assertThat(cors.allowedOrigins()).isEmpty();
        assertThat(cors.allowedMethods()).isEmpty();
    }
}
