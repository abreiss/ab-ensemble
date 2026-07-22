import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WardrobeCell from './WardrobeCell'
import type { Item } from '../types/item'

// The cell owns the delete interaction; mock the API so no network is touched.
vi.mock('../api/items', () => ({
  deleteItem: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { deleteItem } from '../api/items'

const deleteItemMock = vi.mocked(deleteItem)

function item(id: string, category: string | null = 'Jacket'): Item {
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

/** Renders a single cell inside a router + list so links resolve and DOM nests validly. */
function renderCell(it: Item, onDeleted = vi.fn()) {
  render(
    <MemoryRouter>
      <ul>
        <WardrobeCell item={it} onDeleted={onDeleted} />
      </ul>
    </MemoryRouter>,
  )
  return { onDeleted }
}

beforeEach(() => {
  deleteItemMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('WardrobeCell', () => {
  it('renders an accessible delete control while the thumbnail still links to detail', () => {
    renderCell(item('a'))

    expect(screen.getByRole('button', { name: /delete jacket/i })).toBeInTheDocument()
    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/item/a')
    expect(screen.getByRole('img')).toHaveAttribute('src', '/api/items/a/photo')
  })

  it('arms the confirm on first click without deleting, offering Cancel and Delete', async () => {
    const user = userEvent.setup()
    renderCell(item('a'))

    await user.click(screen.getByRole('button', { name: /delete jacket/i }))

    expect(deleteItemMock).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /^cancel$/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument()
  })

  it('deletes on confirm and reports the removal to the parent', async () => {
    deleteItemMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    const { onDeleted } = renderCell(item('a'))

    await user.click(screen.getByRole('button', { name: /delete jacket/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    await waitFor(() => expect(deleteItemMock).toHaveBeenCalledWith('a'))
    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith('a'))
  })

  it('cancels out of the confirm without deleting', async () => {
    const user = userEvent.setup()
    const { onDeleted } = renderCell(item('a'))

    await user.click(screen.getByRole('button', { name: /delete jacket/i }))
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))

    expect(deleteItemMock).not.toHaveBeenCalled()
    expect(onDeleted).not.toHaveBeenCalled()
    // Back to the idle affordance; the confirm controls are gone.
    expect(screen.getByRole('button', { name: /delete jacket/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument()
  })

  it('surfaces an error and keeps the confirm dismissable when delete fails', async () => {
    deleteItemMock.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()
    const { onDeleted } = renderCell(item('a'))

    await user.click(screen.getByRole('button', { name: /delete jacket/i }))
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(await screen.findByText(/couldn.t delete|failed/i)).toBeInTheDocument()
    expect(onDeleted).not.toHaveBeenCalled()
    // The confirm stays open so the user can retry or back out.
    expect(screen.getByRole('button', { name: /^cancel$/i })).toBeInTheDocument()
  })

  it('falls back to a generic label when the item has no category', () => {
    renderCell(item('a', null))

    expect(screen.getByRole('button', { name: /delete garment/i })).toBeInTheDocument()
  })
})
