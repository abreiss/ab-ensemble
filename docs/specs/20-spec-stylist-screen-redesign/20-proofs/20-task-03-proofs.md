# Task 03 Proofs — Frontend data layer: enriched contract + deterministic render helpers

## Task Summary

This task proves the frontend now (a) mirrors the enriched backend `StyleResponse.OutfitItem`
contract — each item carrying the LLM `rationale` plus its stored tag fields — and (b) derives the
spec-sheet **name**, **slot label**, and **color swatch** purely in code from those stored tags,
never from the model. This is the deterministic-render layer the spec-sheet UI (task 5.0) draws
from, keeping the AGENTS.md LLM-vs-deterministic split intact: the model supplies only prose
rationale; name/slot/swatch/pips are computed here.

No view code is added in this task — only TypeScript types (`api/style.ts`) and pure helpers
(`lib/specSheet.ts`), both covered by unit tests including null-tag degradation.

## What This Task Proves

- **Enriched contract (FR U2-4):** `OutfitItem` gains `rationale` + `category`/`primaryColor`/`formality`/`warmth`/`descriptors`, mirroring the backend DTO; the client returns them unchanged.
- **Deterministic slot mapping (FR U3 slot):** `slotForCategory` maps known categories to `TOP`/`BOTTOM`/`SHOES`/`CARRY`, with a `PIECE` fallback for unknown/null.
- **Deterministic name (FR U3 name):** `deriveName` builds `primaryColor + lead descriptor + category` in sentence case, degrading sensibly as each tag drops to null and to a safe `Garment` label when all are null.
- **Deterministic swatch (FR U3 swatch):** `swatchColor` passes CSS-keyword colors through, maps curated non-keyword names (e.g. `natural`) to hex, and degrades unknown/null to a neutral fallback.
- **No regression (Success Metric 3):** the full frontend suite, typecheck, and lint stay green.

## Evidence Summary

- `specSheet.test.ts` (33 tests) and `style.test.ts` (8 tests) pass — 41/41 — covering every helper branch incl. null degradation and the enriched contract round-trip.
- `npx tsc -b` exits clean (0) — the widened `OutfitItem` type compiles across all consumers.
- `npm run lint` (eslint) exits clean (0).
- `npm run test -- --run` — the whole suite (12 files / 129 tests) passes, proving no existing screen regressed.

## Artifact: Deterministic helper + enriched-client unit tests

**What it proves:** every branch of `slotForCategory`, `deriveName`, and `swatchColor` (including null/blank degradation) is exercised, and the `api/style` client carries the enriched `OutfitItem` (rationale + tags) through unchanged.

**Why it matters:** these helpers are the deterministic half of the render contract — if a tag is null the card must still render, and the model must never be asked for the name/slot/swatch. The tests lock that behavior in.

**Command:**

~~~bash
cd frontend && npx vitest run src/lib/specSheet.test.ts src/api/style.test.ts --reporter=verbose
~~~

**Result summary:** 41/41 tests pass across the two files.

~~~text
 ✓ src/lib/specSheet.test.ts > slotForCategory > maps shirt/tee/sweater/jacket to TOP
 ✓ src/lib/specSheet.test.ts > slotForCategory > maps pants/chinos/jeans/shorts/skirt to BOTTOM
 ✓ src/lib/specSheet.test.ts > slotForCategory > maps shoes/loafers/sneakers/boots to SHOES
 ✓ src/lib/specSheet.test.ts > slotForCategory > maps bag/tote/accessory to CARRY
 ✓ src/lib/specSheet.test.ts > slotForCategory > is case-insensitive and trims surrounding whitespace
 ✓ src/lib/specSheet.test.ts > slotForCategory > falls back to PIECE for an unknown category
 ✓ src/lib/specSheet.test.ts > slotForCategory > falls back to PIECE for a null or blank category
 ✓ src/lib/specSheet.test.ts > deriveName > joins color + lead descriptor + category in sentence case
 ✓ src/lib/specSheet.test.ts > deriveName > uses only the first descriptor when several are present
 ✓ src/lib/specSheet.test.ts > deriveName > drops a null color and keeps descriptor + category
 ✓ src/lib/specSheet.test.ts > deriveName > drops an absent descriptor and keeps color + category
 ✓ src/lib/specSheet.test.ts > deriveName > falls back to the category alone when color and descriptor are missing
 ✓ src/lib/specSheet.test.ts > deriveName > ignores blank-string parts
 ✓ src/lib/specSheet.test.ts > deriveName > degrades to a safe label when every tag is null
 ✓ src/lib/specSheet.test.ts > swatchColor > passes the CSS keyword white/olive/tan through as-is
 ✓ src/lib/specSheet.test.ts > swatchColor > is case-insensitive and trims for keyword colors
 ✓ src/lib/specSheet.test.ts > swatchColor > maps a curated non-keyword color name to a hex value
 ✓ src/lib/specSheet.test.ts > swatchColor > falls back to the neutral swatch for an unknown color name
 ✓ src/lib/specSheet.test.ts > swatchColor > falls back to the neutral swatch for a null or blank color
 ✓ src/api/style.test.ts > requestStyle > returns each enriched OutfitItem (rationale + tag fields) unchanged
 ✓ src/api/style.test.ts > requestStyle > (prompt POST / history / backward-compat / empty / non-2xx / network)
 ✓ src/api/style.test.ts > photoUrl > re-exports the items photo-path builder

 Test Files  2 passed (2)
      Tests  41 passed (41)
~~~

## Artifact: Typecheck + lint clean on the widened contract

**What it proves:** widening `OutfitItem` type-checks across every consumer (incl. `Stylist.test.tsx` fixtures) and passes lint.

**Why it matters:** a shared type change that breaks `tsc -b` would break `npm run build`; this confirms the tree stays shippable.

**Command:**

~~~bash
cd frontend && npx tsc -b && npm run lint
~~~

**Result summary:** both exit 0 with no diagnostics.

~~~text
=== TYPECHECK ===
TSC_EXIT: 0
=== LINT ===
> eslint .
LINT_EXIT: 0
~~~

## Artifact: Full frontend suite — no regression

**What it proves:** all existing screens still route and render after the type widening.

**Why it matters:** Success Metric 3 requires no regression; the enriched contract touches a shared type, so the whole suite is the guard.

**Command:**

~~~bash
cd frontend && npm run test -- --run
~~~

**Result summary:** 12 files / 129 tests pass (the +33 specSheet and +1 style enriched-contract test included).

~~~text
 ✓ src/App.test.tsx (6 tests)
 ✓ src/routes/WardrobeGrid.test.tsx (4 tests)
 ✓ src/components/DescriptorChips.test.tsx (4 tests)
 ✓ src/components/TagForm.test.tsx (6 tests)
 ✓ src/routes/ItemDetail.test.tsx (9 tests)
 ✓ src/lib/specSheet.test.ts (33 tests)
 ✓ src/lib/tagValidation.test.ts (14 tests)
 ✓ src/lib/relativeTime.test.ts (6 tests)
 ✓ src/routes/AddItem.test.tsx (8 tests)
 ✓ src/routes/Stylist.test.tsx (14 tests)

 Test Files  12 passed (12)
      Tests  129 passed (129)
~~~

## Reviewer Conclusion

The enriched `OutfitItem` contract is mirrored on the client and the three deterministic
spec-sheet helpers are implemented and fully unit-tested, including every null-tag degradation
path. Typecheck, lint, and the full suite are green, so task 5.0 can build the spec-sheet UI on a
stable, deterministic data layer without touching the model for name/slot/swatch.
