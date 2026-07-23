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
    if (status === 401) return 'Invalid email or password.'
    if (status === 400) return 'Password must be at least 8 characters.'
  } else {
    if (status === 409) return 'That email is already registered.'
    if (status === 400) return 'Password must be at least 8 characters.'
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
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
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
    setPasscode('')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await signup(email, password, passcode)
      }
      setAuthenticated(true)
      setEmail('')
      setPassword('')
      setPasscode('')
    } catch (err) {
      setError(errorMessageFor(mode, err))
    } finally {
      setSubmitting(false)
    }
  }

  const isSignup = mode === 'signup'
  const canSubmit =
    !submitting && email.length > 0 && password.length > 0 && (!isSignup || passcode.length > 0)

  return (
    <div className="auth-gate">
      <div className="auth-card">
        <h1 className="app-title">Ensemble</h1>
        <p className="eyebrow">{isSignup ? 'Create your account' : 'Log in'}</p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="field">
            <label className="field-label" htmlFor="auth-email">
              Email
            </label>
            <input
              id="auth-email"
              className="input"
              type="email"
              autoComplete="email"
              value={email}
              disabled={submitting}
              onChange={(event) => setEmail(event.target.value)}
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
