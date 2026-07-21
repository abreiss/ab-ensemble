# 13-tasks-image-import-options.md

Task plan for the **image-import-options** feature (branch `image-import-options`).
Derived from [`13-spec-image-import-options.md`](./13-spec-image-import-options.md).

Each parent task is a demoable unit of work and maps 1:1 to a spec "Demoable Unit
of Work", built in dependency order: the shared queue spine first, then the three
acquisition sources and the batch-resilience layer that all build on it. This is
a **frontend-led** feature — no backend endpoints change. Per
[docs/TESTING.md](../../TESTING.md) these are frontend UI-wiring tasks: test the
meaningful queue/source/save logic (Vitest + RTL, Arrange-Act-Assert), and do
**not** over-test `<video>`/canvas/render plumbing.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/src/routes/AddItem.tsx` | Core screen refactored from single-photo state into a `1..N` review queue behind a shared `enqueue`; hosts all four sources (file, paste, camera) + "Save all". |
| `frontend/src/routes/AddItem.test.tsx` | Integration tests for the queue, all sources, throttle/cap, and independent save; extends the existing N=1 tests to prove parity. |
| `frontend/src/lib/promisePool.ts` | New pure, React-independent concurrency limiter (default 3) that throttles `tagPreview` fan-out. |
| `frontend/src/lib/promisePool.test.ts` | Unit tests: never exceeds the limit; runs all tasks; propagates per-task results/rejections. |
| `frontend/src/lib/imageFile.ts` | New pure helper wrapping a `Blob` as a valid `image/*` `File` (synthesized name + type) — shared by paste and camera. |
| `frontend/src/lib/imageFile.test.ts` | Unit tests for name/type synthesis and MIME fallback. |
| `frontend/src/lib/clipboardImages.ts` | New pure helpers: extract image `File`s from a paste event's `clipboardData` and from async `ClipboardItem`s; async-clipboard capability detection. |
| `frontend/src/lib/clipboardImages.test.ts` | Unit tests for image extraction (incl. multiple), non-image filtering, and capability gating. |
| `frontend/src/components/CameraCapture.tsx` | New component owning the `getUserMedia` viewfinder, multi-shot capture, guaranteed track teardown, and permission/no-device fallback. |
| `frontend/src/components/CameraCapture.test.tsx` | Tests the mandated teardown (`stop()` on every track), `getUserMedia` rejection fallback, and capture → `onDone(files)`. |
| `frontend/src/api/items.ts` | Add a typed `ApiError { status }` thrown by `ensureOk` so a mid-batch `429` is detectable without string matching; `tagPreview`/`createItem` reused as-is otherwise. |
| `frontend/src/api/items.test.ts` | Add a test that a `429` rejects with `ApiError.status === 429`; existing non-2xx/401 behaviour unchanged. |
| `frontend/src/api/http.ts` | Unchanged — referenced to confirm the `401` re-auth path stays untouched by the `ApiError` change. |
| `frontend/src/components/TagForm.tsx` | Reused per tile; may gain a lightweight way to surface validated tags up to the queue so "Save all" can read each tile's tags. |
| `frontend/src/index.css` | Add review-queue tile, sticky "Save all", cap-banner, and camera-surface styles, reusing existing `photo-picker`/`banner`/`state-note`/`btn` classes and maroon/beige variables. |
| `docs/specs/13-spec-image-import-options/proof/` | Proof screenshots (batch queue, paste import, camera viewfinder) — demo content only, no passcode/token. |

### Notes

- Tests are co-located next to the unit under test (e.g. `AddItem.test.tsx`,
  `promisePool.test.ts`), matching the existing repo layout.
- Run the suite with `cd frontend && npm run test -- --run` (optionally scope to a
  file: `npm test -- --run src/routes/AddItem.test.tsx`). Lint with
  `cd frontend && npm run lint`. Both are the pre-commit gates.
- Follow Red→Green→Refactor: each parent task's first sub-task writes failing
  tests; keep the full suite green before moving on.
- Do not add backend endpoints, a batch API, client-side downscale, or new
  secrets (spec Non-Goals) — batch is a client-side loop over the existing
  single-photo endpoints.

## Tasks

### [x] 1.0 Review-queue refactor — lift `AddItem` to a `1..N` queue behind a shared `enqueue`, with `N=1` preserved exactly

Pure refactor of `frontend/src/routes/AddItem.tsx`: replace the single
`photo`/`previewUrl`/`phase`/`suggestion`/`error` state with an ordered list of
pending items `{ id, file, previewUrl, phase, suggestion, error }`, all fed by one
`enqueue(files: File[])` entry point. The existing single-file `<input>` calls
`enqueue([file])`; auto-tag, the per-item stale-response guard, the
degraded/failed-tagging blank-editable fallback, and object-URL cleanup are all
generalised to per-item. This is the foundation every source in tasks 2–5 builds
on. (Spec Unit 1; FRs: per-item state model, single `enqueue`, per-item auto-tag,
per-item request-id guard, per-item degraded fallback, per-item URL revoke, N=1
parity.)

#### 1.0 Proof Artifact(s)

- Test: `frontend/src/routes/AddItem.test.tsx` — `enqueue([file])` with one file
  produces exactly one queue tile that auto-tags and renders an editable
  `TagForm`, then saves and returns to `/wardrobe` (the preserved N=1 path). Run
  `cd frontend && npm test -- --run src/routes/AddItem.test.tsx`. Demonstrates FR
  "N=1 experience identical to today".
- Test: `AddItem.test.tsx` — an out-of-order (stale) tag response for tile A
  seeds only tile A and never overwrites tile B (per-item request-id guard).
  Demonstrates the generalised stale-response guard.
- Test: `AddItem.test.tsx` — a rejected `tagPreview` (and an all-null suggestion)
  leaves that tile on the blank-but-editable `EMPTY_SUGGESTION` seed, never an
  error/crash. Demonstrates the preserved degraded-tagging fallback.
- Test: `AddItem.test.tsx` — each item's object URL is revoked on remove, on save,
  and on unmount (per-item / `Map` cleanup — no single-URL leak with many items).
  Demonstrates FR "revoke each item's object URL on removal/save/unmount".
- CLI: `cd frontend && npm test -- --run` exits 0 with all prior `AddItem` tests
  plus the new ones green. Demonstrates behaviour parity / no regression.

#### 1.0 Tasks

- [x] 1.1 (RED) In `AddItem.test.tsx`, rewrite the existing suite against the queue
  model and add the new behaviours: `enqueue([file])` yields one tile → auto-tag →
  editable form → save → `/wardrobe`; a stale/out-of-order tag response seeds only
  its own tile; a rejected/all-null `tagPreview` leaves that tile on the editable
  `EMPTY_SUGGESTION` seed; each item's object URL is revoked on remove/save/unmount.
  Run the suite and confirm the new tests fail (RED).
- [x] 1.2 (GREEN) Define a `PendingItem` type `{ id, file, previewUrl, phase,
  suggestion, error }` and lift `AddItem` state to `PendingItem[]`; keep
  `EMPTY_SUGGESTION` in `AddItem.tsx`. Render the queue as an ordered list of tiles.
- [x] 1.3 (GREEN) Implement `enqueue(files: File[])`: for each file create a
  pending item with a unique id, its own object URL, and its own request id; append
  to the queue and trigger its tag-preview. Point the single-file `<input>`
  `onChange` at `enqueue([file])`.
- [x] 1.4 (GREEN) Per-item auto-tag: show a per-tile "tagging…" state, then render
  that item's `TagForm` (keyed by item id) seeded from its suggestion. Guard each
  response with the item's own request id so an out-of-order response only seeds its
  own tile. Preserve the degraded/all-null fallback per item (→ `EMPTY_SUGGESTION`).
- [x] 1.5 (GREEN) Per-item object-URL lifecycle: hold URLs per item (e.g. a `Map`
  or on the `PendingItem`); revoke on remove, on that item's save, and revoke all
  remaining on unmount. Remove the old single-URL ref logic.
- [x] 1.6 (REFACTOR) Confirm the N=1 render reads identically to today; run
  `cd frontend && npm run test -- --run` (all green) and `npm run lint` (clean).

### [~] 2.0 Batch library multi-select + "Save all" fan-out with independent per-item save

Add `multiple` to the file input so `onSelect` reads the whole `FileList` and calls
`enqueue([...files])`; render each queued item as a thumbnail + compact tag summary
that expands to the shared `TagForm`, plus a per-item **remove** control (revoking
that item's URL). Add a sticky **"Save all"** that fans out `createItem(file, tags)`
in a client-side loop with running progress ("3 of 8 saved"). Saves are
**independent**: a per-item failure marks that tile failed and keeps it in the queue
with its edited tags intact and retryable; successes are removed. Navigate to
`/wardrobe` only when the queue fully drains. (Spec Unit 2; FRs: `multiple` +
`FileList` enqueue, per-item tile+form+remove, "Save all" fan-out with progress,
independent per-item save, drained-queue completion, preserved `accept="image/*"` /
"Change photo" semantics.)

#### 2.0 Proof Artifact(s)

- Test: `AddItem.test.tsx` — selecting a multi-file `FileList` enqueues `N` tiles,
  each auto-tagged and individually editable. Run
  `cd frontend && npm test -- --run src/routes/AddItem.test.tsx`. Demonstrates
  batch acquisition.
- Test: `AddItem.test.tsx` — "Save all" with a mix of successes and one
  `createItem` rejection removes the successful tiles, keeps the failed tile (with
  its edited tags) in the queue, and surfaces a retryable error. Demonstrates
  independent per-item save.
- Test: `AddItem.test.tsx` — an all-success "Save all" navigates to `/wardrobe`.
  Demonstrates the drained-queue completion path.
- Test: `AddItem.test.tsx` — the per-item remove control drops that tile and
  revokes only that item's object URL; the input keeps `accept="image/*"` and no
  `capture` attribute. Demonstrates per-item remove/cleanup + preserved picker
  semantics.
- Screenshot: `docs/specs/13-spec-image-import-options/proof/batch-review-queue.png`
  — the `/add` review queue showing multiple tiles with editable tags and "Save
  all" progress, using demo wardrobe content only (no passcode/token visible).
  Demonstrates the batch review UX.

#### 2.0 Tasks

- [x] 2.1 (RED) In `AddItem.test.tsx`, add tests: a multi-file `FileList` enqueues
  `N` editable, auto-tagged tiles; "Save all" with one `createItem` rejection keeps
  the failed tile (edited tags intact, retryable) and removes the successes with a
  surfaced error; an all-success "Save all" navigates to `/wardrobe`; the per-item
  remove control drops a tile and revokes only its URL. Confirm RED.
- [x] 2.2 (GREEN) Add `multiple` to the `<input type="file">`; `onSelect` reads the
  whole `FileList` and calls `enqueue([...files])`. Keep `accept="image/*"`, no
  `capture`, and the "Change photo"/"Add more" affordance.
- [x] 2.3 (GREEN) Render each tile: thumbnail + a compact tag summary that expands
  to the shared `TagForm`, plus a per-item remove control that removes the tile and
  revokes its object URL. Add minimal review-queue classes to `index.css`, reusing
  existing styles.
- [x] 2.4 (GREEN) Surface each tile's validated `TagInput` up to the queue (e.g.
  `TagForm` reports its valid tags on change/commit) so "Save all" can read them.
  Implement "Save all": loop `createItem(file, tags)` per tile with running progress
  ("N of M saved"); on success remove the tile; on failure mark it failed, keep its
  edited tags, and surface a retryable error. Navigate to `/wardrobe` only when the
  queue is fully drained.
- [~] 2.5 (GREEN) Run the app locally, add a small batch, and capture
  `proof/batch-review-queue.png` (multiple tiles + "Save all" progress; demo
  content only, no secrets). **Pending manual capture** — needs the running
  backend + frontend (ideally a Claude key for live auto-tags); this session has
  no headless browser and won't fabricate the image. Exact repro steps are in
  `13-proofs/13-task-02-proofs.md`; target path
  `13-proofs/assets/batch-review-queue.png` (repo convention; the `proof/` path
  above is shorthand).
- [x] 2.6 (REFACTOR) Run `cd frontend && npm run test -- --run` (all green) and
  `npm run lint` (clean).

### [x] 3.0 Tag-preview concurrency throttle + graceful daily-cap (429) handling

Add a pure, React-independent promise-pool helper in `frontend/src/lib/` that caps
concurrent `tagPreview` calls to a small limit (default **3**); a large batch fans
out a bounded number of simultaneous vision requests and the rest queue. Surface the
HTTP status distinctly by introducing a typed `ApiError { status }` thrown by
`ensureOk` in `frontend/src/api/items.ts` (leaving the existing `401` re-auth path in
`http.ts` untouched), so the queue detects a mid-batch `429` without string-matching.
On a `429`: stop firing further auto-tag previews, leave every not-yet-tagged item on
the editable `EMPTY_SUGGESTION` seed, and show a persistent non-blocking banner
("Daily AI limit reached — you can still tag and save these manually."). Manual
tagging and "Save all" still work after a `429` (the cap blocks only auto-tagging). A
per-item **non-429** tag failure remains the Unit-1 degraded fallback for that item
only and does **not** trip the cap banner. (Spec Unit 3; FRs: concurrency throttle,
typed 429 detection, mid-batch 429 stop+preserve+banner, save-after-429, non-429
failure isolation.)

#### 3.0 Proof Artifact(s)

- Test: `frontend/src/lib/promisePool.test.ts` (new) — driving many tasks through
  the pool never runs more than the limit (3) simultaneously; remaining tasks fire
  as slots free and all resolve. Run
  `cd frontend && npm test -- --run src/lib/promisePool.test.ts`. Demonstrates the
  throttle in isolation.
- Test: `frontend/src/api/items.test.ts` — a `429` response makes `tagPreview`
  reject with a typed `ApiError` whose `status === 429` (and other non-2xx still
  reject; the `401` re-auth path is unchanged). Demonstrates typed-status detection
  without string matching.
- Test: `AddItem.test.tsx` — a `429` on one preview mid-batch stops further
  auto-tag calls, leaves remaining tiles on the editable blank seed, and shows the
  cap banner; a subsequent non-429 tag failure on another tile does not show the
  cap banner. Demonstrates the graceful-cap state and non-429 isolation.
- Test: `AddItem.test.tsx` — after a `429`, a manual tag edit + "Save all" still
  persists the preserved items via `createItem`. Demonstrates that the cap blocks
  only auto-tagging, never save.

#### 3.0 Tasks

- [x] 3.1 (RED) Write `frontend/src/lib/promisePool.test.ts`: a pool with limit 3
  never runs more than 3 tasks concurrently (assert observed peak concurrency),
  runs all tasks to completion, and surfaces each task's result/rejection. Confirm
  RED.
- [x] 3.2 (GREEN) Implement `frontend/src/lib/promisePool.ts` — a minimal
  `runWithConcurrency(tasks, limit = 3)` (or equivalent pool), pure and independent
  of React.
- [x] 3.3 (RED) In `items.test.ts`, add a test that a `429` makes `tagPreview`
  reject with an `ApiError` exposing `status === 429`, and that other non-2xx still
  reject. Confirm RED (current `ensureOk` throws a plain `Error`).
- [x] 3.4 (GREEN) Add `class ApiError extends Error { status: number }` to
  `items.ts` and have `ensureOk` throw it with `response.status`; keep the existing
  messages and leave `http.ts`'s `401` handling untouched. Confirm `http.test.ts`
  stays green.
- [x] 3.5 (RED) In `AddItem.test.tsx`, add tests: enqueuing many files never runs
  more than 3 `tagPreview` calls at once; a `429` mid-batch stops further auto-tag
  calls, leaves remaining tiles on the editable blank seed, and shows the cap
  banner; a non-429 tag failure does not show the cap banner; after a `429`, a
  manual edit + "Save all" still persists. Confirm RED.
- [x] 3.6 (GREEN) Route `enqueue`'s per-item `tagPreview` fan-out through the
  promise-pool (limit 3). On an `ApiError` with `status === 429`: set a
  `capReached` flag that skips not-yet-started auto-tags (leaving those tiles on the
  editable `EMPTY_SUGGESTION` seed) and renders the persistent cap banner; a non-429
  rejection remains the per-item degraded fallback (no banner). Ensure "Save all" is
  unaffected by the cap.
- [x] 3.7 (REFACTOR) Run `cd frontend && npm run test -- --run` (all green) and
  `npm run lint` (clean).

### [x] 4.0 Clipboard paste — `paste` event + capability-gated "Paste image" button

Attach a `paste`-event handler on `/add` that reads
`ClipboardEvent.clipboardData.items` for `image/*` entries, calls `getAsFile()`, and
pushes the file(s) through `enqueue`. Add an explicit **"Paste image"** button that
uses `navigator.clipboard.read()` to read image `ClipboardItem`s and `enqueue` them,
gated by capability detection (`navigator.clipboard?.read` /
`ClipboardItem.supports('image/png')`) and hidden/disabled with a hint where
unsupported. Wrap each pasted `Blob` as a valid `image/*` `File` (synthesized name +
reported type) before enqueuing — no client resize (the backend re-encodes to ≤800px
JPEG). Multiple pasted images enqueue as a batch, reusing the throttle/cap/save
behaviour from tasks 2–3. (Spec Unit 4; FRs: paste-event handler, gated async-read
button, Blob→File wrapping, multi-paste batch.)

#### 4.0 Proof Artifact(s)

- Test: `frontend/src/lib/imageFile.test.ts` + `clipboardImages.test.ts` (new) — a
  `Blob` is wrapped as a `File` with a synthesized name and its reported `image/*`
  type (fallback for a missing type); the clipboard extractor returns **all** image
  entries and filters non-image entries; capability detection reports false when
  `navigator.clipboard?.read`/`ClipboardItem.supports` is absent. Run
  `cd frontend && npm test -- --run src/lib`. Demonstrates Blob→File wrapping and
  multi-paste extraction.
- Test: `AddItem.test.tsx` — a `paste` event carrying an `image/png` item enqueues
  one wrapped-`File` tile that tags like any other source. Demonstrates the
  event path end to end.
- Test: `AddItem.test.tsx` — the "Paste image" button is hidden/disabled when the
  async clipboard-read capability is absent and present when available.
  Demonstrates capability gating.
- Screenshot:
  `docs/specs/13-spec-image-import-options/proof/paste-import-queue.png` — an item
  pasted onto `/add` appearing in the review queue with tags, demo content only.
  Demonstrates end-to-end paste import.

#### 4.0 Tasks

- [x] 4.1 (RED) Write `frontend/src/lib/imageFile.test.ts` (Blob→File name/type
  synthesis + MIME fallback) and `frontend/src/lib/clipboardImages.test.ts` (extract
  all image files from clipboard items, filter non-images, capability detection).
  Confirm RED.
- [x] 4.2 (GREEN) Implement `frontend/src/lib/imageFile.ts`:
  `blobToImageFile(blob, namePrefix)` → a `File` with a synthesized name and the
  blob's `image/*` type (fallback `image/png`).
- [x] 4.3 (GREEN) Implement `frontend/src/lib/clipboardImages.ts`:
  `imageFilesFromClipboardData(dataTransfer)` (reads `items` → `getAsFile`),
  `imageFilesFromClipboardItems(items)` (async `getType`), and
  `clipboardReadSupported()` capability check; wrap blobs via `imageFile.ts`.
- [x] 4.4 (RED) In `AddItem.test.tsx`, add tests: a `paste` event carrying an
  `image/png` item enqueues one tile that tags; the "Paste image" button is
  hidden/disabled when `clipboardReadSupported()` is false and shown when true.
  Confirm RED.
- [x] 4.5 (GREEN) In `AddItem`, attach an `onPaste` handler (extract → `enqueue`)
  and render the capability-gated "Paste image" button that calls
  `navigator.clipboard.read()` → extract → `enqueue`; hide/disable with a hint when
  unsupported. Reuse the tasks 2–3 throttle/cap/save path.
- [~] 4.6 (GREEN) Capture `proof/paste-import-queue.png` (pasted item in the queue
  with tags; demo content only). **Pending manual capture** — needs the running
  backend + frontend (ideally a Claude key for live auto-tags); this session has no
  headless browser and won't fabricate the image. Exact repro steps + target path
  (`13-proofs/assets/paste-import-queue.png`) are in `13-proofs/13-task-04-proofs.md`.
- [x] 4.7 (REFACTOR) Run `cd frontend && npm run test -- --run` (all green) and
  `npm run lint` (clean).

### [ ] 5.0 In-app live camera with multi-shot capture and guaranteed stream teardown

Add a **"Take photos"** control that opens an in-app camera via
`getUserMedia({ video: { facingMode: 'environment' } })` into a `<video>`
viewfinder, with a shutter that captures the current frame to `<canvas>` →
`toBlob()` → `File`. Support **multi-shot**: the camera stays open after a capture,
frames accumulate as thumbnails, and **"Done"** pushes all captured files onto the
review queue via `enqueue` (a single shot + Done is just the N=1 case). **Stop all
`MediaStreamTrack`s** on Done, cancel, and unmount (hard requirement — no dangling
camera indicator). Handle permission-denied and no-camera-available with a clear
message and a file-picker fallback; require a secure context and degrade to the file
picker where `getUserMedia` is unavailable. (Spec Unit 5; FRs: getUserMedia
viewfinder + shutter, multi-shot accumulate + Done→enqueue, guaranteed track
teardown, permission/no-device fallback, secure-context degrade.)

#### 5.0 Proof Artifact(s)

- Test: `frontend/src/components/CameraCapture.test.tsx` (new) — unmounting and
  closing/cancelling the open camera calls `stop()` on **every** acquired
  `MediaStreamTrack`. Run
  `cd frontend && npm test -- --run src/components/CameraCapture.test.tsx`.
  Demonstrates guaranteed teardown (the dedicated test the spec mandates).
- Test: `CameraCapture.test.tsx` — a `getUserMedia` rejection (permission denied /
  no device) renders the clear message + a file-picker fallback control.
  Demonstrates graceful degradation.
- Test: `AddItem.test.tsx` — capturing frames then "Done" enqueues each captured
  file as a queue tile that then auto-tags (single shot = N=1). Demonstrates
  multi-shot → queue.
- Screenshot:
  `docs/specs/13-spec-image-import-options/proof/camera-viewfinder-mobile.png` —
  the in-app viewfinder with accumulated shot thumbnails on a mobile viewport
  (secure-context capture on `localhost` or an installed PWA per the spec's open
  risks), demo content only. Demonstrates the capture UX.

#### 5.0 Tasks

- [ ] 5.1 (RED) Write `frontend/src/components/CameraCapture.test.tsx`, mocking
  `navigator.mediaDevices.getUserMedia` and `canvas.toBlob`: assert every acquired
  track's `stop()` is called on unmount and on cancel/Done; a `getUserMedia`
  rejection renders the message + file-picker fallback; capturing then "Done" calls
  `onDone` with the captured `File`(s). Confirm RED.
- [ ] 5.2 (GREEN) Implement `frontend/src/components/CameraCapture.tsx`: open
  `getUserMedia({ video: { facingMode: 'environment' } })` into a `<video>`; shutter
  captures the frame to `<canvas>` → `toBlob()` → `blobToImageFile`; accumulate
  thumbnails; "Done" → `onDone(files)`; "Cancel"/unmount stop all tracks;
  permission-denied/no-device → message + fallback that invokes the file picker;
  guard for `getUserMedia` availability / secure context.
- [ ] 5.3 (RED) In `AddItem.test.tsx`, add a test that the "Take photos" control
  mounts `CameraCapture` and a multi-shot "Done" enqueues each captured file as a
  tile that auto-tags; the control is absent (file picker only) when `getUserMedia`
  is unavailable. Confirm RED.
- [ ] 5.4 (GREEN) Wire the "Take photos" control in `AddItem` to open
  `CameraCapture` and route its `onDone(files)` → `enqueue(files)`; render the
  control only where `getUserMedia` is available, otherwise rely on the always-
  present file picker.
- [ ] 5.5 (GREEN) Add camera-surface styles to `index.css` (full-width video, large
  shutter, thumbnail strip, Done/Cancel); capture
  `proof/camera-viewfinder-mobile.png` on a mobile viewport / secure context (demo
  content only).
- [ ] 5.6 (REFACTOR) Run `cd frontend && npm run test -- --run` (all green) and
  `npm run lint` (clean); manually confirm no dangling camera indicator after
  Done/cancel.
