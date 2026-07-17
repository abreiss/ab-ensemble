import { getToken } from './auth'
import { authedFetch } from './http'
import type { Item, TagInput, TagSuggestion } from '../types/item'

// Typed client for the backend wardrobe API (`/api/items`). Follows the
// `api/health.ts` pattern: resolve with the parsed body on a 2xx response, throw
// on any non-2xx or network/transport failure so callers can render an error
// state. Same-origin `/api/**` (Vite proxies to the backend in dev). Requests go
// through `authedFetch` so the session token is injected and a `401` returns the
// client to the passcode gate.

const BASE = '/api/items'

/** Throws a descriptive error for a non-2xx response; otherwise returns it. */
function ensureOk(response: Response, action: string): Response {
  if (!response.ok) {
    throw new Error(`${action} failed with status ${response.status}`)
  }
  return response
}

/** Appends an optional tag field only when it has a real (non-null) value. */
function appendOptional(form: FormData, key: string, value: string | null | undefined): void {
  if (value !== null && value !== undefined) {
    form.append(key, value)
  }
}

/** Builds the multipart body shared by tag-preview and create. */
function tagFormData(photo: File, tags?: TagInput): FormData {
  const form = new FormData()
  form.append('photo', photo)
  if (tags) {
    form.append('category', tags.category)
    appendOptional(form, 'primaryColor', tags.primaryColor)
    appendOptional(form, 'secondaryColor', tags.secondaryColor)
    form.append('formality', String(tags.formality))
    appendOptional(form, 'pattern', tags.pattern)
    form.append('warmth', String(tags.warmth))
    for (const descriptor of tags.descriptors ?? []) {
      form.append('descriptors', descriptor)
    }
  }
  return form
}

/** All owned items (tags + wear-history + photoUrl). */
export async function listItems(): Promise<Item[]> {
  const response = ensureOk(await authedFetch(BASE), 'List items')
  return (await response.json()) as Item[]
}

/** A single item by id. */
export async function getItem(id: string): Promise<Item> {
  const response = ensureOk(await authedFetch(`${BASE}/${id}`), 'Get item')
  return (await response.json()) as Item
}

/**
 * The URL that serves an item's photo bytes (image/jpeg). `<img>` tags can't set a
 * header, so a stored session token is appended as `?token=` for the gate filter to
 * accept instead of `X-Ensemble-Session`.
 */
export function photoUrl(id: string): string {
  const token = getToken()
  return token ? `${BASE}/${id}/photo?token=${token}` : `${BASE}/${id}/photo`
}

/** Auto-tag a photo without persisting anything; returns an editable suggestion. */
export async function tagPreview(photo: File): Promise<TagSuggestion> {
  const response = ensureOk(
    await authedFetch(`${BASE}/tag`, { method: 'POST', body: tagFormData(photo) }),
    'Tag preview',
  )
  return (await response.json()) as TagSuggestion
}

/** Create an item from a photo + edited tags (multipart). */
export async function createItem(photo: File, tags: TagInput): Promise<Item> {
  const response = ensureOk(
    await authedFetch(BASE, { method: 'POST', body: tagFormData(photo, tags) }),
    'Create item',
  )
  return (await response.json()) as Item
}

/** Replace an item's tags (JSON). */
export async function updateTags(id: string, tags: TagInput): Promise<Item> {
  const response = ensureOk(
    await authedFetch(`${BASE}/${id}/tags`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(tags),
    }),
    'Update tags',
  )
  return (await response.json()) as Item
}

/** Delete an item. */
export async function deleteItem(id: string): Promise<void> {
  ensureOk(await authedFetch(`${BASE}/${id}`, { method: 'DELETE' }), 'Delete item')
}

/**
 * Log a wear: `POST /api/items/:id/worn`. The server increments `wornCount` and
 * sets `lastWorn` (deterministic, never the model), returning the updated item.
 */
export async function markWorn(id: string): Promise<Item> {
  const response = ensureOk(
    await fetch(`${BASE}/${id}/worn`, { method: 'POST' }),
    'Mark worn',
  )
  return (await response.json()) as Item
}
