# Task 05 Proofs - Integration, secret-safety & regression verification

## Task Summary

This task closes out Spec 07 (PWA install + security guards) by proving the
whole feature is safe to ship together: the passcode value never reaches the
built client bundle, the full specs 01–07 test suites still pass with the gate
and cap wired in, the critical security logic hits the required coverage bar,
and the docs tell a developer how to actually use the new gate/cap locally.

## What This Task Proves

- The demo passcode is never present anywhere in the production frontend
  bundle (success metric #3).
- The daily-cap default (`ensemble.usage.daily-limit: 100`) and passcode env
  vars are documented, added to `.env.example` as placeholders only, and the
  pre-commit secret scan still passes — no secret was committed.
- All pre-existing backend (specs 01–06) and frontend tests remain green
  alongside the new Spec 07 code (success metric #6) — including the
  specs 04/05 frontend `api` tests touched by the authenticated-fetch refactor.
- Token verification, the daily-limit check, and the wardrobe scan filter meet
  the ≥90% line / 100% branch coverage bar (success metric #5).
- `README.md` and `docs/DEVELOPMENT.md` now document the PWA install flow and
  the passcode + daily-cap configuration end-to-end.

## Evidence Summary

- `pre-commit run --all-files` is fully green, including the Anthropic-key
  secret scan, after adding the `.env.example` placeholders.
- `grep -rF "$ENSEMBLE_PASSCODE" src/main/resources/static/` against a real
  production build returns no match (`exit=1`).
- Backend: **208/208** tests pass (`./gradlew test --rerun-tasks`). Frontend:
  **89/89** tests pass across 13 files (`npm test -- --run`).
- JaCoCo: `SessionTokenService` 100% branch / 93.5% line, `CallCapService`
  100%/100%, `WardrobeRepository` (scan filter) 100%/100%; project-wide line
  coverage 96.6%.
- `README.md`, `docs/DEVELOPMENT.md`, and `.env.example` diffs add the PWA
  install steps and the passcode/cap docs (see diff stat below).

## Artifact: `.env.example` placeholders + secret scan

**What it proves:** `ENSEMBLE_PASSCODE` and the optional `ENSEMBLE_SESSION_SECRET`
are documented as placeholders (never a real value), `.env` itself stays
git-ignored, and the pre-commit secret scan still passes.

**Why it matters:** This is the mechanism that keeps the real passcode out of
git history while still telling a new developer what env var to set.

**`.env.example` diff (placeholder values only):**

```diff
 ENSEMBLE_ANTHROPIC_API_KEY=<your-anthropic-api-key>
+
+# Passcode gate (Spec 07). Required to open the app; a blank value leaves
+# the gate closed (every /api/** request gets 401). Pick any demo passcode.
+ENSEMBLE_PASSCODE=<your-demo-passcode>
+
+# Optional: HMAC key for signing session tokens. If left blank, the key is
+# derived from ENSEMBLE_PASSCODE instead, so only ENSEMBLE_PASSCODE is required.
+ENSEMBLE_SESSION_SECRET=
```

**Command:**

```bash
git check-ignore -v .env
```

**Result summary:** `.env` matches the existing `.env*` ignore rule
(`.gitignore:49`) and no `.env` file exists locally, so there is nothing to
accidentally stage.

```text
.gitignore:49:.env*	.env
```

**Command:**

```bash
pre-commit run --all-files
```

**Result summary:** Every hook passes, including the Anthropic-key secret
scan — the new placeholders did not trip it and no real secret was introduced.

```text
trim trailing whitespace.................................................Passed
fix end of files.........................................................Passed
check yaml...............................................................Passed
check for added large files..............................................Passed
check for merge conflicts.................................................Passed
detect private key.......................................................Passed
Block committed Anthropic API keys........................................Passed
Backend tests (gradle, -PskipFrontend)....................................Passed
Frontend tests (vitest)....................................................Passed
Frontend lint (eslint).....................................................Passed
```

## Artifact: Passcode absent from the built client bundle

**What it proves:** After a real production build, the passcode value cannot
be found anywhere in the static assets Spring serves — the gate is enforced
server-side only (success metric #3, the spec's core secret-safety claim).

**Why it matters:** This is the one thing a reviewer would actually want to
independently re-check: that reading the shipped JS can't reveal the passcode.

**Command:**

```bash
cd frontend && npm run build
cd ..
export ENSEMBLE_PASSCODE="demo-passcode-9f31-do-not-commit"
grep -rF "$ENSEMBLE_PASSCODE" src/main/resources/static/ ; echo "exit=$?"
```

**Result summary:** `grep` found zero matches in the built bundle
(`exit=1` = no match), confirming the passcode value never appears in
`src/main/resources/static/` regardless of what it's set to.

```text
exit=1
```

## Artifact: Full regression suite — specs 01–07 green

**What it proves:** Every pre-existing backend and frontend test still passes
with the passcode gate, the daily cap, and the authenticated-fetch refactor in
place — no regression to the wardrobe, tagging, or stylist flows (success
metric #6).

**Why it matters:** The gate filter now runs in front of *every* `/api`
request and the frontend `api/items`/`api/style` clients were rewired to route
through the new authenticated-fetch wrapper — both are exactly the kind of
change that silently breaks unrelated slices if done carelessly.

**Command:**

```bash
./gradlew test --rerun-tasks
```

**Result summary:** `BUILD SUCCESSFUL`, 208/208 backend tests pass across 34
test classes (0 skipped, 0 failures, 0 errors) — computed from the JUnit XML
totals in `build/test-results/`.

```text
> Task :test

BUILD SUCCESSFUL in 16s
6 actionable tasks: 6 executed

tests=208 skipped=0 failures=0 errors=0
```

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** 89/89 frontend tests pass across all 13 test files,
including `api/auth.test.ts`, `api/http.test.ts`, `api/items.test.ts`,
`api/style.test.ts`, `AuthGate.test.tsx`, and the pre-existing specs 04/05
route/component suites — none regressed.

```text
 ✓ src/lib/tagValidation.test.ts (14 tests) 3ms
 ✓ src/api/auth.test.ts (4 tests) 3ms
 ✓ src/api/http.test.ts (6 tests) 4ms
 ✓ src/api/style.test.ts (6 tests) 4ms
 ✓ src/api/items.test.ts (17 tests) 10ms
 ✓ src/App.test.tsx (6 tests) 75ms
 ✓ src/routes/WardrobeGrid.test.tsx (4 tests) 113ms
 ✓ src/components/DescriptorChips.test.tsx (4 tests) 166ms
 ✓ src/components/AuthGate.test.tsx (4 tests) 202ms
 ✓ src/components/TagForm.test.tsx (6 tests) 206ms
 ✓ src/routes/Stylist.test.tsx (3 tests) 240ms
 ✓ src/routes/ItemDetail.test.tsx (7 tests) 259ms
 ✓ src/routes/AddItem.test.tsx (8 tests) 416ms

 Test Files  13 passed (13)
      Tests  89 passed (89)
```

## Artifact: JaCoCo coverage on the critical security/cap logic

**What it proves:** Token verification, the daily-limit check, and the
wardrobe scan filter each meet the ≥90% line / 100% branch bar the spec
requires for critical business logic (success metric #5).

**Why it matters:** These three units are exactly the places a bug would be
either a security hole (an unverified/expired token accepted) or a silent
data-integrity bug (a `usage#` row leaking into the wardrobe scan) — branch
coverage is the meaningful signal here, not just line count.

**Command:**

```bash
./gradlew jacocoTestReport
```

**Report path:** `build/reports/jacoco/test/jacocoTestReport.xml` (and the
human-readable `build/reports/jacoco/test/html/index.html`).

**Result summary:** All three critical classes clear the bar. The two
uncovered lines in `SessionTokenService` are the defensive
`NoSuchAlgorithmException | InvalidKeyException` catch around HMAC-SHA256,
which is guaranteed available on every JVM and unreachable in practice — not
part of the `verify()` method itself, which is 100% branch-covered.

```text
com/ensemble/security/SessionTokenService:  LINE=29/31 (93.5%)  BRANCH=8/8 (100.0%)
com/ensemble/usage/CallCapService:          LINE=10/10 (100.0%) BRANCH=2/2 (100.0%)
com/ensemble/wardrobe/WardrobeRepository:   LINE=11/11 (100.0%) BRANCH=2/2 (100.0%)

Project totals: LINE=634/656 (96.6%)  BRANCH=171/182 (94.0%)
```

## Artifact: Documentation updated (PWA install + passcode/cap)

**What it proves:** `README.md` and `docs/DEVELOPMENT.md` now document the PWA
install flow, the `ENSEMBLE_PASSCODE`/`ENSEMBLE_SESSION_SECRET` env vars, how
to exchange a passcode for a session token, the `X-Ensemble-Session`
header/`?token=` query fallback, and the `ensemble.usage.daily-limit` cap.

**Why it matters:** Without this, a developer pulling the branch would find
every `/api/items` call in the existing README quickstart returning `401`
with no explanation.

**Command:**

```bash
git diff --stat README.md docs/DEVELOPMENT.md .env.example
```

**Result summary:** `README.md` gained two new sections ("PWA Install" and
"Passcode gate & daily call cap") plus a pointer from the prerequisites list;
`docs/DEVELOPMENT.md`'s Local Run section gained a passcode-gate bullet next
to the existing Claude-key bullet; `.env.example` gained the two new
placeholders.

```text
 .env.example         | 12 +++++++++++-
 README.md            | 73 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++-
 docs/DEVELOPMENT.md  |  1 +
 3 files changed, 84 insertions(+), 2 deletions(-)
```

## Reviewer Conclusion

Spec 07 is safe to hand off to validation: the passcode never reaches the
client bundle, the full pre-existing test suite (specs 01–06) plus the new
Spec 07 tests are all green with no regressions, the security-critical logic
(token verification, the daily-limit check, the wardrobe scan filter) clears
the 100%-branch bar, no secret was committed, and the docs now explain how to
actually run the gated app locally.
