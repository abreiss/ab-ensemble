import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import TagForm from '../components/TagForm'
import { createItem, tagPreview } from '../api/items'
import type { TagInput, TagSuggestion } from '../types/item'

type ItemPhase = 'tagging' | 'ready'

/**
 * One photo waiting to be reviewed and saved. The single-photo screen's state
 * (`photo`/`previewUrl`/`phase`/`suggestion`/`error`) lifted onto a per-item
 * record so the screen can hold `1..N` of them in a review queue.
 */
interface PendingItem {
  id: string
  file: File
  previewUrl: string
  phase: ItemPhase
  suggestion: TagSuggestion | null
  saving: boolean
  error: string | null
}

// A degraded/failed vision call is a normal, editable state — not an error — so
// both a rejected tag-preview and an all-null suggestion fall back to this blank
// (but fully editable) form seed, per item.
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
 * Add-item screen (`/add`). Every image source funnels through one shared
 * `enqueue(files)` entry point that turns each file into a pending queue tile:
 * auto-tag → editable `TagForm` → save. `N=1` (the single-file picker) behaves
 * exactly as the original single-photo flow — one tile, saved, then back to the
 * grid. A rejected/all-null tag-preview leaves that tile on the blank editable
 * seed (never an error); each tile's object URL is revoked on remove, save, and
 * unmount.
 */
export default function AddItem() {
  const navigate = useNavigate()
  const [items, setItems] = useState<PendingItem[]>([])
  // Monotonic id source so each queued tile is uniquely addressable — the id is
  // also the request identity, so an out-of-order tag response can only seed its
  // own tile (never another item's).
  const idCounter = useRef(0)
  // Live object URLs by item id, so each can be revoked independently on
  // remove/save and any survivors revoked on unmount (no single-URL leak).
  const urls = useRef(new Map<string, string>())
  // True once at least one file has been enqueued, so draining the queue after a
  // save returns to the grid without firing on the initial empty render.
  const enqueuedAny = useRef(false)

  // Release every remaining preview's object URL when leaving the screen.
  useEffect(() => {
    const live = urls.current
    return () => {
      for (const url of live.values()) {
        URL.revokeObjectURL(url)
      }
      live.clear()
    }
  }, [])

  const revokeUrl = (id: string) => {
    const url = urls.current.get(id)
    if (url) {
      URL.revokeObjectURL(url)
      urls.current.delete(id)
    }
  }

  // Auto-fire tag-preview for one queued item. The functional updates match by id
  // so a response only ever seeds its own tile, and a rejected/degraded call
  // falls back to the blank editable seed for that item alone.
  const tagItem = (id: string, file: File) => {
    tagPreview(file)
      .then((result) => {
        setItems((prev) =>
          prev.map((it) => (it.id === id ? { ...it, suggestion: result, phase: 'ready' } : it)),
        )
      })
      .catch(() => {
        setItems((prev) =>
          prev.map((it) =>
            it.id === id ? { ...it, suggestion: EMPTY_SUGGESTION, phase: 'ready' } : it,
          ),
        )
      })
  }

  // The single shared acquisition seam: every source (file, paste, camera) hands
  // its files here. Each becomes a pending tile with its own object URL, then
  // auto-tags.
  const enqueue = (files: File[]) => {
    if (files.length === 0) {
      return
    }
    enqueuedAny.current = true
    const created = files.map((file) => {
      const id = `pending-${(idCounter.current += 1)}`
      const previewUrl = URL.createObjectURL(file)
      urls.current.set(id, previewUrl)
      return { id, file, previewUrl, phase: 'tagging' as const, suggestion: null, saving: false, error: null }
    })
    setItems((prev) => [...prev, ...created])
    for (const item of created) {
      tagItem(item.id, item.file)
    }
  }

  const onSelectPhoto = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      enqueue([file])
    }
  }

  const removeItem = (id: string) => {
    revokeUrl(id)
    setItems((prev) => prev.filter((it) => it.id !== id))
  }

  const onSaveItem = (item: PendingItem, tags: TagInput) => {
    setItems((prev) => prev.map((it) => (it.id === item.id ? { ...it, saving: true, error: null } : it)))
    createItem(item.file, tags)
      .then(() => {
        revokeUrl(item.id)
        setItems((prev) => prev.filter((it) => it.id !== item.id))
      })
      .catch(() => {
        setItems((prev) =>
          prev.map((it) =>
            it.id === item.id
              ? { ...it, saving: false, error: 'We couldn’t save this item. Please try again.' }
              : it,
          ),
        )
      })
  }

  // Once the queue drains after a save, return to the grid (never on the initial
  // empty render).
  useEffect(() => {
    if (enqueuedAny.current && items.length === 0) {
      navigate('/wardrobe')
    }
  }, [items, navigate])

  return (
    <section data-testid="add-item" className="screen">
      <h1 className="screen-title">Add an item</h1>

      <label className="photo-picker">
        <span className="field-label">
          {items.length > 0 ? 'Add another photo' : 'Take or choose a garment photo'}
        </span>
        <input
          type="file"
          accept="image/*"
          aria-label="Choose a garment photo"
          onChange={onSelectPhoto}
        />
      </label>

      <ul className="review-queue">
        {items.map((item) => (
          <li key={item.id} className="queue-tile">
            <img className="photo-preview" src={item.previewUrl} alt="Selected garment photo" />

            <button
              type="button"
              className="btn queue-tile-remove"
              onClick={() => removeItem(item.id)}
            >
              Remove
            </button>

            {item.phase === 'tagging' && <p className="state-note">Tagging your photo…</p>}

            {item.error && (
              <p className="banner banner-error" role="alert">
                {item.error}
              </p>
            )}

            {item.phase === 'ready' && (
              <TagForm
                key={item.id}
                initial={item.suggestion}
                submitLabel="Save item"
                submitting={item.saving}
                onSubmit={(tags) => onSaveItem(item, tags)}
              />
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
