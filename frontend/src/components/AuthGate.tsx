import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'

import { getToken, login } from '../api/auth'
import { onAuthRequired } from '../api/http'

interface AuthGateProps {
  children: ReactNode
}

/**
 * Renders a passcode entry screen until a valid session token is stored, then
 * renders `children`. Also subscribes to the `authedFetch` re-auth signal so any
 * `401` elsewhere in the app (an expired/invalidated token) drops back to the gate.
 */
export default function AuthGate({ children }: AuthGateProps) {
  const [authenticated, setAuthenticated] = useState(() => getToken() !== null)
  const [passcode, setPasscode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => onAuthRequired(() => setAuthenticated(false)), [])

  if (authenticated) {
    return children
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(passcode)
      setAuthenticated(true)
      setPasscode('')
    } catch {
      setError('Incorrect passcode. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-gate">
      <div className="auth-card">
        <h1 className="app-title">Ensemble</h1>
        <p className="eyebrow">Enter passcode</p>
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="field">
            <label className="field-label" htmlFor="auth-passcode">
              Passcode
            </label>
            <input
              id="auth-passcode"
              className="input"
              type="password"
              autoComplete="current-password"
              value={passcode}
              disabled={submitting}
              onChange={(event) => setPasscode(event.target.value)}
            />
            {error && <p className="field-error">{error}</p>}
          </div>
          <button
            type="submit"
            className="btn btn-primary btn-block"
            disabled={submitting || passcode.length === 0}
          >
            {submitting ? 'Checking…' : 'Unlock'}
          </button>
        </form>
      </div>
    </div>
  )
}
