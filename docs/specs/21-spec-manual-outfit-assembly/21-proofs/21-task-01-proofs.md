# Task 01 Proofs - `/assemble` route, mannequin scaffold, and entry points

## Task Summary

This task stands up the reachable, `AuthGate`-gated `/assemble` screen — the
manual outfit-assembly page's entry slice — before any drag behavior exists.
It adds a static, hand-authored SVG mannequin with four labeled, `Slot`-keyed
drop zones (`TOP`, `BOTTOM`, `SHOES`, and a side "extras" tray for
`CARRY`/`PIECE`), reuses the existing loading/empty/list-failure state pattern
from `WardrobeGrid.tsx`, and wires two entry-point links ("Build it yourself")
from the Stylist screen (`/`) and the wardrobe grid (`/wardrobe`). No drag
interaction, no backend change, and no Claude call are introduced by this task
— those are out of scope until later units (2.0–4.0).

## What This Task Proves

- `/assemble` is registered as a sibling route (not nested under `/`) inside
  the existing `AuthGate`, so it is unreachable without a valid session token
  — matching every other protected screen.
- The screen never crashes on a fetch failure or an empty wardrobe: it reuses
  the exact `listItems()` + `settle()` loading/error pattern and the
  `state-block empty-state` / `empty-title` / `btn btn-primary` markup already
  proven in `WardrobeGrid.tsx`.
- The static mannequin (`Mannequin.tsx`) renders four visibly labeled,
  `Slot`-keyed drop regions with generous, non-pixel-perfect hit-boxes, ready
  for `useDroppable` wiring in a later unit.
- Both required entry points ("Build it yourself" → `/assemble`) exist and are
  verified by component tests, not just visual inspection.

## Evidence Summary

- Scoped Vitest run: `App.test.tsx`, `Assemble.test.tsx`, `Stylist.test.tsx`,
  `WardrobeGrid.test.tsx` — **35/35 tests pass**, covering the gated route, the
  empty/error states, the four zone labels, and both entry-point links.
- Full frontend suite: **225/225 tests pass** across 24 files — no regression
  to any existing screen.
- `npm run lint` (ESLint) and `npx tsc -b --noEmit` (TypeScript) both exit
  clean with no output.
- `git diff --stat` for this task touches only `frontend/**` and the task-list
  doc — no `src/main/java` (backend) files changed, consistent with the
  spec's "no backend changes" requirement.
- Screenshot artifacts are **DEFERRED to manual/on-device verification** (see
  note below) — this is a headless implementation run with no browser
  available to capture them honestly.

## Artifact: Gated routing test — `/assemble` mounts inside `AuthGate`

**What it proves:** The route is mounted as a sibling of `/`, `/wardrobe`,
`/add`, and `/item/:id`, and it is unreachable without a valid session token
(the passcode screen renders instead) — the same gating contract every other
protected route already has.

**Why it matters:** This is the FR's core requirement: `/assemble` reachable,
gated by the existing `AuthGate`. A route registered outside the gate (or
missing entirely) would be a security regression, not just a missing feature.

**Command:**

```bash
cd frontend && npm test -- --run App.test.tsx
```

**Result summary:** All 9 `App.test.tsx` cases pass, including the two new
ones added for this task: `/assemble` renders the `assemble` test-id when a
session token is pre-seeded, and falls back to the passcode screen when no
token is present.

```
 ✓ src/App.test.tsx (9 tests) 75ms
```

Relevant new cases (from the file, `frontend/src/App.test.tsx`):

```ts
it('mounts the assemble screen at /assemble', async () => {
  renderAt('/assemble')
  expect(await screen.findByTestId('assemble')).toBeInTheDocument()
})
```

```ts
describe('App shell — /assemble is gated', () => {
  it('shows the passcode screen instead of /assemble when no session token is stored', async () => {
    renderAt('/assemble')
    expect(await screen.findByLabelText(/passcode/i)).toBeInTheDocument()
    expect(screen.queryByTestId('assemble')).not.toBeInTheDocument()
  })
})
```

## Artifact: Empty-wardrobe and list-failure states never crash the screen

**What it proves:** An empty wardrobe renders the standard
`state-block empty-state` / `empty-title` / `btn btn-primary` invite to
`/add` (matching `WardrobeGrid.tsx` verbatim), and a rejected `listItems()`
call renders a non-crashing error note with a working retry — never a blank
or broken screen.

**Why it matters:** The spec calls out these as the two real edge states this
unit must guard against before any drag surface exists.

**Command:**

```bash
cd frontend && npm test -- --run Assemble.test.tsx
```

**Result summary:** All 3 `Assemble.test.tsx` cases pass: the empty state, the
list-failure-then-retry state, and the four labeled drop zones (see next
artifact).

```
 ✓ src/routes/Assemble.test.tsx (3 tests) 68ms
```

## Artifact: Mannequin renders four labeled, `Slot`-keyed drop zones

**What it proves:** `Mannequin.tsx` renders a static SVG line-art silhouette
with four visibly labeled regions — Top, Bottom, Shoes, Extras — each carrying
a stable `data-slot` identifier drawn from the existing `Slot` type
(`lib/specSheet.ts`), so a later unit can wire `useDroppable` without
re-deriving the zone model.

**Why it matters:** This is the mannequin scaffold itself — the visual anchor
of the whole feature — and the FR requires the zones to be keyed to the
existing `Slot` type rather than a new, parallel enum.

**Result summary:** The populated-wardrobe case finds all four zone labels by
their visible text (`Top`, `Bottom`, `Shoes`, `Extras`); the extras zone is
identified by the `CARRY` slot (unrecognized/`PIECE` categories degrade into
the same tray per the existing `slotForCategory` rule — that routing logic
itself belongs to a later unit).

```ts
it('renders the mannequin with four labeled, Slot-keyed drop zones when items exist', async () => {
  listItemsMock.mockResolvedValue([item('a')])
  renderAssemble()
  expect(await screen.findByText(/^top$/i)).toBeInTheDocument()
  expect(screen.getByText(/^bottom$/i)).toBeInTheDocument()
  expect(screen.getByText(/^shoes$/i)).toBeInTheDocument()
  expect(screen.getByText(/^extras$/i)).toBeInTheDocument()
})
```

## Artifact: Both entry points link to `/assemble`

**What it proves:** The Stylist screen (`/`) renders a "Build it yourself"
link near its chat entry point, and the populated `WardrobeGrid` (`/wardrobe`)
renders the same link in its toolbar — both resolving to `href="/assemble"`.

**Why it matters:** The spec requires `/assemble` to be reachable from both
existing screens, not just typeable in the address bar.

**Command:**

```bash
cd frontend && npm test -- --run Stylist.test.tsx WardrobeGrid.test.tsx
```

**Result summary:** Both suites pass in full (18 + 5 = 23 tests), including
the two new link assertions.

```
 ✓ src/routes/WardrobeGrid.test.tsx (5 tests) 87ms
 ✓ src/routes/Stylist.test.tsx (18 tests) 1199ms
```

```ts
// Stylist.test.tsx
it('exposes an entry-point link to the manual assembly screen', () => {
  renderStylist()
  expect(screen.getByRole('link', { name: /build it yourself/i })).toHaveAttribute(
    'href',
    '/assemble',
  )
})
```

```ts
// WardrobeGrid.test.tsx
it('exposes an entry-point link to the manual assembly screen in the populated grid', async () => {
  listItemsMock.mockResolvedValue([item('a')])
  renderGrid()
  const link = await screen.findByRole('link', { name: /build it yourself/i })
  expect(link).toHaveAttribute('href', '/assemble')
})
```

## Artifact: Full frontend suite stays green (no regression)

**What it proves:** Adding the new route, component, and links did not break
any existing screen, API client, or lib module.

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** 225/225 tests pass across 24 files (up from 218/23 before
this task — 7 new tests added: 2 in `App.test.tsx`, 3 in `Assemble.test.tsx`,
1 in `Stylist.test.tsx`, 1 in `WardrobeGrid.test.tsx`).

```
 Test Files  24 passed (24)
      Tests  225 passed (225)
```

## Artifact: Quality gates — lint and typecheck

**What it proves:** The new code follows the repo's ESLint config and
compiles under strict TypeScript with no errors.

**Command:**

```bash
cd frontend && npm run lint
cd frontend && npx tsc -b --noEmit
```

**Result summary:** Both commands exit with no output (clean pass).

## Artifact: No backend surface touched

**What it proves:** This task is frontend-only, as required ("No backend
changes: no Java/Gradle/DynamoDB changes; no new API contract").

**Command:**

```bash
git diff --stat
```

**Result summary:** Only `frontend/**` files and the spec's own task-list doc
changed — no `src/main/java/**` path appears.

```
 docs/specs/21-spec-manual-outfit-assembly/21-tasks-manual-outfit-assembly.md | 16 ++--
 frontend/src/App.test.tsx                                                   | 17 +++++
 frontend/src/App.tsx                                                        | 11 ++-
 frontend/src/index.css                                                      | 85 ++++++++++++++++++++++
 frontend/src/routes/Stylist.test.tsx                                        |  9 +++
 frontend/src/routes/Stylist.tsx                                             |  5 ++
 frontend/src/routes/WardrobeGrid.test.tsx                                   |  9 +++
 frontend/src/routes/WardrobeGrid.tsx                                        |  3 +
 8 files changed, 143 insertions(+), 12 deletions(-)
```

(New untracked files not shown by `--stat`: `frontend/src/components/Mannequin.tsx`,
`frontend/src/routes/Assemble.tsx`, `frontend/src/routes/Assemble.test.tsx` —
all frontend-only, confirmed via `git status --short`.)

## Screenshot artifacts — DEFERRED to manual verification

The spec's proof-artifact list for this unit calls for two screenshots:
`/assemble` with a populated wardrobe (mannequin + four labeled zones + source
list, ~390px viewport) and `/assemble` with an empty wardrobe (the "add items"
message). **This implementation run is headless — there is no browser or
device available to capture a real screenshot.** Rather than fabricate an
image or tool output, these two artifacts are explicitly deferred to manual,
on-device verification before the spec's validation phase treats them as
closed.

The visual behavior they would demonstrate is instead covered by the
machine-verifiable automated tests above: the route renders (`App.test.tsx`),
the empty state renders the correct markup (`Assemble.test.tsx`), the four
zone labels render (`Assemble.test.tsx`), and both entry-point links exist
(`Stylist.test.tsx`, `WardrobeGrid.test.tsx`). A reviewer can additionally run
`npm run dev` locally and navigate to `/assemble` to see the rendered scaffold
directly.

## Reviewer Conclusion

`/assemble` is a real, gated, tested route: it sits inside `AuthGate` exactly
like every other protected screen, degrades gracefully on an empty wardrobe or
a failed fetch, renders a static mannequin with four correctly-labeled and
`Slot`-keyed drop zones ready for the next unit's drag wiring, and is linked
from both existing entry points. The full 225-test frontend suite, ESLint, and
TypeScript all stay green, and no backend file was touched. The only artifact
not machine-verified in this run is the pair of screenshots, which are
explicitly flagged above as deferred rather than faked.
