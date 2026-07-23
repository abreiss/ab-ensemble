# 15-tasks-per-user-data-scoping.md

> Task list for [spec 15 — per-user data scoping](./15-spec-per-user-data-scoping.md)
> (GitHub issue [#15](https://github.com/abreiss/ab-ensemble/issues/15)). Parent
> tasks map one-to-one to the spec's five Demoable Units of Work.

## Parent Task → Spec Traceability

| Parent Task | Spec Unit | Primary Functional Requirements | Acceptance / Success Metric |
| --- | --- | --- | --- |
| 1.0 Owner-stamped, per-user-queryable data model + GSI IaC/IAM | Unit 1 | `userId` on `Item`/`SavedOutfit`; sparse `userId-index` GSI; `findByUserId` GSI query; stamp on write; Terraform GSI + `index/*` IAM; sparse index excludes `usage#` rows | #3 (no scan), #5 (`terraform validate` + Access Analyzer pass) |
| 2.0 Ownership-enforced wardrobe & outfit APIs | Unit 2 | `@CurrentUserId` on every wardrobe + outfit handler; scoped `create`/`list`; `find(userId, id)` → 404 on missing **or** cross-user | #1 (two-account isolation) |
| 3.0 Per-user photo namespacing + cross-user photo isolation | Unit 3 | `photoKey = <userId>/<itemId>.jpg`; `LocalDiskPhotoStorage.save` creates parent dirs; traversal guard retained; photo endpoint ownership | #1 (photo 404 for other user) |
| 4.0 User-scoped stylist + cross-user grounding guardrail | Unit 4 | `style(userId, …)` + scoped `validIds`; cross-user id rejected exactly like hallucinated id; no image bytes; stateless | #2 (stylist privacy), #3 (100% branch) |
| 5.0 One-time cleanup of pre-existing unowned data | Unit 5 | Idempotent, opt-in `ApplicationRunner` deleting only unowned `Item`/`SavedOutfit` rows + their photos; never `usage#` or owned rows | #4 (scoped cleanup) |

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/wardrobe/Item.java` | Add `userId` field + `@DynamoDbSecondaryPartitionKey(indexNames = "userId-index")` getter (Unit 1). |
| `src/main/java/com/ensemble/outfit/SavedOutfit.java` | Add `userId` field + secondary partition key (Unit 1). |
| `src/main/java/com/ensemble/wardrobe/WardrobeRepository.java` | Add `findByUserId` (GSI query) and `findUnowned` (scan for purge); retain `findById`/`save`/`deleteById`; remove `findAll` once no caller remains (Units 1/4/5). |
| `src/main/java/com/ensemble/outfit/OutfitRepository.java` | Add `findByUserId` + `findUnowned`; keep `findById`/`save`/`deleteById` (Units 1/5). |
| `src/main/java/com/ensemble/config/DynamoDbTableInitializer.java` | Extend `ensureTable(...)` with an optional `userId` GSI (index name + attribute def) for items + outfits; users stays plain (Unit 1). |
| `src/main/java/com/ensemble/wardrobe/WardrobeService.java` | Scoped `create(userId, …)` (stamp + namespaced key), `list(userId)`, `find(userId, itemId)` choke point → `ItemNotFoundException` (Units 2/3). |
| `src/main/java/com/ensemble/outfit/OutfitService.java` | Scoped `create(userId, …)`, `list(userId)`, `find(userId, outfitId)` (Unit 2). |
| `src/main/java/com/ensemble/wardrobe/web/WardrobeController.java` | `@CurrentUserId String userId` on all 7 handlers, forwarded to service (Units 2/3). |
| `src/main/java/com/ensemble/outfit/web/OutfitController.java` | `@CurrentUserId` on `save`/`list`/`delete` (Unit 2). |
| `src/main/java/com/ensemble/stylist/web/StyleController.java` | `@CurrentUserId` on `style`; scoped `enrich(outfit, userId)` via `wardrobe.list(userId)` (Unit 4). |
| `src/main/java/com/ensemble/stylist/StylistService.java` | `style(userId, vibe, history)`; build wardrobe text + `validIds` from `wardrobe.list(userId)`; guardrail unchanged in shape (Unit 4). |
| `src/main/java/com/ensemble/storage/LocalDiskPhotoStorage.java` | `save` must `createDirectories(parent)` before write for nested keys; retain `resolve(...)` traversal guard (Unit 3). |
| `src/main/java/com/ensemble/migration/UnownedDataPurgeRunner.java` | **New.** Opt-in, idempotent `ApplicationRunner` mirroring `SeedAccountRunner` (Unit 5). |
| `src/main/java/com/ensemble/migration/MigrationProperties.java` | **New.** `@ConfigurationProperties(prefix = "ensemble.migration")` with `purgeUnowned` (default false) (Unit 5). |
| `src/main/java/com/ensemble/config/DynamoDbConfig.java` | Add `MigrationProperties.class` to `@EnableConfigurationProperties` (mirrors Seed/Photo props) (Unit 5). |
| `src/main/resources/application.yml` | Add `ensemble.migration.purge-unowned: ${ENSEMBLE_MIGRATION_PURGE_UNOWNED:false}` (Unit 5). |
| `.env.example` | Document `ENSEMBLE_MIGRATION_PURGE_UNOWNED=` (default off) (Unit 5). |
| `terraform/deploy/data_stores.tf` | Add `userId` `attribute` + `global_secondary_index { name="userId-index" hash_key="userId" projection_type="ALL" }` on `items` and `outfits` tables (Unit 1). |
| `terraform/deploy/iam.tf` | Add `"${local.dynamodb_arn}/index/*"` to the `RuntimeDynamoDb` statement resources (`Query` already granted; **do not** edit the immutable policy `description`) (Unit 1). |
| `terraform/deploy/policies/abreiss-ensemble-instance-runtime.json` | Rendered lint artifact — mirror the `index/*` Resource so CI's Access Analyzer lint stays in sync (Unit 1). |
| `terraform/deploy/apprunner.tf` | (Optional) expose `ENSEMBLE_MIGRATION_PURGE_UNOWNED` via a tfvar so an operator can opt in for one deploy (Unit 5). |
| `README.md` | "Wardrobe Storage": note the local drop-and-recreate step for the new GSI; add the two-account isolation CLI check (Units 1/2). |
| `docs/ARCHITECTURE.md` | Confirm/adjust the GSI + `ENSEMBLE_SESSION_SECRET` isolation-trust note now that data is `userId`-scoped (Unit 2). |
| `src/test/java/com/ensemble/wardrobe/WardrobeRepositoryIT.java` | Add `findByUserId_*` + `findByUserId_excludesUsageCounterRows`; update table setup for the new `ensureTable` GSI signature (Unit 1). |
| `src/test/java/com/ensemble/outfit/OutfitRepositoryIT.java` | Add `findByUserId_returnsOnlyThatUsersOutfits` (Unit 1); `findUnowned` coverage (Unit 5). |
| `src/test/java/com/ensemble/config/DynamoDbTableInitializerIT.java` | Update for the GSI-declaring `ensureTable`; assert the index exists (Unit 1). |
| `src/test/java/com/ensemble/wardrobe/WardrobeServiceTest.java` | Scoped `list`/`find` tests; update hard-coded `<itemId>.jpg` key assertions to `<userId>/<itemId>.jpg` (Units 2/3). |
| `src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java` | Cross-user `get`/`updateTags`/`markWorn`/`delete`/`photo` → 404 via MockMvc with `.requestAttr("ensemble.userId", …)` (Units 2/3). |
| `src/test/java/com/ensemble/outfit/OutfitServiceTest.java` | Scoped `list`/`find`; cross-user 404 (Unit 2). |
| `src/test/java/com/ensemble/outfit/web/OutfitControllerTest.java` | Cross-user `delete` → 404; scoped `list` (Unit 2). |
| `src/test/java/com/ensemble/storage/LocalDiskPhotoStorageTest.java` | `save_nestedKey_createsParentDirsAndStores`; retain `resolve_pathTraversalKey_isRejected` (Unit 3). |
| `src/test/java/com/ensemble/stylist/StylistServiceTest.java` | `styleRequest_withOtherUsersId_retriesOnceThenRejects`; migrate existing grounding tests to `style(userId, …)` + `wardrobe.list(userId)` stubs (Unit 4). |
| `src/test/java/com/ensemble/stylist/web/StyleControllerTest.java` | Thread `userId` via `.requestAttr(...)`; assert `enrich` uses the scoped list (Unit 4). |
| `src/test/java/com/ensemble/migration/UnownedDataPurgeRunnerIT.java` | **New.** DynamoDB Local: mixed seed → purge → only unowned rows/photos gone (Unit 5). |
| `src/test/java/com/ensemble/migration/UnownedDataPurgeRunnerTest.java` | **New.** `purge_whenDisabled_isNoOp`, `purge_secondRun_isNoOp` (Mockito) (Unit 5). |

### Notes

- **Strict TDD** on all backend domain logic (model, repositories, services, photo
  storage, stylist guardrail, purge runner): write the failing test first
  (RED), minimum code to pass (GREEN), then REFACTOR. Terraform/IaC and doc changes
  are validated with `terraform fmt`/`validate` + the Access Analyzer lint, not
  unit-tested (per `docs/TESTING.md`).
- **Green-commit sequencing (implement 1.0 → 5.0 in order on one branch).** To keep
  every commit compiling: Unit 1 **adds** `findByUserId` while **retaining**
  `WardrobeService.list()`/`WardrobeRepository.findAll()`; Unit 2 adds the scoped
  `list(userId)` and migrates the wardrobe + outfit callers, leaving the no-arg
  `list()` only for the stylist; Unit 4 migrates the stylist and **removes** the
  last unscoped `list()`/`findAll()`. The fully-scoped, gap-closed state exists at
  merge.
- **Controller slice tests:** `@WebMvcTest` loads `CurrentUserWebConfig`
  (a `WebMvcConfigurer`), so `@CurrentUserId` resolves from the
  `ensemble.userId` request attribute — set it with
  `.requestAttr(SessionAuthFilter.USER_ID_ATTRIBUTE, "userA")` (no live filter needed).
- Run backend tests with `./gradlew test -PskipFrontend`; coverage with
  `./gradlew jacocoTestReport`. No frontend code changes are expected (Non-Goal #7);
  frontend tests must still pass.

## Tasks

### [x] 1.0 Owner-stamped, per-user-queryable data model (items & outfits) + GSI IaC/IAM

**Demoable outcome:** With two users' rows written to one DynamoDB Local table, a
per-user `findByUserId(userId)` GSI query returns only that user's rows (no
full-table scan), `usage#<date>` counter rows never surface, and the deployed
tables + instance-role policy gain the index and the `index/*` query grant that
`terraform validate` and the Access Analyzer lint accept.

#### 1.0 Proof Artifact(s)

- Test: `WardrobeRepositoryIT.findByUserId_returnsOnlyThatUsersItems` passes —
  `./gradlew test -PskipFrontend --tests '*WardrobeRepositoryIT'`; with user A's
  and user B's items in one table, the query returns only A's rows.
- Test: `OutfitRepositoryIT.findByUserId_returnsOnlyThatUsersOutfits` passes —
  `./gradlew test -PskipFrontend --tests '*OutfitRepositoryIT'`.
- Test: `WardrobeRepositoryIT.findByUserId_excludesUsageCounterRows` passes — a
  seeded `usage#<date>` row (no `userId`) never appears in a per-user query.
- Diff: `git diff terraform/deploy/data_stores.tf` shows a `global_secondary_index`
  (`name = "userId-index"`, `hash_key = "userId"`, `projection_type = "ALL"`) plus a
  `userId` `attribute` on **both** the `items` and `outfits` tables.
- Diff: `git diff terraform/deploy/iam.tf terraform/deploy/policies/abreiss-ensemble-instance-runtime.json`
  shows the `RuntimeDynamoDb` Resource extended with `…table/${prefix}-*/index/*`
  in **both** files (action list unchanged — `dynamodb:Query` already present).
- CLI: `cd terraform/deploy && terraform fmt -check -recursive && terraform init -backend=false && terraform validate`
  prints `Success! The configuration is valid.`

#### 1.0 Tasks

- [x] 1.1 (RED) In `WardrobeRepositoryIT`, add `findByUserId_returnsOnlyThatUsersItems`
  (write items for `userA` and `userB`, assert the query returns only `userA`'s) and
  `findByUserId_excludesUsageCounterRows` (seed a `usage#<date>` row, assert it never
  appears). Update the per-test table setup to create the `userId-index` GSI via the
  new `ensureTable` signature (task 1.4). Confirm the tests fail to compile/pass.
- [x] 1.2 (GREEN) Add a `userId` String field to `Item` with getter/setter; annotate the
  getter `@DynamoDbSecondaryPartitionKey(indexNames = "userId-index")` and import it
  from `software.amazon.awssdk.enhanced.dynamodb.mapper.annotations`. Keep `itemId` as
  the `@DynamoDbPartitionKey`.
- [x] 1.3 (GREEN) Add the same `userId` + `@DynamoDbSecondaryPartitionKey(indexNames = "userId-index")`
  to `SavedOutfit`, keeping `outfitId` as the primary partition key.
- [x] 1.4 (GREEN) Extend `DynamoDbTableInitializer.ensureTable(...)` with an overload that
  also declares the `userId` GSI: add an `AttributeDefinition` for `userId` (type `S`)
  and a `GlobalSecondaryIndex` (`indexName("userId-index")`, HASH `userId`,
  `projection(ProjectionType.ALL)`); no throughput needed under `PAY_PER_REQUEST`.
  Call the GSI overload for the items and outfits tables in `run(...)`; leave the users
  table on the plain 2-arg form. Keep the skip-if-exists idempotency.
- [x] 1.5 (GREEN) Add `WardrobeRepository.findByUserId(String userId)` using
  `table.index("userId-index").query(QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))`,
  flattening pages to `List<Item>`. Add `OutfitRepository.findByUserId(String userId)`
  equivalently. Retain `findAll()`/`findById`/`save`/`deleteById` for now (removed in Unit 4).
- [x] 1.6 (GREEN) Update `DynamoDbTableInitializerIT` for the new GSI-declaring signature and
  assert the `userId-index` exists on the created items/outfits tables.
- [x] 1.7 Add the `userId` `attribute` + `global_secondary_index` block (name `userId-index`,
  hash_key `userId`, `projection_type = "ALL"`) to the `items` and `outfits` tables in
  `terraform/deploy/data_stores.tf`. Do not touch the `users` table.
- [x] 1.8 In `terraform/deploy/iam.tf`, add `"${local.dynamodb_arn}/index/*"` to the
  `RuntimeDynamoDb` statement `resources` list (Query is already in `actions`). Leave the
  `aws_iam_policy.instance` `description` string unchanged (it is immutable — editing forces
  a destroy/recreate). Mirror the same two-ARN `Resource` array in
  `terraform/deploy/policies/abreiss-ensemble-instance-runtime.json`.
- [x] 1.9 Run `cd terraform/deploy && terraform fmt -check -recursive && terraform init -backend=false && terraform validate`;
  capture the `Success!` output as the proof artifact.
- [x] 1.10 Update `README.md` "Wardrobe Storage": document that a local dev whose
  `ensemble-items`/`ensemble-outfits` tables predate the GSI must drop them (initializer
  skips existing tables) so they are recreated with `userId-index`; the deployed tables
  get the index via Terraform (in-place `UpdateTable`, no recreation). In
  `DynamoDbTableInitializer`, when a table already exists, log a WARN if it is missing the
  `userId-index` (turns the silent dev footgun into a visible, actionable message).
- [x] 1.11 (REFACTOR) Run `./gradlew test -PskipFrontend`; keep tests green and remove
  duplication in the initializer/repository GSI wiring.

### [x] 2.0 Ownership-enforced wardrobe & outfit APIs

**Demoable outcome:** Two logged-in accounts each see only their own items and
outfits; every single-resource operation (`get`, `updateTags`, `markWorn`,
`delete`, outfit `delete`) on another user's id returns a non-enumerating **404**,
never that resource's contents — closing the live cross-user authorization gap.

#### 2.0 Proof Artifact(s)

- Test: `WardrobeServiceTest.list_returnsOnlyCallersItems` and
  `WardrobeServiceTest.find_otherUsersItem_throwsNotFound` pass —
  `./gradlew test -PskipFrontend --tests '*WardrobeServiceTest'`.
- Test: `WardrobeControllerTest` cross-user `get`/`updateTags`/`markWorn`/`delete`
  return **404** via MockMvc.
- Test: `OutfitServiceTest`/`OutfitControllerTest` scoped `list` + cross-user
  `delete` → 404.
- CLI: with shell vars `$TOKEN_A` / `$TOKEN_B` (never literal secrets),
  `curl -s localhost:8080/api/items -H "X-Ensemble-Session: $TOKEN_A"` and `…$TOKEN_B`
  return disjoint id sets, and
  `curl -s -o /dev/null -w '%{http_code}' -X DELETE localhost:8080/api/items/<A_id> -H "X-Ensemble-Session: $TOKEN_B"`
  prints `404`.

#### 2.0 Tasks

- [x] 2.1 (RED) In `WardrobeServiceTest`, add `list_returnsOnlyCallersItems` (stub
  `repository.findByUserId("userA")`) and `find_otherUsersItem_throwsNotFound` (repo returns
  an item whose `userId` is `userB` → expect `ItemNotFoundException`). Also add
  `create_stampsCallerUserId`: capture the `Item` passed to `repository.save(...)` with an
  `ArgumentCaptor` and assert its `getUserId()` equals the caller (guards the "no owned row
  written without an owner" FR). Confirm failing.
- [x] 2.2 (GREEN) `WardrobeService`: change the private choke point to
  `find(String userId, String itemId)` — `repository.findById(itemId)` then throw
  `ItemNotFoundException(itemId)` if empty **or** `!item.getUserId().equals(userId)`. Route
  `get`/`updateTags`/`markWorn`/`delete`/`loadPhoto` through it.
- [x] 2.3 (GREEN) `WardrobeService.create(...)`: add a `userId` parameter, set it on the new
  `Item` before save (satisfying `create_stampsCallerUserId` from 2.1). Add `list(String userId)`
  returning `repository.findByUserId(userId)` mapped to DTOs. (Keep the no-arg `list()` only until Unit 4.)
- [x] 2.4 (RED→GREEN) `WardrobeControllerTest`: add cross-user 404 cases for
  `get`/`updateTags`/`markWorn`/`delete` using `.requestAttr("ensemble.userId", "userB")`
  against a service stubbed to throw for the foreign id. Then add `@CurrentUserId String userId`
  to all seven `WardrobeController` handlers and forward it to the service.
- [x] 2.5 (RED) In `OutfitServiceTest`, add `list_returnsOnlyCallersOutfits`,
  `find_otherUsersOutfit_throwsNotFound`, and `create_stampsCallerUserId` (ArgumentCaptor on
  `repository.save(...)` asserting the persisted `SavedOutfit.getUserId()` equals the caller).
  Confirm failing.
- [x] 2.6 (GREEN) `OutfitService`: `create(userId, request)` stamps `userId` (satisfying
  `create_stampsCallerUserId` from 2.5) and builds `validIds` from `wardrobeService.list(userId)`;
  `list(userId)` → `repository.findByUserId(userId)`; `find(userId, outfitId)` choke point →
  `OutfitNotFoundException` on missing or cross-user.
- [x] 2.7 (RED→GREEN) `OutfitControllerTest`: cross-user `delete` → 404 and scoped `list`; then add
  `@CurrentUserId` to `OutfitController.save`/`list`/`delete` and forward it.
- [x] 2.8 Update `docs/ARCHITECTURE.md` / `README.md` to state that per-user isolation is now
  enforced and is only trustworthy with a distinct `ENSEMBLE_SESSION_SECRET` set (the
  invite-code fallback lets any invited user forge a `userId`); keep the existing startup warning.
- [x] 2.9 (Proof) With two seeded accounts, capture the two-token `GET /api/items` disjoint-set
  output and the cross-user `DELETE … → 404` (sanitized: `$TOKEN_A`/`$TOKEN_B`, redacted ids).
- [x] 2.10 (REFACTOR) `./gradlew test -PskipFrontend` green; de-duplicate the ownership choke-point
  logic between wardrobe and outfit services where sensible.

### [ ] 3.0 Per-user photo namespacing + cross-user photo isolation

**Demoable outcome:** New items store their photo under a per-user key
`<userId>/<itemId>.jpg`; the local-disk backend writes nested keys correctly while
still rejecting path-traversal keys; and `GET /api/items/{id}/photo` for an item
owned by another user returns **404**, never the image bytes.

#### 3.0 Proof Artifact(s)

- Test: `WardrobeServiceTest.create_namespacesPhotoKeyPerUser` asserts the stored key
  equals `<userId>/<itemId>.jpg`.
- Test: `LocalDiskPhotoStorageTest.save_nestedKey_createsParentDirsAndStores` passes and the
  retained `resolve_pathTraversalKey_isRejected` still passes —
  `./gradlew test -PskipFrontend --tests '*LocalDiskPhotoStorageTest'`.
- Test: `WardrobeControllerTest.photo_otherUsersItem_returns404` (MockMvc) returns `404` with
  the JSON error shape, not image bytes.

#### 3.0 Tasks

- [ ] 3.1 (RED) In `LocalDiskPhotoStorageTest`, add `save_nestedKey_createsParentDirsAndStores`
  (save under `"user1/abc.jpg"`, then `load` returns the bytes). Keep
  `resolve_pathTraversalKey_isRejected` (`"../escape.jpg"`). Confirm the nested-key test fails
  (`NoSuchFileException`/`UncheckedIOException`).
- [ ] 3.2 (GREEN) In `LocalDiskPhotoStorage.save`, `Files.createDirectories(target.getParent())`
  before `Files.write`. Do not weaken `resolve(...)`; the traversal guard still rejects escaping keys.
- [ ] 3.3 (RED) In `WardrobeServiceTest`, add `create_namespacesPhotoKeyPerUser` and update the
  existing key assertions (`create_generatesIdStoresPhotoAndPersistsItem`,
  `delete_removesItemRecordBeforePhoto`, `loadPhoto`) from `<itemId>.jpg` to
  `<userId>/<itemId>.jpg`. Confirm failing.
- [ ] 3.4 (GREEN) In `WardrobeService.create`, derive `photoKey = userId + "/" + itemId + ".jpg"`
  (replacing `itemId + ".jpg"`), store it on the `Item`, and use the stored key for save/load/delete.
- [ ] 3.5 (RED→GREEN) In `WardrobeControllerTest`, add `photo_otherUsersItem_returns404`
  (service stubbed to throw `ItemNotFoundException` for the foreign id via `loadPhoto`). Confirm the
  photo handler forwards `@CurrentUserId` (from 2.4) into `loadPhoto`→`find(userId, itemId)`, so the
  cross-user request 404s rather than returning bytes.
- [ ] 3.6 Confirm no `S3PhotoStorage` or `PhotoStorage`-interface change is required (nested key is a
  native S3 prefix; `key` stays an opaque `String`) — note this in the commit body.
- [ ] 3.7 (REFACTOR) `./gradlew test -PskipFrontend` green.

### [ ] 4.0 User-scoped stylist + cross-user grounding guardrail

**Demoable outcome:** The stylist builds outfits only from the caller's wardrobe;
an item id belonging to a **different** user is rejected, fed back, retried once,
and dropped exactly like a hallucinated id (privacy boundary); no image bytes ever
reach the model; and JaCoCo shows grounding/id-validation still at 100% branch,
now covering the cross-user rejection.

#### 4.0 Proof Artifact(s)

- Test: `StylistServiceTest.styleRequest_withOtherUsersId_retriesOnceThenRejects` passes
  (`verify(model, times(2))`, foreign id dropped) —
  `./gradlew test -PskipFrontend --tests '*StylistServiceTest'`.
- Test: existing grounding suite (`styleRequest_withHallucinatedId_*`,
  `styleRequest_allIdsInvalidAfterRetry_returnsError`, `styleRequest_sendsNoImageBytesToModel`)
  still passes against the scoped `style(userId, …)` signature.
- Coverage: `./gradlew jacocoTestReport` → `build/reports/jacoco/test/html/index.html` shows
  grounding/id-validation (`StylistService.pickWithOneRetry`/`invalidIds` + the `validIds`
  filter) at **100% branch**, including the cross-user path.

#### 4.0 Tasks

- [ ] 4.1 (RED) In `StylistServiceTest`, add `styleRequest_withOtherUsersId_retriesOnceThenRejects`:
  stub `wardrobe.list("userA")` to return only A's items, have the model return B's id, assert one
  retry then the id is dropped (identical handling to a hallucinated id). Confirm failing against the
  current unscoped signature.
- [ ] 4.2 (GREEN) `StylistService`: change `style(String vibe, List<StylistMessage> history)` →
  `style(String userId, String vibe, List<StylistMessage> history)` (and the one-arg convenience to
  `style(userId, vibe)`); read `wardrobe.list(userId)` at the top; build `wardrobeText` + `validIds`
  from that scoped list. The reject → one-retry → drop → `StylistUnavailableException` mechanism is
  unchanged.
- [ ] 4.3 (GREEN) Migrate the existing `StylistServiceTest` cases to the new signature: pass a `userId`
  and change `when(wardrobe.list())` stubs to `when(wardrobe.list(userId))`. Keep every assertion
  (including `sendsNoImageBytesToModel` and the re-pick no-image case) intact.
- [ ] 4.4 (RED→GREEN) `StyleControllerTest`: thread `userId` via `.requestAttr("ensemble.userId", …)`;
  then add `@CurrentUserId String userId` to `StyleController.style`, pass it to
  `service.style(userId, prompt, history)`, and change `enrich(outfit)` →
  `enrich(outfit, userId)` using `wardrobe.list(userId)`.
- [ ] 4.5 (GREEN) Remove the now-unused no-arg `WardrobeService.list()` and
  `WardrobeRepository.findAll()` (the last unscoped readers are gone). Confirm the whole suite compiles,
  and verify no unscoped reader remains: `grep -rn "\.findAll()\|\.list()" src/main/java/com/ensemble/{wardrobe,outfit,stylist}`
  returns no wardrobe/outfit callers (the migration `findUnowned` scan is the only sanctioned full-table read).
- [ ] 4.6 (Proof) `./gradlew jacocoTestReport`; confirm grounding/id-validation branch coverage is 100%
  including the cross-user branch; capture the report path/percentage.
- [ ] 4.7 (REFACTOR) `./gradlew test -PskipFrontend` green; ensure the `StyleController`'s single scoped
  wardrobe read is reused (no second unscoped read).

### [ ] 5.0 One-time cleanup of pre-existing unowned data

**Demoable outcome:** An opt-in, idempotent startup runner (default **off**)
deletes every pre-existing `Item` and `SavedOutfit` row that has **no** `userId`
plus each such item's stored photo, while provably leaving owned rows and the
reserved `usage#<date>` counter rows untouched — and is a no-op when disabled or
re-run.

#### 5.0 Proof Artifact(s)

- Test: `UnownedDataPurgeRunnerIT` (DynamoDB Local) seeds unowned items/outfits + owned items + a
  `usage#<date>` row, runs the purge, and asserts **only** the unowned rows and their photos are gone
  while owned rows and the usage counter survive —
  `./gradlew test -PskipFrontend --tests '*UnownedDataPurgeRunnerIT'`.
- Test: `UnownedDataPurgeRunnerTest.purge_whenDisabled_isNoOp` and `purge_secondRun_isNoOp` pass.
- Diff: `git diff src/main/resources/application.yml .env.example` shows the new
  `ensemble.migration.purge-unowned` flag (default `false`) with a comment and the
  `${ENSEMBLE_MIGRATION_PURGE_UNOWNED:false}` mapping.

#### 5.0 Tasks

- [ ] 5.1 (RED) Add `UnownedDataPurgeRunnerTest` (Mockito): `purge_whenDisabled_isNoOp` (flag false →
  repositories/photoStorage never touched) and `purge_secondRun_isNoOp` (empty unowned result → no
  deletes). Confirm failing (class does not exist yet).
- [ ] 5.2 (GREEN) Create `MigrationProperties` (`@ConfigurationProperties(prefix = "ensemble.migration")`,
  `boolean purgeUnowned` default false) and register it in `DynamoDbConfig`'s
  `@EnableConfigurationProperties`.
- [ ] 5.3 (GREEN) Add `WardrobeRepository.findUnowned()` (scan → items with null/blank `userId` **and**
  not `usage#`-prefixed) and `OutfitRepository.findUnowned()` (scan → outfits with null `userId`).
- [ ] 5.4 (GREEN) Create `com.ensemble.migration.UnownedDataPurgeRunner implements ApplicationRunner`,
  `@Component`, `@Order(Ordered.LOWEST_PRECEDENCE - 40)` (after `DynamoDbTableInitializer`). Self-gate
  inside `run(...)`: return immediately if `!props.purgeUnowned()` (mirrors `SeedAccountRunner`, so
  `@SpringBootTest` contexts no-op by default). When enabled: for each unowned item,
  `photoStorage.delete(item.getPhotoKey())` then `wardrobeRepository.deleteById(...)`; delete each
  unowned outfit; log counts. Never delete `usage#` or owned rows.
- [ ] 5.5 (RED→GREEN) Add `UnownedDataPurgeRunnerIT` against DynamoDB Local: seed 1 unowned item (+its
  photo via a real/anonymous `PhotoStorage`), 1 unowned outfit, 1 owned item (`userId=userA`), and a
  `usage#<date>` row; run the purge with the flag on; assert only the unowned item/outfit and the
  unowned photo are gone, and the owned item + usage row survive. Add a `findUnowned` round-trip
  assertion to `OutfitRepositoryIT` if not covered by the IT. Also add a resilience case:
  an unowned item whose photo is **already missing** — assert the runner still deletes the row and
  continues to the next (a `PhotoNotFoundException`/no-op delete must not abort the migration).
- [ ] 5.6 (GREEN) Add `ensemble.migration.purge-unowned: ${ENSEMBLE_MIGRATION_PURGE_UNOWNED:false}` to
  `application.yml` and a commented `ENSEMBLE_MIGRATION_PURGE_UNOWNED=` block to `.env.example`
  explaining it is default-off and opt-in (operator flips it once to clear legacy data).
- [ ] 5.7 (Optional) Wire `ENSEMBLE_MIGRATION_PURGE_UNOWNED` into `terraform/deploy/apprunner.tf`
  `runtime_environment_variables` via a tfvar defaulting to `"false"`, so an operator can opt in for a
  single deploy then flip it back.
- [ ] 5.8 (Proof / regression) Run the full gate: `./gradlew test -PskipFrontend`, `./gradlew jacocoTestReport`,
  `cd frontend && npm test -- --run`, and `cd terraform/deploy && terraform fmt -check -recursive && terraform validate`.
  Capture green output (success metric #5).
- [ ] 5.9 (REFACTOR) Keep tests green; ensure the purge logs a clear "N unowned items / M unowned outfits
  removed" summary and is a visible no-op when disabled.
