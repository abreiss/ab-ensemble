# Task 04 Proofs — Saved Outfits page (`/saved`) + route + header nav link

## Task Summary

This task gives saved looks a home. It adds `frontend/src/routes/SavedOutfits.tsx`
and a `/saved` route inside the existing `AuthGate` in `App.tsx`, plus a `Saved`
header nav link placed **left of `Build`** (order: Saved · Build · Wardrobe ·
+ Add). The page fetches `listOutfits()` and `listItems()` together, renders a
grid of outfit cards — each piece's photo via `photoUrl(itemId)`, the look's
`source` (AI pick / Hand-built) and `reason` when present, and a remove control
that calls `deleteOutfit(id)` and drops the card on success. It reuses the
`WardrobeGrid` loading / empty / error(+retry) state patterns, and tolerates a
piece deleted since the save: pieces are resolved at render time against the
current wardrobe, missing pieces are skipped, survivors render, and a quiet
"N piece(s) no longer in your wardrobe" note is shown — never crashing, even when
every piece is gone (Q3-C). The saved record is never rewritten.

## What This Task Proves

- Navigating to `/saved` mounts the Saved Outfits screen **inside `AuthGate`**
  (the passcode screen shows instead when no session token is stored).
- The header renders a `Saved` link that routes to `/saved` and is ordered
  **before** `Build` in the DOM.
- The page renders one card per saved outfit with its pieces' photos plus
  `source`/`reason`; shows an empty state ("No saved outfits yet"); shows a
  non-crashing error + retry on load failure; and removes a card via
  `deleteOutfit(id)` on success (keeping the card with a retry note on failure).
- A saved outfit that references a since-deleted item renders only the surviving
  pieces plus the loss note and does not crash — including the all-pieces-gone
  case (Q3-C).

## Evidence Summary

- `App.test.tsx` — **13/13** pass, including the 3 new routing/nav/gating
  assertions (`/saved` mounts inside `AuthGate`, `Saved` ordered before `Build`,
  `/saved` gated when unauthenticated) — RED-then-GREEN.
- `SavedOutfits.test.tsx` — **8/8** pass (loading, populated grid with
  pieces + source/reason, empty, error+retry, remove-success, remove-failure,
  and the two Q3-C deleted-piece cases) — RED-then-GREEN.
- Full frontend suite **357/357** (32 files) green; `tsc --noEmit` clean;
  `eslint .` clean.
- Change set is **frontend-only** (`App.tsx`, `App.test.tsx`, `index.css`, the two
  new `SavedOutfits.*` files) — no backend file changed, so the backend suite is
  unaffected (success metric #6).

## Artifact: `/saved` route + `Saved` nav link (4.1)

**What it proves:** The route is mounted and gated, and the nav link sits left of
`Build` in the header.

**Why it matters:** Success metric #4 — "the `Saved` nav link renders left of
`Build` and routes to `/saved`" — and the auth requirement that every screen sits
behind the passcode gate.

**Command:**

~~~bash
cd frontend && npm test -- --run src/App.test.tsx
~~~

**Result summary:** 13/13 pass, including `mounts the saved-outfits screen at
/saved`, `exposes a persistent saved-outfits navigation control in the header,
before Build`, and `shows the passcode screen instead of /saved when no session
token is stored`.

~~~text
 ✓ src/App.test.tsx (13 tests) 84ms

 Test Files  2 passed (2)
      Tests  21 passed (21)
~~~

(Combined run with `SavedOutfits.test.tsx` shown; `App.test.tsx` contributes 13.)

The DOM-order assertion is explicit:

~~~tsx
const savedLink = within(header).getByRole('link', { name: /saved/i })
expect(savedLink).toHaveAttribute('href', '/saved')
const buildLink = within(header).getByRole('link', { name: /build/i })
expect(savedLink.compareDocumentPosition(buildLink)).toBe(
  Node.DOCUMENT_POSITION_FOLLOWING,
)
~~~

## Artifact: Saved Outfits page states (4.2)

**What it proves:** The page renders the populated grid (pieces + source/reason),
the empty state, the error+retry state, and removal — never a blank or broken
screen.

**Why it matters:** Success metric #3 — "`/saved` lists saved outfits with photos
+ `source`/`reason`, supports remove, and renders loading/empty/error states."

**Command:**

~~~bash
cd frontend && npm test -- --run src/routes/SavedOutfits.test.tsx
~~~

**Result summary:** 8/8 pass. The populated test asserts one `outfit-card` per
saved outfit, the pieces' `<img src>` in order via `photoUrl`, the AI look's
`reason` text, and both source labels; empty renders "No saved outfits yet";
load failure renders the retry `state-block` and a retry re-fetches; remove calls
`deleteOutfit('o1')` and drops that card; a failed remove keeps the card with a
"couldn't remove" note.

~~~text
 ✓ src/routes/SavedOutfits.test.tsx (8 tests) 91ms

 Test Files  1 passed (1)
      Tests  8 passed (8)
~~~

## Artifact: Deleted-piece tolerance — Q3-C (4.3)

**What it proves:** A saved outfit referencing an item deleted since the save
renders only its surviving pieces plus a quiet note, and the all-pieces-gone case
still renders the card — no crash, no broken tile.

**Why it matters:** Success metric #3's tail — "a since-deleted piece is skipped
with a note and never crashes the page" — the synchronous analog of the stylist's
grounding tolerance, applied at render time without rewriting the stored record.

**Result summary:** Both Q3-C tests pass. With saved `itemIds = ['a','ghost']` and
a wardrobe of only `a`, exactly one `<img>` (`/api/items/a/photo`) renders plus
"1 piece is no longer in your wardrobe."; with all pieces gone, the card still
renders with "2 pieces are no longer in your wardrobe." and no `<img>`.

Resolution logic (survivors only; count drives the caption; record untouched):

~~~tsx
const pieces = outfit.itemIds
  .map((id) => byId.get(id))
  .filter((piece): piece is Item => piece !== undefined)
const missingCount = outfit.itemIds.length - pieces.length
~~~

## Artifact: Full frontend suite + typecheck + lint (no regression)

**What it proves:** The new page and nav wiring regress no existing test, and the
code is type- and lint-clean.

**Why it matters:** Success metric #6 — "existing backend + frontend tests stay
green." No backend file was touched, so only the frontend gate is exercised here.

**Command:**

~~~bash
cd frontend && npm test -- --run && npx tsc --noEmit && npm run lint
~~~

**Result summary:** 357/357 frontend tests pass across 32 files; `tsc --noEmit`
exits 0; `eslint .` reports no findings.

~~~text
 Test Files  32 passed (32)
      Tests  357 passed (357)
~~~

## Artifact: Screenshot proof (4.4) — deferred to manual verification

**What it proves:** N/A in this headless run.

**Why it matters:** The spec asks for a screenshot of the populated Saved Outfits
page + the header `Saved` link. Honestly producing it requires a running app with
a non-empty local photo store and at least one saved outfit — neither exists in
this headless environment (empty `./data/photos`, no live saved looks).

**Result summary:** Deferred to manual verification, mirroring task 3.4 and
spec 21's same-screen deferral. The machine-verifiable stand-ins are the
RED→GREEN routing/nav tests (`App.test.tsx`) and the page-state + deleted-piece
tests (`SavedOutfits.test.tsx`), which assert the same behaviors the screenshot
would show. To capture manually: seed a couple of items, save an AI look and a
manual look, then screenshot `/saved` (ensuring no passcode/session token is
visible in the frame or URL).

## Reviewer Conclusion

Saved looks now have a gated home: `/saved` mounts inside `AuthGate` with a
`Saved` nav link left of `Build`, the page renders every saved outfit with its
pieces, source, and reason, supports removal, handles loading/empty/error states,
and gracefully tolerates pieces deleted since the save — all covered by
RED→GREEN tests with the full frontend suite, `tsc`, and `eslint` green. The only
outstanding item is the manual screenshot, deferred per the established pattern.
