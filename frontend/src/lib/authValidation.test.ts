import { describe, expect, it } from 'vitest'

import {
  validateConfirmPassword,
  validatePasscode,
  validatePassword,
  validateUsername,
} from './authValidation'

// These rules mirror the backend `SignupRequest` constraints exactly (username
// 3–30 chars + charset regex, password min 8 chars / max 72 UTF-8 bytes,
// non-blank passcode). This is UX-only guardrail logic — the server remains the
// source of truth — so every branch is exercised, including the length and
// charset boundaries.

describe('validateUsername', () => {
  it('returns null for a valid username', () => {
    expect(validateUsername('jane_doe')).toBeNull()
  })

  it('flags a blank username as required', () => {
    expect(validateUsername('')).toBe('Username is required.')
  })

  it('flags a whitespace-only username as required', () => {
    expect(validateUsername('   ')).toBe('Username is required.')
  })

  it('flags a username shorter than 3 characters on length', () => {
    expect(validateUsername('ab')).toBe('Username must be 3–30 characters.')
  })

  it('accepts the lower length boundary of 3 characters', () => {
    expect(validateUsername('abc')).toBeNull()
  })

  it('accepts the upper length boundary of 30 characters', () => {
    expect(validateUsername('a'.repeat(30))).toBeNull()
  })

  it('flags a username longer than 30 characters on length', () => {
    expect(validateUsername('a'.repeat(31))).toBe('Username must be 3–30 characters.')
  })

  const charsetMessage =
    'Username can only use letters, numbers, and . _ - and must start and end with a letter or number.'

  it('flags an interior illegal character (space)', () => {
    expect(validateUsername('bad name')).toBe(charsetMessage)
  })

  it('flags a leading separator', () => {
    expect(validateUsername('_bad')).toBe(charsetMessage)
  })

  it('flags a trailing separator', () => {
    expect(validateUsername('bad_')).toBe(charsetMessage)
  })

  it('accepts interior . _ - separators', () => {
    expect(validateUsername('a.b_c-d')).toBeNull()
  })
})

describe('validatePassword', () => {
  it('returns null for a valid password', () => {
    expect(validatePassword('a-strong-password')).toBeNull()
  })

  it('flags an empty password as required', () => {
    expect(validatePassword('')).toBe('Password is required.')
  })

  it('flags a password shorter than 8 characters', () => {
    expect(validatePassword('short')).toBe('Password must be at least 8 characters.')
  })

  it('accepts the lower boundary of 8 characters', () => {
    expect(validatePassword('12345678')).toBeNull()
  })

  it('accepts a password of exactly 72 UTF-8 bytes', () => {
    expect(validatePassword('a'.repeat(72))).toBeNull()
  })

  it('flags a password longer than 72 UTF-8 bytes', () => {
    expect(validatePassword('a'.repeat(73))).toBe('Password is too long.')
  })

  it('measures the 72-byte limit in UTF-8 bytes, not characters', () => {
    // '😀' is 4 UTF-8 bytes but 2 UTF-16 code units — 20 of them is 80 bytes.
    expect(validatePassword('😀'.repeat(20))).toBe('Password is too long.')
  })
})

describe('validatePasscode', () => {
  it('returns null for a non-blank passcode', () => {
    expect(validatePasscode('invite-code')).toBeNull()
  })

  it('flags a blank passcode as required', () => {
    expect(validatePasscode('')).toBe('Signup code is required.')
  })

  it('flags a whitespace-only passcode as required', () => {
    expect(validatePasscode('   ')).toBe('Signup code is required.')
  })
})

describe('validateConfirmPassword', () => {
  it('returns null when the confirmation matches', () => {
    expect(validateConfirmPassword('secret123', 'secret123')).toBeNull()
  })

  it("flags a mismatch with \"Passwords don't match.\"", () => {
    expect(validateConfirmPassword('secret123', 'Secret123')).toBe("Passwords don't match.")
  })
})
