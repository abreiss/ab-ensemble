# 18-tasks-wardrobe-categories.md

Task list for **issue #18 — Wardrobe categories (grouped browsing) + Jewelry
support**. Derived from `18-spec-wardrobe-categories.md`. Parent tasks map 1:1 to
the spec's four Demoable Units of Work; each is an end-to-end vertical slice that
can be shown on its own. The build order follows the spec's interdependency
warning: the **backend foundation ships first** (Task 1.0), so nothing downstream
depends on the model or the client honoring the taxonomy; the frontend form and
grid follow; the `slotForCategory` fix (Task 4.0) closes the last regression
window.

Follow the repository's mandatory TDD workflow (RED → GREEN → REFACTOR) and the
`docs/TESTING.md` coverage split:

- **Backend domain** (the taxonomy constant + `normalize`, `ItemMapper.applyTags`
  normalization, `TaggingService.map` normalization, vision-schema `enum`) is
  **strict TDD**: `normalize` and vision-tag mapping are **critical logic →
  100% branch coverage**; ≥90% line on new backend domain code.
- **Frontend logic** (the taxonomy constant + `normalizeCategory`, the pure
  section-grouping/ordering helper, the form + validation behavior) is tested with
  **Vitest + React Testing Library** on meaningful logic; grid/form **view
  plumbing** is tested for behavior, not over-tested.

Conventional commits, roughly one per demoable unit. Branch: `wardrobe-grouping`
(assumption M1).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/wardrobe/CategoryTaxonomy.java` | **New.** Backend single source of truth: the ordered taxonomy constant (`Jacket, Top, Bottom, Dress, Shoes, Jewelry, Accessory, Other`) + the pure `normalize(String) → taxonomy value` function (case/whitespace-insensitive, legacy-synonym map, unrecognized/blank/null → `"Other"`, never throws). Pure domain logic, lives with the constant (not in a controller). |
| `src/test/java/com/ensemble/wardrobe/CategoryTaxonomyTest.java` | **New.** Unit tests for `normalize` — every taxonomy value, representative legacy synonyms, casing/whitespace variants, unrecognized/blank/null → `"Other"`. **100% branch coverage** required. |
| `src/main/java/com/ensemble/wardrobe/dto/TagRequest.java` | **Modify.** Drop `@NotNull` from `formality`/`warmth` (keep `@Min`/`@Max`); `category` stays `@NotBlank`, so jewelry saves with null warmth/formality. |
| `src/main/java/com/ensemble/wardrobe/dto/ItemMapper.java` | **Modify.** `applyTags` — the single write choke point (both `create` and `updateTags`) — calls `CategoryTaxonomy.normalize(tags.category())` before setting `category`. |
| `src/test/java/com/ensemble/wardrobe/dto/ItemMapperTest.java` | **Modify.** Assert `applyTags` persists a normalized taxonomy value for a legacy/off-taxonomy input. |
| `src/test/java/com/ensemble/wardrobe/WardrobeServiceTest.java` | **Modify (verify).** Confirm both `create` and `updateTags` persist a normalized category via `applyTags` (choke point covers both paths). |
| `src/main/java/com/ensemble/tagging/AnthropicVisionModelClient.java` | **Modify.** Add `"enum": [taxonomy values]` to the `category` property in `tagTool()`'s input schema; make `tagTool()` package-private so a test can assert the enum (assumption A2.1). Enum derived from `CategoryTaxonomy`. |
| `src/test/java/com/ensemble/tagging/AnthropicVisionModelClientTest.java` | **Modify.** Assert the `category` tool-schema property carries the taxonomy `enum`. |
| `src/main/java/com/ensemble/tagging/TaggingService.java` | **Modify.** In `map`, run the model-emitted category through `CategoryTaxonomy.normalize` so the suggestion pre-fill is already a taxonomy value. |
| `src/test/java/com/ensemble/tagging/TaggingServiceTest.java` | **Modify.** Assert an off-taxonomy model category (e.g. `"sweatshirt"`) is normalized before it reaches the `TagSuggestion`. |
| `src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java` | **Modify.** Add a create case: `category=Jewelry` with `formality`/`warmth` omitted returns `201` (not `400`). |
| `frontend/src/lib/categoryTaxonomy.ts` | **New.** Frontend single source of truth: ordered `CATEGORIES` + `Category` type + `normalizeCategory(raw) → Category` (mirrors the backend map). Task 3.0 extends it with the singular→plural display-label map and the `Other`-last section order. |
| `frontend/src/lib/categoryTaxonomy.test.ts` | **New.** Unit tests: `normalizeCategory` for each value, legacy synonyms, casing/whitespace, unrecognized/blank/null → `Other`; (Task 3.0) display labels + section order. |
| `frontend/src/components/TagForm.tsx` | **Modify.** Replace the free-text category `<input>` with a `<select>` of `CATEGORIES` + `—` placeholder (matching the `formality`/`warmth` `<select>` pattern); pre-select the `normalizeCategory` bucket when seeding from a legacy value. |
| `frontend/src/components/TagForm.test.tsx` | **Modify.** Assert: the category control is a `<select>` of exactly the taxonomy values; selecting `Jewelry` with warmth/formality unset yields a submittable form + null warmth/formality; a legacy `"chinos"` seed pre-selects `Bottom`. |
| `frontend/src/lib/tagValidation.ts` | **Modify.** Make `formality`/`warmth` optional (null valid; a supplied value still range-checked); `category` stays required. Update `RequiredTagFields` accordingly. |
| `frontend/src/lib/tagValidation.test.ts` | **Modify.** Assert null `formality`/`warmth` are valid; a blank `category` is invalid; out-of-range supplied values still fail. |
| `frontend/src/types/item.ts` | **Modify.** `TagInput.formality`/`warmth` become `number | null`. |
| `frontend/src/lib/wardrobeSections.ts` | **New.** Pure grouping helper: `groupByCategory(items) → ordered [{ category, label, items }]` using `normalizeCategory` + the display-label map, in fixed taxonomy order, `Other` last, empty sections omitted. No React (the meaningful headline logic, unit-tested like `placement.ts`). |
| `frontend/src/lib/wardrobeSections.test.ts` | **New.** Unit tests: mixed list (legacy + unrecognized) groups in fixed order, `Other` last, empty sections omitted, each item under the expected bucket; a `Jewelry` item lands in the Jewelry group. |
| `frontend/src/routes/WardrobeGrid.tsx` | **Modify.** Render one section per non-empty group (header + existing `.grid`), driving off `groupByCategory`. Preserve the loading / empty / list-failure-retry states and the existing thumbnail/detail-link + "Build it yourself" entry. |
| `frontend/src/routes/WardrobeGrid.test.tsx` | **Modify.** Assert sections render in fixed order with `Other` last, empty sections omitted, a `Jewelry` item under a "Jewelry" header; preserve existing loading/empty/error assertions. |
| `frontend/src/index.css` | **Modify.** Add a lightweight section-header element reusing existing type/spacing tokens (`.screen`, `.grid`, `.screen-title`); no new design system. |
| `frontend/src/lib/specSheet.ts` | **Modify.** Extend `CATEGORY_SLOTS` so every taxonomy value resolves to a slot (`Jacket`→`TOP`, `Top`→`TOP`, `Bottom`→`BOTTOM`, `Dress`→`TOP`, `Shoes`→`SHOES`, `Jewelry`→`CARRY`, `Accessory`→`CARRY`, `Other`→`PIECE`). Keep the case-insensitive/trimmed lookup + `PIECE` fallback and every existing legacy key. **No new `Slot` value.** |
| `frontend/src/lib/specSheet.test.ts` | **Modify.** Assert each taxonomy value maps to its expected slot (`Jewelry`→`CARRY`, not `PIECE`); existing legacy + `PIECE`-fallback tests stay green. |
| `frontend/src/lib/placement.ts` | **Reference, unchanged.** Reuses `slotForCategory`/`Slot`; benefits from the richer map with no change (no new `Slot` value). |

### Notes

- Backend tests: `./gradlew test -PskipFrontend` (fast); coverage via
  `./gradlew jacocoTestReport` → `build/reports/jacoco/`. Frontend tests:
  `cd frontend && npm test -- --run`; lint: `npm run lint`; branch coverage via the
  Vitest coverage run.
- Unit tests sit beside the code they test (backend: mirror package under
  `src/test/...`; frontend: `foo.ts` + `foo.test.ts`).
- Two derived lists, one intent (assumption A1.4): the Java `CategoryTaxonomy` and
  the TS `categoryTaxonomy.ts` each define the taxonomy once for their own stack;
  cross-stack agreement is a **review/test invariant**, not codegen. Keep the two
  ordered lists and the two synonym maps in sync when editing either.
- Grouping bucket vs card slot are **distinct mappings** (spec Technical
  Considerations): `normalizeCategory("jacket") → Jacket` (grid section) while
  `slotForCategory("Jacket") → TOP` (card slot). Do not collapse them.
- Follow existing conventions: the `@WebMvcTest` + multipart-`param` controller
  test pattern (`WardrobeControllerTest`), the mocked `VisionModelClient` seam
  (`TaggingServiceTest`), the `settle()` loading/error pattern (`WardrobeGrid`),
  and the `formality`/`warmth` `<select>` markup (`TagForm`).
- Adhere to the pre-commit gates (backend + frontend tests, eslint, secret scan).

## Tasks

### [x] 1.0 Backend taxonomy foundation: constant + `normalize`, vision `enum`, nullable warmth/formality, save-path normalization

Establish the taxonomy as the single backend source of truth and make it
authoritative on every save path. Add the ordered taxonomy constant + a pure
`normalize` function (100% branch), constrain the vision tool schema with the
derived `enum`, relax `TagRequest.formality`/`warmth` to nullable so jewelry
saves, and apply `normalize` at the single write choke point (`ItemMapper.applyTags`)
and to the model-emitted category in `TaggingService.map`. No table change, no
batch migration. Covers spec Unit 1 and Success Metrics 2, 3, 5, 6.

#### 1.0 Proof Artifact(s)

- Test: `CategoryTaxonomyTest` — `normalize` covers each taxonomy value,
  representative legacy synonyms (`chinos`→`Bottom`, `T-Shirt`→`Top`,
  `necklace`→`Jewelry`), casing/whitespace variants, and
  unrecognized/blank/null → `"Other"`; run under JaCoCo shows **100% branch
  coverage** on `CategoryTaxonomy`. Demonstrates the fallback (FR 1.1, 1.4).
- Test: `WardrobeControllerTest` — a create request with `category=Jewelry` and
  `formality`/`warmth` omitted returns `201` (not `400`). Demonstrates the
  nullability relaxation lets jewelry save (FR 1.3).
- Test: `AnthropicVisionModelClientTest` — the `category` property of
  `tagTool()`'s input schema carries the taxonomy `enum`. Demonstrates the schema
  constraint is emitted (FR 1.2).
- Test: `TaggingServiceTest` — an off-taxonomy model category (e.g.
  `"sweatshirt"`) is normalized to a taxonomy value in the returned
  `TagSuggestion`. Demonstrates the vision-path fallback (FR 1.5, vision half).
- Test: `ItemMapperTest` — `applyTags` with a legacy/off-taxonomy `category`
  persists the normalized taxonomy value onto the `Item`. Demonstrates save-path
  normalization at the choke point (FR 1.5, save half).
- CLI: `./gradlew test jacocoTestReport` exits 0 with branch coverage on
  `CategoryTaxonomy`. Demonstrates the coverage gate (FR 1.1, 1.4; Success
  Metric 6).
- CLI: `git diff --stat src/main/java/com/ensemble/wardrobe/Item.java` is empty —
  the DynamoDB `category` attribute is unchanged (FR 1.6; Success Metric 5).

#### 1.0 Tasks

- [x] 1.1 [RED] Write `CategoryTaxonomyTest`: assert an ordered `values()` list of
  the eight taxonomy values, and `normalize` cases — each canonical value maps to
  itself; legacy synonyms (`chinos`/`jeans`→`Bottom`, `shirt`/`T-Shirt`→`Top`,
  `necklace`→`Jewelry`, `blazer`→`Jacket`, `sneakers`→`Shoes`, `tote`→`Accessory`,
  `gown`→`Dress`); casing/whitespace (`"  T-SHIRT "`→`Top`); and
  unrecognized/blank/null → `"Other"` (never throws). Run to confirm failure.
- [x] 1.2 [GREEN] Create `com.ensemble.wardrobe.CategoryTaxonomy`: the ordered
  taxonomy constant (source of truth), the case/whitespace-insensitive
  synonym→bucket map (assumption A1.12), and `normalize(String) → taxonomy value`
  returning `"Other"` for unrecognized/blank/null. Make 1.1 pass; confirm **100%
  branch coverage** on `CategoryTaxonomy` via `jacocoTestReport`.
- [x] 1.3 [RED→GREEN] In `WardrobeControllerTest`, add a create case:
  `category=Jewelry` with `formality`/`warmth` params omitted → `201`. Run
  (fails with `400` today). Then in `TagRequest`, drop `@NotNull` from
  `formality`/`warmth` (keep `@Min`/`@Max`, keep `category` `@NotBlank`). Make it
  pass; confirm the existing range-rejection tests stay green (a supplied
  out-of-range value still `400`s).
- [x] 1.4 [RED→GREEN] In `AnthropicVisionModelClientTest`, add a test asserting the
  `category` property of `tagTool()`'s input schema carries the taxonomy `enum`
  (values from `CategoryTaxonomy`). Make `tagTool()` package-private (assumption
  A2.1) so the test can read its `InputSchema`. Add `"enum"` to the `category`
  property in `tagTool()` derived from `CategoryTaxonomy`. Make it pass.
- [x] 1.5 [RED→GREEN] In `TaggingServiceTest`, add a test: a model tag JSON with an
  off-taxonomy `category` (`"sweatshirt"`) yields a `TagSuggestion` whose
  `category` is the normalized bucket (`Top`). Run (fails — raw value today). In
  `TaggingService.map`, wrap the category through `CategoryTaxonomy.normalize`.
  Make it pass; keep 100% branch on the mapping (existing null/blank/absent cases
  stay green).
- [x] 1.6 [RED→GREEN] In `ItemMapperTest`, add a test: `applyTags` with a legacy
  `category` (`"chinos"`) sets the `Item.category` to `Bottom`. Run (fails). In
  `ItemMapper.applyTags`, set `category` via `CategoryTaxonomy.normalize(tags.category())`.
  Make it pass.
- [x] 1.7 [REFACTOR] In `WardrobeServiceTest`, confirm/add coverage that both
  `create` and `updateTags` persist a normalized category (the choke point covers
  both). Verify `./gradlew test jacocoTestReport -PskipFrontend` is green and
  `git diff --stat src/main/java/com/ensemble/wardrobe/Item.java` is empty. Commit
  (`feat(backend): category taxonomy + normalize on every save path`).

### [ ] 2.0 `TagForm` category `<select>` + relaxed client validation

Make the manual add/edit path produce only taxonomy values and let jewelry save
from the form (null warmth/formality). Define the frontend taxonomy once
(`categoryTaxonomy.ts`), replace the free-text category `<input>` with a `<select>`
of taxonomy values, pre-select the normalized bucket when editing a legacy value,
and relax client validation so warmth/formality are optional. Covers spec Unit 2
and Success Metrics 2, 3.

#### 2.0 Proof Artifact(s)

- Test: `categoryTaxonomy.test.ts` — `normalizeCategory` covers each value, legacy
  synonyms, casing/whitespace, unrecognized/blank/null → `Other`. Demonstrates the
  frontend source of truth mirrors the backend (FR 2.1).
- Test: `TagForm.test.tsx` — the category control is a `<select>` listing exactly
  the taxonomy values (with a `—` placeholder). Demonstrates the fixed vocabulary
  (FR 2.2).
- Test: `TagForm.test.tsx` — selecting `Jewelry` and leaving warmth/formality unset
  yields a submittable form whose emitted `TagInput` has null warmth/formality.
  Demonstrates jewelry saves from the form (FR 2.4, 2.5).
- Test: `TagForm.test.tsx` — a form seeded with a legacy category (`"chinos"`)
  pre-selects `Bottom`. Demonstrates edit-time normalization of the control
  (FR 2.3).
- Test: `tagValidation.test.ts` — null `formality`/`warmth` are valid; a blank
  `category` is invalid; a supplied out-of-range value still fails. Demonstrates
  the relaxed rules (FR 2.4).
- Screenshot: the add/edit form with the category dropdown open showing the
  taxonomy values (no passcode/token visible). Demonstrates the UI (FR 2.2).

#### 2.0 Tasks

- [ ] 2.1 [RED] Write `frontend/src/lib/categoryTaxonomy.test.ts`: assert an ordered
  `CATEGORIES` array of the eight values and `normalizeCategory` cases mirroring
  the backend (each value → itself; `chinos`→`Bottom`, `T-Shirt`→`Top`,
  `necklace`→`Jewelry`; casing/whitespace; unrecognized/blank/null → `Other`). Run
  to confirm failure.
- [ ] 2.2 [GREEN] Create `frontend/src/lib/categoryTaxonomy.ts` exporting
  `CATEGORIES`, the `Category` type, and `normalizeCategory(raw): Category` (the
  synonym map mirrors A1.12). Make 2.1 pass.
- [ ] 2.3 [RED] In `tagValidation.test.ts`, add/adjust tests: null `formality`/
  `warmth` are valid; blank `category` is invalid; supplied out-of-range
  formality/warmth still fail. Run to confirm failure against the current
  required-field rules.
- [ ] 2.4 [GREEN] In `tagValidation.ts`, make `formality`/`warmth` optional (null
  valid; range-check only when supplied); keep `category` required. Update
  `RequiredTagFields` and, in `types/item.ts`, `TagInput.formality`/`warmth` to
  `number | null`. Adjust `TagForm`'s `toTagInput` cast accordingly. Make 2.3 pass;
  ensure `tsc -b` is clean.
- [ ] 2.5 [RED] In `TagForm.test.tsx`, add tests: (a) the category control is a
  `<select>` of exactly the taxonomy values with a `—` placeholder; (b) selecting
  `Jewelry` with warmth/formality unset produces a submittable form whose
  `onSubmit`/`onChange` `TagInput` has `category: "Jewelry"`, `warmth: null`,
  `formality: null`; (c) seeding `initial={{ category: "chinos", ... }}`
  pre-selects `Bottom`. Run to confirm failure.
- [ ] 2.6 [GREEN] In `TagForm.tsx`, replace the category `<input>` with a `<select>`
  built from `CATEGORIES` (matching the `formality`/`warmth` `<select>` markup +
  `—` placeholder); seed the control via `normalizeCategory(initial?.category)` so
  a legacy value pre-selects its bucket and never renders blank/off-list. Make 2.5
  pass.
- [ ] 2.7 [REFACTOR] Verify `npm test -- --run` + `npm run lint` + `tsc -b` green;
  confirm the vision → suggestion → editable-form → "Save all" flow still passes
  (`AddItem.test.tsx` stays green). Capture the dropdown screenshot. Commit
  (`feat(frontend): taxonomy select + relaxed tag validation`).

### [ ] 3.0 Wardrobe grid grouped into category sections

The headline browsing improvement: the flat `/wardrobe` grid becomes category
sections. Add a pure grouping/ordering helper (read-time `normalizeCategory` on
stored values, fixed order, `Other` last, empty sections hidden, plural headers
from an explicit label map) and render it in `WardrobeGrid.tsx`, preserving the
existing loading/empty/error states. Covers spec Unit 3 and Success Metrics 1, 2, 5.

#### 3.0 Proof Artifact(s)

- Test: `wardrobeSections.test.ts` — a mixed item list (including legacy free-text
  and an unrecognized value) groups into sections in the fixed order with `Other`
  last, empty sections omitted, each item under the expected bucket (read-time
  `normalizeCategory`, no stored value mutated). Demonstrates grouping + ordering +
  normalization (FR 3.2, 3.3, 3.4, 3.5).
- Test: `wardrobeSections.test.ts` — a `Jewelry` item lands in the `Jewelry`
  group. Demonstrates jewelry grouping (FR 3.1).
- Test: `WardrobeGrid.test.tsx` — sections render in the fixed order with `Other`
  last and empty sections omitted; a `Jewelry` item appears under a "Jewelry"
  header; the loading, empty-wardrobe, and list-failure/retry states and per-item
  thumbnail/detail links still render. Demonstrates the grid wiring + no
  regression (FR 3.1, 3.6).
- Screenshot: `/wardrobe` showing multiple category sections with headers (no
  passcode/token visible). Demonstrates grouped browsing (FR 3.1; Success
  Metric 1).

#### 3.0 Tasks

- [ ] 3.1 [RED] In `categoryTaxonomy.test.ts`, add tests for the singular→plural
  display-label map (`Jacket`→"Jackets", `Accessory`→"Accessories", `Shoes`→"Shoes",
  `Jewelry`→"Jewelry", `Other`→"Other", per A1.11) and the section order helper
  (taxonomy order with `Other` always last). Run to confirm failure.
- [ ] 3.2 [GREEN] Extend `categoryTaxonomy.ts` with the explicit `CATEGORY_LABELS`
  map (or `sectionLabel(category)`) and a `sectionOrder` export/helper that yields
  the taxonomy values with `Other` last. Make 3.1 pass.
- [ ] 3.3 [RED] Write `frontend/src/lib/wardrobeSections.test.ts`: given a mixed
  `Item[]` (a legacy `"shirt"`, a legacy `"chinos"`, an unrecognized `"widget"`, a
  `"Jewelry"`, and one canonical value), `groupByCategory` returns ordered
  `{ category, label, items }` groups in fixed order with `Other` last, empty
  sections omitted, and each item bucketed via `normalizeCategory` of its **stored**
  category. Run to confirm failure.
- [ ] 3.4 [GREEN] Create `frontend/src/lib/wardrobeSections.ts` — a pure
  `groupByCategory(items: Item[])` reusing `normalizeCategory` + the label map +
  section order. No React, no mutation of items. Make 3.3 pass.
- [ ] 3.5 [RED] In `WardrobeGrid.test.tsx`, add tests: a populated list renders one
  section header per non-empty group in fixed order with `Other` last; a `Jewelry`
  item is under a "Jewelry" header; assert the existing loading / empty / error
  states and thumbnail/detail links still render. Run to confirm failure.
- [ ] 3.6 [GREEN] In `WardrobeGrid.tsx`, replace the single flat `.grid` (populated
  branch only) with one section per `groupByCategory` group — a header element +
  the existing `.grid` markup per section — preserving the loading/empty/
  list-failure-retry branches and the "Build it yourself" entry. Make 3.5 pass.
- [ ] 3.7 [REFACTOR] Add a lightweight section-header style to `index.css` reusing
  existing tokens (no new design system). Verify `npm test -- --run` + `npm run
  lint` + `tsc -b` green. Capture the grouped-`/wardrobe` screenshot. Commit
  (`feat(frontend): group wardrobe grid into category sections`).

### [ ] 4.0 `slotForCategory` map updated for the new taxonomy

Prevent the outfit-result card regressing to the generic `PIECE` label once vision
emits taxonomy values. Extend `slotForCategory`'s keyword map so every taxonomy
value resolves to a slot, keeping the case-insensitive lookup + `PIECE` fallback
and introducing **no new `Slot` value** (Jewelry shares `CARRY`). Covers spec
Unit 4 and Success Metric 4.

#### 4.0 Proof Artifact(s)

- Test: `specSheet.test.ts` — each taxonomy value maps to its expected slot
  (`Jacket`/`Top`/`Dress`→`TOP`, `Bottom`→`BOTTOM`, `Shoes`→`SHOES`,
  `Jewelry`/`Accessory`→`CARRY`, `Other`→`PIECE`); a `Jewelry` item resolves to
  `CARRY`, not `PIECE`. Demonstrates the map covers the new vocabulary (FR 4.1).
- Test: `specSheet.test.ts` — existing legacy-keyword and null/blank/unrecognized
  → `PIECE` cases stay green; the `Slot` union is unchanged. Demonstrates no
  regression and no new `Slot` value (FR 4.2, 4.3).
- CLI: `git diff frontend/src/lib/specSheet.ts` shows only `CATEGORY_SLOTS`
  additions (the `Slot` type unchanged); `placement.ts` is untouched. Demonstrates
  the reuse-with-zero-change invariant (FR 4.3; Technical Considerations).

#### 4.0 Tasks

- [ ] 4.1 [RED] In `specSheet.test.ts`, add a test asserting each taxonomy value
  (imported from `categoryTaxonomy.ts` to bind the two lists as a test invariant)
  maps to its expected slot, including `Jewelry`→`CARRY` (not `PIECE`) and
  `Other`→`PIECE`. Run to confirm failure (`Jacket`/`Dress`/`Jewelry`/`Other` not
  yet keyed, or cased differently).
- [ ] 4.2 [GREEN] In `specSheet.ts`, extend `CATEGORY_SLOTS` with lower-cased keys
  for the taxonomy values (`jacket`/`top`/`dress`→`TOP`, `bottom`→`BOTTOM`,
  `shoes`→`SHOES`, `jewelry`/`accessory`→`CARRY`; `other` intentionally left
  unkeyed so it falls through to the `PIECE` fallback). Keep every existing legacy
  key and the trimmed/lower-cased lookup + `PIECE` fallback. Do **not** add a
  `Slot` value. Make 4.1 pass.
- [ ] 4.3 [REFACTOR] Confirm all existing `specSheet.test.ts` cases (legacy keys,
  null/blank/unrecognized → `PIECE`) stay green and `placement.ts`/`placement.test.ts`
  are untouched. Verify `npm test -- --run` + `npm run lint` + `tsc -b` green.
  Commit (`fix(frontend): map new taxonomy values to outfit-card slots`).
