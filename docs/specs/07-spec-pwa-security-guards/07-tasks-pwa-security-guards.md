# 07-tasks-pwa-security-guards.md

Task list for **Spec 07 — PWA install + security guards** (`07-spec-pwa-security-guards.md`).

> Status: **Sub-tasks generated (Phase 3 output). Planning audit: see `07-audit-pwa-security-guards.md`.**

Parent tasks are ordered by dependency: PWA install (1.0) is independent; the passcode
backend (2.0) precedes the passcode frontend (3.0, which consumes the `/api/auth` contract
and the gate); the daily cap (4.0) is an independent backend slice; final integration,
secret-safety, and regression verification (5.0) depends on all prior tasks.

Backend domain sub-tasks follow **strict TDD (RED → GREEN → REFACTOR)** — write the failing
test first. Frontend sub-tasks test meaningful logic only; Unit 1 is infra (validate the build
output, do not unit-test the plugin plumbing) per `docs/TESTING.md`.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/package.json` | Add `vite-plugin-pwa` + `workbox-window` dev deps. |
| `frontend/vite.config.ts` | Register `VitePWA(...)` (manifest, `registerType: 'autoUpdate'`, `navigateFallbackDenylist: [/^\/api/]`, `includeAssets`). |
| `frontend/index.html` | Add `apple-touch-icon` link + `apple-mobile-web-app-*` iOS meta tags. |
| `frontend/src/main.tsx` | Register the service worker via `virtual:pwa-register/react`. |
| `frontend/src/vite-env.d.ts` | Add the `vite-plugin-pwa/react` types reference. |
| `frontend/public/pwa-192x192.png`, `pwa-512x512.png`, `maskable-icon-512x512.png`, `apple-touch-icon.png`, `favicon.ico` | Generated maroon-on-beige icon assets emitted to `static/`. |
| `src/main/java/com/ensemble/security/SecurityProperties.java` | `@ConfigurationProperties(ensemble.security)` — passcode, session-secret, session-ttl; masked `toString()`. |
| `src/test/java/com/ensemble/security/SecurityPropertiesTest.java` | Unit tests for defaults + secret masking. |
| `src/main/java/com/ensemble/security/SessionTokenService.java` | HMAC-signed token `issue()` / `verify()` (pure, `Clock`-driven). |
| `src/test/java/com/ensemble/security/SessionTokenServiceTest.java` | 100%-branch tests for token verify (issue/tamper/expire/malformed). |
| `src/main/java/com/ensemble/security/web/AuthController.java` | `POST /api/auth` — constant-time passcode check → `{token}` or `401`. |
| `src/main/java/com/ensemble/security/dto/AuthRequest.java`, `AuthResponse.java` | Boundary DTOs (`{passcode}` in, `{token}` out). |
| `src/test/java/com/ensemble/security/web/AuthControllerTest.java` | `@WebMvcTest` contract tests for the auth endpoint. |
| `src/main/java/com/ensemble/security/web/SessionAuthFilter.java` | `OncePerRequestFilter` gating `/api/**` (header/query token, 401 short-circuit). |
| `src/test/java/com/ensemble/security/web/SessionAuthFilterTest.java` | MockMvc tests for gate scope + header/query acceptance. |
| `src/main/java/com/ensemble/security/SecurityConfig.java` | `FilterRegistrationBean` registering the gate filter (ordered before the cap); slice-test coexistence. |
| `src/main/java/com/ensemble/usage/UsageProperties.java` | `@ConfigurationProperties(ensemble.usage)` — `daily-limit` default 100. |
| `src/test/java/com/ensemble/usage/UsagePropertiesTest.java` | Unit test for the default limit. |
| `src/main/java/com/ensemble/usage/UsageRepository.java` | Atomic `ADD` `UpdateItem` counter (low-level `DynamoDbClient`), key `usage#<UTC-date>`. |
| `src/test/java/com/ensemble/usage/UsageRepositoryIT.java` | TestContainers IT — atomic increment round-trip. |
| `src/main/java/com/ensemble/usage/CallCapService.java` | `reserve()` — UTC date via `Clock`, increment, throw over limit. |
| `src/test/java/com/ensemble/usage/CallCapServiceTest.java` | 100%-branch tests for the limit check + UTC boundary (fixed `Clock`). |
| `src/main/java/com/ensemble/usage/DailyCapExceededException.java` | Signals the `429` mapping. |
| `src/main/java/com/ensemble/config/ClockConfig.java` | `Clock.systemUTC()` bean (fixed in tests). |
| `src/main/java/com/ensemble/wardrobe/WardrobeRepository.java` | `findAll()` filters out `usage#` rows (the critical scan guard). |
| `src/test/java/com/ensemble/wardrobe/WardrobeRepositoryIT.java` | Add `findAll_excludesUsageRows`. |
| `src/main/java/com/ensemble/stylist/web/StyleController.java`, `tagging/web/TaggingController.java` | Call `callCapService.reserve()` before the Claude call. |
| `src/test/java/com/ensemble/stylist/web/StyleControllerTest.java`, `tagging/web/TaggingControllerTest.java` | Add `over-cap → 429` tests. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Map `DailyCapExceededException` → `429` (shared `ErrorResponse`). |
| `src/main/resources/application.yml`, `src/test/resources/application.yml` | `ensemble.security.*` + `ensemble.usage.*` config + safe test values. |
| `frontend/src/api/auth.ts` | `login(passcode)`, `getToken()`, `clearToken()` (`sessionStorage`). |
| `frontend/src/api/http.ts` | Shared authenticated `fetch` — injects `X-Ensemble-Session`, clears token + re-auth signal on `401`. |
| `frontend/src/api/items.ts`, `style.ts` | Route through the authed fetch; `photoUrl(id)` appends `?token=`. |
| `frontend/src/api/auth.test.ts`, `http.test.ts`, `items.test.ts`, `style.test.ts` | Vitest for the token client, fetch wrapper, and token-in-URL. |
| `frontend/src/components/AuthGate.tsx`, `AuthGate.test.tsx` | Passcode entry screen + auth-state gate wrapping the app. |
| `frontend/src/App.tsx` | Wrap the routed app in `AuthGate`. |
| `frontend/src/index.css` | Passcode-screen styles using the Care Label tokens. |
| `.env.example` | Add `ENSEMBLE_PASSCODE` (+ optional `ENSEMBLE_SESSION_SECRET`) placeholders. |
| `README.md` / `docs/` | Document PWA install + passcode/cap env vars. |

### Notes

- Backend tests live under `src/test/java/com/ensemble/**` mirroring the main package; run with `./gradlew test` (add `--tests '*Name'` to target one). Integration tests end in `IT` and drive their own TestContainers DynamoDB client like `WardrobeRepositoryIT`.
- Frontend tests are co-located (`*.test.ts`/`*.test.tsx`); run with `cd frontend && npm test -- --run`.
- Reuse existing patterns: `@ConfigurationProperties` record + masked `toString()` (`AnthropicProperties`), the shared `ApiExceptionHandler.ErrorResponse` body, the low-level `DynamoDbClient` bean, and the `ensureOk` fetch helper.
- Never call live Claude or use a real passcode in tests. The secret scan (pre-commit) must stay green.

## Tasks

### [x] 1.0 PWA install — manifest, service worker, icons & iOS meta

Make the built app installable and standalone on iPhone via `vite-plugin-pwa`, emitting a
valid manifest + service worker + icon set into `src/main/resources/static/` so Spring
serves them at `/`. Frontend/infra slice — validate the build output, do not unit-test the
plugin plumbing (per `docs/TESTING.md`). (Spec Unit 1.)

#### 1.0 Proof Artifact(s)

- Build output: `cd frontend && npm run build` output plus `ls src/main/resources/static/` showing `manifest.webmanifest`, `sw.js`, the workbox precache manifest, and the `192`/`512`/`maskable`/`apple-touch` icon PNGs — demonstrates the PWA artifacts are produced and served by Spring (FR: vite-plugin-pwa, manifest, service worker).
- CLI: `cat src/main/resources/static/manifest.webmanifest | python3 -m json.tool` shows `name`/`short_name`="Ensemble", `start_url`="/", `display`="standalone", `background_color`="#f3ecdd", a `theme_color`, and an `icons` array with 192, 512, and a `maskable` entry — demonstrates the manifest satisfies the spec's minimum fields.
- File check: generated `src/main/resources/static/index.html` contains the `apple-touch-icon` link and the `apple-mobile-web-app-capable` / `-status-bar-style` / `-title` meta tags — demonstrates iOS "Add to Home Screen" support.
- Config check: `grep -i api src/main/resources/static/sw.js` (or the generated precache list) shows `/api` is excluded (`navigateFallbackDenylist`) — demonstrates authed/priced `/api/**` responses are never served from cache.
- Screenshot: the Ensemble icon on an **iPhone home screen** and the app open **standalone** (no Safari chrome) — demonstrates the primary acceptance criterion end-to-end (no secrets in frame).

#### 1.0 Tasks

- [x] 1.1 Add `vite-plugin-pwa` and `workbox-window` as dev deps (`cd frontend && npm i -D vite-plugin-pwa workbox-window`); commit the updated `package.json` + lockfile.
- [x] 1.2 Generate the icon assets in `frontend/public/`: `pwa-192x192.png`, `pwa-512x512.png`, a `maskable-icon-512x512.png` (maroon `#7c2833` "E" on beige `#f3ecdd`, with maskable safe-zone padding), a `180×180 apple-touch-icon.png`, and `favicon.ico`.
- [x] 1.3 Register `VitePWA({ registerType: 'autoUpdate', includeAssets: ['apple-touch-icon.png','favicon.ico'], manifest: {...}, workbox: { navigateFallbackDenylist: [/^\/api/] } })` in `vite.config.ts`; declare the manifest fields (name/short_name/start_url/`display: standalone`/`background_color: #f3ecdd`/theme_color + the 192/512/maskable icons).
- [x] 1.4 Add the iOS meta + apple-touch link to `index.html` (`apple-mobile-web-app-capable`, `-status-bar-style`, `-title`="Ensemble", `<link rel="apple-touch-icon" href="/apple-touch-icon.png">`).
- [x] 1.5 Register the service worker on startup via `virtual:pwa-register/react` (`useRegisterSW`) in `main.tsx`, and add the `vite-plugin-pwa/react` types reference to `vite-env.d.ts` so `tsc -b` passes.
- [x] 1.6 Run `npm run build`; confirm `manifest.webmanifest`, `sw.js`, the precache manifest, and the icons land in `src/main/resources/static/`, that `SpaForwardingConfig` serves them (real files resolve before the SPA fallback), and that `/api` is denylisted. Capture the build-output + manifest + iOS-meta + cache proofs.
- [x] 1.7 Install to a real iPhone home screen and confirm standalone launch; capture the screenshot proof (no secrets in frame).

### [x] 2.0 Passcode gate — server-side signed token auth & gate filter

Backend domain core (strict TDD): a stateless HMAC-signed session token, a `SecurityProperties`
config record, `POST /api/auth`, and a servlet filter that gates all `/api/**` except
`POST /api/auth` and `GET /api/health`, accepting the token via the `X-Ensemble-Session`
header **or** a `token` query param, and returning the shared sanitized `401`. The filter must
not break the existing `@WebMvcTest` slices / `@SpringBootTest` context tests. (Spec Unit 2 — backend.)

#### 2.0 Proof Artifact(s)

- Test: `./gradlew test --tests '*SessionTokenServiceTest'` passes with `issuesTokenThatVerifies`, `rejectsTamperedToken`, `rejectsExpiredToken`, `rejectsMalformedToken` — demonstrates the signed-token logic with 100% branch coverage on `verify` (constant-time compare + expiry).
- Test: `./gradlew test --tests '*AuthControllerTest'` passes with `correctPasscode_returns200WithToken`, `wrongPasscode_returns401NoToken`, `blankPasscode_returns401` — demonstrates the `POST /api/auth` contract (constant-time passcode compare, no token on failure).
- Test: gate-filter MockMvc tests pass — `protectedApi_withoutToken_returns401`, `protectedApi_withValidToken_passesThrough`, `protectedApi_withValidQueryToken_passesThrough`, `authAndHealth_areOpen` — demonstrates gate scope and header/query token acceptance, and that a blocked request never reaches the controller.
- Test: `SecurityPropertiesTest` passes — secret masked in `toString()`, blank passcode → gate effectively closed — demonstrates the `@ConfigurationProperties` pattern and secret hygiene.
- CLI: `curl -s -o /dev/null -w '%{http_code}' -X POST localhost:8080/api/auth -H 'Content-Type: application/json' -d '{"passcode":"WRONG"}'` → `401`; correct passcode → `200` + a token; `curl localhost:8080/api/items` with no header → `401`, and with `-H "X-Ensemble-Session: <token>"` → `200` — demonstrates end-to-end enforcement (real passcode read from `.env` at runtime, never committed/echoed).
- CLI: `./gradlew test` — the specs 01–06 controller slices and context tests remain green with the filter registered — demonstrates no slice/context regressions from the gate.

#### 2.0 Tasks

- [x] 2.1 **(RED→GREEN)** Write `SecurityPropertiesTest` (defaults: `session-ttl` = `PT12H`; `toString()` masks `passcode` + `session-secret`; blank passcode is accepted but flagged). Then implement `SecurityProperties` `@ConfigurationProperties(prefix="ensemble.security")` with `passcode`, `sessionSecret`, `sessionTtl` (default `PT12H`), deriving the HMAC key from the passcode when `sessionSecret` is blank, and a masked `toString()`. Enable via `@EnableConfigurationProperties`.
- [x] 2.2 **(RED→GREEN→REFACTOR)** Write `SessionTokenServiceTest` (`issuesTokenThatVerifies`, `rejectsTamperedToken`, `rejectsExpiredToken` with a fixed `Clock`, `rejectsMalformedToken` for empty/no-dot/bad-base64). Then implement `SessionTokenService.issue()` = `base64url(payload).base64url(HMAC_SHA256(payload,key))` (payload encodes an expiry epoch) and `verify(token)` (constant-time HMAC compare + expiry check via injected `Clock`). Pure — no Spring/web deps. Refactor to 100% branch on `verify`.
- [x] 2.3 **(RED→GREEN)** Write `AuthControllerTest` (`@WebMvcTest(AuthController.class)`: `correctPasscode_returns200WithToken`, `wrongPasscode_returns401NoToken`, `blankPasscode_returns401`). Then implement `AuthController` `POST /api/auth` with `AuthRequest{passcode}` / `AuthResponse{token}` DTOs, constant-time passcode compare, mint via `SessionTokenService`, and a `401` with the shared `ErrorResponse` body (no token) on mismatch/blank.
- [x] 2.4 **(RED→GREEN)** Write `SessionAuthFilterTest` (`protectedApi_withoutToken_returns401`, `protectedApi_withValidToken_passesThrough` via header, `protectedApi_withValidQueryToken_passesThrough` via `?token=`, `authAndHealth_areOpen`, static path open). Then implement `SessionAuthFilter extends OncePerRequestFilter` matched to `/api/**`, skipping `POST /api/auth` + `GET /api/health`, reading `X-Ensemble-Session` or the `token` param, writing the sanitized `401` body and short-circuiting on failure.
- [x] 2.5 Register the filter via a `FilterRegistrationBean<SessionAuthFilter>` in `SecurityConfig` (URL pattern `/api/*`), ordered **before** the daily-cap check. Ensure it does not break existing `@WebMvcTest` slices / `@SpringBootTest` (a `FilterRegistrationBean` is not auto-added to `@WebMvcTest` slices; verify existing slices still pass or supply a token test-helper). Document the chosen mechanism in the class Javadoc.
- [x] 2.6 Add `ensemble.security.*` to `src/main/resources/application.yml` (`passcode: ${ENSEMBLE_PASSCODE:}`, `session-secret: ${ENSEMBLE_SESSION_SECRET:}`, `session-ttl: PT12H`) and safe test values to `src/test/resources/application.yml`; log the "gate closed (blank passcode)" state at startup.
- [x] 2.7 Run `./gradlew test`; confirm specs 01–06 slices + context tests stay green. Boot the app and capture the `curl` end-to-end + backend-test proof artifacts.

### [x] 3.0 Passcode gate — frontend entry screen & authenticated fetch

Frontend slice (meaningful-logic tests): an `api/auth.ts` token client, a shared authenticated
fetch that injects `X-Ensemble-Session` and clears the token + returns to the gate on any `401`,
`photoUrl(id)` appending `?token=` for `<img>` media GETs, and an `AuthGate` in `App.tsx` that
renders a mobile-first passcode entry screen (Care Label design system) until a valid token is
stored in `sessionStorage`. (Spec Unit 2 — frontend.)

#### 3.0 Proof Artifact(s)

- Test: `cd frontend && npm test -- --run` passes the `api/auth` tests — `login` stores the token in `sessionStorage`, `getToken`/`clearToken` behave — demonstrates the token client.
- Test: authenticated-fetch tests pass — the wrapper injects `X-Ensemble-Session`, and on a `401` clears the stored token and surfaces a re-auth signal — demonstrates the client auth-failure path.
- Test: `AuthGate` RTL tests pass — no token → passcode screen renders; submit correct passcode → token stored → app renders; an API `401` → back to the gate — demonstrates the client auth-state logic.
- Test: `photoUrl(id)` test passes — appends `?token=<token>` when a token is present so gated `<img src>` loads — demonstrates the media-GET auth wiring.
- Screenshot: the passcode entry screen on a mobile viewport following the Care Label system (single centered card, `type="password"` input, ≥44px submit target, inline error on a wrong passcode) — demonstrates the design requirement (no secrets in frame).

#### 3.0 Tasks

- [x] 3.1 **(RED→GREEN)** Write `api/auth.test.ts` (`login(passcode)` POSTs `/api/auth` and stores the returned token in `sessionStorage`; `getToken()` reads it; `clearToken()` removes it). Then implement `frontend/src/api/auth.ts`.
- [x] 3.2 **(RED→GREEN)** Write `api/http.test.ts` (the wrapper injects `X-Ensemble-Session` from `getToken()`; on a `401` it clears the token and fires a re-auth signal). Then implement `frontend/src/api/http.ts`, and refactor `api/items.ts` + `api/style.ts` to route through it (update their tests first to assert the header is sent).
- [x] 3.3 **(RED→GREEN)** Update `api/items.test.ts` so `photoUrl(id)` appends `?token=<token>` when a token is stored (and omits it when absent). Then update `photoUrl`.
- [x] 3.4 **(RED→GREEN)** Write `AuthGate.test.tsx` (no token → passcode screen; submit correct → token stored → children render; injected API `401` → back to gate; wrong passcode → inline error). Then implement `frontend/src/components/AuthGate.tsx` and wrap the routed app in `App.tsx`.
- [x] 3.5 Style the passcode entry screen in `index.css` using the Care Label tokens (single centered card, `type="password"` input with an appropriate mobile keyboard, ≥44px submit target, inline error, honoring `:focus-visible` + `prefers-reduced-motion`). Do not introduce a second visual language.
- [x] 3.6 Run `cd frontend && npm test -- --run` (all green) and `npm run lint`; capture the RTL-test + passcode-screen screenshot proofs.

### [x] 4.0 Daily call cap — atomic counter, 429 & wardrobe scan filter

Backend domain core (strict TDD): a `UsageProperties` limit, a `UsageRepository` atomic `ADD`
counter keyed `usage#<UTC-date>` on the low-level `DynamoDbClient`, a `CallCapService.reserve()`
driven by an injected `Clock` that increments **before** the Claude call and throws
`DailyCapExceededException` (→ `429`) when the limit is exceeded, wired into both `POST /api/style`
and `POST /api/items/tag`, plus a `WardrobeRepository.findAll()` filter that excludes `usage#`
rows from the wardrobe/stylist. (Spec Unit 3.)

#### 4.0 Proof Artifact(s)

- Test: `./gradlew test --tests '*CallCapServiceTest'` passes with `underLimit_allowsAndIncrements`, `atLimit_blocksWith429Signal`, `usesUtcDateKey`, `newUtcDay_resetsCount` (fixed `Clock`) — demonstrates the limit + UTC-boundary logic with 100% branch coverage on the limit check.
- Test: `./gradlew test --tests '*UsageRepositoryIT'` (DynamoDB Local / TestContainers) passes `incrementIsAtomicAndPersists` — demonstrates the real atomic `ADD` `UpdateItem` round-trip.
- Test: `WardrobeRepository` scan-filter test passes `findAll_excludesUsageRows` (a `usage#` row + a real item written → only the item returned) — demonstrates the scan is not polluted, with 100% branch coverage on the filter.
- Test: controller MockMvc `postStyle_overDailyCap_returns429` and `postTag_overDailyCap_returns429` (limit set to a tiny value) — demonstrates the `429` contract on both Claude endpoints and that Claude is not called once over cap.
- CLI: with `ensemble.usage.daily-limit=2`, three authenticated `POST /api/style` calls → the third returns `429` with the sanitized error body — demonstrates the cap end-to-end.

#### 4.0 Tasks

- [x] 4.1 **(RED→GREEN)** Write `UsagePropertiesTest` (`daily-limit` default `100`). Then implement `UsageProperties` `@ConfigurationProperties(prefix="ensemble.usage")` with `dailyLimit` (default 100).
- [x] 4.2 **(RED→GREEN→REFACTOR)** Write the scan-filter test `findAll_excludesUsageRows` (write a `usage#<date>` row + a real item, assert only the item returns). Then add the `usage#`-prefix exclusion to `WardrobeRepository.findAll()` (in-stream `filter` at demo scale, or a `ScanRequest` `FilterExpression`). Ensure 100% branch coverage on the filter.
- [x] 4.3 **(RED→GREEN)** Write `UsageRepositoryIT` (TestContainers, mirroring `WardrobeRepositoryIT`): `incrementIsAtomicAndPersists` (two increments → count 2, stored under `usage#<date>`). Then implement `UsageRepository.increment(dateKey)` using the low-level `DynamoDbClient` `UpdateItem` with `UpdateExpression "ADD #c :one"`, `ReturnValues=UPDATED_NEW`, returning the new count.
- [x] 4.4 **(RED→GREEN→REFACTOR)** Write `CallCapServiceTest` (`underLimit_allowsAndIncrements`, `atLimit_blocksWith429Signal`, `usesUtcDateKey`, `newUtcDay_resetsCount`) with a fixed `Clock` + mocked `UsageRepository`. Then implement `CallCapService.reserve()` (compute the UTC date from an injected `Clock`, increment via `UsageRepository`, throw `DailyCapExceededException` when the new count exceeds `dailyLimit`) and add a `ClockConfig` `Clock.systemUTC()` bean. 100% branch on the limit check.
- [x] 4.5 **(RED→GREEN)** Add MockMvc tests `postStyle_overDailyCap_returns429` (`StyleControllerTest`) and `postTag_overDailyCap_returns429` (`TaggingControllerTest`) with the cap service mocked to throw. Then call `callCapService.reserve()` at the **start** of both flows (before the Claude call) and map `DailyCapExceededException` → `429` with the shared `ErrorResponse` in `ApiExceptionHandler`.
- [x] 4.6 Add `ensemble.usage.daily-limit: 100` to `src/main/resources/application.yml` (+ a value in the test yml); confirm context tests load. Boot with `ensemble.usage.daily-limit=2` and capture the CallCapService/IT/scan-filter/controller test outputs + the three-call `429` curl proof.

### [x] 5.0 Integration, secret-safety & regression verification

Cross-cutting closeout: prove the passcode value is absent from the built client bundle, add the
new env-var placeholders without committing a secret, confirm coverage on the critical logic, run
the full specs 01–07 suites for no regressions, and refresh the docs. (Spec success metrics #3, #5, #6; Security Considerations.)

#### 5.0 Proof Artifact(s)

- Grep: after `npm run build`, `grep -rF "$ENSEMBLE_PASSCODE" src/main/resources/static/ ; echo "exit=$?"` returns no match (`exit=1`) — demonstrates the passcode value never appears in the client bundle (success metric #3), captured without committing the value.
- CLI: `./gradlew test` (full backend, specs 01–07) is green and `cd frontend && npm test -- --run` is green — demonstrates no regressions to the existing wardrobe/tagging/stylist flows (success metric #6).
- Coverage: `./gradlew jacocoTestReport` — the report shows ≥90% line and **100% branch** on token verification, the daily-limit check, and the wardrobe scan filter (report path cited) — demonstrates the coverage metric (#5).
- Diff: `.env.example` gains `ENSEMBLE_PASSCODE=<your-demo-passcode>` (and the optional `ENSEMBLE_SESSION_SECRET=`) placeholders, and `pre-commit run --all-files` passes (secret scan green) — demonstrates the secret-config wiring with no committed secret.
- Diff: `README.md` / relevant `docs/` updated with the PWA install steps and the passcode + daily-cap env vars/config — demonstrates the documentation step of the workflow.

#### 5.0 Tasks

- [x] 5.1 Add `ENSEMBLE_PASSCODE=<your-demo-passcode>` and the optional `ENSEMBLE_SESSION_SECRET=` placeholders to `.env.example`; confirm `.env` stays git-ignored and `pre-commit run --all-files` (secret scan) passes.
- [x] 5.2 Run `cd frontend && npm run build`, then `grep -rF "$ENSEMBLE_PASSCODE" src/main/resources/static/ ; echo "exit=$?"` → no match; capture the bundle-grep secret-safety proof.
- [x] 5.3 Run the full `./gradlew test` and `cd frontend && npm test -- --run`; confirm specs 01–07 are green (no regressions — pay attention to the specs 04/05 frontend api tests touched by the authed-fetch refactor); capture the proof.
- [x] 5.4 Run `./gradlew jacocoTestReport`; confirm ≥90% line + 100% branch on token verify, the daily-limit check, and the scan filter; cite the report path.
- [x] 5.5 Update `README.md` / relevant `docs/` with the PWA install steps and the passcode + daily-cap env vars/config; capture the doc diff.
