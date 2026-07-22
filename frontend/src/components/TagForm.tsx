import { useEffect, useRef, useState, type FormEvent } from 'react'

import DescriptorChips from './DescriptorChips'
import { CATEGORIES, normalizeCategory } from '../lib/categoryTaxonomy'
import { tagsAreValid, validateTags } from '../lib/tagValidation'
import type { TagInput, TagSuggestion } from '../types/item'

interface TagFormProps {
  /** Pre-fill values; every field is nullable (a degraded suggestion is normal). */
  initial?: TagSuggestion | null
  /**
   * Called with a validated `TagInput` when the user submits. Omit it to run the
   * form without its own submit button — e.g. the review queue, which reads each
   * tile's tags via `onChange` and drives saving from a single "Save all".
   */
  onSubmit?: (tags: TagInput) => void
  /**
   * Reports the live draft on every edit: a validated `TagInput` when the required
   * fields are valid, else `null`. Lets a parent (the review queue) read each
   * tile's current tags without owning the draft state.
   */
  onChange?: (tags: TagInput | null) => void
  /** Text for the submit button (e.g. "Save item" / "Save changes"). */
  submitLabel?: string
  /** When true, a save is in flight — the form stays disabled. */
  submitting?: boolean
}

interface Draft {
  category: string
  primaryColor: string
  secondaryColor: string
  formality: number | null
  pattern: string
  warmth: number | null
  descriptors: string[]
}

const FORMALITY_OPTIONS = [1, 2, 3, 4, 5]
const WARMTH_OPTIONS = [1, 2, 3]

/**
 * Seeds the category control: no stored/suggested value yet (`null`/`undefined`/
 * blank) renders the `—` placeholder, same as an unset `formality`/`warmth`.
 * A present value — canonical or legacy/off-taxonomy free text — is always
 * normalized to a taxonomy bucket, so the `<select>` never renders blank or an
 * option outside the list (spec Unit 2: edit-time normalization).
 */
function toDraftCategory(rawCategory: string | null | undefined): string {
  return rawCategory ? normalizeCategory(rawCategory) : ''
}

function toDraft(initial?: TagSuggestion | null): Draft {
  return {
    category: toDraftCategory(initial?.category),
    primaryColor: initial?.primaryColor ?? '',
    secondaryColor: initial?.secondaryColor ?? '',
    formality: initial?.formality ?? null,
    pattern: initial?.pattern ?? '',
    warmth: initial?.warmth ?? null,
    // De-dup seeded descriptors: the add path already blocks duplicates, but a
    // suggestion can repeat one, which would collide chip keys and let a single
    // remove drop every copy.
    descriptors: initial?.descriptors ? [...new Set(initial.descriptors)] : [],
  }
}

/** Trims an optional text field to a value or `null` (so blanks are omitted). */
function optional(text: string): string | null {
  const trimmed = text.trim()
  return trimmed === '' ? null : trimmed
}

/**
 * Maps a valid draft to the API `TagInput` (blank optional fields omitted as
 * `null`). Only call once the required fields pass `tagsAreValid`. `formality`/
 * `warmth` pass through as-is — both are optional, so an unset selection (`null`)
 * is a normal, submittable value (e.g. Jewelry/Accessory).
 */
function toTagInput(draft: Draft): TagInput {
  return {
    category: draft.category.trim(),
    primaryColor: optional(draft.primaryColor),
    secondaryColor: optional(draft.secondaryColor),
    formality: draft.formality,
    pattern: optional(draft.pattern),
    warmth: draft.warmth,
    descriptors: draft.descriptors,
  }
}

/**
 * Shared editable tag form used by both the add and detail screens. It seeds its
 * state from `initial` once on mount, so a parent that receives a new suggestion
 * (e.g. after tag-preview resolves) should remount it via a `key`. A null field
 * renders as an empty, editable control — never an error. Save is gated by the
 * same required-field rules the backend enforces (`tagValidation`).
 */
export default function TagForm({
  initial,
  onSubmit,
  onChange,
  submitLabel,
  submitting = false,
}: TagFormProps) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(initial))
  const [touched, setTouched] = useState(false)

  const errors = validateTags(draft)
  const canSave = tagsAreValid(draft) && !submitting

  // Report the draft's validated tags (or null) on mount and after every edit.
  // A ref holds the latest callback so a parent re-render (new closure) never
  // re-fires the report effect — only a real draft change does. The ref is kept
  // in sync in its own effect (declared first, so it runs before the report).
  const onChangeRef = useRef(onChange)
  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])
  useEffect(() => {
    onChangeRef.current?.(tagsAreValid(draft) ? toTagInput(draft) : null)
  }, [draft])

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setDraft((prev) => ({ ...prev, [key]: value }))
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setTouched(true)
    if (!tagsAreValid(draft)) {
      return
    }
    onSubmit?.(toTagInput(draft))
  }

  return (
    <form className="tag-form" onSubmit={handleSubmit} noValidate>
      <label className="field">
        <span className="field-label">Category</span>
        <select
          className="input"
          value={draft.category}
          onChange={(e) => set('category', e.target.value)}
          onBlur={() => setTouched(true)}
          aria-invalid={touched && errors.category ? true : undefined}
        >
          <option value="">—</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        {touched && errors.category && <span className="field-error">{errors.category}</span>}
      </label>

      <label className="field">
        <span className="field-label">Primary color</span>
        <input
          className="input"
          type="text"
          value={draft.primaryColor}
          onChange={(e) => set('primaryColor', e.target.value)}
        />
      </label>

      <label className="field">
        <span className="field-label">Secondary color</span>
        <input
          className="input"
          type="text"
          value={draft.secondaryColor}
          onChange={(e) => set('secondaryColor', e.target.value)}
        />
      </label>

      <label className="field">
        <span className="field-label">Formality</span>
        <select
          className="input"
          value={draft.formality ?? ''}
          onChange={(e) => set('formality', e.target.value === '' ? null : Number(e.target.value))}
          onBlur={() => setTouched(true)}
          aria-invalid={touched && errors.formality ? true : undefined}
        >
          <option value="">—</option>
          {FORMALITY_OPTIONS.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
        {touched && errors.formality && <span className="field-error">{errors.formality}</span>}
      </label>

      <label className="field">
        <span className="field-label">Pattern</span>
        <input
          className="input"
          type="text"
          value={draft.pattern}
          onChange={(e) => set('pattern', e.target.value)}
        />
      </label>

      <label className="field">
        <span className="field-label">Warmth</span>
        <select
          className="input"
          value={draft.warmth ?? ''}
          onChange={(e) => set('warmth', e.target.value === '' ? null : Number(e.target.value))}
          onBlur={() => setTouched(true)}
          aria-invalid={touched && errors.warmth ? true : undefined}
        >
          <option value="">—</option>
          {WARMTH_OPTIONS.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
        {touched && errors.warmth && <span className="field-error">{errors.warmth}</span>}
      </label>

      <div className="field">
        <span className="field-label">Descriptors</span>
        <DescriptorChips value={draft.descriptors} onChange={(next) => set('descriptors', next)} />
      </div>

      {onSubmit && (
        <button type="submit" className="btn btn-primary btn-block" disabled={!canSave}>
          {submitting ? 'Saving…' : submitLabel}
        </button>
      )}
    </form>
  )
}
