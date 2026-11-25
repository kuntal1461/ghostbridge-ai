package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiTestSupport;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MANUAL live check against a locally running Ollama. Skipped in every normal build. It needs no API key, sends no
 * credential and never contacts Ollama Cloud. It runs only when both are set explicitly by the person running it:
 * <pre>
 *   export GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL=...            (a model already installed locally)
 *   [export GHOSTBRIDGE_AI_OLLAMA_LOCAL_BASE_URL=...]        (default http://127.0.0.1:11434)
 *   mvn -pl ghostbridge-ai-module/ghostbridge-ai-core test -Dtest=OllamaLocalLiveProviderTest -Dlive.local.provider=true
 * </pre>
 * It makes exactly one small request and prints only the model, lengths, duration and token counts.
 */
@Tag("live-provider")
@EnabledIfSystemProperty(named = "live.local.provider", matches = "true")
@EnabledIfEnvironmentVariable(named = "GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL", matches = ".+")
class OllamaLocalLiveProviderTest {
    private static final String DEFAULT_LOCAL_URL = "http://127.0.0.1:11434";

    @Test
    void oneSmallRoundTripAgainstTheLocalOllama() {
        String model = System.getenv("GHOSTBRIDGE_AI_OLLAMA_LOCAL_MODEL");
        String baseUrl = System.getenv().getOrDefault("GHOSTBRIDGE_AI_OLLAMA_LOCAL_BASE_URL", DEFAULT_LOCAL_URL);
        AiProperties properties = AiTestSupport.properties("provider", "ollama", "ollama.local-enabled", "true",
                "ollama.local.base-url", baseUrl, "ollama.local.model", model);
        assertThat(properties.ollama().credentialRequired()).isFalse();
        try (OllamaAiProvider provider = new OllamaAiProvider(properties.ollama(), new ObjectMapper())) {
            long started = System.nanoTime();
            AiProviderResult result = provider.invoke(new AiProviderCall(properties.modelId(), "You are a concise assistant.",
                    "Reply with Hello GhostBridge AI", 32, 0.0, Duration.ofSeconds(120), false));
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertThat(result.text()).isNotBlank();
            System.out.println("local live check ok: model=" + properties.modelId() + " chars=" + result.text().length()
                    + " durationMs=" + millis + " usage(in/out)=" + result.usage().inputTokens() + "/" + result.usage().outputTokens());
        }
    }
}
