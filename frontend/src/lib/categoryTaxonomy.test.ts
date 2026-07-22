import { describe, expect, it } from 'vitest'

import { CATEGORIES, CATEGORY_LABELS, normalizeCategory, sectionOrder } from './categoryTaxonomy'

// The frontend taxonomy mirrors the backend `CategoryTaxonomy` exactly (per
// assumption A1.4/A1.12): the same ordered values and the same starter
// legacy-synonym map, each stack's own source of truth. Cross-stack agreement
// is a review/test invariant, not shared code.

describe('CATEGORIES', () => {
  it('is the ordered eight-value taxonomy with Other last', () => {
    expect(CATEGORIES).toEqual([
      'Jacket',
      'Top',
      'Bottom',
      'Dress',
      'Shoes',
      'Jewelry',
      'Accessory',
      'Other',
    ])
  })
})

describe('normalizeCategory', () => {
  it.each(CATEGORIES)('maps the canonical value %s to itself (case-insensitive)', (value) => {
    expect(normalizeCategory(value)).toBe(value)
  })

  it.each([
    ['chinos', 'Bottom'],
    ['jeans', 'Bottom'],
    ['shirt', 'Top'],
    ['T-Shirt', 'Top'],
    ['necklace', 'Jewelry'],
    ['blazer', 'Jacket'],
    ['sneakers', 'Shoes'],
    ['tote', 'Accessory'],
    ['gown', 'Dress'],
  ] as const)('maps the legacy synonym %s to %s', (raw, expected) => {
    expect(normalizeCategory(raw)).toBe(expected)
  })

  it('is case- and whitespace-insensitive', () => {
    expect(normalizeCategory('  T-SHIRT ')).toBe('Top')
    expect(normalizeCategory('  chinos  ')).toBe('Bottom')
  })

  it('maps an unrecognized value to Other', () => {
    expect(normalizeCategory('widget')).toBe('Other')
  })

  it('maps a blank or whitespace-only value to Other', () => {
    expect(normalizeCategory('')).toBe('Other')
    expect(normalizeCategory('   ')).toBe('Other')
  })

  it('maps null and undefined to Other, never throwing', () => {
    expect(normalizeCategory(null)).toBe('Other')
    expect(normalizeCategory(undefined)).toBe('Other')
  })
})

describe('CATEGORY_LABELS', () => {
  it('maps every taxonomy value to its display label (singular→plural, A1.11)', () => {
    expect(CATEGORY_LABELS).toEqual({
      Jacket: 'Jackets',
      Top: 'Tops',
      Bottom: 'Bottoms',
      Dress: 'Dresses',
      Shoes: 'Shoes',
      Jewelry: 'Jewelry',
      Accessory: 'Accessories',
      Other: 'Other',
    })
  })

  it.each(CATEGORIES)('has a label defined for every taxonomy value %s', (value) => {
    expect(CATEGORY_LABELS[value]).toBeTruthy()
  })
})

describe('sectionOrder', () => {
  it('yields the taxonomy order with Other always last', () => {
    expect(sectionOrder()).toEqual([
      'Jacket',
      'Top',
      'Bottom',
      'Dress',
      'Shoes',
      'Jewelry',
      'Accessory',
      'Other',
    ])
  })

  it('contains every taxonomy value exactly once', () => {
    const order = sectionOrder()
    expect(order).toHaveLength(CATEGORIES.length)
    expect(new Set(order).size).toBe(CATEGORIES.length)
    expect(order[order.length - 1]).toBe('Other')
  })
})
