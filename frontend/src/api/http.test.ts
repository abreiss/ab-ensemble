import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { getToken, login } from './auth'
import { authedFetch, onAuthRequired } from './http'

/** Builds a fetch-like Response stub (mirrors items.test.ts). */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

let fetchMock: ReturnType<typeof vi.fn>

/** Reads the (url, init) of the most recent fetch call. */
function lastCall(): [string, RequestInit] {
  const call = fetchMock.mock.calls.at(-1)
  return [call![0] as string, (call![1] ?? {}) as RequestInit]
}

/** Seeds a session token via the real `login` flow rather than poking storage directly. */
async function seedToken(token: string): Promise<void> {
  fetchMock.mockResolvedValueOnce(jsonResponse({ token }))
  await login('any-passcode')
}

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('authedFetch', () => {
  it('injects the stored token as X-Ensemble-Session', async () => {
    await seedToken('tok-123')
    fetchMock.mockResolvedValue(jsonResponse({}))

    await authedFetch('/api/items')

    const [, init] = lastCall()
    expect((init.headers as Headers).get('X-Ensemble-Session')).toBe('tok-123')
  })

  it('omits the header when no token is stored', async () => {
    fetchMock.mockResolvedValue(jsonResponse({}))

    await authedFetch('/api/items')

    const [, init] = lastCall()
    expect((init.headers as Headers).get('X-Ensemble-Session')).toBeNull()
  })

  it('preserves caller-supplied method/headers alongside the injected token', async () => {
    await seedToken('tok-123')
    fetchMock.mockResolvedValue(jsonResponse({}))

    await authedFetch('/api/items', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    })

    const [, init] = lastCall()
    expect(init.method).toBe('POST')
    expect((init.headers as Headers).get('Content-Type')).toBe('application/json')
    expect((init.headers as Headers).get('X-Ensemble-Session')).toBe('tok-123')
  })

  it('clears the token and fires the re-auth signal on a 401', async () => {
    await seedToken('tok-123')
    fetchMock.mockResolvedValue(jsonResponse({}, 401))
    const listener = vi.fn()
    const unsubscribe = onAuthRequired(listener)

    const response = await authedFetch('/api/items')

    expect(response.status).toBe(401)
    expect(getToken()).toBeNull()
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
  })

  it('does not clear the token or fire the re-auth signal on a non-401 response', async () => {
    await seedToken('tok-123')
    fetchMock.mockResolvedValue(jsonResponse({}, 500))
    const listener = vi.fn()
    const unsubscribe = onAuthRequired(listener)

    await authedFetch('/api/items')

    expect(getToken()).toBe('tok-123')
    expect(listener).not.toHaveBeenCalled()
    unsubscribe()
  })
})

describe('onAuthRequired', () => {
  it('returns an unsubscribe function that stops future notifications', async () => {
    const listener = vi.fn()
    const unsubscribe = onAuthRequired(listener)
    unsubscribe()

    fetchMock.mockResolvedValue(jsonResponse({}, 401))
    await authedFetch('/api/items')

    expect(listener).not.toHaveBeenCalled()
  })
})
