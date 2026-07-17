import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WardrobeDrawer from './WardrobeDrawer'
import type { Item } from '../types/item'

vi.mock('../api/items', () => ({
  listItems: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { listItems } from '../api/items'

const listItemsMock = vi.mocked(listItems)

/** Builds a wardrobe item with renderable defaults (overridable). */
function item(id: string, over: Partial<Item> = {}): Item {
  return {
    itemId: id,
    category: 'shirt',
    primaryColor: 'white',
    secondaryColor: null,
    formality: 3,
    pattern: null,
    warmth: 2,
    descriptors: ['linen'],
    photoUrl: `/api/items/${id}/photo`,
    createdAt: '2026-01-01T00:00:00Z',
    lastWorn: null,
    wornCount: 0,
    ...over,
  }
}

const items: Item[] = [
  item('a', { category: 'shirt', primaryColor: 'white', descriptors: ['linen'] }),
  item('b', { category: 'jeans', primaryColor: 'indigo', descriptors: ['slim'] }),
]

beforeEach(() => {
  listItemsMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('WardrobeDrawer', () => {
  it('lists every wardrobe item as a thumbnail from listItems()', async () => {
    listItemsMock.mockResolvedValue(items)

    render(<WardrobeDrawer />)

    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(2))
    expect(screen.getByRole('img', { name: /shirt/i })).toHaveAttribute(
      'src',
      '/api/items/a/photo',
    )
  })

  it('outlines the tiles that are in the current look', async () => {
    listItemsMock.mockResolvedValue(items)

    const { container } = render(<WardrobeDrawer inLookIds={['a']} />)

    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(2))
    expect(container.querySelector('[data-item-id="a"]')).toHaveClass('is-in-look')
    expect(container.querySelector('[data-item-id="b"]')).not.toHaveClass('is-in-look')
  })

  it('filters the tiles by the search query (category / color / descriptor)', async () => {
    listItemsMock.mockResolvedValue(items)
    const user = userEvent.setup()

    const { container } = render(<WardrobeDrawer />)
    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(2))

    await user.type(screen.getByRole('searchbox', { name: /search/i }), 'jean')

    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(1))
    expect(container.querySelector('[data-item-id="b"]')).toBeInTheDocument()
    expect(container.querySelector('[data-item-id="a"]')).not.toBeInTheDocument()
  })

  it('shows a loading state, then the grid', async () => {
    let resolve!: (v: Item[]) => void
    listItemsMock.mockReturnValue(
      new Promise<Item[]>((r) => {
        resolve = r
      }),
    )

    render(<WardrobeDrawer />)
    expect(screen.getByText(/loading/i)).toBeInTheDocument()

    resolve(items)
    await waitFor(() => expect(screen.getAllByRole('img')).toHaveLength(2))
  })

  it('shows a non-crashing error state when the list fails', async () => {
    listItemsMock.mockRejectedValue(new Error('offline'))

    render(<WardrobeDrawer />)

    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('shows an empty note when the wardrobe has no items', async () => {
    listItemsMock.mockResolvedValue([])

    render(<WardrobeDrawer />)

    expect(await screen.findByText(/no pieces|nothing here|empty/i)).toBeInTheDocument()
  })
})
