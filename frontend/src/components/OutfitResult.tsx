import { useState } from 'react'
import { Heart } from 'lucide-react'

import RatingPips from './RatingPips'
import { deriveName, slotForCategory, swatchColor } from '../lib/specSheet'
import type { Outfit } from '../api/style'

interface OutfitResultProps {
  /** The current grounded look to render (assumed to have at least one piece). */
  outfit: Outfit
  /** Logs the whole look worn (the deterministic `markWorn` fan-out lives in the route). */
  onWearToday: () => void
  /** Wear-log lifecycle, driving the primary action's lock / error affordances. */
  logStatus: 'idle' | 'logging' | 'logged' | 'error'
  /** True while a style/re-pick or a wear-log is in flight — disables the action. */
  busy: boolean
}

/**
 * The stylist's answer as a spec sheet: a numbered flat-lay tray of the real
 * photos on the left, and a per-piece spec card on the right — each with a
 * deterministically derived name, slot label, color swatch, FORM/WARM pips, and
 * the stylist's one-line rationale. The whole-look `reason` is shown by the chat
 * bubble; this component is only the look itself. Name/slot/swatch/pips are
 * derived in code (never the model); only the rationale is LLM text.
 */
export default function OutfitResult({ outfit, onWearToday, logStatus, busy }: OutfitResultProps) {
  const [saved, setSaved] = useState(false)
  const pieces = outfit.items

  return (
    <section className="outfit-result" data-testid="outfit-result" aria-label="Styled outfit">
      <div className="flat-lay">
        <p className="eyebrow">The look · Flat-lay</p>
        <ol className="flat-lay-tray" data-testid="flat-lay-tray">
          {pieces.map((piece, index) => (
            <li key={piece.itemId} className="flat-lay-tile">
              <span className="flat-lay-badge" aria-hidden="true">
                {index + 1}
              </span>
              <img
                className="flat-lay-img"
                src={piece.photoUrl}
                alt={deriveName(piece)}
                loading="lazy"
              />
            </li>
          ))}
        </ol>

        <div className="flat-lay-actions">
          {logStatus === 'logged' ? (
            <button type="button" className="btn btn-logged flat-lay-wear" disabled>
              Logged ✓
            </button>
          ) : (
            <button
              type="button"
              className="btn btn-primary flat-lay-wear"
              onClick={onWearToday}
              disabled={busy}
            >
              Wear today
            </button>
          )}
          <button
            type="button"
            className="btn heart-btn"
            aria-label="Save look"
            aria-pressed={saved}
            onClick={() => setSaved((prev) => !prev)}
          >
            <Heart size={16} aria-hidden="true" fill={saved ? 'currentColor' : 'none'} />
          </button>
        </div>

        {logStatus === 'error' && (
          <p className="banner banner-error" role="alert">
            We couldn’t log that look. Please try again.
          </p>
        )}
      </div>

      <ol className="spec-list" data-testid="spec-list">
        {pieces.map((piece, index) => (
          <li key={piece.itemId} className="spec-card">
            <div className="spec-head">
              <span className="spec-num" aria-hidden="true">
                {index + 1}
              </span>
              <span className="spec-name">{deriveName(piece)}</span>
              <span className="spec-slot">{slotForCategory(piece.category)}</span>
            </div>

            <div className="spec-attrs">
              <span className="spec-color">
                <span
                  className="spec-swatch"
                  style={{ background: swatchColor(piece.primaryColor) }}
                  aria-hidden="true"
                />
                {piece.primaryColor ?? 'unknown'}
              </span>
              <RatingPips label="FORM" value={piece.formality} max={5} />
              <RatingPips label="WARM" value={piece.warmth} max={3} />
            </div>

            {(piece.rationale ?? '').trim() !== '' && (
              <p className="spec-rationale">
                <span className="spec-bullet" aria-hidden="true" />
                {piece.rationale}
              </p>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}
