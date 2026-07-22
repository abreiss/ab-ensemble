import { describe, expect, it } from 'vitest'

import { CATEGORIES, type Category } from './categoryTaxonomy'
import { NEUTRAL_SWATCH, deriveName, slotForCategory, swatchColor } from './specSheet'

describe('specSheet helpers', () => {
  describe('slotForCategory', () => {
    it.each(['shirt', 'tee', 't-shirt', 'tshirt', 'top', 'sweater', 'jacket'])(
      'maps %s to TOP',
      (category) => {
        expect(slotForCategory(category)).toBe('TOP')
      },
    )

    // Binds `specSheet.ts`'s slot map to `categoryTaxonomy.ts`'s taxonomy as a
    // test invariant (assumption A2.4): every taxonomy value must resolve to a
    // slot so the outfit-result card never regresses to the generic PIECE
    // label once vision emits taxonomy values. Grouping bucket (categoryTaxonomy)
    // and card slot (specSheet) stay distinct mappings — this only asserts the
    // slot side is complete for the full vocabulary.
    it.each<[Category, ReturnType<typeof slotForCategory>]>([
      ['Jacket', 'TOP'],
      ['Top', 'TOP'],
      ['Bottom', 'BOTTOM'],
      ['Dress', 'TOP'],
      ['Shoes', 'SHOES'],
      ['Jewelry', 'CARRY'],
      ['Accessory', 'CARRY'],
      ['Other', 'PIECE'],
    ])('maps taxonomy value %s to slot %s', (category, expectedSlot) => {
      expect(slotForCategory(category)).toBe(expectedSlot)
    })

    it('covers every taxonomy value in the assertion above (no silent gaps)', () => {
      const asserted: Category[] = [
        'Jacket',
        'Top',
        'Bottom',
        'Dress',
        'Shoes',
        'Jewelry',
        'Accessory',
        'Other',
      ]
      expect(new Set(asserted)).toEqual(new Set(CATEGORIES))
    })

    it('maps the vision tagger casing (T-Shirt) to TOP', () => {
      expect(slotForCategory('T-Shirt')).toBe('TOP')
    })

    it.each(['footwear'])('maps %s to SHOES', (category) => {
      expect(slotForCategory(category)).toBe('SHOES')
    })

    it.each(['hat', 'cap', 'bag', 'tote', 'accessory'])('maps %s to CARRY', (category) => {
      expect(slotForCategory(category)).toBe('CARRY')
    })

    it.each(['pants', 'chinos', 'jeans', 'shorts', 'skirt'])('maps %s to BOTTOM', (category) => {
      expect(slotForCategory(category)).toBe('BOTTOM')
    })

    it.each(['shoes', 'loafers', 'sneakers', 'boots'])('maps %s to SHOES', (category) => {
      expect(slotForCategory(category)).toBe('SHOES')
    })

    it('is case-insensitive and trims surrounding whitespace', () => {
      expect(slotForCategory('  Sneakers ')).toBe('SHOES')
    })

    it('falls back to PIECE for an unknown category', () => {
      expect(slotForCategory('kimono')).toBe('PIECE')
    })

    it('falls back to PIECE for a null or blank category', () => {
      expect(slotForCategory(null)).toBe('PIECE')
      expect(slotForCategory('   ')).toBe('PIECE')
    })
  })

  describe('deriveName', () => {
    it('joins color + lead descriptor + category in sentence case', () => {
      expect(
        deriveName({ primaryColor: 'white', descriptors: ['linen', 'relaxed'], category: 'shirt' }),
      ).toBe('White linen shirt')
    })

    it('uses only the first descriptor when several are present', () => {
      expect(
        deriveName({ primaryColor: 'navy', descriptors: ['slim', 'stretch'], category: 'jeans' }),
      ).toBe('Navy slim jeans')
    })

    it('drops a null color and keeps descriptor + category', () => {
      expect(
        deriveName({ primaryColor: null, descriptors: ['linen'], category: 'shirt' }),
      ).toBe('Linen shirt')
    })

    it('drops an absent descriptor and keeps color + category', () => {
      expect(deriveName({ primaryColor: 'white', descriptors: [], category: 'shirt' })).toBe(
        'White shirt',
      )
      expect(
        deriveName({ primaryColor: 'white', descriptors: null, category: 'shirt' }),
      ).toBe('White shirt')
    })

    it('falls back to the category alone when color and descriptor are missing', () => {
      expect(deriveName({ primaryColor: null, descriptors: null, category: 'shirt' })).toBe('Shirt')
    })

    it('ignores blank-string parts', () => {
      expect(
        deriveName({ primaryColor: '  ', descriptors: ['   '], category: 'boots' }),
      ).toBe('Boots')
    })

    it('degrades to a safe label when every tag is null', () => {
      expect(deriveName({ primaryColor: null, descriptors: null, category: null })).toBe('Garment')
    })
  })

  describe('swatchColor', () => {
    it.each(['white', 'olive', 'tan'])('passes the CSS keyword %s through as-is', (color) => {
      expect(swatchColor(color)).toBe(color)
    })

    it('is case-insensitive and trims for keyword colors', () => {
      expect(swatchColor('  Olive ')).toBe('olive')
    })

    it('maps a curated non-keyword color name to a hex value', () => {
      expect(swatchColor('natural')).toBe('#cdbf9f')
    })

    it('falls back to the neutral swatch for an unknown color name', () => {
      expect(swatchColor('ultraviolet')).toBe(NEUTRAL_SWATCH)
    })

    it('falls back to the neutral swatch for a null or blank color', () => {
      expect(swatchColor(null)).toBe(NEUTRAL_SWATCH)
      expect(swatchColor('   ')).toBe(NEUTRAL_SWATCH)
    })
  })
})
