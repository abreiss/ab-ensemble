import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearToken, getToken, login } from './auth'

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

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('auth API client', () => {
  describe('login', () => {
    it('POSTs the passcode as JSON to /api/auth and stores the returned token', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ token: 'abc.def' }))

      await login('correct-horse')

      const [url, init] = lastCall()
      expect(url).toBe('/api/auth')
      expect(init.method).toBe('POST')
      expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual({ passcode: 'correct-horse' })
      expect(getToken()).toBe('abc.def')
    })

    it('throws and does not store a token on a 401', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ error: 'unauthorized', message: 'authentication required' }, 401),
      )

      await expect(login('wrong')).rejects.toThrow()
      expect(getToken()).toBeNull()
    })
  })

  describe('getToken / clearToken', () => {
    it('returns null when no token is stored', () => {
      expect(getToken()).toBeNull()
    })

    it('clearToken removes a stored token', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ token: 'xyz' }))
      await login('correct-horse')
      expect(getToken()).toBe('xyz')

      clearToken()

      expect(getToken()).toBeNull()
    })
  })
})
