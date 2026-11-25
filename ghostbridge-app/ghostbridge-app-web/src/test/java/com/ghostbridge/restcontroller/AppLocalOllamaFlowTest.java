package com.ghostbridge.restcontroller;

import com.ghostbridge.support.AbstractAppIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole in-process path with the real local-mode provider: sign in, App controller, App service, coordinator,
 * OllamaAiProvider, a loopback stub standing in for Ollama. No cloud, no credential, no live model.
 */
@SpringBootTest(properties = {"ghostbridge.ai.provider=ollama", "ghostbridge.ai.ollama.local-enabled=true",
        "ghostbridge.ai.ollama.local.model=test-local-model"})
class AppLocalOllamaFlowTest extends AbstractAppIntegrationTest {
    private record Seen(String path, String authorization, String body) {
    }

    private static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    private static final HttpServer OLLAMA = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                SEEN.add(new Seen(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                byte[] reply = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"summary\\\":\\\"local ok\\\"}\"}}],"
                        + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":6}}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void ollama(DynamicPropertyRegistry registry) {
        registry.add("ghostbridge.ai.ollama.local.base-url", () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        OLLAMA.stop(0);
    }

    @Test
    void aSignedInUserReachesTheLocalModelThroughTheAppWithoutAnyCredential() throws Exception {
        SEEN.clear();
        Browser browser = signIn("alice");
        com.fasterxml.jackson.databind.JsonNode before = json.readTree(mvc.perform(get("/api/v1/ai/usage").session(browser.session()))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/ai/provider/status").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("ollama")).andExpect(jsonPath("$.model").value("test-local-model"))
                .andExpect(jsonPath("$.status").value("AVAILABLE")).andExpect(content().string(not(containsString("127.0.0.1"))));

        mvc.perform(post("/api/v1/ai/generate/structured").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                        .contentType("application/json").content("{\"schemaName\":\"generic-summary\",\"variables\":{\"text\":\"Summarize me.\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaName").value("generic-summary"))
                .andExpect(jsonPath("$.model").value("test-local-model"))
                .andExpect(jsonPath("$.structured.summary").value("local ok"))
                .andExpect(jsonPath("$.usage.inputTokens").value(21)).andExpect(jsonPath("$.usage.outputTokens").value(6))
                .andExpect(jsonPath("$.usage.totalTokens").value(27));

        assertThat(SEEN).hasSize(1);
        assertThat(SEEN.get(0).path()).isEqualTo("/v1/chat/completions");
        assertThat(SEEN.get(0).authorization()).isNull();
        assertThat(SEEN.get(0).body()).contains("\"model\":\"test-local-model\"").contains("\"response_format\":{\"type\":\"json_object\"}")
                .doesNotContain("Bearer");

        // usage is the caller's own retained history; the sibling test also records a request, so compare against the start
        com.fasterxml.jackson.databind.JsonNode after = json.readTree(mvc.perform(get("/api/v1/ai/usage").session(browser.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rateLimit.limitPerMinute").value(10)).andReturn().getResponse().getContentAsString());
        assertThat(after.get("requestCount").asLong() - before.get("requestCount").asLong()).isEqualTo(1);
        assertThat(after.get("successCount").asLong() - before.get("successCount").asLong()).isEqualTo(1);
        assertThat(after.get("inputTokens").asLong() - before.get("inputTokens").asLong()).isEqualTo(21);
        assertThat(after.get("outputTokens").asLong() - before.get("outputTokens").asLong()).isEqualTo(6);
        assertThat(after.get("totalTokens").asLong() - before.get("totalTokens").asLong()).isEqualTo(27);
    }

    @Test
    void anUnapprovedModelIsRejectedBeforeAnyRequestReachesTheModelServer() throws Exception {
        SEEN.clear();
        Browser browser = signIn("alice");
        mvc.perform(post("/api/v1/ai/generate").session(browser.session()).header(browser.csrfHeader(), browser.csrfToken())
                        .contentType("application/json").content("{\"promptName\":\"generic-structured\",\"modelId\":\"some-other-model\",\"variables\":{\"text\":\"x\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Model is not approved"));
        assertThat(SEEN).isEmpty();
    }
}
