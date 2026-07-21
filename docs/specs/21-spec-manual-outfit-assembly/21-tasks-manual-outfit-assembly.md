# 21-tasks-manual-outfit-assembly.md

Task list for **issue #20 — Manual outfit-assembly page (`/assemble`)**. Derived
from `21-spec-manual-outfit-assembly.md`. Parent tasks map 1:1 to the spec's four
Demoable Units of Work; each is an end-to-end vertical slice that can be shown on
its own.

Follow the repository's mandatory TDD workflow (RED → GREEN → REFACTOR) and the
`docs/TESTING.md` coverage split: the **placement model is the meaningful logic**
and gets a pure, fully-branch-covered unit module; React view wiring is tested for
the meaningful behavior (a synthetic drop routes to the model, "×" removes,
"Wear today" fans out) and **not** over-tested for real drag fidelity in jsdom
(dnd-kit sensors + layout measurement are unavailable there — see the spec's
Technical Considerations). Conventional commits, roughly one per demoable unit.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/src/routes/Assemble.tsx` | **New.** The `/assemble` screen: item fetch + states, `DndContext`, mannequin + source composition, placement state, remove "×", and the "Wear today" fan-out. |
| `frontend/src/routes/Assemble.test.tsx` | **New.** Component/wiring tests: gated route render, empty/error states, zone labels, synthetic `onDragEnd` routing/replace, tap-remove + drag-back wiring, wear-log fan-out + lifecycle. |
| `frontend/src/lib/placement.ts` | **New.** Pure placement reducer/helper (`Slot` → placed id(s)): default routing (reuses `slotForCategory`), override, single-occupancy replace, multi-occupancy tray, remove, `placedIds`. No React. |
| `frontend/src/lib/placement.test.ts` | **New.** Unit tests for `placement.ts` — the meaningful-logic module, **100% branch coverage** required. |
| `frontend/src/lib/dndConfig.ts` | **New.** Exported dnd-kit sensor descriptors + activation constraints (pointer distance; touch delay/tolerance) so the mobile-first sensor config is unit-testable and documents the on-device-tuning deferral. |
| `frontend/src/lib/dndConfig.test.ts` | **New.** Asserts both a pointer and a touch sensor are configured with the intended activation constraints. |
| `frontend/src/components/Mannequin.tsx` | **New.** Static hand-authored SVG line-art silhouette with four labeled, `Slot`-keyed droppable regions (generous, possibly-overlapping hit-boxes). |
| `frontend/src/components/AssembleSource.tsx` | **New.** Sibling drag-source reusing `WardrobeDrawer`'s `drawer-grid` / `drawer-tile` / `drawer-search` classes + `searchText`; adds draggable tiles + a `placedIds` exclusion filter. Does **not** modify `WardrobeDrawer`. |
| `frontend/src/components/AssembleSource.test.tsx` | **New.** Tests: placed ids excluded; search narrows visible tiles; tiles are draggable. |
| `frontend/src/App.tsx` | **Modify.** Register `<Route path="/assemble">` inside `AuthGate`, sibling of `/`. |
| `frontend/src/App.test.tsx` | **Modify.** Add the gated-route render case for `/assemble`. |
| `frontend/src/routes/Stylist.tsx` | **Modify.** Add a "Build it yourself" entry-point link to `/assemble`. |
| `frontend/src/routes/Stylist.test.tsx` | **Modify.** Assert the `/assemble` entry-point link renders. |
| `frontend/src/routes/WardrobeGrid.tsx` | **Modify.** Add an entry-point link to `/assemble` in the populated grid view. |
| `frontend/src/routes/WardrobeGrid.test.tsx` | **Modify.** Assert the `/assemble` entry-point link renders. |
| `frontend/src/index.css` | **Modify.** Mannequin + zone + placed-tile + "×" styles using existing tokens; drop/return animation inside the existing `@media (prefers-reduced-motion: reduce)` block; ≥44px touch targets. |
| `frontend/package.json` | **Modify.** Add `@dnd-kit/core` (and `@dnd-kit/modifiers` only if used for snap/constraint). |
| `frontend/package-lock.json` | **Modify.** Lockfile updated by `npm install` for the new dependency. |
| `frontend/src/lib/specSheet.ts` | **Reuse, unchanged.** `slotForCategory` + `Slot` are imported by `placement.ts` (no second slot-mapping function). |
| `frontend/src/api/items.ts` | **Reuse, unchanged.** `listItems`, `photoUrl`, `markWorn` (also re-exported from `api/style.ts`). |
| `frontend/src/components/WardrobeDrawer.tsx` | **Reference, unchanged.** Source of the tile/search markup + `searchText` pattern that `AssembleSource.tsx` mirrors. |

### Notes

- Unit tests sit alongside the code they test (`placement.ts` + `placement.test.ts`).
- Run frontend tests with `cd frontend && npm test -- --run`; lint with `npm run lint`. Branch coverage on `placement.ts` via the Vitest coverage run.
- Follow existing conventions: the `MemoryRouter` + pre-seeded `SESSION_TOKEN_STORAGE_KEY` test pattern (`App.test.tsx`), the `settle()` loading/error pattern (`WardrobeGrid.tsx` / `WardrobeDrawer.tsx`), and the `Promise.allSettled(...markWorn...)` + `idle|logging|logged|error` lifecycle (`Stylist.tsx` / `OutfitResult.tsx`).
- Adhere to the pre-commit gates (vitest, eslint, secret scan). No backend Java/Gradle changes.

## Tasks

### [x] 1.0 `/assemble` route, mannequin scaffold, and entry points

Stand up the reachable, `AuthGate`-gated screen: a static hand-authored SVG
mannequin with four `Slot`-keyed, labeled drop regions (TOP head-to-torso,
BOTTOM legs, SHOES feet, CARRY/PIECE side "extras" tray), plus the
loading / list-failure / empty-wardrobe states — before any drag behavior. Add
entry-point links from the Stylist screen (`/`) and the wardrobe grid
(`/wardrobe`). Covers spec Unit 1 and Success Metric 1.

#### 1.0 Proof Artifact(s)

- Test: `Assemble.test.tsx` (or `App.test.tsx` addition) — rendering `<App>` at
  `/assemble` via `MemoryRouter` finds the assemble screen by its test id and it
  sits inside `AuthGate` (no token → passcode screen); mirrors the existing
  `App.test.tsx` routing cases. Demonstrates the route is mounted and gated
  (FR: `/assemble` route; Security: auth gating).
- Test: `Assemble.test.tsx` — with `listItems` mocked to `[]`, the empty-wardrobe
  state renders the `state-block empty-state` / `empty-title` block and a
  `btn btn-primary` link to `/add`; with a failing `listItems`, a non-crashing
  error note renders. Demonstrates the empty + list-failure guards (FR:
  empty-wardrobe state, list-failure reuse).
- Test: `Assemble.test.tsx` — the populated screen renders the four labeled drop
  zones (TOP / BOTTOM / SHOES / extras). Demonstrates the mannequin scaffold
  (FR: static SVG mannequin with labeled Slot-keyed zones).
- Test: `Stylist.test.tsx` and `WardrobeGrid.test.tsx` additions — each screen
  renders a link whose `href` is `/assemble`. Demonstrates both entry points
  exist (FR: entry-point links).
- Screenshot: `/assemble` with a populated wardrobe showing the SVG mannequin +
  four labeled zones + source list at a ~390px viewport — mobile-first scaffold;
  no passcode/token visible (Security: sanitized artifacts).
- Screenshot: `/assemble` with an empty wardrobe showing the "add items" message.

#### 1.0 Tasks

- [x] 1.1 [RED] In `App.test.tsx`, add a routing case: `<App>` at `/assemble`
  (token pre-seeded) finds the assemble screen by `data-testid="assemble"`; and
  a gated case with no token asserts the passcode screen shows instead. Run to
  confirm it fails (screen not yet created).
- [x] 1.2 [GREEN] Create `frontend/src/routes/Assemble.tsx` as a minimal
  `<section data-testid="assemble" className="screen">` that fetches items with
  the `listItems()` + `settle()` loading/error pattern (mirror `WardrobeGrid`).
  Register `<Route path="/assemble" element={<Assemble />} />` inside `AuthGate`
  in `App.tsx`. Make 1.1 pass.
- [x] 1.3 [RED] In `Assemble.test.tsx`, add tests for the empty state
  (`listItems → []` renders `state-block empty-state`, `empty-title`, and a
  `btn btn-primary` link to `/add`) and the list-failure state (`listItems`
  rejects → non-crashing error note + retry control).
- [x] 1.4 [GREEN] Implement the loading / list-failure / empty-wardrobe states in
  `Assemble.tsx`, reusing the `WardrobeGrid.tsx` markup and class names. Make 1.3
  pass.
- [x] 1.5 [RED→GREEN] Add an `Assemble.test.tsx` test asserting the four labeled
  drop zones render (query by their visible labels). Create
  `frontend/src/components/Mannequin.tsx` — a static hand-authored SVG line-art
  silhouette with four labeled droppable regions each carrying a stable `Slot`
  identifier (`data-slot` / id) and generous, possibly-overlapping hit-boxes;
  render it in `Assemble.tsx`'s populated state (no drag wiring yet). Make the
  test pass.
- [x] 1.6 [RED→GREEN] Add link tests to `Stylist.test.tsx` and
  `WardrobeGrid.test.tsx` (a link with `href="/assemble"`), then add a
  "Build it yourself" link near the Stylist chat entry point and a link in the
  populated `WardrobeGrid` view. Make both pass.
- [x] 1.7 [REFACTOR] Add mannequin + zone base styles to `index.css` using
  existing tokens (`--paper-sunk`, `--border`, `--radius*`, `--placeholder`);
  keep labels legible and zone hit areas ≥44px. Verify `npm test -- --run` and
  `npm run lint` are green. Capture the two scaffold screenshots (populated +
  empty). Commit (`feat(frontend): /assemble route + mannequin scaffold`).
  Screenshots deferred to manual verification (headless run, no browser) —
  see `21-proofs/21-task-01-proofs.md`.

### [ ] 2.0 Placement model + drag-and-drop with dnd-kit

Introduce a pure, fully-unit-tested placement reducer/helper (in-memory `Slot` →
placed item id(s) state), then wire `@dnd-kit/core` (pointer + touch sensors) so
items drag from the source onto the mannequin zones: default-slot routing via the
reused `slotForCategory`, drop-into-different-zone manual override,
single-occupancy replace for TOP/BOTTOM/SHOES, and the multi-occupancy CARRY/PIECE
tray. Session-only in-memory state; `prefers-reduced-motion` respected. Covers
spec Unit 2 and Success Metric 2.

#### 2.0 Proof Artifact(s)

- Test: `placement.test.ts` (pure model) — covers default-slot routing via
  `slotForCategory`, drop-into-different-zone override, single-occupancy replace
  (TOP/BOTTOM/SHOES; replaced item returns to available), multi-occupancy tray
  accumulation (CARRY/PIECE), and unknown-category → PIECE. **100% branch
  coverage on the placement module** asserted via the Vitest coverage run
  (FR: default routing, override, single/multi occupancy).
- Test: `dndConfig.test.ts` — the exported sensor config declares both a pointer
  and a touch sensor with the intended activation constraints. Demonstrates the
  machine-verifiable half of the pointer+touch requirement (FR: pointer + touch
  sensors).
- Test: `Assemble.test.tsx` — invoking the component's dnd-kit `onDragEnd`
  handler with a synthetic `{active, over}` payload routes an item to the
  expected zone and replaces an occupant (model wiring, not a real drag).
  Demonstrates the view is wired to the model (FR: on-drop assignment).
- CLI: `grep -n "@dnd-kit/core" frontend/package.json` returns the dependency
  line (FR: add `@dnd-kit/core`).
- CLI: `cd frontend && npm test -- --run` exits 0 with the placement + wiring
  suites green.
- Screenshot or screen recording: items dragged onto the mannequin auto-slotting,
  a replace-on-drop on TOP, and multiple items coexisting in the extras tray —
  the interaction end-to-end. This recording is also the **on-device touch proof**
  for the iOS touch-drag watch-item (see 2.6 / assumptions A1.5).

#### 2.0 Tasks

- [ ] 2.1 [SETUP] Add `@dnd-kit/core` to `frontend/package.json` dependencies
  (add `@dnd-kit/modifiers` only if used for snap/constraint). Run `npm install`
  to update `package-lock.json`. Verify `grep -n "@dnd-kit/core" package.json`.
- [ ] 2.2 [RED] Write `frontend/src/lib/placement.test.ts` defining the pure
  model contract and every branch: initial place via `slotForCategory`; explicit
  target-slot override; single-occupancy replace for TOP/BOTTOM/SHOES returning
  the displaced id to available; multi-occupancy accumulation for CARRY/PIECE;
  `removeItem`; `placedIds` selector; unknown/null category → PIECE. Run to
  confirm failure.
- [ ] 2.3 [GREEN] Implement `frontend/src/lib/placement.ts` as a pure module (no
  React) importing `slotForCategory` from `lib/specSheet.ts`. Make 2.2 pass and
  confirm **100% branch coverage** on `placement.ts` via the Vitest coverage run.
- [ ] 2.4 [RED→GREEN] Write `frontend/src/lib/dndConfig.test.ts` asserting a
  pointer + a touch sensor with activation constraints (pointer distance; touch
  delay + tolerance). Implement `frontend/src/lib/dndConfig.ts` exporting the
  sensor descriptors/constants with a comment noting on-device tuning is a
  documented deferred item (A1.5). Make it pass.
- [ ] 2.5 [GREEN] Wire dnd-kit into `Assemble.tsx`: wrap in `<DndContext>` using
  the `dndConfig` sensors; make `AssembleSource` tiles `useDraggable` and the
  `Mannequin` zones `useDroppable` keyed by `Slot`; hold placement state via
  `useState` seeded from `placement.ts`; `onDragEnd` calls `placeItem(state,
  active.id, category, over?.data.slot)`. Render placed items (photo via
  `photoUrl(id)`) in their zones reusing the `drawer-tile` look.
- [ ] 2.6 [RED→GREEN] Add the `Assemble.test.tsx` wiring test: invoke `onDragEnd`
  with a synthetic payload **typed as dnd-kit's `DragEndEvent`** (imported from
  `@dnd-kit/core`) — so a library upgrade that changes the payload shape breaks
  this test at compile time (FLAG-1 hardening) — and assert the item routes to
  the expected zone and replaces an occupant. Guard any drop/return animation
  inside the existing `@media (prefers-reduced-motion: reduce)` block in
  `index.css`.
- [ ] 2.7 [REFACTOR] Verify `npm test -- --run` + `npm run lint` green. Capture
  the interaction screenshot/recording (auto-slot, replace-on-drop, multi-item
  tray) — doubling as the on-device touch proof. Commit
  (`feat(frontend): drag-and-drop placement onto the mannequin`).

### [ ] 3.0 Remove/undo affordance + real wardrobe-drawer source

Let the user iterate without a drag: a ≥44px tap "×" on each placed tile removes
it (item reappears in the source), plus drag-back-to-source removal. Render the
drag source as a **sibling component reusing `WardrobeDrawer`'s `drawer-grid` /
`drawer-tile` / `drawer-search` markup and CSS classes** (per assumptions A1.3 —
do not mutate the display-only `WardrobeDrawer`, do not fork a divergent tile
style), with draggable tiles and already-placed ids excluded. Covers spec Unit 3
and Success Metric 3.

#### 3.0 Proof Artifact(s)

- Test: `AssembleSource.test.tsx` — the source list omits already-placed item ids
  and the search box narrows the visible tiles (reusing the `searchText` filter);
  tiles expose a draggable handle. Demonstrates exclusion + search reuse
  (FR: exclude placed items, drawer search/markup pattern).
- Test: `Assemble.test.tsx` — tapping the "×" on a placed tile removes it from
  placement state and the item id reappears in the source list; the "×" control
  meets the ≥44px touch-target contract (asserted via its class/style).
  Demonstrates tap-remove without a drag (FR: remove affordance).
- Test: `Assemble.test.tsx` — invoking `onDragEnd` with `over` = the source
  droppable removes the placed item. Demonstrates drag-back-to-source wiring
  (FR: drag-back removal).
- Test: `placement.test.ts` — removing a single-occupancy item frees its zone;
  removing one tray item leaves the other tray items intact. Demonstrates
  removal keeps placement rules consistent (FR: consistent remove/re-add).
- Screenshot: a placed tile showing the "×" affordance alongside the source grid
  with that placed item absent — the iterate/undo flow.

#### 3.0 Tasks

- [ ] 3.1 [RED] Write `frontend/src/components/AssembleSource.test.tsx`: given a
  `placedIds` prop the matching tiles are omitted; typing in the search narrows
  the visible tiles; tiles render with the `drawer-grid` / `drawer-tile` /
  `drawer-search` classes and a draggable attribute. Run to confirm failure.
- [ ] 3.2 [GREEN] Create `frontend/src/components/AssembleSource.tsx` — a sibling
  of `WardrobeDrawer` reusing its CSS classes + `searchText`, adding
  `useDraggable` tiles and a `placedIds` exclusion filter. Do **not** modify
  `WardrobeDrawer`. Use it as the source list in `Assemble.tsx`. Make 3.1 pass.
- [ ] 3.3 [RED→GREEN] Add removal tests to `placement.test.ts` (free-single-zone;
  keep-other-tray-items) if not already covered by 2.2, and ensure the
  `removeItem` branches stay at 100%. Make green.
- [ ] 3.4 [RED] Add an `Assemble.test.tsx` test: a placed tile renders a "×"
  remove control; tapping it removes the item and the id reappears in the source
  (excluded → included); the control meets the ≥44px contract. Add the
  drag-back wiring test (`onDragEnd` with `over` = source droppable removes the
  item). Run to confirm failure.
- [ ] 3.5 [GREEN] Render a ≥44px "×" affordance on each placed tile in
  `Assemble.tsx` calling `removeItem`; wire drag-back-to-source removal in
  `onDragEnd`. Add placed-tile + "×" styles to `index.css` reusing tokens. Make
  3.4 pass.
- [ ] 3.6 [REFACTOR] Verify `npm test -- --run` + `npm run lint` green. Capture
  the placed-tile "×" + source-exclusion screenshot. Commit
  (`feat(frontend): remove/undo affordance + wardrobe drag source`).

### [ ] 4.0 "Wear today" fan-out to `markWorn`

Add a "Wear today" action on the assembled set that fans out to the existing
`markWorn(id)` (`POST /api/items/:id/worn`) once per placed item id, reusing the
exact `Promise.allSettled(...)` shape and `idle | logging | logged | error`
lifecycle from `Stylist.tsx` / `OutfitResult.tsx`. Disabled when nothing is
placed; locks to "Logged ✓" on success; retryable on any per-item failure. No new
backend endpoint or persisted entity. Covers spec Unit 4 and Success Metrics 4 & 6.

#### 4.0 Proof Artifact(s)

- Test: `Assemble.test.tsx` — with `markWorn` mocked, "Wear today" on an
  assembled set of N placed items calls `markWorn` exactly N times (once per
  placed item id) and locks the control to "Logged ✓" on success. Demonstrates
  the fan-out + success affordance (FR: markWorn fan-out, logged lock).
- Test: `Assemble.test.tsx` — a rejected `markWorn` (one fan-out promise)
  surfaces the retryable error state; the action is disabled/withheld when no
  items are placed. Demonstrates the edge paths (FR: error retry, disabled-empty).
- CLI: `grep -rn "markWorn" frontend/src/routes/Assemble.tsx` shows the reused
  import (no new endpoint) and `git diff --stat src/` shows no backend Java
  changes — demonstrates "no new backend surface" (Success Metric 6).
- Screenshot: an assembled look after "Wear today" showing the locked "Logged ✓"
  state — the end-to-end wear-log.

#### 4.0 Tasks

- [ ] 4.1 [RED] Add an `Assemble.test.tsx` test (mock `markWorn`): "Wear today"
  on N placed items calls `markWorn` exactly N times, once per placed id, and the
  control locks to "Logged ✓" on success. Run to confirm failure.
- [ ] 4.2 [RED] Add tests: one rejected `markWorn` surfaces the retryable
  `banner banner-error` + retry state; the "Wear today" action is disabled /
  withheld when `placedIds.length === 0`. Run to confirm failure.
- [ ] 4.3 [GREEN] Implement the "Wear today" action in `Assemble.tsx` reusing the
  exact `Promise.allSettled(placedIds.map(markWorn))` + `idle|logging|logged|
  error` lifecycle from `Stylist.tsx`, and the `btn btn-primary` / `btn
  btn-logged` / `banner banner-error` affordances from `OutfitResult.tsx`.
  Disable when nothing is placed. Make 4.1 + 4.2 pass. (Do **not** add a
  save/favorite affordance — saving looks is a non-goal.)
- [ ] 4.4 [REFACTOR] Verify `npm test -- --run` + `npm run lint` green; confirm
  `git diff --stat src/` shows no backend Java changes and `grep -rn "markWorn"
  frontend/src/routes/Assemble.tsx` shows reuse. Capture the "Logged ✓"
  screenshot. Commit (`feat(frontend): wear-today fan-out on assembled look`).
