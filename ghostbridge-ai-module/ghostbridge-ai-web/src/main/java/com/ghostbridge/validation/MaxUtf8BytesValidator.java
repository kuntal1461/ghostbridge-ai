package com.ghostbridge.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Counts UTF-8 bytes without allocating; lone surrogates are counted as three bytes (the conservative choice). */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {
    private int max;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        this.max = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || utf8Length(value) <= max;
    }

    static long utf8Length(CharSequence text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }
}
