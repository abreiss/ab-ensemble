import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import TagForm from '../components/TagForm'
import { deleteItem, getItem, updateTags } from '../api/items'
import { relativeTime } from '../lib/relativeTime'
import type { Item, TagInput } from '../types/item'

type Status = 'loading' | 'ready' | 'notfound'

/**
 * Item detail (`/item/:id`) — the maintenance surface. Loads one item, shows its
 * photo, a quiet wear-history line (`wornCount` + a relative `lastWorn`, display
 * only), and an editable `TagForm`; saves tag edits via `updateTags` and offers a
 * guarded (two-step) delete. Load failure degrades to a non-crashing "not found"
 * state; save/delete failures preserve the user's context.
 */
export default function ItemDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()

  const [status, setStatus] = useState<Status>('loading')
  const [item, setItem] = useState<Item | null>(null)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getItem(id)
      .then((loaded) => {
        setItem(loaded)
        setStatus('ready')
      })
      .catch(() => setStatus('notfound'))
  }, [id])

  const onSave = useCallback(
    (tags: TagInput) => {
      setSaving(true)
      setError(null)
      setSaved(false)
      updateTags(id, tags)
        .then((updated) => {
          setItem(updated)
          setSaved(true)
        })
        .catch(() => setError('We couldn’t save your changes. Please try again.'))
        .finally(() => setSaving(false))
    },
    [id],
  )

  const onConfirmDelete = () => {
    setDeleting(true)
    setError(null)
    deleteItem(id)
      .then(() => navigate('/wardrobe'))
      .catch(() => {
        setError('We couldn’t delete this item. Please try again.')
        setDeleting(false)
        setConfirmingDelete(false)
      })
  }

  if (status === 'loading') {
    return (
      <section data-testid="item-detail" className="screen">
        <p className="state-note">Loading…</p>
      </section>
    )
  }

  if (status === 'notfound' || item === null) {
    return (
      <section data-testid="item-detail" className="screen">
        <div className="state-block">
          <h1 className="empty-title">Item not found</h1>
          <p className="state-note">This item may have been removed.</p>
          <Link to="/wardrobe" className="btn btn-primary">
            Back to wardrobe
          </Link>
        </div>
      </section>
    )
  }

  const wornCount = item.wornCount ?? 0
  const wearLabel =
    wornCount === 0 ? 'Never worn' : `Worn ${wornCount}× · ${relativeTime(item.lastWorn)}`

  return (
    <section data-testid="item-detail" className="screen">
      <img className="detail-photo" src={item.photoUrl} alt={item.category ?? 'garment'} />

      <p className="wear-history" data-testid="wear-history">
        <span className="eyebrow">Wear history</span>
        <span className="wear-value">{wearLabel}</span>
      </p>

      {saved && !error && (
        <p className="banner banner-ok" role="status">
          Changes saved.
        </p>
      )}
      {error && (
        <p className="banner banner-error" role="alert">
          {error}
        </p>
      )}

      <TagForm
        initial={item}
        submitLabel="Save changes"
        submitting={saving}
        onSubmit={onSave}
      />

      <div className="danger-zone">
        {confirmingDelete ? (
          <div className="confirm-delete">
            <p className="state-note">Delete this item permanently?</p>
            <div className="confirm-actions">
              <button
                type="button"
                className="btn"
                onClick={() => setConfirmingDelete(false)}
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
                {deleting ? 'Deleting…' : 'Confirm delete'}
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            className="btn btn-danger btn-block"
            onClick={() => {
              setError(null)
              setConfirmingDelete(true)
            }}
          >
            Delete item
          </button>
        )}
      </div>
    </section>
  )
}
