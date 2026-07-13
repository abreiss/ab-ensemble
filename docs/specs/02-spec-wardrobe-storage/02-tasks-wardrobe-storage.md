# 02-tasks-wardrobe-storage.md

Task plan for `02-spec-wardrobe-storage`. Strict TDD applies (backend domain):
Red-Green-Refactor, tests before code, ≥90% line, 100% branch on the critical
paths (image compression, id validation, tag-range validation).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `build.gradle` | Add AWS SDK v2 BOM + `dynamodb-enhanced`, image lib, and TestContainers deps. |
| `docker-compose.yml` | New — DynamoDB Local (`amazon/dynamodb-local`) service for local dev. |
| `.gitignore` | Exclude the local photo dir and DynamoDB Local data. |
| `src/main/resources/application.yml` | DynamoDB endpoint/region/table + photo-storage base-dir config keys. |
| `src/main/java/com/ensemble/config/DynamoDbConfig.java` | Builds `DynamoDbClient` (endpoint override, region, local dummy creds) + `DynamoDbEnhancedClient` beans. |
| `src/main/java/com/ensemble/config/DynamoDbTableInitializer.java` | Creates the table if absent on startup. |
| `src/main/java/com/ensemble/wardrobe/Item.java` | `@DynamoDbBean` model: `itemId` PK + 6 tag fields + `photoKey` + `createdAt` + `lastWorn` + `wornCount`. |
| `src/main/java/com/ensemble/wardrobe/WardrobeRepository.java` | Enhanced Client CRUD: put/get/list/update/delete. |
| `src/main/java/com/ensemble/storage/PhotoStorage.java` | Interface: save (bytes→key) / load / delete. |
| `src/main/java/com/ensemble/storage/LocalDiskPhotoStorage.java` | Disk impl; resize ≤800px + JPEG re-encode on save. |
| `src/main/java/com/ensemble/storage/InvalidImageException.java` | Thrown when input is not a decodable image. |
| `src/main/java/com/ensemble/wardrobe/WardrobeService.java` | Wires repository + photo storage; owns id generation + create/list/get/updateTags/delete. |
| `src/main/java/com/ensemble/wardrobe/web/WardrobeController.java` | REST endpoints under `/api/items`. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Maps not-found → `404`, validation → `400`. |
| `src/main/java/com/ensemble/wardrobe/dto/ItemResponse.java` | Outbound item DTO (tags + photo reference; no storage internals). |
| `src/main/java/com/ensemble/wardrobe/dto/UpdateTagsRequest.java` | Inbound tag-update DTO with range validation. |
| `src/main/java/com/ensemble/wardrobe/dto/ItemMapper.java` | Maps `Item` ↔ DTOs so entities never leak past the service. |
| `src/test/java/com/ensemble/wardrobe/WardrobeRepositoryIT.java` | TestContainers DynamoDB Local round-trip (create/get/list/update/delete + empty). |
| `src/test/java/com/ensemble/config/DynamoDbTableInitializerIT.java` | Asserts table auto-create against DynamoDB Local. |
| `src/test/java/com/ensemble/storage/LocalDiskPhotoStorageTest.java` | Resize ≤800px, no-upscale, non-image error, load/delete. |
| `src/test/java/com/ensemble/wardrobe/WardrobeServiceTest.java` | Service logic with mocked repo + storage (Mockito). |
| `src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java` | MockMvc contract + `404`/`400` error paths. |
| `src/test/java/com/ensemble/wardrobe/dto/ItemMapperTest.java` | Mapping correctness both directions. |
| `docs/specs/02-spec-wardrobe-storage/02-proofs/` | Proof artifacts (curl transcripts, coverage summary, photo check). |
| `README.md` / `docs/DEVELOPMENT.md` | Document the storage local-run (DynamoDB Local + photo dir + sample curls). |

### Notes

- Backend tests run with `./gradlew test -PskipFrontend`; coverage via `./gradlew jacocoTestReport` (report in `build/reports/jacoco/`).
- Integration tests (`*IT`) use TestContainers (`amazon/dynamodb-local`); keep them isolated and repeatable — no shared state, no live network beyond the container.
- Follow the existing package-by-feature layout (`com.ensemble.health`, `com.ensemble.web`); new code lives under `com.ensemble.wardrobe`, `com.ensemble.storage`, `com.ensemble.config`.
- No live Claude calls anywhere in this slice. No secrets committed (pre-commit `sk-ant` scan is active).
- Image library (Thumbnailator vs `javax.imageio`) is an implementation choice; only the observable ≤800px-JPEG behavior is tested (spec Open Question 2).

## Tasks

### [x] 1.0 Persistence foundation (AWS SDK v2 + DynamoDB Local wiring)

#### 1.0 Proof Artifact(s)

- CLI: `docker compose up -d dynamodb` starts the container and `docker compose ps` shows it running — demonstrates the local data dependency runs.
- CLI: `./gradlew bootRun` (DynamoDB Local up) logs table create-if-absent and starts clean — demonstrates the Enhanced Client connects and the table is auto-created.
- Diff: `build.gradle` shows the AWS SDK v2 BOM + `dynamodb-enhanced`; `application.yml` shows endpoint/region/table keys — demonstrates configuration is in place.
- Diff: `.gitignore` excludes the local photo dir and DynamoDB Local data — demonstrates local data hygiene.

#### 1.0 Tasks

- [x] 1.1 Add to `build.gradle`: the `software.amazon.awssdk:bom` platform, `dynamodb-enhanced`, the image library, and `testImplementation` TestContainers (`junit-jupiter`).
- [x] 1.2 Add config keys to `application.yml`: `ensemble.dynamodb.endpoint`, `.region`, `.table-name`, and `ensemble.photos.dir` (with local defaults; dummy local creds).
- [x] 1.3 Write `DynamoDbConfig` beans: `DynamoDbClient` (endpoint override + region + static dummy credentials) and `DynamoDbEnhancedClient`.
- [x] 1.4 Add `docker-compose.yml` with a `dynamodb` service (`amazon/dynamodb-local`, port 8000, local data volume).
- [x] 1.5 RED→GREEN: `DynamoDbTableInitializerIT` asserts the table exists after startup (TestContainers); implement `DynamoDbTableInitializer` (create-if-absent on `ApplicationRunner`).
- [x] 1.6 Update `.gitignore` to exclude `ensemble.photos.dir` contents and any DynamoDB Local data directory.

### [x] 2.0 Item model + WardrobeRepository (Enhanced Client CRUD)

#### 2.0 Proof Artifact(s)

- Test: `WardrobeRepositoryIT` (TestContainers) create → get → update → delete passes — demonstrates real persistence round-trips.
- Test: get/list against an empty table returns absent/empty rather than throwing — demonstrates the empty-wardrobe edge case.
- Coverage: `./gradlew jacocoTestReport` shows ≥90% line on `com.ensemble.wardrobe` model/repository — demonstrates the TDD standard.

#### 2.0 Tasks

- [x] 2.1 RED: write `WardrobeRepositoryIT` (TestContainers DynamoDB Local) asserting a create→get round-trip; run and confirm it fails.
- [x] 2.2 GREEN: implement `Item` as a `@DynamoDbBean` — `itemId` partition key, `category`, `primaryColor`, `secondaryColor`, `formality` (int), `pattern`, `warmth` (int), `descriptors` (list), `photoKey`, `createdAt`, `lastWorn`, `wornCount`.
- [x] 2.3 GREEN: implement `WardrobeRepository` (put/get/list-scan/update/delete) over the enhanced table; make the round-trip test pass.
- [x] 2.4 RED→GREEN: extend `WardrobeRepositoryIT` with update, delete, list-all, and empty-wardrobe cases; implement to green.
- [x] 2.5 REFACTOR: tidy naming/structure; confirm `jacocoTestReport` ≥90% line on the package.

### [ ] 3.0 PhotoStorage interface + LocalDiskPhotoStorage (≤800px JPEG)

#### 3.0 Proof Artifact(s)

- Test: saving a >800px image then loading shows longest edge ≤800px and valid JPEG — demonstrates the compression rule (critical branch).
- Test: saving an already-small image leaves dimensions unchanged — demonstrates no-upscale (critical branch).
- Test: saving non-image bytes raises `InvalidImageException` — demonstrates the failure path (critical branch).
- Coverage: JaCoCo shows 100% branch on the resize/validate logic — demonstrates critical-logic coverage.

#### 3.0 Tasks

- [ ] 3.1 RED: write `LocalDiskPhotoStorageTest#save_largeImage_downscaledToMax800JpegOnLoad` using a generated >800px image (temp dir); confirm it fails.
- [ ] 3.2 GREEN: define the `PhotoStorage` interface (save/load/delete) and `InvalidImageException`.
- [ ] 3.3 GREEN: implement `LocalDiskPhotoStorage` — write to base dir, resize longest edge ≤800px, re-encode JPEG; make the test pass.
- [ ] 3.4 RED→GREEN: add tests for no-upscale (small image unchanged) and non-image input → `InvalidImageException`; implement to green.
- [ ] 3.5 REFACTOR: extract resize helper if needed; confirm JaCoCo 100% branch on resize/validate.

### [ ] 4.0 Wardrobe CRUD API (service + controller + DTOs + validation)

#### 4.0 Proof Artifact(s)

- Test: `WardrobeControllerTest` (MockMvc) covers create, list, get, get-photo, update-tags, delete — demonstrates the API contract.
- Test: MockMvc `404` on unknown id and `400` on `formality`/`warmth` out of range and missing photo — demonstrates error handling (critical branch).
- Coverage: JaCoCo ≥90% line on `com.ensemble.wardrobe` service/web and 100% branch on id/tag-range validation — demonstrates critical-logic coverage.

#### 4.0 Tasks

- [ ] 4.1 RED: write `WardrobeControllerTest#createItem_multipart_returns201WithServerId` (MockMvc, mocked service); confirm it fails.
- [ ] 4.2 GREEN: implement DTOs (`ItemResponse`, `UpdateTagsRequest` with range validation) + `ItemMapper` (with `ItemMapperTest`).
- [ ] 4.3 GREEN: implement `WardrobeService` — generate `itemId`, store photo → `photoKey`, set `createdAt`, persist; plus list/get/updateTags/delete (with `WardrobeServiceTest`, mocked repo + storage).
- [ ] 4.4 GREEN: implement `WardrobeController` endpoints (create multipart, list, get, get-photo returning bytes + `image/jpeg`, update tags, delete); make contract tests pass.
- [ ] 4.5 RED→GREEN: add `ApiExceptionHandler` — `404` unknown id, `400` out-of-range tags + missing/invalid photo; MockMvc asserts each.
- [ ] 4.6 REFACTOR: confirm DTO-only boundary (no `Item`/storage type leaks past service); JaCoCo ≥90% line + 100% branch on validation.

### [ ] 5.0 End-to-end local run + docs

#### 5.0 Proof Artifact(s)

- CLI: against a running app, `curl` `POST /api/items` (multipart) then `GET /api/items`, `GET /api/items/{id}`, `PUT /api/items/{id}/tags`, `DELETE /api/items/{id}` return expected statuses/bodies — demonstrates the end-to-end flow.
- CLI: `curl -s -o out.jpg -w "%{content_type}" /api/items/{id}/photo` returns `image/jpeg` and a file whose longest edge is ≤800px — demonstrates the stored-photo contract.
- Diff: `README.md` gains a storage/run section (`docker compose up -d dynamodb`, photo-dir env, sample `curl`s) — demonstrates a new developer can run the slice.

#### 5.0 Tasks

- [ ] 5.1 Add a "Wardrobe storage" section to `README.md`: `docker compose up -d dynamodb`, `ensemble.photos.dir`, and sample `curl`s for the CRUD flow.
- [ ] 5.2 Align `docs/DEVELOPMENT.md` (DynamoDB Local run note already references `docker compose up dynamodb`).
- [ ] 5.3 Capture the end-to-end `curl` transcript (create→list→get→update→delete) into `02-proofs/`.
- [ ] 5.4 Capture the get-photo proof (`content_type=image/jpeg`, longest edge ≤800px) into `02-proofs/`.
- [ ] 5.5 Run `./gradlew test -PskipFrontend jacocoTestReport`; save the coverage summary into `02-proofs/`.
