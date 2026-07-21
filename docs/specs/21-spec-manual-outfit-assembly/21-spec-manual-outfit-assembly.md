# 21-spec-manual-outfit-assembly.md

## Introduction/Overview

A new `/assemble` screen where the user builds an outfit **by hand** — dragging
wardrobe item photos onto a mannequin silhouette — instead of asking the AI
stylist to pick one. It complements the existing AI-driven Stylist flow (`/`)
without replacing it or calling into it. This is deliberately the app's
**non-AI escape hatch**: it makes no Claude call, generates no rationale, and is
in scope purely as a UX convenience for the "let me just try this myself" path
that neither the picked-for-you Stylist (`/`) nor the flat wardrobe grid
(`/wardrobe`) offers today.

The primary goal is a mobile-first, drag-and-drop assembly surface that reuses
the existing `Slot` model (`TOP`/`BOTTOM`/`SHOES`/`CARRY`/`PIECE`), the
wardrobe drawer's tile/search pattern, and the existing per-item wear-log
endpoint — introducing **no new backend endpoints and no new persisted
entities**.

## Goals

- Add an `/assemble` route, gated by the existing `AuthGate`, reachable from
  both the Stylist screen (`/`) and the wardrobe grid (`/wardrobe`).
- Let the user drag wardrobe items onto a static mannequin silhouette whose
  drop zones are keyed by the existing `Slot` type, routing each dragged item to
  a sensible default zone via the existing `slotForCategory`, while still
  allowing a manual override by dropping into a different zone.
- Enforce the occupancy rules: `TOP`/`BOTTOM`/`SHOES` hold **one item each**
  (drop replaces the current occupant); the `CARRY`/`PIECE` "extras" tray holds
  **multiple** items.
- Let the user remove or swap a placed item without a drag being the only way
  (a tap "×" affordance, plus drag-back-to-source).
- Log "Wear today" on the assembled set by fanning out to the existing
  per-item `markWorn` (`POST /api/items/:id/worn`) — the same pattern
  `OutfitResult.tsx` already uses — with no new backend work.
- Be mobile-first: touch targets ≥44px, drag works with touch input on a real
  device, and any drop animation honors `prefers-reduced-motion`.

## User Stories

- **As a wardrobe owner**, I want to drag items from my wardrobe onto a
  mannequin, so I can visually preview an outfit I put together myself instead
  of asking the stylist to pick one.
- **As a wardrobe owner**, I want the mannequin to have body-region drop zones
  (top, bottom, shoes, carry/extras), so items land somewhere sensible without
  me having to think about where each piece goes.
- **As a wardrobe owner**, I want to swap an item out (drag a new top over an
  old one) or remove a placed item (tap an "×"), so I can iterate on the look
  before committing.
- **As a wardrobe owner**, I want to log "Wear today" on my manually-assembled
  outfit the same way I can on an AI-picked one, so wear-history stays accurate
  regardless of how I built the look.
- **As a wardrobe owner with an empty closet**, I want a clear message pointing
  me to add items, so I never face a blank or broken drag surface.

## Demoable Units of Work

### Unit 1: `/assemble` route, mannequin scaffold, and entry points

**Purpose:** Stand up the reachable, gated screen with a static mannequin and
its Slot-keyed zones, plus the empty-wardrobe state — before any drag behavior.
This is the smallest end-to-end slice: a user can navigate to the screen and see
the mannequin with labeled zones and their wardrobe.

**Functional Requirements:**
- The system shall add a route `/assemble` in `frontend/src/App.tsx`, rendered
  inside the existing `AuthGate`, alongside the current `/`, `/wardrobe`,
  `/add`, and `/item/:id` routes (a sibling of `/`, **not** nested under it).
- The system shall render a **static SVG mannequin silhouette** (drawn
  line-art — not a photo, not 3D) with labeled drop zones keyed to the existing
  `Slot` type in `frontend/src/lib/specSheet.ts`: a head-to-torso region for
  `TOP`, a legs region for `BOTTOM`, a feet region for `SHOES`, and a side
  "extras" tray for `CARRY`/`PIECE`.
- The system shall add an entry-point link/button to `/assemble` from the
  Stylist screen (e.g. a "Build it yourself" affordance near the existing chat
  entry point) and from the wardrobe grid (`/wardrobe`).
- The system shall render an **empty-wardrobe state** when the item list is
  empty — a clear message pointing to `/add`, matching the existing
  `state-block empty-state` / `empty-title` / `btn btn-primary` pattern used by
  `WardrobeGrid.tsx` — instead of a blank or broken drag surface.
- The system shall reuse the existing loading and list-failure handling pattern
  (as `WardrobeDrawer.tsx` / `WardrobeGrid.tsx` do) so a failed `listItems()`
  call never crashes the screen.

**Proof Artifacts:**
- Test: routing test — navigating to `/assemble` renders the assemble screen
  (and it sits inside `AuthGate`) — demonstrates the route is mounted and gated.
- Screenshot: `/assemble` with a populated wardrobe showing the mannequin +
  labeled zones + source list — demonstrates the scaffold renders.
- Screenshot: `/assemble` with an empty wardrobe showing the "add items"
  message — demonstrates the empty-state guard.
- Test: component test — Stylist and Wardrobe screens render a link to
  `/assemble` — demonstrates both entry points exist.

### Unit 2: Placement model + drag-and-drop with dnd-kit

**Purpose:** The core interaction. Introduce a pure, unit-tested placement model
and wire `@dnd-kit/core` so items drag from the wardrobe source onto the
mannequin zones, with default-slot routing, single-occupancy replace, and the
multi-occupancy extras tray.

**Functional Requirements:**
- The system shall add `@dnd-kit/core` (and `@dnd-kit/modifiers` only if used
  for snap/constraint behavior) to `frontend/package.json`.
- The system shall implement the placement behavior as a **pure, unit-tested
  module** (a reducer/helper over an in-memory placement state), separate from
  the React view, so the rules are testable without simulating a drag in jsdom.
- On drop, the system shall assign a dragged item to its default zone via the
  existing `slotForCategory(item.category)` (reused unchanged — no second
  slot-mapping function), **unless** the user dropped it onto a different zone,
  in which case the item lands in the drop-target zone (manual override).
- The system shall treat `TOP`, `BOTTOM`, and `SHOES` as **single-occupancy**:
  dropping a new item into one of these zones **replaces** the current occupant
  (the replaced item returns to the available source list).
- The system shall treat the `CARRY`/`PIECE` extras tray as
  **multi-occupancy**: it accumulates multiple items (e.g. bag + jewelry +
  layered jacket) that coexist.
- The system shall wire dnd-kit with both a **pointer sensor and a touch
  sensor** so drag works with mouse and touch input, and shall respect
  `prefers-reduced-motion` for any drop/return animation.
- The assembled outfit shall be **session-only in-memory state** — no
  persistence, no new backend call in this unit.

**Proof Artifacts:**
- Test: placement-model unit tests covering default-slot routing, single-
  occupancy replace (`TOP`/`BOTTOM`/`SHOES`), multi-occupancy tray
  (`CARRY`/`PIECE`), and drop-into-different-zone override — demonstrates the
  rules, with full branch coverage on the placement logic.
- Test: component/interaction test exercising the drop handler (a simulated
  drop event routes an item to the expected zone and replaces an occupant) —
  demonstrates the view is wired to the model.
- Screenshot or screen recording: items dragged onto the mannequin landing in
  auto-slotted zones, a replace-on-drop, and multiple items in the extras tray
  — demonstrates the interaction end-to-end.
- CLI: `grep -n "@dnd-kit/core" frontend/package.json` returns the dependency —
  demonstrates the library was added.

### Unit 3: Remove/undo affordance + real wardrobe-drawer source

**Purpose:** Let the user iterate — remove or swap placed items without needing
a drag gesture — and replace any placeholder source list with the real
drawer-style tiles + search, filtered to items not already placed.

**Functional Requirements:**
- The system shall render a small remove ("×") affordance on each **placed**
  tile that removes that item from the mannequin with a tap (no drag required),
  sized to the ≥44px touch-target convention.
- The system shall also support **drag-back-to-source** to remove a placed item
  (dragging a placed tile back into the drawer/source list).
- The system shall render the drag source using the wardrobe drawer's existing
  **tile markup and search pattern** (the `drawer-grid` / `drawer-tile` /
  `drawer-search` structure from `WardrobeDrawer.tsx`), so items look and behave
  as they do elsewhere in the app.
- The system shall **exclude already-placed items** from the source list, so an
  item cannot be placed twice; a removed item reappears in the source list.
- Removed and re-added items shall keep the placement rules consistent (a
  removed single-occupancy item frees its zone; a removed tray item leaves the
  other tray items intact).

**Proof Artifacts:**
- Test: component test — tapping "×" on a placed tile removes it and it
  reappears in the source list — demonstrates tap-remove without a drag.
- Test: unit/component test — the source list omits placed item ids and the
  search filter narrows the tiles — demonstrates exclusion + search reuse.
- Screenshot: a placed tile showing the "×" affordance and the source grid with
  a placed item absent — demonstrates the iterate/undo flow.

### Unit 4: "Wear today" fan-out to `markWorn`

**Purpose:** Close the loop so a manually-assembled look updates wear-history
exactly like an AI-picked one, reusing the existing per-item endpoint and the
existing lifecycle affordances.

**Functional Requirements:**
- The system shall provide a "Wear today" action on the assembled set that fans
  out to the existing `markWorn(id)` (`POST /api/items/:id/worn`) **once per
  placed item id**, mirroring the `Promise.allSettled(...markWorn...)` pattern
  in `Stylist.tsx` / `OutfitResult.tsx` — introducing **no new backend
  endpoint and no new persisted entity**.
- The system shall reflect the wear-log lifecycle with the existing affordance
  pattern: an in-flight/disabled state, a locked "Logged ✓" success state, and
  a retryable error state when any per-item write fails.
- The system shall disable/withhold the "Wear today" action when **no items are
  placed** (nothing to log).
- The wear-log write shall remain deterministic and server-owned (the backend
  increments `wornCount` and sets `lastWorn`); the screen shall not compute
  wear-history itself.

**Proof Artifacts:**
- Test: component test — "Wear today" on an assembled set calls `markWorn`
  exactly once per placed item id (mocked), and locks to "Logged ✓" on success
  — demonstrates the fan-out and success affordance.
- Test: component test — a rejected `markWorn` surfaces the retryable error
  state; the action is disabled when nothing is placed — demonstrates the edge
  paths.
- Screenshot: an assembled look after "Wear today" showing the "Logged ✓"
  state — demonstrates the end-to-end wear-log.

## Non-Goals (Out of Scope)

1. **No LLM/Claude call anywhere on this page.** No AI-generated rationale for a
   manually-built look. A rationale here would be a separate, explicitly-scoped
   future issue — not implied by this one.
2. **No new backend persistence for "outfits."** A manually-assembled outfit is
   UI-only session state; there is no stored `Outfit` entity today and this
   issue does not add one. Wear-history writes via the existing per-item
   endpoint are the only server-side effect.
3. **No saving / naming / favoriting assembled looks** for later. The look is
   session-only; a page reload starts fresh. "Save this look" is separate scope.
4. **No 3D or photorealistic mannequin/fitting.** A flat SVG line-art silhouette
   with drop zones is sufficient at this scale.
5. **No hard dependency on #18 (category taxonomy).** Categories are free text
   today; the page works with whatever `slotForCategory` produces now
   (unrecognized categories fall into `PIECE`, the existing graceful degrade).
   If #18 ships first, slot accuracy improves for free with no rework here.
6. **No keyboard-only drag-and-drop requirement.** dnd-kit's keyboard sensor is
   available if picked up later, but non-pointer accessibility is not a blocking
   requirement for this single-user demo (tracked as an Open Risk).
7. **Tap-to-select + tap-to-place is not the primary interaction.** Drag is the
   headline (the issue asked for "like a game, dragging onto a person"); the tap
   "×" is only the remove affordance, not a placement mechanism.

## Design Considerations

- **Visual language:** follow the drawer/tray/tile/pip language established by
  spec `docs/specs/20-spec-stylist-screen-redesign/` (issue #12) rather than
  inventing a new style. Reuse the existing tokens where they fit —
  `--paper-sunk`, `--border`, `--ink-2`, `--placeholder`, `--pip-empty`,
  `--accent`, `--accent-line`, and the `--radius*` scale — all already defined
  in `frontend/src/index.css`. No new design tokens are required.
- **Mannequin:** a static, drawn SVG silhouette with four visible drop regions
  (head-to-torso `TOP`, legs `BOTTOM`, feet `SHOES`) plus a side "extras" tray
  (`CARRY`/`PIECE`). Zones should be labeled so it is obvious what lands where.
- **Forgiving hit-boxes:** per the issue's watch-item, the drop-zone hit areas
  must be generously sized (and may overlap) rather than pixel-perfect
  silhouette-shaped, so a thumb on a small screen can reliably hit a zone.
- **Placed-tile affordances:** each placed item shows its photo and a remove
  "×" control sized ≥44px. Placed tiles reuse the existing tile look
  (`drawer-tile` / rounded thumbnail).
- **Mobile-first & motion:** the layout stacks on narrow viewports; touch
  targets stay ≥44px (existing CSS convention); any drop/return animation is
  guarded by the existing `@media (prefers-reduced-motion: reduce)` block.
- **Source list:** reuses the `WardrobeDrawer` search box + `drawer-grid`
  2-column tile grid so the wardrobe reads consistently across screens.

## Repository Standards

- **Frontend framework:** React 19 + Vite 6, mobile-first, `react-router-dom`
  routes mounted in `App.tsx` inside `AuthGate`. New screen lives under
  `frontend/src/routes/` (e.g. `Assemble.tsx`); shared logic under
  `frontend/src/lib/`; reusable UI under `frontend/src/components/`.
- **Reuse over re-invention:** reuse `slotForCategory` (`lib/specSheet.ts`)
  unchanged; reuse `markWorn` / `photoUrl` / `listItems` from the existing API
  clients (`api/items.ts`; `markWorn` and `photoUrl` are also re-exported from
  `api/style.ts`); reuse the drawer/empty-state markup and CSS classes.
- **Testing:** Vitest + React Testing Library. Per `docs/TESTING.md`, this is
  **frontend UI wiring — test the meaningful logic, do not over-test view
  plumbing**. The placement rules are the meaningful logic and should be a pure,
  well-covered module; drag simulation in jsdom is not required for coverage.
- **Commits:** conventional commits, roughly one per demoable unit.
- **No backend changes:** no Java/Gradle/DynamoDB changes; no new API contract.

## Technical Considerations

- **Dependency (verified):** `@dnd-kit/core@6.3.1` declares
  `peerDependencies` of `react >=16.8.0` and `react-dom >=16.8.0`, and
  `@dnd-kit/modifiers@9.0.0` pins `@dnd-kit/core ^6.3.0` + `react >=16.8.0`.
  The app's React 19.1 satisfies both ranges, so the packages install cleanly
  with no peer conflict (no `--legacy-peer-deps` needed). This is dnd-kit's
  stable 6.x line; the newer `@dnd-kit/react` (currently pre-1.0, a separate
  rewrite) is intentionally **not** chosen. dnd-kit is preferred over
  `react-dnd` because it has first-class pointer *and* touch sensors built in
  (mobile-first) without a separate touch-backend package.
- **Testing drag in jsdom (material):** dnd-kit's `PointerSensor` relies on real
  pointer events and layout measurement (`getBoundingClientRect`) that jsdom
  does not implement, so a full drag gesture cannot be reliably simulated in
  Vitest. The placement rules must therefore live in a **pure reducer/helper**
  that is unit-tested directly; component tests assert the wiring (that a drop
  event routes to the model, that "×" removes, that "Wear today" fans out),
  and full drag fidelity is validated by the on-device proof artifact, not
  jsdom. This aligns with `docs/TESTING.md`'s "do not over-test view plumbing."
- **`slotForCategory` reuse:** the default-zone routing uses
  `slotForCategory(category)` exactly as `OutfitResult.tsx` does; unrecognized
  or null categories degrade to `PIECE` (the extras tray), so the screen never
  fails to place an item.
- **`WardrobeDrawer` is display-only today:** it fetches its own items via
  `listItems()` and exposes only an `inLookIds` prop (no click/drag handlers and
  no exclusion filter). The drag source therefore reuses the drawer's *markup
  and search pattern* but needs draggable tiles and a "placed-ids to exclude"
  input — implemented either by extending `WardrobeDrawer` with those props or
  by a sibling source component that shares the CSS classes. Either is
  acceptable; do not fork a divergent tile style.
- **`Item` type is unchanged:** `frontend/src/types/item.ts`
  (`{ itemId, category, primaryColor, ..., photoUrl, wornCount, lastWorn }`)
  needs no new fields; placement state is a client-side map of `Slot` → placed
  item id(s).
- **Wear-log parity:** reuse the exact `Promise.allSettled(pieces.map(p =>
  markWorn(p.itemId)))` shape and `idle | logging | logged | error` lifecycle
  from `Stylist.tsx`/`OutfitResult.tsx` so the two screens behave identically.
- **No AI, no images to the model:** there is no Claude call on this screen; the
  app only renders stored photos via `photoUrl(id)`.

## Security Considerations

- **No new secrets and no new backend surface.** The only server call is the
  existing authenticated `POST /api/items/:id/worn`; no new endpoint, no new
  credential, no new persisted data.
- **Auth gating:** `/assemble` sits inside the existing `AuthGate`, so it is
  reachable only with a valid session token, consistent with every other
  protected screen.
- **No user input reaches an LLM** (there is no LLM call here); rendered text is
  item tags already in the wardrobe, and React escapes rendered strings by
  default.
- **Proof artifacts:** screenshots should not embed the demo passcode or a
  session token; nothing sensitive is introduced by this feature.

## Success Metrics

1. **Reachable & gated:** `/assemble` renders behind `AuthGate` and is linked
   from both `/` and `/wardrobe` (routing + link tests pass).
2. **Correct placement:** dragging an item auto-slots it via `slotForCategory`
   or into the drop-target zone if different; `TOP`/`BOTTOM`/`SHOES` replace on
   drop; the `CARRY`/`PIECE` tray accepts multiple — verified by full-branch
   unit tests on the placement model.
3. **Iterable:** a placed item can be removed via a tap "×" (no drag) and via
   drag-back; placed items are excluded from the source list.
4. **Wear-log parity:** "Wear today" calls `markWorn` exactly once per placed
   item and updates `wornCount`/`lastWorn`; success/error/disabled states match
   the Stylist screen.
5. **Mobile:** works with touch input on a real device (on-device proof), touch
   targets ≥44px, `prefers-reduced-motion` respected.
6. **No regression / no scope creep:** existing backend and frontend tests stay
   green; no new backend endpoints or persisted entities were introduced.

## Open Questions

1. **Mannequin SVG art** — the exact silhouette artwork is an implementation
   detail. Assumption: a simple hand-drawn/line-art SVG authored in-repo (no
   external asset, no license concern) is acceptable; the zones matter more than
   the art. Non-blocking.
2. **Drag source: extend `WardrobeDrawer` vs. new sibling component** — both
   satisfy the requirement (reuse the tile/search markup, add draggable tiles +
   placed-id exclusion). Assumption: either is fine as long as the tile styling
   is not forked. Non-blocking; an implementation choice for task planning.
3. **Placed-tile detail** — whether a placed tile shows anything beyond the
   photo + "×" (e.g. a slot label or FORM/WARM pips via the existing
   `RatingPips`). Assumption: photo + remove "×" is sufficient for this issue;
   extra detail is a nice-to-have, not required. Non-blocking.
4. **Touch drag on iOS Safari (watch-item, not blocking)** — dnd-kit
   `PointerSensor`/`TouchSensor` activation constraints (distance/delay
   thresholds) to resolve scroll-vs-drag conflicts need real on-device tuning,
   captured as the Unit 2 touch requirement + an on-device proof artifact rather
   than a pre-decided constant.
