import { markWorn, photoUrl } from './items'

// Typed client for the stylist API (`POST /api/style`). Follows the `api/items.ts`
// pattern: resolve with the parsed body on a 2xx response, throw on any non-2xx or
// network/transport failure so callers can render an error state. The card renders
// stored photos via the shared `photoUrl(id)` builder, and logs a worn look via
// `markWorn(id)` — both re-exported here so the route imports a single
// stylist-facing module.

const BASE = '/api/style'

/** One rendered piece of the outfit — mirrors `StyleResponse.OutfitItem`. */
export interface OutfitItem {
  itemId: string
  photoUrl: string
}

/**
 * Grounded stylist result — mirrors the backend `StyleResponse`. An empty-wardrobe
 * (or too-small) response is a normal `200` carrying empty `itemIds`/`items` plus an
 * explanatory `reason`, not an error.
 */
export interface Outfit {
  itemIds: string[]
  reason: string
  items: OutfitItem[]
}

/** Throws a descriptive error for a non-2xx response; otherwise returns it. */
function ensureOk(response: Response, action: string): Response {
  if (!response.ok) {
    throw new Error(`${action} failed with status ${response.status}`)
  }
  return response
}

/** Ask the stylist for an outfit matching a free-text vibe. */
export async function requestStyle(prompt: string): Promise<Outfit> {
  const response = ensureOk(
    await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt }),
    }),
    'Style request',
  )
  return (await response.json()) as Outfit
}

export { markWorn, photoUrl }
