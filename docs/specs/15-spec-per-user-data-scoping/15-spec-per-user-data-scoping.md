# 15-spec-per-user-data-scoping.md

> GitHub issue: [#15 — Scope wardrobe data (items, photos, stylist) to the
> logged-in user](https://github.com/abreiss/ab-ensemble/issues/15). Depends on
> #14 (accounts), which produced a resolvable `userId`. Spec sequence `15`
> matches the issue number.

## Introduction/Overview

Issue #14 gave Ensemble accounts and a resolvable `userId`, but the wardrobe
itself is still global: `Item` has no owner field, `WardrobeRepository.findAll()`
does an unscoped `table.scan()`, `photoKey` is just `<itemId>.jpg`, the stylist's
`searchWardrobe` returns every item in the table, and **no wardrobe or outfit
endpoint checks ownership** — any logged-in user can already read, modify, or
delete any other user's item by guessing its `itemId`. This feature makes the app
multi-tenant **at the data layer**: every item, photo, saved outfit, and stylist
interaction is scoped to the authenticated user, closing a live cross-user
authorization gap. The primary goal is that two different accounts each see and
touch **only their own data**, proven end-to-end.

## Goals

- Add a durable `userId` owner to `Item` and `SavedOutfit` and make each user's
  data **queryable without a full-table scan**, using a sparse `userId` Global
  Secondary Index (GSI) on each table.
- Enforce ownership on **every** single-item/single-outfit operation
  (get, photo, update-tags, mark-worn, delete), returning a non-enumerating
  **404** when the caller is not the owner.
- Scope the stylist: `searchWardrobe` returns only the caller's items, and the
  grounding guardrail rejects an id that belongs to a **different user** exactly
  as it rejects a hallucinated id (privacy boundary, not just grounding).
- Namespace photo storage per user (`<userId>/<itemId>.jpg`) so photos cannot
  collide or leak across users, behind the unchanged `PhotoStorage` interface.
- Purge the pre-existing (unowned) demo data as a one-time, idempotent, opt-in
  cleanup **scoped strictly to Ensemble-owned tables/photos**, so the
  multi-tenant era starts clean.

## User Stories

- **As a signed-in user**, I want to see only the clothes I photographed so that
  my wardrobe is personal and isn't polluted by other people's items.
- **As a signed-in user**, I want the stylist to build outfits only from clothes
  I own so that its suggestions are actually wearable by me and never surface
  someone else's garment.
- **As a signed-in user**, I want my photos to be private so that another account
  cannot retrieve an image I uploaded, even if they guess an id.
- **As the operator**, I want a safe, bounded way to clear the old shared demo
  data so that legacy unowned rows don't linger, without any risk of touching
  non-Ensemble resources in the shared AWS account.

## Demoable Units of Work

> **Testing note (applies to every unit):** cross-user isolation is proven at the
> service/repository/controller layers with two users' data present
> simultaneously — using DynamoDB Local (TestContainers) for repository
> round-trips and MockMvc / mocked seams for the web and stylist layers. No two
> real logged-in browser sessions are required to prove isolation. Strict TDD
> (RED → GREEN → REFACTOR) applies to all backend domain logic below.

### Unit 1: Owner-stamped, per-user-queryable data model (items & outfits)

**Purpose:** Establish the ownership foundation everything else builds on — give
`Item` and `SavedOutfit` a `userId`, add a sparse `userId` GSI to both tables, and
replace unscoped scans with per-user GSI queries. Backend domain core, strict TDD.

**Functional Requirements:**

- The system shall add a `userId` string attribute to **`Item`** and
  **`SavedOutfit`**, exposed as a **sparse GSI partition key** (`@DynamoDbSecondaryPartitionKey`)
  on a named index (e.g. `userId-index`), while keeping `itemId` / `outfitId` as
  the primary partition key.
- The system shall replace **`WardrobeRepository.findAll()`**'s unscoped
  `table.scan()` with a **`findByUserId(String userId)`** that runs a **GSI query**
  (`index(...).query(...)`) returning only that user's items — no full-table scan.
  `OutfitRepository` shall gain the equivalent scoped query.
- The system shall keep the primary-key `findById(itemId)` / `findById(outfitId)`
  lookups, so ownership can be enforced at the service layer (Unit 2) without a
  second index.
- The system shall stamp `userId` on every write path (`save`/create) so no
  owned row is ever written without an owner.
- The system shall declare the new GSI for local dev in
  **`DynamoDbTableInitializer`** (extend `ensureTable(...)` to add the `userId`
  attribute definition + `globalSecondaryIndexes`) and in Terraform
  (**`terraform/deploy/data_stores.tf`** — a `global_secondary_index` block on the
  items and outfits tables, hash key `userId`).
- The system shall ensure the App Runner instance role can query the new indexes:
  the DynamoDB policy resource shall include the index ARN
  (`table/${prefix}-*/index/*`) and the `dynamodb:Query` action, since the current
  `table/${prefix}-*` resource does **not** match a GSI ARN.
- The sparse GSI shall naturally exclude the reserved `usage#<date>` counter rows
  (which carry no `userId`), so those rows never appear in a per-user query.

**Proof Artifacts:**

- Test: `WardrobeRepositoryIT` (DynamoDB Local / TestContainers) passes
  `findByUserId_returnsOnlyThatUsersItems` with two users' rows written to one
  table — demonstrates the GSI query is user-scoped with no scan.
- Test: `OutfitRepositoryIT` passes the equivalent `findByUserId` isolation case
  — demonstrates saved outfits are queryable per user.
- Test: `findByUserId_excludesUsageCounterRows` (or equivalent) confirms
  `usage#<date>` rows never surface in a user query — demonstrates the sparse
  index excludes non-owned system rows.
- Diff: `terraform/deploy/data_stores.tf` and the instance-role policy show the
  GSI blocks and the `index/*` Query grant; `terraform validate` passes —
  demonstrates the deployed tables gain the index and the role can query it.

### Unit 2: Ownership-enforced wardrobe & outfit APIs

**Purpose:** Thread the authenticated `userId` (`@CurrentUserId`, the pattern
already used by `MeController`) through the wardrobe and outfit controllers and
services, so listing is scoped and single-resource operations reject cross-user
access. Closes the live authorization gap.

**Functional Requirements:**

- The system shall add `@CurrentUserId String userId` to every
  **`WardrobeController`** handler (`create`, `list`, `get`, `photo`,
  `updateTags`, `markWorn`, `delete`) and every **`OutfitController`** handler
  (`save`, `list`, `delete`), forwarding it into the service layer.
- The system shall make **`WardrobeService.create`** stamp the new item's
  `userId`, and **`list`** return only `findByUserId(userId)` results. The outfit
  service shall do the same for saves and lists.
- The system shall enforce ownership at the single not-found choke point
  (**`WardrobeService.find(userId, itemId)`** / the outfit equivalent): if the row
  is missing **or** its `userId` does not equal the caller's, it shall throw
  `ItemNotFoundException` (mapped to **`404`**) — an existence-non-leaking
  response, consistent with #14's non-enumerating auth posture. No cross-user id
  shall ever return `403` or the item's contents.
- The system shall not change the auth/session mechanism, the DTO boundary, or
  the shared `ApiExceptionHandler` error shapes.

**Proof Artifacts:**

- Test: `WardrobeServiceTest.list_returnsOnlyCallersItems` and
  `find_otherUsersItem_throwsNotFound` (two users' data) — demonstrates scoped
  listing and cross-user 404 at the service layer.
- Test: `WardrobeControllerTest` cases for `get`/`updateTags`/`markWorn`/`delete`
  of another user's id return **404** via MockMvc — demonstrates the API rejects
  cross-user access on every single-item op.
- Test: `OutfitControllerTest`/`OutfitServiceTest` equivalent isolation cases —
  demonstrates saved outfits are scoped and ownership-enforced.
- CLI: with two session tokens A and B, `GET /api/items` returns disjoint sets,
  and `DELETE /api/items/<A's id>` as B → `404` — demonstrates end-to-end
  two-account isolation. (Acceptance criterion: "Two different accounts each see
  only their own items.")

### Unit 3: Per-user photo namespacing + cross-user photo isolation

**Purpose:** Make stored photos private by namespacing the storage key per user
and enforcing ownership on the photo endpoint, behind the unchanged `PhotoStorage`
interface. Backend domain core, strict TDD.

**Functional Requirements:**

- The system shall derive `photoKey` as **`<userId>/<itemId>.jpg`** in
  `WardrobeService.create` (replacing `itemId + ".jpg"`), storing it on the `Item`
  so load/delete continue to use the stored key.
- The system shall make **`LocalDiskPhotoStorage.save`** create the key's parent
  directory before writing (nested keys currently fail because only the base dir
  is created). The existing path-traversal guard in `resolve(...)` shall continue
  to reject escaping keys; `userId` is a server-generated UUID and cannot contain
  `../`.
- The system shall require **no code change** to `S3PhotoStorage` (the namespaced
  key is a valid S3 prefix) and **no signature change** to the `PhotoStorage`
  interface (`key` stays an opaque `String`).
- The system shall enforce ownership on **`GET /api/items/{id}/photo`** through
  the same `find(userId, itemId)` choke point, so a photo uploaded by user A is
  **404** (not the bytes) for user B — the namespacing is defense-in-depth on top
  of this check.

**Proof Artifacts:**

- Test: `WardrobeServiceTest.create_namespacesPhotoKeyPerUser` asserts the stored
  key is `<userId>/<itemId>.jpg` — demonstrates per-user namespacing.
- Test: `LocalDiskPhotoStorageTest.save_nestedKey_createsParentDirsAndStores` and
  the retained `resolve_pathTraversalKey_isRejected` — demonstrates nested keys
  write correctly and traversal is still blocked.
- Test: `photo_otherUsersItem_returns404` (MockMvc) — demonstrates a cross-user
  photo request returns 404, not image bytes. (Acceptance criterion: "A photo
  uploaded as user A is never retrievable via user B's session.")

### Unit 4: User-scoped stylist + cross-user grounding guardrail

**Purpose:** Scope the stylist tool-loop to the caller's wardrobe and extend the
grounding guardrail so an id belonging to another user is rejected like a
hallucinated id — a privacy boundary. Backend domain core, strict TDD; 100% branch
coverage on grounding/id-validation must be maintained.

**Functional Requirements:**

- The system shall pass the caller's `userId` from **`StyleController`**
  (`@CurrentUserId`) into **`StylistService.style(userId, vibe, history)`**, which
  shall build its wardrobe text and `validIds` from **`wardrobe.list(userId)`**
  only. The controller's `enrich(...)` join shall likewise use the scoped list.
- The system shall keep the existing grounding mechanism unchanged in shape: an id
  outside the user-scoped `validIds` — whether hallucinated **or owned by another
  user** — shall be rejected, fed back once, retried exactly once, and dropped
  from the rendered outfit if still invalid (drop-to-empty → `StylistUnavailableException`).
- The system shall never send image bytes to the model (existing guarantee
  preserved), and shall keep the stylist stateless (client resends history).

**Proof Artifacts:**

- Test: `StylistServiceTest.styleRequest_withOtherUsersId_retriesOnceThenRejects`
  — with two users' items present and the model returning user B's id for user A,
  the id is treated exactly like a hallucinated id — demonstrates the cross-user
  privacy rejection. (Acceptance criterion: "`searchWardrobe` returns only the
  calling user's items … covered by a test with two users' data present.")
- Test: existing grounding suite (`styleRequest_withHallucinatedId_*`,
  `allIdsInvalidAfterRetry_returnsError`, `sendsNoImageBytesToModel`) still passes
  against the scoped signature — demonstrates no regression in the guardrail.
- Coverage report: JaCoCo shows grounding/id-validation at **100% branch**,
  now including the "id belongs to a different user" rejection branch —
  demonstrates the critical-logic coverage bar is met. (Acceptance criterion:
  "100% branch coverage maintained on grounding/id-validation, now covering 'id
  belongs to a different user'.")

### Unit 5: One-time cleanup of pre-existing unowned data

**Purpose:** Remove the legacy shared demo data so nothing unowned lingers, using
an idempotent, opt-in startup step that is provably incapable of touching anything
outside Ensemble's own tables/photos in the shared AWS account.

**Functional Requirements:**

- The system shall provide an idempotent startup **`ApplicationRunner`** (mirroring
  `SeedAccountRunner`'s pattern and ordering, running after
  `DynamoDbTableInitializer`) that deletes every `Item` and `SavedOutfit` row with
  **no `userId`** and deletes each such item's stored photo.
- The cleanup shall be **gated by an explicit config flag** (e.g.
  `ensemble.migration.purge-unowned`, sourced from an env var), defaulting to
  **disabled**, so it never runs unless the operator opts in — and shall be a
  **no-op** on subsequent boots once no unowned rows remain (idempotent).
- The cleanup shall target **only unowned Ensemble rows**: it shall never delete
  the reserved `usage#<date>` counter rows, never delete owned rows, and operate
  solely against the Ensemble items/outfits tables and photos backend. It relies
  on the `abreiss-ensemble-*`-scoped instance role (#16) so it is structurally
  incapable of touching any non-Ensemble resource in the shared account.

**Proof Artifacts:**

- Test: `UnownedDataPurgeRunnerTest` (or IT against DynamoDB Local) —
  seeds unowned items/outfits + owned items + a `usage#<date>` row, runs the
  purge, and asserts **only** the unowned rows and their photos are gone; owned
  rows and the usage counter survive — demonstrates the deletion is correctly
  scoped and never over-reaches. (Acceptance criterion: pre-migration items handled
  per the product decision, verified by a migration test.)
- Test: `purge_whenDisabled_isNoOp` and `purge_secondRun_isNoOp` — demonstrates the
  opt-in gate and idempotency.
- Diff: `application.yml` / `.env.example` show the new `purge-unowned` flag
  (default off) with a comment — demonstrates the opt-in contract is documented.

## Non-Goals (Out of Scope)

1. **Per-user daily call cap** — the daily cap **stays a single global**
   `usage#<date>` counter (Resolved Decision Q3); `CallCapService`/`UsageRepository`
   are unchanged. Whether it becomes per-user is deferred.
2. **Reassigning legacy data to a seed account** — the pre-existing rows are
   **deleted**, not migrated to a seed user (Resolved Decision Q2). No seed-owned
   wardrobe data is created.
3. **Composite primary key / table recreation** — the isolation mechanism is a
   sparse `userId` GSI on the existing tables (Resolved Decision Q1); the deployed
   tables are **not** destroyed/recreated and the primary key stays `itemId`/`outfitId`.
4. **A `userId` GSI for the `users` table** — `UserRepository.findByUserId`'s scan
   is untouched; #15 reads the caller's `userId` straight from the session and puts
   no new leg on that lookup. (Remains the documented future scale path.)
5. **Cross-user sharing, social features, or an admin/multi-tenant management UI** —
   isolation only; there is no way to share or view another user's data by design.
6. **Changing the #14 auth/session mechanism** — the token, filter,
   `@CurrentUserId` resolver, and exempt paths are reused as-is.
7. **Frontend redesign** — the client already sends the session token; the same
   endpoints simply return scoped data. No new screens.

## Design Considerations

No new UI is introduced. The existing wardrobe grid, photo `<img>` tags
(`?token=` authenticated), and stylist screens continue to work unchanged; they
now render per-user data because the backend scopes it. No design mockups
required.

## Repository Standards

- **Strict TDD** for all backend domain logic (model, repositories, services,
  photo storage, stylist guardrail, purge runner): RED → GREEN → REFACTOR, ≥90%
  line coverage and **100% branch** on grounding/id-validation (now including the
  cross-user rejection branch) per `AGENTS.md`/`TESTING.md`.
- **Layered architecture** preserved: controllers → services → repositories/storage.
  `@CurrentUserId` is resolved at the web layer only; services receive a plain
  `userId` string and never touch servlet internals.
- **DynamoDB via AWS SDK v2 Enhanced Client**, single-item model; the GSI is added
  via bean annotations + `DynamoDbTableInitializer` (dev) and Terraform (deploy).
- **DTOs at the API boundary**; never leak `Item`/`SavedOutfit`/DynamoDB items or
  the Claude client into controllers. The stylist still reasons over **text tags
  only** — no image bytes.
- **Mock external boundaries** (Claude client, S3) in unit tests; use DynamoDB
  Local (TestContainers) for repository round-trips. No live network calls.
- **Infra/Terraform** validated with `fmt`/`validate` + the Access Analyzer policy
  lint (per the existing CI), not unit-tested.
- Conventional commits, roughly one per demoable unit. Pre-commit hooks (tests,
  format/lint, secret scan) must pass.

## Technical Considerations

- **`userId` source (from #14):** `SessionAuthFilter` verifies the token and stores
  the `userId` as the `ensemble.userId` request attribute; controllers read it via
  `@CurrentUserId String userId` (`CurrentUserIdArgumentResolver`). `MeController`
  is the existing exemplar. #15 threads this same value into `WardrobeController`,
  `StyleController`, and `OutfitController`.
- **GSI query (Enhanced Client):** list-per-user uses
  `table.index("userId-index").query(...)`, replacing the `table.scan()` in
  `WardrobeRepository.findAll()` / `OutfitRepository.findAll()`. `findById` stays a
  primary-key `getItem`, with ownership enforced in the service's `find(...)` choke
  point.
- **IAM for GSI (important):** the instance role's DynamoDB policy currently scopes
  to `arn:.../table/${prefix}-*`, which does **not** authorize a GSI query
  (`.../table/${prefix}-*/index/...`). The policy resource must add
  `table/${prefix}-*/index/*` and the `dynamodb:Query` action. This is a required
  Terraform change verified by the Access Analyzer lint.
- **Dev table GSI (DynamoDB Local):** `DynamoDbTableInitializer.ensureTable(...)` is
  idempotent (skips existing tables), so a dev whose local `ensemble-items` table
  predates the GSI must drop it to have it recreated with the index (or the
  initializer can issue an `UpdateTable` to add the GSI when missing). Document the
  drop-and-recreate step for local dev; the deployed table gets the GSI via
  Terraform (an in-place `UpdateTable`, no recreation).
- **Usage rows:** `usage#<date>` counter rows share the items table but carry no
  `userId`, so they are absent from the sparse GSI and from all per-user queries;
  the global daily cap is unaffected.
- **Photo storage:** only the derived key value changes (`<userId>/<itemId>.jpg`).
  `LocalDiskPhotoStorage.save` must `createDirectories(parent)` before writing;
  `S3PhotoStorage` needs no change (prefix is native); the `PhotoStorage` interface
  is unchanged.
- **Stylist:** only the *input* to the guardrail changes (a user-scoped `validIds`);
  the reject → one-retry → drop mechanism is reused verbatim, so a cross-user id is
  handled by the same code path as a hallucinated id.

## Security Considerations

- **This closes a live authorization gap:** today any logged-in user can read,
  modify, or delete any item/outfit by guessing its id, and can fetch any photo.
  Ownership enforcement (404 on cross-user) and per-user queries close it.
- **Cross-user id in the stylist is a privacy boundary**, strictly worse than a
  hallucinated id: it must never render another user's garment. The guardrail's
  scoped `validIds` guarantees a foreign id is dropped exactly like a ghost id.
- **Session-secret / invite-code coupling (inherited from #14):** when
  `ENSEMBLE_SESSION_SECRET` is blank, the token-signing HMAC key is derived from the
  shared `ENSEMBLE_PASSCODE` (a shared invite code), so any invited user could forge
  a token for an arbitrary `userId` — a **horizontal privilege-escalation** that
  becomes exploitable precisely once data is scoped by `userId`. #14 kept the
  fallback (does not fail closed) with a loud startup warning. #15 shall document
  that a distinct `ENSEMBLE_SESSION_SECRET` must be set for the per-user isolation
  to be trustworthy, and the warning shall remain.
- **Deletion blast radius (Unit 5):** the purge is opt-in, idempotent, targets only
  unowned Ensemble rows (never `usage#` rows, never owned rows), and runs under the
  `abreiss-ensemble-*`-scoped instance role (#16) — structurally incapable of
  touching any non-Ensemble resource in the shared AWS account.
- **Non-enumerating errors:** cross-user access returns 404, not 403, so existence
  is not leaked.
- **No secrets in proof artifacts:** curl/test evidence must not include a real
  session secret, passcode, or Claude key.

## Success Metrics

1. **Two-account isolation:** with accounts A and B present, `GET /api/items` and
   the outfit list return disjoint sets, and A's photo/item/outfit is 404 for B —
   demonstrated by the Unit 2/3 controller tests and the two-token CLI check.
2. **Stylist privacy:** `searchWardrobe`/`validIds` are user-scoped; a foreign id is
   rejected like a hallucination — demonstrated by
   `styleRequest_withOtherUsersId_retriesOnceThenRejects`.
3. **Critical-logic coverage:** grounding/id-validation at **100% branch** including
   the cross-user branch (JaCoCo); overall new backend domain code ≥90% line.
4. **Scoped cleanup:** the purge removes only unowned rows + their photos, leaving
   owned rows and usage counters intact, and is a no-op when disabled/re-run —
   demonstrated by `UnownedDataPurgeRunnerTest`.
5. **No regressions:** all pre-existing backend and frontend tests pass with
   scoping in place; `terraform validate` + Access Analyzer lint pass with the GSI
   and index IAM grant.

## Resolved Decisions (locked from questions round 1)

1. **Item/outfit key design (Q1 → A):** a sparse `userId` **GSI** on the existing
   `itemId`/`outfitId`-keyed tables — no composite primary key, no table
   recreation. Single-item ops enforce ownership and return **404** on cross-user.
2. **Legacy data (Q2 → C):** **delete** the pre-existing unowned items/outfits (and
   their photos) rather than reassign them to a seed account, scoped so nothing
   outside Ensemble's tables/photos is touched; the `abreiss-ensemble-*` IAM
   boundary enforces this.
3. **Daily call cap (Q3 → B):** **keep the single global** `usage#<date>` counter;
   no per-user cap this round.
4. **Saved outfits (Q4 → A):** **in scope** — `SavedOutfit`/`OutfitRepository`/
   `OutfitController` get the same GSI + ownership + cleanup treatment as items, so
   the app is multi-tenant across all user data.

## Open Questions

_All items below are non-blocking: they do not change the implementation path, the
demoable units, the proof artifacts, or the acceptance criteria._

1. **Local-dev GSI recreation vs. `UpdateTable`:** the spec allows either
   documenting a "drop your local table" step or teaching `DynamoDbTableInitializer`
   to add a missing GSI via `UpdateTable`. Assumption unless the user says
   otherwise: document the drop-and-recreate step (lighter, matches the IaC testing
   split); the deployed table gets the GSI via Terraform regardless.
2. **GSI projection type:** assumption is `ALL` (simplest, fine at demo scale so a
   per-user list needs no follow-up primary-key fetch). Could be narrowed to
   `KEYS_ONLY` later if projection cost ever matters. Non-blocking.
3. **Purge flag default in dev vs. deploy:** assumption is default-off everywhere
   and opt-in via env var; an operator enables it once to clear legacy data.
   Non-blocking.
