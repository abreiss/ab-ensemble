import { Fragment, type ReactNode } from 'react'
import { useDroppable } from '@dnd-kit/core'

import type { Slot } from '../lib/specSheet'

interface ZoneDef {
  /** Stable `Slot` identifier used both as the visible zone's data attribute
   * and as the dnd-kit `useDroppable` id. */
  slot: Slot
  label: string
  className: string
}

/**
 * The three body-region zones overlaid on the silhouette, keyed to the
 * existing `Slot` type (`lib/specSheet.ts`): head-to-torso for `TOP`, legs for
 * `BOTTOM`, feet for `SHOES`.
 */
const BODY_ZONES: ZoneDef[] = [
  { slot: 'TOP', label: 'Top', className: 'mannequin-zone-top' },
  { slot: 'BOTTOM', label: 'Bottom', className: 'mannequin-zone-bottom' },
  { slot: 'SHOES', label: 'Shoes', className: 'mannequin-zone-shoes' },
]

/**
 * The side "extras" tray. `CARRY` is its stable identifier; unrecognized/
 * `PIECE` items degrade into the same tray, a rule owned by the placement
 * model (`lib/placement.ts`), not this component.
 */
const EXTRAS_ZONE: ZoneDef = { slot: 'CARRY', label: 'Extras', className: 'mannequin-zone-extras' }

interface MannequinProps {
  /** Item ids currently placed in each zone, keyed by `Slot`. A slot missing
   * from this map (or with an empty array) simply renders no placed tiles. */
  placed?: Partial<Record<Slot, readonly string[]>>
  /** Renders one placed item's tile (photo, etc). `Mannequin` stays agnostic
   * of the `Item` shape and of `photoUrl` — the caller supplies the render. */
  renderPlacedItem?: (itemId: string) => ReactNode
}

interface MannequinZoneProps {
  zone: ZoneDef
  children?: ReactNode
}

/**
 * One droppable zone: wires dnd-kit's `useDroppable` (keyed by the zone's
 * `Slot`, carried in `data.slot` so `Assemble.tsx`'s `onDragEnd` can read the
 * drop target without a second slot lookup) and renders whatever placed
 * tiles the caller passes as `children`. Extracted from `Mannequin` so each
 * zone's hook call is unconditional (rules-of-hooks) rather than nested in a
 * `.map()` callback.
 */
function MannequinZone({ zone, children }: MannequinZoneProps) {
  const { setNodeRef, isOver } = useDroppable({ id: zone.slot, data: { slot: zone.slot } })

  return (
    <div
      ref={setNodeRef}
      className={`mannequin-zone ${zone.className}${isOver ? ' is-drop-target' : ''}`}
      data-slot={zone.slot}
      id={`assemble-zone-${zone.slot}`}
    >
      <span className="mannequin-zone-label">{zone.label}</span>
      {children && <div className="mannequin-zone-items">{children}</div>}
    </div>
  )
}

/**
 * Static, hand-authored SVG line-art mannequin silhouette with four labeled,
 * `Slot`-keyed drop regions: a head-to-torso region for `TOP`, a legs region
 * for `BOTTOM`, a feet region for `SHOES`, and a side "extras" tray for
 * `CARRY`/`PIECE`. Per the spec's "forgiving hit-boxes" design note, each
 * body-region zone's hit area is a generous, possibly-overlapping rectangle
 * rather than a pixel-perfect trace of the silhouette, so a thumb on a small
 * screen reliably lands a drop.
 */
export default function Mannequin({ placed = {}, renderPlacedItem }: MannequinProps) {
  const itemsFor = (slot: Slot): ReactNode => {
    if (!renderPlacedItem) {
      return null
    }
    return (placed[slot] ?? []).map((itemId) => (
      <Fragment key={itemId}>{renderPlacedItem(itemId)}</Fragment>
    ))
  }

  return (
    <div className="mannequin" data-testid="mannequin">
      <div className="mannequin-figure-wrap">
        <svg
          className="mannequin-figure"
          viewBox="0 0 200 320"
          role="img"
          aria-label="Mannequin silhouette"
          focusable="false"
        >
          <circle cx="100" cy="30" r="18" />
          <path d="M70 48 L130 48 L138 140 L62 140 Z" />
          <path d="M70 55 L40 120" />
          <path d="M130 55 L160 120" />
          <path d="M70 140 L62 260 L88 260 L98 150" />
          <path d="M130 140 L138 260 L112 260 L102 150" />
          <path d="M55 260 L95 260 L95 275 L55 275 Z" />
          <path d="M105 260 L145 260 L145 275 L105 275 Z" />
        </svg>

        {BODY_ZONES.map((zone) => (
          <MannequinZone key={zone.slot} zone={zone}>
            {itemsFor(zone.slot)}
          </MannequinZone>
        ))}
      </div>

      <MannequinZone zone={EXTRAS_ZONE}>{itemsFor(EXTRAS_ZONE.slot)}</MannequinZone>
    </div>
  )
}
