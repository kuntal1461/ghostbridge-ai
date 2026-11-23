package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.service.AiClient;
import com.ghostbridge.service.AiPromptService;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.service.AiRequestRegistry;
import com.ghostbridge.service.AiStructuredOutputService;
import com.ghostbridge.service.impl.AiExecutionGuard;
import com.ghostbridge.service.impl.AiLimiter;
import com.ghostbridge.service.impl.AiRequestTelemetry;
import com.ghostbridge.service.impl.AiRetryExecutor;
import com.ghostbridge.service.impl.AiTokenBudget;
import com.ghostbridge.service.impl.ClasspathAiPromptService;
import com.ghostbridge.service.impl.DefaultAiCoordinator;
import com.ghostbridge.service.impl.InMemoryAiRequestRegistry;
import com.ghostbridge.service.impl.JsonSchemaAiStructuredOutputService;
import com.ghostbridge.service.impl.MockAiProvider;
import com.ghostbridge.service.impl.OllamaAiProvider;
import com.ghostbridge.util.AiTextSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.time.Clock;

/**
 * Registers the AI core through AutoConfiguration.imports. Every bean is overridable. The mock provider exists only
 * when explicitly selected; with no provider configured, AI calls fail with a missing-configuration error.
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    /**
     * Keeps the mock-in-production guard and the Ollama provider (credential check) eager even when the application
     * uses lazy initialization, so unsafe or incomplete configuration fails at startup.
     */
    @Bean
    static LazyInitializationExcludeFilter aiProductionValidatorEagerFilter() {
        return LazyInitializationExcludeFilter.forBeanTypes(AiProductionValidator.class, OllamaAiProvider.class);
    }

    /**
     * The credential comes from {@code ghostbridge.ai.ollama.api-key} or, when that is blank, from the
     * {@code OLLAMA_API_KEY} environment variable. The failure message names the settings only, never a value.
     */
    @Bean
    @ConditionalOnProperty(prefix = GhostBridgeAiLiterals.PROPERTY_PREFIX, name = GhostBridgeAiLiterals.PROPERTY_PROVIDER, havingValue = GhostBridgeAiLiterals.PROVIDER_OLLAMA)
    @ConditionalOnMissingBean(OllamaAiProvider.class)
    OllamaAiProvider ollamaAiProvider(AiProperties properties, ObjectMapper mapper, Environment environment) {
        AiProperties.Ollama settings = properties.ollama();
        if (!settings.apiKey().isPresent()) {
            settings = settings.withApiKey(new AiSecret(operatingSystemEnvironmentValue(environment)));
        }
        if (!settings.apiKey().isPresent()) {
            throw new IllegalStateException("The Ollama API key is not configured: set ghostbridge.ai.ollama.api-key "
                    + "or the environment variable " + GhostBridgeAiLiterals.ENV_OLLAMA_API_KEY + " (backend runtime only)");
        }
        return new OllamaAiProvider(settings, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    AiProductionValidator aiProductionValidator(AiProperties properties, Environment environment) {
        return new AiProductionValidator(properties, environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = GhostBridgeAiLiterals.PROPERTY_PREFIX, name = GhostBridgeAiLiterals.PROPERTY_PROVIDER, havingValue = GhostBridgeAiLiterals.PROVIDER_MOCK)
    @ConditionalOnMissingBean(MockAiProvider.class)
    AiProvider mockAiProvider(AiProperties properties) {
        return new MockAiProvider(properties.mock());
    }

    /** Reads {@code OLLAMA_API_KEY} from the OS environment source only, not from files, -D properties or arguments. */
    private static String operatingSystemEnvironmentValue(Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurable) {
            PropertySource<?> source = configurable.getPropertySources()
                    .get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            Object value = source == null ? null : source.getProperty(GhostBridgeAiLiterals.ENV_OLLAMA_API_KEY);
            return value == null ? null : value.toString();
        }
        return null;
    }

    @Bean
    @ConditionalOnMissingBean
    AiTextSanitizer aiTextSanitizer(AiProperties properties) {
        return new AiTextSanitizer(properties.redactionPatterns());
    }

    @Bean
    @ConditionalOnMissingBean
    AiStructuredOutputService aiStructuredOutputService(ObjectMapper mapper) {
        return new JsonSchemaAiStructuredOutputService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    AiPromptService aiPromptService(ObjectMapper mapper, AiStructuredOutputService structuredOutput, AiTextSanitizer sanitizer) {
        return new ClasspathAiPromptService(mapper, structuredOutput, sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    AiRequestRegistry aiRequestRegistry(AiProperties properties) {
        return new InMemoryAiRequestRegistry(properties.historyMaxEntries());
    }

    @Bean
    @ConditionalOnMissingBean
    AiRequestTelemetry aiRequestTelemetry(AiRequestRegistry registry, ObjectProvider<MeterRegistry> meters) {
        return new AiRequestTelemetry(registry, meters);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock aiClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    AiLimiter aiLimiter(AiProperties properties, Clock clock) {
        return new AiLimiter(properties.rateLimit(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AiTokenBudget aiTokenBudget(AiProperties properties, Clock clock) {
        return new AiTokenBudget(properties.budget(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AiRetryExecutor aiRetryExecutor(AiProperties properties) {
        return new AiRetryExecutor(properties.retry());
    }

    @Bean
    @ConditionalOnMissingBean
    AiExecutionGuard aiExecutionGuard(AiLimiter limiter, AiTokenBudget budget, AiRetryExecutor retry) {
        return new AiExecutionGuard(limiter, budget, retry);
    }

    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    AiClient aiClient(AiProperties properties, ObjectProvider<AiProvider> providers, AiPromptService prompts,
                      AiStructuredOutputService structuredOutput, AiExecutionGuard guard, AiRequestTelemetry telemetry, Clock clock) {
        return new DefaultAiCoordinator(properties, providers.orderedStream().toList(), prompts, structuredOutput, guard, telemetry, clock);
    }
}
