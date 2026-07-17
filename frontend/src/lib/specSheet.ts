// Deterministic spec-sheet render helpers. The stylist LLM supplies only the
// per-item rationale; the item name, slot label, and color swatch are derived
// here from the item's stored tags — never asked of the model (AGENTS.md
// deterministic-data rule). Each helper degrades gracefully when a tag is null.

/** The four slot labels the spec sheet draws, plus a generic fallback. */
export type Slot = 'TOP' | 'BOTTOM' | 'SHOES' | 'CARRY' | 'PIECE'

/** Category (lower-cased) → slot label. Unlisted categories fall back to PIECE. */
const CATEGORY_SLOTS: Record<string, Slot> = {
  shirt: 'TOP',
  tee: 'TOP',
  't-shirt': 'TOP',
  tshirt: 'TOP',
  top: 'TOP',
  sweater: 'TOP',
  jacket: 'TOP',
  pants: 'BOTTOM',
  chinos: 'BOTTOM',
  jeans: 'BOTTOM',
  shorts: 'BOTTOM',
  skirt: 'BOTTOM',
  shoes: 'SHOES',
  loafers: 'SHOES',
  sneakers: 'SHOES',
  boots: 'SHOES',
  footwear: 'SHOES',
  bag: 'CARRY',
  tote: 'CARRY',
  hat: 'CARRY',
  cap: 'CARRY',
  accessory: 'CARRY',
}

/**
 * Maps a garment `category` to its spec-sheet slot label. Case-insensitive and
 * whitespace-trimmed; a null, blank, or unrecognized category degrades to the
 * generic `PIECE` label so the card always renders.
 */
export function slotForCategory(category: string | null | undefined): Slot {
  const key = (category ?? '').trim().toLowerCase()
  return CATEGORY_SLOTS[key] ?? 'PIECE'
}

/** The stored tags a display name is derived from — all nullable. */
export interface NameTags {
  primaryColor?: string | null
  descriptors?: string[] | null
  category?: string | null
}

/** Shown when an item has no usable tags at all (matches the photo `alt` fallback). */
const UNNAMED = 'Garment'

/** Trims a value and returns null if it is missing or blank. */
function clean(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim()
  return trimmed === '' ? null : trimmed
}

/**
 * Derives a display name from `primaryColor` + the lead `descriptor` + `category`
 * ("White linen shirt"), since {@link import('../types/item').Item} has no name
 * field. Blank or null parts are dropped; when every part is missing it degrades
 * to a safe {@link UNNAMED} label. The joined result is rendered in sentence case.
 */
export function deriveName(tags: NameTags): string {
  const leadDescriptor = (tags.descriptors ?? []).map(clean).find((d) => d !== null) ?? null
  const parts = [clean(tags.primaryColor), leadDescriptor, clean(tags.category)].filter(
    (p): p is string => p !== null,
  )
  if (parts.length === 0) {
    return UNNAMED
  }
  const label = parts.join(' ').toLowerCase()
  return label.charAt(0).toUpperCase() + label.slice(1)
}

/** Rendered when a color is missing or unrecognized — a muted neutral fill. */
export const NEUTRAL_SWATCH = '#b7ab95'

/**
 * Garment color names that are already valid CSS color keywords, so the swatch
 * can use them verbatim. Kept explicit (rather than trusting any string as CSS)
 * so an unrecognized tag degrades to {@link NEUTRAL_SWATCH} instead of rendering
 * as transparent/black.
 */
const CSS_COLOR_KEYWORDS = new Set([
  'white', 'black', 'gray', 'grey', 'silver', 'ivory', 'beige', 'tan', 'khaki',
  'brown', 'maroon', 'red', 'pink', 'orange', 'coral', 'gold', 'yellow', 'olive',
  'green', 'teal', 'blue', 'navy', 'indigo', 'purple', 'plum', 'lavender',
])

/** Common non-keyword garment color names → a representative hex fill. */
const CURATED_SWATCHES: Record<string, string> = {
  natural: '#cdbf9f',
  cream: '#f5f0e1',
  charcoal: '#36454f',
  camel: '#c19a6b',
  rust: '#b7410e',
  mustard: '#e1ad01',
  burgundy: '#7c2833',
  stone: '#b8b0a1',
}

/**
 * Resolves a `primaryColor` tag to a CSS color for the swatch fill: valid CSS
 * keywords pass through; curated non-keyword names map to a hex; anything else
 * (or a null/blank color) degrades to {@link NEUTRAL_SWATCH}. Case-insensitive.
 */
export function swatchColor(color: string | null | undefined): string {
  const key = (color ?? '').trim().toLowerCase()
  if (key === '') {
    return NEUTRAL_SWATCH
  }
  if (CSS_COLOR_KEYWORDS.has(key)) {
    return key
  }
  return CURATED_SWATCHES[key] ?? NEUTRAL_SWATCH
}
