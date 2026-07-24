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
  it('renders the login screen (not the children) when no token is stored', () => {
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    expect(screen.getByLabelText(/^username$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('toggles to the sign-up form and back to login', async () => {
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.click(screen.getByRole('button', { name: /sign up/i }))

    expect(screen.getByLabelText(/^username$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/signup code/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(screen.queryByLabelText(/signup code/i)).not.toBeInTheDocument()
  })

  it('stores the token and renders children after a successful login', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ token: 'tok-123' }))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.type(screen.getByLabelText(/^username$/i), 'jane_doe')
    await user.type(screen.getByLabelText(/^password$/i), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText('secret content')).toBeInTheDocument()
  })

  it('stores the token and renders children after a successful sign-up', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ token: 'tok-456' }, 201))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.click(screen.getByRole('button', { name: /sign up/i }))
    await user.type(screen.getByLabelText(/^username$/i), 'new_user')
    await user.type(screen.getByLabelText(/^password$/i), 'a-strong-password')
    await user.type(screen.getByLabelText(/signup code/i), 'invite-code')
    await user.click(screen.getByRole('button', { name: /^sign up$/i }))

    expect(await screen.findByText('secret content')).toBeInTheDocument()
  })

  it('shows "Invalid username or password." for a login 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'unauthorized' }, 401))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.type(screen.getByLabelText(/^username$/i), 'jane_doe')
    await user.type(screen.getByLabelText(/^password$/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('shows "That username is already registered." for a signup 409', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'conflict' }, 409))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.click(screen.getByRole('button', { name: /sign up/i }))
    await user.type(screen.getByLabelText(/^username$/i), 'taken_user')
    await user.type(screen.getByLabelText(/^password$/i), 'a-strong-password')
    await user.type(screen.getByLabelText(/signup code/i), 'invite-code')
    await user.click(screen.getByRole('button', { name: /^sign up$/i }))

    expect(await screen.findByText('That username is already registered.')).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })

  it('shows "Enter a valid username and password." for a login 400', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'bad_request' }, 400))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.type(screen.getByLabelText(/^username$/i), 'jane_doe')
    await user.type(screen.getByLabelText(/^password$/i), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText('Enter a valid username and password.')).toBeInTheDocument()
  })

  it('shows "Check your username, password, and signup code." for a signup 400', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'bad_request' }, 400))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.click(screen.getByRole('button', { name: /sign up/i }))
    await user.type(screen.getByLabelText(/^username$/i), 'new_user')
    await user.type(screen.getByLabelText(/^password$/i), 'short')
    await user.type(screen.getByLabelText(/signup code/i), 'invite-code')
    await user.click(screen.getByRole('button', { name: /^sign up$/i }))

    expect(
      await screen.findByText('Check your username, password, and signup code.'),
    ).toBeInTheDocument()
  })

  it('shows "That signup code isn\'t valid." for a signup 401', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ error: 'unauthorized' }, 401))
    const user = userEvent.setup()
    render(
      <AuthGate>
        <div>secret content</div>
      </AuthGate>,
    )

    await user.click(screen.getByRole('button', { name: /sign up/i }))
    await user.type(screen.getByLabelText(/^username$/i), 'new_user')
    await user.type(screen.getByLabelText(/^password$/i), 'a-strong-password')
    await user.type(screen.getByLabelText(/signup code/i), 'wrong-code')
    await user.click(screen.getByRole('button', { name: /^sign up$/i }))

    expect(await screen.findByText("That signup code isn't valid.")).toBeInTheDocument()
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
    await user.type(screen.getByLabelText(/^username$/i), 'jane_doe')
    await user.type(screen.getByLabelText(/^password$/i), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: /log in/i }))
    expect(await screen.findByText('secret content')).toBeInTheDocument()

    fetchMock.mockResolvedValue(jsonResponse({}, 401))
    await act(async () => {
      await authedFetch('/api/items')
    })

    expect(await screen.findByLabelText(/^username$/i)).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })
})
