import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import AssembleSource from './AssembleSource'
import type { Item } from '../types/item'

function item(id: string, overrides: Partial<Item> = {}): Item {
  return {
    itemId: id,
    category: 'shirt',
    primaryColor: 'navy',
    secondaryColor: null,
    formality: 3,
    pattern: null,
    warmth: 2,
    descriptors: null,
    photoUrl: `/api/items/${id}/photo`,
    createdAt: '2026-01-01T00:00:00Z',
    lastWorn: null,
    wornCount: 0,
    ...overrides,
  }
}

describe('AssembleSource', () => {
  it('renders every item as a drawer tile when nothing is placed', () => {
    render(<AssembleSource items={[item('a'), item('b')]} />)

    const grid = document.querySelector('.drawer-grid')
    expect(grid).not.toBeNull()
    expect(grid).toHaveClass('drawer-grid')
    // dnd-kit's draggable `attributes` set `role="button"` on each tile (see
    // the assertion below), which intentionally overrides the `<li>`'s
    // implicit `listitem` role — so tiles are counted by class, not role.
    expect(grid?.querySelectorAll('.drawer-tile')).toHaveLength(2)
  })

  it('omits already-placed item ids from the visible source list', () => {
    render(<AssembleSource items={[item('a'), item('b'), item('c')]} placedIds={['b']} />)

    expect(document.querySelector('[data-item-id="a"]')).not.toBeNull()
    expect(document.querySelector('[data-item-id="b"]')).toBeNull()
    expect(document.querySelector('[data-item-id="c"]')).not.toBeNull()
  })

  it('narrows the visible tiles to the search text, reusing the drawer search pattern', async () => {
    const user = userEvent.setup()
    render(
      <AssembleSource
        items={[
          item('a', { category: 'shirt', primaryColor: 'navy' }),
          item('b', { category: 'jeans', primaryColor: 'blue' }),
        ]}
      />,
    )

    const input = screen.getByRole('searchbox', { name: /search pieces/i })
    expect(input.closest('.drawer-search')).not.toBeNull()

    await user.type(input, 'jeans')

    expect(document.querySelector('[data-item-id="a"]')).toBeNull()
    expect(document.querySelector('[data-item-id="b"]')).not.toBeNull()
  })

  it('renders tiles with the drawer-tile class and a dnd-kit draggable attribute', () => {
    render(<AssembleSource items={[item('a')]} />)

    const tile = document.querySelector('[data-item-id="a"]')
    expect(tile).toHaveClass('drawer-tile')
    expect(tile).toHaveAttribute('aria-roledescription', 'draggable')
  })
})
