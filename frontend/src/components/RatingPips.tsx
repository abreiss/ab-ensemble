/** A labelled row of rating dots — FORM (max 5) and WARM (max 3) on the spec card. */
interface RatingPipsProps {
  /** Short uppercase label rendered before the dots (e.g. `FORM`, `WARM`). */
  label: string
  /** How many dots to fill; clamped to `[0, max]`. A null/absent tag fills none. */
  value: number | null | undefined
  /** Total dots to render. */
  max: number
}

/**
 * Renders `max` dots, filling `value` of them (the rest empty), preceded by a
 * Space Mono label. Deterministic and null-safe: a missing `formality`/`warmth`
 * tag renders as all-empty rather than crashing. Driven straight from the item's
 * stored `formality` (1–5) and `warmth` (1–3).
 */
export default function RatingPips({ label, value, max }: RatingPipsProps) {
  const filled = Math.max(0, Math.min(value ?? 0, max))
  return (
    <span className="pips">
      <span className="pips-label">{label}</span>
      <span className="pips-dots" aria-hidden="true">
        {Array.from({ length: max }, (_, index) => (
          <span key={index} className={index < filled ? 'pip is-filled' : 'pip'} />
        ))}
      </span>
      <span className="sr-only">{`${filled} of ${max}`}</span>
    </span>
  )
}
