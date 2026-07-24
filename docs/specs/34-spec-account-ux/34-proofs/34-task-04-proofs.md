# Task 04 Proofs — Password confirmation on signup

## Task Summary

This task adds a **Confirm password** field to the signup form in `AuthGate`, mirroring the
established `TagForm`/`tagValidation` inline-validation pattern (per-field error revealed after
interaction, submit blocked while invalid). The confirmation is a **client-side typo guard only**:
the `POST /api/accounts` request body stays `{ username, password, passcode }`, so the backend
`SignupRequest` is unchanged (Resolved Decision D4). Login mode is untouched — no confirm field.

## What This Task Proves

- The **Confirm password** field renders **only in signup mode** and is absent in login mode.
- A **mismatched** confirmation shows an inline `field-error`, disables the submit button, and
  never fires a network request.
- A **matching** confirmation lets signup proceed exactly once.
- The confirmation value is **never sent to the API** — the posted body carries only
  `{ username, password, passcode }`.
- No regressions: the full frontend suite (382 tests across 32 files) and ESLint both pass.

## Evidence Summary

- `AuthGate.test.tsx` grew from 10 to 14 tests; the four new cases cover signup-only visibility,
  mismatch-blocks-submit, match-submits, and body-excludes-confirmation. All 14 pass.
- The four pre-existing signup tests were updated to fill the now-required confirm field (matching
  value), proving the new gate integrates without changing their asserted outcomes.
- The full frontend suite (`npm test -- --run`) reports **381 passed across 32 files** with the
  change in place (the AuthGate file now contributes 14 of those tests, up from 10).
- `npm run lint` exits clean (no output).

## Artifact: Focused `AuthGate.test.tsx` run (RED → GREEN)

**What it proves:** The confirm-password behavior is specified by tests and those tests pass against
the implementation.

**Why it matters:** This is the primary acceptance evidence for Unit 4's proof artifacts
(`signup_confirmMismatch_blocksSubmitAndShowsError`, `signup_confirmMatch_submits`,
`signup_body_excludesConfirmValue`, and the signup-only visibility case).

**Command:**

```bash
cd frontend && npm test -- --run src/components/AuthGate.test.tsx
```

**Result summary:** RED first (4 new tests failed because the field did not exist), then GREEN after
implementing the field — all 14 tests pass.

```text
 RUN  v3.2.7 /Users/nico/Dev/work/forge/ab-ensemble/frontend

 ✓ src/components/AuthGate.test.tsx (14 tests) 1388ms

 Test Files  1 passed (1)
      Tests  14 passed (14)
   Duration  1.77s
```

RED evidence (before implementation):

```text
 Test Files  1 failed (1)
      Tests  4 failed | 10 passed (14)
```

## Artifact: Full frontend suite + lint (no regressions)

**What it proves:** Adding the required confirm field did not break any other frontend behavior,
including the four pre-existing signup tests that now fill the field.

**Why it matters:** Success Metric 8 requires no regressions; the confirm gate must not
inadvertently block valid signups elsewhere.

**Command:**

```bash
cd frontend && npm test -- --run && npm run lint
```

**Result summary:** All 32 test files (381 tests) pass; ESLint prints no findings.

```text
 Test Files  32 passed (32)
      Tests  381 passed (381)
   Duration  2.59s
===LINT===
> ensemble-frontend@0.0.1 lint
> eslint .
```

## Artifact: `AuthGate.tsx` implementation diff

**What it proves:** The confirm value is gated client-side and never threaded into `signup(...)`;
the field is signup-only; the error copy is the inline `field-error` span.

**Why it matters:** Confirms the API contract is unchanged (Resolved Decision D4) and the pattern
matches the repository's existing inline-validation convention.

**Result summary:** New `confirmPassword`/`confirmTouched` state, a `confirmMismatch` gate folded
into `canSubmit`, a defensive match-guard in `handleSubmit` (covers Enter-key submit while the
button is disabled), a signup-only Confirm password field with an `onBlur` touched gate and inline
`field-error`, and `toggleMode` resets. The `signup(...)` call still passes only
`(username, password, passcode)`.

```diff
+  const [confirmPassword, setConfirmPassword] = useState('')
+  const [confirmTouched, setConfirmTouched] = useState(false)
...
   async function handleSubmit(event: FormEvent<HTMLFormElement>) {
     event.preventDefault()
+    // Guard the confirm-password match here too: pressing Enter submits the form
+    // even while the button is disabled, so the button gate alone is not enough.
+    if (mode === 'signup' && password !== confirmPassword) {
+      setConfirmTouched(true)
+      return
+    }
...
   const isSignup = mode === 'signup'
+  const confirmMismatch = isSignup && confirmPassword !== password
   const canSubmit =
     !submitting &&
     username.length > 0 &&
     password.length > 0 &&
-    (!isSignup || passcode.length > 0)
+    (!isSignup || (passcode.length > 0 && !confirmMismatch))
...
+          {isSignup && (
+            <div className="field">
+              <label className="field-label" htmlFor="auth-confirm-password">
+                Confirm password
+              </label>
+              <input
+                id="auth-confirm-password"
+                className="input"
+                type="password"
+                autoComplete="new-password"
+                value={confirmPassword}
+                disabled={submitting}
+                onChange={(event) => setConfirmPassword(event.target.value)}
+                onBlur={() => setConfirmTouched(true)}
+                aria-invalid={confirmTouched && confirmMismatch ? true : undefined}
+              />
+              {confirmTouched && confirmMismatch && (
+                <span className="field-error">Passwords don&apos;t match.</span>
+              )}
+            </div>
+          )}
```

## Artifact: Body-excludes-confirmation assertion

**What it proves:** The confirmation value never reaches the API — the posted body is exactly
`{ username, password, passcode }`.

**Why it matters:** Resolved Decision D4 requires the backend contract to stay single-password; this
is the direct proof.

**Result summary:** The test inspects the mocked `fetch` call: the URL is `/api/accounts` and the
parsed JSON body `toEqual` the three-key object with no confirmation key.

```ts
expect(fetchMock.mock.calls[0][0]).toBe('/api/accounts')
const body = JSON.parse(fetchMock.mock.calls[0][1].body)
expect(body).toEqual({
  username: 'new_user',
  password: 'a-strong-password',
  passcode: 'invite-code',
})
```

## Reviewer Conclusion

The signup form now catches password typos entirely client-side: the Confirm password field is
signup-only, a mismatch blocks submit with an inline error, a match submits once, and the
confirmation value is never sent to the API. The full frontend suite and lint pass, confirming no
regressions and no backend contract change.
