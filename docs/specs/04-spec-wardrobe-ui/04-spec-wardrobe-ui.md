# 04-spec-wardrobe-ui.md

## Introduction/Overview

Ensemble's backend can already store clothes, serve their photos, and auto-tag a
garment from a photo (issues #3–#4). What it lacks is a way for a person to
**use** any of that from a phone. This feature adds the **Wardrobe UI**: the
mobile-first React screens to add a piece of clothing (take/upload a photo → see
auto-generated tags → edit → save), browse everything you own as a photo grid,
and open a single item to edit its tags or delete it.

The primary goal is a complete, demoable front end for the wardrobe CRUD +
tag-preview flow, wired to the existing `/api/items` endpoints, styled as an
intentional, branded mobile experience (not a raw form dump), with no new
backend work required.

## Goals

- Let a user add a clothing item end-to-end on a phone browser: capture/upload a
  photo, trigger auto-tagging, edit the returned tags, and save the item.
- Show every owned item as a mobile-first photo grid that lazy-loads thumbnails.
- Let a user open one item to edit its tags or delete it.
- Establish a reusable front-end foundation — client-side routing, a shared API
  client layer, and a small branded design system — that later issues (stylist
  chat #6, PWA #8) build on without rework.
- Handle the real edge states gracefully: empty wardrobe, a degraded/empty
  tag suggestion (failed vision call still returns an editable `200`), and
  network/validation errors — without crashing or losing the user's photo.

## User Stories

- **As someone organizing my closet**, I want to snap a photo of a garment on my
  phone and have it tagged for me, so that I can catalog clothes quickly without
  typing every attribute.
- **As a user reviewing an auto-tag**, I want to correct any field before saving,
  so that the stored tags reflect the real garment even when the AI is unsure or
  the vision call failed.
- **As someone browsing my wardrobe**, I want to see all my clothes as photo
  thumbnails on my phone, so that I can visually recall what I own.
- **As a user maintaining my wardrobe**, I want to open an item to fix its tags or
  remove it, so that my catalog stays accurate over time.
- **As a first-time user with an empty wardrobe**, I want a clear prompt to add my
  first item, so that I know what to do next instead of seeing a blank screen.

## Demoable Units of Work

Four small vertical slices. Unit 1 is the shared foundation; Units 2–4 are the
three user-facing screens and can be demoed independently once Unit 1 exists.

### Unit 1: App shell, routing, API client & design foundation

**Purpose:** The reusable front-end skeleton every screen sits on — client-side
routing, a typed API client layer for `/api/items`, and a small branded
mobile-first design system (tokens + a few shared components). Serves all later
front-end work.

**Functional Requirements:**
- The system shall use client-side routing (**React Router**) with at least these
  routes: `/` (wardrobe grid), `/add` (add item), and `/item/:id` (item detail).
- The system shall provide a typed API client module (mirroring the existing
  `src/api/health.ts` pattern) exposing functions for: list items, get one item,
  get an item's photo URL, tag-preview (upload photo → suggestion), create item
  (multipart photo + tags), update tags, and delete item.
- Each API client function shall resolve with a parsed, typed body on a 2xx
  response and reject on a non-2xx or network failure so callers can render an
  error state.
- The system shall apply a mobile-first layout (single-column, touch-friendly
  targets) and a shared, intentional visual style (typography, color, spacing)
  rather than unstyled browser defaults.
- The app shell shall render a persistent way to navigate to "add item" and back
  to the grid from any screen.

**Proof Artifacts:**
- Test: `src/api/items.test.ts` (Vitest) passes — asserts each client function
  builds the correct request (method, path, multipart vs JSON body) and maps
  2xx/non-2xx/network outcomes — demonstrates the API layer contract.
- Test: a routing/render test passes showing `/`, `/add`, and `/item/:id` mount
  their respective screens — demonstrates routing works.
- Screenshot: the app shell at a mobile viewport (~390px wide) showing the
  branded style and navigation — demonstrates the design foundation.

### Unit 2: Wardrobe grid (browse owned items)

**Purpose:** The home screen — a photo grid of everything the user owns.

**Functional Requirements:**
- The system shall fetch items via the list endpoint on load and render each as a
  thumbnail using the item's `photoUrl`.
- Thumbnails shall lazy-load (photo bytes are fetched per-item via `photoUrl`,
  never embedded in the list response).
- Tapping a thumbnail shall navigate to that item's detail route (`/item/:id`).
- When the wardrobe is empty, the system shall show a clear empty state that
  invites the user to add their first item (linking to `/add`).
- When the list request fails, the system shall show a non-crashing error state
  with a way to retry.

**Proof Artifacts:**
- Test: a grid component test passes rendering N mocked items as N thumbnails and
  asserting a tap navigates to the detail route — demonstrates browse + navigate.
- Test: an empty-wardrobe test passes showing the empty state + add link —
  demonstrates the empty edge case.
- Screenshot: the populated grid at a mobile viewport — demonstrates thumbnails
  render.

### Unit 3: Add item (camera/upload → auto-tag → edit → save) — headline flow

**Purpose:** The headline slice: turn a photo into a saved, tagged item.

**Functional Requirements:**
- The user shall be able to select a garment photo via camera capture
  (`<input type="file" accept="image/*" capture>`) or file upload, and see a
  preview of the chosen photo.
- After a photo is selected, the system shall **automatically** call the
  tag-preview endpoint (no separate "get tags" button) and, while waiting, show a
  loading state; on success it shall pre-fill the editable tag form from the
  returned suggestion.
- The system shall render an editable tag form covering all tag fields:
  `category`, `primaryColor`, `secondaryColor`, `formality` (1–5), `pattern`,
  `warmth` (1–3), and `descriptors` (list). A null/absent suggestion field shall
  render as an empty, editable input (a degraded/failed vision call is a normal,
  editable state — never an error screen). `descriptors` shall be edited as
  add/remove chips (tap to remove a chip, a small input adds new ones).
- The system shall enforce, before enabling save, the same required fields the
  create endpoint validates: non-blank `category`, `formality` within 1–5, and
  `warmth` within 1–3.
- On save, the system shall create the item via the multipart create endpoint
  (the selected photo + edited tags) and, on success, navigate to the **wardrobe
  grid** with the new item visible (so the user can immediately add another).
- If the create request fails validation or network, the system shall show an
  error and preserve the user's photo and entered tags (no data loss).

**Proof Artifacts:**
- Test: an add-flow test passes simulating photo select → mocked tag-preview →
  pre-filled form → edit → save → create call issued with the right multipart
  payload — demonstrates the end-to-end headline flow.
- Test: a degraded-suggestion test passes showing an all-null suggestion still
  yields an editable form and a valid save — demonstrates the failed-tag fallback.
- Test: a validation test passes showing save is blocked until `category`,
  `formality`, and `warmth` are valid — demonstrates client-side guardrails.
- Screenshot / recording: on a phone browser, photo → auto-tags → edit → save,
  with the item then visible in the grid — demonstrates the acceptance criterion.

### Unit 4: Item detail (edit tags + delete)

**Purpose:** Maintain an existing item — correct its tags or remove it.

**Functional Requirements:**
- The system shall fetch and display a single item (photo + current editable
  tags) at `/item/:id`. Wear-history fields (`lastWorn`, `wornCount`) are **not**
  shown in this feature — they are deferred to issue #7.
- The system shall let the user edit the tag fields and save via the update-tags
  endpoint, applying the same required-field rules as create.
- The system shall let the user delete the item via the delete endpoint and, on
  success, navigate back to the grid with the item gone.
- Delete shall require an explicit confirmation step to avoid accidental removal.
- A fetch for a non-existent id shall show a non-crashing "not found" state, and a
  failed save/delete shall show an error without losing the user's context.

**Proof Artifacts:**
- Test: a detail edit test passes loading a mocked item, editing a field, saving,
  and asserting the update-tags call payload — demonstrates edit.
- Test: a delete test passes showing confirm → delete call issued → navigation
  back to grid — demonstrates delete with confirmation.
- Screenshot: the item detail screen at a mobile viewport — demonstrates the edit
  + delete surface.

## Non-Goals (Out of Scope)

1. **Stylist chat / outfit generation** (issue #6) — no `searchWardrobe`, no
   Sonnet reasoning, no outfit cards here.
2. **Re-pick loop & wear-history** ("I wore this") (issue #7) — the detail screen
   neither displays nor mutates wear-history (`lastWorn`/`wornCount`) in this
   feature; both the display and the action land in #7.
3. **PWA install / offline / service worker** (issue #8) — routing is chosen to be
   PWA-friendly, but no manifest or install flow ships here.
4. **Passcode gate / daily-cap UI** (issue #8) — no auth screen; the UI assumes
   direct access in local dev.
5. **Deploy / hosting** (issue #9) — local dev + the existing Spring static-asset
   serving only.
6. **New backend endpoints or changes** — this feature consumes the existing
   `/api/items` API as-is; if a genuine gap is found it is flagged, not silently
   changed.
7. **Multi-photo items, search/filter, sorting, bulk actions** — single photo per
   item; simple full-list grid only.

## Design Considerations

- **Direction:** distinctive and branded (per user decision), not raw browser
  defaults. Implementation should use the **frontend-design skill** to choose an
  intentional type scale, color system, and spacing rhythm, and apply them via a
  small set of shared tokens/components. The look should read as a real, cohesive
  product suitable for the demo recording (issue #10).
- **Mobile-first:** single-column, thumb-reachable primary actions, tap targets
  sized for touch; the design must hold up at ~390px width first, scaling up
  gracefully.
- **Key screens:** (1) grid of photo thumbnails with a prominent "add" affordance;
  (2) add flow with photo preview, a clear tagging/loading state, and an editable
  tag form; (3) item detail with photo, editable tags, and a guarded delete.
- **State clarity:** every screen has explicit loading, empty, error, and success
  states — especially the add flow's "tagging in progress" and the "degraded/empty
  suggestion is still editable" state.
- No specific external mockups exist; the design skill defines the visual system
  within these constraints.

## Repository Standards

- **React 19 + Vite 6, TypeScript**, mobile-first — per `AGENTS.md`. Built assets
  are emitted into Spring's `static/` (existing `vite.config.ts` `build.outDir`),
  so one process serves API + UI.
- **API access pattern:** follow `src/api/health.ts` — a typed interface plus an
  `async` function that returns the parsed body on 2xx and throws otherwise;
  same-origin `/api/**` calls (dev proxy already configured).
- **Testing:** Vitest + React Testing Library. Per `docs/TESTING.md`, test
  *meaningful* front-end logic (API clients, form/preview state, rendering
  decisions, edge cases) with real coverage; do **not** over-test view plumbing.
  No live backend/network calls in tests — mock `fetch`/the API client.
- **Boundary discipline:** the UI consumes DTOs (`ItemResponse`, `TagRequest`,
  `TagSuggestion`) as JSON contracts; mirror their shapes in TypeScript types.
- **Conventional commits**, small demoable units; pre-commit runs frontend tests +
  lint (ESLint) and must pass.

## Technical Considerations

- **Routing:** React Router (current stable line, **v7**) in SPA/library mode
  (`createBrowserRouter` or `<BrowserRouter>`). Add `react-router-dom` as a
  frontend dependency. Routes `/`, `/add`, `/item/:id`. Chosen for deep-linkable
  URLs and browser-back, which also eases the later PWA work (#8).
- **Server state:** plain React hooks + the typed API client are sufficient at
  demo scale (~20 items); no server-state library (e.g. React Query) is required.
  Keep fetch/loading/error handling in small hooks or per-screen state.
- **Photos:** render thumbnails from `ItemResponse.photoUrl` (the backend serves
  bytes at `GET /api/items/{id}/photo`); never embed image bytes in list JSON.
  Use native lazy-loading for grid images.
- **Add flow payloads:** tag-preview and create are **multipart** (`photo` part);
  update-tags is **JSON** (`TagRequest`). The client layer must build each request
  shape correctly. Client-side required-field checks must match backend validation
  (`category` non-blank, `formality` 1–5, `warmth` 1–3) to avoid a round-trip 400.
- **Degraded suggestion:** `TagSuggestion` is all-nullable by design; the form
  must treat any null field as empty-and-editable, not as an error.
- **Latest-standards note:** React Router v7 is the current line (v6 → v7
  consolidated the `react-router`/`react-router-dom` packages); pin the current
  stable version at implementation time and use the documented Vite SPA setup. No
  other external-standards research materially affects this spec.

## Security Considerations

- **No secrets in the front end.** The Claude API key lives only in the backend
  (env/`.env`); the UI never handles it. Tag-preview and tagging happen server-side.
- **No new auth here** — the passcode gate and daily cap are issue #8. This UI
  assumes local-dev access; do not build or fake an auth bypass.
- **Proof artifacts:** screenshots/recordings must not include any API key,
  `.env` contents, or other secrets. Photos used in demos are non-sensitive
  garment images.
- **Client input:** rely on the backend as the source of truth for validation and
  image safety (decode/pixel-cap already enforced server-side); client-side checks
  are UX guardrails, not a security boundary.

## Success Metrics

1. **Headline flow works on a phone browser:** take/upload a photo → see auto-tags
   → edit → save → the item appears in the grid (issue #5 acceptance criterion 1).
2. **Grid + maintenance work:** the grid shows all items with photos; item edit and
   delete both work end-to-end (acceptance criteria 2 and 3).
3. **Edge states covered:** empty wardrobe, degraded/empty tag suggestion, and
   network/validation errors each render a clear, non-crashing state.
4. **Quality gate:** Vitest + RTL suites for the API client and meaningful screen
   logic pass; ESLint clean; pre-commit green. (Front-end view plumbing is not held
   to the backend's 90% bar per `docs/TESTING.md`.)

## Open Questions

No open questions at this time. The four prior assumptions were confirmed with the
user and folded into the requirements above:

1. **Add-flow tag trigger** — tagging fires **automatically** on photo select (no
   separate "get tags" button).
2. **Post-save navigation** — return to the **wardrobe grid** with the new item
   visible.
3. **Wear-history on item detail** — **not shown** in this feature; deferred to #7
   (display + action both land there).
4. **`descriptors` editing** — **add/remove chips** (visual treatment via the
   frontend-design skill).
