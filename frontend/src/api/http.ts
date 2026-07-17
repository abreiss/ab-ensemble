import { clearToken, getToken } from './auth'

// Shared authenticated `fetch` wrapper for every gated `/api/**` call. Injects the
// stored session token as `X-Ensemble-Session`; on a `401` (expired/invalid/missing
// token) it clears the stored token and fires a re-auth signal so `AuthGate` can drop
// the app back to the passcode screen. Callers keep their existing `ensureOk`-style
// non-2xx handling — a `401` is returned like any other response, not thrown here.

const AUTH_REQUIRED_EVENT = 'ensemble:auth-required'

/**
 * Registers a listener for the re-auth signal fired whenever an authenticated request
 * comes back `401`. Returns an unsubscribe function.
 */
export function onAuthRequired(listener: () => void): () => void {
  const handler = () => listener()
  window.addEventListener(AUTH_REQUIRED_EVENT, handler)
  return () => window.removeEventListener(AUTH_REQUIRED_EVENT, handler)
}

/** `fetch`, with the stored session token injected and a `401` clearing client auth state. */
export async function authedFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken()
  const headers = new Headers(init.headers)
  if (token) {
    headers.set('X-Ensemble-Session', token)
  }
  const response = await fetch(input, { ...init, headers })
  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT))
  }
  return response
}
