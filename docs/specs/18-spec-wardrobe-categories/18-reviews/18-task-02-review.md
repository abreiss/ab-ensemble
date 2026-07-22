# Backup Review — Task 2.0: `TagForm` category `<select>` + relaxed client validation

**Commit reviewed:** `515f66d97cafe9e2e5c2e98ddecf5d894c4243ae`
**Reviewer role:** independent, read-only backup check (fast check already passed)

## Verdict

**FAIL** — the literal 2.1–2.7 sub-tasks are all correctly implemented and well
tested, but the parent task's headline acceptance criterion — *"let jewelry
save from the form (null warmth/formality)"* — does not actually work
end-to-end for the create/add path. A real user adding a new Jewelry item with
no formality/warmth via `/add` will hit the backend with a malformed request,
because `frontend/src/api/items.ts` (untouched by this commit) still
unconditionally stringifies `formality`/`warmth`, turning `null` into the
literal 4-character string `"null"` instead of omitting the field.

## What was checked

- Diff (`git show 515f66d97cafe9e2e5c2e98ddecf5d894c4243ae --stat` + full diff)
  against the Task 2.0 sub-tasks (2.1–2.7) and Unit 2 of the spec
  (`18-spec-wardrobe-categories.md`, "Unit 2" section).
- Proof file `18-proofs/18-task-02-proofs.md` against the actual code and test
  diffs (not just the prose).
- Downstream consumers of the widened `TagInput.formality`/`warmth: number | null`
  type (`frontend/src/api/items.ts`, `AddItem.tsx`) since a type-widening change
  is exactly the kind of change whose blast radius extends past the files a
  task lists as "relevant."
- Working tree is clean except this review directory; no need to avoid running
  read-only `git show`/`grep` against history.

## What is correct (matches proof, well tested)

- `frontend/src/lib/categoryTaxonomy.ts` (new): ordered 8-value `CATEGORIES`,
  `Category` type, and a pure, never-throwing `normalizeCategory` with a
  starter synonym map. `categoryTaxonomy.test.ts` (22 cases) covers every
  canonical value, representative legacy synonyms, casing/whitespace, and
  null/undefined/blank → `Other`. Mirrors the backend `CategoryTaxonomy` from
  Task 1.0 as intended (two independent sources of truth, per assumption A1.4).
- `TagForm.tsx`: the free-text category `<input>` is replaced with a `<select>`
  built from `CATEGORIES` plus a `—` placeholder, exactly matching the existing
  `formality`/`warmth` `<select>` markup. `toDraftCategory` normalizes any
  present seed value via `normalizeCategory` so the control never renders blank
  or off-list; `toTagInput` no longer force-casts `formality`/`warmth` to
  `number` and instead passes `draft.formality`/`draft.warmth` through as-is,
  which is the correct fix for the type change.
- `tagValidation.ts`: `formality`/`warmth` are now optional (`null` valid,
  a supplied out-of-range value still rejected); `category` still required.
  Logic reads correctly: `fields.formality !== null && (out of range)`.
  `tagValidation.test.ts` exercises null-is-valid, both boundaries, and
  out-of-range for both fields — 100% branch coverage is a reasonable claim
  for this file (all branches are simple and covered).
- `types/item.ts`: `TagInput.formality`/`warmth` widened to `number | null`,
  consistent with the relaxed validation.
- `TagForm.test.tsx` new tests are not trivial: they assert the exact rendered
  `<select>` option list (`['', ...CATEGORIES]`), assert `onChange`/`onSubmit`
  payloads carry `formality: null, warmth: null` for a Jewelry selection with
  nothing else touched, and assert edit-time normalization (`"chinos"` →
  `"Bottom"`). These are genuine behavior assertions, not smoke tests.
- `AddItem.test.tsx` / `ItemDetail.test.tsx` updates are faithful conversions
  from `user.type(...)` free text to `user.selectOptions(...)` taxonomy values;
  each test's original intent (race-condition guard, per-tile failure, N=1
  happy path, 429-cap interaction) is preserved, matching assumption A3.2's
  rationale in the proof file.
- No secrets, no fabricated command output detected — the pasted Vitest runs
  in the proof file correspond exactly to the tests actually present in the
  diff (counts and test names line up test-for-test).
- The proof file honestly flags the screenshot artifact as deferred (headless
  environment, no browser session) rather than fabricating one, consistent
  with prior precedent cited in the repo.

## The blocking gap

`frontend/src/api/items.ts` (last touched in a prior, unrelated commit —
`5b3f448` — and **not part of this commit's diff**) builds the create-item
multipart body like this:

```ts
function tagFormData(photo: File, tags?: TagInput): FormData {
  const form = new FormData()
  form.append('photo', photo)
  if (tags) {
    form.append('category', tags.category)
    appendOptional(form, 'primaryColor', tags.primaryColor)
    appendOptional(form, 'secondaryColor', tags.secondaryColor)
    form.append('formality', String(tags.formality))   // <-- unconditional
    appendOptional(form, 'pattern', tags.pattern)
    form.append('warmth', String(tags.warmth))          // <-- unconditional
    ...
```

Before this task, `formality`/`warmth` were always non-null (required by
client validation), so `String(tags.formality)` always produced a real digit
string. Task 2.0 deliberately makes `formality: null, warmth: null` a normal,
reachable, submittable state from the form for a Jewelry/Accessory item — that
is its stated purpose. But `tagFormData` was never updated to match: for a
null value, `String(null)` is the **literal string `"null"`**, which gets
appended as the form field's value — the field is not omitted, unlike every
other optional field in the same function (which correctly uses
`appendOptional` to skip null/undefined).

On the backend, `WardrobeControllerTest`'s own Task-1.0 proof for "Jewelry has
no formality/warmth" (`src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java`,
around line 117–129) explicitly **omits** the `formality`/`warmth` request
params entirely to get a null-bound `TagRequest` — it never sends the literal
string `"null"`. `TagRequest.formality`/`warmth` are typed `Integer` with
`@Min`/`@Max` (which pass on a truly-absent/null-bound value); binding the
literal text `"null"` to an `Integer` via Spring's data binder is a type
conversion failure (`Integer.valueOf("null")` throws), not a null-bind — i.e.
this is very likely to surface as a `400` from the real endpoint, not a
successful jewelry save.

**Net effect:** the exact scenario Unit 2 is supposed to enable — add a new
Jewelry item via the camera flow, leave formality/warmth blank, tap "Save
all" — is very likely broken end-to-end in the running app, despite:
- `TagForm.test.tsx` passing (it only asserts the `TagInput` object shape,
  `createItem` is not invoked from that test).
- `AddItem.test.tsx` passing (it mocks `createItem` entirely — `vi.mock('../api/items', ...)`
  — so the real `tagFormData` serialization is never exercised by any test
  touched in this commit; none of the updated `AddItem.test.tsx` cases leave
  formality/warmth unset, they all select concrete values).
- The pre-existing `frontend/src/api/items.test.ts` also never exercises a
  null `formality`/`warmth` through `createItem` (checked: only the "omits
  null/undefined optional fields" test exists, and it doesn't touch
  formality/warmth at all).

The edit path (`updateTags`, used by `ItemDetail`) is **not** affected — it
JSON-serializes the `TagInput` (`JSON.stringify(tags)`), so `null` survives
correctly as JSON `null`. Only the multipart **create** path is broken.

This is precisely the kind of gap a fast check (tests/lint/tsc all green) is
structurally unable to catch: it lives at a boundary between two files, only
one of which the commit touched, and the mock boundary in `AddItem.test.tsx`
hides it entirely.

## Recommendation

Before this task is considered done, fix `tagFormData` in
`frontend/src/api/items.ts` to omit `formality`/`warmth` when null (mirror the
`appendOptional` pattern already used for `primaryColor`/`secondaryColor`/
`pattern` in the same function), and add a regression test to
`items.test.ts` (`createItem` with `formality: null, warmth: null` → the
multipart body has neither key). This is a small, contained fix, but it is
required for the unit's stated purpose to actually hold, and `items.ts` is not
listed as a Relevant File for Task 2.0 (nor for 3.0/4.0), so nothing downstream
in the task list is currently slated to catch or fix it.

## Non-blocking observations

- None beyond the above; the rest of the diff is clean, idiomatic, and
  consistent with existing patterns in the file (e.g. `toDraftCategory`
  mirrors the `formality ?? null` seeding pattern already used for
  formality/warmth).
