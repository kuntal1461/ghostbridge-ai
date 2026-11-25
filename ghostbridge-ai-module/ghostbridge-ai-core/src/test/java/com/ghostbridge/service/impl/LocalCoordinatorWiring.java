package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiTestSupport;
import com.ghostbridge.service.AiProvider;
import com.ghostbridge.util.AiTextSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Test wiring for a coordinator that runs the real local-mode {@link OllamaAiProvider}, built from validated settings. */
final class LocalCoordinatorWiring {
    final AiProperties properties;
    final OllamaAiProvider provider;
    final InMemoryAiRequestRegistry registry = new InMemoryAiRequestRegistry(50);
    final DefaultAiCoordinator coordinator;

    /** {@code extra} are further {@code ghostbridge.ai.*} key/value pairs (for example {@code approved-model-ids}). */
    LocalCoordinatorWiring(String baseUrl, String model, String... extra) {
        List<String> all = new ArrayList<>(List.of("provider", "ollama", "ollama.local-enabled", "true", "ollama.local.base-url", baseUrl,
                "ollama.local.model", model, "retry.max-attempts", "1"));
        all.addAll(List.of(extra));
        properties = AiTestSupport.properties(all.toArray(String[]::new));
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaAiStructuredOutputService structured = new JsonSchemaAiStructuredOutputService(mapper);
        ClasspathAiPromptService prompts = new ClasspathAiPromptService(mapper, structured, new AiTextSanitizer(List.of()));
        Clock clock = Clock.systemUTC();
        AiExecutionGuard guard = new AiExecutionGuard(new AiLimiter(properties.rateLimit(), clock),
                new AiTokenBudget(properties.budget(), clock), new AiRetryExecutor(properties.retry()));
        AiRequestTelemetry telemetry = new AiRequestTelemetry(registry, new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
        provider = new OllamaAiProvider(properties.ollama(), mapper);
        coordinator = new DefaultAiCoordinator(properties, List.<AiProvider>of(provider), prompts, structured, guard, telemetry, clock);
    }
}
