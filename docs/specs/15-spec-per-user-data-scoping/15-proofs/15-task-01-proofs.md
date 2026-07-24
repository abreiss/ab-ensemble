# Task 01 Proofs — Owner-stamped, per-user-queryable data model + GSI IaC/IAM

## Task Summary

This task makes wardrobe items and saved outfits **owner-stamped and per-user
queryable**. `Item` and `SavedOutfit` gain a `userId` that is the partition key
of a **sparse** `userId-index` GSI, both repositories gain a `findByUserId`
that queries that index (no full-table scan), the startup table initializer
declares the GSI on the items/outfits tables (and warns when a pre-existing
local table lacks it), and the deployed tables + instance-role policy gain the
index plus the `index/*` Query grant. It is the data-layer foundation the later
units (ownership-enforced APIs, scoped stylist) build on.

## What This Task Proves

- A per-user `findByUserId(userId)` returns **only that user's rows**, served by
  the GSI rather than a full-table scan (success metric #3).
- The reserved `usage#<date>` daily-cap counter rows carry no `userId` and so
  are **absent from the sparse index** — a per-user query never surfaces them.
- The startup initializer creates the items/outfits tables **with** `userId-index`
  while leaving the users table plain.
- The deployed Terraform tables declare the GSI and the instance role's
  `RuntimeDynamoDb` statement is extended to `table/${prefix}-*/index/*`, and the
  configuration still passes `terraform fmt -check` + `validate` and mirrors the
  Access Analyzer lint artifact (success metric #5).

## Evidence Summary

- RED first: the new tests failed to compile against the pre-change API (missing
  `setUserId`/`findByUserId`/4-arg `ensureTable`) — 12 errors — then passed after
  the minimal implementation.
- The three integration suites pass against real DynamoDB Local (TestContainers):
  `WardrobeRepositoryIT` (10 tests), `OutfitRepositoryIT` (7), and
  `DynamoDbTableInitializerIT` (4), including the three new per-user cases.
- The full backend suite is green: **382 tests, 0 failures, 0 errors**.
- `terraform fmt -check -recursive && terraform validate` → `Success! The
  configuration is valid.`

## Artifact: RED — new tests fail before implementation

**What it proves:** The tests genuinely define new behavior (strict TDD RED); they
cannot pass without the `userId` field, `findByUserId`, and the GSI-declaring
`ensureTable` overload.

**Why it matters:** Confirms the tests exercise the new API rather than trivially
passing.

**Command:** `./gradlew compileTestJava -PskipFrontend`

**Result summary:** 12 compile errors — exactly the missing `setUserId(String)`,
`findByUserId(String)`, and 4-arg `ensureTable(...)` symbols. Expected RED.

```
error: cannot find symbol  method findByUserId(String)   location: WardrobeRepository
error: cannot find symbol  method setUserId(String)      location: Item
error: cannot find symbol  method findByUserId(String)   location: OutfitRepository
error: cannot find symbol  method setUserId(String)      location: SavedOutfit
...
12 errors
BUILD FAILED
```

## Artifact: GREEN — per-user repository/index integration tests pass

**What it proves:** With user A's and user B's rows in one real DynamoDB Local
table, `findByUserId("userA")` returns only A's rows; `usage#<date>` rows never
appear; and the initializer creates the GSI on items/outfits but not users.

**Why it matters:** This is the core demoable outcome — per-user isolation at the
data layer, served by the sparse GSI (metric #3), verified end-to-end against a
real table, not a mock.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*WardrobeRepositoryIT' \
  --tests '*OutfitRepositoryIT' --tests '*DynamoDbTableInitializerIT'
```

**Result summary:** All three suites pass. Per-suite JUnit XML totals and the new
test methods:

```
WardrobeRepositoryIT        tests=10 skipped=0 failures=0 errors=0
  - findByUserId_returnsOnlyThatUsersItems
  - findByUserId_excludesUsageCounterRows
OutfitRepositoryIT          tests=7  skipped=0 failures=0 errors=0
  - findByUserId_returnsOnlyThatUsersOutfits
DynamoDbTableInitializerIT  tests=4  skipped=0 failures=0 errors=0
  - run_createsItemsAndOutfitsTablesWithUserIdIndex
```

## Artifact: Full backend suite green (regression)

**What it proves:** Adding the `userId` attribute and the GSI wiring broke nothing
in the existing domain, controller, or stylist tests.

**Why it matters:** The green-commit sequencing keeps `findAll()`/`list()` alive
this unit; this confirms the whole backend still passes before the parent-task
commit.

**Command:** `./gradlew test -PskipFrontend`

**Result summary:** `BUILD SUCCESSFUL` — **382 tests, 0 skipped, 0 failures, 0
errors** (aggregated from `build/test-results/test/TEST-*.xml`).

## Artifact: Terraform GSI on items + outfits tables

**What it proves:** Both the `items` and `outfits` DynamoDB tables declare a
`userId` attribute and a `userId-index` GSI (`hash_key = "userId"`,
`projection_type = "ALL"`); the `users` table is untouched.

**Why it matters:** The deployed tables must carry the same index the app queries;
`projection_type = "ALL"` lets a per-user query return full attributes with no
follow-up `GetItem`.

**Artifact path:** `terraform/deploy/data_stores.tf`

**Result summary:** `git diff` shows the identical `attribute "userId"` +
`global_secondary_index "userId-index"` block appended to both `items` and
`outfits`, and no change to `users`.

```hcl
  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
```

## Artifact: Instance-role IAM extended to index/* (HCL + rendered lint JSON)

**What it proves:** The `RuntimeDynamoDb` statement's resources now include
`"${local.dynamodb_arn}/index/*"` (a GSI `Query` is authorized against the index
ARN, not the table ARN), in **both** the HCL and the rendered lint artifact CI's
Access Analyzer reads. `dynamodb:Query` was already in the action list; the
immutable policy `description` is deliberately unchanged.

**Why it matters:** Without the `index/*` resource the running app's GSI query
would be denied in the cloud; keeping the two files in sync keeps the CI policy
lint accurate (metric #5).

**Artifact paths:** `terraform/deploy/iam.tf`,
`terraform/deploy/policies/abreiss-ensemble-instance-runtime.json`

**Result summary:** Both files gain the second `index/*` ARN in the
`RuntimeDynamoDb` resource list; action list and policy description unchanged.

```hcl
    resources = [
      local.dynamodb_arn,
      "${local.dynamodb_arn}/index/*",
    ]
```

```json
      "Resource": [
        "arn:aws:dynamodb:us-east-1:123456789012:table/abreiss-ensemble-*",
        "arn:aws:dynamodb:us-east-1:123456789012:table/abreiss-ensemble-*/index/*"
      ],
```

## Artifact: `terraform validate` passes

**What it proves:** The changed HCL is well-formed and internally consistent.

**Why it matters:** Success metric #5 requires `terraform validate` (and
`fmt -check`) to accept the GSI + IAM changes.

**Command:**

```bash
cd terraform/deploy && terraform fmt -check -recursive \
  && terraform init -backend=false && terraform validate
```

**Result summary:** `fmt -check` produced no output (already formatted); validate
printed `Success! The configuration is valid.` The only warning is a
machine-local `dev.local/...` provider dev-override in the global Terraform CLI
config — pre-existing and unrelated to this project's `aws` provider or these
edits.

```
Terraform has been successfully initialized!
...
Success! The configuration is valid, but there were some
validation warnings as shown above.   # warning = local dev_overrides, not this config
```

## Reviewer Conclusion

The data layer is now owner-stamped and per-user queryable end-to-end: a sparse
`userId-index` GSI serves `findByUserId` (no scan, `usage#` rows excluded),
verified against real DynamoDB Local; the initializer declares it locally and
warns on a stale table; and the deployed tables + least-privilege instance policy
gain the index and the `index/*` Query grant with `terraform validate` green.
Success metrics #3 (no scan) and #5 (`validate` + lint in sync) are satisfied for
Unit 1. Ownership *enforcement* on the API surface is the next unit (2.0).
