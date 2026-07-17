import { type FormEvent, useCallback, useState } from 'react'
import { ArrowUp } from 'lucide-react'

import OutfitResult from '../components/OutfitResult'
import WardrobeDrawer from '../components/WardrobeDrawer'
import { markWorn, requestStyle } from '../api/style'
import type { Outfit, StyleTurn } from '../api/style'

type Status = 'idle' | 'loading' | 'ready' | 'error'
type LogStatus = 'idle' | 'logging' | 'logged' | 'error'

/** One turn rendered in the chat stream (display only; the backend thread is `history`). */
interface Message {
  role: 'user' | 'assistant'
  text: string
}

/** The canned user turn a bare "Show me another" regenerate contributes to the thread. */
const REGENERATE_TEXT = 'Show me another look'

/** Quick-start chips under the intro bubble; each sends its text as a first vibe. */
const QUICK_START = ['Brunch', 'Interview', 'Date night', 'What goes with these loafers?']

/** Adjust chips shown once a look renders; each sends its text as pushback. */
const ADJUST = ['Dressier ↑', 'Warmer', 'Swap #1', 'More color']

/** One-line summary of a rendered pick, committed to the thread as an assistant turn. */
function summarize(outfit: Outfit): string {
  return `Previously chose: ${outfit.itemIds.join(', ')} — ${outfit.reason}`
}

/**
 * Stylist screen — the app's landing route (`/`) and its second AI job, as a
 * conversational spec sheet. A vibe (typed in the composer, or tapped from a
 * quick-start / adjust chip) submits to `POST /api/style`; the grounded result
 * renders as an {@link OutfitResult} — a numbered flat-lay of the real photos
 * beside a per-piece spec card (name / slot / swatch / FORM+WARM pips /
 * rationale). The exchange is shown as a chat message stream.
 *
 * Re-pick is stateless on the server: the client accumulates the conversation
 * `history` (the vibe + an assistant summary of each pick + feedback) and resends
 * it every turn, so the model produces a *different* look. The real edge states
 * are preserved across every re-pick — a "thinking" state, request failure (retry
 * replays the same turn), and a too-small wardrobe (a normal 200 with an empty
 * look). The LLM never sees images; name/slot/swatch/pips are derived from stored
 * tags in code. The wardrobe drawer highlights the pieces in the current look.
 */
export default function Stylist() {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
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
  // The wardrobe drawer is always visible on desktop; on narrow viewports it
  // collapses behind this toggle.
  const [drawerOpen, setDrawerOpen] = useState(false)

  // Run one styling turn: send the newest user text plus the thread `base` that
  // precedes it. On success append the assistant reply and, for a non-empty look,
  // commit the turn to `history` so the next re-pick carries it. `base` is captured
  // for retry via `pending`. This never appends the user turn — the caller does, so
  // a retry can replay without duplicating the user's message.
  const run = useCallback((newestUserText: string, base: StyleTurn[]) => {
    setPending({ text: newestUserText, base })
    setStatus('loading')
    // A fresh look resets the wear-log lock so it can be logged on its own.
    setLogStatus('idle')
    requestStyle(newestUserText, base)
      .then((result) => {
        setOutfit(result)
        setStatus('ready')
        setMessages((prev) => [...prev, { role: 'assistant', text: result.reason }])
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

  // Send a user turn: append the user bubble, then run the request with the current
  // accumulated thread as the base. Unifies the first vibe and every re-pick.
  const send = (text: string) => {
    const clean = text.trim()
    if (clean === '') {
      return
    }
    setMessages((prev) => [...prev, { role: 'user', text: clean }])
    run(clean, history)
  }

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

  const onComposerSubmit = (event: FormEvent) => {
    event.preventDefault()
    send(draft)
    setDraft('')
  }

  // Retry replays the exact turn that failed — same newest text, same thread base —
  // without re-appending the user bubble (it is already in the stream).
  const retry = () => run(pending.text, pending.base)

  const loading = status === 'loading'
  // A style/re-pick request or a wear-log fan-out is in flight. All action controls
  // gate on this so the two never overlap — otherwise a wear-log settling mid-re-pick
  // would land its "Logged ✓" (or error) on the freshly re-picked look.
  const busy = loading || logStatus === 'logging'
  const hasLook = outfit !== null && outfit.itemIds.length > 0

  return (
    <div className="stylist-layout">
      <button
        type="button"
        className="drawer-toggle"
        aria-expanded={drawerOpen}
        aria-controls="wardrobe-drawer"
        onClick={() => setDrawerOpen((open) => !open)}
      >
        {drawerOpen ? 'Hide wardrobe' : 'Your wardrobe'}
      </button>

      <aside
        id="wardrobe-drawer"
        className={`wardrobe-drawer${drawerOpen ? ' is-open' : ''}`}
        aria-label="Your wardrobe"
      >
        <WardrobeDrawer inLookIds={hasLook && outfit !== null ? outfit.itemIds : []} />
      </aside>

      <section data-testid="stylist" className="screen stylist-main">
        <div className="chat-stream">
          <div className="chat-turn is-assistant">
            <span className="chat-avatar" aria-hidden="true">
              E
            </span>
            <p className="chat-bubble">
              Tell me the occasion and I’ll pull a look from your closet.
            </p>
          </div>

          {messages.length === 0 && (
            <div className="chips chips-quick-start">
              {QUICK_START.map((chip) => (
                <button
                  key={chip}
                  type="button"
                  className="chip"
                  onClick={() => send(chip)}
                  disabled={busy}
                >
                  {chip}
                </button>
              ))}
            </div>
          )}

          {messages.map((message, index) =>
            message.role === 'user' ? (
              <div key={index} className="chat-turn is-user">
                <p className="chat-bubble">{message.text}</p>
              </div>
            ) : (
              <div key={index} className="chat-turn is-assistant">
                <span className="chat-avatar" aria-hidden="true">
                  E
                </span>
                <p className="chat-bubble">{message.text}</p>
              </div>
            ),
          )}

          {loading && (
            <div className="chat-turn is-assistant">
              <span className="chat-avatar" aria-hidden="true">
                E
              </span>
              <p className="chat-bubble chat-thinking" role="status">
                Styling your look…
              </p>
            </div>
          )}

          {status === 'error' && (
            <div className="state-block chat-error">
              <p className="banner banner-error">We couldn’t style that vibe.</p>
              <button type="button" className="btn" onClick={retry}>
                Try again
              </button>
            </div>
          )}
        </div>

        {hasLook && outfit !== null && (
          <OutfitResult
            outfit={outfit}
            onWearToday={logLook}
            logStatus={logStatus}
            busy={busy}
          />
        )}

        {hasLook && (
          <div className="chips chips-adjust">
            <span className="eyebrow">Adjust</span>
            {ADJUST.map((chip) => (
              <button
                key={chip}
                type="button"
                className="chip"
                onClick={() => send(chip)}
                disabled={busy}
              >
                {chip}
              </button>
            ))}
            <button
              type="button"
              className="chip chip-ghost"
              onClick={() => send(REGENERATE_TEXT)}
              disabled={busy}
            >
              Show me another
            </button>
          </div>
        )}

        <form className="composer" onSubmit={onComposerSubmit}>
          <input
            className="composer-input"
            type="text"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Ask for a change, or a whole new look…"
            aria-label="Message the stylist"
            autoComplete="off"
            disabled={busy}
          />
          <button
            type="submit"
            className="composer-send"
            aria-label="Send"
            disabled={busy || draft.trim() === ''}
          >
            <ArrowUp size={18} aria-hidden="true" />
          </button>
        </form>
      </section>
    </div>
  )
}
