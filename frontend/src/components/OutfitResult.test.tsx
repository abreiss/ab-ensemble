import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import OutfitResult from './OutfitResult'
import type { Outfit, OutfitItem } from '../api/style'

/** Builds an enriched OutfitItem with renderable defaults (overridable). */
function outfitItem(id: string, over: Partial<OutfitItem> = {}): OutfitItem {
  return {
    itemId: id,
    photoUrl: `/api/items/${id}/photo`,
    rationale: '',
    category: null,
    primaryColor: null,
    formality: null,
    warmth: null,
    descriptors: null,
    ...over,
  }
}

const outfit: Outfit = {
  itemIds: ['a', 'b'],
  reason: 'A relaxed, warm-weather brunch look.',
  items: [
    outfitItem('a', {
      category: 'shirt',
      primaryColor: 'white',
      formality: 3,
      warmth: 2,
      descriptors: ['linen'],
      rationale: 'Breathes on a warm morning.',
    }),
    outfitItem('b', {
      category: 'loafers',
      primaryColor: 'tan',
      formality: 3,
      warmth: 1,
      descriptors: ['suede'],
      rationale: 'Warm tan lifts the cool whites.',
    }),
  ],
}

function renderResult(over: Partial<Parameters<typeof OutfitResult>[0]> = {}) {
  const onWearToday = vi.fn()
  render(
    <OutfitResult
      outfit={outfit}
      onWearToday={onWearToday}
      logStatus="idle"
      busy={false}
      {...over}
    />,
  )
  return { onWearToday }
}

describe('OutfitResult', () => {
  it('renders a numbered flat-lay tile per piece with its real photo', () => {
    renderResult()

    const tray = screen.getByTestId('flat-lay-tray')
    const photos = within(tray).getAllByRole('img')
    expect(photos).toHaveLength(2)
    expect(photos[0]).toHaveAttribute('src', '/api/items/a/photo')
    expect(within(tray).getByText('1')).toBeInTheDocument()
    expect(within(tray).getByText('2')).toBeInTheDocument()
  })

  it('renders a spec card per piece: derived name, slot, color, pips, rationale', () => {
    renderResult()

    const specs = screen.getByTestId('spec-list')
    expect(within(specs).getByText('White linen shirt')).toBeInTheDocument()
    expect(within(specs).getByText('Tan suede loafers')).toBeInTheDocument()
    expect(within(specs).getByText('TOP')).toBeInTheDocument()
    expect(within(specs).getByText('SHOES')).toBeInTheDocument()
    expect(within(specs).getByText('white')).toBeInTheDocument()
    expect(within(specs).getByText('tan')).toBeInTheDocument()
    expect(within(specs).getByText('Breathes on a warm morning.')).toBeInTheDocument()
    expect(within(specs).getByText('Warm tan lifts the cool whites.')).toBeInTheDocument()
    // One FORM row and one WARM row per piece.
    expect(within(specs).getAllByText('FORM')).toHaveLength(2)
    expect(within(specs).getAllByText('WARM')).toHaveLength(2)
  })

  it('fires the wear-today callback when the primary action is clicked', async () => {
    const user = userEvent.setup()
    const { onWearToday } = renderResult()

    await user.click(screen.getByRole('button', { name: /wear today/i }))

    expect(onWearToday).toHaveBeenCalledTimes(1)
  })

  it('locks to a disabled "Logged" state once the look is logged', () => {
    renderResult({ logStatus: 'logged' })

    expect(screen.getByRole('button', { name: /logged/i })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /wear today/i })).not.toBeInTheDocument()
  })

  it('shows a retryable message when the wear write failed', () => {
    renderResult({ logStatus: 'error' })

    expect(screen.getByText(/couldn.t log|try again/i)).toBeInTheDocument()
    // Not locked — the primary action is still offered.
    expect(screen.getByRole('button', { name: /wear today/i })).toBeInTheDocument()
  })

  it('disables the wear action while the screen is busy', () => {
    renderResult({ busy: true })

    expect(screen.getByRole('button', { name: /wear today/i })).toBeDisabled()
  })

  it('does not crash when a piece is missing its rationale (defensive)', () => {
    const bare: Outfit = {
      itemIds: ['x'],
      reason: 'r',
      // Simulate a legacy/degraded response where rationale is absent.
      items: [{ ...outfitItem('x', { category: 'shirt' }), rationale: undefined as unknown as string }],
    }
    render(<OutfitResult outfit={bare} onWearToday={vi.fn()} logStatus="idle" busy={false} />)
    expect(screen.getByTestId('spec-list')).toBeInTheDocument()
  })

  it('toggles the non-persisting save/heart control without a callback', async () => {
    const user = userEvent.setup()
    renderResult()

    const heart = screen.getByRole('button', { name: /save look/i })
    expect(heart).toHaveAttribute('aria-pressed', 'false')
    await user.click(heart)
    expect(heart).toHaveAttribute('aria-pressed', 'true')
  })
})
