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
}
