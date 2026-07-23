# 14-spec-user-accounts.md

## Introduction/Overview

Ensemble currently gates the whole app behind a single **shared passcode**: `POST /api/auth`
checks it and mints a stateless session token that proves only *"this caller knows the
passcode."* There is no user identity anywhere — every valid session is interchangeable
(`SessionAuthFilter`, `SessionTokenService`).

This feature replaces that shared passcode with **real email + password accounts**. A user
signs up (email + password, behind a shared **signup passcode** that keeps registration
invite-only), logs in, and receives a session token that resolves to a **durable `userId`**.
The request filter exposes that `userId` to controllers and services, so later work (#15) can
scope wardrobe data per user. The primary goal is a small, complete authentication slice:
**identity only** — not data scoping, not password reset, not OAuth/RBAC.

## Goals

- Introduce a **`User` record** in DynamoDB (email as partition key, generated `userId` UUID, a
  bcrypt password hash, `createdAt`), following the existing single-item, no-GSI data pattern.
- Add **`POST /api/accounts`** (sign up with email + password, behind the shared signup
  passcode) that validates input, rejects duplicate emails atomically, and hashes the password.
- **Repurpose `POST /api/auth`** to verify email + password and issue a session token that
  **carries a concrete `userId`**, not just "passcode known."
- Have **`SessionAuthFilter` resolve the authenticated `userId`** from the token and expose it
  to controllers/services, proven by a minimal **`GET /api/me`** endpoint.
- Store passwords **hashed at rest** (bcrypt, work factor 12) — never in plaintext, never
  logged — and return **clear, non-enumerating errors** for wrong password, duplicate email,
  and malformed input (no stack traces).
- Provide a **seed/default account** (from env) so the existing single user has a login on day
  one, and update `docs/ARCHITECTURE.md` + `README.md` to describe the account model.

## User Stories

- **As a new user**, I want to create an account with my email and a password, so that the app
  can recognize me as a distinct person rather than an anonymous passcode-holder.
- **As a returning user**, I want to log in with my email and password and stay logged in for a
  session, so that I don't have to re-authenticate on every request.
- **As the app owner paying for Claude**, I want account creation to require a shared signup
  passcode, so that a stranger who finds the URL can't register and run up my AI bill.
- **As the app owner**, I want passwords stored only as salted, slow hashes and never logged, so
  that a leak of the data store or logs does not expose anyone's password.
- **As a developer building wardrobe scoping (#15)**, I want each request to resolve to a
  concrete `userId`, so that I can key items, photos, and stylist results to the caller.
- **As the existing single user**, I want a ready-made account after this change ships, so that
  the app keeps working for me without a manual rebuild.

## Demoable Units of Work

### Unit 1: `User` record, repository, and password hashing

**Purpose:** The data + crypto foundation. Backend domain core under strict TDD. Serves every
later unit; no user-facing surface yet (proof is via tests).

**Functional Requirements:**
- The system shall define a **`User`** `@DynamoDbBean` with partition key **`email`** and
  attributes `userId` (UUID string, generated at creation), `passwordHash` (bcrypt), and
  `createdAt` (ISO-8601 instant), mirroring the existing `SavedOutfit`/`Item` bean pattern.
- The system shall **normalize email** (trim + lowercase) before it is used as the key, so that
  `Foo@X.com` and `foo@x.com` resolve to the same account.
- The system shall add a **`usersTableName`** field to `DynamoDbProperties`
  (`${ENSEMBLE_USERS_TABLE_NAME:ensemble-users}`) and a **`UserRepository`** that binds
  `enhancedClient.table(usersTableName, TableSchema.fromBean(User.class))`.
- `UserRepository` shall provide **`findByEmail(email)`** (GetItem on the normalized email) and
  **`create(user)`** that performs a **conditional put** (`attribute_not_exists(email)`); a
  conditional-check failure shall surface as a **`DuplicateEmailException`** (no silent
  overwrite).
- The system shall provide a **`PasswordHasher`** (wrapping `BCryptPasswordEncoder`, **work
  factor 12**) exposing `hash(raw)` and `matches(raw, hash)`, added via the standalone
  **`spring-security-crypto`** dependency (no full Spring Security starter, so the existing
  custom `SessionAuthFilter` is untouched).
- The dev table initializer (`DynamoDbTableInitializer`) shall **auto-create the users table**
  (`ensureTable(usersTableName, "email")`) under the same `auto-create-table` flag; the cloud
  profile shall keep auto-create disabled (table is Terraform-owned).

**Proof Artifacts:**
- Test: `UserRepositoryIT` (DynamoDB Local / TestContainers) passes `createThenFindByEmail`
  and `create_duplicateEmail_throwsDuplicateEmailException` — demonstrates the round-trip and the
  atomic uniqueness guard.
- Test: `PasswordHasherTest` passes `hashVerifiesAgainstRawPassword`,
  `wrongPasswordDoesNotMatch`, and `sameInputProducesDifferentSalts` — demonstrates bcrypt
  storage with 100% branch on the match path.
- Test: `UserTest` passes `emailIsNormalizedToLowercaseTrimmed` — demonstrates key normalization.

### Unit 2: Sign-up endpoint (`POST /api/accounts`) behind the signup passcode

**Purpose:** Let a user create an account, invite-only. Backend domain core under strict TDD.

**Functional Requirements:**
- The system shall expose **`POST /api/accounts`** accepting `{ email, password, passcode }`
  and, on success, create the account and return **`201`** with a session token `{ token }`
  (auto-login), where the token already carries the new `userId`.
- Request binding shall enforce **Jakarta Bean Validation**: `email` `@NotBlank` + `@Email`;
  `password` `@NotBlank` + length **≥ 8**; `passcode` `@NotBlank`. The controller shall use
  `@Valid` on the `@RequestBody`; a binding failure shall return the shared sanitized **`400`**
  (`"invalid request"`, no field echo) — consistent with `ApiExceptionHandler`.
- The system shall additionally reject a password whose **UTF-8 byte length exceeds 72** (bcrypt's
  input limit) with the same sanitized `400`, so no password is silently truncated.
- The system shall verify the submitted `passcode` against the configured signup passcode
  (`ensemble.security.passcode` / `ENSEMBLE_PASSCODE`) using the existing **constant-time**
  SHA-256 comparison; a wrong or blank passcode shall return **`401`** (`InvalidPasscodeException`,
  generic body) and shall **not** create a user. A **blank configured passcode** means signup is
  closed (every attempt `401`), matching today's "gate closed" behavior.
- On a **duplicate email** (post-normalization), the system shall return **`409`**
  (`DuplicateEmailException` → `{"error":"conflict","message":"email already registered"}`) and
  shall not create a second row.
- The password shall be **hashed before persistence**; the raw password shall never be stored,
  returned, or logged.
- The filter's open-route list shall be extended so **`POST /api/accounts` is reachable without a
  session token** (like `POST /api/auth` and `GET /api/health`).

**Proof Artifacts:**
- Test: `AccountControllerTest` (MockMvc) passes `validSignup_returns201WithToken`,
  `duplicateEmail_returns409`, `wrongSignupPasscode_returns401_noUserCreated`,
  `blankOrShortPassword_returns400`, `invalidEmail_returns400`, `passwordOver72Bytes_returns400`
  — demonstrates the signup contract and validation branches.
- CLI: `curl -s -X POST /api/accounts -d '{"email":"a@b.com","password":"correcthorse","passcode":"<signup>"}'`
  → `201` + token; repeating the same email → `409`; wrong passcode → `401` — demonstrates
  end-to-end sign-up + gate.

### Unit 3: Login (`POST /api/auth` repurposed), userId-bearing token, and resolved principal

**Purpose:** Turn login into identity. Backend domain core under strict TDD, including the
100%-branch-critical token logic.

**Functional Requirements:**
- The system shall change **`SessionTokenService`** so the signed payload encodes both the
  **`userId`** and the expiry (format: `base64url(userId + ":" + expiryEpochSeconds) + "." +
  base64url(HMAC_SHA256(payload, key))`). `issue(userId)` shall mint such a token;
  `verify(token)` shall return the **`userId`** (e.g. `Optional<String>`) for a well-formed,
  untampered, unexpired token and **empty** for anything malformed, tampered, or expired
  (constant-time HMAC compare retained).
- Session tokens issued under the old (identity-less) format shall be treated as invalid; users
  simply re-authenticate (tokens are short-lived, so no token migration is needed).
- The system shall repurpose **`POST /api/auth`** to accept `{ email, password }` (the passcode
  field is removed from login). On a valid email + password it shall return **`200`** with
  `{ token }` carrying that user's `userId`; on unknown email **or** wrong password it shall
  return the **same generic `401`** (`InvalidCredentialsException`, `"invalid email or
  password"`) so responses do not reveal whether an email is registered.
- To avoid a user-enumeration timing side-channel, login shall perform a **bcrypt comparison even
  when the email is not found** (against a fixed dummy hash) before returning the generic `401`.
- **`SessionAuthFilter`** shall, on a valid token, resolve the `userId` and **expose it to the
  request** (a request attribute, surfaced to controllers via a small argument resolver / current-
  user accessor) so downstream code reads the caller's `userId` without touching servlet
  internals. A missing/invalid token on a protected route shall still return `401` before the
  controller.
- The system shall add **`GET /api/me`** (protected) returning `{ userId, email }` for the
  authenticated caller — the demoable proof that the filter resolves a concrete principal.

**Proof Artifacts:**
- Test: `SessionTokenServiceTest` passes `issuesTokenCarryingUserId`,
  `verifyReturnsUserId`, `rejectsTamperedToken`, `rejectsExpiredToken`, `rejectsMalformedToken`,
  `rejectsOldIdentitylessToken` — demonstrates the identity-bearing token with **100% branch**.
- Test: `AuthControllerTest` (rewritten) passes `validLogin_returns200WithToken`,
  `wrongPassword_returns401Generic`, `unknownEmail_returns401Generic`, `malformedBody_returns400`
  — demonstrates the login contract and non-enumeration.
- Test: `SessionAuthFilterTest` passes `validToken_resolvesUserId_andPassesThrough`,
  `accountsAndAuthAndHealth_areOpen`, `me_withoutToken_returns401`,
  `me_withValidToken_returns200` — demonstrates the resolved principal and gate scope.
- CLI: log in via `POST /api/auth`, then `curl /api/me -H 'X-Ensemble-Session: <token>'` returns
  the caller's `{userId,email}`; without the header → `401` — demonstrates the principal
  end-to-end.

### Unit 4: Seed account, login/signup UI, and documentation

**Purpose:** Make the change usable and provide the transition path. Frontend (light testing per
the coverage split) + a small backend seed runner + docs.

**Functional Requirements:**
- The system shall provide a **seed-account runner** (an `ApplicationRunner`, like the table
  initializer) that, when `ENSEMBLE_SEED_EMAIL` and `ENSEMBLE_SEED_PASSWORD` are both set and no
  user with that (normalized) email exists, **creates that account** (hashed password). It shall
  be **idempotent** (skip if the account already exists) and a **no-op** (logged) when the env
  vars are unset. It shall not require the signup passcode (it is server-side provisioning).
  (Reassigning existing wardrobe rows to this account's `userId` is **#15's** responsibility, not
  this issue's.)
- The frontend shall **replace the single-passcode `AuthGate`** with a **login form** (email +
  password) and a **sign-up form** (email + password + signup passcode), toggleable, reusing the
  existing "Care Label" design system and mobile-first layout. On successful login **or** sign-up
  it shall store the returned token (unchanged transport: `sessionStorage` +
  `X-Ensemble-Session` header, `?token=` for `<img>`) and render the app.
- The frontend `api/auth.ts` shall expose **`login(email, password)`** (`POST /api/auth`) and
  **`signup(email, password, passcode)`** (`POST /api/accounts`), each storing the token; it shall
  keep `getToken`/`clearToken` and the existing `401 → clear token → re-auth` behavior.
- The UI shall surface **clear inline errors** for invalid credentials (`401`), duplicate email
  (`409`), weak/invalid input (`400`), and wrong signup passcode (`401`).
- **Docs:** `docs/ARCHITECTURE.md` ("Security") and the `README.md` passcode-gate section shall be
  updated to describe email/password accounts, the signup passcode's new role, the seed account,
  and that the old single-passcode login is retired. `.env.example` shall document
  `ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` (placeholders only).

**Proof Artifacts:**
- Test: `SeedAccountRunnerTest` passes `seedsWhenAbsentAndConfigured`,
  `skipsWhenAccountExists`, `noOpWhenUnconfigured` — demonstrates the idempotent transition path.
- Test: frontend `AuthGate`/`auth` tests (Vitest/RTL) pass — no token → login screen; toggle to
  sign-up; submit → token stored → app renders; `401`/`409`/`400` show the right inline error —
  demonstrates the client auth-state logic.
- Screenshot: the login and sign-up screens rendered in the app (Care Label styling), and the app
  rendered after a successful login — demonstrates the end-to-end UI.
- Diff: `docs/ARCHITECTURE.md`, `README.md`, and `.env.example` updated — demonstrates the docs
  reflect the account model.

## Non-Goals (Out of Scope)

1. **Wardrobe / photo / stylist data scoping by `userId`** — the actual per-user data isolation,
   the full-table-scan replacement, and the existing-data reassignment are **issue #15**. This
   issue only *produces* the resolvable `userId`.
2. **Password reset / forgot-password / email verification** — no email-delivery infrastructure
   exists; flag reset as a possible follow-up if the demo needs it.
3. **OAuth / social login / SSO, roles / RBAC / permissions** — individual email+password
   accounts only.
4. **Token revocation / refresh / sliding sessions** — the token stays stateless (no server-side
   session store), 12h TTL, re-login on expiry.
5. **Per-user daily call cap** — the daily cap stays a single global counter; whether it becomes
   per-user is an explicit open question owned by **#15**.
6. **Account management UI** (change email/password, delete account, profile) — beyond the signup
   + login slice.
7. **Rate-limiting the signup/login endpoints** beyond the existing signup-passcode gate and
   bcrypt cost.

## Design Considerations

- The **login and sign-up screens** must use the existing **"Care Label" design system**
  (`frontend/src/index.css` `:root` tokens: `--paper #f3ecdd`, `--paper-raised #fcf8ef`,
  `--ink #33271f`, `--accent #7c2833`; Bricolage Grotesque / Space Mono; mobile-first `#root`
  max-width 30rem). Do not introduce a second visual language.
- Keep it a **single calm centered screen** with a **toggle** between "Log in" and "Create
  account." Inputs: `type="email"` (with `inputMode="email"`, `autoComplete="email"`) and
  `type="password"` (`autoComplete="current-password"` on login, `new-password` on sign-up);
  sign-up adds the passcode input (`type="password"`). Submit buttons ≥ 44px touch target;
  inline error text on failure. Respect `:focus-visible` and `prefers-reduced-motion` already in
  the system.
- Error copy is **user-friendly and non-enumerating**: "Invalid email or password." for login
  failures; "That email is already registered." for `409`; "Password must be at least 8
  characters." for weak input; "That signup code isn't valid." for a wrong signup passcode.

## Repository Standards

- **Strict TDD** for all backend domain code — the `User`/`UserRepository`, `PasswordHasher`, the
  repurposed `SessionTokenService`, the signup and login controllers, the `SessionAuthFilter`
  principal resolution, and the seed runner — RED → GREEN → REFACTOR; **≥90% line** and **100%
  branch** on the critical logic (token verify/parse, password verification, duplicate-email
  conditional, signup-passcode check, password strength/length validation). Frontend forms test
  meaningful state/API logic only, per `docs/TESTING.md`; view plumbing stays light.
- **Layered architecture**: controllers/filter → services (`SessionTokenService`,
  `PasswordHasher`) → repository (`UserRepository`) / config properties; DTOs (Java records) at
  the boundary; no DynamoDB items, crypto internals, or the enhanced client leak into controllers.
- **Config properties** follow the existing `@ConfigurationProperties(prefix = "ensemble.*")`
  record pattern; any secret is masked in `toString()`.
- **Mock external boundaries** in unit tests (no live Claude, no live network); the user
  repository's real round-trip uses **DynamoDB Local via TestContainers**, matching
  `WardrobeRepositoryIT`/`OutfitRepositoryIT`.
- **Errors** reuse the shared `ApiExceptionHandler` `ErrorResponse` shape; the new controller(s)
  must be **added to its `assignableTypes` allow-list**, and new statuses (`409`, plus reused
  `401`/`400`) map there (or in the filter) with the same sanitized body.
- Conventional commits, roughly one per demoable unit. Pre-commit hooks (fast tests + lint +
  **secret scan** — must not commit a password, seed credential, or the passcode) must pass.

## Technical Considerations

**Data + hashing (Unit 1)**
- `User` `@DynamoDbBean` copies the `SavedOutfit` template; `@DynamoDbPartitionKey` on the
  `email` getter. Add `usersTableName` to `DynamoDbProperties` and a `users` case to
  `DynamoDbTableInitializer.ensureTable(...)` (HASH `email`, `PAY_PER_REQUEST`). Add
  `aws_dynamodb_table.users` (hash `email`) to `terraform/deploy/data_stores.tf`; the existing
  IAM wildcard (`table/${prefix}-*`) already grants `GetItem/PutItem` on it — **no IAM change**.
- Atomic uniqueness: use the enhanced client's conditional `PutItemEnhancedRequest`
  (`conditionExpression` = `attribute_not_exists(email)`); catch `ConditionalCheckFailedException`
  → `DuplicateEmailException`.
- Add `org.springframework.security:spring-security-crypto` (version managed by the Spring Boot
  dependency BOM — the app is on Spring Boot 4.1.0 / Java 21). Use `BCryptPasswordEncoder(12)`.
  **Adding this jar alone does not enable any Spring Security auto-config or filter chain** — it
  is a plain utility, so the hand-rolled `SessionAuthFilter` is unaffected.

**Signup + login (Units 2–3)**
- New `SignupRequest` (record `String email, String password, String passcode`) and a repurposed
  `AuthRequest` (now `String email, String password`) with the validation annotations above.
- `AccountController` (`POST /api/accounts`): `@Valid` bind → signup-passcode check (reuse the
  existing constant-time SHA-256 compare, factored out of the old `AuthController`) → 72-byte
  guard → hash → `UserRepository.create(...)` → `tokenService.issue(userId)` → `201 { token }`.
- `AuthController` (`POST /api/auth`, rewritten): `@Valid` bind → `findByEmail` → bcrypt
  `matches` (dummy-hash compare when absent) → `tokenService.issue(userId)` → `200 { token }`, or
  generic `401`.
- `SessionTokenService`: extend the payload to `userId:expiry`; `verify` returns the `userId`.
  Keep it pure/Spring-free and `Clock`-injected. Update `SecurityConfig`'s bean wiring as needed.
- `SessionAuthFilter`: after `verify`, set the `userId` as a request attribute (e.g.
  `ensemble.userId`); add a lightweight `HandlerMethodArgumentResolver` (or accessor) so a
  controller can obtain the current `userId`. Extend `isOpen(...)` to also allow
  `POST /api/accounts`. Keep the filter registered at `HIGHEST_PRECEDENCE` via the existing
  `FilterRegistrationBean` so `@WebMvcTest` slices stay token-free.
- `MeController` (`GET /api/me`): reads the resolved `userId`, loads the email via
  `UserRepository`, returns `{ userId, email }`.
- Add `AccountController` and `MeController` to `ApiExceptionHandler`'s `assignableTypes`; add
  `DuplicateEmailException` → `409` and `InvalidCredentialsException` → `401` mappings.

**Seed + frontend + config (Unit 4)**
- `SeedAccountRunner` (`ApplicationRunner`), ordered after the table initializer; reads
  `ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` (via a small `@ConfigurationProperties` record or
  `@Value`), creates the account idempotently. Bypasses the signup passcode (server-side).
- Frontend: rework `AuthGate.tsx` into login/sign-up forms; extend `api/auth.ts` with
  `login`/`signup`; the `http.ts` `authedFetch` + `?token=` transport is unchanged. Update the
  relevant Vitest/RTL tests.
- Env: `ENSEMBLE_PASSCODE` keeps its env-var name but is now the **signup** passcode; add
  `ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` to `.env.example` and (for deploy) to Secrets
  Manager wiring in `terraform/deploy/apprunner.tf` as additional `runtime_environment_secrets`
  (empty containers; values populated out-of-band, matching the existing secret pattern).

## Security Considerations

- **Passwords hashed at rest** with bcrypt (work factor 12, exceeds the OWASP minimum). The raw
  password and the hash are **never logged**; DTOs never echo them; any `toString()` that could
  touch them masks them.
- **No user enumeration**: login returns one generic `401` for both unknown email and wrong
  password, and performs a bcrypt comparison even when the email is absent to equalize timing.
- **Signup is invite-only**: `POST /api/accounts` requires the shared signup passcode
  (constant-time compare), so an anonymous stranger cannot register and trigger paid Claude
  endpoints — preserving the cost/abuse protection originally added in #8. bcrypt's cost also
  bounds signup-flooding. (This narrows, rather than removes, the passcode: it no longer gates
  login.)
- **Session token** is stateless, signed (HMAC-SHA256), expiring (12h), stored in
  `sessionStorage`, and now carries an **opaque `userId` (UUID) only** — no email or other PII in
  the token. Tampered/expired/old-format tokens are rejected. There is **no mid-TTL revocation**
  (accepted for a demo; short TTL bounds exposure).
- **Sanitized errors**: `400`/`401`/`409` reuse the shared `ErrorResponse` shape and leak no
  stack traces, field detail, or internals. Duplicate-email `409` is the one place that
  necessarily reveals an email is taken (unavoidable for a usable signup); login itself stays
  non-enumerating.
- **No secrets committed**: the signup passcode, the session secret, and the seed credentials
  come from the git-ignored `.env` locally and from **Secrets Manager** at deploy (injected by
  ARN as `ENSEMBLE_*` env vars). Tests never need real values. The pre-commit secret scan must
  catch an accidental commit of any of them.
- **Session-secret / invite-code coupling**: `ENSEMBLE_SESSION_SECRET` still falls back to
  deriving the token-signing HMAC key from `ENSEMBLE_PASSCODE` when blank (the documented
  single-secret local-dev flow). But now that `ENSEMBLE_PASSCODE` is a *shared* invite code,
  leaving the session secret blank means every invited user knows the signing key and could
  forge session tokens for arbitrary userIds — a latent horizontal-privilege-escalation once
  data is scoped by `userId` (#15). Mitigation: the fallback is retained (does not fail closed),
  but a loud startup warning fires while the session secret is unconfigured, and operators are
  told to set a distinct `ENSEMBLE_SESSION_SECRET`, especially before #15.
- **Proof artifacts** must not commit a real password, seed credential, live key, or the passcode
  value; `curl`/screenshot proofs are captured with throwaway demo values.

## Success Metrics

1. **Sign-up works**: a new user can create an account via `POST /api/accounts` (behind the
   signup passcode) and is auto-logged-in (`201` + token) — demonstrated by controller tests +
   `curl`.
2. **Login resolves identity**: `POST /api/auth` with a valid email + password returns a token
   whose `userId` is recovered by the filter and returned by `GET /api/me` — demonstrated by
   token/filter tests + `curl`.
3. **Errors are clear and safe**: wrong password / unknown email → generic `401`; duplicate email
   → `409`; malformed/weak input → `400`; no stack traces — demonstrated by tests.
4. **Passwords protected**: passwords are bcrypt-hashed at rest and never appear in logs or
   responses — demonstrated by `PasswordHasher`/repository tests and a log/response inspection.
5. **Transition covered**: with the seed env vars set, a default account exists at startup
   (idempotently) — demonstrated by `SeedAccountRunnerTest`.
6. **Coverage**: ≥90% line and **100% branch** on token verify/parse, password verification,
   the duplicate-email conditional, the signup-passcode check, and password validation (JaCoCo).
7. **No regressions**: all pre-existing backend and frontend tests still pass with the new auth in
   place (the daily-cap and stylist/wardrobe flows are unchanged).

## Resolved Decisions (locked from questions round 1)

1. **Passcode fate (Q1 → B):** the shared passcode is repurposed as a **signup gate** — required
   to *create* an account, not to log in. The old single-passcode login is retired.
2. **User key design (Q2 → A):** **email is the partition key**; `userId` is a generated UUID
   attribute. Duplicate emails are rejected by an atomic conditional put. No GSI.
3. **Endpoint shape (Q3 → A):** repurpose **`POST /api/auth`** for login (`{email, password}`);
   add **`POST /api/accounts`** for signup. Both stay in the filter's open-route list.
4. **Hashing + strength (Q4 → A/A):** **bcrypt (work factor 12) via `spring-security-crypto`**
   (pure-Java, no native lib, no full security starter). Strength policy is **NIST-minimal**:
   length ≥ 8, ≤ 72 bytes, no composition rules, validated server-side with a clear `400`.
5. **Frontend scope (Q5 → A):** include a **minimal login + sign-up UI** (Care Label styling),
   reusing the unchanged token transport.
6. **Transition / #14↔#15 boundary (Q6 → A):** **#14 seeds a default account from env**
   (`ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD`) idempotently at startup; **#15** owns
   reassigning existing wardrobe data to that `userId`.
7. **Token identity + lifetime (assumption, accepted):** token stays **stateless** with the
   `userId` embedded in the signed payload; **12h TTL, no refresh**; no email verification.

## Open Questions

1. **Signup auto-login vs. explicit login** — this spec has `POST /api/accounts` return a token
   (auto-login) for a smoother demo. If you'd rather sign-up return `201` with no token and force
   an explicit login step, that's a small change with no impact on the data model or acceptance
   criteria. Non-blocking; defaulting to auto-login.
2. **Session TTL value (12h)** — inherited default; tunable via `ensemble.security.session-ttl`
   without design change. Non-blocking.
3. **Seed credentials at deploy** — assumed populated in Secrets Manager like the other secrets
   if the operator wants the seed account in the cloud; if omitted, the seed runner is simply a
   no-op there. Non-blocking.
