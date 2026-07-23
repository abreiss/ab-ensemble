import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { login } from './auth'
import { ApiError, deleteOutfit, listOutfits, saveOutfit } from './outfits'
import type { SavedOutfit } from '../types/outfit'

/** Builds a fetch-like Response stub. */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const sampleOutfit: SavedOutfit = {
  outfitId: 'o-1',
  itemIds: ['a', 'b'],
  source: 'ai',
  reason: 'A clean brunch look.',
  createdAt: '2026-01-01T00:00:00Z',
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
  await login('any@example.com', 'any-password')
}

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('outfits API client', () => {
  describe('saveOutfit', () => {
    it('POSTs JSON to /api/outfits and returns the saved outfit', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleOutfit, 201))

      const saved = await saveOutfit({
        itemIds: ['a', 'b'],
        source: 'ai',
        reason: 'A clean brunch look.',
      })

      const [url, init] = lastCall()
      expect(url).toBe('/api/outfits')
      expect(init.method).toBe('POST')
      expect((init.headers as Headers).get('Content-Type')).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual({
        itemIds: ['a', 'b'],
        source: 'ai',
        reason: 'A clean brunch look.',
      })
      expect(saved).toEqual(sampleOutfit)
    })

    it('omits reason from the body when it is not provided (manual look)', async () => {
      fetchMock.mockResolvedValue(jsonResponse({ ...sampleOutfit, source: 'manual', reason: null }, 201))

      await saveOutfit({ itemIds: ['a'], source: 'manual' })

      const body = JSON.parse(lastCall()[1].body as string)
      expect(body).toEqual({ itemIds: ['a'], source: 'manual' })
      expect('reason' in body).toBe(false)
    })

    it('sends the stored session token as X-Ensemble-Session', async () => {
      await seedToken('tok-abc')
      fetchMock.mockResolvedValue(jsonResponse(sampleOutfit, 201))

      await saveOutfit({ itemIds: ['a'], source: 'manual' })

      expect((lastCall()[1].headers as Headers).get('X-Ensemble-Session')).toBe('tok-abc')
    })

    it('rejects with a typed ApiError carrying the status on a grounding 400', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 400))

      const error = await saveOutfit({ itemIds: ['bogus'], source: 'ai' }).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(400)
    })

    it('propagates a network/transport failure', async () => {
      fetchMock.mockRejectedValue(new TypeError('offline'))
      await expect(saveOutfit({ itemIds: ['a'], source: 'ai' })).rejects.toThrow()
    })
  })

  describe('listOutfits', () => {
    it('GETs /api/outfits and returns the parsed array', async () => {
      fetchMock.mockResolvedValue(jsonResponse([sampleOutfit]))

      const outfits = await listOutfits()

      expect(lastCall()[0]).toBe('/api/outfits')
      expect(outfits).toEqual([sampleOutfit])
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 500))
      await expect(listOutfits()).rejects.toThrow()
    })
  })

  describe('deleteOutfit', () => {
    it('DELETEs /api/outfits/:id and resolves on 204', async () => {
      fetchMock.mockResolvedValue(jsonResponse(null, 204))

      await expect(deleteOutfit('o-1')).resolves.toBeUndefined()

      const [url, init] = lastCall()
      expect(url).toBe('/api/outfits/o-1')
      expect(init.method).toBe('DELETE')
    })

    it('throws on a non-2xx response (unknown id → 404)', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 404))
      await expect(deleteOutfit('missing')).rejects.toThrow()
    })
  })
})
