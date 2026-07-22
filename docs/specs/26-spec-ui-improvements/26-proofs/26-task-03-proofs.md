# Task 03 Proofs — Real save wiring on Stylist (`/`) and Build (`/assemble`)

## Task Summary

This task makes both "save" affordances real. It adds a typed `api/outfits.ts`
client (mirroring `api/items.ts`), replaces the cosmetic heart toggle on the
Stylist screen with a real `idle → saving → saved → error` save that `POST`s the
look with `source: "ai"`, and adds a **"Save outfit"** button to the Build screen
that saves the placed set with `source: "manual"` — disabled when nothing is
placed. Both drive the same lifecycle language already used by "Wear today", and
neither persists anything client-side (the server owns the record).

## What This Task Proves

- A typed `saveOutfit` / `listOutfits` / `deleteOutfit` client hits the right
  method + URL under `BASE = '/api/outfits'`, resolves on 2xx, and throws a typed
  `ApiError` (carrying `status`) on any non-2xx — including the grounding `400`.
- The Stylist heart is no longer a local boolean: clicking it calls `saveOutfit`
  **once** with the rendered look's `itemIds` + `reason` and `source: "ai"`,
  locks to a saved state on success, shows a retryable error on rejection, and
  resets when a fresh look is picked.
- The Build "Save outfit" button calls `saveOutfit` with the placed ids and
  `source: "manual"`, is **disabled when nothing is placed** (no empty outfit),
  locks to "Saved ✓" on success, and surfaces a retryable banner on failure.

## Evidence Summary

- `outfits.test.ts` — **9/9** pass (client contract + error/transport paths).
- `OutfitResult.test.tsx` + `Stylist.test.tsx` — **34/34** pass, including the 8
  new/updated save-lifecycle assertions (RED-then-GREEN).
- `Assemble.test.tsx` — **17/17** pass, including the 4 new manual-save assertions
  (RED-then-GREEN).
- Full frontend suite **346/346** (31 files), `tsc -b` clean, `eslint .` clean.

## Artifact: Typed `/api/outfits` client (3.1)

**What it proves:** The client mirrors `api/items.ts` — correct verb/URL, 2xx
resolve, typed `ApiError` on non-2xx, token injection, and `reason` omitted for a
manual save.

**Why it matters:** Units 2 and 3 both consume this client; the grounding `400`
must surface as a typed status so the UI can react to it.

**Command:**

```bash
cd frontend && npx vitest run src/api/outfits.test.ts --reporter=verbose
```

**Result summary:** 9/9 pass — POST/GET/DELETE contracts, the grounding-`400`
`ApiError`, a transport-failure propagation, and the `reason`-omitted manual body.

```
 ✓ saveOutfit > POSTs JSON to /api/outfits and returns the saved outfit
 ✓ saveOutfit > omits reason from the body when it is not provided (manual look)
 ✓ saveOutfit > sends the stored session token as X-Ensemble-Session
 ✓ saveOutfit > rejects with a typed ApiError carrying the status on a grounding 400
 ✓ saveOutfit > propagates a network/transport failure
 ✓ listOutfits > GETs /api/outfits and returns the parsed array
 ✓ listOutfits > throws on a non-2xx response
 ✓ deleteOutfit > DELETEs /api/outfits/:id and resolves on 204
 ✓ deleteOutfit > throws on a non-2xx response (unknown id → 404)
 Tests  9 passed (9)
```

## Artifact: Stylist real save lifecycle (3.2)

**What it proves:** The cosmetic `useState` toggle is gone. The heart is now a
controlled `saveStatus` machine; the save handler/state was lifted into
`Stylist.tsx` alongside `onWearToday`/`logStatus` (per the spec's Q1 assumption),
and it POSTs `source: "ai"` with the look's `itemIds` + `reason`.

**Why it matters:** This is the headline "turn the cosmetic heart into a real
save" requirement, and it must not double-post or leak state across a re-pick.

**Command:**

```bash
cd frontend && npx vitest run src/routes/Stylist.test.tsx src/components/OutfitResult.test.tsx --reporter=verbose
```

**Result summary:** 34/34 pass. The controlled-heart states (idle/saving/saved/
busy/error) are covered at the component level; the real `saveOutfit('ai')` call,
the saved lock, the retryable error, and the reset-on-re-pick are covered at the
route level.

```
 ✓ OutfitResult > fires the save callback when the heart is clicked (idle)
 ✓ OutfitResult > reflects a saved state on the heart once the look is saved
 ✓ OutfitResult > disables the heart while a save is in flight
 ✓ OutfitResult > disables the heart while the screen is otherwise busy
 ✓ OutfitResult > shows a retryable message and keeps the heart clickable when the save failed
 ✓ Stylist route > saves the rendered look via POST /api/outfits with source "ai" and locks the heart
 ✓ Stylist route > shows a retryable save error and keeps the heart clickable when the save fails
 ✓ Stylist route > resets the saved heart when a fresh look is picked
 Tests  34 passed (34)
```

## Artifact: Build "Save outfit" + empty guard (3.3)

**What it proves:** The Build screen gains a "Save outfit" button beside "Wear
today" that saves `placedIds(placement)` with `source: "manual"`, mirrors the
`idle | saving | saved | error` lifecycle, is disabled when nothing is placed,
and releases the "Saved ✓" lock when the look is edited (so a revised look can be
re-saved).

**Why it matters:** This is the second, hand-built save affordance; the empty
guard prevents persisting an empty outfit.

**Command:**

```bash
cd frontend && npx vitest run src/routes/Assemble.test.tsx --reporter=verbose
```

**Result summary:** 17/17 pass — the four new save assertions plus the 13
pre-existing drag/drop/wear tests stay green.

```
 ✓ Assemble save-outfit > saves the placed set via saveOutfit with source "manual" and locks to "Saved ✓"
 ✓ Assemble save-outfit > disables "Save outfit" when nothing is placed (no empty outfit)
 ✓ Assemble save-outfit > surfaces a retryable error banner when the save is rejected
 ✓ Assemble save-outfit > re-enables "Save outfit" (not the stuck "Saved ✓") once the outfit changes after saving
 Tests  17 passed (17)
```

## Artifact: Full-suite regression + quality gates

**What it proves:** The new wiring regresses nothing, typechecks, and lints clean.

**Why it matters:** Success-metric #6 requires the existing frontend tests to stay
green.

**Commands + result summary:**

```bash
cd frontend && npx vitest run     # → Test Files 31 passed (31) · Tests 346 passed (346)
cd frontend && npx tsc -b         # → exit 0 (no type errors)
cd frontend && npm run lint       # → exit 0 (eslint clean)
```

> Note: an `act(...)` warning surfaces from `WardrobeDrawer`'s own mount-time
> `listItems()` settle in some route tests. It is **pre-existing** and unrelated
> to this task's save wiring (it originates in the wardrobe drawer, not
> `OutfitResult`/`Stylist`/`Assemble`), and all tests pass.

## Artifact: Save affordance screenshots — DEFERRED to manual verification

**What it proves (once captured):** the Stylist look after saving (heart in its
saved state) and the Build screen showing "Save outfit" beside "Wear today".

**Why it is deferred:** This is a headless implementation run. A faithful
screenshot of the Stylist saved heart needs a live AI look, which requires a real
Claude key **and** seeded wardrobe photos; the local photo store is empty here, so
the stylist would return an empty look and the Build screen would show its
empty-wardrobe state — there is nothing to screenshot honestly. This is exactly
how the same Build screen's screenshots were handled in spec 21 (see
`21-task-03-proofs.md` / `21-task-04-proofs.md`, "Screenshot deferred to manual
verification"). The RED-then-GREEN component/route tests above — the `source: "ai"`
and `source: "manual"` call assertions, the saved locks, the retryable error
affordances, and the disabled-when-empty guard — are the machine-verifiable
stand-in. The `.btn`/`.btn-logged`/`.heart-btn`/`.banner-error` classes are
pre-existing, already-proven styles.

## Reviewer Conclusion

Both save affordances are now real and consistent with the app's existing
lifecycle language: the Stylist heart POSTs an `"ai"` save of the rendered look
and locks/errors/resets correctly, and the Build screen POSTs a `"manual"` save of
the placed set with an empty-outfit guard. All logic is TDD-verified (RED → GREEN),
the full frontend suite (346 tests), `tsc`, and `eslint` are green, and nothing is
persisted client-side — the server owns the record.
