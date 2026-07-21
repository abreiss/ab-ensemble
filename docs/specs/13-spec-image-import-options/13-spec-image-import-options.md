# 13-spec-image-import-options.md

## Introduction/Overview

The Add-item screen (`/add`) today accepts exactly one photo from a single
`<input type="file">`, tags it with Haiku 4.5 vision, and saves one item.
Building a wardrobe one photo at a time is the slowest part of onboarding. This
feature adds three new **image sources** to that screen — **batch library
multi-select**, **clipboard paste**, and an **in-app live camera** — and
refactors the screen from a single-photo flow into a **review queue** that holds
`1..N` pending items.

The unifying insight is that all three are just different *sources* of one or
more image `File`/`Blob`s. Once acquired, every one funnels into the *existing*
per-photo pipeline unchanged — tag-preview → editable `TagForm` → `createItem`.
So this is a **frontend-led** feature: it changes *how many* photos reach the
existing vision job and *where they come from*, **not what the model does**. Per
[docs/TESTING.md](../../TESTING.md) this is frontend UI wiring — test the
meaningful queue/source/save logic, not `<video>`/render plumbing.

## Goals

- Decouple "acquire image(s)" from the tag/save flow behind a single shared
  `enqueue(files: File[])` entry point, so adding a future source (drag-drop,
  share-target) is one call and nothing more.
- Turn `AddItem.tsx` into a review queue of `1..N` pending items that preserves
  today's single-photo behaviour **exactly** as the `N=1` case, including the
  degraded/failed-tagging blank-editable fallback and the stale-response guard
  (generalised per-item).
- Add three acquisition sources — batch multi-select, clipboard paste (event +
  button), and an in-app `getUserMedia` camera with multi-shot capture — each
  feeding the shared `enqueue`.
- Keep the batch economical and resilient: throttle concurrent `tagPreview`
  calls, handle a mid-batch daily-cap **429** gracefully (photos preserved,
  manual tag/save still possible), and handle per-item save failures without
  losing work.
- Add **no new backend endpoints**: batch is a client-side loop over the
  existing single-photo `POST /api/items/tag` and `POST /api/items`.

## User Stories

- **As a wardrobe owner**, I want to select multiple photos from my library at
  once so that I can add a whole batch of clothes without repeating the flow per
  item.
- **As a wardrobe owner**, I want to paste an image I copied (a screenshot, a
  product photo) so that I don't have to save it to my library first just to
  import it.
- **As a wardrobe owner**, I want the app to open a live camera and let me take a
  few garment shots in a row so that I can photograph a pile of clothes quickly.
- **As a wardrobe owner adding a batch**, I want each photo auto-tagged and shown
  in an editable review list so that I can fix any wrong tags before saving the
  whole set.
- **As a wardrobe owner**, if one photo fails or the daily AI cap is hit
  mid-batch, I want the rest of my work preserved and clearly explained so that I
  never lose photos I already picked.

## Demoable Units of Work

### Unit 1: Review-queue refactor (the shared spine, `N=1` preserved)

**Purpose:** Lift `AddItem.tsx`'s single-photo state into a list of `1..N`
pending items behind one shared `enqueue(files)`. Pure refactor — the existing
single-file picker pushes one item through the queue and behaves exactly as
today. This is the foundation every source builds on.

**Functional Requirements:**
- The system shall represent each pending item as
  `{ id, file, previewUrl, phase, suggestion, error }` (the per-item state the
  screen tracks today, lifted into a list) and render the queue as an ordered
  list of tiles.
- The system shall expose a single `enqueue(files: File[])` function that, for
  each file, creates a pending item (with its own object URL), appends it to the
  queue, and triggers its tag-preview; the existing single-file input shall call
  `enqueue([file])`.
- The system shall auto-fire `tagPreview(file)` per enqueued item, showing a
  per-tile "tagging…" state, then rendering that item's editable `TagForm`
  seeded from the suggestion.
- The system shall generalise the stale-response guard to **per-item**: each
  item carries its own request id so an out-of-order tag response can only seed
  its own tile (never another item's).
- The system shall preserve the degraded/failed-tagging fallback per item: a
  rejected tag-preview or an all-null suggestion leaves that tile on the blank
  but fully editable `EMPTY_SUGGESTION` seed — never an error, never a crash.
- The system shall revoke each item's object URL on its removal, on save, and on
  unmount (per-item / `Map` cleanup — no single-URL leak with many items).
- The system shall keep the `N=1` experience identical to today: one picked
  photo → one queued tile → auto-tag → editable form → save → return to
  `/wardrobe`.

**Proof Artifacts:**
- Test: `AddItem.test.tsx` — `enqueue` with one file produces one queue item that
  auto-tags and renders an editable form (the preserved `N=1` path) — demonstrates
  behaviour parity after the refactor.
- Test: a per-item stale/out-of-order tag response seeds only its own tile —
  demonstrates the generalised per-item request-id guard.
- Test: a rejected tag-preview leaves that tile on the blank editable seed —
  demonstrates the preserved degraded-tagging fallback.
- Test: `cd frontend && npm test -- --run` passes — demonstrates no regression.

### Unit 2: Batch library multi-select + "Save all" fan-out

**Purpose:** Let a user pick many library photos at once and save the reviewed
set, with independent per-item success/failure.

**Functional Requirements:**
- The system shall add `multiple` to the file input; `onSelect` shall read the
  whole `FileList` and call `enqueue([...files])`, so all picked photos enter the
  queue and auto-tag.
- The system shall render, per queued item, a thumbnail + an editable `TagForm`
  (or a compact tag summary that expands to the form) and a per-item **remove**
  control (which revokes that item's object URL).
- The system shall provide a **"Save all"** action that fans out
  `createItem(file, tags)` per queued item in a client-side loop, showing a
  running progress indicator (e.g. "3 of 8 saved").
- The system shall save items **independently**: a per-item `createItem` failure
  marks that tile failed and keeps it in the queue **with its edited tags
  intact** and retryable, while successfully saved items are removed from the
  queue.
- The system shall return to `/wardrobe` only when the queue is fully drained
  (all items saved); if any items remain failed, the screen stays on the queue
  with the failures surfaced.
- The system shall preserve `accept="image/*"` and the existing "Change photo"
  affordance semantics.

**Proof Artifacts:**
- Test: selecting a multi-file `FileList` enqueues `N` items each auto-tagged and
  individually editable — demonstrates batch acquisition.
- Test: "Save all" with a mix of successes and one `createItem` rejection removes
  the successes, keeps the failed tile (with its edited tags) in the queue, and
  surfaces a retryable error — demonstrates independent per-item save.
- Test: an all-success "Save all" navigates to `/wardrobe` — demonstrates the
  drained-queue completion path.
- Screenshot: the review queue on `/add` showing multiple tiles with editable
  tags + "Save all" progress — demonstrates the batch review UX.

### Unit 3: Tag-preview concurrency throttle + daily-cap (429) handling

**Purpose:** Keep a large batch from hammering the vision endpoint and make a
mid-batch daily-cap **429** a graceful, work-preserving state rather than a hard
failure.

**Functional Requirements:**
- The system shall throttle concurrent `tagPreview` calls to a small limit
  (default **3**) via a client-side concurrency helper, so a large batch fans out
  a bounded number of simultaneous vision requests; remaining items queue and
  fire as slots free.
- The system shall detect a `429` (daily-cap) response from `tagPreview`
  distinctly from other errors (via a typed error / surfaced status — see
  Technical Considerations), rather than string-matching.
- On a mid-batch `429`, the system shall: stop firing further auto-tag previews;
  leave every not-yet-tagged item on the blank but editable `EMPTY_SUGGESTION`
  seed (so nothing is lost); and show a persistent, non-blocking banner —
  "Daily AI limit reached — you can still tag and save these manually."
- The system shall still allow manual tagging and "Save all" of the preserved
  items after a `429` (a `429` blocks only auto-tagging, never save).
- The system shall treat a per-item non-429 tag failure as the existing
  degraded-tagging fallback for that item only (unchanged from Unit 1), without
  tripping the cap banner.

**Proof Artifacts:**
- Test: enqueuing many files never runs more than the concurrency limit of
  `tagPreview` calls at once — demonstrates the throttle.
- Test: a `429` on one preview mid-batch stops further auto-tag calls, leaves
  remaining items on the editable blank seed, and shows the cap banner —
  demonstrates the graceful-cap state.
- Test: after a `429`, manual tag edit + "Save all" still persists the preserved
  items — demonstrates that the cap blocks only auto-tagging.

### Unit 4: Clipboard paste (event + explicit button)

**Purpose:** Import an image straight from the clipboard, via both the desktop
Cmd/Ctrl-V path and a discoverable button.

**Functional Requirements:**
- The system shall attach a `paste`-event handler on the Add screen that reads
  `ClipboardEvent.clipboardData.items` for `image/*` entries, calls
  `getAsFile()`, and pushes the resulting file(s) through `enqueue`.
- The system shall render an explicit **"Paste image"** button that uses
  `navigator.clipboard.read()` where available to read image `ClipboardItem`s and
  `enqueue` them; the button shall be gated by capability detection
  (`navigator.clipboard?.read` / `ClipboardItem.supports('image/png')`) and
  hidden or disabled with a hint where unsupported.
- The system shall wrap a pasted `Blob` as a `File` with a synthesized name and
  its reported `image/*` type before enqueuing (screenshots are typically PNG;
  the backend re-encodes to ≤800px JPEG on save, so no client resize is needed).
- The system shall enqueue multiple pasted images as a batch (each its own queue
  item), reusing the same throttle/cap/save behaviour from Units 2–3.

**Proof Artifacts:**
- Test: a `paste` event carrying an `image/png` item wraps the blob as a `File`
  and enqueues one queue item that tags like any other source — demonstrates the
  event path and blob→File wrapping.
- Test: the "Paste image" button is hidden/disabled when the async clipboard read
  capability is absent — demonstrates capability gating.
- Screenshot: an item pasted onto `/add` appearing in the review queue with tags —
  demonstrates end-to-end paste import.

### Unit 5: In-app live camera with multi-shot capture

**Purpose:** Open a live camera inside the app and let the user snap one or
several garment photos in a row, with a hard requirement that the camera stream
is always torn down.

**Functional Requirements:**
- The system shall provide a **"Take photos"** control that opens an in-app
  camera via
  `navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })`
  rendered into a `<video>` viewfinder, with a shutter button that captures the
  current frame to a `<canvas>` → `toBlob()` → `File`.
- The system shall support **multi-shot**: the camera stays open after a capture,
  captured frames accumulate as thumbnails, and a **"Done"** action pushes all
  captured files onto the review queue via `enqueue` (which then auto-tags them);
  a single shot + "Done" is just the `N=1` case.
- The system shall **stop all `MediaStreamTrack`s** on Done, on cancel, and on
  unmount (no dangling camera indicator / battery drain) — this is a hard
  requirement with a dedicated test.
- The system shall handle **permission-denied** and **no-camera-available** with
  a clear message and a fallback to the file picker, so camera trouble never
  blocks adding items.
- The system shall require a secure context (satisfied by HTTPS in prod and
  `localhost` in dev) and degrade to the file-picker fallback where
  `getUserMedia` is unavailable.

**Proof Artifacts:**
- Test: unmounting (and closing/cancelling) the open camera calls `stop()` on
  every acquired `MediaStreamTrack` — demonstrates guaranteed teardown (no leak).
- Test: a `getUserMedia` rejection (permission denied / no device) renders the
  clear message + file-picker fallback — demonstrates graceful degradation.
- Test: capturing frames then "Done" enqueues each captured file as a queue item —
  demonstrates multi-shot → queue.
- Screenshot: the in-app camera viewfinder with accumulated shot thumbnails on a
  mobile viewport — demonstrates the capture UX (captured on a real installed PWA
  where feasible, per the open risks).

## Non-Goals (Out of Scope)

1. **No new backend endpoints or batch API.** The existing single-photo
   `POST /api/items/tag` and `POST /api/items` are looped client-side; the
   backend is untouched by this issue. (A `/batch` endpoint would add
   partial-failure/transactionality questions for no demo-scale win.)
2. **No change to the vision-tagging model or prompt.** Same Haiku 4.5 per-photo
   call; this issue changes only how many photos and from where.
3. **No raising or reworking the daily call cap.** Batch multiplies calls against
   the existing ~100/day cap; we handle the 429 gracefully rather than change the
   cap.
4. **No multi-item / grid detection in a single photo** ("one photo of 5 shirts →
   5 items"). One photo = one item; "batch" means multiple *photos*.
5. **No barcode / receipt / URL import and no OS share-sheet import.** Possible
   future sources; the `enqueue` seam makes them cheap later, but they are not in
   this issue.
6. **No background/queued or offline upload.** Review + save happen in the
   foreground session, as today.
7. **No client-side downscale before upload.** The backend already normalises to
   ≤800px JPEG; client resize is an optional latency optimisation, deferred.

## Design Considerations

- **Mobile-first, one-handed.** Touch targets ≥44px; camera, paste, and batch
  controls usable one-handed; the source controls (choose files / paste / take
  photos) sit above the review queue on the existing `/add` screen and match the
  current maroon/beige styling.
- **Review queue.** Each item is a tile with a thumbnail, a per-item tag summary
  that expands to the shared `TagForm`, a per-tile state ("tagging…" / editable /
  save-failed), and a remove control. A sticky "Save all" with running progress
  anchors the set.
- **Camera surface.** Full-width `<video>` viewfinder, a large shutter button, a
  strip of captured-shot thumbnails, and "Done"/"Cancel". Permission-denied and
  no-camera states show a message plus a "Choose from library" fallback button.
- **Capability-driven affordances.** The "Paste image" button and "Take photos"
  control appear only when their APIs are available; otherwise the file picker is
  always present as the baseline path.
- Reuse existing components/styles: `TagForm`, `DescriptorChips`, the
  `banner`/`state-note` classes, and the existing `photo-picker` styling.

## Repository Standards

- **Frontend:** React 19 + Vite, mobile-first; CSS variables in
  `frontend/src/index.css`; `react-router-dom`; the `/api/items` client in
  `frontend/src/api/items.ts` (`tagPreview`, `createItem`) reused **as-is**,
  called per item; `Item`/`TagInput`/`TagSuggestion` types in
  `frontend/src/types/item.ts`. Any new client helper (e.g. a typed 429 error or a
  concurrency limiter) follows the existing `api/` and `lib/` module patterns.
- **Testing:** Vitest + React Testing Library, co-located `*.test.tsx` next to the
  unit (as with `AddItem.test.tsx`), Arrange-Act-Assert, descriptive
  behaviour-naming. Test the meaningful queue/source/save logic; do **not**
  over-test `<video>`/canvas render plumbing or raw gesture handling
  ([docs/TESTING.md](../../TESTING.md)).
- **Commits:** conventional commits, roughly one demoable unit per commit
  (queue refactor → batch → concurrency/cap → paste → camera), matching the
  branch `image-import-options`.
- **No secrets**; no backend changes; keep the existing `authedFetch`/`ensureOk`
  error-handling contract for `/api/**`.

## Technical Considerations

- **`AddItem.tsx` generalises cleanly.** Today it holds single
  `photo`/`previewUrl`/`phase`/`suggestion`/`error` state, an `onSelectPhoto` that
  reads `files?.[0]`, one `tagPreview` guarded by a `requestId` ref, an object URL
  revoked on re-select/unmount, and one `createItem` on save. All of this maps
  one-to-one onto a per-item model in a list; `EMPTY_SUGGESTION` stays in
  `AddItem.tsx`.
- **429 detection needs a small, deliberate mechanism.** `items.ts#ensureOk`
  currently throws a generic `Error("Tag preview failed with status 429")`, and
  `http.ts` only special-cases `401`. To detect a mid-batch cap distinctly (Unit
  3), surface the HTTP status on failure — e.g. a typed `ApiError { status }`
  thrown by `ensureOk`, or a `tagPreview` variant that resolves the status — so
  the queue reacts to `429` without brittle string matching. Keep the existing
  `401` re-auth behaviour untouched.
- **Concurrency helper.** A minimal promise-pool (limit `3`) in `lib/` throttles
  `tagPreview` fan-out; it is pure and unit-testable independent of React.
- **Blob→File wrapping (paste & camera).** Wrap clipboard/canvas blobs as a valid
  `image/*` `File` (synthesized name + `type`) before `enqueue`; the backend
  `ImageProcessor` (`src/main/java/com/ensemble/storage/ImageProcessor.java`)
  decodes → resizes to `MAX_EDGE=800` → re-encodes JPEG and rejects oversized
  images (400), so PNG screenshots and large camera frames are handled
  server-side and the client needs no resize.
- **Object-URL / track lifecycle.** N preview URLs must each be revoked (remove /
  save / unmount) — use a per-item URL or a `Map`, not the current single ref.
  Camera `MediaStreamTrack`s must be stopped on Done/cancel/unmount.
- **Current-standards notes (verified July 2026):** `getUserMedia` requires a
  secure context and has a documented history of iOS-PWA quirks (permission not
  persisted, intermittent re-prompts, an iOS 18 regression fixed in 18.1.1,
  orientation oddities) — hence the mandatory file-picker fallback and on-device
  testing. The async Clipboard API reached Baseline (Mar 2025); Safari supports it
  and `image/png` but only under a user gesture — hence the dual
  paste-event + gated button, using `ClipboardItem.supports('image/png')` /
  `navigator.clipboard?.read` for capability detection. These confirm (do not
  change) the decided design.

## Security Considerations

- **No new secrets and no new backend surface.** All three sources reuse the
  existing authenticated `/api/items` endpoints through `authedFetch`; the Claude
  key stays server-side; the `401` re-auth path is unchanged.
- **Camera/clipboard are user-gesture, permission-gated browser APIs.** No
  captured/pasted image bytes leave the app except through the same authenticated
  create/tag calls used today. Stop camera tracks on teardown so the device
  camera indicator can't linger.
- **Cap remains enforced server-side.** The client's graceful `429` handling is a
  UX affordance only; it never bypasses `CallCapService` — each photo is still one
  server-side `reserve()`.
- **Proof-artifact hygiene:** screenshots must not include the passcode, session
  token, or any real secret; use demo wardrobe content.

## Success Metrics

1. **Batch works end-to-end:** selecting multiple library photos yields one review
   queue, each item auto-tagged and individually editable, and "Save all" persists
   the set via the existing create endpoint (no new backend endpoint added).
2. **Resilience:** a per-item save failure keeps that item (with edited tags) in
   the queue while successes are removed; a mid-batch `429` preserves all photos,
   shows the manual-from-here banner, and still allows manual tag/save.
3. **`N=1` parity:** single-photo add still works exactly as before, including the
   degraded/failed-tagging blank-editable fallback (all prior `AddItem` tests plus
   new ones green).
4. **No leaks:** object URLs and camera `MediaStreamTrack`s are cleaned up across
   add/remove/save/unmount (asserted by test).
5. **All three sources reach the queue:** batch, paste (event + button), and
   in-app multi-shot camera each enqueue and tag like any other source, with
   file-picker fallback whenever camera/clipboard is unavailable.

## Open Questions

1. **Concurrency limit value** — assumed **3** (within the issue's "2–3" range) as
   a non-blocking default; tunable without changing scope.
2. **Compact-tile vs always-expanded `TagForm`** in the review queue — assumed a
   compact per-item summary that expands to the full `TagForm` for large batches;
   presentation detail, refinable during implementation without changing
   requirements or proof artifacts.
3. **Real installed-PWA screenshots on iOS** may be substituted with a
   desktop/`localhost` secure-context capture if a physical iOS device is
   unavailable at implementation time; the on-device testing pass (issue "open
   risks") remains a manual follow-up and does not gate the automated proof
   artifacts.
