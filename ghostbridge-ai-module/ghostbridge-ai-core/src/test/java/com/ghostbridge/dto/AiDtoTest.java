package com.ghostbridge.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import com.ghostbridge.constants.GhostBridgeAiLiterals;
import com.ghostbridge.exception.AiErrorReason;
import com.ghostbridge.exception.AiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiDtoTest {

    @Test
    void requestDefaultsAndDefensiveCopy() {
        Map<String, String> vars = new HashMap<>(Map.of("a", "b"));
        AiRequest request = new AiRequest("p", null, vars, null, null, null, null);
        vars.put("c", "d");
        assertThat(request.variables()).containsOnlyKeys("a");
        assertThat(request.options()).isEqualTo(AiGenerationOptions.defaults());
        assertThat(new AiRequest("p", null, null, null, null, null, null).variables()).isEmpty();
        assertThatThrownBy(() -> request.variables().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usageTotalsOnlyWhatWasReported() {
        assertThat(AiUsage.unknown().totalTokens()).isEmpty();
        assertThat(new AiUsage(3, null, null).totalTokens()).contains(3);
        assertThat(new AiUsage(null, 4, null).totalTokens()).contains(4);
        assertThat(new AiUsage(3, 4, null).withEstimatedCost(BigDecimal.ONE).estimatedCost()).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void providerResultNeverHasNullUsage() {
        assertThat(new AiProviderResult("t", null).usage()).isEqualTo(AiUsage.unknown());
    }

    @Test
    void responseStructuredOutputIsOptional() {
        assertThat(new AiResponse("r", "p", "m", "t", null, AiUsage.unknown(), 1).structuredOutput()).isEmpty();
    }

    @Test
    void nullVariableNameOrValueIsTypedInvalidInput() {
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("a", null);
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "v");
        for (Map<String, String> bad : java.util.List.of(nullValue, nullKey)) {
            assertThatThrownBy(() -> new AiRequest("p", null, bad, null, null, null, null))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INVALID_INPUT));
        }
    }

    @Test
    void variableCountAndNameLengthAreBoundedAs413() {
        Map<String, String> many = new HashMap<>();
        for (int i = 0; i <= GhostBridgeAiLiterals.MAX_VARIABLES; i++) {
            many.put("v" + i, "x");
        }
        assertThatThrownBy(() -> new AiRequest("p", null, many, null, null, null, null))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INPUT_TOO_LARGE));
        Map<String, String> longName = Map.of("n".repeat(GhostBridgeAiLiterals.MAX_VARIABLE_NAME_LENGTH + 1), "x");
        assertThatThrownBy(() -> new AiRequest("p", null, longName, null, null, null, null))
                .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INPUT_TOO_LARGE));
    }

    @Test
    void requesterIdIsValidated() {
        assertThat(new AiRequest("p", null, null, null, null, null, "user-123").requesterId()).isEqualTo("user-123");
        for (String bad : java.util.List.of("a\nb", "a\u200Bb", "x".repeat(GhostBridgeAiLiterals.MAX_REQUESTER_ID_LENGTH + 1))) {
            assertThatThrownBy(() -> new AiRequest("p", null, null, null, null, null, bad))
                    .isInstanceOfSatisfying(AiException.class, e -> assertThat(e.getReason()).isEqualTo(AiErrorReason.INVALID_INPUT));
        }
    }

    @Test
    void negativeUsageIsTreatedAsUnknownAndTotalsSaturate() {
        assertThat(new AiUsage(-5, 10, null).inputTokens()).isNull();
        assertThat(new AiUsage(-5, -1, null).totalTokens()).isEmpty();
        assertThat(new AiUsage(Integer.MAX_VALUE, Integer.MAX_VALUE, null).totalTokens()).contains(Integer.MAX_VALUE);
    }
}
