import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import Stylist from './Stylist'
import type { Outfit, OutfitItem } from '../api/style'

// The route must never touch the network in tests; mock both API modules. The
// wardrobe drawer reads `listItems()`; keep it empty so the only images on screen
// are the outfit's flat-lay tiles.
vi.mock('../api/style', () => ({
  requestStyle: vi.fn(),
  markWorn: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

vi.mock('../api/items', () => ({
  listItems: vi.fn().mockResolvedValue([]),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { markWorn, requestStyle } from '../api/style'

const requestStyleMock = vi.mocked(requestStyle)
const markWornMock = vi.mocked(markWorn)

/** Builds an enriched OutfitItem with renderable defaults (overridable). */
function outfitItem(id: string, over: Partial<OutfitItem> = {}): OutfitItem {
  return {
    itemId: id,
    photoUrl: `/api/items/${id}/photo`,
    rationale: '',
    category: null,
    primaryColor: null,
    formality: null,
    warmth: null,
    descriptors: null,
    ...over,
  }
}

const outfit: Outfit = {
  itemIds: ['a', 'b'],
  reason: 'A navy top over slim denim reads clean and modern.',
  items: [outfitItem('a'), outfitItem('b')],
}

// A second, different look returned by a re-pick.
const outfit2: Outfit = {
  itemIds: ['c', 'd'],
  reason: 'Swapped in a bolder jacket and darker denim for more edge.',
  items: [outfitItem('c'), outfitItem('d')],
}

// A third distinct look, so a stream assertion never collides with an earlier reason.
const outfit3: Outfit = {
  itemIds: ['e', 'f'],
  reason: 'Leaned into softer tones for a calmer, more colorful finish.',
  items: [outfitItem('e'), outfitItem('f')],
}

const emptyLook: Outfit = {
  itemIds: [],
  reason: 'Add a few more pieces and I can build you a look.',
  items: [],
}

/** The assistant summary the route commits to the thread after a pick renders. */
function summaryOf(o: Outfit): string {
  return `Previously chose: ${o.itemIds.join(', ')} — ${o.reason}`
}

function renderStylist() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Stylist />
    </MemoryRouter>,
  )
}

/** Types a vibe into the composer and sends it. */
async function submitVibe(user: ReturnType<typeof userEvent.setup>, vibe = 'streetwear today') {
  await user.type(screen.getByRole('textbox', { name: /message the stylist/i }), vibe)
  await user.click(screen.getByRole('button', { name: /send/i }))
}

/** Submits a vibe and waits for the outfit to render. */
async function renderLook(user: ReturnType<typeof userEvent.setup>) {
  requestStyleMock.mockResolvedValueOnce(outfit)
  renderStylist()
  await submitVibe(user)
  await screen.findByText(outfit.reason)
}

beforeEach(() => {
  requestStyleMock.mockReset()
  markWornMock.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('Stylist route', () => {
  it('sends the vibe, shows a thinking state, then renders the look with photos + reason', async () => {
    let resolve!: (o: Outfit) => void
    requestStyleMock.mockReturnValue(
      new Promise<Outfit>((r) => {
        resolve = r
      }),
    )
    const user = userEvent.setup()

    renderStylist()
    await submitVibe(user)

    // A thinking state appears while the request is in flight.
    expect(screen.getByText(/styling/i)).toBeInTheDocument()

    resolve(outfit)

    // The reason renders in the stream, and each id renders its real flat-lay photo.
    expect(await screen.findByText(outfit.reason)).toBeInTheDocument()
    const photos = screen.getAllByRole('img')
    expect(photos).toHaveLength(2)
    expect(photos[0]).toHaveAttribute('src', '/api/items/a/photo')
    expect(requestStyleMock).toHaveBeenCalledWith('streetwear today', [])
  })

  it('appends both the user turn and the assistant reply to the stream', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    // Scrollback grows: the user's vibe and the stylist's reason both remain visible.
    expect(screen.getByText('streetwear today')).toBeInTheDocument()
    expect(screen.getByText(outfit.reason)).toBeInTheDocument()
  })

  it('fires a styling turn from a quick-start chip', async () => {
    requestStyleMock.mockResolvedValueOnce(outfit)
    const user = userEvent.setup()

    renderStylist()
    await user.click(screen.getByRole('button', { name: /^brunch$/i }))

    await screen.findByText(outfit.reason)
    expect(requestStyleMock).toHaveBeenCalledWith('Brunch', [])
  })

  it('fires a styling turn from an adjust chip, carrying the thread', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    requestStyleMock.mockResolvedValueOnce(outfit2)
    await user.click(screen.getByRole('button', { name: /^warmer$/i }))

    await screen.findByText(outfit2.reason)
    expect(requestStyleMock).toHaveBeenLastCalledWith('Warmer', [
      { role: 'user', text: 'streetwear today' },
      { role: 'assistant', text: summaryOf(outfit) },
    ])
  })

  it('shows a non-crashing error state with a retry that replays the same turn', async () => {
    requestStyleMock.mockRejectedValueOnce(new Error('upstream down'))
    const user = userEvent.setup()

    renderStylist()
    await submitVibe(user)

    expect(await screen.findByText(/couldn.t style/i)).toBeInTheDocument()

    // Retry succeeds → the look renders without re-typing the vibe.
    requestStyleMock.mockResolvedValueOnce(outfit)
    await user.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(screen.getByText(outfit.reason)).toBeInTheDocument())
    expect(requestStyleMock).toHaveBeenCalledTimes(2)
    expect(requestStyleMock).toHaveBeenLastCalledWith('streetwear today', [])
  })

  it('shows a friendly empty state (no photos) when the wardrobe is too small', async () => {
    requestStyleMock.mockResolvedValueOnce(emptyLook)
    const user = userEvent.setup()

    renderStylist()
    await submitVibe(user)

    expect(await screen.findByText(emptyLook.reason)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('logs a worn look: marks every rendered piece worn and locks to "Logged ✓"', async () => {
    const user = userEvent.setup()
    await renderLook(user)
    markWornMock.mockResolvedValue({} as never)

    await user.click(screen.getByRole('button', { name: /wear today/i }))

    // One write per rendered piece, exactly once each.
    await waitFor(() => expect(markWornMock).toHaveBeenCalledTimes(2))
    expect(markWornMock).toHaveBeenCalledWith('a')
    expect(markWornMock).toHaveBeenCalledWith('b')

    // The control locks to a one-shot "Logged ✓" state.
    const logged = await screen.findByRole('button', { name: /logged/i })
    expect(logged).toBeDisabled()
    expect(screen.queryByRole('button', { name: /wear today/i })).not.toBeInTheDocument()
  })

  it('keeps the look and shows a retryable message when a wear write fails', async () => {
    const user = userEvent.setup()
    await renderLook(user)
    // First piece succeeds, second rejects → a partial failure.
    markWornMock.mockResolvedValueOnce({} as never).mockRejectedValueOnce(new Error('offline'))

    await user.click(screen.getByRole('button', { name: /wear today/i }))

    expect(await screen.findByText(/couldn.t log|try again/i)).toBeInTheDocument()
    expect(screen.getByText(outfit.reason)).toBeInTheDocument()
    // Not locked — the user can retry the log.
    expect(screen.getByRole('button', { name: /wear today/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /logged/i })).not.toBeInTheDocument()
  })

  it('reveals the adjust chips and "Show me another" once a look renders', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    expect(screen.getByRole('button', { name: /show me another/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^warmer$/i })).toBeInTheDocument()
  })

  it('re-picks on composer pushback: POSTs the feedback with the prior thread', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    requestStyleMock.mockResolvedValueOnce(outfit2)
    await submitVibe(user, 'too plain')

    // The new, different look renders.
    expect(await screen.findByText(outfit2.reason)).toBeInTheDocument()
    const photos = screen.getAllByRole('img')
    expect(photos).toHaveLength(2)
    expect(photos[0]).toHaveAttribute('src', '/api/items/c/photo')

    expect(requestStyleMock).toHaveBeenLastCalledWith('too plain', [
      { role: 'user', text: 'streetwear today' },
      { role: 'assistant', text: summaryOf(outfit) },
    ])
  })

  it('regenerates: "Show me another" POSTs that user turn with the full thread', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    requestStyleMock.mockResolvedValueOnce(outfit2)
    await user.click(screen.getByRole('button', { name: /show me another/i }))

    expect(await screen.findByText(outfit2.reason)).toBeInTheDocument()
    const [prompt, history] = requestStyleMock.mock.calls.at(-1)!
    expect(prompt).toMatch(/show me another/i)
    expect(history).toEqual([
      { role: 'user', text: 'streetwear today' },
      { role: 'assistant', text: summaryOf(outfit) },
    ])
  })

  it('threads across two re-picks: the second carries both prior picks', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    // First re-pick.
    requestStyleMock.mockResolvedValueOnce(outfit2)
    await user.click(screen.getByRole('button', { name: /show me another/i }))
    await screen.findByText(outfit2.reason)

    // Second re-pick carries the accumulated four-turn thread.
    requestStyleMock.mockResolvedValueOnce(outfit3)
    await submitVibe(user, 'more color')
    await screen.findByText(outfit3.reason)

    const [, history] = requestStyleMock.mock.calls.at(-1)!
    expect(history).toHaveLength(4)
    expect(history).toEqual([
      { role: 'user', text: 'streetwear today' },
      { role: 'assistant', text: summaryOf(outfit) },
      { role: 'user', text: expect.stringMatching(/show me another/i) },
      { role: 'assistant', text: summaryOf(outfit2) },
    ])
  })

  it('disables the re-pick controls while a re-pick is in flight (prior look stays)', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    let resolve!: (o: Outfit) => void
    requestStyleMock.mockReturnValueOnce(
      new Promise<Outfit>((r) => {
        resolve = r
      }),
    )
    await user.click(screen.getByRole('button', { name: /show me another/i }))

    // Controls are disabled and the previous look is still on screen.
    expect(screen.getByRole('button', { name: /show me another/i })).toBeDisabled()
    expect(screen.getByRole('textbox', { name: /message the stylist/i })).toBeDisabled()
    expect(screen.getByText(outfit.reason)).toBeInTheDocument()

    resolve(outfit2)
    expect(await screen.findByText(outfit2.reason)).toBeInTheDocument()
  })

  it('preserves the error-with-retry state on a failed re-pick and replays the thread', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    requestStyleMock.mockRejectedValueOnce(new Error('upstream down'))
    await user.click(screen.getByRole('button', { name: /show me another/i }))

    expect(await screen.findByText(/couldn.t style/i)).toBeInTheDocument()

    // Retry replays the same re-pick (same newest text + history) and renders the look.
    requestStyleMock.mockResolvedValueOnce(outfit2)
    await user.click(screen.getByRole('button', { name: /try again/i }))
    expect(await screen.findByText(outfit2.reason)).toBeInTheDocument()

    const [prompt, history] = requestStyleMock.mock.calls.at(-1)!
    expect(prompt).toMatch(/show me another/i)
    expect(history).toEqual([
      { role: 'user', text: 'streetwear today' },
      { role: 'assistant', text: summaryOf(outfit) },
    ])
  })

  it('blocks a re-pick while a wear-log is in flight (no state race onto the next look)', async () => {
    const user = userEvent.setup()
    await renderLook(user)
    // A wear-log that never settles keeps logStatus in "logging".
    markWornMock.mockReturnValue(new Promise<never>(() => {}))

    await user.click(screen.getByRole('button', { name: /wear today/i }))

    expect(screen.getByRole('button', { name: /show me another/i })).toBeDisabled()
    expect(screen.getByRole('textbox', { name: /message the stylist/i })).toBeDisabled()
  })

  it('blocks logging while a re-pick is in flight', async () => {
    const user = userEvent.setup()
    await renderLook(user)
    // A re-pick that never settles keeps status in "loading".
    requestStyleMock.mockReturnValueOnce(new Promise<Outfit>(() => {}))

    await user.click(screen.getByRole('button', { name: /show me another/i }))

    expect(screen.getByRole('button', { name: /wear today/i })).toBeDisabled()
  })

  it('shows the empty state if a re-pick returns nothing to style', async () => {
    const user = userEvent.setup()
    await renderLook(user)

    requestStyleMock.mockResolvedValueOnce(emptyLook)
    await user.click(screen.getByRole('button', { name: /show me another/i }))

    expect(await screen.findByText(emptyLook.reason)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
