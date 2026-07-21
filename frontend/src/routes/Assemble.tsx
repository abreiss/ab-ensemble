import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { DndContext, useDraggable, useSensor, useSensors } from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'

import Mannequin from '../components/Mannequin'
import { listItems, photoUrl } from '../api/items'
import { pointerSensorConfig, touchSensorConfig } from '../lib/dndConfig'
import { createPlacement, placeItem, type PlacementState } from '../lib/placement'
import type { Slot } from '../lib/specSheet'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'

/** The shape a draggable source tile's dnd-kit `data` carries. */
interface DraggableData {
  category?: string | null
}

/** The shape a droppable mannequin zone's dnd-kit `data` carries. */
interface DroppableData {
  slot?: Slot
}

interface SourceTileProps {
  item: Item
}

/**
 * One draggable wardrobe tile in the source list, reusing `WardrobeDrawer`'s
 * `drawer-tile` look. This is a minimal, unexcluded placeholder source for
 * Unit 2 (drag-and-drop mechanics); Unit 3 replaces it with the real
 * `AssembleSource` (search + already-placed exclusion).
 */
function SourceTile({ item: it }: SourceTileProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: it.itemId,
    data: { category: it.category } satisfies DraggableData,
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
 * Manual outfit-assembly screen (`/assemble`) — the app's non-AI escape hatch.
 * The user drags wardrobe items onto a static mannequin silhouette to build a
 * look by hand, instead of asking the AI stylist to pick one. No Claude call
 * happens here; the screen only reads/writes existing item data. Fetches the
 * item list on mount using the same `listItems()` + `settle()` loading/error
 * pattern as `WardrobeGrid.tsx` / `WardrobeDrawer.tsx` so a failed fetch never
 * crashes the screen.
 *
 * Placement is session-only in-memory state (`lib/placement.ts`, a pure
 * reducer/helper): dropping a source tile onto a mannequin zone calls
 * `placeItem`, which routes it to its default zone via `slotForCategory`
 * unless the drop target overrides it, replaces the current occupant for the
 * single-occupancy `TOP`/`BOTTOM`/`SHOES` zones, and accumulates in the
 * multi-occupancy `CARRY`/`PIECE` extras tray.
 */
export default function Assemble() {
  const [items, setItems] = useState<Item[]>([])
  const [status, setStatus] = useState<Status>('loading')
  const [placement, setPlacement] = useState<PlacementState>(() => createPlacement())

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

  // Renders a placed item's tile inside its mannequin zone, reusing the same
  // `drawer-tile` look as the source list. `Mannequin` stays agnostic of the
  // `Item` shape / `photoUrl` — this is the render callback it invokes.
  const renderPlacedItem = useCallback(
    (itemId: string) => {
      const placedItem = items.find((it) => it.itemId === itemId)
      return (
        <div className="drawer-tile placed-tile" data-item-id={itemId}>
          <img
            className="drawer-tile-img"
            src={photoUrl(itemId)}
            alt={placedItem?.category ?? 'garment'}
            loading="lazy"
          />
        </div>
      )
    },
    [items],
  )

  // dnd-kit fires `onDragEnd` on every drag release, including ones that
  // never crossed a valid drop target (`over` is null then) — those are
  // no-ops here, not a fall-through to a default zone. When `over` is a real
  // mannequin zone, its `data.slot` is always populated (see `Mannequin.tsx`),
  // so this becomes the manual-override path; passing `undefined` instead
  // would fall back to the item's category default in `placeItem`.
  const onDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over) {
      return
    }
    const category = (active.data.current as DraggableData | undefined)?.category ?? null
    const targetSlot = (over.data.current as DroppableData | undefined)?.slot
    setPlacement((prev) => placeItem(prev, String(active.id), category, targetSlot))
  }, [])

  const pointerSensor = useSensor(pointerSensorConfig.sensor, pointerSensorConfig.options)
  const touchSensor = useSensor(touchSensorConfig.sensor, touchSensorConfig.options)
  const sensors = useSensors(pointerSensor, touchSensor)

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
      <DndContext sensors={sensors} onDragEnd={onDragEnd}>
        <Mannequin placed={placement} renderPlacedItem={renderPlacedItem} />
        <ul className="drawer-grid assemble-source">
          {items.map((it) => (
            <SourceTile key={it.itemId} item={it} />
          ))}
        </ul>
      </DndContext>
    </section>
  )
}
