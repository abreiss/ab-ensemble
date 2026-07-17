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

	// --- Per-item rationale (Unit 2): the `pieces:[{itemId, rationale}]` shape ---

	@Test
	void pieces_parsesIdsInOrderAndRationaleMap() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"brunch-ready\",\"pieces\":["
				+ "{\"itemId\":\"a\",\"rationale\":\"breathes on a warm morning\"},"
				+ "{\"itemId\":\"b\",\"rationale\":\"earthy tone plays off the shoes\"}]}");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.reason()).isEqualTo("brunch-ready");
		assertThat(outfit.rationaleFor("a")).isEqualTo("breathes on a warm morning");
		assertThat(outfit.rationaleFor("b")).isEqualTo("earthy tone plays off the shoes");
	}

	@Test
	void pieces_missingRationale_defaultsToBlankForThatId() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"x\",\"pieces\":[{\"itemId\":\"a\"},{\"itemId\":\"b\",\"rationale\":\"warm\"}]}");

		assertThat(outfit.itemIds()).containsExactly("a", "b");
		assertThat(outfit.rationaleFor("a")).isEmpty();
		assertThat(outfit.rationaleFor("b")).isEqualTo("warm");
	}

	@Test
	void pieces_rationaleNotTextual_defaultsToBlank() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"x\",\"pieces\":[{\"itemId\":\"a\",\"rationale\":42}]}");

		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.rationaleFor("a")).isEmpty();
	}

	@Test
	void pieces_pieceMissingItemId_isSkipped() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"x\",\"pieces\":[{\"rationale\":\"orphan\"},{\"itemId\":\"b\",\"rationale\":\"kept\"}]}");

		assertThat(outfit.itemIds()).containsExactly("b");
		assertThat(outfit.rationaleFor("b")).isEqualTo("kept");
	}

	@Test
	void pieces_nonTextualItemId_isSkipped() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"x\",\"pieces\":[{\"itemId\":7,\"rationale\":\"num\"},{\"itemId\":\"b\"}]}");

		// A present-but-non-textual itemId is not a usable id, so the piece is skipped.
		assertThat(outfit.itemIds()).containsExactly("b");
	}

	@Test
	void pieces_nonObjectElement_isSkipped() {
		Outfit outfit = OutfitParser.parse(
			"{\"reason\":\"x\",\"pieces\":[\"just-a-string\",{\"itemId\":\"b\",\"rationale\":\"kept\"}]}");

		assertThat(outfit.itemIds()).containsExactly("b");
	}

	@Test
	void pieces_takePrecedenceOverLegacyItemIds() {
		Outfit outfit = OutfitParser.parse(
			"{\"itemIds\":[\"legacy\"],\"reason\":\"x\",\"pieces\":[{\"itemId\":\"a\",\"rationale\":\"r\"}]}");

		// When the model emits the richer `pieces`, ids come from it — not the legacy array.
		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.rationaleFor("a")).isEqualTo("r");
	}

	@Test
	void piecesNotAnArray_fallsBackToLegacyItemIds() {
		Outfit outfit = OutfitParser.parse(
			"{\"itemIds\":[\"a\"],\"reason\":\"x\",\"pieces\":\"nope\"}");

		assertThat(outfit.itemIds()).containsExactly("a");
		assertThat(outfit.rationaleById()).isEmpty();
	}

	@Test
	void emptyPiecesArray_yieldsNoIds() {
		Outfit outfit = OutfitParser.parse("{\"reason\":\"x\",\"pieces\":[]}");

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isEqualTo("x");
	}
}
