# 02-spec-wardrobe-storage.md

## Introduction/Overview

The persistence slice of the Ensemble MVP: the wardrobe data layer. It lets the
app store a garment (a photo plus its text tags), read the wardrobe back, edit
tags, and delete an item — all persisted locally to **DynamoDB Local** (item
records) and **local disk** (photos), behind a swappable `PhotoStorage`
interface so the disk→S3 swap at deploy is configuration, not a rewrite. This
slice adds no AI: tags are supplied by the caller (vision tagging is issue #4).
Its job is to give every later feature — vision tagging (#4), the wardrobe UI
(#5), and the stylist agent (#6) — a working, tested CRUD surface to build on.

## Goals

- Define the single-table DynamoDB **item model** (`itemId` partition key + 6
  vision-tag fields + `photoKey` + `createdAt` + wear-history fields).
- Persist items with the **AWS SDK v2 DynamoDB Enhanced Client** against
  **DynamoDB Local**, with the table auto-created on startup.
- Store photos behind a **`PhotoStorage` interface** with a
  `LocalDiskPhotoStorage` implementation that **compresses/resizes to ≤800px
  JPEG** on save.
- Expose a **CRUD API** under `/api/items` (create, list, get, get-photo,
  update tags, delete) using DTOs at the boundary.
- Meet backend-domain TDD standards (≥90% line; 100% branch on the critical
  storage/mapping/validation paths) with real DynamoDB Local round-trips.

## User Stories

- **As the developer (building later features)**, I want a persisted wardrobe
  with a tested CRUD API so that vision tagging, the UI, and the stylist can
  read and write real items instead of stubs.
- **As the developer**, I want photos stored behind an interface so that moving
  from local disk to S3 at deploy is a configuration swap, not a rewrite.
- **As the eventual user (served by later UI issues)**, I want my garment photos
  stored compactly (≤800px) so that the wardrobe loads quickly on a phone and
  storage stays small.

## Demoable Units of Work

### Unit 1: DynamoDB item model + repository (Enhanced Client → DynamoDB Local)

**Purpose:** Prove garment records persist and round-trip against DynamoDB
Local through the Enhanced Client.

**Functional Requirements:**
- The system shall define an `Item` model with: `itemId` (partition key),
  `category`, `primaryColor`, `secondaryColor`, `formality` (1–5), `pattern`,
  `warmth` (1–3), `descriptors` (list of strings), `photoKey`, `createdAt`,
  `lastWorn`, `wornCount`.
- The system shall persist items with the AWS SDK v2 DynamoDB Enhanced Client
  against DynamoDB Local via a configurable endpoint override.
- The system shall provide repository operations: put/create, get-by-id,
  list-all, update, and delete.
- The system shall auto-create the DynamoDB table on application startup if it
  does not already exist.
- The system shall provide a `docker compose` service for DynamoDB Local and a
  documented command to start it.

**Proof Artifacts:**
- Test: a DynamoDB Local integration test (TestContainers) performing
  create → get → update → delete passes, demonstrates real persistence round-trips.
- CLI: `docker compose up -d dynamodb` starts the local instance, demonstrates the
  local data dependency runs.
- Coverage: JaCoCo report shows ≥90% line on the repository/model package,
  demonstrates the TDD standard is met.

### Unit 2: PhotoStorage interface + LocalDiskPhotoStorage (≤800px JPEG)

**Purpose:** Prove photos are stored behind a swappable interface and shrunk on
save.

**Functional Requirements:**
- The system shall define a `PhotoStorage` interface with save (bytes → key),
  load (key → bytes), and delete (key) operations.
- The system shall provide a `LocalDiskPhotoStorage` implementation that writes
  to a configurable base directory.
- On save, the system shall resize the image so its longest edge is **≤800px**
  (never upscaling smaller images) and re-encode it as **JPEG**.
- The system shall reject input that is not a decodable image with a clear
  error rather than crashing.
- Application code shall depend only on the `PhotoStorage` interface, never on a
  concrete disk/S3 type.

**Proof Artifacts:**
- Test: saving a >800px image then loading it shows the stored image has its
  longest edge ≤800px and is valid JPEG, demonstrates the compression rule.
- Test: saving an already-small image leaves its dimensions unchanged (no
  upscaling), demonstrates the edge case.
- Test: saving non-image bytes raises the defined error, demonstrates the
  failure path.

### Unit 3: Wardrobe CRUD API (controller + service + DTOs)

**Purpose:** Prove the wardrobe is usable end to end over HTTP.

**Functional Requirements:**
- The system shall expose `POST /api/items` accepting a photo plus tag fields,
  storing the photo and item, and returning the created item as a DTO with a
  server-generated `itemId`.
- The system shall expose `GET /api/items` (list all) and `GET /api/items/{id}`
  (single), returning item DTOs including tags and a photo reference.
- The system shall expose `GET /api/items/{id}/photo` returning the stored photo
  bytes with the correct content type.
- The system shall expose `PUT /api/items/{id}/tags` to update an item's tag
  fields and persist the change.
- The system shall expose `DELETE /api/items/{id}` removing the item record and
  its photo.
- The system shall return `404` for get/update/delete/get-photo on an unknown
  `itemId`, and `400` for tag values outside their allowed ranges
  (`formality` 1–5, `warmth` 1–3) or a missing/invalid photo on create.
- Controllers shall exchange DTOs only; DynamoDB items and storage internals
  shall never leak past the service boundary.

**Proof Artifacts:**
- Test: MockMvc tests cover create, list, get, get-photo, update-tags, delete,
  plus the `404` and `400` paths, demonstrates the API contract and error
  handling.
- CLI: `curl` create → list → get → update-tags → delete against a locally
  running app returns the expected statuses/bodies, demonstrates the end-to-end
  flow.

## Non-Goals (Out of Scope)

1. **No vision tagging** — tags are supplied by the caller / stubbed here; the
   Haiku 4.5 tagging call is issue #4.
2. **No wardrobe UI** — this slice is API + persistence only; the grid, camera
   add, and tag-edit screens are issue #5.
3. **No S3** — only `LocalDiskPhotoStorage`; `S3PhotoStorage` arrives with the
   deploy work (issue #9). The interface is defined now so the swap is trivial.
4. **No wear-history write action or recency rules** — the `lastWorn` /
   `wornCount` fields exist on the model and round-trip, but the "I wore this"
   increment and recency-aware behavior are issue #7. This slice does not add a
   wear endpoint.
5. **No stylist reasoning / `searchWardrobe`** — issue #6.
6. **No authentication / passcode gate / daily cap** — issue #8.

## Design Considerations

No UI in this slice. The surface is a JSON + multipart REST API under
`/api/items`. Design choices that later UI issues depend on: photos are served
by reference (`GET /api/items/{id}/photo`) rather than embedded/base64 in item
JSON, so the wardrobe grid can lazy-load thumbnails. Keep responses small and
mobile-friendly (no unbounded nested payloads).

## Repository Standards

Follow the patterns established in issue #2 and codified in `AGENTS.md` /
`docs/`:

- **Layered architecture:** controller → service → repository/storage, under the
  `com.ensemble` package root in a `wardrobe`-focused package (mirroring the
  existing `health` / `web` package layout).
- **Data access:** AWS SDK v2 DynamoDB **Enhanced Client** only — **no Spring
  Data JPA, no relational DB** (per `AGENTS.md`). Single-item model, `itemId`
  partition key, no relationships/cascades.
- **DTOs at the boundary:** never leak the DynamoDB item or storage internals
  into controllers.
- **Strict TDD** for this backend-domain slice: Red-Green-Refactor, tests before
  code, ≥90% line coverage, 100% branch on the critical paths named below.
- **Tooling:** JUnit 5, Mockito, **TestContainers (DynamoDB Local)**, JaCoCo —
  already wired via the Gradle/JaCoCo setup from issue #2.
- **Commits:** small, conventional-commit messages, roughly one per demoable
  unit.
- **Ignore rules:** the local photo directory and any DynamoDB Local data must
  be git-ignored.

### Critical Logic Requiring 100% Branch Coverage (this slice)

- **Image compression rule:** larger-than-800px is downscaled; already-small is
  left unchanged (no upscale); non-image input raises the defined error.
- **Id validation:** get/update/delete/get-photo on an unknown `itemId` returns
  `404`; only existing ids are acted on.
- **Tag-range validation:** `formality` outside 1–5 and `warmth` outside 1–3
  are rejected with `400`.

## Technical Considerations

- **Stack:** Java 21, Spring Boot 4.1.x, Gradle — as established in issue #2.
- **DynamoDB Enhanced Client:** annotate the `Item` as a `@DynamoDbBean` (or an
  equivalent immutable/static schema) with `itemId` as the partition key; build
  a `DynamoDbEnhancedClient` over a `DynamoDbClient` configured with an
  `endpointOverride` to DynamoDB Local and a fixed region + dummy local
  credentials. Add the AWS SDK v2 BOM to `build.gradle`.
- **Table bootstrap:** on startup, create the table if absent (create-if-not-
  exists), so a fresh DynamoDB Local is usable with no manual step. Table name,
  endpoint, and region come from `application.yml` / environment.
- **DynamoDB Local:** a `docker compose` service (e.g. `amazon/dynamodb-local`)
  for manual dev; integration tests use TestContainers for isolation and
  repeatability.
- **Photo storage:** `PhotoStorage` interface; `LocalDiskPhotoStorage` writes to
  a configurable base dir with a `photoKey` derived from the server-generated
  `itemId` (e.g. `<itemId>.jpg`). Resize/re-encode to ≤800px longest edge as
  JPEG on save. The image library (e.g. Thumbnailator or `javax.imageio` +
  `Graphics2D`) is an implementation choice; the observable behavior (≤800px,
  JPEG, no upscale, error on non-image) is what is specified and tested.
- **API contract:** `POST /api/items` is `multipart/form-data` (photo part + tag
  fields); reads return JSON DTOs; `GET /api/items/{id}/photo` streams image
  bytes with the correct `Content-Type`.
- **Statelessness:** the server holds no session state; each request is
  self-contained (consistent with the stateless-server decision in
  `docs/ARCHITECTURE.md`).
- **No live Claude calls** anywhere in this slice; nothing here touches the LLM.

## Security Considerations

- **No secrets introduced** in this slice (no Claude API key yet) — nothing to
  commit.
- **Server-generated ids:** `itemId` is generated server-side (e.g. UUID) and
  the `photoKey` is derived from it, so a client cannot supply a key that
  overwrites another item or escapes the storage directory (no path traversal).
- **Upload safety:** validate that the uploaded part is a decodable image and
  enforce a sane maximum upload size, to avoid crashing or exhausting memory on
  malicious/huge input.
- **Local data hygiene:** the local photo directory and DynamoDB Local data are
  git-ignored so real photos/records are never committed.
- **Auth deferred:** the passcode gate and daily cap are issue #8; this local
  slice runs unauthenticated, which is acceptable pre-deploy.

## Success Metrics

1. A create → get → update-tags → delete round-trip passes against DynamoDB
   Local (integration test green).
2. A saved photo is verified ≤800px on its longest edge, valid JPEG, and
   retrievable via `GET /api/items/{id}/photo`.
3. Backend-domain coverage: ≥90% line on the wardrobe packages; 100% branch on
   image compression, id validation, and tag-range validation.
4. Swap-readiness: all application code references `PhotoStorage`, not a concrete
   storage class (verified by inspection / a compile-time seam).
5. The API error paths behave as specified: `404` for unknown id, `400` for
   out-of-range tags or missing/invalid photo.

## Open Questions

1. **Create request format:** assumed `multipart/form-data` (photo part + tag
   fields). Non-blocking; if the later UI (#5) prefers JSON + base64, the
   controller can add a variant without changing the model or storage.
2. **Image library:** assumed Thumbnailator or `javax.imageio`; the choice is an
   implementation detail because only the observable ≤800px-JPEG behavior is
   specified and tested. Non-blocking.
3. **Test DynamoDB Local mechanism:** assumed TestContainers for integration
   tests and the `docker compose` service for manual dev; both are sanctioned by
   `docs/TESTING.md`. Non-blocking.
4. **`photoKey` scheme:** assumed `<itemId>.jpg`. Non-blocking; internal to
   storage and never exposed as a client-supplied value.
