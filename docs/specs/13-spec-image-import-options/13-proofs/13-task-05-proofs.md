# Task 05 Proofs - In-app live camera with multi-shot capture and guaranteed stream teardown

## Task Summary

This task adds an in-app **live camera** as the fifth image source on `/add`. A
**"Take photos"** control (shown only where `getUserMedia` is available) opens an
`environment`-facing camera into a `<video>` viewfinder; a large shutter captures the
current frame to a `<canvas>` → `toBlob()` → an `image/*` `File`. It is **multi-shot**:
the camera stays open after each capture, frames accumulate as thumbnails, and
**"Done"** hands the whole set up via `onDone`, which `AddItem` routes through the same
shared `enqueue` used by every other source — so each captured shot becomes its own
auto-tagging review tile (a single shot + Done is just the `N=1` case).

It matters because it removes the "leave the app, use the OS camera, come back and
pick the file" round-trip for the primary mobile use case (photographing clothes),
while reusing the entire Units 1–3 spine (queue, throttle, cap handling, "Save all")
with no new backend surface. The **hard requirement** the spec calls out — every
`MediaStreamTrack` is stopped on Done, cancel, and unmount (no dangling camera
indicator / battery drain) — is proven by a dedicated automated test, alongside the
permission/no-device fallback to the file picker.

## What This Task Proves

- A **"Take photos"** control opens the camera and captures frames to a File; the
  camera stays open for **multi-shot**, and "Done" enqueues each captured file as an
  auto-tagging tile (Unit 5 FRs: getUserMedia viewfinder + shutter, multi-shot
  accumulate + Done→enqueue).
- **Every acquired `MediaStreamTrack` is stopped** on unmount, on cancel, and on Done
  — the spec's hard "no dangling camera" requirement, with the dedicated test the spec
  mandates (Unit 5 FR: guaranteed track teardown).
- A `getUserMedia` **rejection** (permission-denied / no device) renders a clear
  message plus a **file-picker fallback** that funnels picked files through the same
  `onDone` hand-off — camera trouble never blocks adding items (Unit 5 FR:
  permission/no-device fallback).
- The control is **hidden** where `getUserMedia` is unavailable, degrading to the
  always-present file picker — covering the secure-context requirement, since
  `navigator.mediaDevices` is exposed only in a secure context (Unit 5 FR:
  secure-context degrade).

## Design Decisions (within spec latitude)

- **Component + `lib/` capability probe split.** The camera UI lives in
  `components/CameraCapture.tsx`; the pure `cameraSupported()` capability probe lives in
  `lib/camera.ts`, mirroring `clipboardReadSupported` in `clipboardImages.ts`. This
  keeps the component file exporting only a component (clears the
  `react-refresh/only-export-components` ESLint warning) and lets `AddItem` gate the
  "Take photos" control on a pure, independently-tested function.
- **Isolation of concerns in tests.** `CameraCapture.test.tsx` drives the component
  directly with a mocked `getUserMedia` + stubbed `canvas.getContext`/`toBlob`, proving
  teardown/fallback/capture. `AddItem.test.tsx` mocks `CameraCapture` (and the
  `cameraSupported` probe) so it proves only the wiring — gated mount + `onDone(files)`
  → `enqueue` — per `docs/TESTING.md` ("do not over-test `<video>`/canvas plumbing").
- **Reused `blobToImageFile`.** Captured canvas blobs are wrapped by the same
  `imageFile.ts` helper the paste source uses (`namePrefix: 'camera'`), so a captured
  frame reaches the queue as a valid `image/*` `File` and no client resize is done
  (backend re-encodes to ≤800px JPEG — spec Non-Goal #7).
- **Idempotent teardown from one seam.** A single `stopStream()` (stops every track,
  clears the stream ref) is called from Done, Cancel, and the unmount cleanup; a stream
  that resolves *after* an early unmount is stopped inline in the async start path, so
  nothing leaks on a fast close.
- **Self-contained fallback.** The error state renders its own `image/*` file input and
  routes picked files through `onDone`, so the failure path reaches the shared queue
  exactly like a successful capture.

## Evidence Summary

- `CameraCapture.test.tsx` (4 tests) proves the mandated teardown on unmount **and**
  cancel **and** Done, the multi-shot capture → `onDone(files)` path, and the
  permission-denied message + file-picker fallback.
- `camera.test.ts` (3 tests) proves the `cameraSupported()` gate: true with
  `getUserMedia`, false when `mediaDevices`/`getUserMedia` is absent.
- `AddItem.test.tsx` (24 tests) proves the "Take photos" control mounts the camera and
  a multi-shot "Done" enqueues each captured file as an auto-tagging tile, and that the
  control is hidden (file-picker-only) when `getUserMedia` is unavailable.
- The whole frontend suite (23 files, **218 tests**) passes and `eslint .` (exit 0, no
  warnings) + `tsc -b` (exit 0) are clean.

## Artifact: CameraCapture component tests — teardown, fallback, multi-shot capture

**What it proves:** Every acquired `MediaStreamTrack` is stopped on unmount, cancel,
and Done; a `getUserMedia` rejection renders a message + file-picker fallback; and
capturing multiple frames then "Done" hands each frame up as an image `File`.

**Why it matters:** The guaranteed teardown is the spec's explicit hard requirement
(no dangling camera / battery drain), called out as needing a dedicated test; these
tests are the automated gate for the whole Unit 5 behaviour set.

**Command:**

```bash
cd frontend && npm test -- --run src/components/CameraCapture.test.tsx src/lib/camera.test.ts
```

**Result summary:** 7/7 pass — 4 `CameraCapture` (teardown-on-unmount,
teardown-on-cancel + `onCancel`, multi-shot capture → `onDone` + teardown, denied →
message + fallback) and 3 `camera` (`cameraSupported` true/absent/partial).

```
✓ src/lib/camera.test.ts (3 tests)
✓ src/components/CameraCapture.test.tsx (4 tests)
  ✓ stops every acquired MediaStreamTrack on unmount (no dangling camera)
  ✓ stops every track and calls onCancel when the camera is cancelled
  ✓ captures multiple frames then Done hands every file to onDone and stops tracks
  ✓ shows a clear message and a file-picker fallback when getUserMedia is denied

 Test Files  2 passed (2)
      Tests  7 passed (7)
```

## Artifact: AddItem camera-wiring tests (RED → GREEN)

**What it proves:** In the real screen, the gated "Take photos" control mounts
`CameraCapture` and its multi-shot `onDone` enqueues each captured file as an
auto-tagging tile; the control is absent (file picker only) when `getUserMedia` is
unavailable.

**Why it matters:** These are the Unit 5 functional requirements exercised through the
shared queue seam, proving the camera composes with the Units 1–3 auto-tag/throttle/
save pipeline unchanged.

**Command:**

```bash
cd frontend && npm test -- --run src/routes/AddItem.test.tsx
```

**Result summary:** 24/24 pass — the 22 prior queue/source tests plus two new Unit 5
tests (open camera → multi-shot Done enqueues two auto-tagging tiles; control hidden +
file picker kept when `getUserMedia` is unavailable).

```
✓ src/routes/AddItem.test.tsx (24 tests)
  ✓ offers a plain library picker: multi-select, image-only, and no forced camera
  ✓ opens the in-app camera and enqueues each captured shot as an auto-tagging tile
  ✓ hides the "Take photos" control and keeps the file picker when getUserMedia is unavailable
```

## Artifact: Full frontend suite + lint + typecheck (no regression)

**What it proves:** Adding the camera component, the `lib/camera` probe, and the
`AddItem` wiring did not regress any existing frontend behaviour, and the code passes
the repo's ESLint gate (now warning-free) and the project TypeScript build.

**Why it matters:** The camera path shares `AddItem`'s `enqueue`/tag/save pipeline; a
green whole-suite run plus clean lint and typecheck confirm the new source composes
with the queue, throttle, and cap handling without side effects.

**Command:**

```bash
cd frontend && npm test -- --run && npm run lint && npx tsc -b
```

**Result summary:** 23 files, **218 tests** pass (up from 209: +4 CameraCapture, +3
camera lib, +2 AddItem); `eslint .` exits 0 with no errors or warnings; `tsc -b`
exits 0.

```
Test Files  23 passed (23)
     Tests  218 passed (218)
```

## Artifact: In-app camera viewfinder screenshot — PENDING MANUAL CAPTURE

**What it proves (once captured):** The in-app viewfinder with accumulated shot
thumbnails on a mobile viewport — the human-visible capture UX of Unit 5.

**Why it matters:** It is the human-visible demo of Unit 5. It is **not** the automated
gate (the teardown/capture/fallback tests above are); consistent with the spec's Open
Question 3 and open risks, an environment- and device-dependent screenshot may be
captured against a running stack on a secure context.

**Why it is pending:** Capturing it requires the running backend + frontend on a
**secure context** (`localhost` or an installed PWA — `getUserMedia` is blocked
otherwise), a **real device camera**, and (for live auto-tags) a Claude API key. This
implementation session has DynamoDB Local only, no headless browser, no device camera,
and no Claude key — and will not fabricate the image. This capture also covers the 5.6
on-device confirmation that no camera indicator lingers after Done/Cancel (the
automated `stop()`-on-every-track test is the code-level proof).

**Target path:** `docs/specs/13-spec-image-import-options/13-proofs/assets/camera-viewfinder-mobile.png`

**Repro steps (operator, on a phone or `localhost`, with a Claude key for live tags):**

1. `docker compose up -d dynamodb`
2. In the backend env set `ENSEMBLE_PASSCODE=<demo>`,
   `ENSEMBLE_SESSION_SECRET=<any-local>`, and `ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...`.
3. Backend: `./gradlew bootRun`  •  Frontend: `cd frontend && npm run dev`
4. Open the app on a secure context — `http://localhost:5173` on the dev machine, or
   the installed PWA on a phone (HTTPS) — enter the passcode, go to **Add an item**.
5. Tap **Take photos**, grant camera permission, then tap the shutter two or three
   times so captured-shot thumbnails accumulate in the strip.
6. Screenshot the viewfinder with the shutter, the thumbnail strip, and Done/Cancel
   visible (demo garments only).
7. Save the PNG to the target path above. Ensure the passcode/session token is **not**
   visible in the frame. Confirm the OS camera indicator turns off after **Done**/
   **Cancel** (the guaranteed-teardown requirement).

## Reviewer Conclusion

Unit 5 in-app live camera is implemented and fully covered by automated tests: the
dedicated teardown test proves every `MediaStreamTrack` is stopped on unmount, cancel,
and Done (the spec's hard requirement); the capture test proves multi-shot frames reach
`onDone` as image Files; the fallback test proves a `getUserMedia` rejection degrades to
a file picker; and the screen tests prove the gated "Take photos" control routes
captures through the shared review queue and hides itself where the camera is
unavailable. The whole suite (23 files, 218 tests) is green with a clean, warning-free
lint and typecheck. Only the human-visible, device-dependent screenshot remains,
deferred to an operator with a running stack on a secure context per spec Open
Question 3.
