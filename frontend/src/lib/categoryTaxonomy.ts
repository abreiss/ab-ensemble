// Frontend single source of truth for the wardrobe item category taxonomy
// (assumption A1.4/A2.3): the ordered taxonomy values and the pure
// `normalizeCategory` fallback that both the `TagForm` `<select>` (Task 2.0) and
// the grid's grouping helper (Task 3.0) derive from. This mirrors the backend
// `com.ensemble.wardrobe.CategoryTaxonomy` constant one-for-one — the same
// ordered list and the same starter legacy-synonym map — but the two are
// intentionally separate, per-stack definitions (no shared codegen at this
// scale); cross-stack agreement is a review/test invariant.

/** Ordered taxonomy values; `Other` is always last. */
export const CATEGORIES = [
  'Jacket',
  'Top',
  'Bottom',
  'Dress',
  'Shoes',
  'Jewelry',
  'Accessory',
  'Other',
] as const

/** A canonical taxonomy value — one of {@link CATEGORIES}. */
export type Category = (typeof CATEGORIES)[number]

const OTHER: Category = 'Other'

/**
 * Case/whitespace-insensitive synonym → taxonomy-bucket map. Keys are
 * lower-case; values are canonical `Category` strings. A starter map
 * (assumption A1.12) covering the canonical values themselves plus
 * representative legacy/free-text synonyms actually seen in wardrobe data.
 */
const SYNONYMS: Record<string, Category> = buildSynonyms()

function buildSynonyms(): Record<string, Category> {
  const map: Record<string, Category> = {}
  const put = (bucket: Category, synonyms: string[]) => {
    for (const synonym of synonyms) {
      map[synonym] = bucket
    }
  }
  put('Jacket', ['jacket', 'coat', 'blazer', 'parka', 'overcoat'])
  put('Top', [
    'top',
    'shirt',
    't-shirt',
    'tshirt',
    'tee',
    'sweater',
    'sweatshirt',
    'hoodie',
    'blouse',
    'polo',
    'knit',
  ])
  put('Bottom', ['bottom', 'pants', 'chinos', 'jeans', 'trousers', 'shorts', 'skirt', 'leggings'])
  put('Dress', ['dress', 'gown'])
  put('Shoes', ['shoes', 'shoe', 'sneakers', 'boots', 'loafers', 'sandals', 'heels', 'footwear'])
  put('Jewelry', ['jewelry', 'jewellery', 'necklace', 'ring', 'bracelet', 'earrings', 'watch'])
  put('Accessory', [
    'accessory',
    'bag',
    'tote',
    'hat',
    'cap',
    'belt',
    'scarf',
    'sunglasses',
    'gloves',
    'tie',
  ])
  put('Other', ['other'])
  return map
}

/**
 * Maps a raw, possibly free-text category to a canonical taxonomy value.
 * Case- and whitespace-insensitive; unrecognized, blank, `null`, or
 * `undefined` input maps to `"Other"`. Never throws.
 */
export function normalizeCategory(raw: string | null | undefined): Category {
  if (raw == null) {
    return OTHER
  }
  const key = raw.trim().toLowerCase()
  if (key === '') {
    return OTHER
  }
  return SYNONYMS[key] ?? OTHER
}

/**
 * Singular→plural display-label map for wardrobe-grid section headers
 * (assumption A1.11). Explicit per value — not derived by string
 * concatenation — so irregular plurals (`Dress`→"Dresses") and unchanged
 * forms (`Shoes`→"Shoes", `Jewelry`→"Jewelry", `Other`→"Other") render
 * correctly.
 */
export const CATEGORY_LABELS: Record<Category, string> = {
  Jacket: 'Jackets',
  Top: 'Tops',
  Bottom: 'Bottoms',
  Dress: 'Dresses',
  Shoes: 'Shoes',
  Jewelry: 'Jewelry',
  Accessory: 'Accessories',
  Other: 'Other',
}

/**
 * The fixed display order for wardrobe-grid sections: taxonomy order with
 * `Other` always last (assumption A1.11). `CATEGORIES` already satisfies
 * this (`Other` is defined last), so this simply names that invariant for
 * callers that group by section.
 */
export function sectionOrder(): readonly Category[] {
  return CATEGORIES
}
