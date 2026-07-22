# 26-spec-ui-improvements.md

## Introduction/Overview

Two front-of-house improvements to the Ensemble UI, plus the backend that
supports the first:

- **Part A — Save outfits.** Let the user save a look they like — an AI-picked
  look from the Stylist screen (`/`) or a hand-built look from the Build
  (`/assemble`) mannequin — to a new **Saved Outfits** page reachable from a new
  header nav link, and remove saved looks they no longer want. Saved outfits are
  persisted server-side in DynamoDB as a new first-class `SavedOutfit` entity.
- **Part B — Consistent responsive layout.** Make every screen render the way the
  Stylist landing page does today: full desktop width on desktop, mobile-first on
  phones. Today only the Stylist screen escapes a global phone-width cap; Build
  and the other screens look like a cramped phone screen on desktop.

Both are UX/plumbing improvements. **No new Claude/LLM capability is
introduced** — saving and browsing outfits and the layout fix are convenience
features, not a third AI job. At demo scale (~dozens of outfits) the outfit
listing scans the table, mirroring how `searchWardrobe` scans items.

## Goals

- Persist saved outfits server-side as a new `SavedOutfit` DynamoDB entity, with
  create/list/delete over a new `/api/outfits` REST API that mirrors the existing
  wardrobe-item slice one-to-one.
- Enforce a **save-time grounding guard**: every `itemId` in a save must exist in
  the wardrobe; an unknown id rejects the whole save with `400` (the synchronous
  analog of the stylist's grounding guardrail) — treated as 100%-branch critical
  logic per `docs/TESTING.md`.
- Turn the existing **cosmetic** "Save look" heart on the Stylist screen into a
  real save, and add an equivalent "Save outfit" action to the Build screen, both
  driving a real `idle → saving → saved/error` lifecycle.
- Add a **Saved Outfits page** at `/saved` listing every saved outfit with its
  pieces' photos, `source`/`reason` when present, and a remove control — with
  loading/empty/error states matching the existing wardrobe screens.
- Add a **`Saved` header nav link left of `Build`** (order: Saved · Build ·
  Wardrobe · + Add).
- Generalize the app shell so **all** screens fill the desktop width (up to a
  sensible max) while staying mobile-first single-column on phones, without
  regressing the Stylist two-pane layout.

## User Stories

- **As a user**, when the AI shows me a look I like on `/`, I want to save it, so
  I can find it again later without re-prompting.
- **As a user**, when I hand-build a look on the Build (`/assemble`) mannequin, I
  want to save it the same way I can save an AI look.
- **As a user**, I want a Saved Outfits page listing every outfit I've saved,
  showing the pieces (photos) in each, so I can browse my looks.
- **As a user**, I want to remove a saved outfit I no longer want.
- **As a user**, I want a nav link to Saved Outfits in the header, left of
  "Build", so it's always one tap away.
- **As a user on a laptop**, I want the Build screen (and every other screen) to
  use the whole window like the Stylist page does, instead of being stuck in a
  narrow phone-width column in the middle of my screen.

## Demoable Units of Work

### Unit 1: `SavedOutfit` persistence + `/api/outfits` API + grounding guard (local + cloud)

**Purpose:** Stand up the full server-side backbone for saved outfits — a new
persisted entity, its repository, a service with the grounding guard, DTOs, and a
gated REST controller — plus the local table auto-create and the small cloud
provisioning so the feature works in production. This is the foundation the
frontend units consume. Built strictly TDD per `AGENTS.md`.

**Functional Requirements:**
- The system shall add a new `com.ensemble.outfit` package holding a
  `@DynamoDbBean` entity **`SavedOutfit`** (mutable POJO, no-arg constructor,
  getters/setters, `@DynamoDbPartitionKey` on `getOutfitId()`) with fields:
  `String outfitId`, `List<String> itemIds` (ordered), `String source`
  (`"ai"` | `"manual"`), `String reason` (nullable — populated for AI looks,
  absent for manual), `Instant createdAt`. The name is `SavedOutfit`, **not**
  `Outfit`, because `com.ensemble.stylist.Outfit` already exists as an ephemeral
  stylist-pick value record.
- The system shall add an `OutfitRepository` (`@Repository`) mirroring
  `WardrobeRepository`: a `DynamoDbTable<SavedOutfit>` obtained from the shared
  `DynamoDbEnhancedClient` via `TableSchema.fromBean(SavedOutfit.class)` against a
  **new outfits table name** from config, exposing `save`, `findAll` (scan; no
  reserved-prefix filtering is needed since this is a dedicated table),
  `findById`, and `deleteById`.
- The system shall add an `OutfitService` (`@Service`) with `create`, `list`, and
  `delete`, generating `outfitId` as a random UUID and setting `createdAt` at
  save time (server-owned, never the client), routing id-based lookups through a
  single not-found choke point that throws `OutfitNotFoundException` (mirroring
  `WardrobeService.find`).
- The system shall enforce a **save-time grounding guard** in the service: build
  the set of valid wardrobe ids (from `WardrobeService.list()`), and if **any**
  submitted `itemId` is not a known wardrobe id, reject the **entire** save with
  `400` (no partial save, no silent drop). The guard shall also reject an empty
  `itemIds` list and a `source` outside `{"ai","manual"}` with `400`.
- The system shall add request/response DTO **records** in
  `com.ensemble.outfit.dto` (`SaveOutfitRequest { List<String> itemIds; String
  source; String reason }` with Jakarta bean-validation; `OutfitResponse
  { outfitId; itemIds; source; reason; createdAt }`) and an `OutfitMapper` (final,
  private constructor, static methods). The `SavedOutfit` entity shall never cross
  the controller boundary.
- The system shall add `com.ensemble.outfit.web.OutfitController`
  (`@RestController @RequestMapping("/api/outfits")`) with `POST /api/outfits`
  (returns `201 Created`), `GET /api/outfits` (returns all saved outfits), and
  `DELETE /api/outfits/{id}` (`204 No Content`), and shall register
  `OutfitController` in `ApiExceptionHandler`'s `@RestControllerAdvice(assignableTypes = {...})`
  so it inherits the shared error shape (grounding/validation failure → `400`
  `bad_request`; delete-unknown → `404`).
- The system shall add the outfits table name as a config property (e.g.
  `ensemble.dynamodb.outfits-table-name`, default `ensemble-outfits`, env
  `ENSEMBLE_OUTFITS_TABLE_NAME`) and extend the local table initializer so the
  outfits table (partition key `outfitId`, `PAY_PER_REQUEST`) is auto-created on
  dev startup, exactly as the items table is. Auto-create stays **off** in the
  cloud profile (Terraform owns the table).
- The system shall add the cloud provisioning for the new table: a Terraform
  `aws_dynamodb_table "outfits"` resource (`${local.prefix}-outfits`, hash_key
  `outfitId`) in `terraform/deploy/data_stores.tf`, and the
  `ENSEMBLE_OUTFITS_TABLE_NAME` env var wired to App Runner in
  `terraform/deploy/apprunner.tf`. **No IAM change** is required — the instance
  role's DynamoDB grant is already `table/${local.prefix}-*`, which covers the new
  table; the policy description shall be updated to mention it.

**Proof Artifacts:**
- Test: `OutfitRepositoryIT` (DynamoDB Local via TestContainers) — a create →
  list → delete round-trip on a real table — demonstrates persistence works
  against DynamoDB.
- Test: `OutfitServiceTest` — grounding guard with **100% branch coverage**: all
  ids valid → saved; any unknown id → rejected `400`; empty `itemIds` → `400`;
  bad `source` → `400`; delete-unknown → `404` — demonstrates the critical logic.
- Test: `OutfitControllerTest` (`@WebMvcTest` + MockMvc, service `@MockitoBean`) —
  `POST` returns `201` with the saved body; `GET` returns the list; `DELETE`
  returns `204`; a save with an unknown id returns `400`; a delete of an unknown
  id returns `404` — demonstrates the API contract and error paths.
- CLI: `curl -sS -X POST localhost:8080/api/outfits -H 'X-Ensemble-Session: <t>'
  -H 'Content-Type: application/json' -d '{"itemIds":["<real-id>"],"source":"manual"}'`
  returns `201`; the same with a bogus id returns `400`; `GET /api/outfits`
  lists it — demonstrates the end-to-end gated flow.
- CLI: `terraform -chdir=terraform/deploy validate` passes and `terraform plan`
  shows the new `aws_dynamodb_table.outfits` + the `ENSEMBLE_OUTFITS_TABLE_NAME`
  env var as the only outfit-related additions (no IAM diff) — demonstrates the
  cloud delta.

### Unit 2: Save wiring on Stylist (`/`) and Build (`/assemble`)

**Purpose:** Make both "save" affordances real. Add a typed `api/outfits` client
and wire the Stylist heart and a new Build "Save outfit" button to it, each with
a real save lifecycle — replacing the cosmetic local-state stub.

**Functional Requirements:**
- The system shall add a typed client `frontend/src/api/outfits.ts` mirroring
  `api/items.ts` (same `authedFetch` / `ensureOk` / typed-`ApiError` pattern,
  `BASE = '/api/outfits'`): `saveOutfit({ itemIds, source, reason? })`,
  `listOutfits()`, and `deleteOutfit(id)`, resolving on 2xx and throwing on any
  non-2xx/transport failure.
- On the Stylist screen, the system shall replace the cosmetic `saved` toggle in
  `frontend/src/components/OutfitResult.tsx` with a real save that `POST`s the
  current `outfit.itemIds` + `outfit.reason` with `source: "ai"`, and shall
  reflect a real `idle → saving → saved → error` lifecycle (not just a local
  boolean), disabling the control while a save is in flight and while the screen
  is otherwise `busy`. (Lifting the save handler/state into `Stylist.tsx`
  alongside the existing `onWearToday`/`logStatus` is preferred for consistency,
  but the meaningful logic to test is the state machine.)
- On the Build screen (`frontend/src/routes/Assemble.tsx`), the system shall add a
  **"Save outfit"** button in `.assemble-actions` (next to "Wear today") that
  saves `placedIds(placement)` with `source: "manual"`, mirroring the existing
  `idle | saving | saved | error` lifecycle already used for "Wear today", and
  shall **disable** it when nothing is placed (no empty outfit).
- The system shall surface a save failure as a retryable error affordance
  consistent with the existing error banners, and shall not persist anything on
  the client (the server owns the record).

**Proof Artifacts:**
- Test: component test — the Stylist save control calls `saveOutfit` once with the
  rendered look's `itemIds` + `reason` and `source: "ai"` (mocked), transitions to
  a saved state on success, and shows the error affordance on rejection —
  demonstrates the real save replaces the stub.
- Test: component test — Build "Save outfit" calls `saveOutfit` with the placed
  ids and `source: "manual"`, is disabled when nothing is placed, and reflects
  saved/error states — demonstrates the manual save + empty guard.
- Screenshot: the Stylist look after saving (heart in its saved state) and the
  Build screen showing the "Save outfit" button beside "Wear today" —
  demonstrates both affordances.

### Unit 3: Saved Outfits page (`/saved`) + route + header nav link

**Purpose:** Give saved looks a home. A new page lists saved outfits with their
photos, supports removal, and tolerates pieces that were deleted from the
wardrobe; a header nav link makes it reachable.

**Functional Requirements:**
- The system shall add a route `/saved` in `frontend/src/App.tsx` rendering a new
  `frontend/src/routes/SavedOutfits.tsx`, inside the existing `AuthGate`,
  alongside the current routes.
- The system shall add a `Saved` link in `App.tsx`'s `.app-nav` **left of
  `Build`**, so the order is **Saved · Build · Wardrobe · + Add**.
- The page shall fetch saved outfits (`listOutfits()`) and the current wardrobe
  (`listItems()`) and render a grid of outfits; each outfit shall show its pieces'
  photos via the existing `photoUrl(itemId)` builder, plus its `source` and
  `reason` when present, and a **remove** control that calls `deleteOutfit(id)`
  and drops the outfit from the list on success.
- The page shall handle **loading**, **empty** ("No saved outfits yet"), and
  **error** (with retry) states matching the `WardrobeGrid.tsx`
  `state-block` / `empty-state` / `state-note` patterns — never a blank or broken
  screen.
- For an outfit whose piece was later **deleted** from the wardrobe, the page
  shall resolve pieces at render time by matching saved `itemIds` against the
  current wardrobe list, **skip** the missing piece(s), render the surviving
  pieces, and show a subtle caption (e.g. "1 piece is no longer in your
  wardrobe"). If **every** piece is gone, it shall still render the card
  gracefully with that note — never crash and never render a broken tile. (The
  saved record is never rewritten when an item is deleted.)

**Proof Artifacts:**
- Test: routing test — navigating to `/saved` renders the Saved Outfits screen
  inside `AuthGate` — demonstrates the route is mounted and gated.
- Test: component test — the header renders a `Saved` link ordered before `Build`
  — demonstrates the nav placement.
- Test: component test — load renders outfit cards with pieces; empty renders the
  empty state; a fetch failure renders the error+retry state; remove calls
  `deleteOutfit` and removes the card — demonstrates the page states.
- Test: component test — a saved outfit referencing a since-deleted item renders
  the surviving pieces + the "no longer in your wardrobe" note and does not crash
  — demonstrates the deleted-piece tolerance (Q3-C behavior).
- Screenshot: the Saved Outfits page with several saved looks (AI + manual) and
  the header showing the new `Saved` link — demonstrates the page end-to-end.

### Unit 4: Consistent responsive layout (Part B)

**Purpose:** Fix the confirmed CSS root cause so every screen fills the desktop
width like the Stylist page, while staying mobile-first and not regressing the
Stylist two-pane layout.

**Functional Requirements:**
- The system shall change the app shell in `frontend/src/index.css` so the
  desktop-width behavior is the **default** rather than a `.stylist-layout`-only
  exception: the content column shall fit the viewport up to a sensible max
  (the existing `72rem`), and shall remain **mobile-first** (single phone-width
  column) on small screens — the behavior the Stylist page has today. The
  `#root { max-width: 30rem }` hard cap and the `#root:has(.stylist-layout)`
  special case shall be reconciled into this single default.
- The change shall apply to **all** narrow screens: Build/Assemble, Wardrobe,
  Add-item, Item-detail, and the new Saved Outfits page.
- Screens whose content is naturally narrow (e.g. the Add-item form, item detail)
  shall **center** within the wider shell rather than stretching edge-to-edge —
  matching today's look, just without a 30rem hard cap on desktop.
- The Stylist screen's existing two-pane (drawer + result) desktop experience
  shall be **visually unchanged**.

**Proof Artifacts:**
- Screenshot: `/assemble` on a desktop viewport filling the width comparably to
  `/` (before/after) — demonstrates the primary fix.
- Screenshot: `/wardrobe`, `/add`, `/item/:id`, and `/saved` on desktop, plus all
  screens on a phone viewport showing single-column, no horizontal scroll —
  demonstrates the fix is app-wide and mobile-first is preserved.
- Screenshot: `/` (Stylist) before/after showing the two-pane layout is unchanged
  — demonstrates no regression.

## Non-Goals (Out of Scope)

1. **No AI/LLM involvement in saving or browsing.** No Claude call in either
   part; no AI-generated rationale for a manual look. The stored `reason` is
   simply the stylist's existing whole-look text for AI saves, and absent for
   manual saves.
2. **No editing a saved outfit in place**, reordering pieces, or naming/renaming
   outfits. Save and delete only.
3. **No tags, favoriting, or sharing** of saved outfits.
4. **No per-user scoping** of saved outfits — they follow the current single-user
   demo model (tracked separately by #14 / #15). Everyone with the passcode sees
   the same saved outfits.
5. **No de-duplication of saves** — saving the same set of pieces twice creates
   two records (harmless at demo scale; the issue's own recommendation).
6. **No new AI daily-cap consumption.** Outfit routes make no Claude call, so —
   consistent with the existing non-Claude item CRUD — they do not call
   `CallCapService.reserve()` (see Security Considerations for the noted deviation
   from the issue's wording).
7. **No persisted per-item rationale** on a saved outfit. Per-item rationales are
   derived at style time and are not stored; the Saved Outfits page shows the
   whole-look `reason` only.

## Design Considerations

- **Reuse the existing visual language.** The Saved Outfits page reuses the
  wardrobe/grid patterns (`.screen`, `.grid`/`.thumb`, `.state-block`,
  `empty-state`, `state-note`, `.btn`/`.btn-danger`) and the existing design
  tokens in `index.css`; no new design system. New classes only where a saved-
  outfit card genuinely differs from a wardrobe cell.
- **Save affordances match today's lifecycle language.** The Stylist heart and
  the Build "Save outfit" button reuse the `idle | saving | saved | error`
  affordance pattern already established by "Wear today" (`btn-primary`,
  `btn-logged`-style done state, `banner-error` retry), so save reads
  consistently with wear-logging.
- **Deleted-piece note is quiet.** The "N piece(s) no longer in your wardrobe"
  caption uses the muted `state-note`/`eyebrow` voice — informative, not alarming.
- **Layout (Part B) preserves the current look.** Screens fill the width on
  desktop but naturally-narrow content (single forms) stays centered at a
  comfortable measure, matching the current feel; the Stylist two-pane is
  untouched. Mobile stays single-column with ≥44px touch targets and no
  horizontal scroll.

## Repository Standards

- **Backend layering & slice shape.** New work follows the established
  feature-slice convention: `com.ensemble.outfit` holds the `@DynamoDbBean`
  entity + `@Repository` + `@Service` + exceptions; `com.ensemble.outfit.web`
  holds the `@RestController`; `com.ensemble.outfit.dto` holds request/response
  **records** + a `*Mapper`. This mirrors `com.ensemble.wardrobe` one-to-one.
- **Persistence.** AWS SDK v2 DynamoDB Enhanced Client, single-item model; no
  Spring Data, no relational modeling. `SavedOutfit` gets its own dedicated table
  (Q1-A), keyed by `outfitId`.
- **DTOs at the boundary.** The Claude client, DynamoDB beans, and storage
  internals never leak into controllers; only DTO records cross the boundary.
- **TDD & coverage.** Strict TDD for the backend domain (entity/repo/service/guard
  + controller contracts): ≥90% line, and **100% branch on the grounding guard**
  (id-validation), per `AGENTS.md` / `docs/TESTING.md`. Frontend: Vitest + RTL on
  the meaningful logic (save state machines, page load/empty/error, deleted-piece
  handling, nav link) — do **not** over-test view plumbing or the CSS change.
- **Testing tools & naming.** Backend: JUnit 5, Mockito (`@MockitoBean` in
  `@WebMvcTest` slices), TestContainers DynamoDB Local for `*IT` tests, AssertJ;
  test method names `action_condition_expectedResult`. Frontend: Vitest + RTL.
- **Infra.** Terraform changes are `fmt`/`validate`-clean and plan cleanly; the IaC
  is validated by plan (per `docs/TESTING.md`), not unit tests.
- **Commits.** Conventional commits, roughly one per demoable unit.

## Technical Considerations

- **Naming collision (hard constraint).** `com.ensemble.stylist.Outfit` already
  exists as an ephemeral value record; the persisted entity is `SavedOutfit` in a
  new package. Frontend types for the saved entity should likewise avoid colliding
  with the existing `Outfit` interface in `api/style.ts` (e.g. name it
  `SavedOutfit`).
- **Separate table, not the shared items table (Q1-A).** `OutfitRepository` binds
  its own `DynamoDbTable<SavedOutfit>` to the outfits table. Because it is a
  dedicated table, its `findAll` scan needs **no** reserved-prefix filtering
  (unlike `WardrobeRepository.findAll`, which must exclude `usage#` counter rows
  from the shared items table).
- **Grounding guard mechanism.** `WardrobeService` exposes no "does this id exist"
  method today; the stylist builds a valid-id set from `wardrobe.list()`. The
  outfit guard reuses that approach (build the id set once, check membership),
  and — unlike the stylist's async drop-and-retry — a synchronous save
  **rejects the whole request** with `400` if any id is unknown.
- **Session gate is automatic.** `SessionAuthFilter` is a servlet filter on
  `/api/*` registered via `FilterRegistrationBean` (not a `@Component`), so
  `/api/outfits` is gated with no code change, and `@WebMvcTest` slices remain
  ungated for testing.
- **Photos via existing URL builder.** The Saved Outfits page renders each piece
  with `photoUrl(itemId)` (session token appended as `?token=` for `<img>`); no
  photo bytes are duplicated into the outfit record. Detecting a deleted piece
  requires the current wardrobe list, so the page fetches `listItems()` alongside
  `listOutfits()` and renders the intersection.
- **Local table auto-create.** The dev-time table initializer must ensure **both**
  tables (items + outfits); the cloud profile keeps `auto-create-table: false`
  because the instance role has item-level DynamoDB permissions only.
- **Cloud delta is minimal and pre-authorized.** The instance-role IAM grant is
  already `table/${local.prefix}-*`, so the new `${local.prefix}-outfits` table
  needs a Terraform table resource + an env var only — no IAM policy change and no
  expansion of the blast-radius box.
- **CSS root cause (Part B).** Confirmed: `#root { max-width: 30rem }` caps all
  screens and `#root:has(.stylist-layout) { max-width: 72rem }` is the only
  exception. The fix generalizes the 72rem default; `:has()` is already used in
  the codebase, so no new browser-support concern.

## Security Considerations

- **No new secrets and no new external surface.** No Claude key, passcode, or
  session material is introduced. Screenshots used as proof artifacts must not
  embed the demo passcode or a session token.
- **Auth gating.** All `/api/outfits` routes and the `/saved` page sit behind the
  existing passcode/session gate (`X-Ensemble-Session`), consistent with every
  other protected route/screen.
- **Daily-cap deviation (noted).** The issue says outfit routes should "count
  against the daily call cap like other `/api/**` routes." In this codebase the
  daily cap is a **Claude-spend backstop** applied per-Claude-endpoint via an
  explicit `CallCapService.reserve()` call (e.g. `StyleController`), **not** a
  blanket `/api/**` filter — the existing non-Claude item CRUD does not consume
  it. Outfit CRUD makes no Claude call, so for consistency it likewise does
  **not** call `reserve()`. This is a deliberate, low-risk deviation from the
  issue's wording; the routes remain session-gated. (Flagged as a non-blocking
  Open Question in case the operator wants a generic non-Claude cap later.)
- **Input handling.** `itemIds`/`source`/`reason` are validated (`source`
  restricted to `ai`/`manual`, `itemIds` grounded against the wardrobe); rendered
  strings (`reason`) are React-escaped by default. No user input reaches an LLM.
- **Grounding as a guard.** The save-time id-validation prevents persisting a look
  that references non-existent items, keeping the store consistent with the
  wardrobe.

## Success Metrics

1. **Persisted & grounded:** `POST /api/outfits` creates a `SavedOutfit`, `GET`
   lists it, `DELETE` removes it; a save with any unknown id returns `400` — all
   covered by tests, with 100% branch on the grounding guard.
2. **Both saves real:** the Stylist heart and the Build "Save outfit" button each
   persist via `POST /api/outfits` with the correct `source`, drive a real
   lifecycle, and (for manual) disable when nothing is placed.
3. **Browsable & removable:** `/saved` lists saved outfits with photos +
   `source`/`reason`, supports remove, and renders loading/empty/error states; a
   since-deleted piece is skipped with a note and never crashes the page.
4. **Reachable:** the `Saved` nav link renders left of `Build` and routes to
   `/saved`.
5. **Responsive:** on desktop, `/assemble`, `/wardrobe`, `/add`, `/item/:id`, and
   `/saved` fill the width comparably to `/`; on a phone all screens stay
   single-column with no horizontal scroll; the Stylist layout is unchanged.
6. **No regression:** existing backend + frontend tests stay green; the only cloud
   IaC delta is the outfits table + env var (no IAM diff).

## Open Questions

1. **Save-handler placement (Stylist).** Whether the AI-save state/handler lives
   in `Stylist.tsx` (mirroring `onWearToday`/`logStatus`) or self-contained in
   `OutfitResult.tsx`. Assumption: lift into the route for consistency; either is
   acceptable as long as the state machine is tested. Non-blocking (task-planning
   detail).
2. **Generic non-Claude daily cap.** The cap deliberately does not apply to
   outfit CRUD (see Security Considerations). Assumption: this matches existing
   non-Claude CRUD and is correct; if the operator later wants a blanket request
   cap that is a separate concern. Non-blocking.
3. **Saved-outfit card detail.** Whether a card shows anything beyond piece
   photos + `source`/`reason` + remove (e.g. a saved-date or piece count).
   Assumption: photos + `source`/`reason` + remove is sufficient for this issue;
   extra detail is a nice-to-have. Non-blocking (design detail for task planning).
