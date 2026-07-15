import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AddItem from './AddItem'
import type { Item, TagSuggestion } from '../types/item'

vi.mock('../api/items', () => ({
  tagPreview: vi.fn(),
  createItem: vi.fn(),
}))

import { createItem, tagPreview } from '../api/items'

const tagPreviewMock = vi.mocked(tagPreview)
const createItemMock = vi.mocked(createItem)

const suggestion: TagSuggestion = {
  category: 'denim jacket',
  primaryColor: 'indigo',
  secondaryColor: null,
  formality: 2,
  pattern: 'solid',
  warmth: 3,
  descriptors: ['denim'],
}

const allNull: TagSuggestion = {
  category: null,
  primaryColor: null,
  secondaryColor: null,
  formality: null,
  pattern: null,
  warmth: null,
  descriptors: null,
}

const createdItem: Item = {
  itemId: 'new-1',
  category: 'denim jacket',
  primaryColor: 'indigo',
  secondaryColor: null,
  formality: 2,
  pattern: 'solid',
  warmth: 3,
  descriptors: ['denim'],
  photoUrl: '/api/items/new-1/photo',
  createdAt: '2026-07-01T00:00:00Z',
  lastWorn: null,
  wornCount: 0,
}

function photoFile() {
  return new File([new Uint8Array([1, 2, 3])], 'jacket.jpg', { type: 'image/jpeg' })
}

function renderAddItem() {
  return render(
    <MemoryRouter initialEntries={['/add']}>
      <Routes>
        <Route path="/add" element={<AddItem />} />
        <Route path="/" element={<div>wardrobe grid</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  tagPreviewMock.mockReset()
  createItemMock.mockReset()
  // jsdom has no object-URL support; stub it for the photo preview.
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => 'blob:preview'),
    revokeObjectURL: vi.fn(),
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AddItem', () => {
  it('runs the headline flow: photo → auto-tag → edit → save → back to grid', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    const file = photoFile()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), file)

    // Auto tag-preview fires on select (no separate button) and pre-fills the form.
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledWith(file))
    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('denim jacket')

    await user.clear(category)
    await user.type(category, 'trucker jacket')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    const [photoArg, tagsArg] = createItemMock.mock.calls[0]
    expect(photoArg).toBe(file)
    expect(tagsArg).toMatchObject({ category: 'trucker jacket', formality: 2, warmth: 3 })

    // On success the app returns to the wardrobe grid.
    expect(await screen.findByText('wardrobe grid')).toBeInTheDocument()
  })

  it('still yields an editable, saveable form when the suggestion is all-null', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('')

    await user.type(category, 'scarf')
    await user.selectOptions(screen.getByLabelText(/formality/i), '2')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '3')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf', formality: 2, warmth: 3 })
  })

  it('blocks save until the required fields are valid', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    await screen.findByLabelText(/^category/i)
    const save = screen.getByRole('button', { name: /save/i })
    expect(save).toBeDisabled()

    await user.type(screen.getByLabelText(/^category/i), 'hat')
    await user.selectOptions(screen.getByLabelText(/formality/i), '1')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '1')
    expect(save).toBeEnabled()
  })

  it('preserves the photo and entered tags when create fails', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    const category = await screen.findByLabelText(/^category/i)
    await user.clear(category)
    await user.type(category, 'trucker jacket')
    await user.click(screen.getByRole('button', { name: /save/i }))

    // An error surfaces without navigating away or clearing the user's work.
    expect(await screen.findByText(/couldn.t save|failed|try again/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^category/i)).toHaveValue('trucker jacket')
    expect(screen.getByAltText(/selected garment/i)).toBeInTheDocument()
    expect(screen.queryByText('wardrobe grid')).not.toBeInTheDocument()
  })
})
