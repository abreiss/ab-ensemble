import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import SavedOutfits from './SavedOutfits'
import type { Item } from '../types/item'
import type { SavedOutfit } from '../types/outfit'

// The page must never touch the network in tests; mock both API modules it reads.
vi.mock('../api/outfits', () => ({
  listOutfits: vi.fn(),
  deleteOutfit: vi.fn(),
}))

vi.mock('../api/items', () => ({
  listItems: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { listItems } from '../api/items'
import { deleteOutfit, listOutfits } from '../api/outfits'

const listOutfitsMock = vi.mocked(listOutfits)
const listItemsMock = vi.mocked(listItems)
const deleteOutfitMock = vi.mocked(deleteOutfit)

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

function outfit(
  outfitId: string,
  itemIds: string[],
  source: 'ai' | 'manual' = 'manual',
  reason: string | null = null,
): SavedOutfit {
  return { outfitId, itemIds, source, reason, createdAt: '2026-02-01T00:00:00Z' }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/saved']}>
      <SavedOutfits />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  listOutfitsMock.mockReset()
  listItemsMock.mockReset()
  deleteOutfitMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('SavedOutfits', () => {
  it('shows a loading note while the lists are in flight', () => {
    listOutfitsMock.mockReturnValue(new Promise<SavedOutfit[]>(() => {}))
    listItemsMock.mockReturnValue(new Promise<Item[]>(() => {}))

    renderPage()

    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('renders one card per saved outfit with its pieces photos and source/reason', async () => {
    listOutfitsMock.mockResolvedValue([
      outfit('o1', ['a', 'b'], 'ai', 'Sharp for the office'),
      outfit('o2', ['c'], 'manual', null),
    ])
    listItemsMock.mockResolvedValue([item('a'), item('b'), item('c')])

    renderPage()

    const cards = await screen.findAllByTestId('outfit-card')
    expect(cards).toHaveLength(2)

    // Each piece renders as a thumbnail via photoUrl(itemId).
    const imgs = screen.getAllByRole('img')
    expect(imgs.map((i) => i.getAttribute('src'))).toEqual([
      '/api/items/a/photo',
      '/api/items/b/photo',
      '/api/items/c/photo',
    ])

    // The AI look shows its whole-look reason; both sources are labelled.
    expect(screen.getByText('Sharp for the office')).toBeInTheDocument()
    expect(screen.getByText(/ai pick/i)).toBeInTheDocument()
    expect(screen.getByText(/hand-built/i)).toBeInTheDocument()
  })

  it('shows an empty state when there are no saved outfits', async () => {
    listOutfitsMock.mockResolvedValue([])
    listItemsMock.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText(/no saved outfits yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('shows a non-crashing error state with a retry that re-fetches on load failure', async () => {
    listOutfitsMock.mockRejectedValueOnce(new Error('offline'))
    listItemsMock.mockResolvedValue([item('a')])
    const user = userEvent.setup()

    renderPage()

    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument()

    // Retry succeeds → the page recovers without a reload.
    listOutfitsMock.mockResolvedValueOnce([outfit('o1', ['a'], 'manual', null)])
    await user.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(screen.getByTestId('outfit-card')).toBeInTheDocument())
    expect(listOutfitsMock).toHaveBeenCalledTimes(2)
  })

  it('removes a saved outfit from the list after its remove control succeeds', async () => {
    listOutfitsMock.mockResolvedValue([outfit('o1', ['a']), outfit('o2', ['b'])])
    listItemsMock.mockResolvedValue([item('a'), item('b')])
    deleteOutfitMock.mockResolvedValue(undefined)
    const user = userEvent.setup()

    renderPage()

    expect(await screen.findAllByTestId('outfit-card')).toHaveLength(2)

    // Remove the first card only.
    await user.click(screen.getAllByRole('button', { name: /remove/i })[0])

    await waitFor(() => expect(deleteOutfitMock).toHaveBeenCalledWith('o1'))
    await waitFor(() => expect(screen.getAllByTestId('outfit-card')).toHaveLength(1))
    // The survivor is the second outfit (its piece photo remains).
    expect(screen.getByRole('img')).toHaveAttribute('src', '/api/items/b/photo')
    expect(listOutfitsMock).toHaveBeenCalledTimes(1)
  })

  it('skips a since-deleted piece, renders the survivors, and notes the loss (Q3-C)', async () => {
    listOutfitsMock.mockResolvedValue([outfit('o1', ['a', 'ghost'], 'ai', 'A crisp look')])
    listItemsMock.mockResolvedValue([item('a')]) // 'ghost' was deleted from the wardrobe

    renderPage()

    // The card renders without crashing, showing only the surviving piece.
    expect(await screen.findByTestId('outfit-card')).toBeInTheDocument()
    const imgs = screen.getAllByRole('img')
    expect(imgs).toHaveLength(1)
    expect(imgs[0]).toHaveAttribute('src', '/api/items/a/photo')

    // And a quiet note explains the missing piece.
    expect(screen.getByText(/1 piece.*no longer in your wardrobe/i)).toBeInTheDocument()
  })

  it('still renders the card with the note when every piece is gone (Q3-C)', async () => {
    listOutfitsMock.mockResolvedValue([outfit('o1', ['ghost1', 'ghost2'], 'manual', null)])
    listItemsMock.mockResolvedValue([]) // both pieces deleted from the wardrobe

    renderPage()

    expect(await screen.findByTestId('outfit-card')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByText(/2 pieces.*no longer in your wardrobe/i)).toBeInTheDocument()
  })

  it('keeps the card with a retryable error when remove fails', async () => {
    listOutfitsMock.mockResolvedValue([outfit('o1', ['a'])])
    listItemsMock.mockResolvedValue([item('a')])
    deleteOutfitMock.mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()

    renderPage()

    await user.click(await screen.findByRole('button', { name: /remove/i }))

    expect(await screen.findByText(/couldn.t remove/i)).toBeInTheDocument()
    expect(screen.getByTestId('outfit-card')).toBeInTheDocument()
  })
})
