package com.ensemble.user.web;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces {@link MaxUtf8Bytes}: a non-null {@code String} is valid only if its UTF-8 encoding is
 * at most {@code max} bytes. A {@code null} value is treated as valid so presence stays the job of
 * {@code @NotBlank} (see {@link MaxUtf8Bytes}).
 */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

	private int max;

	@Override
	public void initialize(MaxUtf8Bytes constraint) {
		this.max = constraint.value();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		return value.getBytes(StandardCharsets.UTF_8).length <= max;
	}
}
