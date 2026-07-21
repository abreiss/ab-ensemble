import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import TagForm from '../components/TagForm'
import { createItem, tagPreview } from '../api/items'
import type { TagInput, TagSuggestion } from '../types/item'

type ItemPhase = 'tagging' | 'ready'

/**
 * One photo waiting to be reviewed and saved. The single-photo screen's state
 * (`photo`/`previewUrl`/`phase`/`suggestion`/`error`) lifted onto a per-item
 * record so the screen can hold `1..N` of them in a review queue. `tags` holds the
 * tile's latest validated `TagInput` (or `null` while its required fields are
 * incomplete), reported up by its `TagForm` so "Save all" can persist the set.
 */
interface PendingItem {
  id: string
  file: File
  previewUrl: string
  phase: ItemPhase
  suggestion: TagSuggestion | null
  tags: TagInput | null
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
 * auto-tag → editable `TagForm` → save. A single picked photo (`N=1`) behaves
 * exactly as the original single-photo flow — one tile, saved, then back to the
 * grid — while a batch multi-select enqueues many tiles saved together by
 * "Save all". A rejected/all-null tag-preview leaves that tile on the blank
 * editable seed (never an error); each tile's object URL is revoked on remove,
 * save, and unmount.
 */
export default function AddItem() {
  const navigate = useNavigate()
  const [items, setItems] = useState<PendingItem[]>([])
  // Batch-save progress: while a "Save all" run is in flight, and how many of the
  // attempted tiles have persisted so far ("N of M saved").
  const [savingBatch, setSavingBatch] = useState(false)
  const [savedCount, setSavedCount] = useState(0)
  const [saveTotal, setSaveTotal] = useState(0)
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
      return {
        id,
        file,
        previewUrl,
        phase: 'tagging' as const,
        suggestion: null,
        tags: null,
        error: null,
      }
    })
    setItems((prev) => [...prev, ...created])
    for (const item of created) {
      tagItem(item.id, item.file)
    }
  }

  const onSelectPhotos = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files
    if (files && files.length > 0) {
      enqueue([...files])
    }
    // Clear the input so re-picking the same file(s) still fires `onChange`.
    event.target.value = ''
  }

  // A tile's `TagForm` reports its validated tags (or null while incomplete) here,
  // so "Save all" can persist each tile without owning its draft state.
  const onTagsChange = (id: string, tags: TagInput | null) => {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, tags } : it)))
  }

  const removeItem = (id: string) => {
    revokeUrl(id)
    setItems((prev) => prev.filter((it) => it.id !== id))
  }

  // Every queued tile is reviewed and ready to persist (auto-tag finished and its
  // required fields are valid) — the gate for firing "Save all".
  const canSaveAll =
    !savingBatch && items.length > 0 && items.every((it) => it.phase === 'ready' && it.tags !== null)

  // Fan out `createItem` per tile in a client-side loop (no batch endpoint).
  // Saves are independent: a success removes its tile (revoking its URL) and bumps
  // progress; a failure keeps the tile — edited tags intact — with a retryable
  // error. Draining the queue navigates back via the effect below.
  const saveAll = async () => {
    const toSave = items.filter((it) => it.phase === 'ready' && it.tags)
    if (toSave.length === 0) {
      return
    }
    setItems((prev) => prev.map((it) => ({ ...it, error: null })))
    setSaveTotal(toSave.length)
    setSavedCount(0)
    setSavingBatch(true)
    let saved = 0
    for (const item of toSave) {
      try {
        await createItem(item.file, item.tags as TagInput)
        revokeUrl(item.id)
        setItems((prev) => prev.filter((it) => it.id !== item.id))
        saved += 1
        setSavedCount(saved)
      } catch {
        setItems((prev) =>
          prev.map((it) =>
            it.id === item.id
              ? { ...it, error: 'We couldn’t save this item. Please try again.' }
              : it,
          ),
        )
      }
    }
    setSavingBatch(false)
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
          {items.length > 0 ? 'Add more photos' : 'Take or choose garment photos'}
        </span>
        <input
          type="file"
          accept="image/*"
          multiple
          aria-label="Choose a garment photo"
          onChange={onSelectPhotos}
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
                onChange={(tags) => onTagsChange(item.id, tags)}
              />
            )}
          </li>
        ))}
      </ul>

      {items.length > 0 && (
        <div className="queue-actions">
          {savingBatch && (
            <p className="state-note" role="status">
              {savedCount} of {saveTotal} saved…
            </p>
          )}
          <button
            type="button"
            className="btn btn-primary btn-block"
            onClick={saveAll}
            disabled={!canSaveAll}
          >
            {savingBatch ? 'Saving…' : `Save all${items.length > 1 ? ` (${items.length})` : ''}`}
          </button>
        </div>
      )}
    </section>
  )
}
