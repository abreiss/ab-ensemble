import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import Assemble from './Assemble'
import type { Item } from '../types/item'

// The screen must never touch the network in tests; mock the items API module.
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

function renderAssemble() {
  return render(
    <MemoryRouter initialEntries={['/assemble']}>
      <Assemble />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  listItemsMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('Assemble route', () => {
  it('shows an empty-wardrobe state inviting a first add when there are no items', async () => {
    listItemsMock.mockResolvedValue([])

    renderAssemble()

    expect(await screen.findByRole('heading', { name: /no items yet/i })).toBeInTheDocument()
    const addLink = screen.getByRole('link', { name: /add/i })
    expect(addLink).toHaveAttribute('href', '/add')
    expect(addLink).toHaveClass('btn', 'btn-primary')
    expect(screen.getByTestId('assemble')).toHaveClass('screen')
  })

  it('shows a non-crashing error state with a retry that re-fetches on list failure', async () => {
    listItemsMock.mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()

    renderAssemble()

    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument()

    listItemsMock.mockResolvedValueOnce([item('a')])
    await user.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(listItemsMock).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/couldn.t load/i)).not.toBeInTheDocument()
  })

  it('renders the mannequin with four labeled, Slot-keyed drop zones when items exist', async () => {
    listItemsMock.mockResolvedValue([item('a')])

    renderAssemble()

    expect(await screen.findByText(/^top$/i)).toBeInTheDocument()
    expect(screen.getByText(/^bottom$/i)).toBeInTheDocument()
    expect(screen.getByText(/^shoes$/i)).toBeInTheDocument()
    expect(screen.getByText(/^extras$/i)).toBeInTheDocument()
  })
})
