import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import Mannequin from '../components/Mannequin'
import { listItems } from '../api/items'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'

/**
 * Manual outfit-assembly screen (`/assemble`) — the app's non-AI escape hatch.
 * The user drags wardrobe items onto a static mannequin silhouette to build a
 * look by hand, instead of asking the AI stylist to pick one. No Claude call
 * happens here; the screen only reads/writes existing item data. Fetches the
 * item list on mount using the same `listItems()` + `settle()` loading/error
 * pattern as `WardrobeGrid.tsx` / `WardrobeDrawer.tsx` so a failed fetch never
 * crashes the screen.
 */
export default function Assemble() {
  const [items, setItems] = useState<Item[]>([])
  const [status, setStatus] = useState<Status>('loading')

  const settle = useCallback((request: Promise<Item[]>) => {
    request
      .then((data) => {
        setItems(data)
        setStatus('ready')
      })
      .catch(() => setStatus('error'))
  }, [])

  useEffect(() => {
    settle(listItems())
  }, [settle])

  const retry = () => {
    setStatus('loading')
    settle(listItems())
  }

  if (status === 'loading') {
    return (
      <section data-testid="assemble" className="screen">
        <p className="state-note">Loading your wardrobe…</p>
      </section>
    )
  }

  if (status === 'error') {
    return (
      <section data-testid="assemble" className="screen">
        <div className="state-block">
          <p className="state-note">We couldn’t load your wardrobe.</p>
          <button type="button" className="btn" onClick={retry}>
            Try again
          </button>
        </div>
      </section>
    )
  }

  if (items.length === 0) {
    return (
      <section data-testid="assemble" className="screen">
        <div className="state-block empty-state">
          <h1 className="empty-title">No items yet</h1>
          <p className="state-note">Add your first piece to start building a look.</p>
          <Link to="/add" className="btn btn-primary">
            + Add an item
          </Link>
        </div>
      </section>
    )
  }

  return (
    <section data-testid="assemble" className="screen">
      <p className="state-note">Drag items from your wardrobe onto the mannequin.</p>
      <Mannequin />
    </section>
  )
}
