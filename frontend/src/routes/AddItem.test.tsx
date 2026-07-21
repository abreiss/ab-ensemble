import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AddItem from './AddItem'
import type { Item, TagSuggestion } from '../types/item'

// Mock only the network functions; keep the real `ApiError` so the screen's
// `instanceof ApiError` 429 detection is exercised against the production class.
vi.mock('../api/items', async () => {
  const actual = await vi.importActual<typeof import('../api/items')>('../api/items')
  return { ...actual, tagPreview: vi.fn(), createItem: vi.fn() }
})

// Keep the real Blob→File wrapping and clipboard extractors (their own suites
// cover them); mock only the capability gate so button visibility is controllable
// without stubbing `navigator`/`ClipboardItem` per test.
vi.mock('../lib/clipboardImages', async () => {
  const actual =
    await vi.importActual<typeof import('../lib/clipboardImages')>('../lib/clipboardImages')
  return { ...actual, clipboardReadSupported: vi.fn(() => false) }
})

import { ApiError, createItem, tagPreview } from '../api/items'
import { clipboardReadSupported } from '../lib/clipboardImages'

const tagPreviewMock = vi.mocked(tagPreview)
const createItemMock = vi.mocked(createItem)
const clipboardReadSupportedMock = vi.mocked(clipboardReadSupported)

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
  // Default: async clipboard read unsupported, so the "Paste image" button is
  // absent unless a test opts in (the paste-event path works regardless).
  clipboardReadSupportedMock.mockReset()
  clipboardReadSupportedMock.mockReturnValue(false)
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
    await user.click(screen.getByRole('button', { name: /save all/i }))

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
    await user.click(screen.getByRole('button', { name: /save all/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf', formality: 2, warmth: 3 })
  })

  it('keeps "Save all" disabled until every tile has its required fields', async () => {
    tagPreviewMock.mockResolvedValue(allNull)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    await screen.findByLabelText(/^category/i)
    const saveAll = screen.getByRole('button', { name: /save all/i })
    expect(saveAll).toBeDisabled()

    await user.type(screen.getByLabelText(/^category/i), 'hat')
    await user.selectOptions(screen.getByLabelText(/formality/i), '1')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '1')
    expect(saveAll).toBeEnabled()
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
    await user.click(screen.getByRole('button', { name: /save all/i }))

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
    await user.click(screen.getByRole('button', { name: /save all/i }))

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
    await user.click(screen.getByRole('button', { name: /save all/i }))

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

  it('offers a plain library picker: multi-select, image-only, and no forced camera', () => {
    tagPreviewMock.mockResolvedValue(allNull)

    renderAddItem()

    const input = screen.getByLabelText(/choose a garment photo/i)
    // A batch multi-select that never forces the camera, so an existing library
    // photo (or several) can be chosen.
    expect(input).not.toHaveAttribute('capture')
    expect(input).toHaveAttribute('accept', 'image/*')
    expect(input).toHaveAttribute('multiple')
  })

  it('enqueues one editable, auto-tagged tile per file from a multi-select', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, [photoFile('a.jpg'), photoFile('b.jpg'), photoFile('c.jpg')])

    // Every picked file becomes its own queue tile and auto-tags like the N=1 path.
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledTimes(3))
    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(3)
    categories.forEach((c) => expect(c).toHaveValue('denim jacket'))
  })

  it('saves independently: a per-item failure keeps that tile (edited tags) while successes are removed', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    // First save succeeds (tile A), second is rejected (tile B) — independent saves.
    createItemMock.mockResolvedValueOnce(createdItem).mockRejectedValueOnce(new Error('nope'))
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, [photoFile('a.jpg'), photoFile('b.jpg')])

    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(2)
    // Edit the second tile so we can prove its edits survive its save failure.
    await user.clear(categories[1])
    await user.type(categories[1], 'edited jacket')

    await user.click(screen.getByRole('button', { name: /save all/i }))

    // The failed tile stays in the queue with its edited tags; the saved one is gone.
    expect(await screen.findByText(/couldn.t save|try again/i)).toBeInTheDocument()
    const remaining = screen.getAllByLabelText(/^category/i)
    expect(remaining).toHaveLength(1)
    expect(remaining[0]).toHaveValue('edited jacket')
    expect(screen.queryByText('wardrobe grid')).not.toBeInTheDocument()
    expect(createItemMock).toHaveBeenCalledTimes(2)
  })

  it('navigates to the wardrobe once "Save all" drains the whole queue', async () => {
    tagPreviewMock.mockResolvedValue(suggestion)
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, [photoFile('a.jpg'), photoFile('b.jpg')])

    await screen.findAllByLabelText(/^category/i)
    await user.click(screen.getByRole('button', { name: /save all/i }))

    expect(await screen.findByText('wardrobe grid')).toBeInTheDocument()
    expect(createItemMock).toHaveBeenCalledTimes(2)
  })

  it('throttles auto-tag fan-out to at most 3 tag-preview calls at once for a large batch', async () => {
    // The concurrency limit is structural, so the observed peak can never exceed 3;
    // with more files than the limit it must also reach it. Each preview stays in
    // flight briefly so overlapping calls are observable.
    let live = 0
    let peak = 0
    tagPreviewMock.mockImplementation(async () => {
      live += 1
      peak = Math.max(peak, live)
      await new Promise((resolve) => setTimeout(resolve, 1))
      live -= 1
      return suggestion
    })
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(
      input,
      Array.from({ length: 7 }, (_, i) => photoFile(`f${i}.jpg`)),
    )

    // Every one of the 7 tiles eventually tags, but never more than 3 at a time.
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledTimes(7))
    expect(peak).toBe(3)
  })

  it('on a mid-batch 429 stops further auto-tagging, keeps every tile editable, and shows the cap banner', async () => {
    // The first preview hits the daily cap; not-yet-started previews must not fire,
    // and every tile must remain on the blank editable seed (nothing lost).
    tagPreviewMock
      .mockRejectedValueOnce(new ApiError(429, 'Tag preview'))
      .mockResolvedValue(suggestion)
    const user = userEvent.setup()

    renderAddItem()
    const input = screen.getByLabelText(/choose a garment photo/i)
    await user.upload(input, [
      photoFile('a.jpg'),
      photoFile('b.jpg'),
      photoFile('c.jpg'),
      photoFile('d.jpg'),
    ])

    // The persistent, non-blocking cap banner appears...
    expect(await screen.findByText(/daily ai limit reached/i)).toBeInTheDocument()
    // ...all four tiles are present and editable (none crashed)...
    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(4)
    // ...and auto-tag stopped after the cap, so the 4th preview never fired.
    expect(tagPreviewMock.mock.calls.length).toBeLessThan(4)
  })

  it('does not show the cap banner for a non-429 tag-preview failure (Unit 1 degraded fallback only)', async () => {
    tagPreviewMock.mockRejectedValue(new Error('vision unavailable'))
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    // The tile still falls back to a blank editable form, but with no cap banner.
    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('')
    expect(screen.queryByText(/daily ai limit reached/i)).not.toBeInTheDocument()
  })

  it('still saves the preserved tiles after a 429 (the cap blocks only auto-tagging)', async () => {
    tagPreviewMock.mockRejectedValue(new ApiError(429, 'Tag preview'))
    createItemMock.mockResolvedValue(createdItem)
    const user = userEvent.setup()

    renderAddItem()
    await user.upload(screen.getByLabelText(/choose a garment photo/i), photoFile())

    // Cap banner shows; the tile is on the blank editable seed.
    expect(await screen.findByText(/daily ai limit reached/i)).toBeInTheDocument()
    const category = await screen.findByLabelText(/^category/i)
    expect(category).toHaveValue('')

    // Manual tagging + "Save all" still persists via the create endpoint.
    await user.type(category, 'scarf')
    await user.selectOptions(screen.getByLabelText(/formality/i), '2')
    await user.selectOptions(screen.getByLabelText(/warmth/i), '3')
    await user.click(screen.getByRole('button', { name: /save all/i }))

    await waitFor(() => expect(createItemMock).toHaveBeenCalledTimes(1))
    expect(createItemMock.mock.calls[0][1]).toMatchObject({ category: 'scarf' })
  })

  it('enqueues a wrapped image File when an image is pasted onto the screen', async () => {
    // A pasted image funnels through the same shared pipeline as any other source.
    tagPreviewMock.mockResolvedValue(suggestion)

    renderAddItem()
    const pasted = new File([new Uint8Array([9, 9, 9])], 'screenshot.png', { type: 'image/png' })
    fireEvent.paste(screen.getByTestId('add-item'), {
      clipboardData: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => pasted }] },
    })

    // One tile appears and auto-tags; the file sent is a wrapped image File.
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledTimes(1))
    const sent = tagPreviewMock.mock.calls[0][0]
    expect(sent).toBeInstanceOf(File)
    expect(sent.type).toBe('image/png')
    const categories = await screen.findAllByLabelText(/^category/i)
    expect(categories).toHaveLength(1)
  })

  it('ignores a paste that carries no image (e.g. plain text)', () => {
    // A text-only paste must not create a tile or fire a tag-preview.
    renderAddItem()
    fireEvent.paste(screen.getByTestId('add-item'), {
      clipboardData: { items: [{ kind: 'string', type: 'text/plain', getAsFile: () => null }] },
    })

    expect(tagPreviewMock).not.toHaveBeenCalled()
    expect(screen.queryByAltText(/selected garment/i)).not.toBeInTheDocument()
  })

  it('hides the "Paste image" button when async clipboard read is unsupported', () => {
    clipboardReadSupportedMock.mockReturnValue(false)

    renderAddItem()

    expect(screen.queryByRole('button', { name: /paste image/i })).not.toBeInTheDocument()
  })

  it('shows the "Paste image" button when async clipboard read is supported', () => {
    clipboardReadSupportedMock.mockReturnValue(true)

    renderAddItem()

    expect(screen.getByRole('button', { name: /paste image/i })).toBeInTheDocument()
  })

  it('reads the clipboard and enqueues an image when "Paste image" is clicked', async () => {
    // The gated button uses the async read API; a returned image ClipboardItem
    // enqueues a tile that tags like any other source.
    clipboardReadSupportedMock.mockReturnValue(true)
    tagPreviewMock.mockResolvedValue(suggestion)
    const pngBlob = new Blob([new Uint8Array([5, 5])], { type: 'image/png' })
    const readMock = vi.fn(async () => [{ types: ['image/png'], getType: async () => pngBlob }])

    renderAddItem()
    // Stub the read API only for the click (after render, and without userEvent,
    // which would install its own navigator.clipboard).
    vi.stubGlobal('navigator', { clipboard: { read: readMock } })
    fireEvent.click(screen.getByRole('button', { name: /paste image/i }))

    await waitFor(() => expect(readMock).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(tagPreviewMock).toHaveBeenCalledTimes(1))
    expect(await screen.findByLabelText(/^category/i)).toBeInTheDocument()
  })
})
