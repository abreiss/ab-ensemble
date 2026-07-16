import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
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

function photoFile(name = 'jacket.jpg') {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/jpeg' })
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

let revokeSpy: ReturnType<typeof vi.fn>

beforeEach(() => {
  tagPreviewMock.mockReset()
  createItemMock.mockReset()
  // jsdom has no object-URL support; stub it. Distinct URLs per call so the
  // preview key changes on re-select (mirrors real browser behavior).
  revokeSpy = vi.fn()
  let n = 0
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => `blob:preview-${n++}`),
    revokeObjectURL: revokeSpy,
  })
})

afterEach(() => {
  // Unmount (which revokes the object URL) while URL is still stubbed, then
  // restore globals — otherwise the unmount cleanup hits a real URL that jsdom
  // doesn't fully implement.
  cleanup()
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

  it('falls back to an editable blank form when tag-preview fails', async () => {
    // Required edge case: a failed vision call is a normal, editable state.
    tagPreviewMock.mockRejectedValue(new Error('vision unavailable'))
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('')

    // The blank fallback is still saveable — never a dead end.
    await user.type(category, 'scarf')
    await user.selectOptions(screen.getByLabelText(/formality/i), '2')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '3')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf' })
  })

  it('shows the latest photo’s tags when a second photo is chosen before the first tags return', async () => {
    // Guards against an out-of-order tag-preview race seeding the wrong photo.
    const first: TagSuggestion = { ...allNull, category: 'first jacket', formality: 2, warmth: 2 }
    const second: TagSuggestion = { ...allNull, category: 'second jacket', formality: 2, warmth: 2 }
    let resolveFirst!: (s: TagSuggestion) => void
    let resolveSecond!: (s: TagSuggestion) => void
    tagPreviewMock
      .mockImplementationOnce(() => new Promise((r) => { resolveFirst = r }))
      .mockImplementationOnce(() => new Promise((r) => { resolveSecond = r }))
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, photoFile('a.jpg'))
    await user.upload(input, photoFile('b.jpg'))

    // The stale (first) call resolves and renders BEFORE the latest one — the
    // separate render is what exposes the race (a single batch would mask it).
    await act(async () => {
      resolveFirst(first)
    })
    await act(async () => {
      resolveSecond(second)
    })

    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('second jacket')
  })

  it('revokes the previous photo’s object URL when a new photo is chosen', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, photoFile('a.jpg'))
    await user.upload(input, photoFile('b.jpg'))

    expect(revokeSpy).toHaveBeenCalledWith('blob:preview-0')
  })

  it('does not force the camera, so an existing photo can be chosen', () => {
    tagPreviewMock.mockResolvedValue(allNull)

    renderAddItem()

    expect(screen.getByLabelText(/choose a garment photo/i)).not.toHaveAttribute('capture')
  })
})
