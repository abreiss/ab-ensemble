package com.ensemble.wardrobe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the category taxonomy constant and its pure {@code normalize}
 * fallback — critical logic requiring 100% branch coverage (AGENTS.md /
 * TESTING.md). Covers every canonical value, representative legacy synonyms,
 * casing/whitespace variants, and unrecognized/blank/null input, all of which
 * must land on a taxonomy value or {@code "Other"} and never throw.
 */
class CategoryTaxonomyTest {

	@Test
	void values_isTheOrderedEightValueTaxonomy() {
		assertThat(CategoryTaxonomy.values())
			.containsExactly("Jacket", "Top", "Bottom", "Dress", "Shoes", "Jewelry", "Accessory", "Other");
	}

	// --- Canonical values map to themselves ---

	@Test
	void normalize_canonicalValues_mapToThemselves() {
		for (String value : CategoryTaxonomy.values()) {
			assertThat(CategoryTaxonomy.normalize(value)).isEqualTo(value);
		}
	}

	// --- Legacy synonyms (starter map, assumption A1.12) ---

	@Test
	void normalize_bottomSynonyms_mapToBottom() {
		assertThat(CategoryTaxonomy.normalize("chinos")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("jeans")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("pants")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("trousers")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("shorts")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("skirt")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize("leggings")).isEqualTo("Bottom");
	}

	@Test
	void normalize_topSynonyms_mapToTop() {
		assertThat(CategoryTaxonomy.normalize("shirt")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("T-Shirt")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("tshirt")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("tee")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("sweater")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("sweatshirt")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("hoodie")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("blouse")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("polo")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("knit")).isEqualTo("Top");
	}

	@Test
	void normalize_jacketSynonyms_mapToJacket() {
		assertThat(CategoryTaxonomy.normalize("blazer")).isEqualTo("Jacket");
		assertThat(CategoryTaxonomy.normalize("coat")).isEqualTo("Jacket");
		assertThat(CategoryTaxonomy.normalize("parka")).isEqualTo("Jacket");
		assertThat(CategoryTaxonomy.normalize("overcoat")).isEqualTo("Jacket");
	}

	@Test
	void normalize_dressSynonyms_mapToDress() {
		assertThat(CategoryTaxonomy.normalize("gown")).isEqualTo("Dress");
	}

	@Test
	void normalize_shoesSynonyms_mapToShoes() {
		assertThat(CategoryTaxonomy.normalize("sneakers")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("shoe")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("boots")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("loafers")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("sandals")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("heels")).isEqualTo("Shoes");
		assertThat(CategoryTaxonomy.normalize("footwear")).isEqualTo("Shoes");
	}

	@Test
	void normalize_jewelrySynonyms_mapToJewelry() {
		assertThat(CategoryTaxonomy.normalize("necklace")).isEqualTo("Jewelry");
		assertThat(CategoryTaxonomy.normalize("jewellery")).isEqualTo("Jewelry");
		assertThat(CategoryTaxonomy.normalize("ring")).isEqualTo("Jewelry");
		assertThat(CategoryTaxonomy.normalize("bracelet")).isEqualTo("Jewelry");
		assertThat(CategoryTaxonomy.normalize("earrings")).isEqualTo("Jewelry");
		assertThat(CategoryTaxonomy.normalize("watch")).isEqualTo("Jewelry");
	}

	@Test
	void normalize_accessorySynonyms_mapToAccessory() {
		assertThat(CategoryTaxonomy.normalize("tote")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("bag")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("hat")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("cap")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("belt")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("scarf")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("sunglasses")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("gloves")).isEqualTo("Accessory");
		assertThat(CategoryTaxonomy.normalize("tie")).isEqualTo("Accessory");
	}

	// --- Casing / whitespace insensitivity ---

	@Test
	void normalize_isCaseAndWhitespaceInsensitive() {
		assertThat(CategoryTaxonomy.normalize("  T-SHIRT ")).isEqualTo("Top");
		assertThat(CategoryTaxonomy.normalize("CHINOS")).isEqualTo("Bottom");
		assertThat(CategoryTaxonomy.normalize(" jacket")).isEqualTo("Jacket");
		assertThat(CategoryTaxonomy.normalize("JEWELRY")).isEqualTo("Jewelry");
	}

	// --- Unrecognized / blank / null → "Other", never throws ---

	@Test
	void normalize_unrecognizedValue_fallsBackToOther() {
		assertThat(CategoryTaxonomy.normalize("sweatpants-with-typo")).isEqualTo("Other");
		assertThat(CategoryTaxonomy.normalize("widget")).isEqualTo("Other");
	}

	@Test
	void normalize_blankOrWhitespaceOnly_fallsBackToOther() {
		assertThat(CategoryTaxonomy.normalize("")).isEqualTo("Other");
		assertThat(CategoryTaxonomy.normalize("   ")).isEqualTo("Other");
	}

	@Test
	void normalize_null_fallsBackToOther() {
		assertThat(CategoryTaxonomy.normalize(null)).isEqualTo("Other");
	}

	@Test
	void normalize_alreadyOther_mapsToOther() {
		assertThat(CategoryTaxonomy.normalize("Other")).isEqualTo("Other");
		assertThat(CategoryTaxonomy.normalize("other")).isEqualTo("Other");
	}

	@Test
	void values_isUnmodifiable() {
		assertThat(CategoryTaxonomy.values()).isUnmodifiable();
	}
}
