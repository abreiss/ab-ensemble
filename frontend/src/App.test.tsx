import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import App from './App'
import type { Item } from './types/item'

const stubItem: Item = {
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
}

// Keep the routed screens off the network; this suite only proves routing + shell.
vi.mock('./api/items', () => ({
  listItems: vi.fn().mockResolvedValue([]),
  getItem: vi.fn().mockResolvedValue(stubItem),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('App shell + routing', () => {
  it('mounts the wardrobe grid at /', async () => {
    renderAt('/')
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

  it('exposes a persistent add-item navigation control', () => {
    renderAt('/')
    expect(screen.getByRole('link', { name: /add/i })).toHaveAttribute('href', '/add')
  })
})
