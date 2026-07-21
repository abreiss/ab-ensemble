import { useMemo, useState } from 'react'
import { Search } from 'lucide-react'
import { useDraggable, useDroppable } from '@dnd-kit/core'

import { photoUrl } from '../api/items'
import type { Item } from '../types/item'

/** The lowercased text a tile is matched against when searching. Kept in
 * lockstep with `WardrobeDrawer`'s `searchText` so the two sources filter
 * identically — do not let the two drift apart. */
function searchText(it: Item): string {
  return [it.category, it.primaryColor, ...(it.descriptors ?? [])]
    .filter((v): v is string => typeof v === 'string')
    .join(' ')
    .toLowerCase()
}

/**
 * The dnd-kit droppable id for "drop back onto the source list" — dragging a
 * placed tile here removes it from the mannequin (Unit 3's
 * drag-back-to-source requirement). It is intentionally not a `Slot` value so
 * `Assemble.tsx`'s `onDragEnd` can distinguish "returned to source" from "drop
 * missed every zone" (`over` null) and from a real zone drop.
 */
export const SOURCE_DROPPABLE_ID = 'assemble-source'

interface AssembleSourceProps {
  /** The full wardrobe list (already fetched by the caller — `Assemble.tsx`
   * owns the `listItems()` call so this component stays fetch-free and easy
   * to unit test). */
  items: Item[]
  /** Ids currently placed on the mannequin; their tiles are excluded here so
   * an item can never be placed twice. */
  placedIds?: string[]
}

interface SourceTileProps {
  item: Item
}

/** One draggable wardrobe tile, reusing `WardrobeDrawer`'s `drawer-tile` look. */
function SourceTile({ item: it }: SourceTileProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: it.itemId,
    data: { category: it.category },
  })

  return (
    <li
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      data-item-id={it.itemId}
      className={isDragging ? 'drawer-tile is-dragging' : 'drawer-tile'}
    >
      <img
        className="drawer-tile-img"
        src={photoUrl(it.itemId)}
        alt={it.category ?? 'garment'}
        loading="lazy"
      />
    </li>
  )
}

/**
 * The real wardrobe drag-source for `/assemble` — a sibling of
 * `WardrobeDrawer` (per assumption A1.3: reuse its CSS classes/markup and
 * `searchText` filter rather than mutating the display-only drawer used
 * elsewhere in the app). Adds two things `WardrobeDrawer` doesn't need:
 * draggable tiles (`useDraggable`) and a `placedIds` exclusion filter so an
 * item already on the mannequin cannot also appear in the source. The whole
 * panel is also a dnd-kit droppable (`SOURCE_DROPPABLE_ID`) so dragging a
 * placed tile back here removes it — see `Assemble.tsx`'s `onDragEnd`.
 */
export default function AssembleSource({ items, placedIds = [] }: AssembleSourceProps) {
  const [query, setQuery] = useState('')
  const { setNodeRef, isOver } = useDroppable({ id: SOURCE_DROPPABLE_ID })

  const placed = useMemo(() => new Set(placedIds), [placedIds])
  const available = useMemo(() => items.filter((it) => !placed.has(it.itemId)), [items, placed])

  const needle = query.trim().toLowerCase()
  const visible =
    needle === '' ? available : available.filter((it) => searchText(it).includes(needle))

  return (
    <div
      ref={setNodeRef}
      className={isOver ? 'drawer-body assemble-source is-drop-target' : 'drawer-body assemble-source'}
    >
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

      {available.length === 0 ? (
        <p className="state-note">Everything is on the mannequin.</p>
      ) : (
        <ul className="drawer-grid">
          {visible.map((it) => (
            <SourceTile key={it.itemId} item={it} />
          ))}
        </ul>
      )}
    </div>
  )
}
