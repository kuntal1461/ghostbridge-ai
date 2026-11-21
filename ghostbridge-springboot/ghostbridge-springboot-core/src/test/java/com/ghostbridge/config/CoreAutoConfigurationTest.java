package com.ghostbridge.config;

import com.ghostbridge.util.UploadValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GhostBridgeCoreAutoConfiguration.class));

    @Test
    void loadsSharedBeansWithoutComponentScanning() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(GhostBridgeProperties.class);
            assertThat(context).hasSingleBean(UploadValidator.class);
            assertThat(context).hasSingleBean(GhostBridgeProductionValidator.class);
        });
    }

    @Test
    void applicationBeanOverridesSharedDefault() {
        runner.withUserConfiguration(CustomValidatorConfig.class).run(context -> {
            assertThat(context).hasSingleBean(UploadValidator.class);
            assertThat(context.getBean(UploadValidator.class)).isSameAs(CustomValidatorConfig.CUSTOM);
        });
    }

    @Test
    void loadsDefaultsFileAtLowestPrecedence() {
        runner.run(context -> assertThat(context.getEnvironment().getProperty("server.shutdown")).isEqualTo("graceful"));
        runner.withPropertyValues("server.shutdown=immediate")
                .run(context -> assertThat(context.getEnvironment().getProperty("server.shutdown")).isEqualTo("immediate"));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomValidatorConfig {
        static final UploadValidator CUSTOM = new UploadValidator(
                new GhostBridgeProperties.Upload(org.springframework.util.unit.DataSize.ofBytes(1), org.springframework.util.unit.DataSize.ofBytes(1), java.util.List.of()));

        @Bean
        UploadValidator customUploadValidator() {
            return CUSTOM;
        }
    }
}
