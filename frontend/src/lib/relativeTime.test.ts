import { describe, expect, it } from 'vitest'

import { relativeTime } from './relativeTime'

// A fixed "now" keeps every span deterministic regardless of when/where the
// suite runs. All instants use explicit UTC (`Z`) so the day math never depends
// on the runner's timezone.
const NOW = new Date('2026-07-16T18:00:00Z')

describe('relativeTime', () => {
  it('returns "today" for an instant earlier the same day', () => {
    expect(relativeTime('2026-07-16T02:00:00Z', NOW)).toBe('today')
  })

  it('returns "1 day ago" for the previous day', () => {
    expect(relativeTime('2026-07-15T20:00:00Z', NOW)).toBe('1 day ago')
  })

  it('returns "2 days ago" for two calendar days back', () => {
    expect(relativeTime('2026-07-14T00:00:00Z', NOW)).toBe('2 days ago')
  })

  it('rolls up to weeks for a ~two-week span', () => {
    expect(relativeTime('2026-07-02T12:00:00Z', NOW)).toBe('2 weeks ago')
  })

  it('rolls up to months for a longer span', () => {
    expect(relativeTime('2026-06-01T12:00:00Z', NOW)).toBe('1 month ago')
  })

  it('returns "not yet worn" when the instant is absent', () => {
    expect(relativeTime(null, NOW)).toBe('not yet worn')
    expect(relativeTime(undefined, NOW)).toBe('not yet worn')
  })
})
