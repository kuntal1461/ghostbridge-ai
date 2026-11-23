package com.ghostbridge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOllamaPropertiesTest {
    private static final String DUMMY = "test-dummy-key-0123456789";

    private static AiProperties ollama(String... extra) {
        String[] base = {"provider", "ollama", "model-id", "configured-model"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return AiTestSupport.properties(all);
    }

    @Test
    void restrictiveDefaultsAndValidatedEndpoint() {
        AiProperties.Ollama settings = ollama().ollama();
        assertThat(settings.mode()).isEqualTo("cloud");
        assertThat(settings.baseUrl()).isEqualTo("https://ollama.com");
        assertThat(settings.allowedHosts()).containsExactly("ollama.com");
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.maxRequestBytes()).isEqualTo(262144);
        assertThat(settings.maxResponseBytes()).isEqualTo(1048576);
        assertThat(settings.apiKey().isPresent()).isFalse();
        assertThat(settings.chatCompletionsUri()).hasToString("https://ollama.com/v1/chat/completions");
    }

    @Test
    void modelIdIsRequiredAndNeverDefaultedForOllama() {
        assertThatThrownBy(() -> AiTestSupport.properties("provider", "ollama")).isInstanceOf(BindException.class)
                .hasRootCauseMessage("ghostbridge.ai.model-id is required when provider=ollama");
    }

    @Test
    void trailingSlashAndHostCaseAreAccepted() {
        assertThat(ollama("ollama.base-url", "https://OLLAMA.com/").ollama().chatCompletionsUri())
                .hasToString("https://OLLAMA.com/v1/chat/completions");
        assertThat(ollama("ollama.base-url", "https://ollama.com:443").ollama().chatCompletionsUri().getPort()).isEqualTo(443);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://ollama.com",                       // not https
            "ftp://ollama.com",
            "https://evil.example",                    // host not allow-listed
            "https://ollama.com.evil.example",         // look-alike host
            "https://user:pw@ollama.com",              // user-info
            "https://ollama.com/path",                 // path
            "https://ollama.com?x=1",                  // query
            "https://ollama.com#frag",                 // fragment
            "https://ollama.com:8443",                 // custom port
            "https://127.0.0.1",                       // IP literal, not allow-listed
            "https://localhost",
            "ollama.com",                              // no scheme
            "",
            "https://ollama .com"})
    void unsafeDestinationsAreRejectedAtStartup(String url) {
        assertThatThrownBy(() -> ollama("ollama.base-url", url)).isInstanceOf(BindException.class);
    }

    @Test
    void destinationErrorsNeverEchoTheUrlOrAnyUserInfo() {
        assertThatThrownBy(() -> ollama("ollama.base-url", "https://user:" + DUMMY + "@evil.example")).satisfies(e -> {
            for (Throwable t = e; t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(DUMMY).doesNotContain("evil.example");
            }
        });
    }

    @Test
    void surroundingWhitespaceIsTrimmedConsistentlyForValidationAndUse() {
        AiProperties.Ollama settings = ollama("ollama.base-url", "  https://ollama.com  ").ollama();
        assertThat(settings.baseUrl()).isEqualTo("https://ollama.com");
        assertThat(settings.chatCompletionsUri()).hasToString("https://ollama.com/v1/chat/completions");
    }

    @Test
    void byteLimitsHaveAFloor() {
        assertThatThrownBy(() -> ollama("ollama.max-request-bytes", "10")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> ollama("ollama.max-response-bytes", "0")).isInstanceOf(BindException.class);
    }

    @Test
    void additionalHostsMustBeAllowListedExplicitly() {
        AiProperties props = ollama("ollama.base-url", "https://proxy.example.org", "ollama.allowed-hosts", "ollama.com,proxy.example.org");
        assertThat(props.ollama().chatCompletionsUri()).hasToString("https://proxy.example.org/v1/chat/completions");
    }

    @Test
    void onlyCloudModeIsSupported() {
        assertThatThrownBy(() -> ollama("ollama.mode", "local")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> ollama("ollama.mode", "")).isInstanceOf(BindException.class);
        assertThat(ollama("ollama.mode", "CLOUD").ollama().mode()).isEqualTo("cloud");
    }

    @Test
    void timeoutsAndByteLimitsAreBounded() {
        assertThatThrownBy(() -> ollama("ollama.connect-timeout", "0s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> ollama("ollama.connect-timeout", "-1s")).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> ollama("ollama.connect-timeout", "10m")).isInstanceOf(BindException.class);
        assertThat(ollama("ollama.max-request-bytes", "2048", "ollama.max-response-bytes", "4096").ollama().maxRequestBytes()).isEqualTo(2048);
    }

    @Test
    void withApiKeyKeepsEverythingElseAndStaysMasked() {
        AiProperties.Ollama base = ollama().ollama();
        AiProperties.Ollama keyed = base.withApiKey(new AiSecret(DUMMY));
        assertThat(keyed.apiKey().isPresent()).isTrue();
        assertThat(keyed.baseUrl()).isEqualTo(base.baseUrl());
        assertThat(keyed.toString()).doesNotContain(DUMMY);
    }
}
