# 07 Questions Round 1 - PWA Install + Security Guards

Answer each question below (check one or more options, or add your own notes under **Notes:**). These four decisions materially change the functional requirements, proof artifacts, and implementation path, so I'm confirming them before writing the spec. Each has a recommendation you can simply accept.

Source issue: [avirdi1/ensemble#8](https://github.com/avirdi1/ensemble/issues/8) — "PWA install + security guards (passcode + daily cap)".

---

## 1. Passcode session mechanism

After the user types the correct passcode on the entry screen, how do subsequent `/api` requests stay authorized? (The raw passcode always lives only in the server env var — never in the client bundle — in every option; this is about what the client holds and sends *after* a correct entry.)

- [ x] (A) **Server mints an opaque session token** on correct passcode (`POST /api/auth`). Client stores it in `sessionStorage` and sends it as a header (e.g. `X-Ensemble-Session`) on each `/api` call; server validates per request. Token is a signed/stateless value (HMAC over an expiry, keyed by a server secret) so the server stays stateless across restarts/scale.
- [ ] (B) **HttpOnly, Secure, SameSite cookie** set by the server on correct passcode; the browser attaches it automatically. Raw passcode/token is never reachable from JS, but adds cookie + CSRF handling.
- [ ] (C) **Spring server-side HTTP session** (`HttpSession` / Spring Security form-style) with the passcode as the credential.
- [ ] (D) **Client stores the entered passcode** (`sessionStorage`) and sends it as a header on every request; server compares to the env var each time. Simplest, but the raw secret sits in client runtime storage.
- [ ] (E) Other (describe under Notes)

**Current best-practice context:** For a single-user demo behind one shared passcode, the goal is "server-enforced, secret never in the bundle" without dragging in a full auth stack. A one-time exchange for a short-lived token (A) keeps the raw passcode server-only and composes cleanly with the existing header-less `fetch` wrapper in `frontend/src/api/*.ts`.

**Recommended answer(s):** (A)

**Why this is recommended:**

- (A) keeps the raw passcode server-only (exchanged once), fits the app's **stateless single-container** design (ARCHITECTURE.md) via a signed token — no in-memory session that dies on restart/scale — and needs only a small `Authorization`-style header added to the existing fetch helpers.
- (D) is the simplest but leaves the **raw** secret in client storage and sends it on every call; (A) sends a revocable, expiring token instead, at nearly the same complexity.
- (B) is arguably the most locked-down but adds cookie + CSRF concerns that are overkill for a single-user demo; (C) pulls in server-side session state (and likely Spring Security), which is heavier than this issue needs and works against the stateless design.

---

## 2. What does the passcode gate protect?

Which requests require a valid session, and which stay open?

- [ x] (A) **All `/api/**` except** the auth endpoint (`POST /api/auth`) and `GET /api/health`. Static assets (SPA shell, `manifest.webmanifest`, service worker, icons) are always served so the app can load and render the passcode screen, and health stays open for probes/uptime.
- [ ] (B) **Only the expensive Claude endpoints** (`POST /api/style`, `POST /api/items/tag`). Wardrobe CRUD (`/api/items`) stays open.
- [ ] (C) **Everything, including static assets** — nothing loads at all until authenticated.
- [ ] (D) Other (describe under Notes)

**Recommended answer(s):** (A)

**Why this is recommended:**

- (A) enforces the gate on all data/AI APIs (including wardrobe photos and CRUD, which are the user's private content) while letting the **static PWA shell + assets** load — required for the passcode screen to render and for iPhone install to work.
- (C) would break PWA install and the passcode screen itself (the SPA can't load to ask for the passcode) and would block health probes.
- (B) leaves the user's wardrobe (photos, item data) publicly readable, which undercuts the point of the gate.

---

## 3. Daily call cap — what counts, and when does it reset?

The cap is the "~100/day → 429" cost backstop on the Claude endpoints.

- [ x] (A) **One global daily counter** shared across both Claude endpoints (`/api/style` + `/api/items/tag`), **incremented before** the Claude call (so a failed/timed-out Claude call still consumes budget — it still costs/risks spend), reset on a **UTC calendar-day** boundary (counter key includes the UTC date, e.g. `2026-07-16`). Returns **429** when the day's count would exceed the configured limit (~100). Limit is configurable via a property.
- [ ] (B) Same as (A) but **separate caps per endpoint** (e.g. 100 style + 100 vision).
- [ ] (C) Same as (A) but **count only successful** Claude calls (a failed upstream call does not consume budget).
- [ ] (D) **Rolling 24-hour window** instead of a UTC calendar day.
- [ ] (E) Other (describe under Notes)

**Recommended answer(s):** (A)

**Why this is recommended:**

- A **single global** budget matches the issue's "~100/day" backstop intent for a single-user app and is the simplest thing to reason about and demo.
- **UTC calendar-day** reset is deterministic and trivial to key/store; a rolling window (D) needs timestamp bookkeeping for little benefit here.
- **Counting before the call** (and counting failures) makes it a true *spend/abuse* guard: the cost/risk is incurred when we call Claude, not only when it succeeds, so (C) could let repeated failing calls run up spend.
- Making the limit a **property** keeps the demo tunable (and lets tests set it to a tiny number to prove the 429 without 100 real calls).

---

## 4. Where does the daily counter live?

Persistence for the counter, given the app's single-table DynamoDB decision.

> **Gotcha that drives this:** the wardrobe lists items via a **full table scan** of `ensemble-items` that maps *every* row to an `Item` bean (`WardrobeRepository.findAll()` → `WardrobeService.list()`). A counter row written into that same table would be scanned and rendered as a **junk garment** in the wardrobe grid unless it is explicitly filtered out.

- [ x] (A) **Reuse `ensemble-items`** with a reserved partition key (e.g. `usage#<UTC-date>`) and an **atomic `ADD` `UpdateItem`** (low-level `DynamoDbClient`, not the bean mapper). **Add a filter** to `findAll()`/the scan so reserved rows never surface as items. Honors the documented single-table design; no new table for deploy (#9).
- [ ] (B) **New dedicated table** (e.g. `ensemble-usage`) with its own atomic counter. Clean isolation from the wardrobe scan, but adds a second table to create locally and in Terraform (#9).
- [ ] (C) Other (describe under Notes)

**Current best-practice context:** DynamoDB atomic counters use an `UpdateItem` with an `ADD` update expression so concurrent increments don't race — this is the standard pattern regardless of A vs B.

**Recommended answer(s):** (A)

**Why this is recommended:**

- (A) honors the **explicit single-table decision** in ARCHITECTURE.md and adds **no new table** to provision now or in the deploy issue (#9).
- The scan-pollution risk is real but is neutralized by one **explicit, tested requirement**: `findAll()` filters out the reserved `usage#` prefix (demo scale is ~20 items, so a scan filter is cheap).
- (B) is cleaner in isolation and a fine choice if you'd rather keep the wardrobe scan untouched — the tradeoff is a second table's lifecycle. If you prefer isolation over the single-table rule, pick (B) and I'll spec it that way.

---

**Notes / anything else I should factor in (icons, app name/theme for the manifest, exact daily limit number, etc.):**

<!-- Non-blocking defaults I'll otherwise assume, override here if you like:
     - PWA registerType: autoUpdate; manifest name "Ensemble", theme/background from the maroon/beige tokens (#F3ECDD bg), display: standalone.
     - App icons: generate simple maroon-on-beige "E" mark icons (192, 512, maskable, 180 apple-touch); no external icon service.
     - Daily limit default: 100 (configurable property).
     - Passcode env var name: ENSEMBLE_PASSCODE (git-ignored .env locally; Secrets Manager at deploy #9). -->
