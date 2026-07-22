# Task 05 Proofs - Remediation: omit null formality/warmth from item API requests

## Task Summary

A read-only backup reviewer FAILED Task 2.0
(`18-reviews/18-task-02-review.md`) with a blocking gap: `frontend/src/api/items.ts`
— a file Task 2.0's commit never touched — builds the create-item multipart
body with `form.append('formality', String(tags.formality))` and
`form.append('warmth', String(tags.warmth))`, unconditionally. Task 2.0
deliberately made `formality: null, warmth: null` a normal, reachable,
submittable state for a Jewelry/Accessory item — but for a `null` value,
`String(null)` is the literal 4-character string `"null"`, which was sent as
the field's value instead of the field being omitted (unlike every other
optional field in the same function, which correctly uses `appendOptional`).
This task fixes exactly that gap: `tagFormData` now omits `formality`/`warmth`
when null/undefined, mirroring the existing `appendOptional` pattern.

## What This Task Proves

- The reviewer-flagged scenario — a real user adding a Jewelry item via
  `/add` with no formality/warmth, tapping "Save all" — now sends a multipart
  body with **no** `formality`/`warmth` key at all, not the string `"null"`.
- The fix is scoped to exactly the one function with the bug (`tagFormData`,
  shared by `tagPreview` and `createItem`); the audited `updateTags` path was
  confirmed unaffected (it already JSON-serializes `TagInput`, so `null`
  already survives as real JSON `null`) and was left unchanged.
- The backend already accepts the fixed request shape: `TagRequest.formality`/
  `warmth` were relaxed to nullable in Task 1.0, and the existing
  `WardrobeControllerTest.createItem_jewelryWithoutFormalityOrWarmth_returns201`
  test already asserts that *omitting* the params (not sending `"null"`)
  binds to `null` and returns `201`. No backend change was needed.
- The happy-path case (real numeric formality/warmth) is unchanged — the
  existing "POSTs photo + tag fields" test still asserts
  `fd.get('formality') === '3'` / `fd.get('warmth') === '2'`.

## Evidence Summary

- A new regression test in `items.test.ts` was run **RED** first (failed:
  `fd.has('formality')` was `true`, not `false`), then made **GREEN** by
  replacing the two unconditional `form.append(...String(...))` calls with a
  new `appendOptionalNumber` helper (a numeric sibling of the existing
  `appendOptional`).
- The full frontend suite (29 files, 319 tests — up from 318 before this
  task), ESLint, and `tsc -b` are all green.
- `items.ts` — the changed module — holds 100% statement/branch/function/line
  coverage.
- `git diff` on `items.ts` shows a minimal, additive change: a new helper
  function plus swapping two call sites; no other logic touched.

## Artifact: RED — regression test fails against the pre-fix `tagFormData`

**What it proves:** Before the fix, a Jewelry `TagInput` with
`formality: null, warmth: null` produced a multipart body where
`fd.has('formality')` was `true` (the literal string `"null"` was present) —
proving the test exercises the exact reviewer-flagged defect, not a vacuous
assertion.

**Why it matters:** Confirms the RED step of the RED→GREEN→REFACTOR cycle
actually ran, per the repo's strict-TDD mandate (AGENTS.md), directly against
the reviewer's finding.

**Command:**

```bash
cd frontend && npx vitest run src/api/items.test.ts
```

**Result summary:** 1 of 22 tests failed — the new
`omits null formality/warmth from the multipart body (Jewelry has neither)`
case — with every pre-existing test (including the happy-path numeric
formality/warmth case) still green.

```
FAIL  src/api/items.test.ts > items API client > createItem > omits null formality/warmth from the multipart body (Jewelry has neither)
AssertionError: expected true to be false // Object.is equality

- Expected
+ Received

- false
+ true

 ❯ src/api/items.test.ts:232:35
    230|
    231|       const fd = lastCall()[1].body as FormData
    232|       expect(fd.has('formality')).toBe(false)
       |                                   ^
    233|       expect(fd.has('warmth')).toBe(false)
    234|       expect(fd.get('category')).toBe('Jewelry')

 Test Files  1 failed (1)
      Tests  1 failed | 21 passed (22)
```

## Artifact: GREEN — `items.test.ts` after the `tagFormData` fix

**What it proves:** After adding `appendOptionalNumber` and swapping the two
unconditional `form.append('formality'/'warmth', String(...))` calls to use
it, all 22 tests in the file pass — the new null-omission case and every
pre-existing case (including the real-value happy path).

**Why it matters:** Direct proof the fix closes the reviewer's gap without
touching any other behavior in the file.

**Command:**

```bash
cd frontend && npx vitest run src/api/items.test.ts
```

**Result summary:** All 22 tests pass.

```
 ✓ src/api/items.test.ts (22 tests) 6ms

 Test Files  1 passed (1)
      Tests  22 passed (22)
```

## Artifact: The fix itself — minimal, mirrors the existing `appendOptional` pattern

**What it proves:** The change is exactly what the remediation scope called
for: a numeric sibling of `appendOptional`, applied only at the two buggy
call sites, with no other logic touched.

**Why it matters:** Small, contained fixes are easier to review and carry
lower regression risk than a broader rewrite; this also matches the
reviewer's own recommendation verbatim.

**Command:**

```bash
git diff frontend/src/api/items.ts
```

```diff
+/**
+ * Appends an optional numeric tag field only when it has a real (non-null)
+ * value. `formality`/`warmth` are nullable (e.g. a Jewelry/Accessory item has
+ * neither) — omitting the field lets the backend's nullable `TagRequest`
+ * fields bind to `null`, instead of sending the literal string `"null"`.
+ */
+function appendOptionalNumber(form: FormData, key: string, value: number | null | undefined): void {
+  if (value !== null && value !== undefined) {
+    form.append(key, String(value))
+  }
+}
+
 /** Builds the multipart body shared by tag-preview and create. */
 function tagFormData(photo: File, tags?: TagInput): FormData {
   const form = new FormData()
@@ -50,9 +62,9 @@ function tagFormData(photo: File, tags?: TagInput): FormData {
     form.append('category', tags.category)
     appendOptional(form, 'primaryColor', tags.primaryColor)
     appendOptional(form, 'secondaryColor', tags.secondaryColor)
-    form.append('formality', String(tags.formality))
+    appendOptionalNumber(form, 'formality', tags.formality)
     appendOptional(form, 'pattern', tags.pattern)
-    form.append('warmth', String(tags.warmth))
+    appendOptionalNumber(form, 'warmth', tags.warmth)
```

## Artifact: `updateTags` audited and confirmed unaffected (no change made)

**What it proves:** The remediation scope required auditing the tag-update
path for the same bug. `updateTags` builds its request body via
`JSON.stringify(tags)` (not `FormData`), so `formality: null`/`warmth: null`
already serialize as real JSON `null` — never the string `"null"`. This is
already covered by the pre-existing `updateTags` test, which round-trips
`sampleTags` (including numeric formality/warmth) through `JSON.parse` and
asserts equality.

**Why it matters:** Confirms the audit was actually performed (not assumed),
and that no unnecessary change was made to a path that was never broken.

**Command:**

```bash
sed -n '103,114p' frontend/src/api/items.ts
```

**Result summary:** `updateTags` is unchanged — it still `JSON.stringify`s the
full `TagInput` (including a null formality/warmth) as the PUT body.

```ts
/** Replace an item's tags (JSON). */
export async function updateTags(id: string, tags: TagInput): Promise<Item> {
  const response = ensureOk(
    await authedFetch(`${BASE}/${id}/tags`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(tags),
    }),
    'Update tags',
  )
  return (await response.json()) as Item
}
```

## Artifact: Backend already accepts the fixed request shape — no backend change needed

**What it proves:** `TagRequest.formality`/`warmth` were already relaxed to
nullable in Task 1.0 (`@Min`/`@Max` only, `@NotNull` dropped), and the
existing `WardrobeControllerTest` Jewelry-create case already asserts the
*correct* (post-fix) request shape — omitted params, not the string
`"null"` — binds to `null` and returns `201`.

**Why it matters:** Confirms the frontend fix and the backend contract now
actually agree end-to-end, closing the reviewer's "very likely a `400` from
the real endpoint" concern, without needing to start the backend or change
any backend code.

**Command:**

```bash
sed -n '115,131p' src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java
```

**Result summary:** The existing test omits `formality`/`warmth` params
entirely (exactly what the fixed `tagFormData` now sends) and asserts `201`
plus a null-bound `TagRequest`.

```java
@Test
void createItem_jewelryWithoutFormalityOrWarmth_returns201() throws Exception {
	// Jewelry has no formality/warmth — TagRequest must accept a null for each
	// (only category remains required), so the item still saves.
	when(service.create(any(), any())).thenReturn(response("new-id"));

	mockMvc.perform(multipart("/api/items")
			.file(photoPart())
			.param("category", "Jewelry")
			.param("primaryColor", "gold"))
		.andExpect(status().isCreated());

	ArgumentCaptor<TagRequest> captor = ArgumentCaptor.forClass(TagRequest.class);
	verify(service).create(captor.capture(), any());
	assertThat(captor.getValue().formality()).isNull();
	assertThat(captor.getValue().warmth()).isNull();
}
```

## Artifact: Coverage on the changed module

**What it proves:** `items.ts` — the file this task modifies — is fully
exercised by the test suite.

**Command:**

```bash
cd frontend && npx vitest run --coverage src/api/items.test.ts
```

**Result summary:** `items.ts` shows 100% statements/branches/functions/lines.

```
File     | % Stmts | % Branch | % Funcs | % Lines
---------|---------|----------|---------|--------
items.ts |     100 |      100 |     100 |     100
```

## Artifact: Full frontend suite, lint, and typecheck are green

**What it proves:** The fix integrates cleanly with the rest of the
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

**Result summary:** 29 test files / 319 tests pass (up from 318 before this
task — 1 new regression case in `items.test.ts`). ESLint and `tsc -b` both
exit clean with no output.

```
 Test Files  29 passed (29)
      Tests  319 passed (319)
```

```
> ensemble-frontend@0.0.1 lint
> eslint .
```

(`tsc -b` produced no output, confirming a clean typecheck.)

## Artifact: Only the expected files changed

**What it proves:** The implementation touched exactly the files the
remediation scope identifies — no stray edits to unrelated modules, and the
backend was not touched (confirmed unnecessary above).

**Command:**

```bash
git status --short
git diff --stat
```

**Result summary:** Modified: the task file, the assumptions log,
`items.ts`, `items.test.ts`. The untracked `18-reviews/` directory is the
read-only background reviewer's own artifact, left unstaged per instruction.

```
 M docs/specs/18-spec-wardrobe-categories/18-assumptions-wardrobe-categories.md
 M docs/specs/18-spec-wardrobe-categories/18-tasks-wardrobe-categories.md
 M frontend/src/api/items.test.ts
 M frontend/src/api/items.ts
?? docs/specs/18-spec-wardrobe-categories/18-reviews/
```

## Reviewer Conclusion

The exact gap the backup reviewer flagged — a Jewelry item's null
formality/warmth being serialized as the literal string `"null"` instead of
omitted — is fixed with a minimal, contained change that mirrors the
codebase's own `appendOptional` convention. The RED→GREEN cycle is directly
evidenced (the regression test fails before the fix, passes after), the
changed module holds 100% coverage, the audited `updateTags` path is
confirmed unaffected, and the existing backend controller test independently
confirms the fixed request shape is exactly what the backend already
expects. The full 319-test frontend suite, ESLint, and `tsc -b` are all
green, closing Task 2.0's blocking gap without any backend change.
