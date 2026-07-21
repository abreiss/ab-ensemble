import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { DndContext, useDraggable, useSensor, useSensors } from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'

import AssembleSource, { SOURCE_DROPPABLE_ID } from '../components/AssembleSource'
import Mannequin from '../components/Mannequin'
import { listItems, markWorn, photoUrl } from '../api/items'
import { pointerSensorConfig, touchSensorConfig } from '../lib/dndConfig'
import { createPlacement, placeItem, placedIds, removeItem, type PlacementState } from '../lib/placement'
import type { Slot } from '../lib/specSheet'
import type { Item } from '../types/item'

type Status = 'loading' | 'ready' | 'error'
type LogStatus = 'idle' | 'logging' | 'logged' | 'error'

/** The shape a draggable tile's dnd-kit `data` carries — both source tiles
 * (`AssembleSource`) and placed tiles (`PlacedTile` below) carry the same
 * shape so `onDragEnd` reads it the same way regardless of where the drag
 * started. */
interface DraggableData {
  category?: string | null
}

/** The shape a droppable mannequin zone's dnd-kit `data` carries. */
interface DroppableData {
  slot?: Slot
}

interface PlacedTileProps {
  itemId: string
  category?: string | null
  onRemove: (itemId: string) => void
}

/**
 * A placed item's tile inside a mannequin zone: the photo, plus a ≥44px tap
 * "×" that removes it without a drag (spec Unit 3). The tile is also
 * `useDraggable` (same `id`/`data` shape as a source tile) so it can be
 * dragged back onto the wardrobe source to remove it instead — `onRemove` is
 * shared by both paths, `Assemble.tsx`'s `onDragEnd` calls it when the drop
 * target is `AssembleSource`'s `SOURCE_DROPPABLE_ID`.
 */
function PlacedTile({ itemId, category, onRemove }: PlacedTileProps) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: itemId,
    data: { category } satisfies DraggableData,
  })

  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      data-item-id={itemId}
      className={isDragging ? 'drawer-tile placed-tile is-dragging' : 'drawer-tile placed-tile'}
    >
      <img
        className="drawer-tile-img"
        src={photoUrl(itemId)}
        alt={category ?? 'garment'}
        loading="lazy"
      />
      <button
        type="button"
        className="placed-remove"
        aria-label="Remove item"
        style={{ minWidth: 44, minHeight: 44 }}
        // A tap on "×" must never be captured as the start of a drag: without
        // this, dnd-kit's pointer listeners (spread on this same node's
        // ancestor) would see the pointerdown before the button's click.
        onPointerDown={(event) => event.stopPropagation()}
        onClick={() => onRemove(itemId)}
      >
        <span aria-hidden="true">×</span>
      </button>
    </div>
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
  const [logStatus, setLogStatus] = useState<LogStatus>('idle')

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

  // Tap- or drag-back-removal both land here: pulls `itemId` out of whichever
  // slot holds it (a no-op if it somehow isn't placed) so it reappears in
  // `AssembleSource`'s exclusion-filtered list on the next render.
  const handleRemove = useCallback((itemId: string) => {
    setPlacement((prev) => removeItem(prev, itemId))
  }, [])

  // Renders a placed item's tile inside its mannequin zone as a draggable
  // `PlacedTile` (photo + "×"), reusing the same `drawer-tile` look as the
  // source list. `Mannequin` stays agnostic of the `Item` shape / `photoUrl`
  // — this is the render callback it invokes.
  const renderPlacedItem = useCallback(
    (itemId: string) => {
      const placedItem = items.find((it) => it.itemId === itemId)
      return (
        <PlacedTile itemId={itemId} category={placedItem?.category} onRemove={handleRemove} />
      )
    },
    [items, handleRemove],
  )

  // dnd-kit fires `onDragEnd` on every drag release, including ones that
  // never crossed a valid drop target (`over` is null then) — those are
  // no-ops here, not a fall-through to a default zone. Dropping onto
  // `AssembleSource`'s `SOURCE_DROPPABLE_ID` is the drag-back-to-source
  // removal path (Unit 3), handled before the zone-routing logic since that
  // id is not a `Slot` and carries no `data.slot`. When `over` is a real
  // mannequin zone, its `data.slot` is always populated (see `Mannequin.tsx`),
  // so this becomes the manual-override path; passing `undefined` instead
  // would fall back to the item's category default in `placeItem`.
  const onDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over) {
      return
    }
    if (over.id === SOURCE_DROPPABLE_ID) {
      setPlacement((prev) => removeItem(prev, String(active.id)))
      return
    }
    const category = (active.data.current as DraggableData | undefined)?.category ?? null
    const targetSlot = (over.data.current as DroppableData | undefined)?.slot
    setPlacement((prev) => placeItem(prev, String(active.id), category, targetSlot))
  }, [])

  const pointerSensor = useSensor(pointerSensorConfig.sensor, pointerSensorConfig.options)
  const touchSensor = useSensor(touchSensorConfig.sensor, touchSensorConfig.options)
  const sensors = useSensors(pointerSensor, touchSensor)

  const currentlyPlacedIds = placedIds(placement)

  // Log the assembled set worn: fans out to the existing per-item `markWorn`
  // (`POST /api/items/:id/worn`) once per placed id, mirroring the exact
  // `Promise.allSettled(...)` + `idle|logging|logged|error` lifecycle used by
  // `Stylist.tsx` / `OutfitResult.tsx` for an AI-picked look — the wear-history
  // write stays deterministic and server-owned either way. No new endpoint, no
  // new persisted "outfit" entity: this screen only re-uses the item write.
  const logWorn = useCallback(() => {
    if (currentlyPlacedIds.length === 0) {
      return
    }
    setLogStatus('logging')
    Promise.allSettled(currentlyPlacedIds.map((itemId) => markWorn(itemId))).then((results) => {
      const anyFailed = results.some((result) => result.status === 'rejected')
      setLogStatus(anyFailed ? 'error' : 'logged')
    })
  }, [currentlyPlacedIds])

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
        <AssembleSource items={items} placedIds={currentlyPlacedIds} />
      </DndContext>

      <div className="assemble-actions">
        {logStatus === 'logged' ? (
          <button type="button" className="btn btn-logged" disabled>
            Logged ✓
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-primary"
            onClick={logWorn}
            disabled={currentlyPlacedIds.length === 0 || logStatus === 'logging'}
          >
            Wear today
          </button>
        )}

        {logStatus === 'error' && (
          <p className="banner banner-error" role="alert">
            We couldn’t log that look. Please try again.
          </p>
        )}
      </div>
    </section>
  )
}
