# Task 02 Proofs — Shared image decode/resize guard (extracted, reused)

## Task Summary

This task extracts the image decode + decompression-bomb pixel-cap + resize-to-
≤800px-JPEG logic out of `LocalDiskPhotoStorage` into a new, reusable
`ImageProcessor` component. Storage now delegates to it, and the vision tagging
path (task 3.0) will reuse the **same** guard rather than duplicating it.

## What This Task Proves

- The image guard is now a standalone component with its critical branches
  (downscale-if-larger, no-upscale, clamp-to-1px, pixel-cap, reject-non-image,
  reader-throws-unchecked) covered directly.
- The extraction is **behavior-preserving**: `LocalDiskPhotoStorage` still stores
  a >800px image as a ≤800px JPEG and still rejects non-images, via delegation.
- **100% branch coverage** on the guard is retained in its new home.

## Evidence Summary

- `ImageProcessorTest` (8 tests) and `LocalDiskPhotoStorageTest` (7 tests) pass.
- JaCoCo: `ImageProcessor` branch = 6/6 (100%); `LocalDiskPhotoStorage` line + branch = 100%.
- The full backend suite is green after the refactor.

## Artifact: ImageProcessor + storage tests pass

**What it proves:** The moved logic works in isolation and storage still works
through delegation.

**Why it matters:** Confirms the refactor preserved behavior — the whole point of
an extraction (spec FLAG-1 regression risk).

**Command:** `./gradlew test -PskipFrontend` (relevant classes shown)

**Result summary:** 15 tests across the two classes, 0 failures.

```text
ImageProcessorTest         -> 8 tests, 0 failed
LocalDiskPhotoStorageTest  -> 7 tests, 0 failed
```

## Artifact: 100% branch coverage retained on the guard

**What it proves:** Every decision point in the decode/pixel-cap/resize logic is
exercised in `ImageProcessor`'s new location.

**Why it matters:** The ≤800px-JPEG + decompression-bomb guard is critical logic;
the spec requires 100% branch, and the extraction must not erode it.

**Command:** `./gradlew jacocoTestReport -PskipFrontend`

**Result summary:** `ImageProcessor` branch 100% (6/6). The 2 uncovered lines are
the unreachable checked-IO catch on the JPEG-encode path (a broken encoder),
consistent with the pre-refactor state.

```text
ImageProcessor        BRANCH  covered=6  missed=0
ImageProcessor        LINE    covered=29 missed=2
LocalDiskPhotoStorage BRANCH  covered=4  missed=0
LocalDiskPhotoStorage LINE    covered=23 missed=0
```

## Reviewer Conclusion

The decode/resize/pixel-cap guard is now a single shared `ImageProcessor` that
storage delegates to and tagging will reuse. Behavior and 100% branch coverage
are preserved, closing the FLAG-1 regression risk from the planning audit.
