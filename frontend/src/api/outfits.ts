import { authedFetch } from './http'
import { ApiError } from './items'
import type { SavedOutfit } from '../types/outfit'

// Typed client for the saved-outfits API (`/api/outfits`). Mirrors `api/items.ts`
// one-to-one: resolve with the parsed body on a 2xx response, throw a typed
// `ApiError` (re-exported from `items.ts` so callers have a single import surface)
// on any non-2xx, and propagate a network/transport failure so callers can render
// an error state. Requests go through `authedFetch` so the session token is
// injected and a `401` returns the client to the passcode gate.

const BASE = '/api/outfits'

export { ApiError }

/** The payload for saving a new outfit. `reason` is omitted for manual looks. */
export interface SaveOutfitInput {
  itemIds: string[]
  source: 'ai' | 'manual'
  reason?: string | null
}

/** Throws a typed `ApiError` for a non-2xx response; otherwise returns it. */
function ensureOk(response: Response, action: string): Response {
  if (!response.ok) {
    throw new ApiError(response.status, action)
  }
  return response
}

/**
 * Persist a new saved outfit (`POST /api/outfits`); resolves with the stored
 * record on `201`. An unknown `itemId` fails the server's grounding guard and
 * surfaces here as an `ApiError` with `status === 400`. A `reason` of
 * `undefined` is dropped by `JSON.stringify`, so a manual save sends no
 * `reason` field (the backend binds it to `null`).
 */
export async function saveOutfit(input: SaveOutfitInput): Promise<SavedOutfit> {
  const response = ensureOk(
    await authedFetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
    'Save outfit',
  )
  return (await response.json()) as SavedOutfit
}

/** All saved outfits. */
export async function listOutfits(): Promise<SavedOutfit[]> {
  const response = ensureOk(await authedFetch(BASE), 'List outfits')
  return (await response.json()) as SavedOutfit[]
}

/** Delete a saved outfit (`204 No Content` on success). */
export async function deleteOutfit(id: string): Promise<void> {
  ensureOk(await authedFetch(`${BASE}/${id}`, { method: 'DELETE' }), 'Delete outfit')
}
