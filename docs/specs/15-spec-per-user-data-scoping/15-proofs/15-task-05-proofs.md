# Task 05 Proofs — One-time cleanup of pre-existing unowned data

## Task Summary

This task proves that an **opt-in, idempotent startup runner** can purge the
pre-existing "unowned" data left over from before per-user ownership existed —
`Item` and `SavedOutfit` rows that carry **no `userId`** — plus each such item's
stored photo, while provably leaving **owned rows** and the reserved
`usage#<date>` daily-cap counter rows untouched. The runner defaults **off**, so
it is a no-op on every ordinary startup (and in every `@SpringBootTest` context,
which has no live DynamoDB); an operator flips one env var / tfvar to `true` for a
single deploy to clear legacy data, then flips it back. The run is idempotent and
resilient — a failing photo delete never aborts the migration.

## What This Task Proves

- A new `UnownedDataPurgeRunner` (`ApplicationRunner`, ordered after
  `DynamoDbTableInitializer`/`SeedAccountRunner`) deletes only unowned items +
  their photos and unowned outfits, and is a **no-op when disabled** — the
  critical path that keeps existing Spring-context tests safe.
- New repository scans `WardrobeRepository.findUnowned()` (excludes
  `usage#`-prefixed counter rows) and `OutfitRepository.findUnowned()` enumerate
  exactly the legacy null/blank-`userId` rows, verified against real DynamoDB Local.
- The purge is **idempotent** (a second run with nothing unowned deletes nothing)
  and **resilient** (a photo whose file is already gone — or a photo delete that
  throws — still lets the row be purged and the run continue).
- The flag is wired end-to-end: `ensemble.migration.purge-unowned`
  (`${ENSEMBLE_MIGRATION_PURGE_UNOWNED:false}`) in `application.yml`, documented in
  `.env.example`, kept off in test config, and exposed as a `false`-defaulting
  `var.purge_unowned_data` in the App Runner Terraform.
- Success metric #4 (scoped cleanup — only unowned rows removed) and #5 (full gate:
  backend + frontend + `terraform validate` green) are met, with **100% line and
  100% branch** coverage on the new migration code and the touched repositories.

## Evidence Summary

- RED: the runner unit test failed to **compile** before any production code existed
  (`UnownedDataPurgeRunner` / `MigrationProperties` / `findUnowned` absent).
- GREEN: `UnownedDataPurgeRunnerTest` (4 cases) + `UnownedDataPurgeRunnerIT` (2 cases)
  + the added `findUnowned` repository IT cases all pass.
- Coverage: `UnownedDataPurgeRunner` 100% line / **6-of-6 branch**; `MigrationProperties`
  100% line; `WardrobeRepository` and `OutfitRepository` **100% line / 100% branch**.
- Full gate: backend `test` + `jacocoTestReport` BUILD SUCCESSFUL; frontend **374/374**
  passing (Non-Goal #7 — no frontend change); `terraform fmt -check` clean +
  `terraform validate` → *Success! The configuration is valid.*

---

## Artifact: RED — runner test fails before the runner exists

**What it proves:** The behavior was defined by a failing test first (strict TDD),
not retrofitted onto existing code.

**Why it matters:** Confirms the RED phase — the test references
`UnownedDataPurgeRunner`, `MigrationProperties`, and the not-yet-added
`findUnowned()` methods, so it cannot compile until GREEN.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*UnownedDataPurgeRunnerTest'
```

**Result summary:** 20 compile errors — `cannot find symbol` for the runner class,
the properties record, and `findUnowned()` on both repositories — then `BUILD FAILED`.

```text
error: cannot find symbol
  when(wardrobeRepository.findUnowned()).thenReturn(List.of(orphanA, orphanB));
                         ^   symbol: method findUnowned()
...
20 errors
BUILD FAILED in 498ms
```

## Artifact: GREEN — runner unit tests (orchestration + resilience)

**What it proves:** With the runner implemented, all four decision branches hold:
disabled no-op, idempotent re-run, delete-photo-then-row happy path, and a
photo-delete failure that still purges the row and continues.

**Why it matters:** The disabled no-op is the branch that keeps every existing
`@SpringBootTest` context safe (no live DynamoDB); the resilience case is the
audit-flagged blind spot (a bad photo must not abort the migration).

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*UnownedDataPurgeRunnerTest'
```

**Result summary:** `tests=4 failures=0 errors=0`.

```text
✓ purge_whenDisabled_isNoOp()
✓ purge_secondRun_isNoOp()
✓ purge_whenEnabled_deletesUnownedItemPhotosThenRows_andUnownedOutfits()
✓ purge_whenPhotoDeleteFails_stillDeletesRowAndContinues()
```

## Artifact: GREEN — end-to-end purge against DynamoDB Local

**What it proves:** Against a real DynamoDB Local table, the enabled purge removes
**only** the unowned item/outfit rows and the unowned item's stored photo, while the
owned item + its photo and the reserved `usage#2026-07-16` counter row survive; and
with the flag off, everything is left untouched. The seed includes an unowned item
whose photo is **already missing** (resilience) — the row is still deleted.

**Why it matters:** This is the headline proof of success metric #4 — scoped cleanup
that never touches owned data or the daily-cap counters, exercised through the real
scan + delete path (not mocks).

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*UnownedDataPurgeRunnerIT'
```

**Result summary:** `tests=2 failures=0 errors=0`.

```text
✓ purge_whenEnabled_removesOnlyUnownedRowsAndTheirPhotos_leavingOwnedAndUsageRows()
✓ purge_whenDisabled_leavesEverythingUntouched()
```

## Artifact: `findUnowned` repository scans (null + blank userId, usage rows excluded)

**What it proves:** `WardrobeRepository.findUnowned()` returns the null-`userId`
orphan **and** a whitespace-`userId` (blank) row, while excluding the owned row and
the reserved `usage#<date>` counter row; `OutfitRepository.findUnowned()` behaves
equivalently (no counter rows on the dedicated outfits table).

**Why it matters:** The `usage#` exclusion is what stops the purge from destroying
legitimate daily-cap counters (they carry no `userId` by design); the blank-`userId`
case covers the defensive `null || isBlank()` guard the runner relies on.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*WardrobeRepositoryIT' --tests '*OutfitRepositoryIT'
```

**Result summary:** `WardrobeRepositoryIT` 8/8 and `OutfitRepositoryIT` 8/8 pass,
including the new `findUnowned_*` cases.

```text
WardrobeRepositoryIT ✓ findUnowned_returnsRowsWithNoOrBlankUserId_excludingUsageCounterRows()
OutfitRepositoryIT   ✓ findUnowned_returnsOnlyOutfitsWithNoOrBlankUserId()
```

## Artifact: Coverage — 100% line / 100% branch on new + touched code

**What it proves:** The migration runner and both repository scans are fully covered,
including the disabled guard, the empty-result path, the delete happy path, the
photo-delete catch branch, and the `null`/`blank`/`usage#` filter branches.

**Why it matters:** Backend domain code must meet ≥90% line; the branch-complete
result shows no untested decision remains in the new cleanup logic.

**Command:**

```bash
./gradlew test jacocoTestReport -PskipFrontend
# then parse build/reports/jacoco/test/jacocoTestReport.xml
```

**Artifact path:** `build/reports/jacoco/test/html/index.html`

**Result summary:**

```text
UnownedDataPurgeRunner   lines 100%  branches 6/6 (100%)
MigrationProperties      lines 100%  branches n/a (record — no branches)
WardrobeRepository       lines 100%  branches 6/6 (100%)
OutfitRepository         lines 100%  branches 4/4 (100%)
```

## Artifact: Flag wiring diff (app config, env doc, test config, Terraform)

**What it proves:** The opt-in flag is threaded through every layer with a safe
default of `false`, and the deploy path exposes it without breaking on App Runner's
empty-string-drop behavior (emitted as an explicit `"true"`/`"false"` literal).

**Why it matters:** Success metric #5 requires the flag be present and default-off;
this shows the exact wiring an operator toggles.

**Command:**

```bash
git diff --stat
```

**Result summary:** 11 files changed (+128/−15), plus two new `migration/` source
directories (runner + properties, and their tests).

```text
 .env.example                                       |  8 +++++++
 src/main/java/com/ensemble/config/DynamoDbConfig.java   |  5 ++++-
 src/main/java/com/ensemble/outfit/OutfitRepository.java | 19 +++++++--
 src/main/java/com/ensemble/wardrobe/WardrobeRepository.java | 19 ++++++++
 src/main/resources/application.yml                 |  9 ++++++++
 src/test/resources/application.yml                 |  5 +++++
 terraform/deploy/apprunner.tf                      |  6 ++++++
 terraform/deploy/variables.tf                      |  6 ++++++
 ... (+ IT test files, task list)
?? src/main/java/com/ensemble/migration/     (UnownedDataPurgeRunner, MigrationProperties)
?? src/test/java/com/ensemble/migration/     (UnownedDataPurgeRunnerTest, UnownedDataPurgeRunnerIT)
```

Key config (`application.yml`):

```yaml
ensemble:
  migration:
    # One-time, opt-in cleanup of pre-existing "unowned" data (spec #15) ...
    purge-unowned: ${ENSEMBLE_MIGRATION_PURGE_UNOWNED:false}
```

Deploy wiring (`terraform/deploy/apprunner.tf`, `false` by default):

```hcl
ENSEMBLE_MIGRATION_PURGE_UNOWNED = var.purge_unowned_data ? "true" : "false"
```

## Artifact: Full regression gate (backend + frontend + Terraform)

**What it proves:** The whole suite stays green with the new code; the frontend is
untouched (Non-Goal #7) and still passes; the Terraform still validates.

**Why it matters:** Success metric #5 — the complete gate — must pass before this
unit is considered done.

**Commands & result summary:**

```bash
./gradlew test jacocoTestReport -PskipFrontend
# BUILD SUCCESSFUL

( cd frontend && npm test -- --run )
# Test Files 32 passed (32) | Tests 374 passed (374)

( cd terraform/deploy && terraform fmt -check -recursive && terraform init -backend=false && terraform validate )
# fmt: clean (no output) ; validate: Success! The configuration is valid.
```

## Reviewer Conclusion

The one-time unowned-data cleanup works end-to-end and safely: it is opt-in and
default-off (a proven no-op on ordinary startup and under `@SpringBootTest`), it
removes **only** legacy null/blank-`userId` rows and their photos, it preserves owned
rows and the reserved `usage#<date>` counters, and it is idempotent and resilient to a
missing/failed photo delete. New migration code and the touched repositories are at
100% line and 100% branch coverage, and the full backend + frontend + Terraform gate
is green — satisfying spec success metrics #4 and #5 for Unit 5.
