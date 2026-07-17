import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import AuthGate from './AuthGate'
import { authedFetch } from '../api/http'

/** Builds a fetch-like Response stub (mirrors api/items.test.ts). */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AuthGate', () => {
  it('renders the passcode screen (not the children) when no token is stored', () => {
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    expect(screen.getByLabelText(/passcode/i)).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('stores the token and renders children after a correct passcode', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ token: 'tok-123' }))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.type(screen.getByLabelText(/passcode/i), 'right-passcode')
    await user.click(screen.getByRole('button', { name: /unlock/i }))

    expect(await screen.findByText('secret content')).toBeInTheDocument()
  })

  it('shows an inline error and stays on the gate for a wrong passcode', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ error: 'unauthorized', message: 'authentication required' }, 401),
    )
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.type(screen.getByLabelText(/passcode/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /unlock/i }))

    expect(await screen.findByText(/incorrect passcode/i)).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('returns to the gate when an authenticated request comes back 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ token: 'tok-123' }))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )
    await user.type(screen.getByLabelText(/passcode/i), 'right-passcode')
    await user.click(screen.getByRole('button', { name: /unlock/i }))
    expect(await screen.findByText('secret content')).toBeInTheDocument()

    fetchMock.mockResolvedValue(jsonResponse({}, 401))
    await act(async () => {
      await authedFetch('/api/items')
    })

    expect(await screen.findByLabelText(/passcode/i)).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })
})
