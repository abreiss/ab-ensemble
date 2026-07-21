# Backup Review — Parent Task 1.0 (`/assemble` route, mannequin scaffold, entry points)

**Reviewer role:** independent read-only double-check of a worker's completed task
and its passing fast-check (commit `45356a3`, proof file, task-list `[x]`s).

## Verdict

**PASS**

## What was checked

1. **Spec alignment (Unit 1, `21-spec-manual-outfit-assembly.md` lines ~58–93).**
   Every FR bullet is implemented:
   - `/assemble` registered as a `<Route>` **inside** `<AuthGate>` in `App.tsx`,
     sibling of `/`, `/wardrobe`, `/add`, `/item/:id` (not nested) — confirmed by
     reading `App.tsx` directly.
   - Static hand-drawn SVG mannequin (`Mannequin.tsx`) with four zones keyed to
     the real `Slot` type imported from `lib/specSheet.ts` (`TOP`, `BOTTOM`,
     `SHOES`, and `CARRY` for the extras tray) — not a new parallel enum.
   - Entry-point links: `Stylist.tsx` adds a `chip chip-ghost assemble-entry`
     link near the chat entry point; `WardrobeGrid.tsx` adds a `btn
     assemble-entry` link, placed correctly in the **populated** branch only
     (after the empty/error/loading early-returns), matching "populated grid"
     from the task description.
   - Empty-wardrobe state reuses `state-block empty-state` / `empty-title` /
     `btn btn-primary` → `/add`, verbatim pattern from `WardrobeGrid.tsx`.
   - List-failure state renders a non-crashing note + retry, re-fetches via the
     same `settle()` closure pattern.

2. **Diff vs. task claims.** Read the full commit diff
   (`git show 45356a3`) for `Assemble.tsx`, `Mannequin.tsx`, `App.tsx`,
   `App.test.tsx`, `Stylist.tsx`/`.test.tsx`, `WardrobeGrid.tsx`/`.test.tsx`,
   `index.css`. Code matches the commit message and proof file's description
   exactly — no hand-waving. `index.css` additions use existing tokens
   (`--border`, `--paper-sunk`, `--radius`, `--ink-2`) and each zone has
   `min-height: 44px; min-width: 44px` (touch-target contract).

3. **Tests are real, not trivial.** Re-ran the suite myself rather than trusting
   the proof file:
   - `cd frontend && npm test -- --run` → **225/225 passed, 24 files** (matches
     proof exactly, including the "up from 218" delta claim).
   - `npm run lint` → clean, no output.
   - `npx tsc -b --noEmit` → clean, no output.
   - Inspected `Assemble.test.tsx`, `App.test.tsx`: the gated-route test
     correctly omits the token-seeding `beforeEach` used by the other routing
     tests, so it genuinely exercises the "no token → passcode screen, no
     `assemble` testid" path rather than accidentally inheriting a seeded
     token. Not a copy-paste no-op.
   - `Stylist.test.tsx` / `WardrobeGrid.test.tsx` link assertions check
     `getByRole('link', {name: /build it yourself/i})` → `href="/assemble"`,
     a real DOM assertion, not a snapshot or shallow render.

4. **Proof file honesty.** Cross-checked every number and command in
   `21-proofs/21-task-01-proofs.md` against a live re-run: test counts,
   file counts, lint/tsc results, and `git diff --stat` all match what I
   independently reproduced. Grepped the commit diff for
   `passcode|session.?token|sk-ant` — only conceptual references ("passcode
   screen", "session token is stored") appear, no literal secret values,
   consistent with the repo's no-secrets-committed rule. The screenshot
   deferral is disclosed plainly (not silently dropped) and the automated
   tests it says stand in for the screenshots do in fact cover the same
   visible-text/markup assertions (zone labels, empty-state markup, gating).

5. **Task-list state.** `21-tasks-manual-outfit-assembly.md`: parent 1.0 and
   sub-tasks 1.1–1.7 are `[x]`; parent tasks 2.0–4.0 remain `[ ]` — no
   premature marking of out-of-scope work.

6. **Repo-convention / security check.** `git status --short` is clean (no
   stray uncommitted files). Commit only touches `frontend/**` and spec docs —
   no `src/main/java` changes, matching the "no backend changes" claim for this
   slice. No new dependency added yet (dnd-kit is correctly deferred to Unit
   2.0's task list, not pulled in early).

## Minor observations (non-blocking)

- `Assemble.tsx` and `WardrobeGrid.tsx` currently duplicate the
  fetch/settle/loading/error/empty boilerplate almost verbatim. The task
  explicitly permits this ("reuse the pattern", not "extract a shared hook"),
  and extracting a shared hook is reasonable future refactor territory, not a
  defect of this task.
- The extras-tray zone visually sits beside the figure rather than overlaid
  on it (`position: static`), which is a reasonable and disclosed design choice
  (accumulating items need more room than an overlay region), not a spec
  deviation — the spec only requires a "side extras tray," which this is.

## Conclusion

The parent task's sub-tasks (1.1–1.7) are genuinely implemented, not just
checked off: the route exists and is gated, the mannequin renders four
correctly `Slot`-keyed and labeled zones, the empty/error states reuse the
established pattern faithfully, and both entry-point links exist and are
tested. The frontend suite, lint, and typecheck are all independently
verified green. No fabricated evidence, no leaked secrets, no scope creep into
Unit 2–4 territory.
