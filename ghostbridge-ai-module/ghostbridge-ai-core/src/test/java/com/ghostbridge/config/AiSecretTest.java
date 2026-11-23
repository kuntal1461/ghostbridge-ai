package com.ghostbridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSecretTest {
    /** Synthetic dummy credential: not a real key. */
    private static final String DUMMY = "test-dummy-key-0123456789";

    @Test
    void everyStringAndJsonRepresentationIsMasked() throws Exception {
        AiSecret secret = new AiSecret(DUMMY);
        ObjectMapper mapper = new ObjectMapper();
        assertThat(secret.toString()).isEqualTo("****");
        assertThat(String.valueOf(secret)).doesNotContain(DUMMY);
        assertThat(mapper.writeValueAsString(secret)).isEqualTo("\"****\"");
        assertThat(mapper.writeValueAsString(Map.of("k", secret))).doesNotContain(DUMMY);
        assertThat(List.of(secret).toString()).doesNotContain(DUMMY);
        assertThat(String.format("%s|%s", secret, Map.of("a", secret))).doesNotContain(DUMMY);
    }

    @Test
    void valueIsAvailableOnlyThroughRevealAndPresenceCheckNeverShowsIt() {
        AiSecret secret = new AiSecret("  " + DUMMY + "  ");
        assertThat(secret.isPresent()).isTrue();
        assertThat(secret.reveal()).isEqualTo(DUMMY);
        assertThat(AiSecret.empty().isPresent()).isFalse();
        assertThat(new AiSecret(null).isPresent()).isFalse();
        assertThat(new AiSecret("   ").isPresent()).isFalse();
    }

    @Test
    void headerSafetyAcceptsOnlyPlainPrintableAsciiWithoutSpaces() {
        assertThat(new AiSecret(DUMMY).isHeaderSafe()).isTrue();
        assertThat(new AiSecret("test-dummy-key.DEF_123~+/=").isHeaderSafe()).isTrue();
        assertThat(AiSecret.empty().isHeaderSafe()).isFalse();
        for (String bad : List.of("a b", "a\nb", "a\rb", "a\tb", "a\u0000b", "a\u200Bb", "caf\u00E9", "a\u007Fb")) {
            assertThat(new AiSecret(bad).isHeaderSafe()).as(bad.replaceAll("[^ -~]", "?")).isFalse();
        }
    }

    @Test
    void equalityAndHashCodeDoNotLeakTheValue() {
        assertThat(new AiSecret(DUMMY)).isEqualTo(new AiSecret(DUMMY)).isNotEqualTo(new AiSecret("other"));
        assertThat(new AiSecret(DUMMY).hashCode()).isEqualTo(new AiSecret("other").hashCode());
    }

    @Test
    void propertiesRecordsNeverPrintTheCredential() {
        AiProperties props = AiTestSupport.properties("provider", "ollama", "model-id", "m", "ollama.api-key", DUMMY);
        assertThat(props.ollama().apiKey().reveal()).isEqualTo(DUMMY);
        assertThat(props.toString()).doesNotContain(DUMMY).contains("****");
        assertThat(props.ollama().toString()).doesNotContain(DUMMY);
    }

    @Test
    void bindFailuresDoNotQuoteTheCredential() {
        assertThatThrownBy(() -> AiTestSupport.properties("provider", "ollama", "model-id", "m", "ollama.api-key", DUMMY,
                "ollama.mode", "local")).isInstanceOf(BindException.class).satisfies(e -> {
            for (Throwable t = e; t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain(DUMMY);
            }
        });
    }
}
