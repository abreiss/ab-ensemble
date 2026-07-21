import { describe, expect, it } from 'vitest'

import { createPlacement, placeItem, placedIds, removeItem } from './placement'

describe('createPlacement', () => {
  it('starts with every slot empty and no placed ids', () => {
    const state = createPlacement()

    expect(state).toEqual({ TOP: [], BOTTOM: [], SHOES: [], CARRY: [], PIECE: [] })
    expect(placedIds(state)).toEqual([])
  })
})

describe('placeItem — default routing via slotForCategory', () => {
  it('routes a recognized category to its default slot when no target is given', () => {
    const state = placeItem(createPlacement(), 'shirt-1', 'shirt')

    expect(state.TOP).toEqual(['shirt-1'])
    expect(placedIds(state)).toEqual(['shirt-1'])
  })

  it('degrades an unrecognized category to PIECE', () => {
    const state = placeItem(createPlacement(), 'mystery-1', 'monocle')

    expect(state.PIECE).toEqual(['mystery-1'])
  })

  it('degrades a null category to PIECE', () => {
    const state = placeItem(createPlacement(), 'mystery-2', null)

    expect(state.PIECE).toEqual(['mystery-2'])
  })

  it('degrades an undefined category to PIECE', () => {
    const state = placeItem(createPlacement(), 'mystery-3', undefined)

    expect(state.PIECE).toEqual(['mystery-3'])
  })
})

describe('placeItem — drop-into-different-zone override', () => {
  it('lands the item in the explicit target slot instead of its category default', () => {
    // "shirt" defaults to TOP, but the user dropped it directly onto BOTTOM.
    const state = placeItem(createPlacement(), 'shirt-1', 'shirt', 'BOTTOM')

    expect(state.BOTTOM).toEqual(['shirt-1'])
    expect(state.TOP).toEqual([])
  })

  it('lets an unrecognized-category item be overridden into a body-region slot', () => {
    const state = placeItem(createPlacement(), 'mystery-1', 'monocle', 'SHOES')

    expect(state.SHOES).toEqual(['mystery-1'])
    expect(state.PIECE).toEqual([])
  })
})

describe('placeItem — single-occupancy replace (TOP/BOTTOM/SHOES)', () => {
  it.each(['TOP', 'BOTTOM', 'SHOES'] as const)(
    'replaces the current %s occupant and returns the displaced item to available',
    (slot) => {
      const afterFirst = placeItem(createPlacement(), 'item-a', null, slot)
      const afterSecond = placeItem(afterFirst, 'item-b', null, slot)

      expect(afterSecond[slot]).toEqual(['item-b'])
      // The displaced item no longer appears anywhere in placement state, so the
      // caller's "available" list (all items minus placedIds()) shows it again.
      expect(placedIds(afterSecond)).toEqual(['item-b'])
    },
  )

  it('replacing with the same id again is a no-op (no duplicate entries)', () => {
    const placed = placeItem(createPlacement(), 'item-a', 'shirt')
    const replaced = placeItem(placed, 'item-a', 'shirt')

    expect(replaced.TOP).toEqual(['item-a'])
  })
})

describe('placeItem — multi-occupancy tray (CARRY/PIECE)', () => {
  it('accumulates multiple items in CARRY instead of replacing', () => {
    const withBag = placeItem(createPlacement(), 'bag-1', 'bag')
    const withHat = placeItem(withBag, 'hat-1', 'hat')

    expect(withHat.CARRY).toEqual(['bag-1', 'hat-1'])
    expect(placedIds(withHat)).toEqual(['bag-1', 'hat-1'])
  })

  it('accumulates multiple items in PIECE instead of replacing', () => {
    const first = placeItem(createPlacement(), 'mystery-1', 'unknown-category')
    const second = placeItem(first, 'mystery-2', 'another-unknown')

    expect(second.PIECE).toEqual(['mystery-1', 'mystery-2'])
  })
})

describe('placeItem — moving an already-placed item', () => {
  it('removes the item from its old slot when re-placed into a new one', () => {
    const inTop = placeItem(createPlacement(), 'shirt-1', 'shirt')
    const movedToCarry = placeItem(inTop, 'shirt-1', 'shirt', 'CARRY')

    expect(movedToCarry.TOP).toEqual([])
    expect(movedToCarry.CARRY).toEqual(['shirt-1'])
    expect(placedIds(movedToCarry)).toEqual(['shirt-1'])
  })
})

describe('removeItem', () => {
  it('frees a single-occupancy zone so a later drop is not treated as a replace of nothing', () => {
    const placed = placeItem(createPlacement(), 'shirt-1', 'shirt')
    const removed = removeItem(placed, 'shirt-1')

    expect(removed.TOP).toEqual([])
    expect(placedIds(removed)).toEqual([])
  })

  it('removing one tray item leaves the other tray items intact', () => {
    const withBag = placeItem(createPlacement(), 'bag-1', 'bag')
    const withHat = placeItem(withBag, 'hat-1', 'hat')
    const removed = removeItem(withHat, 'bag-1')

    expect(removed.CARRY).toEqual(['hat-1'])
  })

  it('is a no-op when the id was never placed', () => {
    const state = createPlacement()

    expect(removeItem(state, 'never-placed')).toEqual(state)
  })
})

describe('placedIds', () => {
  it('flattens every slot into a single list of ids', () => {
    const withTop = placeItem(createPlacement(), 'shirt-1', 'shirt')
    const withBottom = placeItem(withTop, 'jeans-1', 'jeans')
    const withCarry = placeItem(withBottom, 'bag-1', 'bag')

    expect(placedIds(withCarry)).toEqual(['shirt-1', 'jeans-1', 'bag-1'])
  })
})
