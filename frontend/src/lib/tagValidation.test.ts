import { describe, expect, it } from 'vitest'

import { tagsAreValid, validateTags } from './tagValidation'

// The three required fields mirror the backend `TagRequest` constraints exactly:
// non-blank `category`, `formality` in 1–5, `warmth` in 1–3. This is critical
// guardrail logic, so every branch is exercised.

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

  describe('formality (1–5)', () => {
    it('flags a missing formality', () => {
      expect(validateTags({ ...valid, formality: null }).formality).toBeDefined()
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

  describe('warmth (1–3)', () => {
    it('flags a missing warmth', () => {
      expect(validateTags({ ...valid, warmth: null }).warmth).toBeDefined()
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

  it('is false when any required field is invalid', () => {
    expect(tagsAreValid({ ...valid, category: '' })).toBe(false)
    expect(tagsAreValid({ ...valid, formality: null })).toBe(false)
    expect(tagsAreValid({ ...valid, warmth: 9 })).toBe(false)
  })
})
