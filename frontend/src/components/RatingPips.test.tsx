import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import RatingPips from './RatingPips'

/** Counts total vs filled pip dots in the rendered output. */
function counts(container: HTMLElement) {
  return {
    total: container.querySelectorAll('.pip').length,
    filled: container.querySelectorAll('.pip.is-filled').length,
  }
}

describe('RatingPips', () => {
  it('renders `max` dots and fills `value` of them (FORM = 5)', () => {
    const { container } = render(<RatingPips label="FORM" value={3} max={5} />)
    expect(counts(container)).toEqual({ total: 5, filled: 3 })
  })

  it('supports the WARM scale (max = 3)', () => {
    const { container } = render(<RatingPips label="WARM" value={2} max={3} />)
    expect(counts(container)).toEqual({ total: 3, filled: 2 })
  })

  it('clamps a value above max to all filled', () => {
    const { container } = render(<RatingPips label="FORM" value={9} max={5} />)
    expect(counts(container)).toEqual({ total: 5, filled: 5 })
  })

  it('renders zero filled for a null value', () => {
    const { container } = render(<RatingPips label="WARM" value={null} max={3} />)
    expect(counts(container)).toEqual({ total: 3, filled: 0 })
  })

  it('renders zero filled for a negative value', () => {
    const { container } = render(<RatingPips label="FORM" value={-2} max={5} />)
    expect(counts(container)).toEqual({ total: 5, filled: 0 })
  })

  it('renders the label text', () => {
    const { getByText } = render(<RatingPips label="FORM" value={1} max={5} />)
    expect(getByText('FORM')).toBeInTheDocument()
  })
})
