package com.ensemble.user.web;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that a {@code String}'s UTF-8 <strong>byte</strong> length does not exceed
 * {@link #value()}. Distinct from {@code @Size}, which counts characters: bcrypt only hashes the
 * first 72 <em>bytes</em> of its input, so a multi-byte password within a 72-character limit could
 * still be silently truncated. This constraint enforces the true 72-byte ceiling (issue #14).
 *
 * <p>A {@code null} value is considered valid — presence is the concern of {@code @NotBlank},
 * following the Bean Validation convention that each constraint validates one thing.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

	/** Maximum allowed UTF-8 byte length. */
	int value();

	String message() default "must not exceed {value} UTF-8 bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
