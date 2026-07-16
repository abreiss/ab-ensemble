import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { photoUrl, requestStyle } from './style'
import type { Outfit, StyleTurn } from './style'

/** Builds a fetch-like Response stub (mirrors items.test.ts). */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const sampleOutfit: Outfit = {
  itemIds: ['a', 'b'],
  reason: 'A navy top over slim denim reads clean and modern.',
  items: [
    { itemId: 'a', photoUrl: '/api/items/a/photo' },
    { itemId: 'b', photoUrl: '/api/items/b/photo' },
  ],
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
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('style API client', () => {
  describe('requestStyle', () => {
    it('POSTs the prompt as JSON to /api/style and returns the parsed outfit', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleOutfit))

      const outfit = await requestStyle('streetwear today')

      const [url, init] = lastCall()
      expect(url).toBe('/api/style')
      expect(init.method).toBe('POST')
      expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual({ prompt: 'streetwear today' })
      expect(outfit).toEqual(sampleOutfit)
    })

    it('POSTs the accumulated history alongside the prompt when re-picking', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleOutfit))
      const history: StyleTurn[] = [
        { role: 'user', text: 'streetwear today' },
        { role: 'assistant', text: 'Previously chose: a, b — clean and modern.' },
      ]

      await requestStyle('too plain', history)

      const [url, init] = lastCall()
      expect(url).toBe('/api/style')
      expect(init.method).toBe('POST')
      expect(JSON.parse(init.body as string)).toEqual({ prompt: 'too plain', history })
    })

    it('omits history from the body when none is given (backward compatible)', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleOutfit))

      await requestStyle('streetwear today', [])

      const [, init] = lastCall()
      const body = JSON.parse(init.body as string)
      expect(body).toEqual({ prompt: 'streetwear today' })
      expect('history' in body).toBe(false)
    })

    it('returns an empty-wardrobe outfit (empty itemIds + explanatory reason) unchanged', async () => {
      const empty: Outfit = { itemIds: [], reason: 'Add a few pieces first.', items: [] }
      fetchMock.mockResolvedValue(jsonResponse(empty))

      const outfit = await requestStyle('anything')

      expect(outfit).toEqual(empty)
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 502))
      await expect(requestStyle('streetwear')).rejects.toThrow()
    })

    it('propagates a network/transport failure', async () => {
      fetchMock.mockRejectedValue(new TypeError('offline'))
      await expect(requestStyle('streetwear')).rejects.toThrow()
    })
  })

  describe('photoUrl', () => {
    it('re-exports the items photo-path builder for card rendering', () => {
      expect(photoUrl('a')).toBe('/api/items/a/photo')
    })
  })
})
