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
import com.ghostbridge.util.AiTextSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Clock;

/**
 * Registers the AI core through AutoConfiguration.imports. Every bean is overridable. The mock provider exists only
 * when explicitly selected; with no provider configured, AI calls fail with a missing-configuration error.
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
public class AiAutoConfiguration {

    /** Keeps the mock-in-production guard eager even when the application uses lazy initialization. */
    @Bean
    static LazyInitializationExcludeFilter aiProductionValidatorEagerFilter() {
        return LazyInitializationExcludeFilter.forBeanTypes(AiProductionValidator.class);
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
