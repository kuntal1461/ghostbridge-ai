package com.ghostbridge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void masksPasswordsInKeyValueAndJson() {
        assertThat(SensitiveDataMasker.mask("user=a&password=hunter2&x=1")).doesNotContain("hunter2");
        assertThat(SensitiveDataMasker.mask("{\"password\":\"hunter2\",\"name\":\"a\"}"))
                .doesNotContain("hunter2").contains("\"name\":\"a\"");
    }

    @Test
    void masksAuthorizationHeaderIncludingScheme() {
        String masked = SensitiveDataMasker.mask("Authorization: Bearer abc.def.ghi");
        assertThat(masked).doesNotContain("abc.def.ghi").doesNotContain("Bearer abc");
    }

    @Test
    void masksApiKeysAndTokens() {
        assertThat(SensitiveDataMasker.mask("api_key=SECRET123 token: TOK456"))
                .doesNotContain("SECRET123").doesNotContain("TOK456");
    }

    @Test
    void masksStandaloneBearerToken() {
        assertThat(SensitiveDataMasker.mask("sent Bearer eyJhbGciOi.payload.sig ok")).doesNotContain("eyJhbGciOi");
    }

    @Test
    void leavesPlainTextAndNullUntouched() {
        assertThat(SensitiveDataMasker.mask("hello world")).isEqualTo("hello world");
        assertThat(SensitiveDataMasker.mask(null)).isNull();
    }

    @Test
    void masksSensitiveHeaderValues() {
        assertThat(SensitiveDataMasker.maskHeader("Authorization", "Bearer x")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskHeader("Cookie", "a=b")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskHeader("Accept", "application/json")).isEqualTo("application/json");
    }
}
