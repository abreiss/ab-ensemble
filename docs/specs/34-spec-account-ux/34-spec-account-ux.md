# 34-spec-account-ux.md

> GitHub issue: [#34 — Account UX: sign-out, password confirmation, and username-based signup](https://github.com/abreiss/ab-ensemble/issues/34)
> Depends on #14 (email/password accounts) and #15 (per-user data scoping). Spec sequence `34` matches the issue number.

## Introduction/Overview

Ensemble accounts today are email/password, invite-only, with a stateless HMAC session token. This feature delivers three account-UX improvements from issue #34: a **sign-out control**, a **confirm-password field** on signup, and a switch from **email to username** as the account identity. The username change is a real data-model migration — email is currently the DynamoDB partition key of the `ensemble-users` table — while sign-out and confirm-password are focused frontend additions. The invite passcode, bcrypt hashing, and the opaque-`userId` session token are all preserved unchanged.

## Goals

- Let a signed-in user **sign out** and return to the login gate, using the existing stateless-token design (client-side token discard).
- Add a **confirm-password** field to signup that catches typos client-side without changing the API contract.
- Replace **email with username** end-to-end (signup, login, storage, seed account, `/api/me`, all copy), preserving the non-enumerating `401`, the duplicate `409`, the invite passcode, and the session token.
- Keep an existing valid session token working across the switch (the token carries only the opaque `userId`, never email).
- Uphold the project's strict-TDD bar on backend domain code (≥90% line; 100% branch on username validation, non-enumeration, and duplicate-username uniqueness).

## User Stories

- **As a signed-in user**, I want a visible sign-out control so that I can leave the app on a shared device and require a fresh login to get back in.
- **As a new user signing up**, I want a confirm-password field so that a typo in my password doesn't lock me out of the account I just created.
- **As a user**, I want to sign up and log in with a **username** instead of an email so that I don't have to share an email address to use the app.
- **As the operator**, I want the username migration to be explicit and safe (no silent table drops) so that I recreate the account table deliberately, not as a surprise side effect.

## Demoable Units of Work

> _Testing note (applies to every unit):_ Follow strict TDD (RED → GREEN → REFACTOR) per `AGENTS.md` / `docs/TESTING.md`. **Backend domain** code (User model, repository, DTO validation, controllers, seed) is held to ≥90% line and **100% branch** on critical logic (username validation, non-enumeration, duplicate-username uniqueness, seed idempotency). **Frontend** units test the meaningful logic (sign-out clears token and returns to the gate; confirm-password mismatch blocks submit; the request body carries `username`, not `email`, and never the confirmation value) without over-testing view plumbing. Never call the live Claude API or a live network in tests.

### Unit 1: Username-based accounts — backend

**Purpose:** Replace email with username across the account data model, repository, DTOs, controllers, exceptions, seed, and `/api/me`. This is the strict-TDD backend core; email is removed entirely.

**Functional Requirements:**
- The system shall make **`username`** the DynamoDB partition key of the `ensemble-users` table: `@DynamoDbPartitionKey` moves to `User.getUsername()`, the `email` field is **removed**, and `User.normalizeEmail` becomes `User.normalizeUsername` (`trim().toLowerCase(Locale.ROOT)`, null-safe) for **case-insensitive** store-and-lookup.
- The system shall expose **`POST /api/accounts`** accepting `{ username, password, passcode }`; on success it shall create the account and return **`201`** with `{ token }` (auto-login), the token carrying the new `userId` (unchanged token contract).
- Request binding shall enforce **Jakarta Bean Validation** on `SignupRequest`: `username` `@NotBlank` + `@Size(min = 3, max = 30)` + `@Pattern` enforcing charset `[A-Za-z0-9._-]` with **no leading or trailing separator** (representative pattern `^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$`); `password` keeps `@NotBlank` + `@Size(min = 8)` + `@MaxUtf8Bytes(72)`; `passcode` keeps `@NotBlank`. A binding failure shall return the shared sanitized **`400`** (`{"bad_request","invalid request"}`, no field echo) via `ApiExceptionHandler`, **before** any persistence.
- The system shall expose **`POST /api/auth`** accepting `{ username, password }`; `AuthRequest`'s `@Email` is replaced by the same username `@Size`/`@Pattern`. On unknown username **or** wrong password it shall return one non-enumerating **`401`** (`InvalidCredentialsException` → `{"unauthorized","invalid username or password"}`), retaining the **dummy-hash timing defense** (`PasswordHasher.matches` runs against a fixed dummy hash when the username is unknown).
- The system shall enforce **username uniqueness atomically** via a conditional put on **`attribute_not_exists(username)`**; a duplicate (post-normalization) shall throw **`DuplicateUsernameException`** (renamed from `DuplicateEmailException`) → **`409`** `{"conflict","username already registered"}`, and shall not overwrite the existing row.
- The system shall change **`GET /api/me`** to return `{ userId, username }` (no `email`); `MeResponse` and the `MeController` mapping drop the email field.
- The system shall seed a default account keyed on **username**: `SeedProperties` and `SeedAccountRunner` read **`ENSEMBLE_SEED_USERNAME`** / `ENSEMBLE_SEED_PASSWORD`, remain idempotent via `findByUsername`, and bypass the signup passcode. `application.yml` binds `ensemble.seed.username: ${ENSEMBLE_SEED_USERNAME:}`.
- The system shall update `DynamoDbTableInitializer.USERS_PARTITION_KEY` from `"email"` to `"username"`; the users table remains GSI-free (looked up by username key GET only).
- All account error/log copy shall reword "email" → "username"; the raw password shall never be stored, returned, or logged (unchanged); `UserRepository.findByEmail` becomes `findByUsername`.

**Proof Artifacts:**
- Test: `AccountControllerTest` passes `validSignup_returns201WithToken`, `duplicateUsername_returns409`, `wrongSignupPasscode_returns401_noUserCreated`, `blankOrShortPassword_returns400`, `invalidUsername_returns400` (blank, too short, too long, illegal char, leading/trailing separator), `passwordOver72Bytes_returns400` — demonstrates the signup contract and every username-validation branch.
- Test: `AuthControllerTest` passes `validLogin_returns200WithToken`, `wrongPassword_returns401Generic`, `unknownUsername_returns401Generic` (same body `{"unauthorized","invalid username or password"}` **and** `verify(passwordHasher).matches(...)` proving the dummy-hash timing path runs) — demonstrates non-enumerating login.
- Test: `UserRepositoryIT` (DynamoDB Local via TestContainers) passes `createThenFindByUsername`, `create_duplicateUsername_throwsDuplicateUsernameException` (first row not overwritten), `findByUsername_isCaseAndSpaceInsensitive` — demonstrates atomic conditional-put uniqueness on a real round-trip.
- Test: `SeedAccountRunnerTest` + `SeedPropertiesTest` pass `seedsWhenAbsentAndConfigured` (username normalized, hash not raw), `skipsWhenAccountExists` (idempotent via `findByUsername`), `noOpWhenUnconfigured`, `configured()` true only when both set — demonstrates username-keyed seed idempotency.
- Test: `SessionAuthFilterTest.me_withValidToken_returns200WithUserIdAndUsername` asserts `$.userId` and `$.username` (no `$.email`) — demonstrates the new `/api/me` shape.
- CLI: `curl -s -X POST /api/accounts -d '{"username":"jane_doe","password":"correcthorse","passcode":"<invite>"}'` → `201` + token; repeating same username → `409`; `POST /api/auth {"username":"jane_doe","password":"correcthorse"}` → `200` + token; wrong password or unknown username → identical `401` — demonstrates end-to-end username signup + login.
- Coverage report: JaCoCo shows username validation, the login non-enumeration path, and the duplicate-username conditional at 100% branch — demonstrates the critical-logic coverage bar.

### Unit 2: Username-based accounts — frontend, docs, and local migration

**Purpose:** Surface the username field in the auth UI, drop email everywhere in the frontend, update the docs/config, and perform the account-table migration safely at demo scale.

**Functional Requirements:**
- The frontend shall replace the email field in `AuthGate` with a **Username** field: label "Username", `id="auth-username"`, dropping `type="email"`/`autoComplete="email"` (use `autoComplete="username"`); state/setters rename `email` → `username`; `canSubmit` and `handleSubmit` use `username`.
- `frontend/src/api/auth.ts` shall expose `login(username, password)` and `signup(username, password, passcode)` posting bodies `{ username, password }` and `{ username, password, passcode }` respectively — **no email anywhere** in the auth UI or client.
- The frontend error copy shall reword to username: login `401`/`400` and signup `409`/`400`/`401` messages replace "email" with "username" (e.g. "Invalid username or password.", "That username is already registered.").
- The migration shall **recreate** the `ensemble-users` table with `username` as the partition key (DynamoDB cannot change a key schema in place): **locally**, drop the DynamoDB-Local `ensemble-users` table and let dev startup auto-recreate it, then reseed via `ENSEMBLE_SEED_USERNAME`. The migration step shall be **explicit and confirmed** — the implementation shall **not** auto-drop any table without an explicit operator confirmation, and shall never enumerate-and-delete rows from a shared store.
- The docs shall be updated to describe username accounts and `ENSEMBLE_SEED_USERNAME`: `.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md` (which currently narrate email/password and `ENSEMBLE_SEED_EMAIL`).

**Proof Artifacts:**
- Test: `frontend/src/api/auth.test.ts` passes updated cases asserting `login` posts `{ username, password }` to `/api/auth` and `signup` posts `{ username, password, passcode }` to `/api/accounts` (no `email` key) — demonstrates the client contract.
- Test: `frontend/src/components/AuthGate.test.tsx` passes with the field found via `getByLabelText(/^username$/i)` and asserts the reworded error copy — demonstrates the UI uses username.
- Screenshot: the login and sign-up screens rendered in the app showing the **Username** field (no email field), and the app after a successful username login — demonstrates the end-to-end UI.
- CLI: `curl -s localhost:8080/api/me -H 'X-Ensemble-Session: <token>'` → `{"userId":"…","username":"…"}` after a username login — demonstrates the migrated identity end-to-end.
- Diff: `.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md` updated to username + `ENSEMBLE_SEED_USERNAME` — demonstrates the docs reflect the account model.

### Unit 3: Sign-out control

**Purpose:** Let a signed-in user discard their session token and return to the login gate, honoring the stateless-token design (no server-side revocation).

**Functional Requirements:**
- The frontend shall render a visible **Sign out** control in the app header/nav (`frontend/src/App.tsx`), shown only when signed in.
- Clicking Sign out shall call **`clearToken()`** and then trigger the same re-auth path `AuthGate` already uses — dispatching the existing **`ensemble:auth-required`** window event — so `AuthGate` flips `authenticated` to `false` and renders the login form; the wardrobe/build/saved views become unreachable without logging in again.
- After sign-out, `sessionStorage` shall no longer hold `ensemble.session.token`; a subsequent gated `/api/**` call would `401`, and logging back in shall restore access.
- Sign-out shall make **no backend change** (the discarded token remains technically valid until its 12h TTL expires — an accepted property of the stateless design).

**Proof Artifacts:**
- Test: an `App`/`AuthGate` RTL test passes `signOut_clearsTokenAndReturnsToLogin` — seeds a token, renders the app shell, clicks **Sign out**, and asserts `getToken()` is `null` and the login form (`getByLabelText(/^username$/i)`) is shown — demonstrates the client-side discard + gate return.
- Screenshot: the app header showing the **Sign out** control while signed in, and the login screen shown immediately after clicking it — demonstrates the visible control and its effect.

### Unit 4: Password confirmation on signup

**Purpose:** Catch password typos at signup with a confirm-password field, entirely client-side (the API contract is unchanged).

**Functional Requirements:**
- The frontend shall render a **Confirm password** field **only in signup mode** (`AuthGate` `isSignup` block).
- When the two password values do not match, the frontend shall **block submit** and show an inline `field-error` message, mirroring the inline-validation pattern in `frontend/src/components/TagForm.tsx` + `lib/tagValidation.ts` (per-field error, shown after interaction; submit disabled/blocked while invalid). When they match, signup proceeds.
- The confirmation value shall **never** be sent to the API: the `POST /api/accounts` body remains `{ username, password, passcode }` — the backend `SignupRequest` stays single-password (no backend change).
- Login mode shall be unchanged (no confirm field).

**Proof Artifacts:**
- Test: `frontend/src/components/AuthGate.test.tsx` passes `signup_confirmMismatch_blocksSubmitAndShowsError`, `signup_confirmMatch_submits`, and `signup_body_excludesConfirmValue` (posted body has no confirmation key) — demonstrates the client-side check and the unchanged contract.
- Screenshot: the signup screen showing the **Confirm password** field with a visible mismatch error, and a successful signup when the values match — demonstrates the UX.

## Non-Goals (Out of Scope)

1. **Admin view** — removed from the original ticket; no account-management/admin UI is added.
2. **Confirm-email flow** — moot once email is gone; not implemented.
3. **Server-side token revocation / logout endpoint** — sign-out is a client-side token discard only, matching the stateless HMAC token design (Resolved Decision D3). No session store or revocation list is added.
4. **Password reset / recovery** — out of scope; no reset flow, email or otherwise.
5. **Migrating existing email accounts to usernames** — at demo scale there are no real users to preserve; the account table is **recreated**, not migrated (Resolved Decision D2). No backfill or dual-read.
6. **Changing the session token contract** — the opaque-`userId` HMAC token, its 12h TTL, and the `X-Ensemble-Session` header are unchanged; existing tokens keep working.
7. **Adding a `userId` GSI to the users table** — `/api/me`'s `findByUserId` scan is unchanged (demo-scale, per ARCHITECTURE.md); not part of this work.

## Design Considerations

- **Sign-out placement:** the control lives in the existing `<nav className="app-nav">` in `App.tsx` (alongside Saved/Build/Wardrobe/+Add). It is the only new persistent chrome. Style it consistently with the existing nav controls (Care Label styling).
- **Confirm-password:** reuse the established inline-validation look — a `<span className="field-error">` under the field, revealed after interaction, with the submit button disabled while invalid — matching `TagForm`. Keep it signup-only.
- **Username field:** plain text input labelled "Username", `autoComplete="username"`; no email affordances.

## Repository Standards

- **Strict TDD (RED → GREEN → REFACTOR)** per `AGENTS.md` / `docs/TESTING.md`. Backend domain: **≥90% line** and **100% branch** on critical logic — username validation (charset/length/boundary branches), login non-enumeration, the duplicate-username conditional, and seed idempotency. Frontend: test the meaningful logic (sign-out, confirm mismatch, request-body shape) only; do not over-test view plumbing.
- **Layered architecture** (controllers → services → repositories); DTOs at the API boundary — no DynamoDB items or the token internals leak into controllers.
- **Mock external boundaries** in unit tests; real round-trips via **DynamoDB Local (TestContainers)** for repository ITs. No live network in any test.
- **Errors** reuse the shared `ApiExceptionHandler` shape (`{error, message}`), with generic sanitized `400` and non-enumerating `401`.
- **Conventional commits, roughly one per demoable unit.** Pre-commit hooks (tests, format/lint, **secret scan**) must pass. Commit messages end with the project `Co-Authored-By` trailer.

## Technical Considerations

- **Backend rename surface (Unit 1):** `User` (`@DynamoDbPartitionKey` → `getUsername()`, drop `email`, `normalizeEmail` → `normalizeUsername`); `UserRepository` (`findByEmail` → `findByUsername`, conditional put `attribute_not_exists(username)`, `DuplicateEmailException` → `DuplicateUsernameException`); `SignupRequest` + `AuthRequest` (`@Email` → username `@Size`/`@Pattern`); `AccountController` + `AuthController` (rename, `findByUsername`, reworded messages, dummy-hash timing retained); `InvalidCredentialsException` (message "invalid username or password"); `ApiExceptionHandler` (401/409 messages); `SeedAccountRunner` + `SeedProperties` (`ENSEMBLE_SEED_USERNAME`); `MeController`/`MeResponse` (drop email); `DynamoDbTableInitializer.USERS_PARTITION_KEY = "username"`; `application.yml` seed binding. Keep the custom `@MaxUtf8Bytes(72)` password constraint.
- **Username validation:** apply `@Size(min=3,max=30)` + `@Pattern` (charset + no leading/trailing separator) to the raw input; normalize to lowercase only for storage/lookup, so case-insensitive uniqueness holds. Cover every rejection branch (blank, short, long, illegal char, leading/trailing separator) for 100% branch coverage.
- **Sign-out wiring (Unit 3):** `AuthGate` currently flips `authenticated` to `false` only via the `ensemble:auth-required` event or a remount. The minimal, no-refactor path is for the Sign-out handler in `App.tsx` to call `clearToken()` then `window.dispatchEvent(new CustomEvent('ensemble:auth-required'))`, reusing the exact machinery a 401 already uses (Resolved Decision D3). Lifting auth state into a context is unnecessary.
- **Confirm-password (Unit 4):** pure client-side; reuse the `TagForm`/`tagValidation` pattern (per-field error + `touched` + submit gate). Do not thread the confirm value into `api/auth.signup`.
- **Migration (Unit 2) — destructive, gated:** DynamoDB cannot alter a table's key schema in place, so `ensemble-users` must be recreated. `DynamoDbTableInitializer.ensureTable` is idempotent (skips existing tables), so simply changing the key constant will **not** recreate an already-existing local table — the stale email-keyed table must be dropped first. **Locally:** the operator drops the DynamoDB-Local `ensemble-users` table, dev startup auto-recreates it with the username key, and `ENSEMBLE_SEED_USERNAME` reseeds. **Cloud:** the table is Terraform-owned; recreation is a deliberate operator Terraform change, out of band. The implementation must **confirm before dropping** any table and must never enumerate-and-delete rows from a shared/dev store.
- **Token compatibility:** because the session token carries only the opaque `userId` (never email), an existing valid token keeps resolving after the switch — verify no token/format change is needed.
- **Test updates:** existing suites assert email — update `UserTest`, `UserRepositoryIT`, `AccountControllerTest`, `AuthControllerTest`, `SessionAuthFilterTest` (`/api/me` shape + open-route bodies), `SeedAccountRunnerTest`, `SeedPropertiesTest`, `DynamoDbTableInitializerIT` (users table partition key), and frontend `AuthGate.test.tsx` / `auth.test.ts` / `App.test.tsx` (gated-route selector uses the username label).

## Security Considerations

- **No user enumeration:** login returns one generic `401` for unknown username or wrong password, and the dummy-hash timing-equalizer path is retained so response timing does not distinguish the two — verified by an explicit `passwordHasher.matches` assertion on the unknown-username branch.
- **Invite gate preserved:** the shared `ENSEMBLE_PASSCODE` signup passcode (constant-time SHA-256 compare) still gates `POST /api/accounts`; a blank/unset passcode still closes signup.
- **Session token unchanged:** opaque HMAC-signed `userId`, 12h TTL, never in the client bundle. The `ENSEMBLE_SESSION_SECRET` / invite-code coupling and the cloud-profile fail-closed behavior from #14/#15 are untouched — per-user isolation remains only as trustworthy as the token.
- **Sign-out residual risk:** because tokens are stateless, a signed-out token remains valid until its TTL expires. This is an accepted property of the design (no revocation store); the mitigation is the short 12h TTL. `sessionStorage` (not `localStorage`) already clears on tab close.
- **Destructive migration hygiene:** the account-table recreation is gated behind explicit operator confirmation — no automated table drop, no row enumeration-and-delete against a shared store.
- **No secrets in proofs:** proof artifacts (curl output, screenshots) must use synthetic usernames/passwords and must not embed the session secret, invite passcode, or any real credential.

## Success Metrics

1. **Sign-out works**: clicking Sign out clears the token and returns to the login gate; a subsequent gated call `401`s — demonstrated by the `signOut_clearsTokenAndReturnsToLogin` RTL test and the header/login screenshots.
2. **Confirm-password guards typos**: mismatched confirm blocks submit with an inline error; matching submits; the confirm value never reaches the API — demonstrated by the `AuthGate.test.tsx` confirm cases.
3. **Username replaces email end-to-end**: signup/login/`/api/me` use username, no `email` remains in the auth path or UI — demonstrated by backend controller/IT tests, the frontend `auth.test.ts`/`AuthGate.test.tsx`, and the `/api/me` CLI proof.
4. **Non-enumeration preserved**: unknown username and wrong password return an identical `401` with the timing defense intact — demonstrated by `AuthControllerTest`.
5. **Uniqueness is atomic**: a duplicate username returns `409` without overwriting the existing row — demonstrated by `UserRepositoryIT` + `AccountControllerTest`.
6. **Token compatibility**: an existing valid token keeps working across the switch — demonstrated by `SessionAuthFilterTest` passing with the unchanged token contract.
7. **Coverage**: ≥90% line and **100% branch** on username validation, login non-enumeration, the duplicate-username conditional, and seed idempotency (JaCoCo).
8. **No regressions**: all pre-existing backend and frontend tests pass with the account changes in place (stylist/wardrobe/daily-cap flows unchanged).

## Resolved Decisions (adopted defaults from the issue)

These are the issue's stated defaults, adopted here to avoid a blocking questions round. They are called out for sign-off at spec review; changing any is a small edit, not a re-plan.

1. **D1 — Username rules:** 3–30 chars, charset `[A-Za-z0-9._-]`, no leading/trailing separator, **case-insensitive** uniqueness (normalize to lowercase). Adjustable at review if you want stricter/looser.
2. **D2 — Migration:** wipe and recreate `ensemble-users` with the username key and reseed; do **not** migrate existing email accounts (demo scale, no real users). The drop is operator-confirmed, never automatic.
3. **D3 — Sign-out mechanism:** client-side token discard that dispatches the existing `ensemble:auth-required` event; no server-side revocation, no logout endpoint.
4. **D4 — Confirm-password:** client-side only; the confirmation value is never sent to the API and the backend `SignupRequest` stays single-password.
5. **D5 — Seed env var:** `ENSEMBLE_SEED_EMAIL` → `ENSEMBLE_SEED_USERNAME` (paired with the existing `ENSEMBLE_SEED_PASSWORD`).

## Open Questions

_All items below are non-blocking: they do not change the units, proof artifacts, or acceptance criteria — each states the default taken._

1. **Reserved usernames** — no denylist (e.g. `admin`, `root`) is applied; any username matching the charset/length rules is accepted. Default unless you want a small reserved list.
2. **Username display casing** — the stored/normalized value is lowercase; the UI shows what the user typed on the form but has no separate "display name" preserving original casing. Default: no display-name field.
3. **Sign-out confirmation prompt** — clicking Sign out signs out immediately with no "are you sure?" dialog. Default: no confirmation step (a re-login is cheap).
