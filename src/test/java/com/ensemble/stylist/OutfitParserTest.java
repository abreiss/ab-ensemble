package com.ensemble.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Branch-exhaustive tests for the defensive forced-output parser. Every malformed
 * or partial shape must yield a safe {@link Outfit} rather than throwing — the
 * parser is critical logic (100% branch) because it sits between an untrusted
 * model response and the grounding guardrail.
 */
class OutfitParserTest {

	@Test
	void validJson_parsesIdsAndReason() {
		Outfit outfit = OutfitParser.parse(
			"{\"itemIds\":[\"a\",\"b\"],\"reason\":\"navy on navy reads intentional\"}");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("navy on navy reads intentional");
	}

	@Test
	void nullJson_yieldsEmptyOutfit() {
		Outfit outfit = OutfitParser.parse(null);

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isEmpty();
	}

	@Test
	void blankJson_yieldsEmptyOutfit() {
		assertThat(OutfitParser.parse("   ").itemIds()).isEmpty();
	}

	@Test
	void malformedJson_yieldsEmptyOutfitWithoutThrowing() {
		assertThatCode(() -> OutfitParser.parse("{not valid json")).doesNotThrowAnyException();
		assertThat(OutfitParser.parse("{not valid json").itemIds()).isEmpty();
	}

	@Test
	void absentItemIds_yieldsEmptyIdsButKeepsReason() {
		Outfit outfit = OutfitParser.parse("{\"reason\":\"just the reason\"}");

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isEqualTo("just the reason");
	}

	@Test
	void absentReason_yieldsEmptyReasonButKeepsIds() {
		Outfit outfit = OutfitParser.parse("{\"itemIds\":[\"a\"]}");

		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.reason()).isEmpty();
	}

	@Test
	void itemIdsNotAnArray_yieldsEmptyIds() {
		assertThat(OutfitParser.parse("{\"itemIds\":\"a\",\"reason\":\"x\"}").itemIds()).isEmpty();
	}

	@Test
	void itemIdsWithNonStringElements_keepsOnlyStrings() {
		Outfit outfit = OutfitParser.parse("{\"itemIds\":[\"a\",7,null,\"b\"],\"reason\":\"x\"}");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
	}

	@Test
	void reasonNotTextual_yieldsEmptyReason() {
		assertThat(OutfitParser.parse("{\"itemIds\":[\"a\"],\"reason\":42}").reason()).isEmpty();
	}

	@Test
	void extraUnknownFields_areIgnored() {
		Outfit outfit = OutfitParser.parse(
			"{\"itemIds\":[\"a\"],\"reason\":\"x\",\"confidence\":0.9,\"notes\":\"ignore me\"}");

		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.reason()).isEqualTo("x");
	}
}
