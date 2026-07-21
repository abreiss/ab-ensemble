# Task 04 Proofs - "Wear today" fan-out to `markWorn`

## Task Summary

This is the fourth and final demoable unit of the manual outfit-assembly
feature (issue #20). It closes the loop between a hand-assembled look and
wear-history: a "Wear today" action on `/assemble` fans out to the existing
per-item `markWorn(id)` (`POST /api/items/:id/worn`) once per placed item id,
reusing the exact `Promise.allSettled(...)` shape and the
`idle | logging | logged | error` lifecycle already proven out on the AI
stylist path (`Stylist.tsx` / `OutfitResult.tsx`). No new backend endpoint, no
new persisted "outfit" entity — the screen only re-uses the item write that
already exists.

## What This Task Proves

- "Wear today" calls `markWorn` **exactly once per currently-placed item id**
  (not once per zone, not once total) — proven by placing two items in
  different zones (a single-occupancy `TOP` slot and the multi-occupancy
  `CARRY` tray) and asserting `markWorn` was called with each id exactly once.
- On success the control **locks** to a disabled "Logged ✓" button
  (`btn btn-logged`), mirroring `OutfitResult.tsx`'s lock exactly.
- On any per-item rejection, a retryable `banner banner-error` (`role="alert"`)
  appears and the primary action **stays clickable** (not locked to
  "Logged ✓"), matching `Stylist.tsx`'s "keep the look, offer a retry" pattern.
- The action is **disabled** when nothing is placed (`placedIds.length === 0`)
  — it never fans out to zero calls silently.
- No new backend surface was introduced: `markWorn` is the same function
  already imported from `api/items.ts` elsewhere in the app; `git diff --stat`
  touches no Java/Gradle files.
- No save/favorite affordance was added — the existing "Save look" heart
  button lives only in `OutfitResult.tsx` (the AI-picked-look screen) and was
  deliberately not copied here, per the spec's non-goal ("no saving / naming /
  favoriting assembled looks").

## Evidence Summary

- `frontend/src/routes/Assemble.test.tsx` — **11/11 tests pass** (8 carried
  over from tasks 1.0–3.0 unchanged + 3 new: success fan-out + lock, rejected
  fan-out + retryable error, disabled-when-empty).
- Full frontend suite: **259/259 tests pass** across **27 files** — up from
  256/27 after task 3.0 (+3 for this task).
- `npm run lint` (ESLint) and `npx tsc -b` (TypeScript project build) both
  exit clean with no output.
- `git diff --stat` / `grep -rn "markWorn"` confirm the "no new backend
  surface, reused import" requirement.
- Screenshot is **DEFERRED to manual verification** (see note at the end) —
  this is a headless implementation run with no browser available to capture
  it honestly, same as tasks 1.0–3.0.

## Artifact: RED — the three new tests fail before the "Wear today" control exists

**What it proves:** Strict TDD was followed — the fan-out, lock, error, and
disabled-when-empty behaviors were all written as failing tests first.

**Why it matters:** Confirms the tests actually exercise new behavior rather
than passing vacuously once the control exists.

**Command (run before any `Assemble.tsx` production-code change for this
task):**

```bash
cd frontend && npx vitest run src/routes/Assemble.test.tsx
```

**Result summary:** Exactly the 3 new tests failed — each on
`getByRole('button', { name: /wear today/i })` finding nothing, since no such
control existed yet. All 8 pre-existing tests (route/state, drag wiring,
remove/undo) kept passing, confirming the change was additive.

```
 FAIL  src/routes/Assemble.test.tsx > Assemble wear-today fan-out > logs every placed item via markWorn exactly once and locks to "Logged ✓" on success
 FAIL  src/routes/Assemble.test.tsx > Assemble wear-today fan-out > surfaces a retryable error banner when a per-item markWorn write is rejected
 FAIL  src/routes/Assemble.test.tsx > Assemble wear-today fan-out > disables the "Wear today" action when nothing is placed
Error: Unable to find an accessible element with the role "button" and name `/wear today/i`

 Test Files  1 failed (1)
      Tests  3 failed | 8 passed (11)
```

## Artifact: GREEN — the "Wear today" fan-out, lock, error, and disabled states

**What it proves:** All three behaviors work end-to-end against a mocked
`markWorn`, and nothing else in `/assemble` regressed.

**Command:**

```bash
cd frontend && npx vitest run src/routes/Assemble.test.tsx --reporter=verbose
```

**Result summary:** All 11 tests pass, including the 3 new wear-today cases.

```
 ✓ src/routes/Assemble.test.tsx > Assemble route > shows an empty-wardrobe state inviting a first add when there are no items
 ✓ src/routes/Assemble.test.tsx > Assemble route > shows a non-crashing error state with a retry that re-fetches on list failure
 ✓ src/routes/Assemble.test.tsx > Assemble route > renders the mannequin with four labeled, Slot-keyed drop zones when items exist
 ✓ src/routes/Assemble.test.tsx > Assemble drag-and-drop wiring > routes a dropped item to its target zone and replaces the prior occupant
 ✓ src/routes/Assemble.test.tsx > Assemble drag-and-drop wiring > accumulates multiple items in the CARRY/PIECE extras tray instead of replacing
 ✓ src/routes/Assemble.test.tsx > Assemble drag-and-drop wiring > ignores a drag that ends without a valid drop target
 ✓ src/routes/Assemble.test.tsx > Assemble remove/undo affordance > removes a placed item via its tap "×" control and it reappears in the source list
 ✓ src/routes/Assemble.test.tsx > Assemble remove/undo affordance > removes a placed item when onDragEnd reports it dropped back onto the source droppable
 ✓ src/routes/Assemble.test.tsx > Assemble wear-today fan-out > logs every placed item via markWorn exactly once and locks to "Logged ✓" on success
 ✓ src/routes/Assemble.test.tsx > Assemble wear-today fan-out > surfaces a retryable error banner when a per-item markWorn write is rejected
 ✓ src/routes/Assemble.test.tsx > Assemble wear-today fan-out > disables the "Wear today" action when nothing is placed

 Test Files  1 passed (1)
      Tests  11 passed (11)
```

The three new tests, in detail:

1. **Success fan-out + lock** — places `shirt-1` in `TOP` and `bag-1` in the
   `CARRY` tray (two different occupancy modes, on purpose), clicks
   "Wear today", and asserts `markWorn` was called exactly twice — once with
   `'shirt-1'`, once with `'bag-1'` — then that the button becomes a disabled
   "Logged ✓".
2. **Rejected fan-out + retry** — `markWorn` mocked to reject, one item
   placed, clicks "Wear today", asserts a `role="alert"` banner reading
   "couldn't log" appears and the "Wear today" button is **not** disabled
   (still retryable — it never reached the "Logged ✓" lock).
3. **Disabled when empty** — renders with items available but none placed,
   asserts the "Wear today" button is disabled and `markWorn` was never
   called.

## Artifact: Full frontend suite — no regressions

**What it proves:** The rest of the app (all 26 other test files) is
unaffected by this task's change.

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** All 259 tests pass across 27 files (256/27 after task
3.0 → +3 for this task's new wear-today cases).

```
 Test Files  27 passed (27)
      Tests  259 passed (259)
```

## Artifact: No new backend surface — reused `markWorn` import, no Java diff

**What it proves:** The fan-out reuses the existing `POST /api/items/:id/worn`
client function; no new endpoint, controller, service, or persisted entity was
added on the backend.

**Why it matters:** This is the literal Success Metric 6 proof artifact for
the whole feature ("no new backend surface").

**Commands:**

```bash
grep -rn "markWorn" frontend/src/routes/Assemble.tsx
git diff --stat src/
```

**Result summary:** `markWorn` in `Assemble.tsx` is an import from
`../api/items` (the same client function `Stylist.tsx` already uses via
`api/style.ts`'s re-export) — not a new function definition. `git diff --stat
src/` is empty: there is no top-level backend `src/` directory touched by this
branch (the backend lives under its own Gradle module path, untouched here).

```
frontend/src/routes/Assemble.tsx:8:import { listItems, markWorn, photoUrl } from '../api/items'
frontend/src/routes/Assemble.tsx:171:  // Log the assembled set worn: fans out to the existing per-item `markWorn`
frontend/src/routes/Assemble.tsx:182:    Promise.allSettled(currentlyPlacedIds.map((itemId) => markWorn(itemId))).then((results) => {
```

(`git diff --stat src/` printed nothing — no output is the expected, passing
result.)

## Artifact: Quality gates — lint and typecheck

**What it proves:** The task's code passes the repository's standing quality
gates cleanly.

**Commands:**

```bash
cd frontend && npm run lint
cd frontend && npx tsc -b
```

**Result summary:** ESLint and the TypeScript project build both exit clean
with no output.

## Artifact: Changed files for this task

**What it proves:** The change is scoped to exactly what the task describes —
the route component, its test file, and one small CSS addition for the new
action row's layout — plus the task-file status update.

**Command:**

```bash
git status --short
```

**Result summary:**

```
 M docs/specs/21-spec-manual-outfit-assembly/21-tasks-manual-outfit-assembly.md
 M frontend/src/index.css
 M frontend/src/routes/Assemble.test.tsx
 M frontend/src/routes/Assemble.tsx
```

The `index.css` change is a single new `.assemble-actions` flex-column rule
(spacing only, using existing `--space-2`/`--space-4` tokens) that lays out
the new "Wear today" button + error banner under the mannequin/source panel —
the `btn btn-primary` / `btn btn-logged` / `banner banner-error` classes
themselves are unchanged, pre-existing styles already proven out by
`OutfitResult.tsx`.

## Note: Screenshot deferred to manual verification

The spec's Unit 4 screenshot artifact ("an assembled look after 'Wear today'
showing the locked 'Logged ✓' state") is **deferred to manual verification**
— this is a headless implementation run with no browser available to capture
it honestly, consistent with how tasks 1.0–3.0 handled the same constraint
(see `21-task-01-proofs.md` through `21-task-03-proofs.md`). The automated
evidence above — the fan-out call count, the success lock, the retryable
error banner, and the disabled-when-empty guard, all RED-then-GREEN verified
— is the machine-verifiable stand-in for this task.

## Reviewer Conclusion

The manual outfit-assembly screen now closes the loop with wear-history
exactly the way the AI-picked-look screen does: "Wear today" fans out to the
same per-item `markWorn` endpoint once per placed id, locks to "Logged ✓" on
success, and surfaces a retryable error banner on any per-item failure —
all with zero new backend surface. This completes all four parent tasks
(1.0–4.0) for issue #20 / spec 21; the full frontend suite (259 tests, 27
files), lint, and TypeScript build all stay green.
