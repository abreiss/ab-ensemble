// TypeScript views of the backend `/api/items` DTOs. These mirror the Java
// records (ItemResponse / TagSuggestion / TagRequest) that form the API contract.

/** Outbound item — mirrors `ItemResponse`. `photoUrl` is fetched separately. */
export interface Item {
  itemId: string
  category: string | null
  primaryColor: string | null
  secondaryColor: string | null
  formality: number | null
  pattern: string | null
  warmth: number | null
  descriptors: string[] | null
  photoUrl: string
  createdAt: string
  lastWorn: string | null
  wornCount: number | null
}

/**
 * Auto-tag suggestion — mirrors `TagSuggestion`. Every field is nullable: a
 * degraded or failed vision call still returns an editable `200`, so a null in
 * any field is a normal, renderable state rather than an error.
 */
export interface TagSuggestion {
  category: string | null
  primaryColor: string | null
  secondaryColor: string | null
  formality: number | null
  pattern: string | null
  warmth: number | null
  descriptors: string[] | null
}

/**
 * Tag fields the user submits when creating or updating an item — mirrors
 * `TagRequest`. `category` is required (non-blank) by the backend; `formality`
 * (1–5) and `warmth` (1–3) are present but nullable — a Jewelry/Accessory item
 * commonly has neither — and are still range-checked when supplied; the rest
 * are optional.
 */
export interface TagInput {
  category: string
  primaryColor?: string | null
  secondaryColor?: string | null
  formality: number | null
  pattern?: string | null
  warmth: number | null
  descriptors?: string[]
}
