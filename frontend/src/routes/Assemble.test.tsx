import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DndContextProps, DragEndEvent } from '@dnd-kit/core'

import Assemble from './Assemble'
import type { Slot } from '../lib/specSheet'
import type { Item } from '../types/item'

// The screen must never touch the network in tests; mock the items API module.
vi.mock('../api/items', () => ({
  listItems: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { listItems } from '../api/items'

const listItemsMock = vi.mocked(listItems)

// jsdom cannot simulate a real dnd-kit drag (PointerSensor needs real pointer
// events + layout measurement it doesn't implement — see the spec's Technical
// Considerations). So `@dnd-kit/core` is mocked here: `useDraggable` /
// `useDroppable` become inert (the tiles/zones still render, they just don't
// need a live DndContext), and `DndContext` is replaced with a stand-in that
// captures the real `onDragEnd` callback the component wires up, so a test can
// invoke it directly with a synthetic drop — this is the "component test
// exercising the drop handler" the spec's Unit 2 proof artifacts call for.
const dragEndRef = vi.hoisted(() => ({
  current: undefined as ((event: DragEndEvent) => void) | undefined,
}))

vi.mock('@dnd-kit/core', async () => {
  const actual = await vi.importActual<typeof import('@dnd-kit/core')>('@dnd-kit/core')
  return {
    ...actual,
    useDraggable: () => ({
      attributes: {},
      listeners: {},
      setNodeRef: () => {},
      isDragging: false,
    }),
    useDroppable: () => ({ setNodeRef: () => {}, isOver: false }),
    DndContext: ({ onDragEnd, children }: DndContextProps) => {
      dragEndRef.current = onDragEnd
      return <>{children}</>
    },
  }
})

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

function renderAssemble() {
  return render(
    <MemoryRouter initialEntries={['/assemble']}>
      <Assemble />
    </MemoryRouter>,
  )
}

/**
 * Builds a synthetic dnd-kit `DragEndEvent` for the wiring tests below. Typed
 * against the real `DragEndEvent` (imported from `@dnd-kit/core`) rather than
 * a hand-rolled `{active, over}` shape, so a future dnd-kit upgrade that
 * changes the event shape fails this test at *compile* time instead of
 * silently passing a stale synthetic payload (approved FLAG-1 hardening from
 * the planning audit).
 */
function dragEndEvent(activeId: string, category: string | null, overSlot: Slot | null): DragEndEvent {
  return {
    activatorEvent: new Event('pointerup'),
    active: {
      id: activeId,
      data: { current: { category } },
      rect: { current: { initial: null, translated: null } },
    },
    collisions: null,
    delta: { x: 0, y: 0 },
    over:
      overSlot === null
        ? null
        : {
            id: overSlot,
            data: { current: { slot: overSlot } },
            disabled: false,
            rect: { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 },
          },
  }
}

beforeEach(() => {
  listItemsMock.mockReset()
  dragEndRef.current = undefined
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('Assemble route', () => {
  it('shows an empty-wardrobe state inviting a first add when there are no items', async () => {
    listItemsMock.mockResolvedValue([])

    renderAssemble()

    expect(await screen.findByRole('heading', { name: /no items yet/i })).toBeInTheDocument()
    const addLink = screen.getByRole('link', { name: /add/i })
    expect(addLink).toHaveAttribute('href', '/add')
    expect(addLink).toHaveClass('btn', 'btn-primary')
    expect(screen.getByTestId('assemble')).toHaveClass('screen')
  })

  it('shows a non-crashing error state with a retry that re-fetches on list failure', async () => {
    listItemsMock.mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()

    renderAssemble()

    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument()

    listItemsMock.mockResolvedValueOnce([item('a')])
    await user.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => expect(listItemsMock).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/couldn.t load/i)).not.toBeInTheDocument()
  })

  it('renders the mannequin with four labeled, Slot-keyed drop zones when items exist', async () => {
    listItemsMock.mockResolvedValue([item('a')])

    renderAssemble()

    expect(await screen.findByText(/^top$/i)).toBeInTheDocument()
    expect(screen.getByText(/^bottom$/i)).toBeInTheDocument()
    expect(screen.getByText(/^shoes$/i)).toBeInTheDocument()
    expect(screen.getByText(/^extras$/i)).toBeInTheDocument()
  })
})

describe('Assemble drag-and-drop wiring', () => {
  it('routes a dropped item to its target zone and replaces the prior occupant', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt'), item('shirt-2', 'shirt')])

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })

    const topZone = document.querySelector('[data-slot="TOP"]') as HTMLElement
    expect(topZone).not.toBeNull()
    expect(within(topZone).getByRole('img')).toHaveAttribute('src', '/api/items/shirt-1/photo')

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-2', 'shirt', 'TOP'))
    })

    // shirt-2 replaced shirt-1 in the single-occupancy TOP zone — exactly one
    // placed tile remains there, and it is the new occupant.
    expect(within(topZone).getByRole('img')).toHaveAttribute('src', '/api/items/shirt-2/photo')
  })

  it('accumulates multiple items in the CARRY/PIECE extras tray instead of replacing', async () => {
    listItemsMock.mockResolvedValue([item('bag-1', 'bag'), item('hat-1', 'hat')])

    renderAssemble()
    await screen.findByText(/^extras$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('bag-1', 'bag', 'CARRY'))
    })
    act(() => {
      dragEndRef.current?.(dragEndEvent('hat-1', 'hat', 'CARRY'))
    })

    const extrasZone = document.querySelector('[data-slot="CARRY"]') as HTMLElement
    expect(within(extrasZone).getAllByRole('img')).toHaveLength(2)
  })

  it('ignores a drag that ends without a valid drop target', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt')])

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', null))
    })

    const topZone = document.querySelector('[data-slot="TOP"]') as HTMLElement
    expect(within(topZone).queryByRole('img')).not.toBeInTheDocument()
  })
})
