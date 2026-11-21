package com.ghostbridge.service.impl;

import com.ghostbridge.config.AiProperties;
import com.ghostbridge.config.AiTestSupport;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.dto.AiProviderCall;
import com.ghostbridge.dto.AiProviderResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {
    private final MockAiProvider provider = new MockAiProvider(AiTestSupport.properties("provider", "mock").mock());

    private static AiProviderCall call(String user, boolean json) {
        return new AiProviderCall("mock-model", "system", user, 100, 0.2, Duration.ofSeconds(1), json);
    }

    @Test
    void sameCallGivesSameOutput() {
        assertThat(provider.invoke(call("hello", true))).isEqualTo(provider.invoke(call("hello", true)));
    }

    @Test
    void differentInputGivesDifferentOutput() {
        assertThat(provider.invoke(call("a", false)).text()).isNotEqualTo(provider.invoke(call("b", false)).text());
    }

    @Test
    void jsonModeReturnsJsonObject() {
        assertThat(provider.invoke(call("x", true)).text()).startsWith("{\"summary\":\"mock-");
        assertThat(provider.invoke(call("x", false)).text()).startsWith("mock-response-");
    }

    @Test
    void reportsSimulatedUsageAndReadiness() {
        AiProviderResult result = provider.invoke(call("x".repeat(40), false));
        assertThat(result.usage().inputTokens()).isEqualTo((6 + 40 + 3) / 4);
        assertThat(result.usage().outputTokens()).isPositive();
        assertThat(provider.status().ready()).isTrue();
        assertThat(provider.name()).isEqualTo(GhostBridgeAiLiterals.PROVIDER_MOCK);
    }

    @Test
    void configuredResponseOverridesGeneratedOutput() {
        MockAiProvider canned = new MockAiProvider(AiTestSupport.properties("provider", "mock", "mock.response", "{\"summary\":\"fixed\"}").mock());
        assertThat(canned.invoke(call("x", true)).text()).isEqualTo("{\"summary\":\"fixed\"}");
    }
}
