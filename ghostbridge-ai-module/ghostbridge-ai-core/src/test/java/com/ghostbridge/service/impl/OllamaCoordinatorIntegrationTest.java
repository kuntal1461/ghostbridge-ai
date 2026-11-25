package com.ghostbridge.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiSecret;
import com.ghostbridge.config.AiTestSupport;
import com.ghostbridge.dto.AiRequest;
import com.ghostbridge.dto.AiRequestMetadata;
import com.ghostbridge.dto.AiResponse;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import com.ghostbridge.util.AiTextSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** DefaultAiCoordinator driving the real OllamaAiProvider against a local stub server. */
class OllamaCoordinatorIntegrationTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY_KEY = "test-dummy-key-0123456789";
    private static final String SECRET_TEXT = "USER-TEXT-THAT-MUST-NOT-APPEAR-IN-LOGS";

    private final InMemoryAiRequestRegistry registry = new InMemoryAiRequestRegistry(50);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureLogs() {
        logs.start();
        rootLogger.addAppender(logs);
    }

    private final List<OllamaAiProvider> created = new java.util.ArrayList<>();

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logs);
        created.forEach(OllamaAiProvider::close);
    }

    private DefaultAiCoordinator coordinator(URI endpoint, int maxRequestBytes, String... extra) {
        String[] base = {"provider", "ollama", "ollama.local-enabled", "false", "model-id", "test-model", "retry.initial-backoff", "1ms", "retry.max-backoff", "1ms"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        AiProperties properties = AiTestSupport.properties(all);
        ObjectMapper mapper = new ObjectMapper();
        JsonSchemaAiStructuredOutputService structured = new JsonSchemaAiStructuredOutputService(mapper);
        ClasspathAiPromptService prompts = new ClasspathAiPromptService(mapper, structured, new AiTextSanitizer(List.of()));
        Clock clock = Clock.systemUTC();
        AiExecutionGuard guard = new AiExecutionGuard(new AiLimiter(properties.rateLimit(), clock),
                new AiTokenBudget(properties.budget(), clock), new AiRetryExecutor(properties.retry()));
        AiRequestTelemetry telemetry = new AiRequestTelemetry(registry, new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class));
        OllamaAiProvider provider = new OllamaAiProvider(endpoint, new AiSecret(DUMMY_KEY), Duration.ofSeconds(2),
                maxRequestBytes, 65536, mapper);
        created.add(provider);
        return new DefaultAiCoordinator(properties, List.of(provider), prompts, structured, guard, telemetry, clock);
    }

    private static AiRequest request(String text) {
        return new AiRequest("generic-structured", null, Map.of("text", text), null, null, null, "user-1");
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + new ObjectMapper().valueToTree(content) + "}}],"
                + "\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":5}}";
    }

    private static AiErrorReason reasonOf(DefaultAiCoordinator coordinator, AiRequest request) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class, () -> coordinator.generate(request)).getReason();
    }

    private void assertNoSecretInLogs() {
        assertThat(logs.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain(DUMMY_KEY).doesNotContain(SECRET_TEXT).doesNotContain("Bearer"));
    }

    @Test
    void structuredRequestFlowsThroughTheCoordinatorAndProviderWithTelemetry() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"all good\"}"))) {
            AiResponse response = coordinator(server.chatUri(), 65536).generate(request(SECRET_TEXT));
            assertThat(response.provider()).isEqualTo("ollama");
            assertThat(response.model()).isEqualTo("test-model");
            assertThat(response.structuredOutput().orElseThrow().get("summary").asText()).isEqualTo("all good");
            assertThat(response.usage().inputTokens()).isEqualTo(20);
            assertThat(response.usage().outputTokens()).isEqualTo(5);
            AiRequestMetadata meta = registry.find(response.requestId()).orElseThrow();
            assertThat(meta.provider()).isEqualTo("ollama");
            assertThat(meta.toString()).doesNotContain(DUMMY_KEY).doesNotContain(SECRET_TEXT);
            // the sanitized, delimited user text did reach the provider; the credential only in the header
            assertThat(server.requests().get(0).bodyText()).contains(SECRET_TEXT).contains("<untrusted_content>").doesNotContain(DUMMY_KEY);
            assertNoSecretInLogs();
        }
    }

    @Test
    void schemaViolationsAndTrailingJsonFromTheModelAreRejected() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"nope\":1}"))) {
            assertThat(reasonOf(coordinator(server.chatUri(), 65536), request("t"))).isEqualTo(AiErrorReason.OUTPUT_VALIDATION_FAILED);
        }
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"ok\"} extra"))) {
            assertThat(reasonOf(coordinator(server.chatUri(), 65536), request("t"))).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        }
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("not json"))) {
            assertThat(reasonOf(coordinator(server.chatUri(), 65536), request("t"))).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        }
    }

    @Test
    void transientFailuresAreRetriedUpToTheConfiguredAttemptsThenSucceed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (StubOllamaServer server = new StubOllamaServer(exchange ->
                StubOllamaServer.respond(exchange, calls.incrementAndGet() < 3 ? 503 : 200, completion("{\"summary\":\"ok\"}")))) {
            AiResponse response = coordinator(server.chatUri(), 65536, "retry.max-attempts", "3").generate(request("t"));
            assertThat(response.text()).isEqualTo("{\"summary\":\"ok\"}");
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void retriesAreBoundedByMaxAttempts() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(503, "{}")) {
            assertThat(reasonOf(coordinator(server.chatUri(), 65536, "retry.max-attempts", "3"), request("t")))
                    .isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE);
            assertThat(server.requests()).hasSize(3);
        }
        try (StubOllamaServer server = StubOllamaServer.replying(429, "{}")) {
            assertThat(reasonOf(coordinator(server.chatUri(), 65536, "retry.max-attempts", "2"), request("t")))
                    .isEqualTo(AiErrorReason.PROVIDER_THROTTLED);
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void rejectedCredentialsAreNotRetriedAndRevealNothing() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(401, "{\"error\":\"" + DUMMY_KEY + "\"}")) {
            AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                    () -> coordinator(server.chatUri(), 65536, "retry.max-attempts", "5").generate(request(SECRET_TEXT)));
            assertThat(failure.getReason()).isEqualTo(AiErrorReason.MISSING_CREDENTIALS);
            assertThat(failure.getMessage()).doesNotContain(DUMMY_KEY);
            assertThat(server.requests()).hasSize(1);
            assertThat(registry.recent(1).get(0).toString()).doesNotContain(DUMMY_KEY).doesNotContain(SECRET_TEXT);
            assertNoSecretInLogs();
        }
    }

    @Test
    void oversizedRequestsNeverReachTheProvider() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, completion("{\"summary\":\"ok\"}"))) {
            DefaultAiCoordinator small = coordinator(server.chatUri(), 1500);
            assertThat(reasonOf(small, request("😀".repeat(600)))).isEqualTo(AiErrorReason.INPUT_TOO_LARGE);
            assertThat(server.requests()).isEmpty();
        }
    }
}
