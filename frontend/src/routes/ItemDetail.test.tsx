import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ItemDetail from './ItemDetail'
import type { Item } from '../types/item'

vi.mock('../api/items', () => ({
  getItem: vi.fn(),
  updateTags: vi.fn(),
  deleteItem: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

// Deterministic relative label — the helper has its own unit test; here we only
// care that the component wires a present/absent instant to the right copy.
vi.mock('../lib/relativeTime', () => ({
  relativeTime: (iso: string | null | undefined) => (iso ? '2 days ago' : 'not yet worn'),
}))

import { deleteItem, getItem, updateTags } from '../api/items'

const getItemMock = vi.mocked(getItem)
const updateTagsMock = vi.mocked(updateTags)
const deleteItemMock = vi.mocked(deleteItem)

const sampleItem: Item = {
  itemId: 'abc',
  category: 'shirt',
  primaryColor: 'navy',
  secondaryColor: null,
  formality: 3,
  pattern: 'striped',
  warmth: 2,
  descriptors: ['cotton'],
  photoUrl: '/api/items/abc/photo',
  createdAt: '2026-01-01T00:00:00Z',
  lastWorn: '2026-05-05T00:00:00Z',
  wornCount: 7,
}

function renderDetail(id = 'abc') {
  return render(
    <MemoryRouter initialEntries={[`/item/${id}`]}>
      <Routes>
        <Route path="/item/:id" element={<ItemDetail />} />
        <Route path="/wardrobe" element={<div>wardrobe grid</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  getItemMock.mockReset()
  updateTagsMock.mockReset()
  deleteItemMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('ItemDetail', () => {
  it('loads an item, edits a tag, and saves via updateTags with the JSON payload', async () => {
    getItemMock.mockResolvedValue(sampleItem)
    updateTagsMock.mockResolvedValue(sampleItem)
    const user = userEvent.setup()

    renderDetail()

    const primary = await screen.findByLabelText(/primary color/i)
    expect(primary).toHaveValue('navy')

    await user.clear(primary)
    await user.type(primary, 'black')
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => expect(updateTagsMock).toHaveBeenCalledTimes(1))
    const [idArg, tagsArg] = updateTagsMock.mock.calls[0]
    expect(idArg).toBe('abc')
    expect(tagsArg).toMatchObject({ category: 'shirt', primaryColor: 'black', formality: 3, warmth: 2 })
  })

  it('shows the wear count and a relative last-worn label for a worn item', async () => {
    getItemMock.mockResolvedValue(sampleItem)

    renderDetail()

    await screen.findByLabelText(/primary color/i)
    // wornCount 7 + a present lastWorn → "Worn 7× · 2 days ago".
    expect(screen.getByText(/worn 7×/i)).toBeInTheDocument()
    expect(screen.getByText(/2 days ago/i)).toBeInTheDocument()
  })

  it('shows a "Never worn" state when the item has never been worn', async () => {
    getItemMock.mockResolvedValue({ ...sampleItem, wornCount: null, lastWorn: null })

    renderDetail()

    await screen.findByLabelText(/primary color/i)
    expect(screen.getByText(/never worn/i)).toBeInTheDocument()
    expect(screen.queryByText(/worn 7×/i)).not.toBeInTheDocument()
  })

  it('shows a "not yet worn" last-worn state when lastWorn is absent', async () => {
    getItemMock.mockResolvedValue({ ...sampleItem, wornCount: 2, lastWorn: null })

    renderDetail()

    await screen.findByLabelText(/primary color/i)
    expect(screen.getByText(/worn 2×/i)).toBeInTheDocument()
    expect(screen.getByText(/not yet worn/i)).toBeInTheDocument()
  })

  it('requires an explicit confirm before deleting, then navigates back to the grid', async () => {
    getItemMock.mockResolvedValue(sampleItem)
    deleteItemMock.mockResolvedValue(undefined)
    const user = userEvent.setup()

    renderDetail()
    await screen.findByLabelText(/primary color/i)

    // First press only arms the confirmation — no delete yet.
    await user.click(screen.getByRole('button', { name: /^delete item$/i }))
    expect(deleteItemMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: /confirm delete/i }))

    await waitFor(() => expect(deleteItemMock).toHaveBeenCalledWith('abc'))
    expect(await screen.findByText('wardrobe grid')).toBeInTheDocument()
  })

  it('backs out of delete when Cancel is pressed, without deleting the item', async () => {
    getItemMock.mockResolvedValue(sampleItem)
    const user = userEvent.setup()

    renderDetail()
    await screen.findByLabelText(/primary color/i)

    // Arm the confirmation, then back out — the guard must NOT delete.
    await user.click(screen.getByRole('button', { name: /^delete item$/i }))
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))

    expect(deleteItemMock).not.toHaveBeenCalled()
    // The confirm step is dismissed and the primary delete control is back.
    expect(screen.queryByRole('button', { name: /confirm delete/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^delete item$/i })).toBeInTheDocument()
  })

  it('shows a non-crashing not-found state when the item cannot be loaded', async () => {
    getItemMock.mockRejectedValue(new Error('404'))

    renderDetail('missing')

    expect(await screen.findByText(/not found|couldn.t load/i)).toBeInTheDocument()
    // A link back to the grid keeps the dead-end navigable.
    expect(screen.getByRole('link', { name: /wardrobe|back|grid/i })).toBeInTheDocument()
  })

  it('keeps the user context when a save fails', async () => {
    getItemMock.mockResolvedValue(sampleItem)
    updateTagsMock.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()

    renderDetail()
    const primary = await screen.findByLabelText(/primary color/i)
    await user.clear(primary)
    await user.type(primary, 'black')
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    expect(await screen.findByText(/couldn.t save|failed/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/primary color/i)).toHaveValue('black')
  })

  it('keeps the user on the page when a delete fails', async () => {
    getItemMock.mockResolvedValue(sampleItem)
    deleteItemMock.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()

    renderDetail()
    await screen.findByLabelText(/primary color/i)
    await user.click(screen.getByRole('button', { name: /^delete item$/i }))
    await user.click(screen.getByRole('button', { name: /confirm delete/i }))

    expect(await screen.findByText(/couldn.t delete|failed/i)).toBeInTheDocument()
    expect(screen.queryByText('wardrobe grid')).not.toBeInTheDocument()
  })
})
