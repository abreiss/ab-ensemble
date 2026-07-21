import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { listItems, photoUrl } from '../api/items'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'

/**
 * Home screen (`/`): the wardrobe as a mobile-first photo grid. Fetches the item
 * list on mount and renders each as a lazy-loaded thumbnail linking to its detail
 * route. Handles the three real edge states — loading, empty, and list-failure
 * (with retry) — without crashing.
 */
export default function WardrobeGrid() {
  const [items, setItems] = useState<Item[]>([])
  const [status, setStatus] = useState<Status>('loading')

  // Settle a list request into state via promise callbacks. Keeping setState in
  // the .then/.catch callbacks (not the effect body) is the pattern the
  // react-hooks effect rule endorses for syncing with an external system.
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
      <section data-testid="wardrobe-grid" className="screen">
        <p className="state-note">Loading your wardrobe…</p>
      </section>
    )
  }

  if (status === 'error') {
    return (
      <section data-testid="wardrobe-grid" className="screen">
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
      <section data-testid="wardrobe-grid" className="screen">
        <div className="state-block empty-state">
          <h1 className="empty-title">No items yet</h1>
          <p className="state-note">Add your first piece to start your wardrobe.</p>
          <Link to="/add" className="btn btn-primary">
            + Add an item
          </Link>
        </div>
      </section>
    )
  }

  return (
    <section data-testid="wardrobe-grid" className="screen">
      <Link to="/assemble" className="btn assemble-entry">
        Build it yourself
      </Link>
      <ul className="grid">
        {items.map((it) => (
          <li key={it.itemId} className="grid-cell">
            <Link to={`/item/${it.itemId}`} className="thumb">
              <img
                className="thumb-img"
                src={photoUrl(it.itemId)}
                alt={it.category ?? 'garment'}
                loading="lazy"
              />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
