// Typed client for the passcode gate (`POST /api/auth`). Follows the `api/items.ts`
// pattern: resolve on a 2xx response, throw on any non-2xx or network/transport
// failure. The returned session token is held in `sessionStorage` (cleared on tab
// close, unlike `localStorage`) so `AuthGate` and the authenticated fetch wrapper can
// read/clear it without threading it through component state.

const BASE = '/api/auth'

/** `sessionStorage` key for the signed session token; exported so tests can seed it directly. */
export const SESSION_TOKEN_STORAGE_KEY = 'ensemble.session.token'

/** Throws a descriptive error for a non-2xx response; otherwise returns it. */
function ensureOk(response: Response, action: string): Response {
  if (!response.ok) {
    throw new Error(`${action} failed with status ${response.status}`)
  }
  return response
}

/** Trades a passcode for a signed session token, storing it in `sessionStorage`. */
export async function login(passcode: string): Promise<void> {
  const response = ensureOk(
    await fetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ passcode }),
    }),
    'Login',
  )
  const { token } = (await response.json()) as { token: string }
  sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, token)
}

/** The stored session token, or `null` if not authenticated. */
export function getToken(): string | null {
  return sessionStorage.getItem(SESSION_TOKEN_STORAGE_KEY)
}

/** Clears the stored session token (e.g. after a `401` from any authenticated request). */
export function clearToken(): void {
  sessionStorage.removeItem(SESSION_TOKEN_STORAGE_KEY)
}
