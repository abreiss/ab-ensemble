package com.ensemble.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Contract tests for the {@link Outfit} value object: both fields are always non-null. */
class OutfitTest {

	@Test
	void nullFields_normalizeToEmpty() {
		Outfit outfit = new Outfit(null, null);

		assertThat(outfit.itemIds()).isEmpty();
		assertThat(outfit.reason()).isEmpty();
	}

	@Test
	void empty_hasNoIdsAndBlankReason() {
		assertThat(Outfit.empty().itemIds()).isEmpty();
		assertThat(Outfit.empty().reason()).isEmpty();
	}

	@Test
	void itemIds_areDefensivelyCopied() {
		List<String> source = new ArrayList<>(List.of("a"));
		Outfit outfit = new Outfit(source, "reason");
		source.add("b");

		// The outfit is not mutated by later changes to the source list.
		assertThat(outfit.itemIds()).containsExactly("a");
		assertThatThrownBy(() -> outfit.itemIds().add("c"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void twoArgConstructor_defaultsToEmptyRationale() {
		// Back-compat: the pre-rationale constructor still works and carries no rationale.
		Outfit outfit = new Outfit(List.of("a"), "reason");

		assertThat(outfit.rationaleById()).isEmpty();
		assertThat(outfit.rationaleFor("a")).isEmpty();
	}

	@Test
	void nullRationaleMap_normalizesToEmpty() {
		Outfit outfit = new Outfit(List.of("a"), "reason", null);

		assertThat(outfit.rationaleById()).isEmpty();
	}

	@Test
	void rationaleFor_returnsPerItemText_andEmptyForUnknownId() {
		Outfit outfit = new Outfit(List.of("a", "b"), "reason",
			java.util.Map.of("a", "breathes on a warm morning", "b", "earthy tone"));

		assertThat(outfit.rationaleFor("a")).isEqualTo("breathes on a warm morning");
		assertThat(outfit.rationaleFor("b")).isEqualTo("earthy tone");
		// An id with no rationale (or unknown) degrades to blank, never null.
		assertThat(outfit.rationaleFor("missing")).isEmpty();
	}

	@Test
	void rationaleById_isDefensivelyCopiedAndUnmodifiable() {
		java.util.Map<String, String> source = new java.util.HashMap<>();
		source.put("a", "one");
		Outfit outfit = new Outfit(List.of("a"), "reason", source);
		source.put("b", "two");

		assertThat(outfit.rationaleById()).containsOnlyKeys("a");
		assertThatThrownBy(() -> outfit.rationaleById().put("c", "three"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void empty_hasNoRationale() {
		assertThat(Outfit.empty().rationaleById()).isEmpty();
	}
}
