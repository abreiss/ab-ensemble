# 06-tasks-maroon-recolor.md

> Task plan for `06-spec-maroon-recolor.md`. Each parent task mirrors a Demoable
> Unit of Work from the spec. This is a pure token recolor: per `docs/TESTING.md`
> frontend view plumbing is lightly tested, so **no new test files are added** —
> the "test artifact" for each requirement is a reproducible grep/diff assertion
> plus the existing suite staying green.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/src/index.css` | The single `:root` token block + the one hard-coded `color: #fff` (line ~169). All recolor edits happen here. |
| `frontend/index.html` | Holds the `theme-color` meta (line 6), which duplicates the paper color and is not a CSS variable — edited separately. |
| `frontend/src/App.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/components/TagForm.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/components/DescriptorChips.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/lib/tagValidation.test.ts` | Existing suite — must stay green; not modified. |
| `frontend/src/api/items.test.ts` | Existing suite — must stay green; not modified. |
| `frontend/src/api/style.test.ts` | Existing suite — must stay green; not modified. |
| `frontend/src/routes/WardrobeGrid.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/routes/AddItem.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/routes/ItemDetail.test.tsx` | Existing suite — must stay green; not modified. |
| `frontend/src/routes/Stylist.test.tsx` | Existing suite — must stay green; not modified. |

### Notes

- No new tests: a pure recolor changes no logic and no test asserts a color.
  Verification is grep/diff assertions + the full existing suite staying green
  (`docs/TESTING.md`: frontend view plumbing is lightly tested).
- Run frontend tests with `cd frontend && npm test -- --run` (Vitest). Lint with
  `cd frontend && npm run lint` (ESLint).
- Edit the token values in place in the existing single `:root` block — there is
  no CSS-module split (per spec Repository Standards).
- Do not touch `.tsx` markup, class names, `--danger*`, or non-color tokens.
- Conventional commit, e.g. `feat: maroon/beige theme (token recolor)`, on the
  `feat/maroon-recolor` branch.

## Tasks

### [x] 1.0 Repalette the design system (cobalt → maroon/beige base tokens)

Replace the 7 base color tokens in the single `:root` block of
`frontend/src/index.css` with the handoff maroon/beige values, and update the
`theme-color` meta in `frontend/index.html`. No component markup, class names,
`--danger*`, or non-color tokens change. Recolor propagates automatically to all
four screens (`/`, `/add`, `/item/:id`, `/style`) via the ~53 existing token
references and `color-mix()` shades.

Maps to: Spec Unit 1 (FRs: 7-token replacement, danger/non-color unchanged,
theme-color meta, no markup change), Success Metrics 1, 2 (partial), 5.

#### 1.0 Proof Artifact(s)

- CLI: `grep -nE "#2540ff|#e7ebff|#f7f5f0|#fffefb|#1c1b19|#8a857b|#e0dcd2" frontend/src/index.css`
  returns **no matches** — demonstrates no cobalt/cream base values remain.
- CLI: `grep -niE "#f3ecdd|#fcf8ef|#33271f|#8f8272|#e3d8c4|#7c2833|#ecd9d3" frontend/src/index.css`
  returns **7 matches** (one per replaced token) — demonstrates the maroon/beige
  values are in place.
- CLI: `grep -niE "theme-color" frontend/index.html` shows `content="#F3ECDD"` —
  demonstrates the browser chrome color was updated.
- Diff: `git diff frontend/src/index.css frontend/index.html` shows only the 7
  token value lines + the meta line changed (no markup/class/behavior edits) —
  demonstrates the change is a pure token swap.
- Screenshots: `/`, `/add`, `/item/:id`, `/style` captured from the Vite dev
  server (`cd frontend && npm run dev`) show the maroon/beige palette across all
  existing screens. (Without the backend running, data-driven screens show
  empty/error states; palette is still visible on chrome, buttons, banners.)

#### 1.0 Tasks

- [x] 1.1 In `frontend/src/index.css` `:root` (lines 10–16), replace the 7 base
  color token values exactly: `--paper: #f7f5f0` → `#f3ecdd`; `--paper-raised:
  #fffefb` → `#fcf8ef`; `--ink: #1c1b19` → `#33271f`; `--muted: #8a857b` →
  `#8f8272`; `--hairline: #e0dcd2` → `#e3d8c4`; `--accent: #2540ff` → `#7c2833`;
  `--accent-soft: #e7ebff` → `#ecd9d3`. Leave `--danger`, `--danger-soft`, and
  every non-color token (type, spacing, radii) untouched.
- [x] 1.2 In `frontend/index.html` (line 6), change the `theme-color` meta
  `content` from `#F7F5F0` to `#F3ECDD`.
- [x] 1.3 Verify no stale base values: run
  `grep -nE "#2540ff|#e7ebff|#f7f5f0|#fffefb|#1c1b19|#8a857b|#e0dcd2" frontend/src/index.css`
  (expect no matches) and
  `grep -niE "#f3ecdd|#fcf8ef|#33271f|#8f8272|#e3d8c4|#7c2833|#ecd9d3" frontend/src/index.css`
  (expect 7 matches). Confirm `git diff` touches only the 7 value lines + the meta
  line — no markup, class, or `--danger*` changes.
- [x] 1.4 Start the Vite dev server (`cd frontend && npm run dev`) and capture
  screenshots of `/`, `/add`, `/item/:id`, and `/style` showing the maroon/beige
  palette on app chrome, buttons, and banners. Save them as the Unit 1 proof
  artifacts.

### [x] 2.0 Tokenize the on-accent color and keep the suite green

Add `--on-accent: #f6ecd9;` to `:root` (with a short purpose comment) and replace
the single hard-coded `color: #fff` on the `.btn-add, .btn-primary` rule
(line ~169) with `color: var(--on-accent)`. Do **not** add the six stylist-only
tokens (deferred to issue #7). Then confirm the full frontend suite stays green
with no test modifications — proving neither unit changed behavior.

Maps to: Spec Unit 2 (FRs: add `--on-accent`, tokenize `#fff`, no other handoff
tokens), Success Metrics 2 (bare-white), 3 (correct tokens present / stylist
tokens absent), 4 (behavior unchanged).

#### 2.0 Proof Artifact(s)

- CLI: `grep -n "#fff" frontend/src/index.css` returns **no bare-white match** —
  demonstrates the hard-coded button color was removed.
- CLI: `grep -n -- "--on-accent" frontend/src/index.css` shows **both** the
  `:root` definition and the `.btn-add, .btn-primary` usage — demonstrates the
  color was tokenized and the token is reused, not inlined.
- CLI: `grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css`
  returns **no matches** — demonstrates the six stylist-only tokens were not
  added (correctly deferred to #7).
- Test: `cd frontend && npm test -- --run` passes with all 10 existing test
  suites green and **zero test files modified** — demonstrates no behavioral
  change (no test asserts a color).
- CLI: `cd frontend && npm run lint` reports no new errors — demonstrates the CSS
  change respects repo lint standards.

#### 2.0 Tasks

- [x] 2.1 In `frontend/src/index.css` `:root`, add `--on-accent: #f6ecd9;` (place
  it with the other color tokens, e.g. after `--accent-soft`) with a short purpose
  comment such as `/* cream text/icons on maroon fill */`. Do not add
  `--paper-sunk`, `--border`, `--ink-2`, `--placeholder`, `--pip-empty`, or
  `--accent-line` (those belong to issue #7).
- [x] 2.2 In `frontend/src/index.css`, change the `.btn-add, .btn-primary` rule
  (line ~169) from `color: #fff;` to `color: var(--on-accent);`.
- [x] 2.3 Verify tokenization: `grep -n "#fff" frontend/src/index.css` (expect no
  bare-white match); `grep -n -- "--on-accent" frontend/src/index.css` (expect the
  definition + the button usage); and
  `grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css`
  (expect no matches — the six stylist tokens stay deferred).
- [x] 2.4 Run `cd frontend && npm test -- --run`; confirm all 10 existing suites
  pass and that `git status` shows **no `*.test.*` files modified** (behavior
  unchanged, no test edits).
- [x] 2.5 Run `cd frontend && npm run lint`; confirm no new lint errors from the
  CSS change.
