# Task 02 Proofs - Placement model + drag-and-drop with dnd-kit

## Task Summary

This task adds the core `/assemble` interaction: a pure, fully-branch-covered
placement model (`lib/placement.ts`) and `@dnd-kit/core` wiring so items drag
from the wardrobe source onto the mannequin's `Slot`-keyed zones. It covers
default-zone routing via the existing `slotForCategory`, a manual
drop-into-different-zone override, single-occupancy replace for
`TOP`/`BOTTOM`/`SHOES`, and the multi-occupancy `CARRY`/`PIECE` extras tray.
Placement is session-only in-memory state; no backend or Claude call is
introduced.

## What This Task Proves

- The placement rules live in a pure, React-free module
  (`frontend/src/lib/placement.ts`) that reuses `slotForCategory` unchanged —
  no second slot-mapping function — and is exercised directly by unit tests
  rather than by simulating a drag in jsdom (which dnd-kit's `PointerSensor`
  cannot do reliably; see the spec's Technical Considerations).
- **100% branch coverage** on `placement.ts`, confirmed via the Vitest
  coverage run with explicit `100` thresholds on branches/lines/functions/
  statements — not just an eyeballed report.
- `@dnd-kit/core` is wired into `Assemble.tsx`: a real `<DndContext>` with a
  pointer + touch sensor (`lib/dndConfig.ts`), draggable source tiles, and
  droppable mannequin zones keyed by `Slot`.
- The component's actual `onDragEnd` handler — not a stand-in helper — routes
  a dropped item to its target zone and replaces the prior single-occupancy
  occupant, verified by invoking it directly with a payload typed as
  dnd-kit's real `DragEndEvent` (the approved FLAG-1 hardening from the
  planning audit).
- The dependency was added correctly (`grep` evidence below) and the full
  frontend suite, ESLint, and TypeScript all stay green — no regression to
  task 1.0's work.

## Evidence Summary

- `frontend/src/lib/placement.test.ts` — **18/18 tests pass**, and Vitest's
  coverage run reports **100% statements/branches/functions/lines** on
  `placement.ts` with thresholds enforced (not just observed).
- `frontend/src/lib/dndConfig.test.ts` — **4/4 tests pass**, proving a pointer
  sensor (small activation distance) and a touch sensor (hold delay +
  movement tolerance) are both configured.
- `frontend/src/routes/Assemble.test.tsx` — **6/6 tests pass** (3 carried over
  from task 1.0 + 3 new wiring tests): routing + replace-on-drop, tray
  accumulation, and a no-op on an invalid drop target.
- Full frontend suite: **250/250 tests pass** across **26 files** — up from
  225/24 after task 1.0 (25 new tests: 18 placement, 4 dndConfig, 3 Assemble
  wiring).
- `npm run lint` (ESLint) and `npx tsc -b` (TypeScript project build) both
  exit clean with no output.
- `grep -n "@dnd-kit/core" frontend/package.json` returns the dependency line.
- Screenshot/recording is **DEFERRED to manual verification** (see note
  below) — this is a headless implementation run with no browser or device
  available to capture it honestly.

## Artifact: `@dnd-kit/core` added as a dependency

**What it proves:** The library required for pointer + touch drag-and-drop is
declared in `frontend/package.json` (FR: add `@dnd-kit/core`).

**Why it matters:** This is the spec's literal Unit 2 CLI proof artifact.
`@dnd-kit/modifiers` was **not** added — nothing in this task needed a
snap/constraint modifier, so it would have been an unused dependency.

**Command:**

```bash
grep -n "@dnd-kit/core" frontend/package.json
```

**Result summary:** The dependency is present at `^6.3.1` (dnd-kit's stable
6.x line, matching the spec's verified Technical Considerations).

```
14:    "@dnd-kit/core": "^6.3.1",
```

## Artifact: Placement model — 100% branch coverage on the meaningful logic

**What it proves:** Every rule the spec requires — default routing via
`slotForCategory`, the drop-into-different-zone override, single-occupancy
replace (with the displaced item implicitly returned to "available"),
multi-occupancy tray accumulation for both `CARRY` and `PIECE`, and the
null/unknown-category degrade to `PIECE` — is exercised, and the coverage run
proves there is no untested branch left in the module.

**Why it matters:** `docs/TESTING.md` requires 100% branch coverage on the
"meaningful logic" unit for this kind of feature; this is the module that
owns every placement decision the UI will ever make, so an untested branch
here would be a silent correctness gap (e.g. an item landing in two zones at
once, or a replace failing to free the old occupant).

**Command:**

```bash
cd frontend && npx vitest run src/lib/placement.test.ts \
  --coverage --coverage.include='src/lib/placement.ts' \
  --coverage.thresholds.branches=100 --coverage.thresholds.lines=100 \
  --coverage.thresholds.functions=100 --coverage.thresholds.statements=100
```

**Result summary:** All 18 tests pass and the coverage thresholds (set to
100 on every dimension) are satisfied — the run would have failed non-zero
if any branch were uncovered.

```
 ✓ src/lib/placement.test.ts (18 tests) 3ms

 % Coverage report from v8
--------------|---------|----------|---------|---------|-------------------
File          | % Stmts | % Branch | % Funcs | % Lines | Uncovered Line #s
--------------|---------|----------|---------|---------|-------------------
All files     |     100 |      100 |     100 |     100 |
 placement.ts |     100 |      100 |     100 |     100 |
--------------|---------|----------|---------|---------|-------------------
```

The test file covers every required scenario as its own `describe` block:
default routing (recognized category, plus null/undefined/unrecognized →
`PIECE`), the manual override (including overriding an unrecognized category
into a body-region slot), single-occupancy replace for all three of
`TOP`/`BOTTOM`/`SHOES` (via `it.each`) plus a same-id-replace no-op,
multi-occupancy accumulation for both `CARRY` and `PIECE`, moving an
already-placed item between slots, `removeItem` (including the no-op-if-
never-placed edge case and "removing one tray item leaves the others
intact"), and the `placedIds` selector.

## Artifact: Sensor config — pointer + touch, both machine-verified

**What it proves:** `lib/dndConfig.ts` exports a descriptor pairing
`PointerSensor` with a small activation distance and `TouchSensor` with a
hold delay + movement tolerance, satisfying the FR's "wire dnd-kit with both
a pointer sensor and a touch sensor" requirement in a way a test can assert
directly (not just by eyeballing `Assemble.tsx`).

**Why it matters:** The planning audit's one advisory flag was that
touch-drag fidelity can't be verified in jsdom; extracting the sensor config
to its own module means the *configuration* half of that requirement (as
opposed to on-device feel) is still machine-verified.

**Command:**

```bash
cd frontend && npm test -- --run src/lib/dndConfig.test.ts
```

**Result summary:** All 4 tests pass, including one that pins the pointer and
touch activation constants as the single source of truth the sensor
descriptors consume.

```
 ✓ src/lib/dndConfig.test.ts (4 tests) 2ms
```

`lib/dndConfig.ts` documents the deferred on-device tuning inline (assumption
A1.5):

```ts
/**
 * ASSUMPTION A1.5 (deferred, non-blocking — see
 * docs/specs/21-spec-manual-outfit-assembly/21-assumptions-manual-outfit-assembly.md
 * and the spec's Open Question 4): this SDD run has no real iOS device to
 * tune against. The constants below are sensible defaults ... On-device
 * tuning/validation of these thresholds is a documented, deferred manual
 * step (the on-device touch proof artifact), not a hard blocker for this
 * issue.
 */
export const POINTER_ACTIVATION_CONSTRAINT: PointerActivationConstraint = { distance: 4 }
export const TOUCH_ACTIVATION_CONSTRAINT: PointerActivationConstraint = { delay: 150, tolerance: 8 }
```

## Artifact: Component wiring — a synthetic, dnd-kit-typed drop routes and replaces

**What it proves:** `Assemble.tsx`'s actual `onDragEnd` callback — the one
passed to the real `<DndContext>` — routes a dropped item to the zone named
in the drop event and replaces the prior single-occupancy occupant, and the
multi-occupancy tray accumulates rather than replaces. This is "the view is
wired to the model," not just "the model is correct in isolation."

**Why it matters:** This is the FLAG-1 hardening the planning audit approved:
the synthetic payload is typed as dnd-kit's own `DragEndEvent` (imported from
`@dnd-kit/core`), so if a future dnd-kit upgrade changes that event's shape,
this test fails to *compile* rather than silently passing a stale, hand-rolled
`{active, over}` object. jsdom cannot simulate a real pointer/touch drag (see
the spec's Technical Considerations), so `@dnd-kit/core` is mocked in the test
file only to capture the component's real `onDragEnd` prop (via `vi.hoisted`)
— the handler that runs is the component's own code, not a test double.

**Command:**

```bash
cd frontend && npm test -- --run src/routes/Assemble.test.tsx
```

**Result summary:** All 6 tests pass — the 3 scaffold tests from task 1.0
plus 3 new wiring tests.

```
 ✓ src/routes/Assemble.test.tsx (6 tests) 83ms
```

The synthetic event is built from the real type, not a loose object literal:

```ts
import type { DndContextProps, DragEndEvent } from '@dnd-kit/core'

function dragEndEvent(activeId: string, category: string | null, overSlot: Slot | null): DragEndEvent {
  return {
    activatorEvent: new Event('pointerup'),
    active: {
      id: activeId,
      data: { current: { category } },
      rect: { current: { initial: null, translated: null } },
    },
    collisions: null,
    delta: { x: 0, y: 0 },
    over:
      overSlot === null
        ? null
        : {
            id: overSlot,
            data: { current: { slot: overSlot } },
            disabled: false,
            rect: { width: 0, height: 0, top: 0, left: 0, right: 0, bottom: 0 },
          },
  }
}
```

Routing + replace:

```ts
it('routes a dropped item to its target zone and replaces the prior occupant', async () => {
  listItemsMock.mockResolvedValue([item('shirt-1', 'shirt'), item('shirt-2', 'shirt')])
  renderAssemble()
  await screen.findByText(/^top$/i)

  act(() => { dragEndRef.current?.(dragEndEvent('shirt-1', 'shirt', 'TOP')) })
  const topZone = document.querySelector('[data-slot="TOP"]') as HTMLElement
  expect(within(topZone).getByRole('img')).toHaveAttribute('src', '/api/items/shirt-1/photo')

  act(() => { dragEndRef.current?.(dragEndEvent('shirt-2', 'shirt', 'TOP')) })
  expect(within(topZone).getByRole('img')).toHaveAttribute('src', '/api/items/shirt-2/photo')
})
```

Two additional tests beyond the spec's minimum: multi-occupancy tray
accumulation (`bag-1` + `hat-1` both land in the `CARRY` zone, 2 images) and a
no-op when a drag ends with `over: null` (no valid drop target) — proving the
handler doesn't fall through to a default placement when the drag was
effectively cancelled.

## Artifact: Full frontend suite stays green (no regression)

**What it proves:** Adding the placement model, sensor config, and dnd-kit
wiring did not break any existing screen, API client, or lib module —
including task 1.0's route/scaffold tests, which still pass unmodified
against the now-real `<DndContext>`/`useDraggable`/`useDroppable`.

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** 250/250 tests pass across 26 files (up from 225/24 after
task 1.0 — 25 new tests: 18 `placement.test.ts`, 4 `dndConfig.test.ts`, 3 new
`Assemble.test.tsx` wiring cases).

```
 Test Files  26 passed (26)
      Tests  250 passed (250)
```

## Artifact: Quality gates — lint and typecheck

**What it proves:** The new code follows the repo's ESLint config (including
`react-hooks/rules-of-hooks` — relevant here since `Mannequin.tsx` needed a
`MannequinZone` sub-component specifically so each zone's `useDroppable` call
is unconditional rather than nested in a `.map()` callback) and compiles
under strict TypeScript with no errors, including the dnd-kit types pulled in
by the FLAG-1 synthetic-event test.

**Command:**

```bash
cd frontend && npm run lint
cd frontend && npx tsc -b
```

**Result summary:** Both commands exit with no output (clean pass).

## Artifact: No backend surface touched

**What it proves:** This task is frontend-only, consistent with the spec's
"no backend changes" requirement.

**Command:**

```bash
git diff --stat
```

**Result summary:** Only `frontend/**`, the root `.gitignore`, and the spec's
own task-list doc changed — no `src/main/java/**` path appears.

```
 .gitignore                                                                    |   1 +
 docs/specs/21-spec-manual-outfit-assembly/21-tasks-manual-outfit-assembly.md  |  42 +-
 frontend/package-lock.json                                                   | 621 ++++++++++++++++++++-
 frontend/package.json                                                        |   2 +
 frontend/src/components/Mannequin.tsx                                        |  79 ++-
 frontend/src/index.css                                                       |  35 ++
 frontend/src/routes/Assemble.test.tsx                                        | 121 +++-
 frontend/src/routes/Assemble.tsx                                             | 108 +++-
 8 files changed, 976 insertions(+), 33 deletions(-)
```

(New untracked files not shown by `--stat`: `frontend/src/lib/placement.ts`,
`frontend/src/lib/placement.test.ts`, `frontend/src/lib/dndConfig.ts`,
`frontend/src/lib/dndConfig.test.ts` — all frontend-only, confirmed via
`git status --short`. The root `.gitignore` gained a `frontend/coverage/`
rule since this task is the first to run the Vitest coverage reporter in this
repo — generated coverage output must not be committed.)

## Screenshot/recording artifact — DEFERRED to manual verification

The spec's Unit 2 proof artifacts call for a screenshot or screen recording
of items dragging onto the mannequin (auto-slotting, a replace-on-drop, and
multiple items coexisting in the extras tray), which per the task list also
doubles as the on-device touch proof for assumption A1.5. **This
implementation run is headless — there is no browser or physical device
available to capture that honestly.** Rather than fabricate an image or
recording, this artifact is explicitly deferred to manual, on-device
verification, consistent with how task 1.0 deferred its screenshots.

The interaction this recording would demonstrate is instead covered by
machine-verifiable evidence above: the placement unit tests prove every
routing/replace/accumulate rule in isolation, and the `Assemble.test.tsx`
wiring tests prove the component's real `onDragEnd` handler produces the
correct on-screen result (the right image in the right zone) for a routed
drop, a replace, and a multi-item tray. A reviewer can additionally run
`npm run dev` locally, navigate to `/assemble`, and drag real wardrobe tiles
onto the mannequin to see the interaction directly — and, per assumption
A1.5, should validate the pointer/touch activation thresholds
(`lib/dndConfig.ts`) on a real touchscreen device before treating that one
acceptance criterion as closed.

## Reviewer Conclusion

The manual-assembly screen now has a real placement engine and real
drag-and-drop wiring, not a mockup: `lib/placement.ts` is a pure, 100%
branch-covered module that owns every routing/replace/accumulate rule, and
`Assemble.tsx` wires `@dnd-kit/core`'s pointer + touch sensors so a genuine
drop calls into that model and re-renders the correct zone contents — proven
by invoking the component's actual `onDragEnd` handler with a payload typed
against dnd-kit's own `DragEndEvent` (so a future library upgrade breaks this
test at compile time rather than silently). The full 250-test frontend
suite, ESLint, and TypeScript all stay green, `@dnd-kit/core` is the only
new runtime dependency, and no backend file was touched. The only artifact
not machine-verified in this run is the interaction screenshot/recording
(doubling as the on-device touch proof), which is explicitly flagged above
as deferred rather than faked.
