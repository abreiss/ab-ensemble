import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { SESSION_TOKEN_STORAGE_KEY } from './api/auth'

// Keep the routed screens off the network; this suite only proves routing + shell.
// The stub item is inlined in the factory because `vi.mock` is hoisted above the
// module body — referencing an outer const here would hit its temporal dead zone.
vi.mock('./api/items', () => ({
  listItems: vi.fn().mockResolvedValue([]),
  getItem: vi.fn().mockResolvedValue({
    itemId: 'abc',
    category: 'top',
    primaryColor: 'navy',
    secondaryColor: null,
    formality: 3,
    pattern: null,
    warmth: 2,
    descriptors: null,
    photoUrl: '/api/items/abc/photo',
    createdAt: '2026-01-01T00:00:00Z',
    lastWorn: null,
    wornCount: 0,
  }),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

// The Saved Outfits screen fetches saved outfits on mount; keep it off the network.
vi.mock('./api/outfits', () => ({
  listOutfits: vi.fn().mockResolvedValue([]),
  deleteOutfit: vi.fn().mockResolvedValue(undefined),
}))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('App shell + routing', () => {
  // This suite only proves routing + shell, so a token is pre-seeded to keep
  // AuthGate (spec 07) out of the way; auth-gate behavior itself is covered by
  // AuthGate.test.tsx.
  beforeEach(() => {
    sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, 'test-token')
  })

  afterEach(() => {
    sessionStorage.clear()
  })

  it('mounts the stylist screen at / (the landing route)', async () => {
    renderAt('/')
    expect(await screen.findByTestId('stylist')).toBeInTheDocument()
  })

  it('mounts the wardrobe grid at /wardrobe', async () => {
    renderAt('/wardrobe')
    expect(await screen.findByTestId('wardrobe-grid')).toBeInTheDocument()
  })

  it('mounts the add-item screen at /add', async () => {
    renderAt('/add')
    expect(await screen.findByTestId('add-item')).toBeInTheDocument()
  })

  it('mounts the item-detail screen at /item/:id', async () => {
    renderAt('/item/abc')
    expect(await screen.findByTestId('item-detail')).toBeInTheDocument()
  })

  it('mounts the assemble screen at /assemble', async () => {
    renderAt('/assemble')
    expect(await screen.findByTestId('assemble')).toBeInTheDocument()
  })

  it('mounts the saved-outfits screen at /saved', async () => {
    renderAt('/saved')
    expect(await screen.findByTestId('saved-outfits')).toBeInTheDocument()
  })

  it('redirects the legacy /style route to the stylist landing at /', async () => {
    renderAt('/style')
    expect(await screen.findByTestId('stylist')).toBeInTheDocument()
  })

  it('exposes a persistent add-item navigation control', () => {
    renderAt('/')
    expect(screen.getByRole('link', { name: /add/i })).toHaveAttribute('href', '/add')
  })

  it('exposes a persistent wardrobe navigation control', () => {
    renderAt('/')
    expect(screen.getByRole('link', { name: /wardrobe/i })).toHaveAttribute('href', '/wardrobe')
  })

  it('exposes a persistent build-your-own navigation control in the header, before Wardrobe', () => {
    renderAt('/')
    // Scope to the header (role="banner") so the Stylist screen's own
    // "Build it yourself" link doesn't collide with this assertion.
    const header = screen.getByRole('banner')
    const buildLink = within(header).getByRole('link', { name: /build/i })
    expect(buildLink).toHaveAttribute('href', '/assemble')

    // It must sit to the LEFT of (before) the Wardrobe control in DOM order.
    const wardrobeLink = within(header).getByRole('link', { name: /wardrobe/i })
    expect(buildLink.compareDocumentPosition(wardrobeLink)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })

  it('exposes a persistent saved-outfits navigation control in the header, before Build', () => {
    renderAt('/')
    const header = screen.getByRole('banner')
    const savedLink = within(header).getByRole('link', { name: /saved/i })
    expect(savedLink).toHaveAttribute('href', '/saved')

    // It must sit to the LEFT of (before) the Build control in DOM order.
    const buildLink = within(header).getByRole('link', { name: /build/i })
    expect(savedLink.compareDocumentPosition(buildLink)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
  })
})

describe('App shell — /assemble is gated', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('shows the login screen instead of /assemble when no session token is stored', async () => {
    renderAt('/assemble')
    expect(await screen.findByLabelText(/^username$/i)).toBeInTheDocument()
    expect(screen.queryByTestId('assemble')).not.toBeInTheDocument()
  })

  it('shows the login screen instead of /saved when no session token is stored', async () => {
    renderAt('/saved')
    expect(await screen.findByLabelText(/^username$/i)).toBeInTheDocument()
    expect(screen.queryByTestId('saved-outfits')).not.toBeInTheDocument()
  })
})
