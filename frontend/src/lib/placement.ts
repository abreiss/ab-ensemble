import { slotForCategory, type Slot } from './specSheet'

/**
 * Pure, React-free manual-outfit placement model: an in-memory map of `Slot`
 * → placed item id(s). This is the "meaningful logic" for the manual
 * outfit-assembly screen (`/assemble`, spec Unit 2) — see
 * `docs/specs/21-spec-manual-outfit-assembly/`. It knows nothing about
 * dnd-kit, React, or the DOM, so the placement rules are unit-tested
 * directly rather than by simulating a drag gesture in jsdom (dnd-kit's
 * `PointerSensor` needs real pointer events + layout measurement that jsdom
 * does not implement — see the spec's Technical Considerations).
 *
 * Rules:
 * - Default routing: an item's zone is `slotForCategory(category)` — reused
 *   unchanged from `lib/specSheet.ts`, no second slot-mapping function —
 *   unless an explicit `targetSlot` override is given (the user dropped it
 *   onto a different zone), in which case the override wins.
 * - `TOP` / `BOTTOM` / `SHOES` are single-occupancy: placing a new item there
 *   replaces whatever was already there. The replaced item is not tracked
 *   anywhere else in this state — the caller's "available" list (all items
 *   minus `placedIds()`) shows it again automatically.
 * - `CARRY` / `PIECE` are multi-occupancy: items accumulate rather than
 *   replace one another.
 * - An item is only ever placed in one slot at a time: re-placing an
 *   already-placed id first removes it from its previous slot.
 */

/** Every slot the placement state tracks — mirrors `Slot` from `specSheet.ts`. */
const SLOTS: readonly Slot[] = ['TOP', 'BOTTOM', 'SHOES', 'CARRY', 'PIECE']

/** Slots that hold at most one item; placing a new item replaces the occupant. */
const SINGLE_OCCUPANCY_SLOTS: ReadonlySet<Slot> = new Set(['TOP', 'BOTTOM', 'SHOES'])

/** In-memory placement state: every slot maps to the item ids placed there. */
export type PlacementState = Readonly<Record<Slot, readonly string[]>>

/** A fresh, empty placement — nothing placed in any slot. */
export function createPlacement(): PlacementState {
  return { TOP: [], BOTTOM: [], SHOES: [], CARRY: [], PIECE: [] }
}

/** Returns `state` with `itemId` removed from every slot (a no-op if it wasn't placed). */
function withoutItem(state: PlacementState, itemId: string): PlacementState {
  const next = {} as Record<Slot, readonly string[]>
  for (const slot of SLOTS) {
    next[slot] = state[slot].filter((id) => id !== itemId)
  }
  return next
}

/**
 * Places `itemId` (of the given `category`) into the layout.
 *
 * - `targetSlot` omitted → default routing via `slotForCategory(category)`.
 * - `targetSlot` given → manual override; the item lands there instead of its
 *   category's default zone.
 * - Single-occupancy slots (`TOP`/`BOTTOM`/`SHOES`) end up holding exactly the
 *   new item (any prior occupant is displaced back to "available").
 * - Multi-occupancy slots (`CARRY`/`PIECE`) end up holding the new item
 *   alongside whatever else was already there.
 */
export function placeItem(
  state: PlacementState,
  itemId: string,
  category: string | null | undefined,
  targetSlot?: Slot,
): PlacementState {
  const slot = targetSlot ?? slotForCategory(category)
  const cleared = withoutItem(state, itemId)
  if (SINGLE_OCCUPANCY_SLOTS.has(slot)) {
    return { ...cleared, [slot]: [itemId] }
  }
  return { ...cleared, [slot]: [...cleared[slot], itemId] }
}

/** Removes `itemId` from wherever it is currently placed (a no-op if it wasn't placed). */
export function removeItem(state: PlacementState, itemId: string): PlacementState {
  return withoutItem(state, itemId)
}

/** All currently-placed item ids across every slot, in a stable slot order. */
export function placedIds(state: PlacementState): string[] {
  return SLOTS.flatMap((slot) => state[slot])
}
