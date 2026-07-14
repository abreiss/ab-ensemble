# Task 03 Proofs — PhotoStorage interface + LocalDiskPhotoStorage (≤800px JPEG)

## Task Summary

This task adds the swappable `PhotoStorage` seam and its local-disk
implementation, which compresses every saved photo to ≤800px on the longest edge
and re-encodes it as JPEG. It proves the compression rules — the task's critical
branch logic — behave exactly as specified, including the no-upscale and
non-image edge cases.

## What This Task Proves

- Application code depends only on the `PhotoStorage` interface (disk→S3 swap is
  configuration, not a rewrite).
- A larger-than-800px image is downscaled so its longest edge is exactly 800px,
  aspect ratio preserved, stored as valid JPEG.
- A smaller image is stored unchanged — never upscaled.
- Non-image input is rejected with `InvalidImageException`, not a crash.
- Path-traversal keys are rejected; missing keys raise `PhotoNotFoundException`.
- Coverage meets the critical-logic bar: 100% branch (and 100% line) on the impl.

## Evidence Summary

- `LocalDiskPhotoStorageTest` passes 9/9, covering downscale, no-upscale,
  exact-max, non-image, missing key, delete, delete-noop, traversal, and the
  I/O-error wrapper path.
- JaCoCo: `LocalDiskPhotoStorage` at 100% line and **100% branch** (8/8).

## Artifact: Compression + edge-case unit tests

**What it proves:** The ≤800px-JPEG rule and its edge cases work on real image
bytes decoded back and measured.

**Why it matters:** These are the task's critical branches (downscale /
no-upscale / reject-non-image); AGENTS.md requires 100% branch coverage here.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `LocalDiskPhotoStorageTest` ran 9 tests, 0 failures. Cases:

- `save_largeImage_isDownscaledToMax800AndStoredAsJpeg` (1200×600 → 800×400, JPEG magic bytes `FF D8`)
- `save_smallImage_isNotUpscaled` (300×200 stays 300×200)
- `save_imageExactlyAtMax_isKept` (800×500 unchanged)
- `save_nonImageBytes_throwsInvalidImageException`
- `load_unknownKey_throwsPhotoNotFound`
- `delete_removesStoredPhoto`, `delete_unknownKey_isNoOp`
- `resolve_pathTraversalKey_isRejected`
- `load_whenKeyIsADirectory_wrapsIoErrorAsUnchecked`

## Artifact: Coverage report (storage package)

**What it proves:** The compression/validation logic is fully branch-covered.

**Why it matters:** Objective evidence for the 100%-branch critical-logic gate.

**Command:**

```bash
./gradlew jacocoTestReport -PskipFrontend
```

**Result summary:**

```
LocalDiskPhotoStorage    line=34/34 (100%)   branch=8/8 (100%)
InvalidImageException    line=2/2 (100%)
PhotoNotFoundException   line=2/2 (100%)
```

## Reviewer Conclusion

Photo storage is implemented behind a swappable interface and proven: images are
downscaled to ≤800px JPEG (aspect preserved), small images are left untouched,
and non-image/edge inputs are handled safely — all with 100% branch coverage on
the storage logic.
