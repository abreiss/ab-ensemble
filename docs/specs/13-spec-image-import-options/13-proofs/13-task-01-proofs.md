# Task 01 Proofs — Review-queue refactor (`1..N` queue behind a shared `enqueue`, `N=1` preserved)

## Task Summary

This task proves `AddItem.tsx` was lifted from a single-photo screen into a
`1..N` review queue, with every image source funnelling through one shared
`enqueue(files)` seam, **without changing the single-photo (`N=1`) experience**.
Each queued photo becomes a `PendingItem { id, file, previewUrl, phase,
suggestion, saving, error }` that auto-tags into an editable `TagForm` and saves
independently. This is the foundation Units 2–5 (batch, throttle/429, paste,
camera) build on — they will each be one `enqueue` call and nothing more.

## What This Task Proves

- **`N=1` parity:** one picked photo → one queue tile → auto-tag → editable form
  → save → return to `/wardrobe`, exactly as before the refactor.
- **Single `enqueue` seam:** the single-file `<input>` calls `enqueue([file])`;
  all per-item state (object URL, request identity, tag-preview) is created there.
- **Per-item request-id guard:** each tile is keyed by its own id, so an
  out-of-order (stale) tag response for one tile can only seed that tile and
  never bleeds into another.
- **Per-item degraded fallback preserved:** a rejected or all-null `tagPreview`
  leaves that tile on the blank-but-editable `EMPTY_SUGGESTION` seed — never an
  error, never a crash.
- **Per-item object-URL lifecycle:** each tile's object URL is revoked on remove,
  on that tile's save, and any survivors on unmount — no single-URL leak with
  many items.
- **No regression:** the full frontend suite and lint stay green.

## Evidence Summary

- The scoped `AddItem.test.tsx` suite (10 tests) passes, covering the headline
  `N=1` flow, the out-of-order per-tile guard, the degraded fallback, independent
  per-tile save failure, and object-URL revocation on remove/save/unmount.
- The full frontend suite (18 files, 181 tests) passes — no regression from the
  refactor.
- `npm run lint` (eslint) exits clean.

## Artifact: `AddItem.test.tsx` queue-model suite

**What it proves:** The refactored screen satisfies every Unit-1 functional
requirement — `N=1` parity, the shared `enqueue`, the per-item stale-response
guard, the degraded fallback, and the per-item URL lifecycle.

**Why it matters:** These are the behaviours the whole feature rests on; if the
queue spine were wrong, every source built on it would inherit the bug.

**Command:**

```bash
cd frontend && npm test -- --run src/routes/AddItem.test.tsx
```

**Result summary:** 10 of 10 tests pass. The per-test names map one-to-one onto
the Unit-1 FRs.

```
✓ AddItem review queue > runs the headline N=1 flow: photo → auto-tag → edit → save → back to grid
✓ AddItem review queue > still yields an editable, saveable tile when the suggestion is all-null
✓ AddItem review queue > blocks a tile save until its required fields are valid
✓ AddItem review queue > keeps the tile, its photo, and its edited tags when its create fails
✓ AddItem review queue > falls back to a blank editable tile when that item’s tag-preview rejects
✓ AddItem review queue > seeds each tile from its own tag response even when one arrives out of order
✓ AddItem review queue > revokes only the removed tile’s object URL when it is removed
✓ AddItem review queue > revokes a tile’s object URL on save
✓ AddItem review queue > revokes every remaining tile’s object URL on unmount
✓ AddItem review queue > does not force the camera, so an existing photo can be chosen

 Test Files  1 passed (1)
      Tests  10 passed (10)
```

## Artifact: Full frontend suite (no regression)

**What it proves:** Lifting the single-photo state into a queue broke nothing
elsewhere in the app.

**Why it matters:** The refactor touches a screen shared with `TagForm`,
routing, and the items API client; the full suite confirms behaviour parity.

**Command:**

```bash
cd frontend && npm run test -- --run
```

**Result summary:** 181 tests across 18 files pass.

```
 Test Files  18 passed (18)
      Tests  181 passed (181)
```

## Artifact: Lint clean

**What it proves:** The new queue code meets the repo's eslint gate (one of the
pre-commit gates).

**Command:**

```bash
cd frontend && npm run lint
```

**Result summary:** eslint exits 0 with no output.

## Reviewer Conclusion

`AddItem.tsx` is now a `1..N` review queue behind a single `enqueue(files)` seam,
and the `N=1` path is provably identical to the original single-photo flow. The
per-item request-id guard, degraded-tag fallback, and object-URL lifecycle are
each exercised by a dedicated test, and the full suite plus lint stay green — the
spine is ready for the batch/paste/camera sources to plug into.
