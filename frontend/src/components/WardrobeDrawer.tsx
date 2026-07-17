import { useCallback, useEffect, useMemo, useState } from 'react'
import { Search } from 'lucide-react'

import { listItems, photoUrl } from '../api/items'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'

interface WardrobeDrawerProps {
  /** Item ids in the current look; their tiles are outlined with `--accent`. */
  inLookIds?: string[]
}

/** The lowercased text a tile is matched against when searching. */
function searchText(it: Item): string {
  return [it.category, it.primaryColor, ...(it.descriptors ?? [])]
    .filter((v): v is string => typeof v === 'string')
    .join(' ')
    .toLowerCase()
}

/**
 * The wardrobe drawer beside the stylist: a search box over a 2-column grid of
 * the user's real photos (from `listItems()`). Tiles already in the current look
 * are outlined so the user can see what's been used. Mirrors the wardrobe grid's
 * loading / error / empty handling so a list failure never crashes the screen.
 */
export default function WardrobeDrawer({ inLookIds = [] }: WardrobeDrawerProps) {
  const [items, setItems] = useState<Item[]>([])
  const [status, setStatus] = useState<Status>('loading')
  const [query, setQuery] = useState('')

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

  const inLook = useMemo(() => new Set(inLookIds), [inLookIds])
  const needle = query.trim().toLowerCase()
  const visible = needle === '' ? items : items.filter((it) => searchText(it).includes(needle))

  return (
    <div className="drawer-body">
      <p className="drawer-title">Wardrobe</p>

      <div className="drawer-search">
        <Search className="drawer-search-icon" size={14} aria-hidden="true" />
        <input
          type="search"
          className="drawer-search-input"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search pieces"
          aria-label="Search pieces"
          autoComplete="off"
        />
      </div>

      {status === 'loading' && <p className="state-note">Loading…</p>}

      {status === 'error' && <p className="state-note">We couldn’t load your wardrobe.</p>}

      {status === 'ready' && items.length === 0 && (
        <p className="state-note">No pieces yet — add some from the wardrobe.</p>
      )}

      {status === 'ready' && items.length > 0 && (
        <ul className="drawer-grid">
          {visible.map((it) => (
            <li
              key={it.itemId}
              data-item-id={it.itemId}
              className={inLook.has(it.itemId) ? 'drawer-tile is-in-look' : 'drawer-tile'}
            >
              <img
                className="drawer-tile-img"
                src={photoUrl(it.itemId)}
                alt={it.category ?? 'garment'}
                loading="lazy"
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
