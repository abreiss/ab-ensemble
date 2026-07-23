package com.ensemble.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@code @MaxUtf8Bytes} constraint (issue #14): a password must fit bcrypt's
 * <strong>72-byte</strong> input limit, measured in UTF-8 <em>bytes</em> (not characters) so a
 * multi-byte password is never silently truncated. Exercised through the real Bean Validation
 * engine ({@link Validator#validateValue}) so the annotation → validator wiring is proven, with
 * 100% branch coverage on {@link MaxUtf8BytesValidator#isValid} (null-valid + at/over boundary).
 */
class MaxUtf8BytesValidatorTest {

	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();

	/** Field carrying the constraint; {@code validateValue} checks candidates against it. */
	@SuppressWarnings("unused")
	private static final class Holder {
		@MaxUtf8Bytes(72)
		private String password;
	}

	private static boolean isValid(String candidate) {
		return VALIDATOR.validateValue(Holder.class, "password", candidate).isEmpty();
	}

	@Test
	void accepts72Bytes() {
		// Arrange: exactly 72 single-byte (ASCII) characters == 72 UTF-8 bytes.
		String at72 = "a".repeat(72);

		// Act + Assert
		assertThat(isValid(at72)).isTrue();
	}

	@Test
	void rejects73Bytes() {
		// Arrange: one byte over the bcrypt limit.
		String over = "a".repeat(73);

		// Act + Assert
		assertThat(isValid(over)).isFalse();
	}

	@Test
	void countsMultiByteCharsAsBytes() {
		// Arrange: "é" (U+00E9) is 2 UTF-8 bytes. 36 of them == 72 bytes (valid); 37 == 74 bytes
		// (invalid) while still only 37 characters — proving the count is by BYTES, not chars.
		String at72Bytes = "é".repeat(36);
		String over72Bytes = "é".repeat(37);

		// Act + Assert
		assertThat(isValid(at72Bytes)).isTrue();
		assertThat(isValid(over72Bytes)).isFalse();
	}

	@Test
	void nullIsValidDelegatedToNotBlank() {
		// Act + Assert: null passes this constraint — presence is @NotBlank's job, not ours.
		assertThat(isValid(null)).isTrue();
	}
}
