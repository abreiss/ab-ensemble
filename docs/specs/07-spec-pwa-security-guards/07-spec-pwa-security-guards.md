# 07-spec-pwa-security-guards.md

## Introduction/Overview

This feature makes Ensemble **installable to an iPhone home screen** and adds two lightweight **security/cost guards** appropriate for a single-user demo that spends real Claude money: a **user-entered passcode gate** (checked server-side, never shipped in the client bundle) and a **daily call cap** (~100/day) that returns `429` on the two Claude-backed endpoints once exceeded.

The primary goal is to turn the working web app into something that (a) launches full-screen from the home screen like a native app, and (b) can be shared/demoed without exposing the user's private wardrobe or letting anyone run up an unbounded Claude bill. None of this changes the AI behavior — it wraps the existing app in an install surface and two guards.

## Goals

- Ship a valid **PWA** (manifest, service worker, icons, iOS meta) so the app installs to the iPhone home screen and opens **standalone** (no browser chrome).
- Add a **server-enforced passcode gate**: a wrong/absent passcode is blocked; a correct one grants access. The passcode lives only in a server env var and is **never present in the client bundle**.
- Add a **global daily call cap** on the Claude endpoints (`POST /api/style`, `POST /api/items/tag`): once the day's count exceeds a configurable limit (~100), further calls get **`429`**; the counter resets at UTC midnight.
- Keep the server **stateless** (signed session token, atomic DynamoDB counter) and honor the existing **single-table** DynamoDB decision, without polluting the wardrobe listing.
- Do all of the above without regressing the existing wardrobe, tagging, or stylist flows or their tests.

## User Stories

- **As the app owner demoing on my phone**, I want to install Ensemble to my home screen and open it full-screen, so that it feels like a real app instead of a browser tab.
- **As the app owner sharing a link**, I want a passcode in front of the app, so that a stranger with the URL can't browse my wardrobe or trigger paid AI calls.
- **As the app owner**, I want the passcode checked on the server and never baked into the downloaded JavaScript, so that reading the bundle can't reveal it.
- **As the app owner watching cost**, I want a hard daily cap on the AI endpoints that returns a clear `429` once hit, so that a runaway loop or abuse can't run up an unbounded Claude bill.
- **As a legitimate user within limits**, I want the gate and cap to be invisible in normal use (log in once, style/tag freely under the limit), so that the guards don't get in my way.

## Demoable Units of Work

### Unit 1: PWA install — manifest, service worker, icons, iOS meta

**Purpose:** Make the built app installable and standalone on iPhone. Frontend/infra slice (vite-plugin-pwa); light testing per the coverage split.

**Functional Requirements:**
- The system shall integrate **`vite-plugin-pwa`** into the Vite build (`registerType: 'autoUpdate'`) so a production build emits a **`manifest.webmanifest`** and a **service worker** into the existing static output (`../src/main/resources/static`, so Spring serves them at `/`).
- The web app manifest shall declare at minimum: `name` ("Ensemble"), `short_name` ("Ensemble"), `start_url` (`/`), `display: "standalone"`, `background_color` (`#f3ecdd`), `theme_color` (matching the existing `<meta name="theme-color">`), and an `icons` array with **192×192** and **512×512** PNGs plus a **maskable** icon.
- The system shall provide a **180×180 `apple-touch-icon.png`** and the iOS standalone meta tags (`apple-mobile-web-app-capable`, `apple-mobile-web-app-status-bar-style`, `apple-mobile-web-app-title`) in `index.html`, and include the apple-touch icon via `includeAssets` so iOS uses it on "Add to Home Screen".
- The system shall register the service worker on app startup (via `virtual:pwa-register/react`), and the service worker shall **not cache `/api/**` responses** (app-shell precache only; `navigateFallbackDenylist` excludes `/api`) so authed/priced API calls are never served from cache.
- The install/standalone behavior shall be verified on a **real iPhone** (opens full-screen from the home screen with the app icon).

**Proof Artifacts:**
- Build output: `npm run build` listing showing `manifest.webmanifest`, the generated `sw.js` (and precache manifest), and the icon assets in `src/main/resources/static/` — demonstrates the PWA artifacts are produced and served by Spring.
- File check: `index.html` (or generated head) contains the `apple-touch-icon` link + `apple-mobile-web-app-*` meta — demonstrates iOS install support.
- Screenshot: the Ensemble icon on an **iPhone home screen** and the app open **standalone** (no Safari chrome) — demonstrates the primary acceptance criterion end-to-end.

### Unit 2: Passcode gate — server-side token auth + entry screen

**Purpose:** Gate the app behind a server-checked passcode. **Backend domain core** (token minting/verification + the auth filter) under strict TDD; a mobile-first entry screen and token wiring on the frontend.

**Functional Requirements:**
- The system shall expose **`POST /api/auth`** accepting `{ passcode: string }`. On a match against the server-side passcode it shall return **`200`** with `{ token }`; on a mismatch (or blank) it shall return **`401`** with the shared sanitized error body and **no token**. Passcode comparison shall be constant-time.
- The session **token** shall be a **stateless, signed value** (HMAC-SHA256 over an expiry timestamp, keyed by a server secret) with a bounded TTL (default 12h). Verification shall accept only a well-formed, untampered, unexpired token; a malformed, tampered, or expired token shall be treated as unauthenticated.
- The system shall enforce authentication on **all `/api/**` requests except** `POST /api/auth` and `GET /api/health`. A protected request without a valid token shall return **`401`** and shall **not** reach the controller (no Claude call, no data access).
- The client shall present the token as an **`X-Ensemble-Session` header**; for media GETs that cannot set headers (e.g. `<img src>` on `/api/items/{id}/photo`), the server shall **also accept the token as a `token` query parameter**. (See Resolved Decisions #2.)
- Static assets (SPA shell, `manifest.webmanifest`, service worker, icons) shall **not** be gated, so the app can load and render the passcode screen and remain installable.
- The **passcode shall never appear in the client bundle** — it exists only as a server env var and is checked server-side. This shall be assertable (grep of the built `static/` assets finds no passcode value).
- **Frontend:** the app shall render a **passcode entry screen** when no valid session token is stored; on successful `POST /api/auth` it shall store the token in **`sessionStorage`** and render the app. Any API `401` shall clear the stored token and return the user to the passcode screen. The screen shall follow the existing "Care Label" design system and be mobile-first.

**Proof Artifacts:**
- Test: `SessionTokenServiceTest` passes with `issuesTokenThatVerifies`, `rejectsTamperedToken`, `rejectsExpiredToken`, `rejectsMalformedToken` — demonstrates the signed-token logic with 100% branch coverage.
- Test: `AuthControllerTest` passes with `correctPasscode_returns200WithToken`, `wrongPasscode_returns401NoToken`, `blankPasscode_returns401` — demonstrates the auth contract.
- Test: filter/MockMvc tests pass — `protectedApi_withoutToken_returns401`, `protectedApi_withValidToken_passesThrough`, `protectedApi_withValidQueryToken_passesThrough`, `authAndHealth_areOpen` — demonstrates the gate scope and header/query acceptance.
- Test: frontend `AuthGate`/`api/auth` tests pass under Vitest/RTL (no token → passcode screen; submit → token stored → app renders; API 401 → back to gate) — demonstrates the client auth state logic.
- CLI: `curl -s -X POST /api/auth -d '{"passcode":"wrong"}'` → `401`; with the correct passcode → `200` + token; `curl /api/items` without the header → `401`, with `X-Ensemble-Session` → `200` — demonstrates end-to-end server enforcement.
- Grep: search of `src/main/resources/static/**` (built bundle) shows the passcode value is **absent** — demonstrates "not in client bundle".

### Unit 3: Daily call cap — atomic counter + 429

**Purpose:** Cap paid Claude calls per UTC day. **Backend domain core** (atomic counter + limit check + scan filter) under strict TDD; returns `429` on the two Claude endpoints once exceeded.

**Functional Requirements:**
- The system shall maintain a **single global daily counter** persisted in DynamoDB, keyed by UTC calendar day (reserved partition key `usage#<UTC-date>`, e.g. `usage#2026-07-16`), using an **atomic `ADD` `UpdateItem`** so concurrent increments do not race.
- Before invoking Claude, both **`POST /api/style`** and **`POST /api/items/tag`** shall **increment the shared counter and check it against a configurable limit** (`ensemble.usage.daily-limit`, default 100). If incrementing would exceed the limit, the system shall return **`429`** with the shared sanitized error body and shall **not** call Claude. The increment happens **before** the Claude call, so a failed/timed-out upstream call still consumes budget.
- The counter shall reset implicitly at the **UTC calendar-day boundary** (a new day uses a new key). "Today" shall be derived from an injectable `Clock` (UTC) so the boundary is deterministically testable.
- The wardrobe listing shall **exclude reserved `usage#` rows**: `WardrobeRepository.findAll()` (feeding `WardrobeService.list()` and the stylist's `searchWardrobe`) shall never return a usage-counter row as an `Item`.
- Requests that do not reach the Claude call (auth `401`, cap `429`, bad input `400`) shall not corrupt the counter's meaning; only the two Claude endpoints touch it, and only on an authenticated, accepted request.

**Proof Artifacts:**
- Test: `CallCapServiceTest` passes with `underLimit_allowsAndIncrements`, `atLimit_blocksWith429Signal`, `usesUtcDateKey`, `newUtcDay_resetsCount` (fixed `Clock`) — demonstrates the limit logic with 100% branch coverage.
- Test: `UsageRepositoryIT` (DynamoDB Local / TestContainers) passes `incrementIsAtomicAndPersists` — demonstrates the real atomic `ADD` round-trip.
- Test: `WardrobeRepository` scan-filter test passes `findAll_excludesUsageRows` (a `usage#` row + a real item → only the item returned) — demonstrates the scan is not polluted (100% branch on the filter).
- Test: controller MockMvc `postStyle_overDailyCap_returns429` and `postTag_overDailyCap_returns429` (limit set to a tiny value) — demonstrates the `429` contract on both endpoints.
- CLI: with `ensemble.usage.daily-limit=2`, three authenticated `POST /api/style` calls → the third returns `429` — demonstrates the cap end-to-end.

## Non-Goals (Out of Scope)

1. **Real authentication / multi-user / accounts / RBAC** — this is a single shared passcode for a single-user demo, not a login system.
2. **Per-IP or per-user rate limiting** — the cap is a single global daily budget, not a fairness/rate limiter (explicitly out of scope per the issue).
3. **Deploy pipeline / S3 / Secrets Manager / Terraform** — issue #9. Locally the passcode and secret come from the git-ignored `.env`; wiring them into Secrets Manager is #9's job.
4. **Offline-first / rich caching** — the service worker precaches the app shell to satisfy install; it is not a goal to make the app usable offline, and `/api` is never cached.
5. **Push notifications / background sync / other PWA capabilities** — installability only.
6. **Changes to tagging (Haiku) or stylist (Sonnet) behavior** — those paths are unchanged; this feature only wraps them with a gate and a cap.
7. **Wear-history writes** — issue #7, untouched here.
8. **Encryption of stored photos or a secrets vault** — beyond the demo's scope.

## Design Considerations

- The **passcode entry screen** must use the existing **"Care Label" design system** (`frontend/src/index.css` `:root` tokens: `--paper #f3ecdd`, `--paper-raised #fcf8ef`, `--ink #33271f`, `--accent #7c2833`; display font Bricolage Grotesque, mono Space Mono; mobile-first `#root` max-width 30rem). Do not introduce a second visual language.
- The gate should be a **single, calm, centered screen**: app title, a short prompt, one passcode input (`type="password"`, `inputMode`/appropriate mobile keyboard), a submit button (≥44px touch target), and a clear inline error on a wrong passcode. Respect `:focus-visible` and `prefers-reduced-motion` already defined in the system.
- **PWA icons** should be a simple, on-brand mark — a maroon (`#7c2833`) "E" / Ensemble glyph on the beige (`#f3ecdd`) paper — generated as 192/512/maskable + a 180×180 apple-touch icon. No external icon service; assets live in the frontend and are emitted to `static/`.
- The manifest `theme_color` should match the existing `<meta name="theme-color">` (`#f3ecdd`) for a cohesive status bar on launch.

## Repository Standards

- **Strict TDD** for the backend domain: the **session token service**, the **auth filter** logic, the **daily-cap service**, the **usage counter**, and the **scan filter** — RED → GREEN → REFACTOR; ≥90% line and **100% branch** on token verification, the limit check, and the scan filter. Frontend (entry screen, auth state, token wiring) tests meaningful logic only, per `docs/TESTING.md`; Unit 1 (vite-plugin-pwa glue) is infra — validate the build output, don't unit-test plumbing.
- **Layered architecture**: controllers/filter → services (`SessionTokenService`, `CallCapService`) → repository (`UsageRepository`) / config properties; DTOs at the boundary; no DynamoDB or crypto internals leak into controllers.
- **Config properties** follow the existing `@ConfigurationProperties(prefix = "ensemble.*")` record pattern (see `AnthropicProperties`, `DynamoDbProperties`), with secrets masked in `toString()`.
- **Mock external boundaries** in unit tests (no live Claude, no live network); the usage counter's real round-trip uses **DynamoDB Local via TestContainers**, matching `WardrobeRepositoryIT`.
- **Errors** reuse the shared `ApiExceptionHandler` `ErrorResponse` shape; new statuses (`401`, `429`) are added there or via the filter with the same sanitized body.
- Conventional commits, roughly one per demoable unit. Pre-commit hooks (fast tests + lint + **secret scan** — must not commit the passcode) must pass.

## Technical Considerations

**PWA (Unit 1)**
- Add `vite-plugin-pwa` (and `workbox-window`) as frontend dev deps; register `VitePWA({ registerType: 'autoUpdate', manifest: {...}, includeAssets: ['apple-touch-icon.png', 'favicon.ico'], workbox: { navigateFallbackDenylist: [/^\/api/] } })` in `vite.config.ts`. Add `vite-plugin-pwa/react` to the TS types (`vite-env.d.ts` reference or `tsconfig`).
- Register via `virtual:pwa-register/react` (`useRegisterSW`) in `main.tsx` (or a tiny component). Assets emit into the existing `outDir` (`../src/main/resources/static`) so Spring serves them; confirm `SpaForwardingConfig` serves `manifest.webmanifest`/`sw.js` (they're real files under `/static`, so the resolver returns them before the SPA fallback).

**Passcode gate (Unit 2)**
- New `SecurityProperties` bound from `ensemble.security.*`: `passcode` = `${ENSEMBLE_PASSCODE:}` (blank → gate effectively closed / all `401`, and this state is logged at startup), optional `session-secret` = `${ENSEMBLE_SESSION_SECRET:}` (when blank, **derive the HMAC key from the passcode** so only one env var is required), `session-ttl` (default `PT12H`). Mask secrets in `toString()`.
- `SessionTokenService`: `issue()` → `base64url(payload).base64url(HMAC_SHA256(payload, key))` where `payload` encodes an expiry epoch; `verify(token)` → boolean (constant-time HMAC compare, expiry check). Pure/unit-testable, no Spring/web deps.
- `AuthController` (`POST /api/auth`) → validates passcode (constant-time), returns `{token}` or `401`.
- **Gate enforcement** as a servlet `Filter` (or `HandlerInterceptor`) matched to `/api/**`, skipping `POST /api/auth` and `GET /api/health`; reads the token from `X-Ensemble-Session` **or** the `token` query param; on failure writes the sanitized `401` body and stops. **Important:** ensure this filter does **not** break the existing `@WebMvcTest` slices and `@SpringBootTest` context tests (the 5 prior specs) — scope/register it so slice tests either bypass it or supply a token; document the chosen mechanism. Order it **before** the daily-cap check.
- Add a `401` handler (in the filter or `ApiExceptionHandler`) using the existing `ErrorResponse` shape.

**Daily cap (Unit 3)**
- New `UsageProperties` bound from `ensemble.usage.*`: `daily-limit` (default `100`).
- `UsageRepository` uses the **low-level `DynamoDbClient`** (already a bean) for an atomic counter: `UpdateItem` on key `{ itemId: "usage#<date>" }` with `UpdateExpression "ADD #c :one"` and `ReturnValues=UPDATED_NEW`, returning the new count. (The enhanced client's bean mapper is item-shaped and can't express `ADD` cleanly.)
- `CallCapService.reserve()` computes the UTC date from an injected **`Clock`** bean (`Clock.systemUTC()` by default; fixed in tests), increments via `UsageRepository`, and throws `DailyCapExceededException` when the new count exceeds the limit. Invoked at the **start** of the style and tag flows (thin call in the controller/service, or a `HandlerInterceptor` on exactly those two paths).
- `DailyCapExceededException` → `429` in `ApiExceptionHandler`.
- **Scan filter:** update `WardrobeRepository.findAll()` to exclude `itemId` values starting with `usage#` (a DynamoDB `ScanRequest` `FilterExpression`, or an in-stream `filter` at demo scale). This is the critical guard that keeps counter rows out of the wardrobe/stylist.
- New properties need safe defaults so the test `application.yml` and context tests load without extra env; the counter IT drives its own TestContainers client like `WardrobeRepositoryIT`.

**Frontend wiring (Unit 2)**
- New `api/auth.ts` (`login(passcode)`, `getToken()`, `clearToken()`), and a shared authenticated fetch (inject `X-Ensemble-Session`, on `401` clear token + surface a re-auth signal). Update `api/items.ts` / `api/style.ts` to route through it; update `photoUrl(id)` to append `?token=<token>` for `<img>` rendering. An `AuthGate` component wraps the routed app in `App.tsx`.

## Security Considerations

- **No secrets committed.** `ENSEMBLE_PASSCODE` (and optional `ENSEMBLE_SESSION_SECRET`) come from the git-ignored `.env` locally (add to `.env.example` as a placeholder) and from Secrets Manager at deploy (#9). Tests never need the real passcode. The secret scan in pre-commit must catch an accidental passcode commit.
- **Passcode never in the client bundle** — entered at runtime, checked server-side; asserted by grepping the built `static/` assets. This is the core of acceptance criterion #3.
- **Session token** is signed (HMAC-SHA256), **expiring** (default 12h), and stored in **`sessionStorage`** (cleared when the tab closes) rather than `localStorage`. Tampered/expired tokens are rejected. Constant-time comparison for both the passcode and the HMAC to avoid timing side-channels.
- **Token-in-URL tradeoff:** to let `<img>` load gated photos, the token is accepted as a `?token=` query param on media GETs. This can surface in server access logs; mitigated by the short TTL, single-user demo scope, and same-origin usage. (A blob-fetch-to-object-URL approach avoids token-in-URL and is noted as a stricter alternative — see Resolved Decisions #2.)
- **Gate scope** protects the user's private wardrobe (photos + item data) and the paid AI endpoints; only `POST /api/auth` and `GET /api/health` are open, plus non-`/api` static assets.
- **Daily cap** is the app-side spend backstop (no key-level spend cap is available). Counting **before** the Claude call ensures failed/looping calls still consume budget, so the guard bounds cost, not just successes. Error bodies for `401`/`429` leak no internals.
- **Proof artifacts** must not commit the real passcode, a live key, or real wardrobe photos; the live `curl`/screenshot proofs are captured without embedding secrets.

## Success Metrics

1. **Installable:** the app installs to an iPhone home screen and opens standalone (screenshot proof); `npm run build` emits a valid manifest + service worker + icons.
2. **Gated:** a wrong/absent passcode is blocked (`401`) on every protected `/api` route; a correct passcode grants access. 100% of protected endpoints reject a missing/invalid token in tests.
3. **Secret safety:** the passcode value does not appear anywhere in the built client bundle (grep proof).
4. **Capped:** exceeding the configured daily limit returns `429` on both `/api/style` and `/api/items/tag`; the counter is atomic and resets on the UTC-day boundary (tests with a fixed `Clock`).
5. **Coverage:** ≥90% line and **100% branch** on token verification, the daily-limit check, and the wardrobe scan filter (JaCoCo).
6. **No regressions:** all pre-existing backend and frontend tests (specs 01–06) still pass with the gate and cap in place.

## Resolved Decisions (locked from questions round 1 + drafting)

1. **Session mechanism (Q1 → A):** server mints a **stateless signed token** on correct passcode; the client sends it as the `X-Ensemble-Session` header. Raw passcode stays server-only.
2. **Photo/media auth (drafting):** because header tokens can't ride on `<img src>`, the auth filter **also accepts the token as a `?token=` query param** for media GETs, and `photoUrl(id)` appends it. Accepted tradeoff: token may appear in access logs (short TTL + demo scope mitigate). Stricter alternative (blob-fetch → object URL, header-only) is available if token-in-URL is later deemed unacceptable.
3. **Gate scope (Q2 → A):** all `/api/**` gated except `POST /api/auth` and `GET /api/health`; static/PWA assets always open.
4. **Cap semantics (Q3 → A):** one **global** counter across both Claude endpoints, **incremented before** the Claude call (failures still consume budget), **UTC calendar-day** reset, configurable limit (default 100) → `429`.
5. **Counter storage (Q4 → A):** **reuse `ensemble-items`** with reserved `usage#<UTC-date>` key + atomic `ADD`; `findAll()` filters out `usage#` rows so the wardrobe/stylist never see them.
6. **Non-blocking defaults (accepted):** `registerType: 'autoUpdate'`; manifest name "Ensemble", `background_color`/`theme_color` from the beige/maroon tokens, `display: standalone`; maroon-on-beige generated icons (192/512/maskable + 180 apple-touch); daily limit default 100; passcode env var `ENSEMBLE_PASSCODE` (session secret derived from it unless `ENSEMBLE_SESSION_SECRET` is set).

## Open Questions

1. **Exact daily limit value** — defaulting to **100** (configurable via `ensemble.usage.daily-limit`); adjust in config if the demo warrants a different number. Non-blocking.
2. **Session TTL** — defaulting to **12h**; can be tuned via `ensemble.security.session-ttl` without design change. Non-blocking.
3. **Icon artwork polish** — a simple maroon-on-beige "E" mark is assumed; final glyph/aesthetic can be refined during Unit 1 without affecting scope. Non-blocking.
