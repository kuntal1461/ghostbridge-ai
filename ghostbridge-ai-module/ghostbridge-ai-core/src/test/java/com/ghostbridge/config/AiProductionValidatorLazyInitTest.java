package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mock-in-production guard must hold even when the whole application is lazily initialized. */
class AiProductionValidatorLazyInitTest {

    @Configuration
    @Import(AiAutoConfiguration.class)
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void prodWithMockFailsStartupEvenWithLazyInitialization() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE).profiles("prod")
                .properties("ghostbridge.ai.provider=mock", "spring.main.lazy-initialization=true")
                .run().close()).hasRootCauseMessage(
                "Unsafe AI configuration: ghostbridge.ai.provider=mock is allowed only when one of the profiles local, dev or test is active and prod is not");
    }
}
