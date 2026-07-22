# Task 05 Proofs — Consistent responsive layout (Part B)

## Task Summary

This task fixes the confirmed CSS root cause behind Part B: before, every screen
except the Stylist was hard-capped at a phone-width `30rem`, so on desktop the
Build/Wardrobe/Add/Item/Saved screens sat in a narrow column while only the
Stylist page filled the width. Task 5.0 reconciles the `#root { max-width: 30rem }`
cap and the `#root:has(.stylist-layout) { max-width: 72rem }` special case into a
single default — the content column fills the viewport up to `72rem` on desktop
and stays a single phone-width column on small screens — while making
naturally-narrow content (the add-item form and item detail) **center** within
the wider shell instead of stretching edge-to-edge. The Stylist two-pane layout
is visually unchanged (it was already `72rem`).

## What This Task Proves

- Desktop-width fill is now the **default** for every screen, not a Stylist-only
  exception (`#root` is a single `max-width: 72rem` shell; the `:has(.stylist-layout)`
  override is gone).
- Naturally-narrow screens (add-item, item-detail) **center** at a comfortable
  `~30rem` measure within the wider shell rather than stretching.
- The width-filling screens (Build/`assemble`, Wardrobe, Saved) and the Stylist
  two-pane layout keep the full column — no regression.
- The change regresses **no** existing frontend test (357 green) and is lint-clean.

## Evidence Summary

- The `index.css` diff is 15 insertions / 8 deletions: one shell rule generalized,
  one special-case rule removed, one centering rule added — nothing else touched.
- `npm test -- --run` → **357 tests passed** across 32 files (same count as the
  pre-change baseline recorded in task 4's proof), confirming no CSS-driven
  regression in the RTL suite.
- `npm run lint` (eslint) → **clean**.

## Artifact: The reconciled shell + centering CSS

**What it proves:** The `30rem` hard cap and the Stylist-only `72rem` exception
are reconciled into one `72rem` default, and the two naturally-narrow screens are
re-centered at `30rem` within it — the exact FR for Part B.

**Why it matters:** This is the whole behavioral change. A reviewer can confirm
the fix is a small, surgical CSS edit (no markup churn, no per-screen overrides)
that reads like the surrounding care-label design system.

**Command:**

~~~bash
git diff frontend/src/index.css
~~~

**Result summary:** `#root` becomes a single `max-width: 72rem` shell (comment
updated); the `#root:has(.stylist-layout)` override is deleted; a new rule centers
`[data-testid='add-item']` and `[data-testid='item-detail']` at `max-width: 30rem;
margin-inline: auto`. The attribute selectors match the file's existing
single-quote convention (e.g. `.photo-picker input[type='file']`) and cover every
render state (loading / error / loaded) of each screen with one rule.

~~~diff
 #root {
-  /* Mobile-first: a single phone-width column, centered on larger screens. */
-  max-width: 30rem;
+  /* Mobile-first single column that fills the viewport up to a desktop measure.
+     Every screen shares this shell; naturally-narrow screens re-center
+     themselves at a comfortable measure within it (see `.screen` below). */
+  max-width: 72rem;
   margin: 0 auto;
   min-height: 100dvh;
 }

-/* The stylist landing is a two-pane desktop experience (drawer + result), so it
-   breaks out of the phone-width cap when mounted; other screens stay narrow. */
-#root:has(.stylist-layout) {
-  max-width: 72rem;
-}
-
 h1,
 h2,
 h3 {
...
+/* Naturally-narrow screens (the add-item form and item detail) sit at a
+   comfortable reading measure and center within the now-wider shell rather than
+   stretching edge-to-edge; the width-filling screens (Build, Wardrobe, Saved,
+   Stylist) keep the full column. Targeted by their stable test ids so every
+   render state (loading / error / loaded) centers with one rule. */
+[data-testid='add-item'],
+[data-testid='item-detail'] {
+  max-width: 30rem;
+  margin-inline: auto;
+}
~~~

## Artifact: Full frontend suite stays green (no regression)

**What it proves:** The layout change breaks no existing behavior — every route,
component, and library test still passes.

**Why it matters:** Part B is verified by manual screenshots, not unit tests
(per `docs/TESTING.md`: view/CSS plumbing is not over-tested). The test suite is
therefore the machine-verifiable guard that the CSS edit did not regress any
rendering-decision logic covered by RTL.

**Command:**

~~~bash
cd frontend && npm test -- --run
~~~

**Result summary:** 357 tests passed across 32 files — identical to the baseline
count captured in `26-task-04-proofs.md`, so the CSS change added no failures.

~~~text
 Test Files  32 passed (32)
      Tests  357 passed (357)
~~~

## Artifact: Lint gate clean

**What it proves:** The change satisfies the repo's enforced frontend quality gate.

**Why it matters:** The pre-commit / CI frontend gate is `eslint` on `.ts/.tsx`.
There is **no** prettier config in the repo, so CSS is outside any format gate;
the new selector nonetheless matches the file's existing single-quote style, so it
introduces no inconsistency.

**Command:**

~~~bash
cd frontend && npm run lint
~~~

**Result summary:** eslint completed with no errors or warnings.

## Artifact: Screenshot proof (5.3) — deferred to manual verification

**What it proves:** N/A in this headless run.

**Why it matters:** The spec asks for desktop/phone before-after screenshots of
`/`, `/assemble`, `/wardrobe`, `/add`, `/item/:id`, and `/saved`. Honestly
producing them requires a running app past the `AuthGate` passcode gate (which
blocks every screen without a live session) **and** a non-empty local photo store
(the local `./data/photos` is empty and there are no seeded items/outfits) — so no
truthful populated screenshots are producible in this headless environment.

**Result summary:** Deferred to manual verification, mirroring tasks 3.4 / 4.4 and
spec 21's same-screen deferral. The machine-verifiable stand-ins are the reviewable
CSS diff above plus the full frontend suite (357) staying green and eslint clean.

**To capture manually:**

1. Run the stack: `./gradlew bootRun` (backend, with `ENSEMBLE_PASSCODE` set) and
   `cd frontend && npm run dev`; open the Vite URL and enter the passcode.
2. Seed a few wardrobe items, then save one AI look (from `/`) and one manual look
   (from `/assemble`) so the populated screens have content.
3. On a **desktop** viewport, screenshot `/` and `/assemble` side by side (both
   should fill the width comparably), then `/wardrobe`, `/add`, `/item/:id`, and
   `/saved` (Build/Wardrobe/Saved fill the column; Add/Item-detail center at
   ~30rem).
4. On a **~390px phone** viewport, screenshot every screen — each should be a
   single column with no horizontal scroll.
5. Confirm the Stylist two-pane (drawer + result) desktop layout is visually
   unchanged from before.
6. Ensure no passcode or session token is visible in any frame or URL.

## Reviewer Conclusion

The layout inconsistency is fixed at its root with a small, surgical CSS edit: a
single `72rem` shell replaces the `30rem` cap and the Stylist-only exception, and
the two naturally-narrow screens re-center within it — so every screen now fills
the desktop width like the Stylist page while mobile stays single-column. The
change is covered by the full frontend suite (357 green) and eslint, with the
visual screenshots deferred to manual verification per the established pattern.
