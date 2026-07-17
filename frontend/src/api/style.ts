import { markWorn, photoUrl } from './items'

// Typed client for the stylist API (`POST /api/style`). Follows the `api/items.ts`
// pattern: resolve with the parsed body on a 2xx response, throw on any non-2xx or
// network/transport failure so callers can render an error state. The card renders
// stored photos via the shared `photoUrl(id)` builder, and logs a worn look via
// `markWorn(id)` — both re-exported here so the route imports a single
// stylist-facing module.

const BASE = '/api/style'

/**
 * One rendered piece of the outfit — mirrors `StyleResponse.OutfitItem`. The
 * `rationale` is the stylist's per-item reason (LLM output); the remaining tag
 * fields are the item's stored tags (deterministic, not LLM-derived), used to
 * derive the spec-sheet name, slot label, color swatch, and FORM/WARM pips. Tag
 * fields are nullable — a degraded vision tag stays a normal, renderable state.
 */
export interface OutfitItem {
  itemId: string
  photoUrl: string
  rationale: string
  category: string | null
  primaryColor: string | null
  formality: number | null
  warmth: number | null
  descriptors: string[] | null
}

/**
 * One turn of the re-pick conversation — mirrors the backend `StyleRequest.StyleTurn`.
 * The server is stateless: the client accumulates the thread and resends it each turn.
 * Text-only (the stylist never sees images); `assistant` turns summarize a prior pick.
 */
export interface StyleTurn {
  role: 'user' | 'assistant'
  text: string
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

/**
 * Ask the stylist for an outfit matching a free-text vibe. On a re-pick the caller
 * passes the accumulated `history` (prior vibe + assistant summaries + feedback); the
 * server reads it, produces a different look, and stays stateless. An empty history is
 * omitted from the body so a first pick stays byte-for-byte backward compatible.
 */
export async function requestStyle(prompt: string, history: StyleTurn[] = []): Promise<Outfit> {
  const response = ensureOk(
    await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt, ...(history.length ? { history } : {}) }),
    }),
    'Style request',
  )
  return (await response.json()) as Outfit
}

export { markWorn, photoUrl }
