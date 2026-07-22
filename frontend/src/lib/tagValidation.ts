// Client-side validation for garment tags. These rules mirror the backend
// `TagRequest` constraints exactly: `category` is required (non-blank);
// `formality` (1–5) and `warmth` (1–3) are optional — a null is valid (e.g. a
// Jewelry/Accessory item has neither), but a supplied value is still
// range-checked — so the UI can gate save and avoid a round-trip 400. This is a
// UX guardrail, not a security boundary — the backend remains the source of
// truth.

/** The tag fields the form validates (unset `formality`/`warmth` = `null`). */
export interface RequiredTagFields {
  category: string
  formality: number | null
  warmth: number | null
}

/** Field-keyed messages; an empty object means the fields are valid. */
export interface TagValidationErrors {
  category?: string
  formality?: string
  warmth?: string
}

/** Returns a message per invalid field; `{}` when all are valid. */
export function validateTags(fields: RequiredTagFields): TagValidationErrors {
  const errors: TagValidationErrors = {}
  if (fields.category.trim() === '') {
    errors.category = 'Category is required.'
  }
  if (fields.formality !== null && (fields.formality < 1 || fields.formality > 5)) {
    errors.formality = 'Formality must be from 1 to 5.'
  }
  if (fields.warmth !== null && (fields.warmth < 1 || fields.warmth > 3)) {
    errors.warmth = 'Warmth must be from 1 to 3.'
  }
  return errors
}

/** Convenience predicate for gating the save action. */
export function tagsAreValid(fields: RequiredTagFields): boolean {
  return Object.keys(validateTags(fields)).length === 0
}
