# Task 03 Proofs — Per-user photo namespacing + cross-user photo isolation

## Task Summary

This task moves stored photos behind a per-user key prefix (`<userId>/<itemId>.jpg`)
and proves the local-disk backend writes those nested keys correctly while still
rejecting path-traversal keys, and that `GET /api/items/{id}/photo` for an item
owned by another user returns a non-enumerating **404** — never the image bytes.
It closes the last read path (raw photo bytes) that was not yet scoped to the caller.

## What This Task Proves

- **Nested keys write correctly.** `LocalDiskPhotoStorage.save` creates the parent
  directory for a nested key before writing, so the first photo under a new user's
  prefix stores and round-trips instead of failing with `NoSuchFileException`.
- **The traversal guard is intact.** Adding `createDirectories` did not weaken
  `resolve(...)`; `../escape.jpg` is still rejected.
- **Photos are namespaced per owner.** `WardrobeService.create` derives
  `photoKey = <userId>/<itemId>.jpg`, persists it on the `Item`, and uses that stored
  key for save/load/delete — one user's photos live under a per-user prefix and can
  never be reached under another user's id.
- **Cross-user photo requests 404.** The photo handler forwards `@CurrentUserId` into
  `loadPhoto → find(userId, itemId)`, so a request for another user's item id is
  rejected exactly like a missing id (JSON error shape, no bytes).

## Evidence Summary

- RED then GREEN for the storage change: the nested-key test first fails with
  `NoSuchFileException`, then passes once `createDirectories` is added.
- RED then GREEN for the service change: the two `create` key-assertion tests fail
  against the old `<itemId>.jpg` key, then pass once `create` derives the namespaced key.
- The cross-user photo controller test returns `404` with `{"error":"not_found"}`.
- The full backend suite (`./gradlew test -PskipFrontend`) is green, including the
  DynamoDB Local integration tests.
- No `S3PhotoStorage` / `PhotoStorage`-interface change was required — the S3 backend
  passes `key` opaquely, so a nested key is a native S3 prefix.

## Artifact: Nested-key disk storage — RED → GREEN

**What it proves:** `LocalDiskPhotoStorage.save` must create the parent directory of a
nested key before writing; the traversal guard is untouched.

**Why it matters:** Per-user keys are the mechanism that keeps photos scoped; if the
first write under a new user's prefix failed, no photo could ever be stored per-user.

**RED** — before adding `Files.createDirectories(...)`:

```
LocalDiskPhotoStorageTest > save_nestedKey_createsParentDirsAndStores() FAILED
    java.io.UncheckedIOException at LocalDiskPhotoStorageTest.java:108
        Caused by: java.nio.file.NoSuchFileException at LocalDiskPhotoStorageTest.java:108
8 tests completed, 1 failed
```

**GREEN** — after adding `Files.createDirectories(target.getParent())`:

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*LocalDiskPhotoStorageTest'
```

**Result summary:** All 8 storage tests pass — the nested-key round-trip succeeds and
the retained `resolve_pathTraversalKey_isRejected` still rejects `../escape.jpg`.

```
> Task :test
BUILD SUCCESSFUL in 1s
```

## Artifact: Per-user photo-key namespacing in `WardrobeService.create` — RED → GREEN

**What it proves:** `create` stores the photo under `<userId>/<itemId>.jpg` (verified on
both the storage-key captor and the persisted `Item.photoKey`).

**Why it matters:** Namespacing under the owner is what keeps one user's photos from
colliding with or being reachable under another user's id.

**RED** — before deriving the namespaced key (both `create` tests fail on the key
assertion; the un-namespaced `delete`/`loadPhoto` fixtures already updated pass):

```
WardrobeServiceTest > create_namespacesPhotoKeyPerUser() FAILED
    org.opentest4j.AssertionFailedError at WardrobeServiceTest.java:93
WardrobeServiceTest > create_generatesIdStoresPhotoAndPersistsItem() FAILED
    org.opentest4j.AssertionFailedError at WardrobeServiceTest.java:73
20 tests completed, 2 failed
```

**GREEN** — after `String photoKey = userId + "/" + itemId + ".jpg"`:

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*WardrobeServiceTest'
```

**Result summary:** All 20 service tests pass — `create_namespacesPhotoKeyPerUser`
confirms both the storage key and the persisted key equal `<userId>/<itemId>.jpg`.

```
> Task :test
BUILD SUCCESSFUL in 1s
```

## Artifact: Cross-user photo request returns 404

**What it proves:** `GET /api/items/{id}/photo` for an item owned by another user 404s
with the JSON error shape rather than returning the owner's bytes.

**Why it matters:** Raw photo bytes were the last unscoped read path; this closes it so
photo access matches the ownership choke point used by every other single-resource op.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*WardrobeControllerTest'
```

**Result summary:** `photo_otherUsersItem_returns404` passes — the handler forwards the
caller (`userB`) into `loadPhoto`, the service throws `ItemNotFoundException`, and the
response is `404` with `{"error":"not_found"}`. The photo handler already threaded
`@CurrentUserId` (task 2.4), so this locks in that behavior for the photo path.

```
> Task :test
BUILD SUCCESSFUL in 2s
```

## Artifact: Full backend suite green

**What it proves:** The photo-key change did not regress any other behavior, including
the DynamoDB Local integration tests.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** Whole backend suite passes.

```
> Task :test
BUILD SUCCESSFUL in 18s
```

## Artifact: No S3 / interface change (task 3.6)

**What it proves:** The nested key needs no S3-backend or interface change.

**Why it matters:** Confirms the local→S3 swap stays configuration, not a rewrite.

**Result summary:** `S3PhotoStorage` passes `key` straight to
`PutObjectRequest.key(key)` / `GetObjectRequest.key(key)` / `DeleteObjectRequest.key(key)`,
so `<userId>/<itemId>.jpg` is a native S3 object prefix. `PhotoStorage.save/load/delete`
keep `String key` opaque — unchanged.

## Reviewer Conclusion

Photos are now owned data: stored under a per-user prefix, written correctly on disk
without weakening the traversal guard, and unreadable across users (cross-user photo
requests 404 like any other non-owned resource). The change is backend-only, needs no
S3 or interface change, and the full suite stays green.
