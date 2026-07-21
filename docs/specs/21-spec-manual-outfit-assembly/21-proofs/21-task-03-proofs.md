# Task 03 Proofs - Remove/undo affordance + real wardrobe-drawer source

## Task Summary

This task lets a user iterate on a manually-assembled look without a drag
being the only tool: a ≥44px tap "×" on any placed tile removes it from the
mannequin instantly, and dragging a placed tile back onto the wardrobe panel
does the same thing. It also replaces the task-2.5 inline placeholder source
list with the real `AssembleSource` component — a sibling of `WardrobeDrawer`
that reuses its `drawer-grid` / `drawer-tile` / `drawer-search` markup and
`searchText` filter, adding draggable tiles and a `placedIds` exclusion filter
so an item can never appear both on the mannequin and in the source at once.
`WardrobeDrawer.tsx` itself is untouched, per assumption A1.3.

## What This Task Proves

- `AssembleSource.tsx` is a genuine sibling component, not a fork: it imports
  nothing from `WardrobeDrawer.tsx` but reuses the exact same CSS class names
  (`drawer-body` / `drawer-title` / `drawer-search*` / `drawer-grid` /
  `drawer-tile`) and an equivalent `searchText` matcher, so tiles look and
  filter identically wherever they appear.
- Already-placed item ids are excluded from the source list (`placedIds` prop
  → filtered before render), and the search box narrows the visible tiles —
  both proven directly against the component, no mocked network calls needed
  since `Assemble.tsx` (which already owns the `listItems()` fetch) passes
  `items` down as a prop instead of `AssembleSource` re-fetching.
- Each placed tile renders a ≥44px "×" (`aria-label="Remove item"`, inline
  `minWidth`/`minHeight: 44px` so the contract is testable without a real
  stylesheet in jsdom) that calls the placement model's `removeItem` on tap —
  no drag required — and the removed item reappears in the source list on the
  very next render.
- `onDragEnd` also treats a drop onto `AssembleSource`'s `SOURCE_DROPPABLE_ID`
  droppable as "remove" rather than "place" — the drag-back-to-source path —
  proven by invoking the component's real handler with a synthetic
  `DragEndEvent` whose `over.id` is that sentinel.
- `placement.ts`'s removal branches (free a single-occupancy zone; leave other
  tray items intact; no-op on a never-placed id) were already exercised by
  task 2.2's tests and are reconfirmed at **100% branch coverage** with no new
  test needed.
- The two new behavioral tests were verified RED-then-GREEN: the
  implementation was temporarily stripped, the suite failed in exactly the
  two new tests (all 6 pre-existing tests stayed green), then the
  implementation was restored and the whole suite passed again.

## Evidence Summary

- `frontend/src/components/AssembleSource.test.tsx` — **4/4 tests pass**:
  renders every unplaced item, excludes placed ids, search narrows the
  visible tiles, and tiles carry `drawer-tile` + a real dnd-kit
  `aria-roledescription="draggable"` attribute.
- `frontend/src/routes/Assemble.test.tsx` — **8/8 tests pass** (6 carried over
  from task 2.0 unchanged after the `AssembleSource` swap + 2 new: tap-"×"
  remove, drag-back-to-source remove).
- `frontend/src/lib/placement.test.ts` — **18/18 tests pass**, coverage run
  reconfirms **100% statements/branches/functions/lines** on `placement.ts`
  (no new test required — already covered by task 2.2).
- Full frontend suite: **256/256 tests pass** across **27 files** — up from
  250/26 after task 2.0 (6 new: 4 `AssembleSource` + 2 `Assemble` remove/undo).
- `npm run lint` (ESLint) and `npx tsc -b` (TypeScript project build) both
  exit clean with no output.
- `git diff --stat` / `git status --short` show only frontend files touched
  (no backend Java/Gradle changes) — see the last artifact below.
- Screenshot is **DEFERRED to manual verification** (see note at the end) —
  this is a headless implementation run with no browser available to capture
  it honestly.

## Artifact: RED — the two new tests fail before the "×" / drag-back code exists

**What it proves:** Strict TDD was followed for this task's behavioral tests,
not just written-and-immediately-passing.

**Why it matters:** Confirms the tests actually exercise the new behavior
(tap-remove, drag-back removal) rather than passing vacuously.

**Command (implementation temporarily stripped: the "×" button removed from
`PlacedTile`, the `SOURCE_DROPPABLE_ID` branch removed from `onDragEnd`):**

```bash
cd frontend && npx vitest run src/routes/Assemble.test.tsx
```

**Result summary:** Exactly the 2 new tests failed — one on
`getByRole('button', { name: /remove item/i })` finding nothing (no "×" button
rendered), the other on the placed `<img>` still being present after a
synthetic drag-back event (the removal branch didn't exist yet). All 6
pre-existing wiring tests kept passing, confirming the strip didn't touch
unrelated behavior.

```
 FAIL  src/routes/Assemble.test.tsx > Assemble remove/undo affordance > removes a placed item via its tap "×" control and it reappears in the source list
Error: Unable to find an accessible element with the role "button" and name `/remove item/i`

 FAIL  src/routes/Assemble.test.tsx > Assemble remove/undo affordance > removes a placed item when onDragEnd reports it dropped back onto the source droppable
Error: expect(element).not.toBeInTheDocument()
expected document not to contain element, found <img alt="shirt" ... src="/api/items/shirt-1/photo" /> instead

 Test Files  1 failed (1)
      Tests  2 failed | 6 passed (8)
```

The implementation was restored immediately after this run (see the GREEN
artifact below) — no code was left in the stripped state.

## Artifact: GREEN — full suite after the "×" + drag-back implementation

**What it proves:** Tap-remove and drag-back-to-source removal both work, and
nothing else in the `/assemble` screen or the rest of the app regressed.

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** All 256 tests pass across 27 files (250/26 after task
2.0 → +6 for this task: 4 `AssembleSource` + 2 `Assemble` remove/undo).

```
 ✓ src/components/AssembleSource.test.tsx (4 tests)
 ✓ src/routes/Assemble.test.tsx (8 tests)
 ...
 Test Files  27 passed (27)
      Tests  256 passed (256)
```

## Artifact: `AssembleSource` — exclusion, search, and real draggable tiles

**What it proves:** The source list is the real wardrobe-drawer-style
component the spec's Unit 3 FRs require, not the task-2.5 placeholder: it
excludes placed ids, narrows on search, and its tiles are genuinely
`useDraggable` (not mocked in this component-level test — `aria-roledescription`
comes straight from real `@dnd-kit/core`).

**Why it matters:** This is the literal Unit 3 proof artifact — "the source
list omits already-placed item ids and the search filter narrows the tiles."

**Command:**

```bash
cd frontend && npx vitest run src/components/AssembleSource.test.tsx
```

**Result summary:** All 4 tests pass.

```
 ✓ src/components/AssembleSource.test.tsx (4 tests) 64ms
   ✓ renders every item as a drawer tile when nothing is placed
   ✓ omits already-placed item ids from the visible source list
   ✓ narrows the visible tiles to the search text, reusing the drawer search pattern
   ✓ renders tiles with the drawer-tile class and a dnd-kit draggable attribute

 Test Files  1 passed (1)
      Tests  4 passed (4)
```

## Artifact: `placement.ts` removal branches — still 100% branch coverage

**What it proves:** Removing a single-occupancy item frees its zone, and
removing one tray item leaves the other tray items intact — both already
exercised by task 2.2's tests, reconfirmed here with the coverage thresholds
pinned to 100 on every dimension so the run would fail non-zero on any
uncovered branch.

**Why it matters:** `docs/TESTING.md` requires 100% branch coverage on this
module; this proves task 3's removal requirement introduced no gap.

**Command:**

```bash
cd frontend && npx vitest run src/lib/placement.test.ts \
  --coverage --coverage.include='src/lib/placement.ts' \
  --coverage.thresholds.branches=100 --coverage.thresholds.lines=100 \
  --coverage.thresholds.functions=100 --coverage.thresholds.statements=100
```

**Result summary:** All 18 tests pass; coverage is 100/100/100/100 with no
uncovered lines.

```
 ✓ src/lib/placement.test.ts (18 tests)

 % Coverage report from v8
--------------|---------|----------|---------|---------|-------------------
File          | % Stmts | % Branch | % Funcs | % Lines | Uncovered Line #s
--------------|---------|----------|---------|---------|-------------------
All files     |     100 |      100 |     100 |     100 |
 placement.ts |     100 |      100 |     100 |     100 |
--------------|---------|----------|---------|---------|-------------------
```

## Artifact: Quality gates — lint, typecheck, and "no backend changes"

**What it proves:** The task's code quality gates pass cleanly, and this
frontend-only task introduced no backend Java/Gradle changes.

**Why it matters:** Pre-commit / CI parity, and confirms scope discipline
(no accidental backend edits from a frontend-only task).

**Commands:**

```bash
cd frontend && npm run lint
cd frontend && npx tsc -b
git status --short
```

**Result summary:** ESLint and the TypeScript project build both exit clean
with no output. `git status --short` shows only frontend + spec-doc files
touched:

```
 M docs/specs/21-spec-manual-outfit-assembly/21-tasks-manual-outfit-assembly.md
 M frontend/src/index.css
 M frontend/src/routes/Assemble.test.tsx
 M frontend/src/routes/Assemble.tsx
?? frontend/src/components/AssembleSource.test.tsx
?? frontend/src/components/AssembleSource.tsx
```

## Note: Screenshot deferred to manual verification

The spec's Unit 3 screenshot artifact ("a placed tile showing the '×'
affordance alongside the source grid with that placed item absent") is
**deferred to manual verification** — this is a headless implementation run
with no browser available to capture it honestly, consistent with how tasks
1.0 and 2.0 handled the same constraint (see `21-task-01-proofs.md` /
`21-task-02-proofs.md`). The automated evidence above — exclusion, search,
tap-remove, and drag-back wiring, all RED-then-GREEN verified — is the
machine-verifiable stand-in for this task.

## Reviewer Conclusion

The `/assemble` screen now has a complete iterate/undo loop backed by the real
wardrobe-drawer source: a tap "×" or a drag back onto the source panel both
remove a placed item and it reappears in the (now-search-and-exclusion-aware)
source list, all routed through the same already-100%-branch-covered
`placement.ts` removal logic. The full frontend suite, lint, and TypeScript
build all stay green, and the diff touches only frontend files.
