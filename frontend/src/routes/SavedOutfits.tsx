import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { listItems, photoUrl } from '../api/items'
import { deleteOutfit, listOutfits } from '../api/outfits'
import type { Item } from '../types/item'
import type { SavedOutfit } from '../types/outfit'

type Status = 'loading' | 'ready' | 'error'

/** Human-readable eyebrow for each save source (never the model). */
const SOURCE_LABEL: Record<SavedOutfit['source'], string> = {
  ai: 'AI pick',
  manual: 'Hand-built',
}

/**
 * Saved Outfits screen (`/saved`): browse every look the user has saved — AI
 * picks from the Stylist screen and hand-built looks from Build — as a grid of
 * cards showing each piece's photo, the look's `source`/`reason`, and a remove
 * control. Fetches the saved outfits and the current wardrobe together (the
 * wardrobe is needed to resolve a piece to its photo and, in 4.3, to detect a
 * piece deleted since the save). Reuses the `WardrobeGrid` loading / empty /
 * error(+retry) state patterns so the screen is never blank or broken.
 */
export default function SavedOutfits() {
  const [outfits, setOutfits] = useState<SavedOutfit[]>([])
  const [items, setItems] = useState<Item[]>([])
  const [status, setStatus] = useState<Status>('loading')

  // Settle both list requests together. Keeping setState in the .then/.catch
  // (not the effect body) is the pattern the react-hooks rule endorses for
  // syncing with an external system — same as WardrobeGrid.
  const settle = useCallback(() => {
    Promise.all([listOutfits(), listItems()])
      .then(([savedOutfits, wardrobe]) => {
        setOutfits(savedOutfits)
        setItems(wardrobe)
        setStatus('ready')
      })
      .catch(() => setStatus('error'))
  }, [])

  useEffect(() => {
    settle()
  }, [settle])

  const retry = () => {
    setStatus('loading')
    settle()
  }

  // Drop a removed outfit from the local list — no refetch. The empty-state
  // branch derives from `outfits`, so removing the last one falls through to it.
  const handleRemoved = (id: string) => {
    setOutfits((prev) => prev.filter((o) => o.outfitId !== id))
  }

  if (status === 'loading') {
    return (
      <section data-testid="saved-outfits" className="screen">
        <p className="state-note">Loading your saved outfits…</p>
      </section>
    )
  }

  if (status === 'error') {
    return (
      <section data-testid="saved-outfits" className="screen">
        <div className="state-block">
          <p className="state-note">We couldn’t load your saved outfits.</p>
          <button type="button" className="btn" onClick={retry}>
            Try again
          </button>
        </div>
      </section>
    )
  }

  if (outfits.length === 0) {
    return (
      <section data-testid="saved-outfits" className="screen">
        <div className="state-block empty-state">
          <h1 className="empty-title">No saved outfits yet</h1>
          <p className="state-note">
            Save a look from the Stylist or Build screen to see it here.
          </p>
          <Link to="/" className="btn btn-primary">
            Style a look
          </Link>
        </div>
      </section>
    )
  }

  const byId = new Map(items.map((it) => [it.itemId, it]))

  return (
    <section data-testid="saved-outfits" className="screen">
      <ul className="outfit-list">
        {outfits.map((o, index) => (
          <SavedOutfitCard
            key={o.outfitId}
            outfit={o}
            position={index + 1}
            byId={byId}
            onRemoved={handleRemoved}
          />
        ))}
      </ul>
    </section>
  )
}

interface SavedOutfitCardProps {
  outfit: SavedOutfit
  /** 1-based position in the rendered list, to distinguish same-source remove controls. */
  position: number
  /** Current wardrobe indexed by id, for resolving each piece to its photo. */
  byId: Map<string, Item>
  /** Called with the outfitId once the backend delete succeeds, so the list drops it. */
  onRemoved: (outfitId: string) => void
}

/**
 * One saved-outfit card: a source eyebrow, the pieces' thumbnails, the whole-look
 * `reason` when present, and a single-click remove control. Each card owns its
 * own removing/error state (mirroring `WardrobeCell`) so one card's failure never
 * disturbs its neighbours; on a failed remove the card stays with a retry note.
 */
function SavedOutfitCard({ outfit, position, byId, onRemoved }: SavedOutfitCardProps) {
  const [removing, setRemoving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Resolve pieces at render time by intersecting the saved `itemIds` with the
  // current wardrobe: a piece deleted since the save is skipped (never a broken
  // tile), and the count of missing pieces drives a quiet caption. The saved
  // record itself is never rewritten (Q3-C).
  const pieces = outfit.itemIds
    .map((id) => byId.get(id))
    .filter((piece): piece is Item => piece !== undefined)
  const missingCount = outfit.itemIds.length - pieces.length

  const onRemove = () => {
    setRemoving(true)
    setError(null)
    deleteOutfit(outfit.outfitId)
      .then(() => onRemoved(outfit.outfitId))
      .catch(() => {
        setError('We couldn’t remove this outfit. Please try again.')
        setRemoving(false)
      })
  }

  return (
    <li className="outfit-card" data-testid="outfit-card">
      <div className="outfit-card-head">
        <span className="eyebrow">{SOURCE_LABEL[outfit.source]}</span>
        <button
          type="button"
          className="btn btn-danger"
          aria-label={`Remove ${SOURCE_LABEL[outfit.source]} outfit ${position}`}
          onClick={onRemove}
          disabled={removing}
        >
          {removing ? 'Removing…' : 'Remove'}
        </button>
      </div>

      <ul className="grid outfit-pieces">
        {pieces.map((piece) => (
          <li key={piece.itemId} className="grid-cell">
            <span className="thumb">
              <img
                className="thumb-img"
                src={photoUrl(piece.itemId)}
                alt={piece.category ?? 'garment'}
                loading="lazy"
              />
            </span>
          </li>
        ))}
      </ul>

      {missingCount > 0 && (
        <p className="state-note outfit-missing">
          {missingCount} {missingCount === 1 ? 'piece is' : 'pieces are'} no longer in your
          wardrobe.
        </p>
      )}

      {outfit.reason && outfit.reason.trim() !== '' && (
        <p className="outfit-reason">{outfit.reason}</p>
      )}

      {error && (
        <p className="banner banner-error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}
