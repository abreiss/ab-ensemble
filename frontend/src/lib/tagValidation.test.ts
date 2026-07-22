import { describe, expect, it } from 'vitest'

import { tagsAreValid, validateTags } from './tagValidation'

// These rules mirror the backend `TagRequest` constraints exactly: `category` is
// required (non-blank); `formality` (1–5) and `warmth` (1–3) are optional — a
// null is valid (e.g. jewelry has neither), but a supplied value is still
// range-checked. This is critical guardrail logic, so every branch is exercised.

const valid = { category: 'shirt', formality: 3, warmth: 2 }

describe('validateTags', () => {
  it('returns no errors when all required fields are valid', () => {
    expect(validateTags(valid)).toEqual({})
  })

  describe('category', () => {
    it('flags an empty category', () => {
      expect(validateTags({ ...valid, category: '' }).category).toBeDefined()
    })

    it('flags a whitespace-only category as blank', () => {
      expect(validateTags({ ...valid, category: '   ' }).category).toBeDefined()
    })

    it('accepts a non-blank category', () => {
      expect(validateTags({ ...valid, category: 'jacket' }).category).toBeUndefined()
    })
  })

  describe('formality (1–5, optional)', () => {
    it('accepts a null formality (optional — e.g. jewelry has no formality)', () => {
      expect(validateTags({ ...valid, formality: null }).formality).toBeUndefined()
    })

    it('flags formality below 1', () => {
      expect(validateTags({ ...valid, formality: 0 }).formality).toBeDefined()
    })

    it('flags formality above 5', () => {
      expect(validateTags({ ...valid, formality: 6 }).formality).toBeDefined()
    })

    it('accepts the boundary values 1 and 5', () => {
      expect(validateTags({ ...valid, formality: 1 }).formality).toBeUndefined()
      expect(validateTags({ ...valid, formality: 5 }).formality).toBeUndefined()
    })
  })

  describe('warmth (1–3, optional)', () => {
    it('accepts a null warmth (optional — e.g. jewelry has no warmth)', () => {
      expect(validateTags({ ...valid, warmth: null }).warmth).toBeUndefined()
    })

    it('flags warmth below 1', () => {
      expect(validateTags({ ...valid, warmth: 0 }).warmth).toBeDefined()
    })

    it('flags warmth above 3', () => {
      expect(validateTags({ ...valid, warmth: 4 }).warmth).toBeDefined()
    })

    it('accepts the boundary values 1 and 3', () => {
      expect(validateTags({ ...valid, warmth: 1 }).warmth).toBeUndefined()
      expect(validateTags({ ...valid, warmth: 3 }).warmth).toBeUndefined()
    })
  })
})

describe('tagsAreValid', () => {
  it('is true only when there are no validation errors', () => {
    expect(tagsAreValid(valid)).toBe(true)
  })

  it('is true with category only — null formality/warmth are valid (jewelry saves)', () => {
    expect(tagsAreValid({ category: 'necklace', formality: null, warmth: null })).toBe(true)
  })

  it('is false when a required field is invalid', () => {
    expect(tagsAreValid({ ...valid, category: '' })).toBe(false)
    expect(tagsAreValid({ ...valid, formality: 9 })).toBe(false)
    expect(tagsAreValid({ ...valid, warmth: 9 })).toBe(false)
  })
})
