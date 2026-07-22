# 26-tasks-ui-improvements.md

Task plan for `26-spec-ui-improvements.md` — Saved Outfits (Part A) + consistent
responsive layout (Part B). Sub-tasks generated; the mandatory planning audit
(`26-audit-ui-improvements.md`) runs next.

## Standards Evidence (Blocking Checkpoint)

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD (Red-Green-Refactor) for backend domain; ≥90% line + 100% branch on grounding/id-validation; layered slice (controller→service→repository); DTOs at boundary, no `@DynamoDbBean` leak; DynamoDB Enhanced Client single-item model, no Spring Data. | none |
| `README.md` | yes | `/api/**` gated by session filter; item CRUD contract (`201`/`204`/`404`); local table auto-created on dev startup; deploy is operator-run Terraform + push CI; pre-commit runs fast tests + secret scan. | none |
| `docs/TESTING.md` | yes | Coverage split — strict TDD on backend domain, 100% branch on grounding/id-validation + forced-output; controllers test request/response contracts + error paths; frontend Vitest+RTL on meaningful logic only, not view plumbing; IaC validated by plan, not unit-tested; test names `action_condition_expectedResult`. | none |
| `docs/ARCHITECTURE.md` | yes | Single Spring process serves `/api` + built PWA; grounding guardrail = validate every id, reject hallucinated; instance role scoped to `table/${prefix}-*`; secrets by ARN, no plaintext in state. | Spec Q1-A adds a *dedicated* outfits table, a documented deviation from ARCHITECTURE's "single-table" note — resolved in spec §Technical Considerations and questions Q1 (accepted trade-off). |
| `src/main/java/com/ensemble/wardrobe/` (`Item`, `WardrobeRepository`, `WardrobeService`, `ItemNotFoundException`, `dto/`, `web/`) | yes | Slice shape to mirror one-to-one: `@DynamoDbBean` POJO (no-arg ctor, `@DynamoDbPartitionKey`); `@Repository` binding `DynamoDbTable<T>` via `TableSchema.fromBean`; `@Service` with single `find()` not-found choke point + server-owned id/`createdAt`; DTO records + final `*Mapper` (private ctor, static methods); `@RestControllerAdvice(assignableTypes={...})` shared error shape (`404`/`400`). | none |
| `frontend/src/api/items.ts`, `http.ts`, `style.ts`, `App.tsx`, `routes/WardrobeGrid.tsx` | yes | Typed client via `authedFetch`/`ensureOk`/`ApiError`, `BASE` const, resolve-on-2xx/throw-otherwise; `photoUrl(id)` token-append builder; `Outfit` type already exists in `api/style.ts` (must not collide → `SavedOutfit`); nav links + routes under `AuthGate`; `state-block`/`empty-state`/`state-note` + `settle()` loading/error pattern. | none |
| `src/main/java/com/ensemble/config/` (`DynamoDbProperties`, `DynamoDbTableInitializer`), `terraform/deploy/data_stores.tf`, `apprunner.tf` | yes | `DynamoDbProperties` record bound to `ensemble.dynamodb.*`; initializer `ensureTable()` idempotent, gated by `auto-create-table`; `aws_dynamodb_table = ${local.prefix}-<name>`, `PAY_PER_REQUEST`, hash_key + matching `attribute`; env via `runtime_environment_variables` `ENSEMBLE_*`; instance-role grant already `table/${prefix}-*`. | none |

**Confidence:** high. ≥2 guideline sources read (AGENTS.md + README.md + TESTING.md + ARCHITECTURE.md); root `AGENTS.md` and `README.md` both reviewed.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/outfit/SavedOutfit.java` | New `@DynamoDbBean` entity (`outfitId` PK, `itemIds`, `source`, `reason`, `createdAt`). |
| `src/main/java/com/ensemble/outfit/OutfitRepository.java` | New `@Repository` binding `DynamoDbTable<SavedOutfit>` to the dedicated outfits table. |
| `src/main/java/com/ensemble/outfit/OutfitService.java` | New `@Service`: create/list/delete, server-owned id/`createdAt`, `find()` choke point, **save-time grounding guard**. |
| `src/main/java/com/ensemble/outfit/OutfitNotFoundException.java` | New — thrown on delete/lookup of an unknown `outfitId` (→ 404). |
| `src/main/java/com/ensemble/outfit/InvalidOutfitException.java` | New — thrown by the grounding guard on unknown id / empty `itemIds` / bad `source` (→ 400). |
| `src/main/java/com/ensemble/outfit/dto/SaveOutfitRequest.java` | New request record (`itemIds`, `source`, `reason`) with bean-validation. |
| `src/main/java/com/ensemble/outfit/dto/OutfitResponse.java` | New response record (`outfitId`, `itemIds`, `source`, `reason`, `createdAt`). |
| `src/main/java/com/ensemble/outfit/dto/OutfitMapper.java` | New final mapper (private ctor, static `toResponse`); keeps the bean off the boundary. |
| `src/main/java/com/ensemble/outfit/web/OutfitController.java` | New `@RestController @RequestMapping("/api/outfits")` — `POST`/`GET`/`DELETE`. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Register `OutfitController.class` in `assignableTypes`; map `InvalidOutfitException`→400, `OutfitNotFoundException`→404. |
| `src/main/java/com/ensemble/config/DynamoDbProperties.java` | Add `outfitsTableName` component. |
| `src/main/java/com/ensemble/config/DynamoDbTableInitializer.java` | Ensure **both** tables (items + outfits) on dev startup. |
| `src/main/resources/application.yml` | Add `ensemble.dynamodb.outfits-table-name: ${ENSEMBLE_OUTFITS_TABLE_NAME:ensemble-outfits}`. |
| `src/main/resources/application-cloud.yml` | Add outfits-table-name (auto-create stays off in cloud). |
| `src/test/java/com/ensemble/outfit/OutfitRepositoryIT.java` | New TestContainers IT — real create→list→delete round-trip. |
| `src/test/java/com/ensemble/outfit/OutfitServiceTest.java` | New — grounding guard **100% branch** + choke point. |
| `src/test/java/com/ensemble/outfit/web/OutfitControllerTest.java` | New `@WebMvcTest` — API contract + error paths. |
| `src/test/java/com/ensemble/config/DynamoDbTableInitializerTest.java` (or existing) | Assert both tables are ensured. |
| `terraform/deploy/data_stores.tf` | Add `aws_dynamodb_table "outfits"`. |
| `terraform/deploy/apprunner.tf` | Add `ENSEMBLE_OUTFITS_TABLE_NAME` runtime env var. |
| `terraform/deploy/iam.tf` | Update instance-role policy **description** to mention outfits (no statement change). |
| `frontend/src/types/outfit.ts` | New `SavedOutfit` type (avoids colliding with `Outfit` in `api/style.ts`). |
| `frontend/src/api/outfits.ts` | New typed client (`saveOutfit`/`listOutfits`/`deleteOutfit`) mirroring `api/items.ts`. |
| `frontend/src/api/outfits.test.ts` | New — client resolves on 2xx, throws `ApiError` on non-2xx. |
| `frontend/src/components/OutfitResult.tsx` | Replace cosmetic `saved` toggle with a real save lifecycle. |
| `frontend/src/components/OutfitResult.test.tsx` | Cover the real save state machine. |
| `frontend/src/routes/Stylist.tsx` | Lift AI-save handler/state alongside `onWearToday`/`logStatus` (Q1 assumption). |
| `frontend/src/routes/Stylist.test.tsx` | Cover the lifted AI-save wiring. |
| `frontend/src/routes/Assemble.tsx` | Add "Save outfit" button in `.assemble-actions` (`source: "manual"`, empty guard). |
| `frontend/src/routes/Assemble.test.tsx` | Cover the manual save + empty guard. |
| `frontend/src/routes/SavedOutfits.tsx` | New `/saved` page: grid, states, remove, deleted-piece skip+note. |
| `frontend/src/routes/SavedOutfits.test.tsx` | New — states, remove, deleted-piece (Q3-C). |
| `frontend/src/App.tsx` | Add `/saved` route + `Saved` nav link left of `Build`. |
| `frontend/src/App.test.tsx` (or routing test) | Assert `/saved` mounts inside `AuthGate` + nav order. |
| `frontend/src/index.css` | Generalize `#root` desktop-width default; center naturally-narrow screens. |

### Notes

- Backend tests live under `src/test/java/com/ensemble/outfit/…` mirroring the
  production package; `*IT` = TestContainers integration, `*Test` = unit/slice.
- Frontend tests are colocated (`Foo.tsx` ↔ `Foo.test.tsx`); run with
  `cd frontend && npm test -- --run` (Vitest + RTL).
- Test method names follow `action_condition_expectedResult` (backend) per
  `docs/TESTING.md`.
- The grounding guard's three reject branches (unknown id, empty `itemIds`, bad
  `source`) live **in `OutfitService`** so all branches are unit-testable to 100%;
  DTO bean-validation is defense-in-depth at the controller boundary, not the
  authoritative check.
- Part B (Task 5.0) is verified by manual desktop/phone screenshots, not unit
  tests (spec assumption + `docs/TESTING.md`: view/CSS plumbing is not over-tested).

## Tasks

### [x] 1.0 `SavedOutfit` backend slice + gated `/api/outfits` API with save-time grounding guard (strict TDD)

Stand up the full server-side backbone for saved outfits, mirroring the
`com.ensemble.wardrobe` slice one-to-one: a new `com.ensemble.outfit` package with
the `@DynamoDbBean` `SavedOutfit` entity, `OutfitRepository` (dedicated table, no
reserved-prefix filtering), `OutfitService` (server-owned `outfitId`/`createdAt`,
single not-found choke point, and the **save-time grounding guard**), DTO records +
`OutfitMapper`, and the gated `OutfitController` registered in
`ApiExceptionHandler`. Adds the `ensemble.dynamodb.outfits-table-name` config
property and extends the local table initializer to auto-create the outfits table
on dev startup (cloud auto-create stays off). Built strictly TDD — the grounding
guard (all-ids-valid → save; any unknown id → whole-save `400`; empty `itemIds` →
`400`; bad `source` → `400`; delete-unknown → `404`) is 100%-branch critical logic.

#### 1.0 Proof Artifact(s)

- Test: `OutfitRepositoryIT` (DynamoDB Local via TestContainers) — create → list → delete round-trip on a real dedicated table — demonstrates persistence (maps FR "OutfitRepository"). Run: `./gradlew test --tests 'com.ensemble.outfit.OutfitRepositoryIT'`.
- Test: `OutfitServiceTest` — grounding guard with **100% branch coverage**: all ids valid → saved; any unknown id → `400`; empty `itemIds` → `400`; `source` ∉ {ai,manual} → `400`; delete-unknown → `404` — demonstrates the critical logic (maps FR "save-time grounding guard"). JaCoCo branch report for `OutfitService` shows 100%.
- Test: `OutfitControllerTest` (`@WebMvcTest` + MockMvc, `@MockitoBean` service) — `POST`→`201` with saved body + `Location`; `GET`→list; `DELETE`→`204`; unknown id on save→`400`; delete-unknown→`404` — demonstrates the API contract + error paths (maps FR "OutfitController" + exception-handler registration).
- CLI: `curl -sS -X POST localhost:8080/api/outfits -H 'X-Ensemble-Session: <token>' -H 'Content-Type: application/json' -d '{"itemIds":["<real-id>"],"source":"manual"}'` returns `201`; the same with a bogus id returns `400`; `GET /api/outfits` lists the saved outfit — demonstrates the end-to-end gated flow. (Log sanitized: `<token>` placeholder, no real session token committed.)

#### 1.0 Tasks

- [x] 1.1 Create `com.ensemble.outfit.SavedOutfit` — a `@DynamoDbBean` POJO (no-arg ctor, getters/setters) with `String outfitId` (`@DynamoDbPartitionKey` on `getOutfitId()`), `List<String> itemIds`, `String source`, `String reason`, `Instant createdAt`, mirroring `Item.java`.
- [x] 1.2 **RED:** write `OutfitRepositoryIT` (TestContainers DynamoDB Local, mirroring the items IT setup) asserting `save` → `findById` → `findAll` → `deleteById` round-trips a `SavedOutfit`. **GREEN:** implement `OutfitRepository` (`@Repository`) binding `DynamoDbTable<SavedOutfit>` via `TableSchema.fromBean(SavedOutfit.class)` to `props.outfitsTableName()`; `findAll` is a plain `scan()` with **no** reserved-prefix filter (dedicated table).
- [x] 1.3 Add `OutfitNotFoundException` (mirrors `ItemNotFoundException`, message `outfit not found: <id>`) and `InvalidOutfitException extends RuntimeException` (carries a user-safe message for the guard rejections).
- [x] 1.4 **RED:** write `OutfitServiceTest` (Mockito `WardrobeService` + `OutfitRepository`) covering the guard to **100% branch**: all ids in `WardrobeService.list()` → `create` persists (assert server-set `outfitId` non-null UUID + `createdAt` non-null, `itemIds`/`source`/`reason` preserved); any unknown id → `InvalidOutfitException`; empty `itemIds` → `InvalidOutfitException`; `source` ∉ {`ai`,`manual`} → `InvalidOutfitException`; `list` maps to DTOs; `delete` unknown id → `OutfitNotFoundException`. **GREEN:** implement `OutfitService` (build the valid-id set once from `WardrobeService.list()`, membership-check every submitted id, generate `outfitId` via `UUID.randomUUID()`, set `createdAt = Instant.now()`, route id lookups through a single private `find()` choke point).
- [x] 1.5 Add DTO records in `com.ensemble.outfit.dto`: `SaveOutfitRequest` (`@NotEmpty List<String> itemIds`; `@NotBlank @Pattern(regexp="ai|manual") String source`; nullable `String reason`) and `OutfitResponse` (`outfitId`, `itemIds`, `source`, `reason`, `createdAt`); add `OutfitMapper` (final, private ctor, static `toResponse(SavedOutfit)` and a `toEntity`/apply helper). The `SavedOutfit` bean never crosses the controller boundary.
- [x] 1.6 **RED:** write `OutfitControllerTest` (`@WebMvcTest(OutfitController.class)` + MockMvc, `@MockitoBean OutfitService`): `POST`→`201` + `Location: /api/outfits/<id>` + body; `GET`→list JSON; `DELETE`→`204`; a save whose service throws `InvalidOutfitException`→`400` `bad_request`; a delete throwing `OutfitNotFoundException`→`404` `not_found`. **GREEN:** implement `OutfitController` (`@RestController @RequestMapping("/api/outfits")`, `POST` consumes JSON with `@Valid SaveOutfitRequest`, `GET`, `DELETE` `@ResponseStatus(NO_CONTENT)`), mirroring `WardrobeController`.
- [x] 1.7 Register `OutfitController.class` in `ApiExceptionHandler`'s `@RestControllerAdvice(assignableTypes = {...})`; add `@ExceptionHandler(InvalidOutfitException.class)`→`400 bad_request` and `@ExceptionHandler(OutfitNotFoundException.class)`→`404 not_found` (or fold into the existing not-found handler). Extend the handler test coverage for both mappings.
- [x] 1.8 Add `outfitsTableName` to the `DynamoDbProperties` record; add `ensemble.dynamodb.outfits-table-name: ${ENSEMBLE_OUTFITS_TABLE_NAME:ensemble-outfits}` to `application.yml`. Refactor `DynamoDbTableInitializer.ensureTable()` to accept `(tableName, partitionKey)` and call it for **both** the items table (`itemId`) and the outfits table (`outfitId`) on startup. (Note: `outfits-table-name` lives in base `application.yml` only, inherited by the cloud profile — matching how `table-name` is handled today; `application-cloud.yml` overrides only `endpoint` + `auto-create-table`, so no outfits key is duplicated there. Spec-compliant and repo-consistent.)
- [x] 1.9 **RED/verify:** extend the initializer test to assert both tables are ensured (idempotent). Run `./gradlew test` (all green) and generate the JaCoCo report; confirm ≥90% line on the new `com.ensemble.outfit` package and **100% branch** on `OutfitService`. (312 tests green; `com.ensemble.outfit.*` 100% line; `OutfitService` 100% branch.)

### [x] 2.0 Cloud provisioning for the outfits table (Terraform + App Runner env var, no IAM diff)

Add the minimal, pre-authorized cloud delta so a deploy after merge writes to a
real table instead of a 5xx landmine: an `aws_dynamodb_table "outfits"`
(`${local.prefix}-outfits`, hash_key `outfitId`, `PAY_PER_REQUEST`) in
`data_stores.tf`, and the `ENSEMBLE_OUTFITS_TABLE_NAME` runtime env var wired to
App Runner in `apprunner.tf`. Update the instance-role policy *description* to
mention the outfits table; make **no** IAM policy change (the grant is already
`table/${local.prefix}-*`). Validated by `fmt`/`validate` + `plan` per
`docs/TESTING.md` (IaC is not unit-tested).

#### 2.0 Proof Artifact(s)

- CLI: `terraform -chdir=terraform/deploy fmt -check` and `terraform -chdir=terraform/deploy validate` both pass — demonstrates the IaC is well-formed (maps FR "Terraform outfits table + env var").
- CLI: `terraform -chdir=terraform/deploy plan` output shows the only outfit-related additions are `aws_dynamodb_table.outfits` + the `ENSEMBLE_OUTFITS_TABLE_NAME` env var, and **no `aws_iam_*` resource is added or changed** — demonstrates the cloud delta matches success-metric #6 (no IAM diff). (Plan excerpt saved with account-specific ARNs redacted.)

#### 2.0 Tasks

- [x] 2.1 Add `resource "aws_dynamodb_table" "outfits"` to `terraform/deploy/data_stores.tf`: `name = "${local.prefix}-outfits"`, `billing_mode = "PAY_PER_REQUEST"`, `hash_key = "outfitId"`, and the matching `attribute { name = "outfitId"; type = "S" }` — mirroring the `items` table resource.
- [x] 2.2 Add `ENSEMBLE_OUTFITS_TABLE_NAME = aws_dynamodb_table.outfits.name` to `runtime_environment_variables` in `terraform/deploy/apprunner.tf`, alongside `ENSEMBLE_DYNAMODB_TABLE_NAME`.
- [x] 2.3 Update the instance-role DynamoDB policy **comment** in `terraform/deploy/iam.tf` to mention the outfits table; confirmed the resource ARN pattern is already `table/${local.prefix}-*` so **no statement/resource change** is needed. **Deviation:** the mention was carried in an HCL *comment*, not the live `description` string — an `aws_iam_policy` `description` is immutable in AWS, so editing it forces a destroy+recreate of the attached policy (plan proved `1 add/1 change` → `3 add/2 destroy` when the string changed). Keeping the string unchanged preserves proof 2.0 / success-metric-#6's "no IAM diff" guarantee.
- [x] 2.4 Ran `terraform -chdir=terraform/deploy fmt -check` and `validate` (both pass); captured a `plan` excerpt (account id / SHA redacted) proving the only delta is `aws_dynamodb_table.outfits` (add) + the `ENSEMBLE_OUTFITS_TABLE_NAME` env var (in-place change) — `Plan: 1 to add, 1 to change, 0 to destroy` — and that **no `aws_iam_*` resource is added/changed/destroyed**. (The plan also shows a pre-existing, unrelated `image_identifier` drift `:sha-<git-sha>` → `:latest`, which is the accepted CI-vs-config drift noted in `apprunner.tf`, not part of this task and never applied via `terraform apply`.)

### [x] 3.0 Real save wiring on Stylist (`/`) and Build (`/assemble`)

Make both "save" affordances real. Add a typed `frontend/src/api/outfits.ts` client
mirroring `api/items.ts` (`saveOutfit`/`listOutfits`/`deleteOutfit`, typed
`ApiError`). Replace the cosmetic `saved` toggle in `OutfitResult.tsx` with a real
save that `POST`s `outfit.itemIds` + `outfit.reason` as `source: "ai"` and drives an
`idle → saving → saved → error` state machine (disabled while saving/busy). Add a
**"Save outfit"** button to `.assemble-actions` in `Assemble.tsx` that saves the
placed ids as `source: "manual"`, mirrors the existing "Wear today" lifecycle, and
is **disabled when nothing is placed**. Surface failures via the existing retryable
error affordance; persist nothing client-side.

#### 3.0 Proof Artifact(s)

- Test: `OutfitResult.test.tsx` / `Stylist.test.tsx` — the save control calls `saveOutfit` once with the rendered look's `itemIds` + `reason` and `source: "ai"` (mocked), transitions to `saved` on success, and shows the error affordance on rejection — demonstrates the real save replaces the stub (maps FR "Stylist real save lifecycle").
- Test: `Assemble.test.tsx` — "Save outfit" calls `saveOutfit` with the placed ids and `source: "manual"`, is **disabled when nothing is placed**, and reflects saved/error states — demonstrates the manual save + empty guard (maps FR "Build Save outfit").
- Screenshot: the Stylist look after saving (heart in its saved state) and the Build screen showing "Save outfit" beside "Wear today" — demonstrates both affordances (no session token / passcode visible).

#### 3.0 Tasks

- [x] 3.1 Add `frontend/src/types/outfit.ts` exporting a `SavedOutfit` interface (`outfitId`, `itemIds`, `source: 'ai' | 'manual'`, `reason: string | null`, `createdAt`). **RED:** write `frontend/src/api/outfits.test.ts` (mock `fetch`/`authedFetch`) asserting `saveOutfit`/`listOutfits`/`deleteOutfit` hit the right method+URL under `BASE = '/api/outfits'`, resolve on 2xx, and throw a typed `ApiError` on non-2xx. **GREEN:** implement `frontend/src/api/outfits.ts` reusing the `authedFetch`/`ensureOk`/`ApiError` pattern from `api/items.ts`.
- [x] 3.2 **RED:** extend `OutfitResult.test.tsx` (and/or `Stylist.test.tsx`) — the save control calls `saveOutfit` once with `outfit.itemIds` + `outfit.reason` + `source: "ai"`, transitions to a `saved` state on resolve, shows the error banner on reject, and is disabled while `saving`/`busy`. **GREEN:** replace the local `saved` `useState` in `OutfitResult.tsx` with a real lifecycle; lift the handler/state into `Stylist.tsx` alongside `onWearToday`/`logStatus` (pass `onSave` + `saveStatus` props), per the Q1 assumption.
- [x] 3.3 **RED:** extend `Assemble.test.tsx` — a "Save outfit" button calls `saveOutfit` with `placedIds(placement)` + `source: "manual"`, is disabled when `currentlyPlacedIds.length === 0`, and reflects `saving`/`saved`/`error`. **GREEN:** add the button to `.assemble-actions` (next to "Wear today") with an `idle | saving | saved | error` state machine mirroring `logWorn`, reusing `btn-primary`/`btn-logged`/`banner-error`.
- [x] 3.4 Capture the screenshot proof artifact (Stylist saved heart + Build "Save outfit" button), ensuring no passcode/session token is visible. **Deferred to manual verification** (headless run: empty local photo store + no live AI look to screenshot honestly) — mirrors spec 21's same-screen deferral; the RED→GREEN component/route tests are the machine-verifiable stand-in. See `26-proofs/26-task-03-proofs.md`.

### [x] 4.0 Saved Outfits page (`/saved`) + route + header nav link

Give saved looks a home. Add `frontend/src/routes/SavedOutfits.tsx` and a `/saved`
route inside `AuthGate` in `App.tsx`, plus a `Saved` nav link **left of `Build`**
(order: Saved · Build · Wardrobe · + Add). The page fetches `listOutfits()` +
`listItems()`, renders a grid of outfit cards showing each piece's photo via
`photoUrl(itemId)` plus `source`/`reason` when present, and a remove control
(`deleteOutfit(id)` → drop the card on success). It reuses the `WardrobeGrid`
`state-block`/`empty-state`/`state-note` patterns for loading / empty ("No saved
outfits yet") / error+retry. For a saved outfit whose piece was later deleted, it
resolves pieces at render time against the current wardrobe, **skips** missing
pieces, renders survivors, and shows a quiet "N piece(s) no longer in your
wardrobe" note — never crashing, even when every piece is gone (Q3-C).

#### 4.0 Proof Artifact(s)

- Test: routing test — navigating to `/saved` renders the Saved Outfits screen inside `AuthGate` — demonstrates the route is mounted and gated (maps FR "/saved route").
- Test: nav test — the header renders a `Saved` link ordered **before** `Build` — demonstrates the nav placement (maps FR "Saved link left of Build").
- Test: `SavedOutfits.test.tsx` — load renders outfit cards with pieces; empty renders the empty state; fetch failure renders error+retry; remove calls `deleteOutfit` and removes the card — demonstrates the page states (maps FR "page states").
- Test: `SavedOutfits.test.tsx` — an outfit referencing a since-deleted item renders the surviving pieces + the "no longer in your wardrobe" note and does not crash (and the all-gone case still renders the card) — demonstrates Q3-C deleted-piece tolerance.
- Screenshot: the Saved Outfits page with several saved looks (AI + manual) and the header showing the new `Saved` link — demonstrates the page end-to-end.

#### 4.0 Tasks

- [x] 4.1 **RED:** write the routing + nav test(s) — `/saved` renders the Saved Outfits screen inside `AuthGate`; the header renders a `Saved` link and it is ordered **before** `Build` (assert DOM order). **GREEN:** add `<Route path="/saved" element={<SavedOutfits />} />` and a `<Link to="/saved" className="btn">Saved</Link>` placed left of `Build` in `App.tsx`'s `.app-nav`.
- [x] 4.2 **RED:** write `SavedOutfits.test.tsx` (mock `listOutfits`/`listItems`/`deleteOutfit`) — loading shows the `state-note`; a successful load renders one card per outfit with its pieces' `<img>` (via `photoUrl`) plus `source`/`reason`; empty renders "No saved outfits yet"; a fetch rejection renders the error + retry `state-block`; clicking remove calls `deleteOutfit(id)` and drops the card. **GREEN:** implement `SavedOutfits.tsx` fetching both lists with the `settle()` pattern, rendering the grid + states, reusing `.screen`/`.grid`/`.thumb`/`.state-block`/`empty-state`/`state-note`/`.btn`/`.btn-danger`.
- [x] 4.3 **RED:** extend `SavedOutfits.test.tsx` (Q3-C) — an outfit whose `itemIds` includes an id absent from the wardrobe list renders only the surviving pieces + a muted "N piece(s) no longer in your wardrobe" note and does not crash; the all-pieces-gone case still renders the card with the note. **GREEN:** resolve pieces at render time by intersecting saved `itemIds` with the current wardrobe items, skip misses, and compute the missing-count caption (muted `state-note`/`eyebrow` voice). The saved record is never rewritten.
- [x] 4.4 Capture the screenshot proof artifact (populated Saved Outfits page + header `Saved` link), no passcode/token visible. **Deferred to manual verification** (headless run: empty local photo store + no live saved outfits to screenshot honestly) — mirrors spec 21's and task 3.4's same-screen deferral; the RED→GREEN routing/nav + page-state/deleted-piece tests are the machine-verifiable stand-in. See `26-proofs/26-task-04-proofs.md`.

### [x] 5.0 Consistent responsive layout (Part B)

Fix the confirmed CSS root cause so every screen fills the desktop width like the
Stylist page while staying mobile-first. In `frontend/src/index.css`, reconcile the
`#root { max-width: 30rem }` hard cap and the `#root:has(.stylist-layout)` special
case into a single default: the content column fits the viewport up to `72rem` on
desktop and stays a single phone-width column on small screens. Apply app-wide
(Build/Assemble, Wardrobe, Add-item, Item-detail, Saved). Naturally-narrow content
(Add-item form, item detail) **centers** within the wider shell rather than
stretching edge-to-edge. The Stylist two-pane (drawer + result) desktop layout is
**visually unchanged**. Verified manually on mobile + desktop viewports (not
unit-tested), per the spec's assumption.

#### 5.0 Proof Artifact(s)

- Screenshot: `/assemble` on a desktop viewport filling the width comparably to `/` (before/after pair) — demonstrates the primary fix (maps FR "desktop-width default").
- Screenshot: `/wardrobe`, `/add`, `/item/:id`, and `/saved` on desktop, **plus** all screens on a ~390px phone viewport showing single-column with no horizontal scroll — demonstrates the fix is app-wide and mobile-first is preserved (maps FR "applies to all narrow screens" + "narrow content centers").
- Screenshot: `/` (Stylist) before/after showing the two-pane layout unchanged — demonstrates no regression (maps FR "Stylist visually unchanged").

#### 5.0 Tasks

- [x] 5.1 In `frontend/src/index.css`, generalize the shell: remove the `#root:has(.stylist-layout) { max-width: 72rem }` exception and change `#root` from `max-width: 30rem` to `max-width: 72rem` (keep `margin: 0 auto`, `min-height: 100dvh`), so desktop-width fill is the default for every screen.
- [x] 5.2 Constrain naturally-narrow screens so they **center** instead of stretching: give the Add-item form and Item-detail containers a comfortable `max-width` + `margin-inline: auto` (matching today's ~30rem measure) within the now-wider shell. Confirm the width-filling screens (`/assemble`, `/wardrobe`, `/saved`) use the full column.
- [x] 5.3 Manually verify on a desktop viewport (`/`, `/assemble`, `/wardrobe`, `/add`, `/item/:id`, `/saved` fill/center correctly) and a ~390px phone viewport (all single-column, no horizontal scroll), and that the Stylist two-pane layout is visually unchanged; capture the screenshot proof artifacts. **Screenshots deferred to manual verification** (headless run: the `AuthGate` passcode gate blocks every screen without a live session, and the local photo store is empty — no honest populated screenshots are producible), mirroring tasks 3.4/4.4 and spec 21's same-screen deferral. The machine-verifiable stand-ins are the reviewable CSS diff + the full frontend suite (357) staying green and eslint clean (no regression). See `26-proofs/26-task-05-proofs.md` for the exact manual capture steps + viewports.
- [x] 5.4 Run `cd frontend && npm test -- --run` and the lint/format gate to confirm the CSS change regresses no existing frontend test. (357 tests green across 32 files; `npm run lint`/eslint clean. Note: the repo has no prettier config — CSS is outside the format gate; the new attribute selector matches the file's existing single-quote style, e.g. `input[type='file']`.)
