# Task 02 Proofs — Username-based accounts (frontend, docs, and gated local migration)

## Task Summary

This task finishes the email → username switch on the **frontend and in the docs**, and writes
the **operator-confirmed local-migration procedure**. Building on the Task 1.0 backend contract
(`{username,...}` bodies, `/api/me` → `{userId,username}`), it: renames the auth client
(`api/auth.ts`) and `AuthGate` to username (label, field id, `autoComplete`, state, error copy);
switches the frontend test selectors and copy assertions; rewords `.env.example`, `README.md`,
`docs/DEVELOPMENT.md`, and `docs/ARCHITECTURE.md` to username + `ENSEMBLE_SEED_USERNAME`; and adds
an explicit, operator-run table-recreation procedure. The API contract is unchanged (no backend
edits), and the migration performs **no automatic table drop** and **no enumerate-and-delete**.

Delivered under strict TDD (RED → GREEN) on the frontend meaningful logic; docs/migration follow
the project testing split (Diff/Doc-Log evidence, not unit tests).

## What This Task Proves

- The auth client posts `{ username, password }` to `/api/auth` and `{ username, password, passcode }`
  to `/api/accounts` — **no `email` key** in either body.
- `AuthGate` renders a **Username** field (label "Username", `id="auth-username"`, `type="text"`,
  `autoComplete="username"`) and shows username-worded error copy for every login/signup status.
- The gated-route guard (`App.test.tsx`) and all auth tests now anchor on the username label; the
  full frontend suite and ESLint pass.
- The docs/config describe username accounts and `ENSEMBLE_SEED_USERNAME`; the migration procedure
  is explicit and operator-gated, with the cloud/Terraform rename flagged as an out-of-band
  operator follow-up (audit FLAG 1).
- End-to-end against a live backend, a **username** login returns `/api/me` → `{userId, username}`
  (no email), non-enumerating `401`s and the duplicate `409` still hold, and the destructive
  migration touched only a self-created throwaway table.

## Evidence Summary

- **Frontend TDD:** RED captures show the intended failures (body was `{email}`; label `/^email$/i`
  not found); GREEN captures show them passing. Full suite **376 tests / 32 files passed**; `eslint`
  clean.
- **Backend regression (2.8):** a forced clean re-run (`--rerun-tasks`) is green —
  **58 classes, 427 tests, 0 failures / 0 errors / 0 skipped** — confirming no backend regression
  (Task 2.0 changed zero Java files).
- **Live `/api/me` transcript:** username signup → login → `/api/me` → `{userId, username}` against a
  running Spring Boot process on DynamoDB Local, using a throwaway `ensemble-users-proof34ux` table
  that was created on startup and deleted afterward; the dev `ensemble-users` table was never touched.
- **Docs diffs + greps:** account narrative reworded to username; no `email` anywhere in
  `frontend/src`; no auto-drop / enumerate-and-delete code; `ENSEMBLE_SEED_EMAIL` remains only in the
  intentional README follow-up note and in Terraform (out-of-band).

---

## Artifact: Frontend TDD — RED → GREEN (client + AuthGate)

**What it proves:** The client and UI were driven from failing tests that pin the username contract
and copy, then made to pass — the strict-TDD requirement for the frontend meaningful logic.

**Why it matters:** The RED output is the evidence the tests actually constrain behavior (body shape
and the username label), not just that green tests exist.

**Commands:** `cd frontend && npm test -- --run src/api/auth.test.ts` (then `src/components/AuthGate.test.tsx`)

**Result summary:** `auth.test.ts` RED = 2 failing on the `{email}`→`{username}` body assertion;
GREEN = 10/10. `AuthGate.test.tsx` RED = 10 failing on the `/^username$/i` label + reworded copy;
GREEN = 10/10.

```
# 2.1 RED (auth.test.ts, before auth.ts change)
 × login  → expected { email: 'jane_doe', … } to deeply equal { username: 'jane_doe', … }
 × signup → expected { email: 'new_user', … } to deeply equal { username: 'new_user', … }
      Tests  2 failed | 8 passed (10)

# 2.2 GREEN (auth.ts → {username,...})
      Tests  10 passed (10)

# 2.3 RED (AuthGate.test.tsx, before AuthGate.tsx change)
 × (all 10) → Unable to find a label with the text of: /^username$/i  (+ reworded copy)
      Tests  10 failed (10)

# 2.4 GREEN (AuthGate.tsx → Username field + reworded errorMessageFor)
      Tests  10 passed (10)
```

## Artifact: Full frontend suite + lint

**What it proves:** The username switch (client, AuthGate, gated-route selector in `App.test.tsx`)
causes no regression across the whole frontend, and the code is lint-clean.

**Why it matters:** This is the acceptance evidence that the meaningful frontend logic is correct and
nothing else broke.

**Commands:** `cd frontend && npm test -- --run` then `npm run lint`

**Result summary:** All suites green; ESLint exits clean with no output.

```
 Test Files  32 passed (32)
      Tests  376 passed (376)
   Duration  3.07s

> ensemble-frontend@0.0.1 lint
> eslint .
        (no errors)
```

## Artifact: Live `/api/me` end-to-end transcript (migrated identity)

**What it proves:** Against a running backend, a **username** login yields a token whose `/api/me`
returns `{userId, username}` (no email); signup `201`, the non-enumerating `401` (unknown user ==
wrong password), the duplicate `409`, and the sanitized invalid-username `400` all hold on the wire.

**Why it matters:** Demonstrates the migrated identity end-to-end, not just in mocked unit tests.

**Setup (non-destructive):** the app was run from a freshly built jar with
`ENSEMBLE_USERS_TABLE_NAME=ensemble-users-proof34ux` (a throwaway table auto-created on startup with
the `username` key, matching the repo's `ensemble-users-proof14` precedent) and a seed account
(`ENSEMBLE_SEED_USERNAME=jane_doe`). Per Resolved Decision D2 and the standing "only delete what I
created" rule, the dev `ensemble-users` table (email-keyed, live data) was never touched; the
throwaway table was deleted afterward. All credentials are synthetic; tokens truncated + `REDACTED`.

```
=== health gate ===                     {"status":"ok"}

1) Signup "Jane_Doe" (== seeded jane_doe) → 409  {"error":"conflict","message":"username already registered"}
2) Login "jane_doe"                       → 200  {"token":"YzcwYzZj…REDACTED"}
3) GET /api/me  (login token)             → 200  {"userId":"c70c6cc2-…-237340c689b1","username":"jane_doe"}
4) Wrong password                         → 401  {"error":"unauthorized","message":"invalid username or password"}
5) Unknown username (IDENTICAL 401)       → 401  {"error":"unauthorized","message":"invalid username or password"}
6) Duplicate signup                       → 409  {"error":"conflict","message":"username already registered"}
7) Fresh signup "fresh_user"              → 201  {"token":"MDY2MGRh…REDACTED"}
8) GET /api/me  (fresh_user token)        → 200  {"userId":"0660da95-…-a9e8d58f75bf","username":"fresh_user"}
9) Invalid username "_bad" (leading sep)  → 400  {"error":"bad_request","message":"invalid request"}
```

**Cleanup verification (only the self-created table removed):**

```
tables BEFORE: ['ensemble-items','ensemble-outfits','ensemble-users','ensemble-users-proof14','ensemble-users-proof34ux']
deleted:       ensemble-users-proof34ux
tables AFTER:  ['ensemble-items','ensemble-outfits','ensemble-users','ensemble-users-proof14']
```

## Artifact: Backend regression re-run (2.8)

**What it proves:** No backend regression from the account-UX branch; `/api/me`'s username shape and
the non-enumeration/duplicate invariants remain green.

**Why it matters:** Task 2.0 makes no backend edits, so a full green re-run is the regression guard
the task requires.

**Command:** `./gradlew test -PskipFrontend --rerun-tasks`

**Result summary:** `BUILD SUCCESSFUL in 22s` (all tasks executed, not cached). Aggregated across the
JUnit XMLs: **classes=58 tests=427 failures=0 errors=0 skipped=0**, including the DynamoDB-Local
TestContainers ITs.

```
BUILD SUCCESSFUL in 22s
classes=58  tests=427  failures=0  errors=0  skipped=0
```

## Artifact: Docs / config reword (email → username)

**What it proves:** The account narrative and seed env var now describe username end-to-end.

**Why it matters:** Docs are the human-facing contract; leaving email would misrepresent the app.

**Files:** `.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md`

**Result summary:** representative diffs —

```
.env.example:        -ENSEMBLE_SEED_EMAIL=            +ENSEMBLE_SEED_USERNAME=demo_user
DEVELOPMENT.md:      "email/password accounts … {email,password}"  →  "username/password accounts … {username,password}"
ARCHITECTURE.md:     "Accounts live in a separate email-keyed table"  →  "… keyed on `username` (the partition key; formerly `email`)"
README.md:           login/signup bodies {username,...}; /api/me → {userId,username}; ENSEMBLE_SEED_USERNAME; username charset/length/case rules
```

## Artifact: Migration procedure + destructive-migration hygiene greps

**What it proves:** The local `ensemble-users` recreation is an explicit, operator-run step; the app
adds no automatic table drop and never enumerates-and-deletes rows; the cloud/Terraform rename is a
documented out-of-band operator follow-up (audit FLAG 1).

**Why it matters:** Closes the destructive-migration safety property (D2 + the standing cleanup rule)
by inspection, per the project testing split (migration/infra is not unit-tested).

**Procedure location:** `README.md` — new subsection "Migrating a local `ensemble-users` table
(email → username)" in the Passcode-gate section (stop app → `aws dynamodb delete-table
--table-name ensemble-users --endpoint-url http://localhost:8000` → restart to auto-recreate with
the username key → set `ENSEMBLE_SEED_USERNAME`/`ENSEMBLE_SEED_PASSWORD` and restart to reseed),
plus an "Operator follow-up (cloud, out-of-band)" note; cross-referenced from `docs/DEVELOPMENT.md`
and `docs/ARCHITECTURE.md`.

**Result summary:** no `deleteTable` / enumerate-and-delete in app source (the `scan()` hits are
pre-existing read scans, incl. the documented `UserRepository.findByUserId` `/api/me` scan); no
`email` anywhere in `frontend/src`; `ENSEMBLE_SEED_EMAIL` survives only in the intentional README
follow-up note and in Terraform (out-of-band).

```
A) deleteTable / scan-delete in src/main/java         → NONE (only pre-existing read scans)
B) 'email' in frontend/src/api/auth.ts, AuthGate.tsx, App.tsx  → NONE
C) 'email' anywhere in frontend/src                   → NONE
D) ENSEMBLE_SEED_EMAIL in .env.example/README/docs    → README.md:257 only (the follow-up note)
E) ENSEMBLE_SEED_EMAIL in terraform/                  → variables.tf / apprunner.tf / secrets.tf (out-of-band follow-up)
```

## Reviewer Conclusion

The email → username switch is complete on the frontend and in the docs: the client and `AuthGate`
use username with reworded copy (driven by RED→GREEN tests), the full frontend suite (376) and lint
pass, and a live transcript shows the migrated identity resolving through `/api/me` with the
non-enumeration, duplicate, and validation invariants intact. The backend re-run stays green (427).
The local migration is documented as an explicit operator-run recreation with the cloud rename
flagged out-of-band, and inspection confirms no automatic drop or enumerate-and-delete was added —
the destructive-migration hygiene property holds.
