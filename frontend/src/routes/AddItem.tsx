import { useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import TagForm from '../components/TagForm'
import { createItem, tagPreview } from '../api/items'
import type { TagInput, TagSuggestion } from '../types/item'

type Phase = 'idle' | 'tagging' | 'ready'

// A degraded/failed vision call is a normal, editable state — not an error — so
// both a rejected tag-preview and an all-null suggestion fall back to this blank
// (but fully editable) form seed.
const EMPTY_SUGGESTION: TagSuggestion = {
  category: null,
  primaryColor: null,
  secondaryColor: null,
  formality: null,
  pattern: null,
  warmth: null,
  descriptors: null,
}

/**
 * Add-item screen (`/add`) — the headline flow. Selecting a photo (camera or
 * file) auto-fires tag-preview, shows a loading state, then pre-fills an editable
 * `TagForm`. On save it creates the item and returns to the grid; a create
 * failure surfaces an error while preserving the photo and entered tags.
 */
export default function AddItem() {
  const navigate = useNavigate()
  const [photo, setPhoto] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [suggestion, setSuggestion] = useState<TagSuggestion | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onSelectPhoto = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    setPhoto(file)
    setPreviewUrl(URL.createObjectURL(file))
    setError(null)
    setPhase('tagging')
    // Fall back to an editable blank form whether the call degrades or fails.
    tagPreview(file)
      .then((result) => setSuggestion(result))
      .catch(() => setSuggestion(EMPTY_SUGGESTION))
      .finally(() => setPhase('ready'))
  }

  const onSave = (tags: TagInput) => {
    if (!photo) {
      return
    }
    setSaving(true)
    setError(null)
    createItem(photo, tags)
      .then(() => navigate('/'))
      .catch(() => {
        setError('We couldn’t save this item. Please try again.')
        setSaving(false)
      })
  }

  return (
    <section data-testid="add-item" className="screen">
      <h1 className="screen-title">Add an item</h1>

      <label className="photo-picker">
        <span className="field-label">
          {photo ? 'Change photo' : 'Take or choose a garment photo'}
        </span>
        <input
          type="file"
          accept="image/*"
          capture="environment"
          aria-label="Choose a garment photo"
          onChange={onSelectPhoto}
        />
      </label>

      {previewUrl && (
        <img className="photo-preview" src={previewUrl} alt="Selected garment photo" />
      )}

      {phase === 'tagging' && <p className="state-note">Tagging your photo…</p>}

      {error && (
        <p className="banner banner-error" role="alert">
          {error}
        </p>
      )}

      {phase === 'ready' && (
        <TagForm
          key={previewUrl ?? 'form'}
          initial={suggestion}
          submitLabel="Save item"
          submitting={saving}
          onSubmit={onSave}
        />
      )}
    </section>
  )
}
