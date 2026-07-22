import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WardrobeGrid from './WardrobeGrid'
import type { Item } from '../types/item'

// The grid must never touch the network in tests; mock the API module.
vi.mock('../api/items', () => ({
  listItems: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { listItems } from '../api/items'

const listItemsMock = vi.mocked(listItems)

function item(id: string, category = 'top'): Item {
  return {
    itemId: id,
    category,
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
  }
}

/** Renders the grid inside a router with a detail probe so navigation is observable. */
function renderGrid() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<WardrobeGrid />} />
        <Route path="/add" element={<div>add screen</div>} />
        <Route path="/item/:id" element={<DetailProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

function DetailProbe() {
  const { id } = useParams()
  return <div>detail for {id}</div>
}

beforeEach(() => {
  listItemsMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('WardrobeGrid', () => {
  it('renders one thumbnail per owned item using its photoUrl', async () => {
    listItemsMock.mockResolvedValue([item('a'), item('b'), item('c')])

    renderGrid()

    const thumbs = await screen.findAllByRole('img')
    expect(thumbs).toHaveLength(3)
    expect(thumbs[0]).toHaveAttribute('src', '/api/items/a/photo')
    // Thumbnails lazy-load — bytes are fetched per-item, not embedded in list JSON.
    expect(thumbs[0]).toHaveAttribute('loading', 'lazy')
  })

  it('navigates to the item detail route when a thumbnail is tapped', async () => {
    listItemsMock.mockResolvedValue([item('a'), item('b')])
    const user = userEvent.setup()

    renderGrid()

    const links = await screen.findAllByRole('link')
    const detailLink = links.find((l) => l.getAttribute('href') === '/item/b')
    expect(detailLink).toBeDefined()
    await user.click(detailLink!)

    expect(await screen.findByText('detail for b')).toBeInTheDocument()
  })

  it('exposes an entry-point link to the manual assembly screen in the populated grid', async () => {
    listItemsMock.mockResolvedValue([item('a')])

    renderGrid()

    const link = await screen.findByRole('link', { name: /build it yourself/i })
    expect(link).toHaveAttribute('href', '/assemble')
  })

  it('shows an empty state inviting a first add when the wardrobe is empty', async () => {
    listItemsMock.mockResolvedValue([])

    renderGrid()

    expect(await screen.findByRole('heading', { name: /no items yet/i })).toBeInTheDocument()
    const addLink = screen.getByRole('link', { name: /add/i })
    expect(addLink).toHaveAttribute('href', '/add')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('shows a non-crashing error state with a retry that re-fetches on list failure', async () => {
    listItemsMock.mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()

    renderGrid()

    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument()

    // Retry succeeds → the grid recovers without a reload.
    listItemsMock.mockResolvedValueOnce([item('a')])
    await user.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(screen.getByRole('img')).toBeInTheDocument())
    expect(listItemsMock).toHaveBeenCalledTimes(2)
  })

  it('groups items into category sections rendered in fixed taxonomy order with Other last', async () => {
    listItemsMock.mockResolvedValue([
      item('widget-item', 'widget'), // unrecognized → Other
      item('jacket-item', 'Jacket'),
      item('shoes-item', 'Shoes'),
    ])

    renderGrid()

    const headings = await screen.findAllByRole('heading')
    const headingNames = headings.map((h) => h.textContent)
    expect(headingNames).toEqual(['Jackets', 'Shoes', 'Other'])
  })

  it('shows a Jewelry item under a "Jewelry" section header', async () => {
    listItemsMock.mockResolvedValue([item('necklace-item', 'Jewelry')])

    renderGrid()

    expect(await screen.findByRole('heading', { name: 'Jewelry' })).toBeInTheDocument()
    expect(await screen.findByRole('img')).toBeInTheDocument()
  })

  it('omits empty category sections entirely', async () => {
    listItemsMock.mockResolvedValue([item('a', 'Jacket')])

    renderGrid()

    await screen.findByRole('heading', { name: 'Jackets' })
    expect(screen.queryByRole('heading', { name: 'Tops' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Bottoms' })).not.toBeInTheDocument()
  })
})
