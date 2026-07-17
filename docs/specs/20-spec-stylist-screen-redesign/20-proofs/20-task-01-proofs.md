# Task 01 Proofs — Six deferred design tokens defined

## Task Summary

This task adds the six stylist-screen design tokens that spec 06 deliberately
deferred (`--paper-sunk`, `--border`, `--ink-2`, `--placeholder`, `--pip-empty`,
`--accent-line`) to `:root` in `frontend/src/index.css`, using the exact handoff
values. It is a pure token addition — no component or behavior change — so the
drawer/tray/pip/rationale components built in later tasks read them directly.

## What This Task Proves

- All six tokens exist in `:root` with the exact values from
  `claude-design/design_handoff_stylist_maroon/theme-tokens.css`.
- No existing token value changed and `--on-accent` was not re-added (the change
  is additions-only).
- The full frontend suite and lint stay green (no behavior/regression).

## Evidence Summary

- `grep` returns the six tokens with the specified values.
- `git diff` shows 8 insertions, 0 deletions — additions only, all inside
  `:root`.
- `npm run test -- --run` → 95 passed; `npm run lint` → clean.

## Artifact: Tokens present with exact values

**What it proves:** The six tokens exist as the handoff specifies (FR U1-1).

**Why it matters:** Later components depend on these exact custom properties.

**Command:**

~~~bash
grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css
~~~

**Result summary:** Six matches with `#e9dfca` / `#d7cab2` / `#574a3d` /
`#a89a86` / `#d8cbb2` / `rgba(124, 40, 51, .22)`.

~~~text
22:  --paper-sunk: #e9dfca; /* drawer + flat-lay tray background (a step darker than paper) */
23:  --border: #d7cab2; /* stronger border than --hairline: inputs, tiles, buttons */
24:  --ink-2: #574a3d; /* secondary body text / rationale copy */
25:  --placeholder: #a89a86; /* input placeholder + search hint */
26:  --pip-empty: #d8cbb2; /* unfilled rating pip */
27:  --accent-line: rgba(124, 40, 51, .22); /* border on the user chat bubble */
~~~

## Artifact: Additions-only diff (no recolor regression)

**What it proves:** No existing token value was modified (FR U1-2).

**Why it matters:** Spec 06's maroon base must stay intact; this task only adds.

**Command:**

~~~bash
git diff --stat frontend/src/index.css
~~~

**Result summary:** `1 file changed, 8 insertions(+)` — zero deletions.

## Artifact: Suite + lint green

**What it proves:** No behavior change (FR U1-3).

**Why it matters:** A token addition must not regress any existing screen.

**Command:**

~~~bash
cd frontend && npm run test -- --run && npm run lint
~~~

**Result summary:** `Test Files 11 passed (11) · Tests 95 passed (95)`; lint
emitted no errors.

## Reviewer Conclusion

The six deferred tokens are defined exactly per the handoff, the change is
strictly additive, and the suite/lint remain green — Unit 1 is complete and safe.
