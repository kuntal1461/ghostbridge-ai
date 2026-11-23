package com.ghostbridge.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

class AiOllamaAutoConfigurationTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY = "test-dummy-key-0123456789";

    /** Replaces the real OS environment with an empty one so the developer's own OLLAMA_API_KEY can never leak into a test. */
    private static void isolatedEnvironment(Map<String, Object> osEnvironment, ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, osEnvironment));
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
            .withInitializer(context -> isolatedEnvironment(Map.of(), context))
            .withPropertyValues("ghostbridge.ai.provider=ollama", "ghostbridge.ai.model-id=configured-model");

    private static String chainOf(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }

    private static void assertSafeStartupFailure(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        String text = chainOf(context.getStartupFailure());
        assertThat(text).contains("OLLAMA_API_KEY").contains("ghostbridge.ai.ollama.api-key").doesNotContain(DUMMY);
    }

    @Test
    void missingCredentialFailsStartupSafelyNamingTheSettingOnly() {
        runner.run(AiOllamaAutoConfigurationTest::assertSafeStartupFailure);
    }

    @Test
    void blankCredentialIsTreatedAsMissing() {
        runner.withPropertyValues("ghostbridge.ai.ollama.api-key=   ").run(AiOllamaAutoConfigurationTest::assertSafeStartupFailure);
    }

    @Test
    void credentialFromThePropertyStartsTheProvider() {
        runner.withPropertyValues("ghostbridge.ai.ollama.api-key=" + DUMMY).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiClient.class).hasSingleBean(AiProvider.class);
            AiProvider provider = context.getBean(AiProvider.class);
            assertThat(provider.name()).isEqualTo("ollama");
            assertThat(provider.status().ready()).isTrue();
            assertThat(provider.status().detail()).doesNotContain(DUMMY);
            assertThat(context.getBean(AiProperties.class).toString()).doesNotContain(DUMMY);
        });
    }

    @Test
    void credentialFallsBackToTheOllamaApiKeyOperatingSystemEnvironmentVariable() {
        runner.withInitializer(context -> isolatedEnvironment(Map.of("OLLAMA_API_KEY", DUMMY), context)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiProvider.class).name()).isEqualTo("ollama");
            assertThat(context.getBean(AiProperties.class).toString()).doesNotContain(DUMMY);
        });
    }

    @Test
    void theFallbackReadsTheOperatingSystemEnvironmentOnlyNotOtherPropertySources() {
        runner.withPropertyValues("OLLAMA_API_KEY=" + DUMMY).run(AiOllamaAutoConfigurationTest::assertSafeStartupFailure);
        runner.withSystemProperties("OLLAMA_API_KEY=" + DUMMY).run(AiOllamaAutoConfigurationTest::assertSafeStartupFailure);
    }

    @Test
    void credentialsThatAreNotHeaderSafeFailStartupWithoutQuotingThem() {
        for (String bad : List.of("test-dummy-key\ninjected-0123", "test-dummy-key with-space-0123", "test-dummy-key-\u200B0123")) {
            runner.withPropertyValues("ghostbridge.ai.ollama.api-key=" + bad).run(context -> {
                assertThat(context).hasFailed();
                String text = chainOf(context.getStartupFailure());
                assertThat(text).contains("ghostbridge.ai.ollama.api-key").doesNotContain("test-dummy").doesNotContain("injected");
            });
        }
    }

    @Test
    void theProviderBeanIsClosedByTheContext() {
        runner.withPropertyValues("ghostbridge.ai.ollama.api-key=" + DUMMY).run(context -> {
            assertThat(AutoCloseable.class).isAssignableFrom(com.ghostbridge.service.impl.OllamaAiProvider.class);
            String destroyMethod = context.getBeanFactory().getBeanDefinition("ollamaAiProvider").getDestroyMethodName();
            assertThat(destroyMethod).as("Spring infers close() for @Bean methods").isIn("(inferred)", null);
        });
    }

    @Test
    void ollamaIsNotSubjectToTheMockOnlyProfileGuard() {
        runner.withPropertyValues("ghostbridge.ai.ollama.api-key=" + DUMMY)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void mockProviderGuardStillAppliesAndNoOllamaBeanExistsForMock() {
        new ApplicationContextRunner().withBean(ObjectMapper.class, ObjectMapper::new)
                .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
                .withPropertyValues("ghostbridge.ai.provider=mock")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withBean(ObjectMapper.class, ObjectMapper::new)
                .withConfiguration(AutoConfigurations.of(AiAutoConfiguration.class))
                .withPropertyValues("ghostbridge.ai.provider=mock")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context.getBean(AiProvider.class).name()).isEqualTo("mock"));
    }

    @Test
    void invalidDestinationFailsStartupWithoutEchoingIt() {
        runner.withPropertyValues("ghostbridge.ai.ollama.api-key=" + DUMMY, "ghostbridge.ai.ollama.base-url=https://user:pw@evil.example")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(chainOf(context.getStartupFailure())).doesNotContain(DUMMY).doesNotContain("evil.example").doesNotContain("pw@");
                });
    }

    @Configuration
    @Import(AiAutoConfiguration.class)
    static class LazyApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void missingCredentialStillFailsAtStartupUnderLazyInitialization() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(LazyApp.class).web(WebApplicationType.NONE)
                .initializers(context -> isolatedEnvironment(Map.of(), context))
                .properties("ghostbridge.ai.provider=ollama", "ghostbridge.ai.model-id=configured-model",
                        "spring.main.lazy-initialization=true").run().close())
                .satisfies(e -> assertThat(chainOf(e)).contains("OLLAMA_API_KEY").doesNotContain(DUMMY));
    }

    @Test
    void theApiKeyComponentCarriesNoDefaultValueAnnotation() throws Exception {
        java.lang.reflect.RecordComponent apiKeyComponent = java.util.Arrays.stream(AiProperties.Ollama.class.getRecordComponents())
                .filter(component -> component.getName().equals("apiKey")).findFirst().orElseThrow();
        assertThat(apiKeyComponent.getType()).isEqualTo(AiSecret.class);
        assertThat(apiKeyComponent.isAnnotationPresent(org.springframework.boot.context.properties.bind.DefaultValue.class))
                .as("a credential must never have a built-in default").isFalse();
        for (java.lang.reflect.Parameter parameter : AiProperties.Ollama.class.getDeclaredConstructors()[0].getParameters()) {
            if (parameter.getType() == AiSecret.class) {
                assertThat(parameter.isAnnotationPresent(org.springframework.boot.context.properties.bind.DefaultValue.class)).isFalse();
            }
        }
    }

    /**
     * Generated configuration metadata must not carry a credential default. The metadata file is produced by the
     * annotation processor, which does not run by default on JDK 23+ (processors found only on the classpath), so the
     * check applies only when the file and the key entry exist; the annotation test above is the unconditional guard.
     */
    @Test
    void generatedConfigurationMetadataNeverCarriesACredentialDefault() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assumeThat(in).as("configuration metadata is generated only when annotation processing runs").isNotNull();
            JsonNode root = new ObjectMapper().readTree(in);
            List<String> keyEntries = new ArrayList<>();
            for (JsonNode property : root.path("properties")) {
                if (property.path("name").asText().equals("ghostbridge.ai.ollama.api-key")) {
                    keyEntries.add(property.path("name").asText());
                    assertThat(property.has("defaultValue")).isFalse();
                }
            }
            assumeThat(keyEntries).as("the metadata found on the classpath is not the AI core's").isNotEmpty();
            assertThat(root.toString()).doesNotContain(DUMMY);
        }
    }
}
