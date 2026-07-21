# Task 03 Proofs - Tag-preview concurrency throttle + graceful daily-cap (429) handling

## Task Summary

This task keeps a large batch from hammering the vision endpoint and turns a
mid-batch **daily-cap `429`** into a graceful, work-preserving state rather than a
hard failure. It adds a pure, React-independent concurrency limiter that caps
simultaneous `tagPreview` calls to **3**, introduces a typed `ApiError { status }`
so the queue detects a `429` by status (not by string-matching the message), and
wires both into the `/add` review queue: on a `429` auto-tagging stops, every
not-yet-tagged tile stays on the blank editable seed, and a persistent non-blocking
banner explains that items can still be tagged and saved by hand.

It matters because batch multiplies vision calls against the existing ~100/day cap
(spec Non-Goal #3: we do not raise the cap, we handle the `429` gracefully). No new
backend surface is added — the throttle and cap handling are entirely client-side.

## What This Task Proves

- Concurrent `tagPreview` fan-out is throttled to a small limit (3); a large batch
  fans out a bounded number of simultaneous requests and the rest queue (Unit 3 FR:
  concurrency throttle).
- A `429` is detected distinctly from other errors via a typed `ApiError.status`,
  with the existing `401` re-auth path in `http.ts` untouched (Unit 3 FR: typed 429
  detection).
- A mid-batch `429` stops further auto-tag previews, leaves remaining tiles on the
  editable `EMPTY_SUGGESTION` seed (nothing lost), and shows the persistent cap
  banner (Unit 3 FR: stop + preserve + banner).
- After a `429`, manual tag edit + "Save all" still persists the preserved items —
  the cap blocks only auto-tagging, never save (Unit 3 FR: save-after-429).
- A per-item **non-429** tag failure remains the Unit 1 degraded fallback for that
  tile alone and does **not** trip the cap banner (Unit 3 FR: non-429 isolation).

## Design Decisions (within spec latitude)

- **Concurrency limit = 3** — the spec's Open Question 1 assumed value (its "2–3"
  range), encoded as the `runWithConcurrency` default and the `TAG_CONCURRENCY`
  constant in `AddItem.tsx`. Tunable in one place without changing scope.
- **`allSettled` semantics in the pool** — `runWithConcurrency` returns per-task
  settled results so one rejection never aborts the batch; the Add screen's
  per-item success/failure handling lives in `tagItem` (kept out of the pure pool).
- **Cap stop via a mirrored ref** — `capReachedRef` mirrors the `capReached` state
  so in-flight pool tasks read the latest value synchronously (React state closes
  over a stale value); the ref is written only in an async catch, never during
  render, satisfying the `react-hooks/refs` ESLint rule.
- **Neutral `.banner-warn`** — the cap notice reuses the existing `.banner` class
  with a new neutral `.banner-warn` (paper-sunk / ink / hairline tokens); it is not
  an error (red) and not a success (accent), matching "persistent, non-blocking".

## Evidence Summary

- `promisePool.test.ts` (4 tests) proves the limiter never exceeds 3 concurrent
  tasks, reaches 3, completes every task in order, and surfaces each result/rejection.
- `items.test.ts` (21 tests) proves `tagPreview` rejects with a typed `ApiError`
  whose `status` is `429` (and `400`), while `http.test.ts` (6 tests) stays green —
  the `401` re-auth path is unchanged.
- `AddItem.test.tsx` (17 tests) proves the throttle (peak of 3 for a 7-file batch),
  the mid-batch `429` stop + preserve + banner, non-429 isolation, and save-after-429.
- The whole frontend suite (19 files, **194 tests**) passes and `eslint .` is clean.

## Artifact: Concurrency limiter unit tests (RED → GREEN)

**What it proves:** The pure `runWithConcurrency(tasks, limit = 3)` never runs more
than the limit at once (observed peak), reaches the limit when there is enough work,
runs every task to completion in order, and preserves each task's result/rejection.

**Why it matters:** This is the isolated, React-independent proof of the throttle
that the batch relies on; testing it directly (per `docs/TESTING.md`) is cheaper and
more reliable than inferring it only through the screen.

**Command:**

```bash
cd frontend && npm test -- --run src/lib/promisePool.test.ts
```

**Result summary:** 4/4 pass — peak concurrency is exactly 3 for 9 tasks at limit 3
(and 6 tasks at the default), an empty list resolves to `[]`, and a middle rejection
does not abort the siblings.

```
✓ src/lib/promisePool.test.ts (4 tests)
  ✓ never runs more than the limit (3) at once while completing every task in order
  ✓ surfaces each task’s result or rejection independently (one failure never aborts the rest)
  ✓ resolves to an empty array for no tasks
  ✓ defaults the limit to 3 when none is given
```

## Artifact: Typed `ApiError` status detection (RED → GREEN), `401` path unchanged

**What it proves:** `tagPreview` rejects with a typed `ApiError` exposing
`status === 429` on the daily cap (and `400` for other non-2xx), so the queue reacts
to the code without string-matching. `http.test.ts` staying green confirms the
`401` re-auth behaviour in `http.ts` is untouched.

**Why it matters:** Distinguishing a `429` from any other tag failure is the hinge
of the graceful-cap behaviour; doing it by typed status (not message text) is the
spec's Technical Consideration.

**Command:**

```bash
cd frontend && npm test -- --run src/api/items.test.ts src/api/http.test.ts
```

**Result summary:** 27/27 pass (items 21, http 6). The two new items tests assert
`ApiError` + `status` for `429` and `400`; the existing non-2xx/network tests and the
whole `http` re-auth suite are unchanged and green.

```
✓ src/api/http.test.ts (6 tests)
✓ src/api/items.test.ts (21 tests)
  ✓ tagPreview > rejects with a typed ApiError carrying status 429 on the daily cap
  ✓ tagPreview > rejects with an ApiError carrying the status for other non-2xx responses
```

## Artifact: Review-queue throttle + graceful-cap behaviour tests (RED → GREEN)

**What it proves:** In the real screen, a 7-file batch never runs more than 3
tag-preview calls at once; a mid-batch `429` stops further auto-tagging (the 4th
preview never fires), keeps all four tiles editable, and shows the cap banner; a
non-429 failure shows no banner; and after a `429` a manual edit + "Save all" still
persists via `createItem`.

**Why it matters:** These are the Unit 3 functional requirements exercised end to end
through the queue, alongside the preserved Unit 1–2 behaviours (still green).

**Command:**

```bash
cd frontend && npm test -- --run src/routes/AddItem.test.tsx
```

**Result summary:** 17/17 pass — the 13 prior queue tests plus the four new Unit 3
tests (throttle peak-of-3, mid-batch 429 stop+preserve+banner, non-429 isolation,
save-after-429).

```
✓ src/routes/AddItem.test.tsx (17 tests)
  ✓ throttles auto-tag fan-out to at most 3 tag-preview calls at once for a large batch
  ✓ on a mid-batch 429 stops further auto-tagging, keeps every tile editable, and shows the cap banner
  ✓ does not show the cap banner for a non-429 tag-preview failure (Unit 1 degraded fallback only)
  ✓ still saves the preserved tiles after a 429 (the cap blocks only auto-tagging)
```

## Artifact: Full frontend suite + lint (no regression)

**What it proves:** Introducing the pool, the typed `ApiError`, and the cap wiring
did not regress any existing frontend behaviour, and the code passes the repo's
ESLint gate.

**Why it matters:** `ApiError` replaces the generic `Error` thrown by `ensureOk`
(shared by every `items.ts` caller) and `AddItem`'s fan-out changed shape; a green
whole-suite run + clean lint confirm both were strictly compatible.

**Command:**

```bash
cd frontend && npm test -- --run && npm run lint
```

**Result summary:** 19 files, **194 tests** pass (up from 184: +4 promisePool, +2
items, +4 AddItem); `eslint .` exits 0 with no errors or warnings.

```
Test Files  19 passed (19)
     Tests  194 passed (194)
```

## Reviewer Conclusion

Unit 3 is implemented and fully covered by automated tests: the vision fan-out is
throttled to 3, a `429` is detected by typed status, and a mid-batch cap becomes a
graceful, work-preserving state (auto-tag stops, tiles stay editable, banner shows,
save still works) while non-429 failures remain the per-item degraded fallback. No
backend endpoints were added, the `401` re-auth path is untouched, and the whole
suite is green with a clean lint. This task has no screenshot artifact — its
behaviours are queue/logic and proven by the tests above.
