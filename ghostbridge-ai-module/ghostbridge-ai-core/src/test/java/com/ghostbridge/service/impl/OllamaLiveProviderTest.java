package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiSecret;
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
 * MANUAL, PAID live check against the real Ollama Cloud. Skipped in every normal build. It runs only when all of the
 * following are set explicitly by the person running it:
 * <pre>
 *   read -rs OLLAMA_API_KEY && export OLLAMA_API_KEY   (type the key at the prompt: not echoed, not stored in shell history)
 *   export GHOSTBRIDGE_AI_MODEL_ID=...    (a model you are entitled to use)
 *   mvn -pl ghostbridge-ai-module/ghostbridge-ai-core test -Dtest=OllamaLiveProviderTest -Dlive.provider=true
 * </pre>
 * The test prints only lengths and token counts. It makes exactly one small request.
 */
@Tag("live-provider")
@EnabledIfSystemProperty(named = "live.provider", matches = "true")
@EnabledIfEnvironmentVariable(named = "OLLAMA_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GHOSTBRIDGE_AI_MODEL_ID", matches = ".+")
class OllamaLiveProviderTest {

    @Test
    void oneSmallRoundTripAgainstOllamaCloud() {
        AiProperties properties = AiTestSupport.properties("provider", "ollama", "model-id", System.getenv("GHOSTBRIDGE_AI_MODEL_ID"));
        AiProperties.Ollama settings = properties.ollama().withApiKey(new AiSecret(System.getenv("OLLAMA_API_KEY")));
        OllamaAiProvider provider = new OllamaAiProvider(settings, new ObjectMapper());
        AiProviderResult result = provider.invoke(new AiProviderCall(properties.modelId(), "Reply with one short word.",
                "Say hello.", 16, 0.0, Duration.ofSeconds(60), false));
        assertThat(result.text()).isNotBlank();
        System.out.println("live check ok: chars=" + result.text().length() + " usage="
                + result.usage().inputTokens() + "/" + result.usage().outputTokens());
    }
}
