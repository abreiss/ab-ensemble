import type { Slot } from '../lib/specSheet'

interface ZoneDef {
  /** Stable `Slot` identifier used both as the visible zone's data attribute
   * and (in a later unit) as the dnd-kit `useDroppable` id. */
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
 * model added in a later unit, not this component.
 */
const EXTRAS_ZONE: ZoneDef = { slot: 'CARRY', label: 'Extras', className: 'mannequin-zone-extras' }

/**
 * Static, hand-authored SVG line-art mannequin silhouette with four labeled,
 * `Slot`-keyed drop regions: a head-to-torso region for `TOP`, a legs region
 * for `BOTTOM`, a feet region for `SHOES`, and a side "extras" tray for
 * `CARRY`/`PIECE`. Per the spec's "forgiving hit-boxes" design note, each
 * body-region zone's hit area is a generous, possibly-overlapping rectangle
 * rather than a pixel-perfect trace of the silhouette, so a thumb on a small
 * screen reliably lands a drop. This component only renders the static
 * scaffold — dnd-kit `useDroppable` wiring is added in a later unit (see spec
 * Unit 2).
 */
export default function Mannequin() {
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
          <div
            key={zone.slot}
            className={`mannequin-zone ${zone.className}`}
            data-slot={zone.slot}
            id={`assemble-zone-${zone.slot}`}
          >
            <span className="mannequin-zone-label">{zone.label}</span>
          </div>
        ))}
      </div>

      <div
        className={`mannequin-zone ${EXTRAS_ZONE.className}`}
        data-slot={EXTRAS_ZONE.slot}
        id={`assemble-zone-${EXTRAS_ZONE.slot}`}
      >
        <span className="mannequin-zone-label">{EXTRAS_ZONE.label}</span>
      </div>
    </div>
  )
}
