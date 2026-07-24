import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearToken, getToken, login, signup } from './auth'

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
    it('POSTs email/password as JSON to /api/auth and stores the returned token', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ token: 'abc.def' }))

      await login('jane@example.com', 'correct-horse-battery')

      const [url, init] = lastCall()
      expect(url).toBe('/api/auth')
      expect(init.method).toBe('POST')
      expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual({
        email: 'jane@example.com',
        password: 'correct-horse-battery',
      })
      expect(getToken()).toBe('abc.def')
    })

    it('throws with the response status and does not store a token on a 401', async () => {
      fetchMock.mockResolvedValue(
        jsonResponse({ error: 'unauthorized', message: 'authentication required' }, 401),
      )

      await expect(login('jane@example.com', 'wrong-password')).rejects.toMatchObject({
        status: 401,
      })
      expect(getToken()).toBeNull()
    })

    it('propagates a network/transport failure', async () => {
      fetchMock.mockRejectedValue(new TypeError('offline'))
      await expect(login('jane@example.com', 'correct-horse-battery')).rejects.toThrow()
      expect(getToken()).toBeNull()
    })
  })

  describe('signup', () => {
    it('POSTs email/password/passcode as JSON to /api/accounts and stores the returned token', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ token: 'new.tok' }, 201))

      await signup('new@example.com', 'a-strong-password', 'invite-code')

      const [url, init] = lastCall()
      expect(url).toBe('/api/accounts')
      expect(init.method).toBe('POST')
      expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual({
        email: 'new@example.com',
        password: 'a-strong-password',
        passcode: 'invite-code',
      })
      expect(getToken()).toBe('new.tok')
    })

    it('throws with the response status and does not store a token on a 409 (duplicate email)', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ error: 'conflict' }, 409))

      await expect(
        signup('taken@example.com', 'a-strong-password', 'invite-code'),
      ).rejects.toMatchObject({ status: 409 })
      expect(getToken()).toBeNull()
    })

    it('throws with the response status and does not store a token on a 400 (invalid input)', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ error: 'bad_request' }, 400))

      await expect(signup('new@example.com', 'short', 'invite-code')).rejects.toMatchObject({
        status: 400,
      })
      expect(getToken()).toBeNull()
    })

    it('throws with the response status and does not store a token on a 401 (wrong signup code)', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ error: 'unauthorized' }, 401))

      await expect(signup('new@example.com', 'a-strong-password', 'wrong-code')).rejects.toMatchObject(
        { status: 401 },
      )
      expect(getToken()).toBeNull()
    })

    it('propagates a network/transport failure', async () => {
      fetchMock.mockRejectedValue(new TypeError('offline'))
      await expect(
        signup('new@example.com', 'a-strong-password', 'invite-code'),
      ).rejects.toThrow()
      expect(getToken()).toBeNull()
    })
  })

  describe('getToken / clearToken', () => {
    it('returns null when no token is stored', () => {
      expect(getToken()).toBeNull()
    })

    it('clearToken removes a stored token', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ token: 'xyz' }))
      await login('jane@example.com', 'correct-horse-battery')
      expect(getToken()).toBe('xyz')

      clearToken()

      expect(getToken()).toBeNull()
    })
  })
})
