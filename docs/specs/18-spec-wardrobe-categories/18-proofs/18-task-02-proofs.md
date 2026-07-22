# Task 02 Proofs - `TagForm` category `<select>` + relaxed client validation

## Task Summary

This task makes the manual add/edit path (the shared `TagForm` used by both
the add-item review queue and the item-detail screen) produce only taxonomy
values and lets Jewelry/Accessory items save with no formality/warmth. It
defines the frontend category taxonomy once (`categoryTaxonomy.ts`, mirroring
the backend `CategoryTaxonomy` one-for-one), replaces the free-text category
`<input>` with a `<select>` of the eight taxonomy values (matching the
existing `formality`/`warmth` `<select>` pattern), normalizes any legacy/
off-taxonomy stored category at edit time so the control never renders blank
or off-list, and relaxes `tagValidation.ts` so `formality`/`warmth` are
optional while `category` stays required.

## What This Task Proves

- The frontend defines the taxonomy once and the form derives its options
  from it — the same eight values, in the same order, as the backend.
- The category control is a real `<select>` listing exactly the taxonomy
  values behind a `—` placeholder — a user can no longer submit arbitrary
  free text from this form.
- Selecting `Jewelry` and leaving `formality`/`warmth` unset is enough to
  enable Save, and the emitted `TagInput` carries `formality: null`,
  `warmth: null` — jewelry saves from the form.
- Seeding the form from a legacy/off-taxonomy stored category (`"chinos"`)
  pre-selects its normalized bucket (`"Bottom"`) rather than rendering blank
  or an invalid option.
- `tagValidation.ts` treats a null `formality`/`warmth` as valid but still
  range-checks a supplied value, and `category` remains required.
- The existing vision → suggestion → editable-form → save flow (including the
  multi-item review-queue "Save all" path) still works end-to-end after the
  free-text → `<select>` change.

## Evidence Summary

- `categoryTaxonomy.test.ts` (22 tests, new) — the ordered `CATEGORIES` list,
  `normalizeCategory` for every canonical value, representative legacy
  synonyms, casing/whitespace variants, and unrecognized/blank/`null`/
  `undefined` → `"Other"`. **100% line and branch coverage** on
  `categoryTaxonomy.ts` (Vitest v8 coverage report).
- `TagForm.test.tsx` (9 tests, 3 new) — the category control is a `<select>`
  listing exactly `['', ...CATEGORIES]`; selecting `Jewelry` with warmth/
  formality unset yields an enabled Save and an emitted `TagInput` with
  `category: 'Jewelry', formality: null, warmth: null`; seeding
  `category: 'chinos'` pre-selects `Bottom`. The three pre-existing tests that
  typed free text into the category field were updated to `selectOptions` and
  now assert the selected/normalized taxonomy value.
- `tagValidation.test.ts` (15 tests, updated) — null `formality`/`warmth` are
  now valid; a supplied out-of-range value still fails; blank `category`
  still fails. **100% line and branch coverage** on `tagValidation.ts`.
- Full frontend suite: **288 tests, 0 failures** (`npm test -- --run`),
  including `AddItem.test.tsx` (24 tests) and `ItemDetail.test.tsx`
  (10 tests) — both updated in place where they asserted the old free-text
  category behavior (see the "Consumer test updates" artifact below), and
  both fully green.
- `npm run lint` (ESLint) — 0 errors, 0 warnings.
- `tsc -b` — 0 errors (the widened `TagInput.formality`/`warmth: number | null`
  type-checks cleanly through `TagForm`, `AddItem`, and `ItemDetail`).

## Artifact: Frontend taxonomy source of truth + `normalizeCategory`

**What it proves:** The frontend defines the eight-value taxonomy once and a
pure, never-throwing `normalizeCategory` mirrors the backend's synonym map —
the same source every consumer (this form now, the grid grouping in Task 3.0)
derives from.

**Why it matters:** Without a single frontend source of truth, the `<select>`
options and the edit-time normalization could drift from each other and from
the backend, reintroducing the casing/spelling drift this issue exists to fix.

**Command:**

```bash
cd frontend && npx vitest run src/lib/categoryTaxonomy.test.ts --reporter=verbose
```

**Result summary:** All 22 cases pass — the ordered list, every canonical
value mapping to itself, representative legacy synonyms, casing/whitespace
insensitivity, and unrecognized/blank/null/undefined all resolving to
`"Other"`.

```
 ✓ src/lib/categoryTaxonomy.test.ts > CATEGORIES > is the ordered eight-value taxonomy with Other last 1ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Jacket to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Top to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Bottom to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Dress to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Shoes to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Jewelry to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Accessory to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the canonical value Other to itself (case-insensitive) 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym chinos to Bottom 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym jeans to Bottom 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym shirt to Top 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym T-Shirt to Top 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym necklace to Jewelry 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym blazer to Jacket 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym sneakers to Shoes 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym tote to Accessory 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps the legacy synonym gown to Dress 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > is case- and whitespace-insensitive 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps an unrecognized value to Other 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps a blank or whitespace-only value to Other 0ms
 ✓ src/lib/categoryTaxonomy.test.ts > normalizeCategory > maps null and undefined to Other, never throwing 0ms

 Test Files  1 passed (1)
      Tests  22 passed (22)
```

## Artifact: `TagForm` renders a taxonomy `<select>` and lets Jewelry save with nulls

**What it proves:** The category control is a genuine `<select>` restricted to
`CATEGORIES` (no free text reaches the backend anymore); selecting `Jewelry`
alone — with formality/warmth left unset — is enough to enable Save, and the
emitted `TagInput` carries `formality: null, warmth: null`; a legacy stored
value (`"chinos"`) pre-selects its normalized bucket (`"Bottom"`) rather than
rendering blank.

**Why it matters:** This is the headline behavior change of Task 2.0 — it is
what actually lets a Jewelry item be tagged and saved manually, and it is the
control a reviewer would click through in the UI.

**Command:**

```bash
cd frontend && npx vitest run src/components/TagForm.test.tsx --reporter=verbose
```

**Result summary:** All 9 `TagForm` tests pass, including the 3 new
taxonomy-select tests; the 3 pre-existing tests that used to type free text
into the category field now select a taxonomy option and assert the emitted
value.

```
 ✓ src/components/TagForm.test.tsx > TagForm > pre-fills every field from a suggestion 21ms
 ✓ src/components/TagForm.test.tsx > TagForm > renders a null suggestion as empty, editable fields (degraded vision is normal) 4ms
 ✓ src/components/TagForm.test.tsx > TagForm > disables save until the required fields are valid, then submits the edited tags 69ms
 ✓ src/components/TagForm.test.tsx > TagForm > omits blank optional fields from the submitted payload 41ms
 ✓ src/components/TagForm.test.tsx > TagForm > keeps save disabled while a submit is in flight 4ms
 ✓ src/components/TagForm.test.tsx > TagForm > de-duplicates repeated descriptors from a suggestion so a chip is not rendered twice 3ms
 ✓ src/components/TagForm.test.tsx > TagForm > category taxonomy select > renders the category control as a <select> listing exactly the taxonomy values, with a — placeholder 2ms
 ✓ src/components/TagForm.test.tsx > TagForm > category taxonomy select > lets Jewelry save with warmth/formality left unset — both are optional 19ms
 ✓ src/components/TagForm.test.tsx > TagForm > category taxonomy select > pre-selects the normalized bucket for a legacy stored category (edit-time normalization) 2ms

 Test Files  1 passed (1)
      Tests  9 passed (9)
```

## Artifact: Relaxed client validation (`tagValidation.ts`)

**What it proves:** `formality`/`warmth` are optional — a `null` is valid,
matching the backend's relaxed `TagRequest` from Task 1.0 — while a supplied
out-of-range value still fails, and `category` remains required.

**Why it matters:** This is the guardrail that gates the Save button. Without
it, `tagsAreValid` would keep Save disabled for a Jewelry item forever
because it has no formality/warmth to give.

**Command:**

```bash
cd frontend && npx vitest run src/lib/tagValidation.test.ts --reporter=verbose
```

**Result summary:** All 15 cases pass — a null `formality`/`warmth` is valid,
boundary and out-of-range values behave exactly as before, and a blank
`category` still fails.

```
 ✓ src/lib/tagValidation.test.ts > validateTags > returns no errors when all required fields are valid 1ms
 ✓ src/lib/tagValidation.test.ts > validateTags > category > flags an empty category 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > category > flags a whitespace-only category as blank 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > category > accepts a non-blank category 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > formality (1–5, optional) > accepts a null formality (optional — e.g. jewelry has no formality) 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > formality (1–5, optional) > flags formality below 1 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > formality (1–5, optional) > flags formality above 5 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > formality (1–5, optional) > accepts the boundary values 1 and 5 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > warmth (1–3, optional) > accepts a null warmth (optional — e.g. jewelry has no warmth) 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > warmth (1–3, optional) > flags warmth below 1 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > warmth (1–3, optional) > flags warmth above 3 0ms
 ✓ src/lib/tagValidation.test.ts > validateTags > warmth (1–3, optional) > accepts the boundary values 1 and 3 0ms
 ✓ src/lib/tagValidation.test.ts > tagsAreValid > is true only when there are no validation errors 0ms
 ✓ src/lib/tagValidation.test.ts > tagsAreValid > is true with category only — null formality/warmth are valid (jewelry saves) 0ms
 ✓ src/lib/tagValidation.test.ts > tagsAreValid > is false when a required field is invalid 0ms

 Test Files  1 passed (1)
      Tests  15 passed (15)
```

## Artifact: Consumer test updates — `AddItem.test.tsx` and `ItemDetail.test.tsx` stay green

**What it proves:** Replacing the category `<input>` with a `<select>` is a
real behavior change (a select can only hold one of `CATEGORIES`, never
arbitrary free text; any *present* seed value is now normalized). The
pre-existing add/edit-flow tests that typed or asserted raw free text
(`"denim jacket"`, `"scarf"`, `"trucker jacket"`, a round-tripped `"shirt"`,
etc.) were updated in place — `selectOptions` instead of `type`, asserting the
selected/normalized taxonomy bucket instead of raw text — while every test's
original intent (a race-condition guard, a per-tile-failure guard, the N=1
happy path, the 429-cap interaction) is unchanged. See assumption A3.2 in
`18-assumptions-wardrobe-categories.md` for the full rationale.

**Why it matters:** These two files are not in the Task 2.0 Relevant Files
table, but leaving them red would violate task 2.7's own "confirm
`AddItem.test.tsx` stays green" requirement and the phase's "run the full
test suite" gate — this is necessary collateral of implementing 2.0 as
specified, not scope creep into other tasks.

**Command:**

```bash
cd frontend && npx vitest run src/routes/AddItem.test.tsx src/routes/ItemDetail.test.tsx
```

**Result summary:** Both suites are fully green — 24 + 10 = 34 tests, 0
failures.

```
 ✓ src/routes/ItemDetail.test.tsx (10 tests)
 ✓ src/routes/AddItem.test.tsx (24 tests)

 Test Files  2 passed (2)
      Tests  34 passed (34)
```

## Artifact: Full frontend suite, lint, and typecheck all green

**What it proves:** No regression was introduced anywhere else in the
frontend by the taxonomy select or the relaxed validation, and the widened
`TagInput.formality`/`warmth: number | null` type-checks cleanly through
every caller.

**Why it matters:** This is the parent-task-completion quality gate
(`docs/PRECOMMIT.md` / `AGENTS.md`) — the same gates the repo's pre-commit
hook enforces for any staged frontend file.

**Command:**

```bash
cd frontend && npm test -- --run && npm run lint && npx tsc -b
```

**Result summary:** 288/288 tests pass across 28 suites; ESLint reports 0
problems; `tsc -b` exits with no output (0 errors).

```
 Test Files  28 passed (28)
      Tests  288 passed (288)

> ensemble-frontend@0.0.1 lint
> eslint .
(no output — 0 problems)

$ npx tsc -b
(no output — 0 errors)
```

## Artifact: 100% coverage on the new/changed pure logic

**What it proves:** `categoryTaxonomy.ts` and `tagValidation.ts` — the
meaningful logic this task adds/changes — are fully exercised, not just
passing on the happy path.

**Why it matters:** `docs/TESTING.md` calls for testing frontend logic
thoroughly (as opposed to view plumbing); a coverage report is stronger
evidence than eyeballing the test file.

**Command:**

```bash
cd frontend && npx vitest run --coverage
```

**Result summary:** Both new/changed lib modules show 100% statements,
branches, functions, and lines; the coverage table below is the relevant
excerpt from the full run (which also shows no other file regressed).

```
File               | % Stmts | % Branch | % Funcs | % Lines
-------------------|---------|----------|---------|--------
 src/lib
  categoryTaxonomy.ts |   100 |      100 |     100 |   100
  tagValidation.ts    |   100 |      100 |     100 |   100
```

## Screenshot artifact — DEFERRED to manual verification

The spec's Unit 2 proof-artifact list calls for a screenshot of the add/edit
form with the category dropdown open, showing the taxonomy values. **This
implementation run is headless — no paired browser session (`claude-in-chrome`)
was available to capture a real screenshot**, matching the precedent already
recorded in this repo for the same situation
(`docs/specs/21-spec-manual-outfit-assembly/21-proofs/21-task-01-proofs.md`
through `21-task-03-proofs.md`). Rather than fabricate an image, this artifact
is explicitly deferred to manual/on-device verification before the spec's
validation phase treats it as closed (see assumption A3.3 in
`18-assumptions-wardrobe-categories.md`).

The visual behavior it would demonstrate is instead covered by the
machine-verified `TagForm.test.tsx` assertion that the category control
renders as a `<select>` listing exactly `['', ...CATEGORIES]` (the artifact
above). A reviewer can additionally run `npm run dev` (frontend) alongside
`./gradlew bootRun` (backend), open `/add` or an item's detail page, and click
the Category dropdown to see it directly.

## Reviewer Conclusion

The frontend now has its own single source of truth for the category
taxonomy (`categoryTaxonomy.ts`, mirroring the backend one-for-one), the
shared `TagForm` renders category as a constrained `<select>` that normalizes
any legacy stored value at edit time, and client-side validation now lets a
Jewelry/Accessory item save with no formality/warmth — closing the loop with
the backend relaxation shipped in Task 1.0. The full 288-test frontend suite,
ESLint, and `tsc -b` are all green, including the two pre-existing consumer
suites (`AddItem.test.tsx`, `ItemDetail.test.tsx`) that had to be updated in
place because they encoded the old free-text behavior this task deliberately
removes. The only artifact not machine-verified in this run is the dropdown
screenshot, explicitly flagged above as deferred rather than faked.
