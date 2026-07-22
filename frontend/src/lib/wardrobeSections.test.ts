import { describe, expect, it } from 'vitest'

import { groupByCategory } from './wardrobeSections'
import type { Item } from '../types/item'

// Pure grouping/ordering logic for the wardrobe grid (spec Unit 3): the
// headline behavior under test, independent of React. Read-time
// `normalizeCategory` buckets each item by its *stored* category — no stored
// value is mutated (FR 3.2) — sections render in fixed taxonomy order with
// `Other` always last (FR 3.3), and empty sections are omitted (FR 3.4).

function item(id: string, category: string | null): Item {
  return {
    itemId: id,
    category,
    primaryColor: 'navy',
    secondaryColor: null,
    formality: 3,
    pattern: null,
    warmth: 2,
    descriptors: null,
    photoUrl: `/api/items/${id}/photo`,
    createdAt: '2026-01-01T00:00:00Z',
    lastWorn: null,
    wornCount: 0,
  }
}

describe('groupByCategory', () => {
  it('buckets a mixed list (legacy free-text + unrecognized + canonical) into fixed-order sections with Other last', () => {
    const items = [
      item('shirt-item', 'shirt'), // legacy → Top
      item('chinos-item', 'chinos'), // legacy → Bottom
      item('widget-item', 'widget'), // unrecognized → Other
      item('jewelry-item', 'Jewelry'), // canonical
      item('jacket-item', 'Jacket'), // canonical
    ]

    const groups = groupByCategory(items)

    expect(groups.map((g) => g.category)).toEqual(['Jacket', 'Top', 'Bottom', 'Jewelry', 'Other'])
    expect(groups[groups.length - 1].category).toBe('Other')
  })

  it('omits empty sections entirely', () => {
    const items = [item('a', 'Jacket'), item('b', 'Shoes')]

    const groups = groupByCategory(items)

    expect(groups).toHaveLength(2)
    expect(groups.map((g) => g.category)).toEqual(['Jacket', 'Shoes'])
  })

  it('places each item under the expected bucket, keyed by its stored category', () => {
    const shirt = item('shirt-item', 'shirt')
    const chinos = item('chinos-item', 'chinos')

    const groups = groupByCategory([shirt, chinos])

    const topGroup = groups.find((g) => g.category === 'Top')
    const bottomGroup = groups.find((g) => g.category === 'Bottom')
    expect(topGroup?.items).toEqual([shirt])
    expect(bottomGroup?.items).toEqual([chinos])
  })

  it('attaches the display label for every rendered section', () => {
    const groups = groupByCategory([item('a', 'Jewelry'), item('b', 'Accessory')])

    const jewelry = groups.find((g) => g.category === 'Jewelry')
    const accessory = groups.find((g) => g.category === 'Accessory')
    expect(jewelry?.label).toBe('Jewelry')
    expect(accessory?.label).toBe('Accessories')
  })

  it('groups a Jewelry item into the Jewelry section', () => {
    const necklace = item('necklace-item', 'necklace') // legacy synonym → Jewelry

    const groups = groupByCategory([necklace])

    expect(groups).toEqual([{ category: 'Jewelry', label: 'Jewelry', items: [necklace] }])
  })

  it('does not mutate the stored category of grouped items', () => {
    const shirt = item('shirt-item', 'shirt')

    groupByCategory([shirt])

    expect(shirt.category).toBe('shirt')
  })

  it('returns no groups for an empty item list', () => {
    expect(groupByCategory([])).toEqual([])
  })
})
