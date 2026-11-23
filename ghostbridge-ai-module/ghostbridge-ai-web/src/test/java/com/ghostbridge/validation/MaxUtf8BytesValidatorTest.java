package com.ghostbridge.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesValidatorTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private record ThreeBytes(@MaxUtf8Bytes(3) String value) {
    }

    private record FourBytes(@MaxUtf8Bytes(4) String value) {
    }

    private static boolean fitsThree(String value) {
        return VALIDATOR.validate(new ThreeBytes(value)).isEmpty();
    }

    @Test
    void countsBytesNotCharacters() {
        assertThat(fitsThree("abc")).isTrue();
        assertThat(fitsThree("abcd")).isFalse();
        assertThat(fitsThree("é")).isTrue();          // 1 char, 2 bytes
        assertThat(fitsThree("éé")).isFalse();   // 2 chars, 4 bytes
        assertThat(fitsThree("€")).isTrue();          // euro sign: 3 bytes
        assertThat(fitsThree("€€")).isFalse();
        assertThat(fitsThree("😀")).isFalse();   // emoji: 4 bytes (surrogate pair)
        assertThat(VALIDATOR.validate(new FourBytes("😀"))).isEmpty();
    }

    @Test
    void agreesWithTheJdkEncoderAndTreatsLoneSurrogatesConservatively() {
        String mixed = "aé€😀z";
        assertThat(MaxUtf8BytesValidator.utf8Length(mixed)).isEqualTo(mixed.getBytes(StandardCharsets.UTF_8).length);
        assertThat(MaxUtf8BytesValidator.utf8Length("\uD83D")).isEqualTo(3);
        assertThat(MaxUtf8BytesValidator.utf8Length("\uDE00\uD83D")).isEqualTo(6);
    }

    @Test
    void nullIsValidAndViolationsCarryTheLimitInTheMessage() {
        assertThat(fitsThree(null)).isTrue();
        assertThat(VALIDATOR.validate(new ThreeBytes("abcd"))).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("3 bytes"));
    }
}
