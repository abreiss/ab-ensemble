import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { login } from './auth'
import {
  ApiError,
  createItem,
  deleteItem,
  getItem,
  listItems,
  markWorn,
  photoUrl,
  tagPreview,
  updateTags,
} from './items'
import type { Item, TagInput, TagSuggestion } from '../types/item'

/** Builds a fetch-like Response stub. */
function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

const sampleItem: Item = {
  itemId: 'abc',
  category: 'top',
  primaryColor: 'navy',
  secondaryColor: null,
  formality: 3,
  pattern: 'striped',
  warmth: 2,
  descriptors: ['cotton'],
  photoUrl: '/api/items/abc/photo',
  createdAt: '2026-01-01T00:00:00Z',
  lastWorn: null,
  wornCount: 0,
}

const sampleTags: TagInput = {
  category: 'top',
  primaryColor: 'navy',
  secondaryColor: null,
  formality: 3,
  pattern: 'striped',
  warmth: 2,
  descriptors: ['cotton', 'slim'],
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

describe('items API client', () => {
  describe('listItems', () => {
    it('GETs /api/items and returns the parsed array', async () => {
      fetchMock.mockResolvedValue(jsonResponse([sampleItem]))

      const items = await listItems()

      expect(lastCall()[0]).toBe('/api/items')
      expect(items).toEqual([sampleItem])
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 500))
      await expect(listItems()).rejects.toThrow()
    })

    it('propagates a network/transport failure', async () => {
      fetchMock.mockRejectedValue(new TypeError('offline'))
      await expect(listItems()).rejects.toThrow()
    })

    it('sends the stored session token as X-Ensemble-Session', async () => {
      await seedToken('tok-123')
      fetchMock.mockResolvedValue(jsonResponse([sampleItem]))

      await listItems()

      const [, init] = lastCall()
      expect((init.headers as Headers).get('X-Ensemble-Session')).toBe('tok-123')
    })
  })

  describe('getItem', () => {
    it('GETs /api/items/:id and returns the item', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleItem))

      const item = await getItem('abc')

      expect(lastCall()[0]).toBe('/api/items/abc')
      expect(item).toEqual(sampleItem)
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 404))
      await expect(getItem('missing')).rejects.toThrow()
    })
  })

  describe('photoUrl', () => {
    it('builds the photo path for an id with no token stored', () => {
      expect(photoUrl('abc')).toBe('/api/items/abc/photo')
    })

    it('appends ?token=<token> when a session token is stored', async () => {
      await seedToken('tok-123')

      expect(photoUrl('abc')).toBe('/api/items/abc/photo?token=tok-123')
    })
  })

  describe('tagPreview', () => {
    it('POSTs the photo as multipart to /api/items/tag and returns the suggestion', async () => {
      const suggestion: TagSuggestion = {
        category: 'top',
        primaryColor: 'navy',
        secondaryColor: null,
        formality: 3,
        pattern: null,
        warmth: 2,
        descriptors: ['cotton'],
      }
      fetchMock.mockResolvedValue(jsonResponse(suggestion))
      const photo = new File([new Uint8Array([1, 2, 3])], 'p.jpg', { type: 'image/jpeg' })

      const result = await tagPreview(photo)

      const [url, init] = lastCall()
      expect(url).toBe('/api/items/tag')
      expect(init.method).toBe('POST')
      expect(init.body).toBeInstanceOf(FormData)
      expect((init.body as FormData).get('photo')).toBe(photo)
      expect(result).toEqual(suggestion)
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 400))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })
      await expect(tagPreview(photo)).rejects.toThrow()
    })

    it('rejects with a typed ApiError carrying status 429 on the daily cap', async () => {
      // The batch queue detects a mid-batch daily-cap 429 via the typed status, not
      // by string-matching the message — so `tagPreview` must surface the code.
      fetchMock.mockResolvedValue(jsonResponse({}, 429))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })

      const error = await tagPreview(photo).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(429)
    })

    it('rejects with an ApiError carrying the status for other non-2xx responses', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 400))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })

      const error = await tagPreview(photo).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(400)
    })
  })

  describe('createItem', () => {
    it('POSTs photo + tag fields as multipart to /api/items and returns the item', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleItem, 201))
      const photo = new File([new Uint8Array([1, 2, 3])], 'p.jpg', { type: 'image/jpeg' })

      const created = await createItem(photo, sampleTags)

      const [url, init] = lastCall()
      expect(url).toBe('/api/items')
      expect(init.method).toBe('POST')
      const fd = init.body as FormData
      expect(fd.get('photo')).toBe(photo)
      expect(fd.get('category')).toBe('top')
      expect(fd.get('primaryColor')).toBe('navy')
      expect(fd.get('formality')).toBe('3')
      expect(fd.get('warmth')).toBe('2')
      expect(fd.getAll('descriptors')).toEqual(['cotton', 'slim'])
      expect(created).toEqual(sampleItem)
    })

    it('omits null/undefined optional fields from the multipart body', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleItem, 201))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })

      await createItem(photo, { category: 'top', formality: 2, warmth: 1 })

      const fd = lastCall()[1].body as FormData
      expect(fd.has('primaryColor')).toBe(false)
      expect(fd.has('descriptors')).toBe(false)
      expect(fd.get('category')).toBe('top')
    })

    it('omits null formality/warmth from the multipart body (Jewelry has neither)', async () => {
      // Regression test for 18-reviews/18-task-02-review.md: a Jewelry item saved
      // with formality: null, warmth: null must NOT send the literal string
      // "null" — the field must be absent entirely, exactly like every other
      // optional field, so the backend's nullable TagRequest.formality/warmth
      // bind to null instead of failing Integer conversion on "null".
      fetchMock.mockResolvedValue(jsonResponse(sampleItem, 201))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })

      await createItem(photo, { category: 'Jewelry', formality: null, warmth: null })

      const fd = lastCall()[1].body as FormData
      expect(fd.has('formality')).toBe(false)
      expect(fd.has('warmth')).toBe(false)
      expect(fd.get('category')).toBe('Jewelry')
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 400))
      const photo = new File([new Uint8Array([1])], 'p.jpg', { type: 'image/jpeg' })
      await expect(createItem(photo, sampleTags)).rejects.toThrow()
    })
  })

  describe('updateTags', () => {
    it('PUTs JSON tags to /api/items/:id/tags and returns the item', async () => {
      fetchMock.mockResolvedValue(jsonResponse(sampleItem))

      const updated = await updateTags('abc', sampleTags)

      const [url, init] = lastCall()
      expect(url).toBe('/api/items/abc/tags')
      expect(init.method).toBe('PUT')
      expect((init.headers as Headers).get('Content-Type')).toBe('application/json')
      expect(JSON.parse(init.body as string)).toEqual(sampleTags)
      expect(updated).toEqual(sampleItem)
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 400))
      await expect(updateTags('abc', sampleTags)).rejects.toThrow()
    })
  })

  describe('markWorn', () => {
    it('POSTs /api/items/:id/worn and returns the updated item', async () => {
      const worn: Item = { ...sampleItem, wornCount: 1, lastWorn: '2026-07-16T00:00:00Z' }
      fetchMock.mockResolvedValue(jsonResponse(worn))

      const result = await markWorn('abc')

      const [url, init] = lastCall()
      expect(url).toBe('/api/items/abc/worn')
      expect(init.method).toBe('POST')
      expect(result).toEqual(worn)
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 404))
      await expect(markWorn('missing')).rejects.toThrow()
    })
  })

  describe('deleteItem', () => {
    it('DELETEs /api/items/:id and resolves on 204', async () => {
      fetchMock.mockResolvedValue(jsonResponse(null, 204))

      await expect(deleteItem('abc')).resolves.toBeUndefined()

      const [url, init] = lastCall()
      expect(url).toBe('/api/items/abc')
      expect(init.method).toBe('DELETE')
    })

    it('throws on a non-2xx response', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}, 404))
      await expect(deleteItem('missing')).rejects.toThrow()
    })
  })
})
