import { useState } from 'react'
import { Link } from 'react-router-dom'

import { deleteItem, photoUrl } from '../api/items'
import type { Item } from '../types/item'

interface WardrobeCellProps {
  item: Item
  /** Called with the itemId once the backend delete succeeds, so the grid can drop it. */
  onDeleted: (itemId: string) => void
}

/**
 * One wardrobe-grid cell: the thumbnail linking to detail, plus an inline,
 * two-step delete that never leaves the grid. The whole thumbnail is a
 * navigation `<Link>`, so the delete control is a sibling of it (not a child)
 * — otherwise a tap on delete would also fire navigation.
 *
 * Each cell keeps its own confirm/deleting/error state (mirroring
 * `ItemDetail`'s guarded delete) so one card's confirmation never disturbs its
 * neighbours. On a failed delete the confirm stays open with an error, so the
 * user can retry or back out without losing context.
 */
export default function WardrobeCell({ item, onDeleted }: WardrobeCellProps) {
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const label = item.category ?? 'garment'

  const onConfirmDelete = () => {
    setDeleting(true)
    setError(null)
    deleteItem(item.itemId)
      .then(() => onDeleted(item.itemId))
      .catch(() => {
        setError('We couldn’t delete this item. Please try again.')
        setDeleting(false)
      })
  }

  return (
    <li className="grid-cell">
      <Link to={`/item/${item.itemId}`} className="thumb">
        <img
          className="thumb-img"
          src={photoUrl(item.itemId)}
          alt={label}
          loading="lazy"
        />
      </Link>

      {confirming ? (
        <div className="cell-confirm">
          {error && (
            <p className="cell-confirm-error" role="alert">
              {error}
            </p>
          )}
          <div className="cell-confirm-actions">
            <button
              type="button"
              className="btn"
              onClick={() => setConfirming(false)}
              disabled={deleting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-danger"
              onClick={onConfirmDelete}
              disabled={deleting}
            >
              {deleting ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          className="cell-delete"
          aria-label={`Delete ${label}`}
          onClick={() => {
            setError(null)
            setConfirming(true)
          }}
        >
          ✕
        </button>
      )}
    </li>
  )
}
