import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DndContextProps, DragEndEvent } from '@dnd-kit/core'

import Assemble from './Assemble'
import { SOURCE_DROPPABLE_ID } from '../components/AssembleSource'
import type { Slot } from '../lib/specSheet'
import type { Item } from '../types/item'

// The screen must never touch the network in tests; mock the items API module.
vi.mock('../api/items', () => ({
  listItems: vi.fn(),
  markWorn: vi.fn(),
  photoUrl: (id: string) => `/api/items/${id}/photo`,
}))

import { listItems, markWorn } from '../api/items'

const listItemsMock = vi.mocked(listItems)
const markWornMock = vi.mocked(markWorn)

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

/**
 * Builds a synthetic drag-back-to-source `DragEndEvent`: `over` is
 * `AssembleSource`'s `SOURCE_DROPPABLE_ID` droppable rather than a mannequin
 * zone, so its `data.current` carries no `slot` (that field only exists on
 * `Mannequin`'s zones — see `DroppableData` in `Assemble.tsx`).
 */
function dragBackToSourceEvent(activeId: string, category: string | null): DragEndEvent {
  return {
    activatorEvent: new Event('pointerup'),
    active: {
      id: activeId,
      data: { current: { category } },
      rect: { current: { initial: null, translated: null } },
    },
    collisions: null,
    delta: { x: 0, y: 0 },
    over: {
      id: SOURCE_DROPPABLE_ID,
      data: { current: {} },
      disabled: false,
      rect: { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 },
    },
  }
}

/**
 * Builds a synthetic `DragEndEvent` whose drop target carries no `data.slot`
 * at all — modeling `slotForCategory`'s default-routing path. Every real
 * mannequin zone always populates `data.slot` (see `DroppableData` in
 * `Assemble.tsx`), so this path is unreachable via today's UI; it exists so
 * an unrecognized `category` can be regression-tested routing through
 * `slotForCategory` to `PIECE`.
 */
function dragEndEventNoSlotOverride(activeId: string, category: string | null): DragEndEvent {
  return {
    activatorEvent: new Event('pointerup'),
    active: {
      id: activeId,
      data: { current: { category } },
      rect: { current: { initial: null, translated: null } },
    },
    collisions: null,
    delta: { x: 0, y: 0 },
    over: {
      id: 'unslotted-zone',
      data: { current: {} },
      disabled: false,
      rect: { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 },
    },
  }
}

beforeEach(() => {
  listItemsMock.mockReset()
  markWornMock.mockReset()
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

describe('Assemble remove/undo affordance', () => {
  it('removes a placed item via its tap "×" control and it reappears in the source list', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt')])
    const user = userEvent.setup()

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })

    const topZone = document.querySelector('[data-slot="TOP"]') as HTMLElement
    expect(within(topZone).getByRole('img')).toBeInTheDocument()
    // Placed — excluded from the source list.
    expect(document.querySelector('.assemble-source [data-item-id="shirt-1"]')).toBeNull()

    const removeButton = within(topZone).getByRole('button', { name: /remove item/i })
    // The ≥44px touch-target contract, asserted directly on the control.
    expect(removeButton).toHaveStyle({ minWidth: '44px', minHeight: '44px' })

    await user.click(removeButton)

    expect(within(topZone).queryByRole('img')).not.toBeInTheDocument()
    // Removed — back in the source list (excluded → included).
    expect(document.querySelector('.assemble-source [data-item-id="shirt-1"]')).not.toBeNull()
  })

  it('removes a placed item when onDragEnd reports it dropped back onto the source droppable', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt')])

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })

    const topZone = document.querySelector('[data-slot="TOP"]') as HTMLElement
    expect(within(topZone).getByRole('img')).toBeInTheDocument()

    act(() => {
      dragEndRef.current?.(dragBackToSourceEvent('shirt-1', 'shirt'))
    })

    expect(within(topZone).queryByRole('img')).not.toBeInTheDocument()
    expect(document.querySelector('.assemble-source [data-item-id="shirt-1"]')).not.toBeNull()
  })
})

describe('Assemble wear-today fan-out', () => {
  it('logs every placed item via markWorn exactly once and locks to "Logged ✓" on success', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt'), item('bag-1', 'bag')])
    markWornMock.mockResolvedValue(item('shirt-1', 'shirt'))
    const user = userEvent.setup()

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })
    act(() => {
      dragEndRef.current?.(dragEndEvent('bag-1', 'bag', 'CARRY'))
    })

    const wearButton = screen.getByRole('button', { name: /wear today/i })
    expect(wearButton).not.toBeDisabled()

    await user.click(wearButton)

    await waitFor(() => expect(markWornMock).toHaveBeenCalledTimes(2))
    expect(markWornMock).toHaveBeenCalledWith('shirt-1')
    expect(markWornMock).toHaveBeenCalledWith('bag-1')

    expect(await screen.findByRole('button', { name: /logged/i })).toBeDisabled()
  })

  it('surfaces a retryable error banner when a per-item markWorn write is rejected', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt')])
    markWornMock.mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })

    await user.click(screen.getByRole('button', { name: /wear today/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn.t log/i)
    // Still retryable — the primary action itself is not locked to "Logged ✓".
    expect(screen.getByRole('button', { name: /wear today/i })).not.toBeDisabled()
  })

  it('disables the "Wear today" action when nothing is placed', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt')])

    renderAssemble()
    await screen.findByText(/^top$/i)

    expect(screen.getByRole('button', { name: /wear today/i })).toBeDisabled()
    expect(markWornMock).not.toHaveBeenCalled()
  })

  it('re-enables "Wear today" (not the stuck "Logged ✓") once the outfit changes after logging', async () => {
    listItemsMock.mockResolvedValue([item('shirt-1', 'shirt'), item('bag-1', 'bag')])
    markWornMock.mockResolvedValue(item('shirt-1', 'shirt'))
    const user = userEvent.setup()

    renderAssemble()
    await screen.findByText(/^top$/i)

    act(() => {
      dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP'))
    })

    await user.click(screen.getByRole('button', { name: /wear today/i }))
    expect(await screen.findByRole('button', { name: /logged/i })).toBeDisabled()

    // Editing the assembled look after logging must release the lock so the
    // revised outfit can be logged too, instead of staying stuck on "Logged ✓".
    act(() => {
      dragEndRef.current?.(dragEndEvent('bag-1', 'bag', 'CARRY'))
    })

    expect(screen.queryByRole('button', { name: /logged/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /wear today/i })).not.toBeDisabled()
  })
})

describe('Assemble extras tray PIECE routing', () => {
  it('renders a PIECE-routed placement as a removable tile in the extras tray', async () => {
    listItemsMock.mockResolvedValue([item('mystery-1', 'monocle')])
    const user = userEvent.setup()

    renderAssemble()
    await screen.findByText(/^extras$/i)

    act(() => {
      dragEndRef.current?.(dragEndEventNoSlotOverride('mystery-1', 'monocle'))
    })

    const extrasZone = document.querySelector('[data-slot="CARRY"]') as HTMLElement
    expect(within(extrasZone).getByRole('img')).toHaveAttribute(
      'src',
      '/api/items/mystery-1/photo',
    )

    await user.click(within(extrasZone).getByRole('button', { name: /remove item/i }))
    expect(within(extrasZone).queryByRole('img')).not.toBeInTheDocument()
  })
})
