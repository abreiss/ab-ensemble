import { useState, type FormEvent } from 'react'

import DescriptorChips from './DescriptorChips'
import { tagsAreValid, validateTags } from '../lib/tagValidation'
import type { TagInput, TagSuggestion } from '../types/item'

interface TagFormProps {
  /** Pre-fill values; every field is nullable (a degraded suggestion is normal). */
  initial?: TagSuggestion | null
  /** Called with a validated `TagInput` when the user saves. */
  onSubmit: (tags: TagInput) => void
  /** Text for the submit button (e.g. "Save item" / "Save changes"). */
  submitLabel: string
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

function toDraft(initial?: TagSuggestion | null): Draft {
  return {
    category: initial?.category ?? '',
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
 * Shared editable tag form used by both the add and detail screens. It seeds its
 * state from `initial` once on mount, so a parent that receives a new suggestion
 * (e.g. after tag-preview resolves) should remount it via a `key`. A null field
 * renders as an empty, editable control — never an error. Save is gated by the
 * same required-field rules the backend enforces (`tagValidation`).
 */
export default function TagForm({ initial, onSubmit, submitLabel, submitting = false }: TagFormProps) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(initial))
  const [touched, setTouched] = useState(false)

  const errors = validateTags(draft)
  const canSave = tagsAreValid(draft) && !submitting

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setDraft((prev) => ({ ...prev, [key]: value }))
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setTouched(true)
    if (!tagsAreValid(draft)) {
      return
    }
    onSubmit({
      category: draft.category.trim(),
      primaryColor: optional(draft.primaryColor),
      secondaryColor: optional(draft.secondaryColor),
      formality: draft.formality as number,
      pattern: optional(draft.pattern),
      warmth: draft.warmth as number,
      descriptors: draft.descriptors,
    })
  }

  return (
    <form className="tag-form" onSubmit={handleSubmit} noValidate>
      <label className="field">
        <span className="field-label">Category</span>
        <input
          className="input"
          type="text"
          value={draft.category}
          onChange={(e) => set('category', e.target.value)}
          onBlur={() => setTouched(true)}
          aria-invalid={touched && errors.category ? true : undefined}
        />
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

      <button type="submit" className="btn btn-primary btn-block" disabled={!canSave}>
        {submitting ? 'Saving…' : submitLabel}
      </button>
    </form>
  )
}
