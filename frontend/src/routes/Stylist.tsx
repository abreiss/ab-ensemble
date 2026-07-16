import { type FormEvent, useCallback, useState } from 'react'
import { Link } from 'react-router-dom'

import { markWorn, photoUrl, requestStyle } from '../api/style'
import type { Outfit } from '../api/style'

type Status = 'idle' | 'loading' | 'ready' | 'error'
type LogStatus = 'idle' | 'logging' | 'logged' | 'error'

/**
 * Stylist screen (`/style`): the app's second AI job. A free-text vibe submits to
 * `POST /api/style`; the grounded result renders as an outfit card — the picked
 * garments as their real stored photos, the stylist's reason as a hang-tag "note".
 * Handles the real edge states — loading, request-failure (with retry that re-runs
 * the same vibe), and a too-small wardrobe (a normal 200 with an empty look) —
 * without crashing. Reuses the "Care Label" tokens; the LLM never sees images.
 */
export default function Stylist() {
  const [vibe, setVibe] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [outfit, setOutfit] = useState<Outfit | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [logStatus, setLogStatus] = useState<LogStatus>('idle')

  // Settle a style request into state via promise callbacks (the pattern the
  // react-hooks effect rule endorses for syncing with an external system).
  const run = useCallback((prompt: string) => {
    setSubmitted(prompt)
    setStatus('loading')
    // A fresh look resets the wear-log lock so it can be logged on its own.
    setLogStatus('idle')
    requestStyle(prompt)
      .then((result) => {
        setOutfit(result)
        setStatus('ready')
      })
      .catch(() => setStatus('error'))
  }, [])

  // Log the whole look worn: mark every rendered piece via the deterministic
  // wear-history write. One log per look — on success the control locks to
  // "Logged ✓"; a partial failure keeps the look and offers a retry.
  const logLook = useCallback(() => {
    if (outfit === null) {
      return
    }
    setLogStatus('logging')
    Promise.allSettled(outfit.items.map((piece) => markWorn(piece.itemId))).then((results) => {
      const anyFailed = results.some((result) => result.status === 'rejected')
      setLogStatus(anyFailed ? 'error' : 'logged')
    })
  }, [outfit])

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const prompt = vibe.trim()
    if (prompt !== '') {
      run(prompt)
    }
  }

  // Retry re-runs the last submitted vibe — the user need not re-type it.
  const retry = () => run(submitted)

  const hasLook = outfit !== null && outfit.itemIds.length > 0

  return (
    <section data-testid="stylist" className="screen">
      <p className="eyebrow">Stylist</p>
      <h1 className="screen-title">What’s the vibe?</h1>

      <form className="vibe-form" onSubmit={onSubmit}>
        <div className="field">
          <label className="field-label" htmlFor="vibe">
            Vibe
          </label>
          <input
            id="vibe"
            className="input"
            type="text"
            value={vibe}
            onChange={(event) => setVibe(event.target.value)}
            placeholder="streetwear today"
            autoComplete="off"
          />
        </div>
        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={status === 'loading' || vibe.trim() === ''}
        >
          Style me
        </button>
      </form>

      {status === 'loading' && <p className="state-note">Styling your look…</p>}

      {status === 'error' && (
        <div className="state-block">
          <p className="banner banner-error">We couldn’t style that vibe.</p>
          <button type="button" className="btn" onClick={retry}>
            Try again
          </button>
        </div>
      )}

      {status === 'ready' && outfit !== null && !hasLook && (
        <div className="state-block empty-state">
          <h2 className="empty-title">Not enough to style yet</h2>
          <p className="state-note">{outfit.reason}</p>
          <Link to="/add" className="btn btn-primary">
            + Add an item
          </Link>
        </div>
      )}

      {status === 'ready' && hasLook && outfit !== null && (
        <article className="outfit-card" aria-label="Styled outfit">
          <header className="outfit-head">
            <span className="eyebrow">The look</span>
            <span className="outfit-count">
              {outfit.items.length} {outfit.items.length === 1 ? 'piece' : 'pieces'}
            </span>
          </header>
          <ul className="outfit-pieces">
            {outfit.items.map((piece) => (
              <li key={piece.itemId} className="outfit-piece">
                <img
                  className="thumb-img"
                  src={photoUrl(piece.itemId)}
                  alt="Garment in the styled look"
                  loading="lazy"
                />
              </li>
            ))}
          </ul>
          <div className="outfit-note">
            <span className="eyebrow">Stylist’s note</span>
            <p className="outfit-reason">{outfit.reason}</p>
          </div>

          <div className="outfit-log">
            {logStatus === 'logged' ? (
              <button type="button" className="btn btn-logged btn-block" disabled>
                Logged ✓
              </button>
            ) : (
              <button
                type="button"
                className="btn btn-primary btn-block"
                onClick={logLook}
                disabled={logStatus === 'logging'}
              >
                I wore this look
              </button>
            )}
            {logStatus === 'error' && (
              <p className="banner banner-error" role="alert">
                We couldn’t log that look. Please try again.
              </p>
            )}
          </div>
        </article>
      )}
    </section>
  )
}
