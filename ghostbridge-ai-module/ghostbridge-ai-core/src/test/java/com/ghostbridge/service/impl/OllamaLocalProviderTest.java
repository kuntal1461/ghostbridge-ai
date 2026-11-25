package com.ghostbridge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiSecret;
import com.ghostbridge.config.AiTestSupport;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Local mode against a loopback stub: no credential is required, read or sent, and nothing else is contacted. */
class OllamaLocalProviderTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY = "test-dummy-key-0123456789";

    private static AiProperties.Ollama localSettings(int port, String... extra) {
        String[] base = {"provider", "ollama", "ollama.local-enabled", "true", "ollama.local.base-url", "http://127.0.0.1:" + port,
                "ollama.local.model", "qwen2.5-coder:3b", "retry.max-attempts", "1"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return AiTestSupport.properties(all).ollama();
    }

    private static AiProviderCall call() {
        return new AiProviderCall("qwen2.5-coder:3b", "system text", "hello", 64, 0.0, Duration.ofSeconds(3), false);
    }

    @Test
    void localRequestsGoToTheLocalEndpointWithoutAnAuthorizationHeader() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY);
             OllamaAiProvider provider = new OllamaAiProvider(localSettings(server.port()), new ObjectMapper())) {
            AiProviderResult result = provider.invoke(call());
            assertThat(result.usage().inputTokens()).isEqualTo(11);
            assertThat(server.requests()).hasSize(1);
            StubOllamaServer.Received received = server.requests().get(0);
            assertThat(received.path()).isEqualTo("/v1/chat/completions");
            assertThat(received.header("Authorization")).isNull();
            assertThat(received.bodyText()).contains("qwen2.5-coder:3b");
            assertThat(provider.status().detail()).contains("Local").doesNotContain(DUMMY);
        }
    }

    @Test
    void aCloudCredentialThatIsConfiguredAnywayIsNeverSentInLocalMode() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY);
             OllamaAiProvider provider = new OllamaAiProvider(
                     localSettings(server.port(), "ollama.cloud.api-key", DUMMY).withApiKey(new AiSecret(DUMMY)), new ObjectMapper())) {
            provider.invoke(call());
            StubOllamaServer.Received received = server.requests().get(0);
            assertThat(received.header("Authorization")).isNull();
            assertThat(received.headers().toString()).doesNotContain(DUMMY);
            assertThat(received.bodyText()).doesNotContain(DUMMY);
        }
    }

    @Test
    void aLocalServerErrorIsReportedAndNeverRetriedAgainstAnotherEndpoint() throws IOException {
        try (StubOllamaServer server = StubOllamaServer.replying(500, "boom");
             OllamaAiProvider provider = new OllamaAiProvider(localSettings(server.port()), new ObjectMapper())) {
            AiException failure = catchThrowableOfType(AiException.class, () -> provider.invoke(call()));
            assertThat(failure.getMessage()).doesNotContain("127.0.0.1").doesNotContain("boom");
            assertThat(server.requests()).hasSize(1);
        }
    }

    @Test
    void anUnreachableLocalServerFailsClearlyWithoutLeakingTheAddress() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        try (OllamaAiProvider provider = new OllamaAiProvider(localSettings(closedPort), new ObjectMapper())) {
            AiException failure = catchThrowableOfType(AiException.class, () -> provider.invoke(call()));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage()).doesNotContain("127.0.0.1").doesNotContain(String.valueOf(closedPort));
            assertThat(failure.getCause()).isNull();
        }
    }

    @Test
    void aRedirectFromTheLocalServerIsNotFollowed() throws IOException {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
            exchange.sendResponseHeaders(302, -1);
        }); OllamaAiProvider provider = new OllamaAiProvider(localSettings(server.port()), new ObjectMapper())) {
            AiException failure = catchThrowableOfType(AiException.class, () -> provider.invoke(call()));
            assertThat(failure).isNotNull();
            assertThat(server.requests()).hasSize(1);
        }
    }

    @Test
    void cloudModeStillRequiresAUsableCredential() {
        AiProperties.Ollama cloud = AiTestSupport.properties("provider", "ollama", "ollama.local-enabled", "false", "model-id", "m").ollama();
        assertThatThrownBy(() -> new OllamaAiProvider(cloud, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ghostbridge.ai.ollama.cloud.api-key");
    }
}
