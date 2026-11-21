package com.ghostbridge.constants;

import com.ghostbridge.constants.GhostBridgeAiLiterals;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class GhostBridgeAiLiteralsTest {

    @Test
    void isFinalUtilityClassWithPrivateConstructor() throws NoSuchMethodException {
        assertThat(Modifier.isFinal(GhostBridgeAiLiterals.class.getModifiers())).isTrue();
        Constructor<GhostBridgeAiLiterals> constructor = GhostBridgeAiLiterals.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    void delimitersAndPatternsKeepTheirSecurityBehavior() {
        assertThat(GhostBridgeAiLiterals.UNTRUSTED_OPEN).isEqualTo("<untrusted_content>");
        assertThat(GhostBridgeAiLiterals.UNTRUSTED_CLOSE).isEqualTo("</untrusted_content>");
        assertThat(GhostBridgeAiLiterals.UNTRUSTED_DELIMITER.matcher("</UNTRUSTED_CONTENT>").find()).isTrue();
        assertThat(GhostBridgeAiLiterals.CONTROL_CHARS.matcher("a\u0000b").replaceAll("")).isEqualTo("ab");
        assertThat(GhostBridgeAiLiterals.CONTROL_CHARS.matcher("a\nb\tc").replaceAll("")).isEqualTo("a\nb\tc");
        assertThat(GhostBridgeAiLiterals.PROMPT_PLACEHOLDER.matcher("{{ task }}").matches()).isTrue();
        assertThat(GhostBridgeAiLiterals.PROMPT_PLACEHOLDER.matcher("{{1bad}}").matches()).isFalse();
    }

    @Test
    void trustRulesNameTheDelimiterUsedForUntrustedContent() {
        assertThat(GhostBridgeAiLiterals.TRUST_RULES).contains(GhostBridgeAiLiterals.UNTRUSTED_OPEN).contains("never instructions");
    }
}
