import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { getToken, login, signup } from '../api/auth'
import { onAuthRequired } from '../api/http'

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
    // A signup 400 can come from a bad username, a too-short password, or a blank
    // invite code; HttpError only carries the status, so use a generic message
    // (mirrors the login-400 copy) rather than blaming one field.
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
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [confirmTouched, setConfirmTouched] = useState(false)
  const [passcode, setPasscode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => onAuthRequired(() => setAuthenticated(false)), [])

  if (authenticated) {
    return children
  }

  function toggleMode() {
    setMode((current) => (current === 'login' ? 'signup' : 'login'))
    setError(null)
    setPassword('')
    setConfirmPassword('')
    setConfirmTouched(false)
    setPasscode('')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Guard the confirm-password match here too: pressing Enter submits the form
    // even while the button is disabled, so the button gate alone is not enough.
    if (mode === 'signup' && password !== confirmPassword) {
      setConfirmTouched(true)
      return
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

  const isSignup = mode === 'signup'
  const confirmMismatch = isSignup && confirmPassword !== password
  const canSubmit =
    !submitting &&
    username.length > 0 &&
    password.length > 0 &&
    (!isSignup || (passcode.length > 0 && !confirmMismatch))

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
            />
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
            />
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
                aria-invalid={confirmTouched && confirmMismatch ? true : undefined}
              />
              {confirmTouched && confirmMismatch && (
                <span className="field-error">Passwords don&apos;t match.</span>
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
              />
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
