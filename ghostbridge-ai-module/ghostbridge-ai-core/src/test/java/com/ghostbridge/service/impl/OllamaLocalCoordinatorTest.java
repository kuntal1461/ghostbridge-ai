package com.ghostbridge.service.impl;

import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The full coordinator path with the real local-mode provider against a loopback stub: structured JSON mode, approved
 * model validation, token usage reporting, and the absence of any credential. Deterministic, no live Ollama.
 */
class OllamaLocalCoordinatorTest {
    private static final String LOCAL_MODEL = "qwen2.5-coder:3b";

    private static String completion(String content, String usage) {
        return "{\"choices\":[{\"message\":{\"content\":" + new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content) + "}}]"
                + usage + "}";
    }

    private static final String USAGE = ",\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":9}";

    private static AiRequest structuredRequest(String model) {
        return new AiRequest("generic-structured", null, Map.of("text", "Summarize this."), null, model, null, "user-1");
    }

    @Test
    void aStructuredRequestRunsEndToEndInJsonModeWithTheLocalModelAndNoCredential() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"all good\"}", USAGE))) {
            LocalCoordinatorWiring wiring = new LocalCoordinatorWiring("http://127.0.0.1:" + server.port(), LOCAL_MODEL);
            try (OllamaAiProvider ignored = wiring.provider) {
                AiResponse response = wiring.coordinator.generate(structuredRequest(null));
                assertThat(response.provider()).isEqualTo("ollama");
                assertThat(response.model()).isEqualTo(LOCAL_MODEL);
                assertThat(response.structuredOutput().orElseThrow().get("summary").asText()).isEqualTo("all good");
                assertThat(response.usage().inputTokens()).isEqualTo(42);
                assertThat(response.usage().outputTokens()).isEqualTo(9);
                assertThat(wiring.registry.find(response.requestId()).orElseThrow().model()).isEqualTo(LOCAL_MODEL);

                StubOllamaServer.Received received = server.requests().get(0);
                assertThat(server.requests()).hasSize(1);
                assertThat(received.path()).isEqualTo("/v1/chat/completions");
                assertThat(received.header("Authorization")).isNull();
                assertThat(received.bodyText()).contains("\"model\":\"" + LOCAL_MODEL + "\"").contains("\"response_format\":{\"type\":\"json_object\"}")
                        .contains("\"stream\":false");
            }
        }
    }

    @Test
    void anApprovedAlternativeModelIsUsedAndAnUnapprovedOneIsRejectedBeforeAnyRequest() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"ok\"}", USAGE))) {
            LocalCoordinatorWiring wiring = new LocalCoordinatorWiring("http://127.0.0.1:" + server.port(), LOCAL_MODEL,
                    "approved-model-ids", "phi3:3.8b");
            try (OllamaAiProvider ignored = wiring.provider) {
                assertThat(wiring.coordinator.generate(structuredRequest("phi3:3.8b")).model()).isEqualTo("phi3:3.8b");
                assertThat(server.requests().get(0).bodyText()).contains("\"model\":\"phi3:3.8b\"");

                AiException rejected = catchThrowableOfType(AiException.class, () -> wiring.coordinator.generate(structuredRequest("llama3.1:8b")));
                assertThat(rejected.getReason()).isEqualTo(AiErrorReason.INVALID_INPUT);
                assertThat(rejected.getMessage()).isEqualTo("Model is not approved");
                assertThat(server.requests()).hasSize(1);
            }
        }
    }

    @Test
    void missingTokenUsageIsReportedAsAbsentNeverFabricated() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"ok\"}", ""))) {
            LocalCoordinatorWiring wiring = new LocalCoordinatorWiring("http://127.0.0.1:" + server.port(), LOCAL_MODEL);
            try (OllamaAiProvider ignored = wiring.provider) {
                AiResponse response = wiring.coordinator.generate(structuredRequest(null));
                assertThat(response.usage().inputTokens()).isNull();
                assertThat(response.usage().outputTokens()).isNull();
                assertThat(response.usage().estimatedCost()).isNull();
            }
        }
    }

    @Test
    void aSingleMarkdownFenceAroundValidJsonIsUnwrappedAndStillSchemaValidated() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("```json\n{\"summary\":\"fenced\"}\n```", USAGE))) {
            LocalCoordinatorWiring wiring = new LocalCoordinatorWiring("http://127.0.0.1:" + server.port(), LOCAL_MODEL);
            try (OllamaAiProvider ignored = wiring.provider) {
                assertThat(wiring.coordinator.generate(structuredRequest(null)).structuredOutput().orElseThrow().get("summary").asText())
                        .isEqualTo("fenced");
            }
        }
    }

    @Test
    void aLocalModelThatIgnoresTheSchemaIsRejectedNotAccepted() throws IOException {
        for (String content : new String[]{"{\"answer\":\"wrong shape\"}", "{\"summary\":\"ok\",\"extra\":1}", "Sure! Here is the JSON", "```json\n{\"answer\":1}\n```"}) {
            try (StubOllamaServer server = StubOllamaServer.replying(200, completion(content, USAGE))) {
                LocalCoordinatorWiring wiring = new LocalCoordinatorWiring("http://127.0.0.1:" + server.port(), LOCAL_MODEL);
                try (OllamaAiProvider ignored = wiring.provider) {
                    AiException failure = catchThrowableOfType(AiException.class, () -> wiring.coordinator.generate(structuredRequest(null)));
                    assertThat(failure).as("accepted: %s", content).isNotNull();
                    assertThat(failure.getReason()).isIn(AiErrorReason.OUTPUT_VALIDATION_FAILED, AiErrorReason.INVALID_MODEL_RESPONSE);
                    assertThat(failure.getMessage()).doesNotContain(content);
                }
            }
        }
    }
}
