// Client-side validation for the sign-up form. These rules mirror the backend
// `SignupRequest` constraints exactly (see
// `src/main/java/com/ensemble/user/web/SignupRequest.java`): username 3–30 chars
// with the charset regex below, password min 8 chars / max 72 UTF-8 bytes
// (bcrypt's input limit), and a non-blank signup passcode. This is a UX guardrail
// that gives instant per-field feedback and avoids a round-trip to the deliberately
// generic, non-enumerating server 400 — it is NOT a security boundary. The server
// remains authoritative; if these rules ever drift from `SignupRequest.java`, the
// server still rejects and `AuthGate`'s form-level 400 backstop covers it.

/** Username length bounds (inclusive), matching `@Size(min = 3, max = 30)`. */
export const USERNAME_MIN_LENGTH = 3
export const USERNAME_MAX_LENGTH = 30

/**
 * Allowed username shape: letters, numbers, and interior `. _ -` separators, with
 * a leading/trailing letter-or-number. Mirrors the `@Pattern` on `SignupRequest`.
 */
export const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$/

/** Password minimum length (chars), matching `@Size(min = 8)`. */
export const PASSWORD_MIN_LENGTH = 8

/** Password maximum length in UTF-8 bytes, matching `@MaxUtf8Bytes(72)` (bcrypt limit). */
export const PASSWORD_MAX_BYTES = 72

/**
 * Returns a user-facing message for an invalid username, or `null` when valid.
 * Order matters: blank first, then length, then charset — so the clearest message wins.
 */
export function validateUsername(value: string): string | null {
  if (value.trim() === '') {
    return 'Username is required.'
  }
  if (value.length < USERNAME_MIN_LENGTH || value.length > USERNAME_MAX_LENGTH) {
    return `Username must be ${USERNAME_MIN_LENGTH}–${USERNAME_MAX_LENGTH} characters.`
  }
  if (!USERNAME_PATTERN.test(value)) {
    return 'Username can only use letters, numbers, and . _ - and must start and end with a letter or number.'
  }
  return null
}

/**
 * Returns a user-facing message for an invalid password, or `null` when valid.
 * The max is enforced in UTF-8 bytes (bcrypt's limit), not characters.
 */
export function validatePassword(value: string): string | null {
  if (value === '') {
    return 'Password is required.'
  }
  if (value.length < PASSWORD_MIN_LENGTH) {
    return `Password must be at least ${PASSWORD_MIN_LENGTH} characters.`
  }
  if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
    return 'Password is too long.'
  }
  return null
}

/** Returns a message when the signup passcode is blank, or `null` when present. */
export function validatePasscode(value: string): string | null {
  if (value.trim() === '') {
    return 'Signup code is required.'
  }
  return null
}

/** Returns a message when the confirmation does not equal the password, or `null` when it matches. */
export function validateConfirmPassword(password: string, confirm: string): string | null {
  if (password !== confirm) {
    return "Passwords don't match."
  }
  return null
}
