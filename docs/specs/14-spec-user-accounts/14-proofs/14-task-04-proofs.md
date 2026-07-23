# Task 04 Proofs — Seed account, login/sign-up UI, and documentation

## Task Summary

This task makes the account model **usable end-to-end and provides the transition path**. On
the backend, a `SeedAccountRunner` (`ApplicationRunner`) idempotently creates a default account
from `ENSEMBLE_SEED_EMAIL` / `ENSEMBLE_SEED_PASSWORD` on startup — bypassing the signup passcode
server-side (no HTTP round trip), so there is a usable login before anyone knows the invite code.
On the frontend, the old single-passcode `AuthGate` becomes a **toggleable email/password
login ↔ invite-only sign-up** form in the Care Label design system, and `api/auth.ts` gains
`login(email,password)` / `signup(email,password,passcode)`. Docs (`ARCHITECTURE.md` Security,
`README.md`, `docs/DEVELOPMENT.md`), `.env.example`, and Terraform (`secrets.tf` / `apprunner.tf`)
are updated to describe the account model, the signup-passcode's new role, and the seed account.

## What This Task Proves

- **Idempotent, env-gated seed (transition path).** `SeedAccountRunner` seeds when both env vars
  are set and the email is absent, **skips** when the account already exists, and **no-ops** when
  unconfigured (both blank *or* half-configured) — the no-op path is what keeps every existing
  `@SpringBootTest` context green with no seed config and no live DynamoDB. Proven both by
  `SeedAccountRunnerTest` (100% branch) **and live** (the seeded account logs in on a freshly
  booted current-build server).
- **Password safety.** The seeded account stores only the bcrypt hash; the raw password never
  reaches the persisted record and is never logged (`SeedProperties.toString()` masks both fields).
- **Client auth-state + API logic.** `auth.ts` posts the right bodies to `/api/auth` and
  `/api/accounts`, stores the returned token, and throws a status-bearing `HttpError` on non-2xx.
  `AuthGate` renders the login screen with no token, toggles to sign-up and back, renders children
  after a successful login/sign-up, and maps `401`/`409`/`400` (and signup-`401`) to the correct
  non-enumerating inline copy.
- **Docs + config reflect the account model with no committed secret.** Terraform `fmt`/`validate`
  pass; `.env.example` and both `.tf` files carry only placeholders / empty strings / ARN refs.
- **No regressions.** Full backend (376) and frontend (374) suites green; lint + `tsc -b` clean;
  the pre-commit secret scans pass.

## Evidence Summary

- Backend: `./gradlew test -PskipFrontend` → **BUILD SUCCESSFUL, 376 tests, 0 failures/0 errors**
  (was 370 before this task — 6 new tests). JaCoCo: **`SeedAccountRunner` 100% line (21/21) +
  100% branch (4/4)**, **`SeedProperties` 100% line (9/9) + 100% branch (10/10)**;
  `DynamoDbTableInitializer` stays **100%/100%** after the `@Order` addition.
- Frontend: `npm run test -- --run` → **32 files, 374 tests, all passed**; `npm run lint` → exit 0;
  `tsc -b` → clean.
- Live end-to-end against a freshly booted current-build server (throwaway invite code + throwaway
  seed/account creds, isolated `ensemble-users-proof14ui` table, torn down after): seeded-account
  login `200`, signup `201`, duplicate `409`, wrong invite passcode `401`, short password `400`,
  login `200`, `GET /api/me` returns the new identity, wrong-password **and** unknown-email both
  return the **identical** generic `401`.
- Terraform `fmt -check -recursive` (exit 0) + `validate` → **Success**.
- Pre-commit `detect-private-key`, `block-anthropic-keys`, `block-aws-keys` → **all Passed**.
- **Screenshots:** intentionally **waived by the project owner for this task** ("I do not need
  them"). The login / sign-up / post-login behavior is instead proven by the 10 `AuthGate.test.tsx`
  RTL cases (which assert exactly those rendered states + error copy) and the live login round-trip
  below. No screenshot was fabricated.

## Artifact: Seed runner + config unit tests (strict TDD, 100% branch)

**What it proves:** The three seed decision branches (seed / skip-existing / no-op-unconfigured)
and the masked, both-required `SeedProperties` behave per spec — the idempotent transition path.

**Why it matters:** This is backend-domain code under strict TDD; a missed branch (e.g. seeding a
half-configured account, or touching a collaborator when unconfigured) would either break existing
`@SpringBootTest` contexts or create a half-baked account.

**Command:**

```bash
./gradlew test -PskipFrontend \
  --tests 'com.ensemble.user.SeedAccountRunnerTest' \
  --tests 'com.ensemble.config.SeedPropertiesTest'
./gradlew test -PskipFrontend jacocoTestReport   # coverage parsed from the XML report
```

**Result summary:** RED first (compile failure — neither class existed), then GREEN. Coverage
(parsed from `build/reports/jacoco/test/jacocoTestReport.xml`):

```
SeedAccountRunner         LINE 21/21 (100%)   BRANCH 4/4  (100%)
SeedProperties            LINE  9/9  (100%)   BRANCH 10/10 (100%)
DynamoDbTableInitializer  LINE 26/26 (100%)   BRANCH 2/2  (100%)   # unchanged by the @Order add
```

## Artifact: Full backend suite (no regressions)

**What it proves:** The new `@Component` runner + `SeedProperties` wire into every Spring context
without breaking it — in particular the runner no-ops in the test contexts (no seed config, mocked
`UserRepository`), so the `@SpringBootTest` slices still start with no live DynamoDB.

**Command:** `./gradlew test -PskipFrontend`

**Result summary:** `BUILD SUCCESSFUL` — **376 tests, 0 failures, 0 errors, 0 skipped** across 52
test classes.

## Artifact: Frontend auth client + AuthGate (RED → GREEN)

**What it proves:** `login`/`signup` request shape + token storage + status-bearing errors, and the
reworked login/sign-up UI with the exact non-enumerating error copy.

**Why it matters:** This is the meaningful frontend logic (state, API calls, status→copy mapping)
that `docs/TESTING.md` asks to cover; view plumbing is kept light.

**Command:**

```bash
cd frontend && npm run test -- --run   # whole suite
npm run lint                            # eslint
npx tsc -b                              # type-check
```

**Result summary:** `auth.test.ts` went RED (6 failing) → GREEN (8/8); `AuthGate.test.tsx` RED
(10 failing against the old component) → GREEN (10/10). Whole suite: **374 passed / 32 files**.
Lint exit 0; `tsc -b` clean. Error-copy mappings asserted: login `401` → "Invalid email or
password.", signup `409` → "That email is already registered.", `400` → "Password must be at least
8 characters.", signup `401` → "That signup code isn't valid."

## Artifact: Live end-to-end auth round-trip (current build, incl. seed runner)

**What it proves:** The wired app — bcrypt, the conditional-put uniqueness guard, token issue, the
filter's open routes, **and the new `SeedAccountRunner`** — behaves as specified against real
DynamoDB Local, not just against mocks.

**Why it matters:** The unit tests prove *our handling*; the stale long-running dev server on
:8080 predates this work, so a fresh current-build server was booted to prove the new code live.

**Command** (throwaway values only; server on :8090 with an isolated `ensemble-users-proof14ui`
table, both torn down afterward — the invite code `demo-invite-14` and seed/account creds were
disposable demo values):

```bash
SERVER_PORT=8090 ENSEMBLE_PASSCODE='demo-invite-14' \
  ENSEMBLE_SEED_EMAIL='seed14@example.com' ENSEMBLE_SEED_PASSWORD='seed-demo-pw-14aa' \
  ENSEMBLE_USERS_TABLE_NAME='ensemble-users-proof14ui' ./gradlew bootRun -PskipFrontend &
BASE=http://localhost:8090
```

**Result summary:** Every status matched the contract; the seeded account (created on boot by
`SeedAccountRunner`, never via HTTP) logs in successfully, and unknown-email / wrong-password are
byte-for-byte identical `401`s.

```
A. seeded-account login  -> 200 {"token":"<redacted>"}      # SeedAccountRunner ran on boot
1. signup (invite ok)    -> 201 {"token":"<redacted>"}
2. duplicate email       -> 409 {"error":"conflict","message":"email already registered"}
3. wrong invite passcode -> 401 {"error":"unauthorized","message":"invalid passcode"}   # no user
4. short password        -> 400 {"error":"bad_request","message":"invalid request"}
5. login                 -> 200 {"token":"<redacted>"}
6. GET /api/me           -> 200 {"userId":"b31f380b-…","email":"proof14-ui@example.com"}
7. wrong password        -> 401 {"error":"unauthorized","message":"invalid email or password"}
8. unknown email         -> 401 {"error":"unauthorized","message":"invalid email or password"}
```

## Artifact: Docs + Terraform config

**What it proves:** Docs and config describe the email/password account model, the signup-passcode's
new role, and the seed account — with no secret committed.

**Command:**

```bash
cd terraform/deploy && terraform fmt -check -recursive && terraform validate
pre-commit run detect-private-key block-anthropic-keys block-aws-keys --all-files
```

**Result summary:** `terraform fmt -check` exit 0; `terraform validate` → *Success! The
configuration is valid.* All three secret-scan hooks **Passed**. Edits: `docs/ARCHITECTURE.md`
Security bullet (account model replaces the stale "single-user passcode gate"); `README.md`
passcode-gate section + prerequisites open-route list + deploy runbook (now names the required vs
optional secret containers); `docs/DEVELOPMENT.md` account paragraph; `.env.example`
(`ENSEMBLE_PASSCODE` reworded to the signup gate + blank `ENSEMBLE_SEED_EMAIL`/`_PASSWORD`);
`terraform/deploy/secrets.tf` (two new container-only secrets) + `apprunner.tf` (two new
`runtime_environment_secrets` ARN refs).

## Reviewer Conclusion

The account model is now usable and documented: a bcrypt-hashed seed account is created idempotently
on startup (verified live, 100% branch), the frontend is a Care-Label login ↔ invite-only sign-up
form with correct non-enumerating error copy (10 RTL cases), and docs + Terraform reflect the model
with zero committed secrets. Backend (376) and frontend (374) suites are green; lint/type-check and
the secret scans pass. Screenshots were waived by the project owner; all functional behavior is
proven by tests plus the live end-to-end round-trip. All demo values are throwaway and the proof
server + its isolated table were torn down.
