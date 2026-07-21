# Task 04 Proofs - Clipboard paste (paste event + capability-gated "Paste image" button)

## Task Summary

This task adds **clipboard paste** as a third image source on `/add`, via both the
desktop Cmd/Ctrl-V path and a discoverable button. A screen-wide `paste`-event
handler reads `ClipboardEvent.clipboardData` for `image/*` entries and funnels them
through the shared `enqueue`; an explicit **"Paste image"** button uses
`navigator.clipboard.read()` where available and is gated behind capability
detection. Every pasted `Blob` is wrapped as a valid `image/*` `File` (synthesized
name + reported type) before enqueuing, so a pasted image reaches the queue and tags
exactly like a picked or camera-captured file.

It matters because it removes the "save the image to your library first" step for
screenshots and product photos, and it reuses the whole Units 1–3 spine (queue,
throttle, cap handling, "Save all") with no new backend surface — the paste path is
just another caller of `enqueue`.

## What This Task Proves

- A `paste` event carrying an `image/*` item wraps the blob as a `File` and enqueues
  one queue tile that auto-tags like any other source (Unit 4 FR: paste-event handler
  + Blob→File wrapping).
- The clipboard extractor returns **all** image entries and filters non-image ones;
  the async-read path reads each image `ClipboardItem` via `getType` (Unit 4 FR:
  multi-paste batch).
- A `Blob` is normalised into an `image/*` `File` with a synthesized name and its
  reported type, falling back to `image/png` for a missing/non-image type (Unit 4 FR:
  Blob→File wrapping).
- The **"Paste image"** button is hidden where the async clipboard-read capability is
  absent and shown where present; clicking it reads the clipboard and enqueues (Unit 4
  FR: gated async-read button).

## Design Decisions (within spec latitude)

- **Document-level paste listener.** The `paste` handler is bound to `document` (not
  a focused element) so Cmd/Ctrl-V works anywhere on the Add screen; it is registered
  once and calls the latest `enqueue` through a `enqueueRef` handle, avoiding a
  re-subscribe every render and any `react-hooks/exhaustive-deps` churn.
- **Two pure `lib/` helpers.** `imageFile.ts` (`blobToImageFile`) and
  `clipboardImages.ts` (`imageFilesFromClipboardData` / `imageFilesFromClipboardItems`
  / `clipboardReadSupported`) are React-independent and unit-tested in isolation,
  matching the existing `lib/` module + co-located-test pattern.
- **Capability gate probed once.** `clipboardReadSupported()` requires
  `navigator.clipboard.read` + `ClipboardItem`, and — where the newer static probe
  exists — `ClipboardItem.supports('image/png')`; it is read once via a lazy
  `useState` initializer since capability does not change during a session. The button
  is **hidden** (not merely disabled) when unsupported, keeping the always-present file
  picker as the baseline path.
- **No client resize.** Pasted blobs (typically PNG screenshots) are sent as-is; the
  backend `ImageProcessor` re-encodes to ≤800px JPEG (spec Non-Goal #7).

## Evidence Summary

- `imageFile.test.ts` (4 tests) proves Blob→File wrapping: type preserved, name
  synthesized with the right extension, and `image/png` fallback for missing/non-image
  types.
- `clipboardImages.test.ts` (6 tests) proves multi-image extraction from a paste
  event, non-image filtering, async `getType` extraction, and the capability gate
  (false without `clipboard.read`; true/false driven by `ClipboardItem.supports`).
- `AddItem.test.tsx` (22 tests) proves the paste-event path enqueues a wrapped image
  tile that tags, a text-only paste is ignored, the button is hidden/shown by
  capability, and a button click reads the clipboard and enqueues.
- The whole frontend suite (21 files, **209 tests**) passes and `eslint .` +
  `tsc -b` are clean.

## Artifact: Blob→File + clipboard-extractor unit tests (RED → GREEN)

**What it proves:** `blobToImageFile` wraps any blob as an `image/*` `File` with a
synthesized name and MIME fallback; `imageFilesFromClipboardData` returns all image
entries (filtering non-images) and `imageFilesFromClipboardItems` reads each image
`ClipboardItem`; `clipboardReadSupported` reports capability correctly.

**Why it matters:** These are the pure, reusable seams the paste UI (and later the
camera) build on; testing them directly (per `docs/TESTING.md`) is cheaper and more
reliable than inferring them only through the screen.

**Command:**

```bash
cd frontend && npm test -- --run src/lib/imageFile.test.ts src/lib/clipboardImages.test.ts
```

**Result summary:** 10/10 pass — 4 `imageFile` (type preserved, `image/png` fallback
for empty and non-image types, prefix + extension) and 6 `clipboardImages` (extract
all / filter non-image / no-data → `[]`, async `getType` extraction, and three
capability-gate cases).

```
✓ src/lib/imageFile.test.ts (4 tests)
✓ src/lib/clipboardImages.test.ts (6 tests)
  ✓ imageFilesFromClipboardData > extracts every image file and filters out non-image entries
  ✓ imageFilesFromClipboardItems > reads all image ClipboardItems via getType and wraps them as image Files
  ✓ clipboardReadSupported > is false when the async clipboard read API is absent
  ✓ clipboardReadSupported > is true when async read + ClipboardItem image support are present
  ✓ clipboardReadSupported > is false when ClipboardItem reports no image/png support
```

## Artifact: Review-queue paste tests (RED → GREEN)

**What it proves:** In the real screen, a `paste` event carrying an `image/png` item
enqueues one tile whose sent file is a wrapped image `File` and which auto-tags; a
text-only paste creates no tile and fires no preview; the "Paste image" button is
hidden when `clipboardReadSupported()` is false and shown when true; and clicking it
calls `navigator.clipboard.read()` and enqueues the returned image.

**Why it matters:** These are the Unit 4 functional requirements exercised end to end
through the shared queue, alongside the preserved Units 1–3 behaviours (still green).

**Command:**

```bash
cd frontend && npm test -- --run src/routes/AddItem.test.tsx
```

**Result summary:** 22/22 pass — the 17 prior queue tests plus five new Unit 4 tests
(paste-event enqueue + wrapping, non-image paste ignored, button hidden when
unsupported, button shown when supported, button-click read + enqueue).

```
✓ src/routes/AddItem.test.tsx (22 tests)
  ✓ enqueues a wrapped image File when an image is pasted onto the screen
  ✓ ignores a paste that carries no image (e.g. plain text)
  ✓ hides the "Paste image" button when async clipboard read is unsupported
  ✓ shows the "Paste image" button when async clipboard read is supported
  ✓ reads the clipboard and enqueues an image when "Paste image" is clicked
```

## Artifact: Full frontend suite + lint + typecheck (no regression)

**What it proves:** Adding the two `lib/` helpers and the paste wiring did not regress
any existing frontend behaviour, and the code passes the repo's ESLint gate and the
project TypeScript build.

**Why it matters:** The paste path shares `AddItem`'s `enqueue`/tag pipeline changed
in earlier tasks; a green whole-suite run plus clean lint and typecheck confirm the
new source composes with the queue, throttle, and cap handling without side effects.

**Command:**

```bash
cd frontend && npm test -- --run && npm run lint && npx tsc -b
```

**Result summary:** 21 files, **209 tests** pass (up from 194: +4 imageFile, +6
clipboardImages, +5 AddItem); `eslint .` exits 0 with no errors or warnings; `tsc -b`
exits 0.

```
Test Files  21 passed (21)
     Tests  209 passed (209)
```

## Artifact: Paste-import review-queue screenshot — PENDING MANUAL CAPTURE

**What it proves (once captured):** An item pasted onto `/add` appearing in the review
queue with tags — the end-to-end paste-import UX.

**Why it matters:** It is the human-visible demo of Unit 4. It is **not** the
automated gate (the tests above are); consistent with spec Open Question 3, an
environment-dependent screenshot may be captured against a running stack.

**Why it is pending:** Capturing it requires the running backend + frontend (and, for
live auto-tags, a Claude API key) plus a browser to perform a real clipboard paste.
This implementation session has DynamoDB Local only, no headless browser, and no
Claude key — and will not fabricate the image.

**Target path:** `docs/specs/13-spec-image-import-options/13-proofs/assets/paste-import-queue.png`

**Repro steps (operator, with a Claude key for live tags):**

1. `docker compose up -d dynamodb`
2. In the backend env set `ENSEMBLE_PASSCODE=<demo>`,
   `ENSEMBLE_SESSION_SECRET=<any-local>`, and `ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...`.
3. Backend: `./gradlew bootRun`  •  Frontend: `cd frontend && npm run dev`
4. Open `http://localhost:5173`, enter the passcode, go to **Add an item**.
5. Copy a garment image to the clipboard (a screenshot or a product photo — demo
   content only), then press **Cmd/Ctrl-V** on the Add screen (or click **Paste
   image** in a Chromium browser where the async read API is available).
6. Wait for the pasted tile to auto-tag, then screenshot the queue showing the tile
   with its editable tags.
7. Save the PNG to the target path above. Ensure the passcode/session token is **not**
   visible in the frame.

## Reviewer Conclusion

Unit 4 clipboard paste is implemented and fully covered by automated tests: the pure
`blobToImageFile` and clipboard extractors are unit-tested in isolation, and the
screen proves the paste-event path, non-image filtering, capability-gated button
visibility, and the button-click read → enqueue — all funnelling through the shared
queue with no new backend surface. The whole suite (21 files, 209 tests) is green with
a clean lint and typecheck. Only the human-visible screenshot remains, deferred to an
operator with a running stack per spec Open Question 3.
