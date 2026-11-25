package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MANUAL live check of the complete coordinator path (prompt rendering, JSON-mode request, schema validation, usage,
 * telemetry) against a locally running Ollama. Skipped in every normal build; needs no API key and never contacts
 * Ollama Cloud. It makes exactly one request:
 * <pre>
 *   export GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL=...            (a model already installed locally)
 *   [export GHOSTBRIDGE_AI_OLLAMA_LOCAL_BASE_URL=...]        (default http://127.0.0.1:11434)
 *   mvn -pl ghostbridge-ai-module/ghostbridge-ai-core test -Dtest=OllamaLocalCoordinatorLiveTest -Dlive.local.provider=true
 * </pre>
 * If the model does not honour the schema the coordinator rejects the output (the test then fails with the AiException
 * reason); validation is never relaxed to make a model pass.
 */
@Tag("live-provider")
@EnabledIfSystemProperty(named = "live.local.provider", matches = "true")
@EnabledIfEnvironmentVariable(named = "GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL", matches = ".+")
class OllamaLocalCoordinatorLiveTest {
    private static final String DEFAULT_LOCAL_URL = "http://127.0.0.1:11434";

    @Test
    void oneStructuredRequestThroughTheCoordinator() {
        String model = System.getenv("GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL");
        String baseUrl = System.getenv().getOrDefault("GHOSTBRIDGE_AI_OLLAMA_LOCAL_BASE_URL", DEFAULT_LOCAL_URL);
        LocalCoordinatorWiring wiring = new LocalCoordinatorWiring(baseUrl, model, "timeout", "180s", "max-output-tokens", "256");
        try (OllamaAiProvider ignored = wiring.provider) {
            AiResponse response = wiring.coordinator.generate(new AiRequest("generic-structured", null,
                    Map.of("text", "GhostBridge AI connects a React frontend to a Spring Boot backend and a local language model."),
                    null, null, null, "live-check"));
            assertThat(response.provider()).isEqualTo("ollama");
            assertThat(response.model()).isEqualTo(model);
            assertThat(response.structuredOutput()).isPresent();
            assertThat(response.structuredOutput().orElseThrow().path("summary").asText()).isNotBlank();
            assertThat(wiring.registry.find(response.requestId())).isPresent();
            System.out.println("local coordinator live check ok: model=" + response.model() + " summaryChars="
                    + response.structuredOutput().orElseThrow().path("summary").asText().length() + " durationMs=" + response.durationMs()
                    + " usage(in/out)=" + response.usage().inputTokens() + "/" + response.usage().outputTokens());
        }
    }
}
