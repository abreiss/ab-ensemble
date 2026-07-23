package com.ensemble.stylist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextBounds#cap(String, int)} — the deterministic
 * output-length backstop applied to the stylist's free-text {@code reason} and
 * per-piece {@code rationale} before they cross the DTO boundary. This is
 * <strong>critical logic</strong> (output truncation) and must reach 100% branch
 * coverage: the null branch, the at/below-cap pass-through branch, and the
 * above-cap truncation branch are each exercised here.
 */
class TextBoundsTest {

	@Test
	void cap_belowMax_returnsUnchanged() {
		assertThat(TextBounds.cap("short", 10)).isEqualTo("short");
	}

	@Test
	void cap_atMax_returnsUnchanged() {
		String exact = "abcde";
		assertThat(TextBounds.cap(exact, 5)).isEqualTo("abcde");
	}

	@Test
	void cap_aboveMax_truncatesToMaxLength() {
		String truncated = TextBounds.cap("abcdefghij", 4);
		assertThat(truncated).isEqualTo("abcd");
		assertThat(truncated).hasSize(4);
	}

	@Test
	void cap_null_returnsNull() {
		assertThat(TextBounds.cap(null, 10)).isNull();
	}

	@Test
	void cap_blankWithinMax_returnsUnchanged() {
		assertThat(TextBounds.cap("   ", 10)).isEqualTo("   ");
	}
}
