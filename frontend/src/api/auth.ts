// Typed client for the account endpoints: `POST /api/auth` (email/password login) and
// `POST /api/accounts` (invite-passcode-gated signup). Follows the `api/items.ts` pattern:
// resolve on a 2xx response, throw on any non-2xx or network/transport failure. The
// returned session token is held in `sessionStorage` (cleared on tab close, unlike
// `localStorage`) so `AuthGate` and the authenticated fetch wrapper can read/clear it
// without threading it through component state.

const LOGIN_URL = '/api/auth'
const SIGNUP_URL = '/api/accounts'

/** `sessionStorage` key for the signed session token; exported so tests can seed it directly. */
export const SESSION_TOKEN_STORAGE_KEY = 'ensemble.session.token'

/** An HTTP error whose `status` lets callers (e.g. `AuthGate`) map it to specific copy. */
export class HttpError extends Error {
  readonly status: number

  constructor(action: string, status: number) {
    super(`${action} failed with status ${status}`)
    this.name = 'HttpError'
    this.status = status
  }
}

/** Throws an `HttpError` carrying the response status for a non-2xx response. */
function ensureOk(response: Response, action: string): Response {
  if (!response.ok) {
    throw new HttpError(action, response.status)
  }
  return response
}

/** POSTs `body` as JSON to `url`, stores the returned `token`, and throws on non-2xx. */
async function postForToken(
  url: string,
  body: Record<string, string>,
  action: string,
): Promise<void> {
  const response = ensureOk(
    await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
    action,
  )
  const { token } = (await response.json()) as { token: string }
  sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, token)
}

/** Trades an email/password for a signed session token, storing it in `sessionStorage`. */
export async function login(email: string, password: string): Promise<void> {
  await postForToken(LOGIN_URL, { email, password }, 'Login')
}

/**
 * Trades an email/password plus the invite passcode for a signed session token
 * (`POST /api/accounts`), storing it in `sessionStorage`.
 */
export async function signup(email: string, password: string, passcode: string): Promise<void> {
  await postForToken(SIGNUP_URL, { email, password, passcode }, 'Signup')
}

/** The stored session token, or `null` if not authenticated. */
export function getToken(): string | null {
  return sessionStorage.getItem(SESSION_TOKEN_STORAGE_KEY)
}

/** Clears the stored session token (e.g. after a `401` from any authenticated request). */
export function clearToken(): void {
  sessionStorage.removeItem(SESSION_TOKEN_STORAGE_KEY)
}
