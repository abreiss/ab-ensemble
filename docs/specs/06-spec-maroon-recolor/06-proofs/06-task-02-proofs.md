# Task 02 Proofs — Tokenize the on-accent color, suite stays green

## Task Summary

This task removes the single hard-coded color in the stylesheet by registering a
new palette token `--on-accent: #f6ecd9` and pointing the filled-button rule at
it (`color: #fff` → `color: var(--on-accent)`). The six stylist-only tokens are
deliberately **not** added (deferred to issue #7). The full frontend test suite
stays green with zero test edits, proving neither recolor unit changed behavior.

## What This Task Proves

- No bare `#fff` remains in `frontend/src/index.css`.
- `--on-accent` is defined once in `:root` and consumed by `.btn-add,
  .btn-primary` — tokenized, not inlined.
- The six stylist-only tokens (`--paper-sunk`, `--border`, `--ink-2`,
  `--placeholder`, `--pip-empty`, `--accent-line`) are absent.
- All 10 existing test suites (72 tests) pass with no test files modified.
- ESLint passes clean.
- The filled button renders cream-on-maroon (high-contrast light-on-dark).

## Evidence Summary

- `grep "#fff"` → no matches.
- `grep -- "--on-accent"` → definition (line 17) + usage (line 170).
- `grep` for the six stylist tokens → no matches.
- `npm test -- --run` → **10 files / 72 tests passed**; no `*.test.*` modified.
- `npm run lint` → exit 0.

## Artifact: Bare white removed, color tokenized

**What it proves:** The one hard-coded color is gone and replaced by a reused
token.

**Why it matters:** Spec Unit 2 + Success Metric 2 require no bare `#fff` and a
registered `--on-accent` used by the button.

**Commands:**

```bash
grep -n "#fff" frontend/src/index.css
grep -n -- "--on-accent" frontend/src/index.css
```

**Result summary:** No bare `#fff`; `--on-accent` defined at line 17 and consumed
at line 170.

```
# grep -n "#fff"      -> (no matches)
# grep -n -- "--on-accent"
17:  --on-accent: #f6ecd9; /* cream text/icons on maroon fill */
170:  color: var(--on-accent);
```

## Artifact: Six stylist-only tokens correctly deferred

**What it proves:** Scope stayed narrow — issue #7's tokens were not added.

**Why it matters:** Spec Non-Goal 1 + Success Metric 3 require these six tokens
to be absent here.

**Command:**

```bash
grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css
```

**Result summary:** No matches — none of the six tokens are present.

```
(no matches)
```

## Artifact: Full test suite green, zero test edits

**What it proves:** Neither recolor unit changed behavior.

**Why it matters:** Spec Unit 2 + Success Metric 4 require the suite to pass with
no test modifications (no test asserts a color).

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** 10 test files, 72 tests passed; `git status` shows no
`*.test.*` files modified. (The `act(...)` lines are pre-existing React testing
warnings, not failures.)

```
 ✓ src/App.test.tsx (6 tests)
 ✓ src/routes/WardrobeGrid.test.tsx (4 tests)
 ✓ src/components/DescriptorChips.test.tsx (4 tests)
 ✓ src/components/TagForm.test.tsx (6 tests)
 ✓ src/routes/Stylist.test.tsx (3 tests)
 ✓ src/routes/ItemDetail.test.tsx (7 tests)
 ✓ src/lib/tagValidation.test.ts (14 tests)
 ✓ src/routes/AddItem.test.tsx (8 tests)

 Test Files  10 passed (10)
      Tests  72 passed (72)
```

## Artifact: Lint clean

**What it proves:** The CSS change respects repo lint standards.

**Why it matters:** Repository quality gate (`npm run lint`).

**Command:**

```bash
cd frontend && npm run lint
```

**Result summary:** ESLint exited 0 with no errors.

## Artifact: Cream-on-maroon button render

**What it proves:** `var(--on-accent)` resolves to cream on the maroon fill —
the button label is light-on-dark, comfortably high contrast.

**Why it matters:** Success Metric 5 (button cream-on-maroon meets WCAG AA); the
tokenization must not visually regress the button.

**Artifact path:** `screenshots/05-on-accent-button.png`

**Result summary:** The `+ Add` button shows cream text `#f6ecd9` on maroon
`#7c2833` — a high-contrast pairing, visually equivalent to the previous white
but now driven by a palette token.

![Add-item screen showing the maroon + Add button with cream on-accent label text](screenshots/05-on-accent-button.png)

## Reviewer Conclusion

The one hard-coded color is now a reused `--on-accent` token, the six stylist-only
tokens remain deferred to issue #7, ESLint is clean, and the full 72-test suite
passes with no test changes — confirming the recolor is behavior-neutral. Combined
with Task 1.0, the entire app is repaletted to maroon/beige by token edits alone.
