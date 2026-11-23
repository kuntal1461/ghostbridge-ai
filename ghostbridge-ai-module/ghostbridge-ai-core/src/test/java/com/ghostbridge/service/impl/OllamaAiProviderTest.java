package com.ghostbridge.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostbridge.config.AiSecret;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAiProviderTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY_KEY = "test-dummy-key-0123456789";
    private static final String SECRET_PROMPT = "PROMPT-TEXT-THAT-MUST-NOT-APPEAR-IN-LOGS";
    private static final int MAX_REQUEST = 4096;
    private static final int MAX_RESPONSE = 4096;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureAllLogs() {
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logs);
        created.forEach(OllamaAiProvider::close);
    }

    private final List<OllamaAiProvider> created = new ArrayList<>();

    private OllamaAiProvider provider(URI endpoint) {
        OllamaAiProvider provider = new OllamaAiProvider(endpoint, new AiSecret(DUMMY_KEY), Duration.ofSeconds(2), MAX_REQUEST, MAX_RESPONSE, mapper);
        created.add(provider);
        return provider;
    }

    private static AiProviderCall call(String user, boolean json) {
        return new AiProviderCall("test-model", "system text", user, 256, 0.3, Duration.ofSeconds(3), json);
    }

    private AiErrorReason reasonOf(OllamaAiProvider provider, AiProviderCall call) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class, () -> provider.invoke(call)).getReason();
    }

    private void assertNoSecretOrPromptAnywhere(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            assertThat(String.valueOf(t.getMessage())).doesNotContain(DUMMY_KEY).doesNotContain("Bearer");
        }
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(DUMMY_KEY).doesNotContain(SECRET_PROMPT);
            assertThat(String.valueOf(event.getThrowableProxy())).doesNotContain(DUMMY_KEY);
        });
    }

    // ---- request format (P-01..P-03)

    @Test
    void sendsChatCompletionRequestToTheEndpointWithBearerAuthorization() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            provider(server.chatUri()).invoke(call("hello", true));
            StubOllamaServer.Received received = server.requests().get(0);
            assertThat(received.method()).isEqualTo("POST");
            assertThat(received.path()).isEqualTo("/v1/chat/completions");
            assertThat(received.query()).isNull();
            assertThat(received.header("Authorization")).isEqualTo("Bearer " + DUMMY_KEY);
            assertThat(received.header("Content-Type")).isEqualTo("application/json");
            JsonNode body = mapper.readTree(received.body());
            assertThat(body.get("model").asText()).isEqualTo("test-model");
            assertThat(body.get("stream").asBoolean()).isFalse();
            assertThat(body.get("max_tokens").asInt()).isEqualTo(256);
            assertThat(body.get("temperature").asDouble()).isEqualTo(0.3);
            assertThat(body.get("messages")).hasSize(2);
            assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
            assertThat(body.get("messages").get(1).get("role").asText()).isEqualTo("user");
            assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("hello");
            assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
            assertThat(received.bodyText()).doesNotContain(DUMMY_KEY);
        }
    }

    @Test
    void jsonModeFieldIsOmittedWhenStructuredOutputIsNotRequired() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            provider(server.chatUri()).invoke(call("hello", false));
            assertThat(mapper.readTree(server.requests().get(0).body()).has("response_format")).isFalse();
        }
    }

    // ---- response mapping (P-14, P-15)

    @Test
    void mapsSuccessfulResponseTextAndReportedUsage() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            AiProviderResult result = provider(server.chatUri()).invoke(call("hello", true));
            assertThat(result.text()).isEqualTo("{\"summary\":\"ok\"}");
            assertThat(result.usage().inputTokens()).isEqualTo(11);
            assertThat(result.usage().outputTokens()).isEqualTo(7);
        }
    }

    @Test
    void missingOrInvalidUsageIsNotFabricated() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":\"hi\"}}],\"usage\":{\"prompt_tokens\":-5,\"completion_tokens\":\"7\"}}";
        try (StubOllamaServer server = StubOllamaServer.replying(200, body)) {
            AiProviderResult result = provider(server.chatUri()).invoke(call("hello", false));
            assertThat(result.usage().inputTokens()).isNull();
            assertThat(result.usage().outputTokens()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "not json at all",
            "{}",
            "{\"choices\":[]}",
            "{\"choices\":\"x\"}",
            "{\"choices\":[{\"message\":{}}]}",
            "{\"choices\":[{\"message\":{\"content\":42}}]}",
            "{\"choices\":[{\"message\":{\"content\":null}}]}",
            "[1,2,3]"})
    void unexpectedResponseShapesAreInvalidModelResponses(String body) throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, body)) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(reasonOf(provider, call("hello", false))).isEqualTo(AiErrorReason.INVALID_MODEL_RESPONSE);
        }
    }

    // ---- provider status mapping (P-06..P-10)

    @ParameterizedTest
    @CsvSource({"401,MISSING_CREDENTIALS", "403,MISSING_CREDENTIALS", "429,PROVIDER_THROTTLED", "408,PROVIDER_TIMEOUT",
            "500,PROVIDER_UNAVAILABLE", "502,PROVIDER_UNAVAILABLE", "503,PROVIDER_UNAVAILABLE", "504,PROVIDER_UNAVAILABLE",
            "400,PROVIDER_FAILURE", "404,PROVIDER_FAILURE", "422,PROVIDER_FAILURE"})
    void providerStatusCodesMapToTypedErrorsWithoutEchoingTheBody(int status, AiErrorReason expected) throws Exception {
        String leaky = "{\"error\":\"echo of " + DUMMY_KEY + " and " + SECRET_PROMPT + "\"}";
        try (StubOllamaServer server = StubOllamaServer.replying(status, leaky)) {
            OllamaAiProvider provider = provider(server.chatUri());
            AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                    () -> provider.invoke(call(SECRET_PROMPT, false)));
            assertThat(failure.getReason()).isEqualTo(expected);
            assertThat(failure.getMessage()).doesNotContain("echo").doesNotContain(DUMMY_KEY);
            assertThat(failure.getCause()).isNull();
            assertNoSecretOrPromptAnywhere(failure);
        }
    }

    @Test
    void retryableReasonsAreExactlyThrottlingTimeoutAndUnavailable() {
        assertThat(AiErrorReason.PROVIDER_THROTTLED.retryable()).isTrue();
        assertThat(AiErrorReason.PROVIDER_UNAVAILABLE.retryable()).isTrue();
        assertThat(AiErrorReason.MISSING_CREDENTIALS.retryable()).isFalse();
        assertThat(AiErrorReason.PROVIDER_FAILURE.retryable()).isFalse();
    }

    // ---- transport failures (P-11..P-13)

    @Test
    void responseTimeoutIsMapped() throws Exception {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            OllamaAiProvider provider = provider(server.chatUri());
            AiProviderCall shortDeadline = new AiProviderCall("m", "s", "u", 10, 0.1, Duration.ofMillis(300), false);
            assertThat(reasonOf(provider, shortDeadline)).isEqualTo(AiErrorReason.PROVIDER_TIMEOUT);
        }
    }

    @Test
    void stalledBodyIsCutOffByTheOverallDeadline() throws Exception {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write('{');
            out.flush();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            OllamaAiProvider provider = provider(server.chatUri());
            AiProviderCall shortDeadline = new AiProviderCall("m", "s", "u", 10, 0.1, Duration.ofMillis(400), false);
            assertThat(reasonOf(provider, shortDeadline)).isEqualTo(AiErrorReason.PROVIDER_TIMEOUT);
        }
    }

    @Test
    void connectionRefusedIsProviderUnavailable() throws Exception {
        URI closedPort;
        try (StubOllamaServer server = StubOllamaServer.replying(200, "{}")) {
            closedPort = server.chatUri();
        }
        OllamaAiProvider provider = provider(closedPort);
        AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                () -> provider.invoke(call("hello", false)));
        assertThat(failure.getReason()).isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE);
        assertThat(failure.getCause()).isNull();
        assertNoSecretOrPromptAnywhere(failure);
    }

    @Test
    void connectAndRequestTimeoutsFromTheJdkClientAreMappedToProviderTimeout() {
        ExecutionException connect = new ExecutionException(new HttpConnectTimeoutException("connect timed out"));
        ExecutionException request = new ExecutionException(new HttpTimeoutException("request timed out"));
        assertThat(OllamaAiProvider.translate(connect).getReason()).isEqualTo(AiErrorReason.PROVIDER_TIMEOUT);
        assertThat(OllamaAiProvider.translate(request).getReason()).isEqualTo(AiErrorReason.PROVIDER_TIMEOUT);
        assertThat(OllamaAiProvider.translate(new ExecutionException(new java.net.ConnectException("refused"))).getReason())
                .isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE);
        assertThat(OllamaAiProvider.translate(new ExecutionException(new OllamaBoundedBodySubscriber.BodyTooLargeException())).getReason())
                .isEqualTo(AiErrorReason.RESPONSE_TOO_LARGE);
        assertThat(OllamaAiProvider.translate(new ExecutionException(new IllegalStateException("x"))).getCause()).isNull();
    }

    @Test
    void interruptionIsMappedAndTheFlagIsPreserved() throws Exception {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            OllamaAiProvider provider = provider(server.chatUri());
            Thread.currentThread().interrupt();
            try {
                assertThat(reasonOf(provider, call("hello", false))).isEqualTo(AiErrorReason.INTERRUPTED);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted(); // clear the flag for the rest of the suite
            }
        }
    }

    @Test
    void requestSerializationFailureIsAGenericProviderFailureWithoutCause() throws Exception {
        ObjectMapper failing = new ObjectMapper() {
            private static final long serialVersionUID = 1L;

            @Override
            public byte[] writeValueAsBytes(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, "boom " + DUMMY_KEY);
            }
        };
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            OllamaAiProvider provider = new OllamaAiProvider(server.chatUri(), new AiSecret(DUMMY_KEY), Duration.ofSeconds(2),
                    MAX_REQUEST, MAX_RESPONSE, failing);
            created.add(provider);
            AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                    () -> provider.invoke(call("hello", false)));
            assertThat(failure.getReason()).isEqualTo(AiErrorReason.PROVIDER_FAILURE);
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage()).doesNotContain(DUMMY_KEY);
            assertThat(server.requests()).isEmpty();
        }
    }

    // ---- credential and construction guards

    @Test
    void credentialsThatAreNotPlainPrintableAsciiAreRefusedWithoutQuotingThem() {
        for (String bad : List.of("test-dummy\nkey-0123456789", "test-dummy key-0123456789", "test-dummy\u200Bkey-0123456789",
                "test-dummy\tkey", "t\u00E9st-dummy-key")) {
            assertThatThrownBy(() -> new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), new AiSecret(bad),
                    Duration.ofSeconds(1), MAX_REQUEST, MAX_RESPONSE, mapper))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("test-dummy").doesNotContain("dummy"));
        }
    }

    @Test
    void theTestConstructorAcceptsLoopbackEndpointsOnly() {
        for (String remote : List.of("https://ollama.com/v1/chat/completions", "http://example.org/x", "https://evil.example/v1",
                "http://10.0.0.5:8080/x")) {
            assertThatThrownBy(() -> new OllamaAiProvider(URI.create(remote), new AiSecret(DUMMY_KEY), Duration.ofSeconds(1),
                    MAX_REQUEST, MAX_RESPONSE, mapper)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void jdkHeaderLoggingModesAreRefusedAtConstruction() {
        String property = "jdk.httpclient.HttpClient.log";
        String previous = System.getProperty(property);
        try {
            for (String mode : List.of("headers", "requests,headers", "content", "trace", "all", "ERRORS,HEADERS")) {
                System.setProperty(property, mode);
                assertThatThrownBy(() -> new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), new AiSecret(DUMMY_KEY),
                        Duration.ofSeconds(1), MAX_REQUEST, MAX_RESPONSE, mapper))
                        .as(mode).isInstanceOf(IllegalStateException.class).hasMessageContaining(property);
            }
            System.setProperty(property, "errors,requests");
            new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), new AiSecret(DUMMY_KEY), Duration.ofSeconds(1),
                    MAX_REQUEST, MAX_RESPONSE, mapper).close();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void javaxNetDebugPlaintextModesAreRefusedAtConstruction() {
        String property = "javax.net.debug";
        String previous = System.getProperty(property);
        try {
            for (String mode : List.of("all", "ssl,plaintext", "ssl:record:plaintext", "ALL")) {
                System.setProperty(property, mode);
                assertThatThrownBy(() -> new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), new AiSecret(DUMMY_KEY),
                        Duration.ofSeconds(1), MAX_REQUEST, MAX_RESPONSE, mapper))
                        .as(mode).isInstanceOf(IllegalStateException.class).hasMessageContaining(property);
            }
            System.setProperty(property, "ssl:handshake");
            new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), new AiSecret(DUMMY_KEY), Duration.ofSeconds(1),
                    MAX_REQUEST, MAX_RESPONSE, mapper).close();
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void afterCloseTheProviderFailsSafelyAndCloseIsIdempotent() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            OllamaAiProvider provider = new OllamaAiProvider(server.chatUri(), new AiSecret(DUMMY_KEY), Duration.ofSeconds(2),
                    MAX_REQUEST, MAX_RESPONSE, mapper);
            assertThat(provider.invoke(call("hello", false)).text()).isNotBlank();
            provider.close();
            provider.close();
            AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                    () -> provider.invoke(call("hello again", false)));
            assertThat(failure.getReason()).isEqualTo(AiErrorReason.PROVIDER_UNAVAILABLE);
            assertThat(failure.getCause()).isNull();
            assertThat(server.requests()).as("no request after close").hasSize(1);
        }
    }

    // ---- byte limits: G-M3-1 (P-17..P-20)

    @Test
    void oversizedRequestIsRejectedBeforeAnyNetworkCall() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(reasonOf(provider, call("x".repeat(MAX_REQUEST), false))).isEqualTo(AiErrorReason.INPUT_TOO_LARGE);
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void requestLimitCountsUtf8BytesNotCharacters() throws Exception {
        // 1500 four-byte emoji = 1500 code points (3000 chars) but 6000 UTF-8 bytes, above the 4096-byte limit
        String emoji = "😀".repeat(1500);
        String cjk = "漢".repeat(1500); // 1500 chars, 4500 bytes
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(reasonOf(provider, call(emoji, false))).isEqualTo(AiErrorReason.INPUT_TOO_LARGE);
            assertThat(reasonOf(provider, call(cjk, false))).isEqualTo(AiErrorReason.INPUT_TOO_LARGE);
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void multibyteTextWithinTheByteLimitIsSentIntact() throws Exception {
        String text = "漢字 😀 café ü";
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            provider(server.chatUri()).invoke(call(text, false));
            JsonNode body = mapper.readTree(server.requests().get(0).body());
            assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo(text);
        }
    }

    @Test
    void oversizedChunkedResponseIsCutOffWithoutBuffering() throws Exception {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked, no Content-Length
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = new byte[1024];
                for (int i = 0; i < 4096; i++) {
                    out.write(chunk);
                }
            } catch (IOException expectedWhenClientCancels) {
                // the client closes the connection once the limit is exceeded
            }
        })) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(reasonOf(provider, call("hello", false))).isEqualTo(AiErrorReason.RESPONSE_TOO_LARGE);
        }
    }

    @Test
    void declaredContentLengthAboveTheLimitIsRejected() throws Exception {
        try (StubOllamaServer server = new StubOllamaServer(exchange -> {
            exchange.sendResponseHeaders(200, MAX_RESPONSE + 1000L);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(new byte[MAX_RESPONSE + 1000]);
            } catch (IOException expectedWhenClientCancels) {
                // the client rejects the response without reading the body
            }
        })) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(reasonOf(provider, call("hello", false))).isEqualTo(AiErrorReason.RESPONSE_TOO_LARGE);
        }
    }

    @Test
    void responseExactlyAtTheLimitIsAccepted() throws Exception {
        String prefix = "{\"choices\":[{\"message\":{\"content\":\"";
        String suffix = "\"}}]}";
        String body = prefix + "a".repeat(MAX_RESPONSE - prefix.length() - suffix.length()) + suffix;
        assertThat(body.length()).isEqualTo(MAX_RESPONSE);
        try (StubOllamaServer server = StubOllamaServer.replying(200, body)) {
            assertThat(provider(server.chatUri()).invoke(call("hello", false)).text()).hasSize(MAX_RESPONSE - prefix.length() - suffix.length());
        }
    }

    // ---- redirects and credential forwarding (P-22)

    @Test
    void redirectsAreNotFollowedAndTheCredentialIsNeverForwarded() throws Exception {
        try (StubOllamaServer attacker = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY);
             StubOllamaServer approved = new StubOllamaServer(exchange -> {
                 exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + attacker.port() + "/steal");
                 exchange.sendResponseHeaders(302, -1);
             })) {
            OllamaAiProvider provider = provider(approved.chatUri());
            AiException failure = org.assertj.core.api.Assertions.catchThrowableOfType(AiException.class,
                    () -> provider.invoke(call("hello", false)));
            assertThat(failure.getReason()).isEqualTo(AiErrorReason.PROVIDER_FAILURE);
            assertThat(approved.requests()).hasSize(1);
            assertThat(approved.requests().get(0).header("Authorization")).isEqualTo("Bearer " + DUMMY_KEY);
            assertThat(attacker.requests()).as("redirect target must receive nothing").isEmpty();
            assertNoSecretOrPromptAnywhere(failure);
        }
    }

    // ---- construction and status

    @Test
    void blankCredentialIsRejectedWithoutPrintingAnything() {
        assertThatThrownBy(() -> new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), AiSecret.empty(), Duration.ofSeconds(1),
                MAX_REQUEST, MAX_RESPONSE, mapper)).isInstanceOf(IllegalStateException.class).hasMessageContaining("api-key");
        assertThatThrownBy(() -> new OllamaAiProvider(URI.create("http://127.0.0.1:1/x"), null, Duration.ofSeconds(1),
                MAX_REQUEST, MAX_RESPONSE, mapper)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nameAndStatusExposeNeitherTheCredentialNorTheEndpoint() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, "{}")) {
            OllamaAiProvider provider = provider(server.chatUri());
            assertThat(provider.name()).isEqualTo("ollama");
            assertThat(provider.status().ready()).isTrue();
            assertThat(provider.status().detail()).doesNotContain(DUMMY_KEY).doesNotContain("127.0.0.1");
        }
    }

    @Test
    void successfulCallsLogNothingSensitive() throws Exception {
        try (StubOllamaServer server = StubOllamaServer.replying(200, StubOllamaServer.OK_BODY)) {
            provider(server.chatUri()).invoke(call(SECRET_PROMPT, true));
            assertNoSecretOrPromptAnywhere(null);
        }
    }
}
