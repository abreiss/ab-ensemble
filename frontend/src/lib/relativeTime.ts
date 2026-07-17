// A short, human relative label for a wear-history instant ("today",
// "2 days ago", "1 month ago"). Kept deliberately coarse — this is a quiet
// metadata line, not a precise duration. Day math is done in UTC so the label
// is stable regardless of the viewer's timezone, matching how the backend
// stores `lastWorn`.

const MS_PER_DAY = 86_400_000

/** UTC calendar-day epoch for a Date (time-of-day zeroed). */
function utcDay(date: Date): number {
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
}

/**
 * Renders `iso` as a short relative label against `now` (defaults to the current
 * time). An absent instant (`null`/`undefined`) — an item that has never been
 * worn — yields `"not yet worn"`.
 */
export function relativeTime(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) {
    return 'not yet worn'
  }

  const days = Math.round((utcDay(now) - utcDay(new Date(iso))) / MS_PER_DAY)

  if (days <= 0) {
    return 'today'
  }
  if (days < 7) {
    return days === 1 ? '1 day ago' : `${days} days ago`
  }

  const weeks = Math.floor(days / 7)
  if (weeks < 5) {
    return weeks === 1 ? '1 week ago' : `${weeks} weeks ago`
  }

  const months = Math.floor(days / 30)
  if (months < 12) {
    return months === 1 ? '1 month ago' : `${months} months ago`
  }

  const years = Math.floor(days / 365)
  return years === 1 ? '1 year ago' : `${years} years ago`
}
