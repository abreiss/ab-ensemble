# Task 03 Proofs - Sign-out control

## Task Summary

This task adds a visible **Sign out** control to the app nav (`App.tsx`), shown only inside
the signed-in shell. Clicking it discards the stored session token client-side and returns
the user to the login gate by reusing the *exact* re-auth machinery a `401` already triggers —
the `ensemble:auth-required` window event that `AuthGate` subscribes to. There is **no backend
change**: the stateless HMAC token is simply dropped on the client and expires at its 12h TTL
(spec Resolved Decision D3).

## What This Task Proves

- A signed-in user sees a **Sign out** button and clicking it clears the session token.
- After sign-out the app returns to the login form (the username-labelled auth gate) and the
  gated screens are no longer reachable without logging back in.
- `sessionStorage` no longer holds `ensemble.session.token` after sign-out — the token is truly
  discarded, not merely hidden.
- Sign-out and a `401` share a single source of the re-auth event name (no duplicated literal),
  so the two paths cannot drift apart.

## Evidence Summary

- The new RTL test `signOut_clearsTokenAndReturnsToLogin` passes: it seeds a token, renders the
  shell, clicks **Sign out**, and asserts `getToken()` is `null`, `sessionStorage` no longer
  holds the token, and the username login form is shown.
- The full frontend suite (32 files, 377 tests) is green and `eslint` is clean — no regressions.
- A repo-wide grep shows the `'ensemble:auth-required'` literal appears exactly **once** (in
  `api/http.ts`); `App.tsx` dispatches via the exported `signalAuthRequired()` helper.

## Artifact: `signOut_clearsTokenAndReturnsToLogin` RTL test

**What it proves:** The client-side token discard + gate-return behavior works end-to-end in a
rendered app shell.

**Why it matters:** This is the primary behavioral proof for the feature — it exercises the real
button, the real `clearToken()`, and the real `AuthGate` event subscription rather than mocking
them.

**Command:**

~~~bash
cd frontend && npm test -- --run src/App.test.tsx -t "signOut"
~~~

**Result summary:** The targeted test passes (the other 13 App tests are filtered out by the `-t`
name matcher).

~~~text
 ✓ src/App.test.tsx (14 tests | 13 skipped) 53ms
 Test Files  1 passed (1)
      Tests  1 passed | 13 skipped (14)
~~~

The test body (`frontend/src/App.test.tsx`):

~~~tsx
it('signOut_clearsTokenAndReturnsToLogin', async () => {
  // Arrange: a signed-in session renders the app shell.
  sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, 'test-token')
  renderAt('/')
  expect(await screen.findByTestId('stylist')).toBeInTheDocument()

  // Act: click the Sign out control.
  await userEvent.click(screen.getByRole('button', { name: /sign out/i }))

  // Assert: the token is discarded and the login gate is shown again.
  expect(getToken()).toBeNull()
  expect(sessionStorage.getItem('ensemble.session.token')).toBeNull()
  expect(await screen.findByLabelText(/^username$/i)).toBeInTheDocument()
})
~~~

## Artifact: Full frontend suite + lint (no regressions)

**What it proves:** Adding the Sign out control and refactoring the event dispatch broke nothing
across the frontend.

**Why it matters:** The refactor touched `api/http.ts` (used by every gated request), so a
green full suite confirms the shared re-auth path is intact.

**Command:**

~~~bash
cd frontend && npm test -- --run && npm run lint
~~~

**Result summary:** All 377 tests across 32 files pass; `eslint .` reports no problems.

~~~text
 Test Files  32 passed (32)
      Tests  377 passed (377)

> ensemble-frontend@0.0.1 lint
> eslint .
~~~

## Artifact: Single-source event name (REFACTOR)

**What it proves:** Sign-out reuses the exact `401` machinery rather than duplicating the event
literal, satisfying task 3.3.

**Why it matters:** If the literal were duplicated, a future rename in `api/http.ts` would
silently break sign-out. Routing both through `signalAuthRequired()` makes that impossible.

**Command:**

~~~bash
grep -rn "ensemble:auth-required" frontend/src
~~~

**Result summary:** The literal occurs exactly once, in `api/http.ts`. `App.tsx` calls the
exported `signalAuthRequired()`; `authedFetch` (the `401` path) now calls the same helper.

~~~text
frontend/src/api/http.ts:9:const AUTH_REQUIRED_EVENT = 'ensemble:auth-required'
~~~

The shared helper (`frontend/src/api/http.ts`):

~~~ts
export function signalAuthRequired(): void {
  window.dispatchEvent(new CustomEvent(AUTH_REQUIRED_EVENT))
}
~~~

The sign-out handler (`frontend/src/App.tsx`):

~~~tsx
function signOut() {
  clearToken()
  signalAuthRequired()
}
~~~

## Reviewer Conclusion

The Sign out control is present in the signed-in shell, discards the session token on click, and
returns the user to the username login gate — proven by a passing RTL test that inspects real
`sessionStorage` state. The re-auth event is single-sourced through `signalAuthRequired()`, so
sign-out and 401-driven re-auth share one code path. No backend change was made, matching the
stateless-token design (D3), and the full frontend suite plus lint stay green.
