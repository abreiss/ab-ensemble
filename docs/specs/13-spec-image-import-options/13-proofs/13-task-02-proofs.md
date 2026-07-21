# Task 02 Proofs - Batch multi-select + "Save all" fan-out with independent per-item save

## Task Summary

This task turns the `/add` review queue (built in Task 1.0) into a real **batch**
importer. The library picker now accepts many photos at once, every picked file
becomes its own auto-tagging tile, and a single sticky **"Save all"** persists the
reviewed set by looping the existing single-photo `createItem` endpoint client-side
(no new backend surface). Saves are **independent**: a per-item failure keeps that
tile — with its edited tags intact and retryable — while successfully saved tiles
leave the queue; the screen returns to `/wardrobe` only once the queue fully drains.

It matters because building a wardrobe one photo at a time is the slowest part of
onboarding; batch multi-select + resilient "Save all" is the first of the three new
acquisition sources this feature adds, all on top of Task 1.0's shared `enqueue`.

## What This Task Proves

- A multi-file selection enqueues **N** independently-editable, auto-tagged tiles
  (Unit 2 FR: `multiple` + `FileList` → `enqueue([...files])`).
- "Save all" saves each tile independently: a mid-batch `createItem` rejection keeps
  the failed tile with its **edited tags intact**, removes the successes, and
  surfaces a retryable error (Unit 2 FR: independent per-item save).
- An all-success "Save all" drains the queue and navigates to `/wardrobe` (Unit 2
  FR: drained-queue completion).
- The library picker keeps `accept="image/*"`, gains `multiple`, and never forces the
  camera (Unit 2 FR: preserved picker semantics).
- Per-item **remove** drops only that tile and revokes only its object URL; N=1
  parity (headline flow, degraded/all-null fallback, stale-response guard, URL
  lifecycle) is preserved through the new save affordance (Units 1–2).

## Design Decisions (within spec latitude)

- **Always-expanded `TagForm` per tile.** The Unit 2 FR permits "an editable
  `TagForm` **(or** a compact tag summary that expands to the form)"; spec Open
  Question 2 makes compact-vs-expanded a non-blocking presentation detail. The
  always-expanded form preserves the N=1 tests and keeps the queue simple.
- **Single "Save all" save path** (matches Design Considerations: a sticky "Save
  all" anchors the set; the tile has thumbnail + form + remove, with no per-tile
  save button). `TagForm` now renders its submit button only when an `onSubmit` prop
  is supplied — `ItemDetail` still passes it ("Save changes"); the queue omits it and
  instead reads each tile's validated tags via a new optional `onChange`, which
  "Save all" fans out. This keeps `TagForm` fully backward-compatible (its own tests
  and `ItemDetail`'s are unchanged and green).

## Evidence Summary

- The full `AddItem` suite (13 tests) passes, including the four new Unit 2 tests
  and the N=1 parity tests re-pointed at "Save all".
- The whole frontend suite (18 files, **184 tests**) passes — `TagForm`'s new
  optional `onSubmit`/`onChange` did not regress `TagForm` or `ItemDetail`.
- `eslint .` is clean (including `react-hooks/refs` — the `onChange` ref is synced in
  an effect, never written during render).
- The batch-review-queue **screenshot is pending manual capture** (see the last
  artifact) — it needs the running app; this session has no headless browser and
  will not fabricate an image.

## Artifact: New/updated `AddItem` review-queue tests (RED → GREEN)

**What it proves:** Every Unit 2 functional requirement — multi-select enqueue,
independent per-item save, drained-queue navigation, preserved picker semantics — is
covered by a behaviour-named test, alongside the preserved N=1 parity tests.

**Why it matters:** Per `docs/TESTING.md` this is frontend UI-wiring; the meaningful
queue/source/save logic is exactly what must be tested (not `<video>`/render
plumbing). These tests are the automated gate for Unit 2.

**Command:**

```bash
cd frontend && npm test -- --run src/routes/AddItem.test.tsx --reporter=verbose
```

**Result summary:** 13/13 pass. The four new Unit 2 tests are the multi-select
enqueue, the mixed success/failure independent save, the all-success navigation, and
the multi-select/image-only/no-camera picker assertion; the rest are the preserved
Unit 1 behaviours now driven through "Save all".

```
✓ runs the headline N=1 flow: photo → auto-tag → edit → save → back to grid
✓ still yields an editable, saveable tile when the suggestion is all-null
✓ keeps "Save all" disabled until every tile has its required fields
✓ keeps the tile, its photo, and its edited tags when its create fails
✓ falls back to a blank editable tile when that item’s tag-preview rejects
✓ seeds each tile from its own tag response even when one arrives out of order
✓ revokes only the removed tile’s object URL when it is removed
✓ revokes a tile’s object URL on save
✓ revokes every remaining tile’s object URL on unmount
✓ offers a plain library picker: multi-select, image-only, and no forced camera
✓ enqueues one editable, auto-tagged tile per file from a multi-select
✓ saves independently: a per-item failure keeps that tile (edited tags) while successes are removed
✓ navigates to the wardrobe once "Save all" drains the whole queue
```

## Artifact: Full frontend suite (no regression from the `TagForm` change)

**What it proves:** Making `TagForm`'s `onSubmit`/`submitLabel` optional and adding
`onChange` did not break `TagForm`'s own tests or its other consumer (`ItemDetail`).

**Why it matters:** `TagForm` is shared; the change had to be strictly additive for
existing callers. A green whole-suite run confirms it.

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** 18 files, 184 tests pass (`TagForm` 6/6, `ItemDetail` 10/10,
`AddItem` 13/13). The one console `act(...)` notice originates from the untouched
`WardrobeDrawer.test.tsx` and predates this task.

```
Test Files  18 passed (18)
     Tests  184 passed (184)
```

## Artifact: Lint clean

**What it proves:** The implementation passes the repo's ESLint gate, including the
`react-hooks/refs` rule that flagged an earlier draft (writing a ref during render).

**Why it matters:** ESLint is a pre-commit gate; the `onChange` reporter had to sync
its callback ref inside an effect, not during render.

**Command:**

```bash
cd frontend && npm run lint
```

**Result summary:** `eslint .` exits 0 with no errors or warnings.

## Artifact: Batch review-queue screenshot — PENDING MANUAL CAPTURE

**What it proves (once captured):** The `/add` review queue rendering multiple tiles
with editable tags and "Save all" progress — the batch review UX.

**Why it matters:** It is the human-visible demo of Unit 2. It is **not** the
automated gate (the tests above are); consistent with spec Open Question 3, an
environment-dependent screenshot may be captured against a running stack.

**Why it is pending:** Capturing it requires the running backend + frontend (and,
for live auto-tags, a Claude API key) plus a browser to drive a native file upload.
This implementation session has DynamoDB Local only, no headless browser, and no
Claude key — and will not fabricate the image.

**Target path:** `docs/specs/13-spec-image-import-options/13-proofs/assets/batch-review-queue.png`

**Repro steps (operator, with a Claude key for live tags):**

1. `docker compose up -d dynamodb`
2. In `frontend/.env` (or the backend env) set `ENSEMBLE_PASSCODE=<demo>`,
   `ENSEMBLE_SESSION_SECRET=<any-local>`, and `ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...`.
3. Backend: `./gradlew bootRun`  •  Frontend: `cd frontend && npm run dev`
4. Open `http://localhost:5173`, enter the passcode, go to **Add an item**.
5. Choose **3–4** garment photos at once (demo wardrobe content only).
6. Wait for the tiles to auto-tag, then click **Save all** and screenshot while the
   "N of M saved…" progress is visible.
7. Save the PNG to the target path above. Ensure the passcode/session token is **not**
   visible in the frame.

## Reviewer Conclusion

The Unit 2 batch acquisition and resilient "Save all" behaviour is implemented and
fully covered by automated tests (13/13 `AddItem`, 184/184 overall) with a clean
lint, and the shared `TagForm` change is strictly backward-compatible. The only
outstanding artifact is the demo screenshot, which is environment-dependent and
documented above for manual capture — it does not gate the automated proof.
