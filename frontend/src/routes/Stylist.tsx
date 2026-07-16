import { type FormEvent, useCallback, useState } from 'react'
import { Link } from 'react-router-dom'

import { markWorn, photoUrl, requestStyle } from '../api/style'
import type { Outfit, StyleTurn } from '../api/style'

type Status = 'idle' | 'loading' | 'ready' | 'error'
type LogStatus = 'idle' | 'logging' | 'logged' | 'error'

/** The canned user turn a bare "Show me another" regenerate contributes to the thread. */
const REGENERATE_TEXT = 'Show me another look'

/** One-line summary of a rendered pick, committed to the thread as an assistant turn. */
function summarize(outfit: Outfit): string {
  return `Previously chose: ${outfit.itemIds.join(', ')} — ${outfit.reason}`
}

/**
 * Stylist screen (`/style`): the app's second AI job, now a multi-turn loop. A free-text
 * vibe submits to `POST /api/style`; the grounded result renders as an outfit card — the
 * picked garments as their real stored photos, the stylist's reason as a hang-tag "note".
 *
 * Re-pick is stateless on the server: the client accumulates the conversation `history`
 * (the vibe + an assistant summary of each pick + the user's feedback) and resends it each
 * turn. Pushback ("too plain") and "Show me another" both replay the full thread so the
 * model produces a *different* look. Handles the real edge states — loading, request
 * failure (retry replays the same turn), and a too-small wardrobe (a normal 200 with an
 * empty look) — across every re-pick without crashing. The LLM never sees images.
 */
export default function Stylist() {
  const [vibe, setVibe] = useState('')
  const [pushback, setPushback] = useState('')
  const [outfit, setOutfit] = useState<Outfit | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [logStatus, setLogStatus] = useState<LogStatus>('idle')
  // The accumulated conversation resent on every re-pick (server stays stateless).
  const [history, setHistory] = useState<StyleTurn[]>([])
  // The turn currently in flight / last attempted, so a retry can replay it verbatim.
  const [pending, setPending] = useState<{ text: string; base: StyleTurn[] }>({
    text: '',
    base: [],
  })

  // Run one styling turn: send the newest user text plus the thread `base` that precedes
  // it. On success commit the turn (newest user text + a summary of the pick) so the next
  // re-pick carries it. `base` is captured for retry via `pending`.
  const run = useCallback((newestUserText: string, base: StyleTurn[]) => {
    setPending({ text: newestUserText, base })
    setStatus('loading')
    // A fresh look resets the wear-log lock so it can be logged on its own.
    setLogStatus('idle')
    requestStyle(newestUserText, base)
      .then((result) => {
        setOutfit(result)
        setStatus('ready')
        // Only a look with pieces becomes a thread turn; an empty-wardrobe reply does not.
        if (result.itemIds.length > 0) {
          setHistory([
            ...base,
            { role: 'user', text: newestUserText },
            { role: 'assistant', text: summarize(result) },
          ])
        }
      })
      .catch(() => setStatus('error'))
  }, [])

  // Log the whole look worn: mark every rendered piece via the deterministic wear-history
  // write. One log per look — on success the control locks to "Logged ✓"; a partial
  // failure keeps the look and offers a retry.
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

  // A brand-new vibe from the top form starts a fresh thread (empty base).
  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const prompt = vibe.trim()
    if (prompt !== '') {
      run(prompt, [])
    }
  }

  // Pushback: send the free-text feedback with the full accumulated thread.
  const onPushback = (event: FormEvent) => {
    event.preventDefault()
    const feedback = pushback.trim()
    if (feedback !== '' && status !== 'loading') {
      setPushback('')
      run(feedback, history)
    }
  }

  // Regenerate: a bare "show me another" turn with the full thread.
  const regenerate = () => {
    if (status !== 'loading') {
      run(REGENERATE_TEXT, history)
    }
  }

  // Retry replays the exact turn that failed — same newest text, same thread base.
  const retry = () => run(pending.text, pending.base)

  const hasLook = outfit !== null && outfit.itemIds.length > 0
  const loading = status === 'loading'
  // A style/re-pick request or a wear-log fan-out is in flight. All action controls
  // gate on this so the two never overlap — otherwise a wear-log settling mid-re-pick
  // would land its "Logged ✓" (or error) on the freshly re-picked look.
  const busy = loading || logStatus === 'logging'
  // Keep the current look on screen while a re-pick is in flight, controls disabled.
  const showCard = hasLook && outfit !== null && (status === 'ready' || loading)

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
          disabled={busy || vibe.trim() === ''}
        >
          Style me
        </button>
      </form>

      {loading && <p className="state-note">Styling your look…</p>}

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

      {showCard && outfit !== null && (
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
                disabled={busy}
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

          <form className="repick" onSubmit={onPushback}>
            <label className="field-label" htmlFor="pushback">
              Not quite right?
            </label>
            <div className="repick-row">
              <input
                id="pushback"
                className="input"
                type="text"
                value={pushback}
                onChange={(event) => setPushback(event.target.value)}
                placeholder="too plain — add a jacket"
                autoComplete="off"
                disabled={busy}
              />
              <button type="submit" className="btn" disabled={busy || pushback.trim() === ''}>
                Re-pick
              </button>
            </div>
            <button
              type="button"
              className="btn btn-ghost btn-block"
              onClick={regenerate}
              disabled={busy}
            >
              Show me another
            </button>
          </form>
        </article>
      )}
    </section>
  )
}
