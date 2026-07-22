import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import WardrobeCell from '../components/WardrobeCell'
import { listItems } from '../api/items'
import { groupByCategory } from '../lib/wardrobeSections'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'

/**
 * Wardrobe screen (`/wardrobe`): the wardrobe as a mobile-first photo grid,
 * grouped into category sections (spec Unit 3). Fetches the item list on
 * mount, buckets it via `groupByCategory` (read-time `normalizeCategory` of
 * each item's stored category, fixed taxonomy order, `Other` last, empty
 * sections omitted), and renders each item as a lazy-loaded thumbnail linking
 * to its detail route. Handles the three real edge states — loading, empty,
 * and list-failure (with retry) — without crashing.
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

  // Drop a deleted item from the local list — no refetch. `groupByCategory` and
  // the empty-state branch both derive from `items`, so emptied sections and the
  // empty wardrobe fall out automatically on re-render.
  const handleDeleted = (id: string) => {
    setItems((prev) => prev.filter((it) => it.itemId !== id))
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
      {groupByCategory(items).map((group) => (
        <section key={group.category} className="wardrobe-section">
          <h2 className="section-header">{group.label}</h2>
          <ul className="grid">
            {group.items.map((it) => (
              <WardrobeCell key={it.itemId} item={it} onDeleted={handleDeleted} />
            ))}
          </ul>
        </section>
      ))}
    </section>
  )
}
