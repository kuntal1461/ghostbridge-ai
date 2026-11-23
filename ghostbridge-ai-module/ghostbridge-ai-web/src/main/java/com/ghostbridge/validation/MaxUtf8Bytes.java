package com.ghostbridge.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotated string must not be longer than {@link #value()} bytes when encoded as UTF-8 (null is valid). */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {
    int value();

    String message() default "must not exceed {value} bytes (UTF-8)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
