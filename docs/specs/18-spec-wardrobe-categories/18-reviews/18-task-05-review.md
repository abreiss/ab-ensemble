# Backup Review — Task 5.0: Remediation: omit null formality/warmth from item API requests

**Commit reviewed:** `9050b4bdd886cae43227cd78260493e8bedb2672`
**Reviewer role:** independent, read-only backup check (fast check already passed)

## Verdict

**PASS** — the fix closes the exact gap identified in
`18-reviews/18-task-02-review.md`, the regression test is genuine (verified it
fails without the fix), the `updateTags` JSON path is correctly confirmed
unaffected, and the proof file's evidence matches the code and test output
exactly. No new problems introduced.

## What was checked

- Full diff of `9050b4bdd886cae43227cd78260493e8bedb2672` (`git show --stat`
  and per-file diff) against the 5.1/5.2/5.3 sub-tasks and the original
  Task 2.0 review finding.
- Current state of `frontend/src/api/items.ts` and
  `frontend/src/api/items.test.ts` read directly.
- The cited `WardrobeControllerTest.createItem_jewelryWithoutFormalityOrWarmth_returns201`
  test, read directly to confirm it exists and asserts what the proof claims.
- Independently ran `npx vitest run src/api/items.test.ts` (22/22 pass,
  including the new regression case), `npm test -- --run` (29 files / 319
  tests, matches proof exactly), `npx tsc -b` (clean), and `npm run lint`
  (clean) — tree was clean apart from the untracked `18-reviews/` directory,
  so running the suite was safe.
- `git status`/`git diff --stat` scope: only `items.ts`, `items.test.ts`, the
  task list, the assumptions log, and the proof file changed — no stray edits.

## Does the fix close the original finding?

Yes. Before:

```ts
form.append('formality', String(tags.formality))
...
form.append('warmth', String(tags.warmth))
```

After:

```ts
appendOptionalNumber(form, 'formality', tags.formality)
...
appendOptionalNumber(form, 'warmth', tags.warmth)
```

where `appendOptionalNumber` is a new, minimal, numeric sibling of the
existing `appendOptional` helper — appends only when
`value !== null && value !== undefined`, otherwise the key is omitted
entirely. This mirrors the pattern already used for
`primaryColor`/`secondaryColor`/`pattern` in the same function, exactly as
the original review recommended. The happy-path call sites are unaffected:
`fd.get('formality') === '3'` / `fd.get('warmth') === '2'` still hold for the
existing "POSTs photo + tag fields" test (verified in the live test run, not
just the proof's prose).

`updateTags` was audited, not touched — confirmed correct by inspection: it
builds its PUT body via `JSON.stringify(tags)`, so `formality: null`/
`warmth: null` already serialize as real JSON `null` (never the string
`"null"`), which was never the affected path. Leaving it unchanged is the
right call, not an oversight.

## Does the regression test genuinely fail without the fix?

Yes, judged directly from the diff plus the semantics of the removed code.
The new test in `items.test.ts`:

```ts
await createItem(photo, { category: 'Jewelry', formality: null, warmth: null })
const fd = lastCall()[1].body as FormData
expect(fd.has('formality')).toBe(false)
expect(fd.has('warmth')).toBe(false)
```

Against the pre-fix code (`form.append('formality', String(tags.formality))`
with `tags.formality === null`), `String(null)` evaluates to the literal
string `"null"`, and `form.append('formality', 'null')` unconditionally adds
the key — so `fd.has('formality')` would be `true`, failing
`expect(...).toBe(false)`. This is not a vacuous or tautological assertion;
it directly targets the removed line. The proof file's pasted RED-run output
(`expected true to be false`, 1 failed / 21 passed) is consistent with this
reasoning, and re-running the test suite post-fix independently confirms
22/22 pass now.

## Does the proof file's evidence match the code, and is it honest?

Yes on both counts.

- The pasted diff of `items.ts` in the proof file matches the actual diff in
  the commit verbatim (helper doc-comment, function body, both call-site
  swaps).
- The claimed `WardrobeControllerTest.createItem_jewelryWithoutFormalityOrWarmth_returns201`
  test exists at the cited location and asserts exactly what the proof
  states: params are omitted (not sent as `"null"`), `201` returned,
  `TagRequest.formality()`/`warmth()` bind to `null`. No backend change was
  needed, and none was made — correctly scoped.
- Test-count and coverage claims (22 tests in `items.test.ts`, 100%
  statements/branch/functions/lines on `items.ts`, 29 files / 319 tests for
  the full suite) all reproduced exactly on independent re-run.
- The proof honestly and directly references the original finding
  (`18-reviews/18-task-02-review.md`) by name, both in the proof file and the
  commit message, and does not overstate scope (explicitly notes `updateTags`
  was audited and left alone, and that no backend change was made).
- All three sub-tasks (5.1/5.2/5.3) are checked `[x]` in
  `18-tasks-wardrobe-categories.md`, consistent with the commit.

## New problems introduced?

None found. The change is additive and narrowly scoped (one new private
helper, two call-site swaps, one new test); no other logic in `items.ts` was
touched; lint/typecheck/full suite are all green.
