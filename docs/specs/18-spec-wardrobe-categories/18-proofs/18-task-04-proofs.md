# Task 04 Proofs - `slotForCategory` map updated for the new taxonomy

## Task Summary

This task closes the last regression window the spec calls out: once vision
and the manual `<select>` (Tasks 1.0/2.0) started emitting canonical taxonomy
values (`Jacket`, `Top`, `Bottom`, `Dress`, `Shoes`, `Jewelry`, `Accessory`,
`Other`) instead of only legacy free text, the outfit-result card's
`slotForCategory` keyword map had gaps — a taxonomy value like `Bottom`,
`Dress`, or `Jewelry` would silently fall through to the generic `PIECE`
label. `CATEGORY_SLOTS` in `frontend/src/lib/specSheet.ts` is now extended
with lower-cased keys for every taxonomy value, keeping the case-insensitive
lookup + `PIECE` fallback and introducing **no new `Slot` value** — Jewelry
deliberately shares `CARRY` with Accessory (assumption A1.8).

## What This Task Proves

- Every one of the eight taxonomy values now resolves to a real slot, not the
  generic `PIECE` fallback: `Jacket`/`Top`/`Dress` → `TOP`, `Bottom` →
  `BOTTOM`, `Shoes` → `SHOES`, `Jewelry`/`Accessory` → `CARRY` (FR 4.1).
- `Other` is intentionally left unkeyed and still degrades to `PIECE`, and
  every pre-existing legacy keyword (`shirt`, `chinos`, `sneakers`, `hat`,
  etc.) plus the null/blank/unrecognized → `PIECE` fallback keep working
  (FR 4.2).
- The `Slot` union (`TOP | BOTTOM | SHOES | CARRY | PIECE`) is unchanged, and
  `placement.ts`/`placement.test.ts` — which reuse `slotForCategory`/`Slot`
  unmodified — are untouched by this task (FR 4.3; Technical
  Considerations' reuse-with-zero-change invariant).
- The test asserting the taxonomy → slot mapping imports `CATEGORIES` from
  `categoryTaxonomy.ts` directly, binding the two lists as a test invariant
  per assumption A2.4 (grouping bucket and card slot stay distinct mappings,
  but the vocabulary is provably in sync).

## Evidence Summary

- A new `it.each` block in `specSheet.test.ts` was run **RED** first (3 of 8
  new cases failed: `Bottom`, `Dress`, `Jewelry` still resolved to `PIECE`),
  then made **GREEN** by the three-key `CATEGORY_SLOTS` addition.
- `specSheet.test.ts` grew from 46 to 49 tests; all pass, and `specSheet.ts`
  reports **100% statement/branch/function/line coverage**.
- The full frontend suite (29 files, 318 tests — up from 315 before this
  task), ESLint, and `tsc -b` are all green.
- `git diff` on `specSheet.ts` shows only additions inside `CATEGORY_SLOTS`
  (plus an explanatory comment) — the `Slot` type is untouched; `placement.ts`
  and `placement.test.ts` have zero diff.

## Artifact: RED — new taxonomy-to-slot assertions fail before the map is extended

**What it proves:** Before `CATEGORY_SLOTS` was extended, three of the eight
new taxonomy-value cases (`Bottom`, `Dress`, `Jewelry`) failed against the
existing keyword map — proving the test exercises real, previously-missing
behavior rather than being vacuously true. (`Jacket`, `Top`, `Shoes`,
`Accessory`, `Other` already passed because their lower-cased legacy keys —
`jacket`, `top`, `shoes`, `accessory` — already existed, and `Other` already
fell through to the `PIECE` fallback.)

**Why it matters:** Confirms the RED step of the RED→GREEN→REFACTOR cycle
actually ran, per the repo's strict-TDD mandate (AGENTS.md).

**Command:**

```bash
cd frontend && npx vitest run src/lib/specSheet.test.ts
```

**Result summary:** 3 of 49 tests failed, all in the new taxonomy-value
block, each showing the pre-extension gap (`PIECE` instead of the expected
slot).

```
FAIL  src/lib/specSheet.test.ts > specSheet helpers > slotForCategory > maps taxonomy value Bottom to slot BOTTOM
AssertionError: expected 'PIECE' to be 'BOTTOM'

FAIL  src/lib/specSheet.test.ts > specSheet helpers > slotForCategory > maps taxonomy value Dress to slot TOP
AssertionError: expected 'PIECE' to be 'TOP'

FAIL  src/lib/specSheet.test.ts > specSheet helpers > slotForCategory > maps taxonomy value Jewelry to slot CARRY
AssertionError: expected 'PIECE' to be 'CARRY'

 Test Files  1 failed (1)
      Tests  3 failed | 46 passed (49)
```

## Artifact: GREEN — `specSheet.test.ts` after extending `CATEGORY_SLOTS`

**What it proves:** After adding `dress`/`bottom`/`jewelry` (lower-cased) to
`CATEGORY_SLOTS`, all 49 tests pass — the three new taxonomy cases, the
"covers every taxonomy value" completeness check, and every pre-existing
legacy-keyword / case-insensitivity / null-blank-unrecognized → `PIECE`
assertion.

**Why it matters:** Direct proof that FR 4.1 and FR 4.2 are both satisfied —
new vocabulary resolves correctly and nothing pre-existing regressed.

**Command:**

```bash
cd frontend && npx vitest run src/lib/specSheet.test.ts
```

**Result summary:** All 49 tests pass.

```
 ✓ src/lib/specSheet.test.ts (49 tests) 3ms

 Test Files  1 passed (1)
      Tests  49 passed (49)
```

## Artifact: Coverage on the changed module

**What it proves:** `specSheet.ts` — the file this task modifies — is fully
exercised by the test suite, not just passing on a happy path.

**Why it matters:** `docs/TESTING.md`'s frontend-logic expectation is
coverage-backed, matching the standard already set by `deriveName`/
`swatchColor` in the same file.

**Command:**

```bash
cd frontend && npx vitest run --coverage src/lib/specSheet.test.ts
```

**Result summary:** `specSheet.ts` shows 100% statements/branches/functions/
lines.

```
File           | % Stmts | % Branch | % Funcs | % Lines
---------------|---------|----------|---------|--------
 specSheet.ts  |     100 |      100 |     100 |     100
```

## Artifact: Full frontend suite, lint, and typecheck are green

**What it proves:** The task's changes integrate cleanly with the rest of the
frontend — no other suite regressed, and the codebase stays lint- and
type-clean.

**Why it matters:** This is the parent-task quality gate required before
commit (repository pre-commit + CI equivalent gates).

**Command:**

```bash
cd frontend && npm test -- --run
cd frontend && npm run lint
cd frontend && npx tsc -b
```

**Result summary:** 29 test files / 318 tests pass (up from 315 before this
task — 3 new cases in `specSheet.test.ts`: the taxonomy-value `it.each` block
plus a completeness check binding it to `CATEGORIES`). ESLint and `tsc -b`
both exit clean with no output.

```
 Test Files  29 passed (29)
      Tests  318 passed (318)
```

```
> ensemble-frontend@0.0.1 lint
> eslint .
```

(`tsc -b` produced no output, confirming a clean typecheck.)

## Artifact: No new `Slot` value; `placement.ts` untouched; diff scoped to `CATEGORY_SLOTS`

**What it proves:** The reuse-with-zero-change invariant from the spec's
Technical Considerations holds — `slotForCategory`'s consumers
(`placement.ts`, and transitively the manual-assembly screen from issue #21)
need no changes, and the `Slot` union itself is unchanged.

**Why it matters:** This is the direct proof artifact the spec's Unit 4
proof-artifact list calls for (FR 4.3).

**Command:**

```bash
git diff --stat -- frontend/src/lib/placement.ts frontend/src/lib/placement.test.ts
git diff -- frontend/src/lib/specSheet.ts
```

**Result summary:** The `placement.ts`/`placement.test.ts` diff-stat is
empty (zero lines changed). The `specSheet.ts` diff shows only three new
entries inside `CATEGORY_SLOTS` (`dress`, `bottom`, `jewelry`) plus an
expanded doc comment — the `export type Slot = ...` line is untouched.

```diff
 const CATEGORY_SLOTS: Record<string, Slot> = {
   shirt: 'TOP',
   ...
   jacket: 'TOP',
+  dress: 'TOP',
   pants: 'BOTTOM',
   ...
   skirt: 'BOTTOM',
+  bottom: 'BOTTOM',
   shoes: 'SHOES',
   ...
   accessory: 'CARRY',
+  jewelry: 'CARRY',
 }
```

## Artifact: Only the expected files changed

**What it proves:** The implementation touched exactly the files the task
list identifies for Task 4.0 — no stray edits to unrelated modules.

**Why it matters:** Confirms scope discipline for a task boundary that will
be reviewed and committed independently, and that no other parent task's
files were touched.

**Command:**

```bash
git status --short
```

**Result summary:** Modified: the task file, `specSheet.ts`,
`specSheet.test.ts`. The untracked `18-reviews/` directory is a read-only
background reviewer artifact, left unstaged per instruction.

```
 M docs/specs/18-spec-wardrobe-categories/18-tasks-wardrobe-categories.md
 M frontend/src/lib/specSheet.test.ts
 M frontend/src/lib/specSheet.ts
?? docs/specs/18-spec-wardrobe-categories/18-reviews/
```

## Reviewer Conclusion

Every taxonomy value now resolves to a real outfit-card slot instead of the
generic `PIECE` fallback, with `Jewelry` intentionally sharing `CARRY` with
`Accessory` and no new `Slot` value introduced. The RED→GREEN cycle is
directly evidenced (3 failing cases before the fix, all passing after), the
changed module holds 100% coverage, and `placement.ts` — the other consumer
of `slotForCategory`/`Slot` — has zero diff, confirming the reuse-with-
zero-change invariant the spec requires. The full 318-test frontend suite,
ESLint, and `tsc -b` are all green, closing out the last of the four
demoable units in this spec.
