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
        <Route path="/wardrobe" element={<div>wardrobe grid</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

let revokeSpy: ReturnType<typeof vi.fn>

beforeEach(() => {
  tagPreviewMock.mockReset()
  createItemMock.mockReset()
  // jsdom has no object-URL support; stub it. Distinct URLs per call so each
  // queued item gets its own preview key (mirrors real browser behavior).
  revokeSpy = vi.fn()
  let n = 0
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => `blob:preview-${n++}`),
    revokeObjectURL: revokeSpy,
  })
})

afterEach(() => {
  // Unmount (which revokes remaining object URLs) while URL is still stubbed,
  // then restore globals — otherwise the unmount cleanup hits a real URL that
  // jsdom doesn't fully implement.
  cleanup()
  vi.unstubAllGlobals()
})

describe('AddItem review queue', () => {
  it('runs the headline N=1 flow: photo → auto-tag → edit → save → back to grid', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    const file = photoFile()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), file)

    // A single picked file enqueues exactly one tile that auto-tags (no separate
    // button) and pre-fills its form — the preserved N=1 experience.
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledWith(file))
    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(1)
    expect(categories[0]).toHaveValue('denim jacket')

    await user.clear(categories[0])
    await user.type(categories[0], 'trucker jacket')
    await user.click(screen.getByRole('button', { name: /save item/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    const [photoArg, tagsArg] = createItemMock.mock.calls[0]
    expect(photoArg).toBe(file)
    expect(tagsArg).toMatchObject({ category: 'trucker jacket', formality: 2, warmth: 3 })

    // Saving the only queued item drains the queue and returns to the grid.
    expect(await screen.findByText('wardrobe grid')).toBeInTheDocument()
  })

  it('still yields an editable, saveable tile when the suggestion is all-null', async () => {
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
    await user.click(screen.getByRole('button', { name: /save item/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf', formality: 2, warmth: 3 })
  })

  it('blocks a tile save until its required fields are valid', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    await screen.findByLabelText(/^category/i)
    const save = screen.getByRole('button', { name: /save item/i })
    expect(save).toBeDisabled()

    await user.type(screen.getByLabelText(/^category/i), 'hat')
    await user.selectOptions(screen.getByLabelText(/formality/i), '1')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '1')
    expect(save).toBeEnabled()
  })

  it('keeps the tile, its photo, and its edited tags when its create fails', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    const category = await screen.findByLabelText(/^category/i)
    await user.clear(category)
    await user.type(category, 'trucker jacket')
    await user.click(screen.getByRole('button', { name: /save item/i }))

    // A per-item error surfaces without navigating away or clearing that tile's work.
    expect(await screen.findByText(/couldn.t save|failed|try again/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^category/i)).toHaveValue('trucker jacket')
    expect(screen.getByAltText(/selected garment/i)).toBeInTheDocument()
    expect(screen.queryByText('wardrobe grid')).not.toBeInTheDocument()
  })

  it('falls back to a blank editable tile when that item’s tag-preview rejects', async () => {
    // Required edge case: a failed vision call is a normal, editable per-item state.
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
    await user.click(screen.getByRole('button', { name: /save item/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf' })
  })

  it('seeds each tile from its own tag response even when one arrives out of order', async () => {
    // Per-item request-id guard: a late (stale) response for tile A must seed only
    // tile A and never bleed into tile B. Two separate tiles expose the race.
    const first: TagSuggestion = { ...allNull, category: 'first jacket', formality: 2, warmth: 2 }
    const second: TagSuggestion = { ...allNull, category: 'second jacket', formality: 2, warmth: 2 }
    let resolveA!: (s: TagSuggestion) => void
    let resolveB!: (s: TagSuggestion) => void
    tagPreviewMock
      .mockImplementationOnce(() => new Promise((r) => { resolveA = r }))
      .mockImplementationOnce(() => new Promise((r) => { resolveB = r }))
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, photoFile('a.jpg')) // tile A (queued first)
    await user.upload(input, photoFile('b.jpg')) // tile B (queued second)

    // Tile B resolves first; tile A's response arrives out of order afterwards.
    await act(async () => {
      resolveB(second)
    })
    await act(async () => {
      resolveA(first)
    })

    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(2)
    expect(categories[0]).toHaveValue('first jacket') // tile A kept its own tags
    expect(categories[1]).toHaveValue('second jacket') // tile B untouched by A
  })

  it('revokes only the removed tile’s object URL when it is removed', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, photoFile('a.jpg')) // blob:preview-0
    await user.upload(input, photoFile('b.jpg')) // blob:preview-1

    await waitFor(() => expect(screen.getAllByRole('button', { name: /remove/i })).toHaveLength(2))
    await user.click(screen.getAllByRole('button', { name: /remove/i })[0])

    expect(revokeSpy).toHaveBeenCalledWith('blob:preview-0')
    expect(revokeSpy).not.toHaveBeenCalledWith('blob:preview-1')
  })

  it('revokes a tile’s object URL on save', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile()) // blob:preview-0

    await screen.findByLabelText(/^category/i)
    await user.click(screen.getByRole('button', { name: /save item/i }))

    await waitFor(() => expect(revokeSpy).toHaveBeenCalledWith('blob:preview-0'))
  })

  it('revokes every remaining tile’s object URL on unmount', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    const { unmount } = renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, photoFile('a.jpg')) // blob:preview-0
    await user.upload(input, photoFile('b.jpg')) // blob:preview-1

    unmount()

    expect(revokeSpy).toHaveBeenCalledWith('blob:preview-0')
    expect(revokeSpy).toHaveBeenCalledWith('blob:preview-1')
  })

  it('does not force the camera, so an existing photo can be chosen', () => {
    tagPreviewMock.mockResolvedValue(allNull)

    renderAddItem()

    expect(screen.getByLabelText(/choose a garment photo/i)).not.toHaveAttribute('capture')
  })
})
