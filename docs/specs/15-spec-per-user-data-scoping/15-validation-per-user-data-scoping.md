# 15-validation-per-user-data-scoping.md

Validation report for [spec 15 — per-user data scoping](./15-spec-per-user-data-scoping.md)
against [15-tasks-per-user-data-scoping.md](./15-tasks-per-user-data-scoping.md) and its
five proof artifacts in [`15-proofs/`](./15-proofs/). GitHub issue
[#15](https://github.com/abreiss/ab-ensemble/issues/15).

All evidence below was **independently reproduced** on branch
`feat/per-user-data-scoping` (Docker running, Terraform v1.14.8) — not taken from the
proof docs at face value.

## 1) Executive Summary

- **Overall: PASS** — no gate tripped (GATE A/B/C/D/E/F all satisfied).
- **Implementation Ready: Yes** — every functional requirement across all five units is
  implemented and verified with file:line evidence, the full backend + frontend suites are
  green, critical-logic branch coverage is 100%, and Terraform validates; the only follow-up
  is a housekeeping commit (see Issue V-1).
- **Key metrics:**
  - **% Requirements Verified: 100%** (21/21 functional requirements Verified; 0 Failed; 0 Unknown).
  - **% Proof Artifacts Working: 100%** — backend `tests=403 failures=0 errors=0`; frontend
    `374/374`; JaCoCo 100% branch on `StylistService`, `UnownedDataPurgeRunner`,
    `WardrobeRepository`, `OutfitRepository`; `terraform validate` → *Success*.
  - **Files Changed vs Expected:** all changed core/supporting files map to the task list's
    Relevant Files and to a spec unit. The single out-of-list change (`UserRepository.java`)
    is a javadoc-only cleanup with clear linkage. **Unit 5 is implemented but uncommitted** in
    the working tree (Issue V-1, MEDIUM traceability — non-blocking).

## 2) Coverage Matrix

### Functional Requirements

| Requirement | Status | Evidence |
| --- | --- | --- |
| **U1** `userId` attribute + sparse `userId-index` GSI on `Item` & `SavedOutfit`; primary key retained | Verified | `Item.java:26,54-56` (`@DynamoDbSecondaryPartitionKey(indexNames="userId-index")`), `:39-40` (`itemId` `@DynamoDbPartitionKey`); `SavedOutfit.java:34-35,48-49` |
| **U1** `findByUserId` GSI query replaces unscoped scan (wardrobe + outfit) | Verified | `WardrobeRepository.java:53-59` `table.index("userId-index").query(...)`; `OutfitRepository.java:57-63`; `findAll()` fully removed (commit `d98f131`) |
| **U1** `findById` primary-key lookups kept for service-layer ownership | Verified | `WardrobeRepository.getItem(...)` retained; `WardrobeService.find(...)` uses it (`:126-132`) |
| **U1** `userId` stamped on every write path | Verified | `WardrobeService.java:56` `item.setUserId(userId)`; `OutfitService.java:58-81`; test `create_stampsCallerUserId` (ArgumentCaptor) PASSED for both aggregates |
| **U1** GSI declared in `DynamoDbTableInitializer` (dev) + Terraform (deploy); users table plain | Verified | `DynamoDbTableInitializer.java:66-68` (items/outfits GSI overload; users 2-arg), `:133-142` WARN on stale table; `data_stores.tf:43-52,76-85` (items+outfits), `:94-103` (users untouched) |
| **U1** Instance role gains `table/${prefix}-*/index/*` + `dynamodb:Query` | Verified | `iam.tf:56-63`; rendered lint `abreiss-ensemble-instance-runtime.json:17,23-26` |
| **U1** Sparse GSI excludes `usage#<date>` counter rows | Verified | `WardrobeRepositoryIT.findByUserId_excludesUsageCounterRows` PASSED (DynamoDB Local) |
| **U2** `@CurrentUserId` on all 7 wardrobe + 3 outfit handlers, forwarded to services | Verified | `WardrobeController.java:52,62,67,72,80,85,91`; `OutfitController.java:51,59,65` |
| **U2** Scoped `create`/`list` (only caller's rows) | Verified | `WardrobeService.java:47,72-74`; `OutfitService.java:58,84-86`; tests `list_returnsOnlyCallersItems`/`...Outfits` PASSED |
| **U2** `find(userId,id)` choke point → non-enumerating **404** on missing **or** cross-user | Verified | `WardrobeService.java:126-132` (throws `ItemNotFoundException` on missing OR `!userId.equals(owner)`); `OutfitService.java:103-109`; tests `find_otherUsersItem/Outfit_throwsNotFound` + controller cross-user `get/updateTags/markWorn/delete` → 404 PASSED |
| **U2** No change to auth/session, DTO boundary, or `ApiExceptionHandler` shapes | Verified | No diff to session/filter/handler in the four scoping commits; `SessionAuthFilterTest` (9, full-context) still green |
| **U3** `photoKey = <userId>/<itemId>.jpg` derived in `create`, stored on `Item` | Verified | `WardrobeService.java:51`; test `create_namespacesPhotoKeyPerUser` PASSED |
| **U3** `LocalDiskPhotoStorage.save` creates parent dir; traversal guard retained | Verified | `LocalDiskPhotoStorage.java:47` `Files.createDirectories(...)`, `:68-73` `resolve()` traversal guard; tests `save_nestedKey_...` + `resolve_pathTraversalKey_isRejected` PASSED |
| **U3** No `S3PhotoStorage` / `PhotoStorage`-interface change | Verified | Task 3.6 — S3 passes `key` opaquely; no diff to either file |
| **U3** Photo endpoint ownership → 404 for cross-user | Verified | `WardrobeController` photo handler routes through `find(userId,itemId)`; test `photo_otherUsersItem_returns404` PASSED (JSON error, not bytes) |
| **U4** `style(userId, vibe, history)` builds text + `validIds` from `wardrobe.list(userId)` | Verified | `StylistService.java:105-106`; `StyleController.java:60,77-81` (`enrich(outfit,userId)` scoped) |
| **U4** Cross-user id rejected like hallucinated id: feedback → one retry → drop → empty ⇒ exception | Verified | `StylistService.java:125-129,149-169`; test `styleRequest_withOtherUsersId_retriesOnceThenRejects` (`verify(model, times(2))`) PASSED |
| **U4** No image bytes to model; stateless | Verified | Existing `sendsNoImageBytesToModel` + re-pick suite still green against scoped signature; server holds no history |
| **U5** Idempotent, opt-in `ApplicationRunner` deletes unowned `Item`/`SavedOutfit` + photos | Verified | `UnownedDataPurgeRunner.java:37-38,56-76`; `UnownedDataPurgeRunnerIT` (2, DynamoDB Local) PASSED |
| **U5** Gated by flag, default disabled; no-op when disabled/re-run | Verified | `MigrationProperties.java:17-18` (default false); `application.yml:82`; `.env.example:36`; tests `purge_whenDisabled_isNoOp`, `purge_secondRun_isNoOp` PASSED |
| **U5** Targets only unowned Ensemble rows; never `usage#` or owned; resilient to photo-delete failure | Verified | `findUnowned()` excludes `usage#` + owned (`WardrobeRepository.java:70-75`); runner try/catch around photo delete (`:63-70`); IT asserts owned + `usage#` survive; `purge_whenPhotoDeleteFails_stillDeletesRowAndContinues` PASSED |

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Strict TDD (backend domain) | Verified | Each proof doc shows a genuine RED (compile/assertion failure) before GREEN; new behavior tests exist at model/repo/service/controller/stylist/migration layers |
| Coverage (≥90% line domain; **100% branch** critical logic) | Verified | JaCoCo: `StylistService` branch 18/18 & line 53/53; `UnownedDataPurgeRunner` 6/6 & 26/26; `WardrobeRepository` 6/6 & 17/17; `OutfitRepository` 4/4 & 17/17 — all 100% |
| Layered architecture (`@CurrentUserId` at web layer only; services take plain `userId`) | Verified | Controllers resolve `@CurrentUserId`; service signatures take `String userId`, no servlet access |
| DynamoDB Enhanced Client, single-item model, GSI via annotation + initializer + Terraform | Verified | `@DynamoDbSecondaryPartitionKey`; initializer GSI overload; `data_stores.tf` GSI blocks |
| DTOs at boundary; stylist reasons over text tags only | Verified | Controllers return DTOs; stylist builds text from `ItemResponse`, no image bytes |
| Mock external boundaries; DynamoDB Local for round-trips; no live network | Verified | Claude client & S3 mocked in unit tests; TestContainers ITs; suite runs offline |
| Infra validated (`fmt`/`validate` + Access Analyzer lint), not unit-tested | Verified | `terraform fmt -check -recursive` clean; `validate` → *Success*; rendered lint JSON mirrors HCL |
| Conventional commits, ~one per demoable unit | **Partial** | Units 1–4 = 4 clean conventional commits mapped to tasks; **Unit 5 is uncommitted** (Issue V-1) |
| Frontend suite still passes (Non-Goal #7) | Verified | `vitest --run` → 32 files / **374 tests** passed; no frontend source change |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| Unit 1 | `WardrobeRepositoryIT` / `OutfitRepositoryIT` / `DynamoDbTableInitializerIT` (GSI, per-user, `usage#` exclusion) | Verified | All named cases present + PASSED in `build/test-results/test/*.xml` |
| Unit 1 | `terraform fmt -check && init && validate` | Verified | fmt clean; init OK; `Success! The configuration is valid.` (sole warning = pre-existing local provider dev-override) |
| Unit 2 | Service/controller scoped-list, cross-user 404, owner-stamp tests | Verified | `WardrobeServiceTest`, `WardrobeControllerTest`, `OutfitServiceTest`, `OutfitControllerTest`, `SessionAuthFilterTest` all PASSED |
| Unit 2 | Live two-account isolation run (disjoint lists; cross-user ops → 404) | Verified (as documented) | Proof `15-task-02-proofs.md` shows sanitized `$TOKEN_A/$TOKEN_B`, redacted UUIDs, isolated `proof15-*` tables torn down after; consistent with reproduced service/controller tests |
| Unit 3 | `LocalDiskPhotoStorageTest`, `create_namespacesPhotoKeyPerUser`, `photo_otherUsersItem_returns404` | Verified | All PASSED |
| Unit 4 | `styleRequest_withOtherUsersId_retriesOnceThenRejects` + retained grounding suite | Verified | PASSED; grounding branch coverage 100% incl. cross-user path |
| Unit 4 | Grep-guard: no unscoped `.findAll()`/`.list()` reader remains | Verified | `findAll()`/no-arg `list()` removed in `d98f131`; source scan clean |
| Unit 5 | `UnownedDataPurgeRunnerTest` (4), `UnownedDataPurgeRunnerIT` (2), `findUnowned` repo cases | Verified | All PASSED (DynamoDB Local) |
| Unit 5 | Flag-wiring diff (`application.yml`, `.env.example`, test config, Terraform tfvar) | Verified | Present with default `false`; `apprunner.tf` emits explicit `"true"/"false"` literal |
| All | Secret scan of proof docs | Verified | No API keys/tokens/passwords — only `$TOKEN_*` placeholders + redacted UUIDs (GATE F) |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| **MEDIUM** | **Unit 5 (purge runner) is implemented but uncommitted.** `git log main..HEAD` shows 4 commits (Units 1–4 only). The working tree still holds `src/main/java/com/ensemble/migration/{UnownedDataPurgeRunner,MigrationProperties}.java`, its two tests, and modified `DynamoDbConfig.java`, `Wardrobe/OutfitRepository.java` (`findUnowned`), `application.yml`, `src/test/resources/application.yml`, `.env.example`, `apprunner.tf`, `variables.tf`, plus `15-task-05-proofs.md`. | Git traceability (R4) for Unit 5 is currently absent; requirement verification itself is unaffected (all files present and tested green on the working tree). | Commit the Unit 5 changes as one conventional commit (e.g. `feat(scoping): one-time opt-in purge of pre-existing unowned data`) referencing spec-15 T5.0, before opening the PR. |
| **LOW** | `UserRepository.java` changed in the Unit-4 commit but is **not** in the task list's Relevant Files. | Potential traceability ambiguity only. | None required — verified as a **javadoc-only** edit removing a now-stale reference to the `WardrobeRepository.findAll()` deleted in the same commit; behavior and the users-table scan are unchanged, so Non-Goal #4 holds. Recorded here for completeness. |

No CRITICAL or HIGH issues. No `Unknown` coverage entries. No unmapped out-of-scope core file changes.

## 4) Evidence Appendix

**Branch / commits analyzed** (`git log main..HEAD`):

```
d98f131 feat(scoping): user-scoped stylist + cross-user grounding guardrail      (Unit 4)
1ba06b8 feat(scoping): per-user photo namespacing + cross-user photo isolation   (Unit 3)
5f46daa feat(scoping): ownership-enforced wardrobe & outfit APIs                 (Unit 2)
6b81983 feat(scoping): owner-stamped, per-user-queryable data model + GSI IaC/IAM (Unit 1)
```
Each commit body carries a `Related to T#.0 in Spec 15` trailer. Unit 5 has **no commit** (working tree only — Issue V-1).

**Backend suite** (`./gradlew test -PskipFrontend`; aggregated from `build/test-results/test/*.xml`):

```
BUILD SUCCESSFUL
TOTAL tests=403 skipped=0 failures=0 errors=0
```

**JaCoCo branch/line** (`build/reports/jacoco/test/jacocoTestReport.xml`):

```
StylistService          BRANCH missed=0 covered=18   LINE missed=0 covered=53   (100% / 100%)
UnownedDataPurgeRunner  BRANCH missed=0 covered=6    LINE missed=0 covered=26   (100% / 100%)
WardrobeRepository      BRANCH missed=0 covered=6    LINE missed=0 covered=17   (100% / 100%)
OutfitRepository        BRANCH missed=0 covered=4    LINE missed=0 covered=17   (100% / 100%)
```

**Frontend suite** (`cd frontend && npm test -- --run`):

```
Test Files  32 passed (32)
     Tests  374 passed (374)
```

**Terraform** (`cd terraform/deploy && terraform fmt -check -recursive && terraform init -backend=false && terraform validate`):

```
fmt -check: clean (no output)
init:       Terraform has been successfully initialized!
validate:   Success! The configuration is valid.
            (sole warning = pre-existing dev.local provider dev-override, unrelated to this change)
```

**Named proof tests confirmed present + PASSED** (`build/test-results/test/*.xml`):
`findByUserId_returnsOnlyThatUsersItems`, `findByUserId_excludesUsageCounterRows`,
`findByUserId_returnsOnlyThatUsersOutfits`, `list_returnsOnlyCallersItems`,
`find_otherUsersItem_throwsNotFound`, `create_stampsCallerUserId` (wardrobe + outfit),
`create_namespacesPhotoKeyPerUser`, `styleRequest_withOtherUsersId_retriesOnceThenRejects`,
`purge_whenDisabled_isNoOp`, `purge_secondRun_isNoOp`.

**Secret scan** (`grep` for `sk-ant-*`, `AKIA*`, live session tokens, literal passcodes over
`15-proofs/`): no matches — clean.

**Success-metric roll-up:**

| # | Success Metric | Result |
| --- | --- | --- |
| 1 | Two-account isolation (disjoint lists; A's item/photo/outfit → 404 for B) | ✅ controller/service tests + documented live run |
| 2 | Stylist privacy (scoped `validIds`; foreign id rejected like hallucination) | ✅ `styleRequest_withOtherUsersId_...` |
| 3 | Grounding/id-validation 100% branch (incl. cross-user); domain ≥90% line | ✅ `StylistService` 18/18 branch |
| 4 | Scoped cleanup (only unowned rows + photos; owned + `usage#` survive; no-op when off/re-run) | ✅ `UnownedDataPurgeRunnerIT` + unit tests |
| 5 | No regressions; `terraform validate` + lint pass with GSI + index IAM | ✅ 403 backend + 374 frontend green; terraform valid |

## How to Continue the SDD Workflow

Likely next phase action: this feature's SDD workflow is complete — validation **PASSED**. Before
merging, commit the Unit 5 changes (Issue V-1) and do a final human code review of the completed
implementation and this validation report. The next SDD action would be starting Phase 1 for a new
feature.

To continue the workflow in this chat, reply with:

`Start SDD for a new feature.`

You can also continue in a new chat if you want to keep context lean; the SDD skill will reassess
repository state from the persisted spec/task/audit/proof/validation artifacts.

**Validation Completed:** 2026-07-23
**Validation Performed By:** Claude Opus 4.8 (1M context)
