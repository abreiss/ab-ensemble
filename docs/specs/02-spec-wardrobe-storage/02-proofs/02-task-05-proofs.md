# Task 05 Proofs — End-to-end local run + docs

## Task Summary

This task proves the whole wardrobe slice works over HTTP against a live app +
DynamoDB Local, documents the local run in `README.md`/`docs/DEVELOPMENT.md`, and
captures the final coverage summary. It is the closeout that ties tasks 1–4
together into a runnable, documented feature.

## What This Task Proves

- The full CRUD lifecycle works end to end against a running backend and real
  DynamoDB Local: create → list → get → get-photo → update-tags → delete → 404.
- A 1200×600 upload is stored resized to 800×400 JPEG and served as `image/jpeg`.
- Tag updates persist; deleting an item then fetching it returns `404`.
- A new developer can run the slice from the documented commands.
- Backend-domain coverage exceeds the ≥90% standard.

## Evidence Summary

- Live `curl` transcript shows every endpoint returning the expected
  status/body against DynamoDB Local.
- `GET /api/items/{id}/photo` returns `image/jpeg`; `sips` reports the stored
  image is 800×400 (downscaled from 1200×600).
- Final suite: 53 tests, 0 failures; total coverage 98% line / 90% branch, with
  the wardrobe/storage domain packages at 100% line.

## Artifact: End-to-end CRUD transcript (live app + DynamoDB Local)

**What it proves:** Every endpoint works against the real stack, not mocks.

**Why it matters:** This is the acceptance-criteria proof for the whole spec —
create/list/get/update/delete plus photo retrieval and the 404 path.

**Commands:** `docker compose up -d dynamodb` → `./gradlew bootRun` → the `curl`
sequence in `README.md`.

**Result summary:** Create returned `201` with a server-generated `itemId` and a
`photoUrl`; the photo was served as `image/jpeg` at 800×400; the tag update
persisted (`formality` 3→5, `primaryColor` navy→black); delete returned `204`
and the subsequent get returned `404`.

```
### 1) CREATE (multipart, 1200x600 photo)
{"itemId":"19b7b3e6-...","category":"top",...,"photoUrl":"/api/items/19b7b3e6-.../photo",
 "createdAt":"2026-07-14T00:08:14Z","lastWorn":null,"wornCount":0}
HTTP 201

### 3) GET /{id}                                             -> HTTP 200
### 4) GET /{id}/photo   content_type=image/jpeg HTTP 200
        stored dims: pixelWidth: 800  pixelHeight: 400        (from 1200x600)

### 5) PUT /{id}/tags   -> HTTP 200  (formality 3->5, primaryColor navy->black)
### 6) GET /{id}        -> formality:5, primaryColor:"black"  (persisted)
### 7) DELETE /{id}     -> DELETE HTTP 204
### 8) GET /{id}        -> {"error":"not_found",...}  HTTP 404
```

## Artifact: Photo compression on the real request path

**What it proves:** The ≤800px-JPEG rule holds through the API, not just in the
storage unit test.

**Why it matters:** Confirms uploads are actually shrunk before storage/serving.

**Result summary:** Uploaded 1200×600 JPEG; `GET .../photo` returned
`Content-Type: image/jpeg`; `sips` on the downloaded bytes reported 800×400 —
the longest edge is exactly 800.

## Artifact: Final test + coverage summary

**What it proves:** All tests pass and backend-domain coverage meets the bar.

**Why it matters:** Objective closeout evidence for the validation phase.

**Command:**

```bash
docker compose up -d dynamodb
./gradlew clean test jacocoTestReport -PskipFrontend
```

**Result summary:** 53 tests, 0 failures. Coverage by package:

```
com/ensemble/storage        line=38/38 (100%)   branch=8/8 (100%)
com/ensemble/wardrobe       line=73/73 (100%)
com/ensemble/wardrobe/dto   line=23/23 (100%)
com/ensemble/wardrobe/web   line=19/19 (100%)
com/ensemble/config         line=34/36 (94%)    branch=2/2 (100%)
com/ensemble/health         line=2/2  (100%)
com/ensemble/web            line=17/17 (100%)   branch=8/10 (80%)   (skeleton SPA config, task 01)
com/ensemble (EnsembleApplication.main)  line=1/3 (33%)             (entrypoint, run via bootRun)
---
TOTAL                       line=207/211 (98%)  branch=18/20 (90%)
```

The wardrobe + storage domain packages are at 100% line; the only sub-100%
spots are the application entrypoint and the pre-existing skeleton SPA config —
neither is domain logic for this spec.

## Artifact: Documentation

**What it proves:** The local run is documented for a new developer.

**Why it matters:** Acceptance requires the slice be runnable from docs alone.

**Result summary:** `README.md` gains a "Wardrobe Storage" section
(`docker compose up -d dynamodb`, config keys, and the full `curl` CRUD flow);
`docs/DEVELOPMENT.md` points at it and notes the photo dir.

## Reviewer Conclusion

The wardrobe storage slice runs end to end: items and photos persist to
DynamoDB Local and disk, photos are compressed to ≤800px JPEG, the API behaves
per contract including error paths, it is documented, and the domain code is at
100% line coverage.
