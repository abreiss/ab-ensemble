import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { getToken, login, signup } from '../api/auth'
import { onAuthRequired } from '../api/http'
import {
  validateConfirmPassword,
  validatePasscode,
  validatePassword,
  validateUsername,
} from '../lib/authValidation'

interface AuthGateProps {
  children: ReactNode
}

type Mode = 'login' | 'signup'

/** Reads a numeric `status` off a thrown value, if one is present (see `api/auth.ts`'s `HttpError`). */
function statusOf(err: unknown): number | undefined {
  if (err && typeof err === 'object' && 'status' in err) {
    const { status } = err as { status: unknown }
    if (typeof status === 'number') {
      return status
    }
  }
  return undefined
}

/** Maps a failed login/signup attempt's HTTP status to user-facing copy. */
function errorMessageFor(mode: Mode, err: unknown): string {
  const status = statusOf(err)
  if (mode === 'login') {
    if (status === 401) return 'Invalid username or password.'
    if (status === 400) return 'Enter a valid username and password.'
  } else {
    if (status === 409) return 'That username is already registered.'
    // Client-side validation (see `lib/authValidation`) now catches bad usernames,
    // short/long passwords, and a blank invite code before submit, so a signup 400
    // is a rare client/server rule-drift backstop; HttpError only carries the status,
    // so use a generic message (mirrors the login-400 copy) rather than blaming a field.
    if (status === 400) return 'Check your username, password, and signup code.'
    if (status === 401) return "That signup code isn't valid."
  }
  return 'Something went wrong. Try again.'
}

/**
 * Renders a login / sign-up screen until a valid session token is stored, then renders
 * `children`. Also subscribes to the `authedFetch` re-auth signal so any `401` elsewhere
 * in the app (an expired/invalidated token) drops back to the gate.
 */
export default function AuthGate({ children }: AuthGateProps) {
  const [authenticated, setAuthenticated] = useState(() => getToken() !== null)
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [usernameTouched, setUsernameTouched] = useState(false)
  const [password, setPassword] = useState('')
  const [passwordTouched, setPasswordTouched] = useState(false)
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmTouched, setConfirmTouched] = useState(false)
  const [passcode, setPasscode] = useState('')
  const [passcodeTouched, setPasscodeTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => onAuthRequired(() => setAuthenticated(false)), [])

  if (authenticated) {
    return children
  }

  function toggleMode() {
    setMode((current) => (current === 'login' ? 'signup' : 'login'))
    setError(null)
    setUsernameTouched(false)
    setPassword('')
    setPasswordTouched(false)
    setConfirmPassword('')
    setConfirmTouched(false)
    setPasscode('')
    setPasscodeTouched(false)
  }

  // Per-field validation runs only in signup mode (login has no such rules); each
  // helper returns a user-facing message or null. These feed both the inline field
  // errors and the submit-time guard below. Login mode leaves every error null.
  const isSignup = mode === 'signup'
  const usernameError = isSignup ? validateUsername(username) : null
  const passwordError = isSignup ? validatePassword(password) : null
  const confirmError = isSignup ? validateConfirmPassword(password, confirmPassword) : null
  const passcodeError = isSignup ? validatePasscode(passcode) : null

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Guard all signup field rules here too: pressing Enter submits the form even
    // while the button is disabled, so the button gate alone is not enough. Mark
    // every field touched (surfacing its inline error) and block the request if any
    // field is invalid, so a bad value never round-trips to the generic server 400.
    if (isSignup) {
      setUsernameTouched(true)
      setPasswordTouched(true)
      setConfirmTouched(true)
      setPasscodeTouched(true)
      if (usernameError || passwordError || confirmError || passcodeError) {
        return
      }
    }
    setSubmitting(true)
    setError(null)
    try {
      if (mode === 'login') {
        await login(username, password)
      } else {
        await signup(username, password, passcode)
      }
      setAuthenticated(true)
      setUsername('')
      setPassword('')
      setConfirmPassword('')
      setPasscode('')
    } catch (err) {
      setError(errorMessageFor(mode, err))
    } finally {
      setSubmitting(false)
    }
  }

  const canSubmit =
    !submitting &&
    username.length > 0 &&
    password.length > 0 &&
    (!isSignup || (passcode.length > 0 && confirmError === null))

  return (
    <div className="auth-gate">
      <div className="auth-card">
        <h1 className="app-title">Ensemble</h1>
        <p className="eyebrow">{isSignup ? 'Create your account' : 'Log in'}</p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="field">
            <label className="field-label" htmlFor="auth-username">
              Username
            </label>
            <input
              id="auth-username"
              className="input"
              type="text"
              autoComplete="username"
              value={username}
              disabled={submitting}
              onChange={(event) => setUsername(event.target.value)}
              onBlur={() => setUsernameTouched(true)}
              aria-invalid={usernameTouched && usernameError ? true : undefined}
            />
            {usernameTouched && usernameError && (
              <span className="field-error">{usernameError}</span>
            )}
          </div>
          <div className="field">
            <label className="field-label" htmlFor="auth-password">
              Password
            </label>
            <input
              id="auth-password"
              className="input"
              type="password"
              autoComplete={isSignup ? 'new-password' : 'current-password'}
              value={password}
              disabled={submitting}
              onChange={(event) => setPassword(event.target.value)}
              onBlur={() => setPasswordTouched(true)}
              aria-invalid={passwordTouched && passwordError ? true : undefined}
            />
            {passwordTouched && passwordError && (
              <span className="field-error">{passwordError}</span>
            )}
          </div>
          {isSignup && (
            <div className="field">
              <label className="field-label" htmlFor="auth-confirm-password">
                Confirm password
              </label>
              <input
                id="auth-confirm-password"
                className="input"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                disabled={submitting}
                onChange={(event) => setConfirmPassword(event.target.value)}
                onBlur={() => setConfirmTouched(true)}
                aria-invalid={confirmTouched && confirmError ? true : undefined}
              />
              {confirmTouched && confirmError && (
                <span className="field-error">{confirmError}</span>
              )}
            </div>
          )}
          {isSignup && (
            <div className="field">
              <label className="field-label" htmlFor="auth-passcode">
                Signup code
              </label>
              <input
                id="auth-passcode"
                className="input"
                type="password"
                autoComplete="off"
                value={passcode}
                disabled={submitting}
                onChange={(event) => setPasscode(event.target.value)}
                onBlur={() => setPasscodeTouched(true)}
                aria-invalid={passcodeTouched && passcodeError ? true : undefined}
              />
              {passcodeTouched && passcodeError && (
                <span className="field-error">{passcodeError}</span>
              )}
            </div>
          )}
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={!canSubmit}>
            {submitting ? 'Please wait…' : isSignup ? 'Sign up' : 'Log in'}
          </button>
        </form>
        <button
          type="button"
          className="auth-toggle"
          onClick={toggleMode}
          disabled={submitting}
        >
          {isSignup ? 'Log in' : 'Sign up'}
        </button>
      </div>
    </div>
  )
}
