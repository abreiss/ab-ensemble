# Task 02 Proofs — Item model + WardrobeRepository (Enhanced Client CRUD)

## Task Summary

This task defines the wardrobe's persisted shape (`Item`) and the thin
`WardrobeRepository` that creates, reads, lists, and deletes items via the AWS
SDK v2 DynamoDB Enhanced Client. It proves items round-trip faithfully against a
real DynamoDB Local, including the empty-wardrobe edge case.

## What This Task Proves

- `Item` maps cleanly through the Enhanced Client bean mapper: all fields
  (tags, `descriptors` list, `Instant` timestamps, wear-history ints) persist
  and read back unchanged.
- `WardrobeRepository` supports create, get-by-id, list-all, replace, and delete.
- The empty wardrobe and missing-id cases return empty rather than throwing.
- Backend-domain coverage exceeds the ≥90% line standard.

## Evidence Summary

- `WardrobeRepositoryIT` (TestContainers, DynamoDB Local) passes 6/6, covering
  round-trip, missing id, empty list, list-all, replace, and delete.
- Full suite green (19 tests, 0 failures); skeleton + task-01 tests unaffected.
- JaCoCo: `Item` and `WardrobeRepository` at 100% line coverage.

## Artifact: DynamoDB Local round-trip integration tests

**What it proves:** Every repository operation works against real DynamoDB Local,
each test isolated on its own uniquely-named table.

**Why it matters:** This is the real persistence contract later tasks (service,
API) depend on — not a mock.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `WardrobeRepositoryIT` ran 6 tests, 0 failures. Full per-suite
breakdown (19 tests total, all green):

```
com.ensemble.wardrobe.WardrobeRepositoryIT       tests=6 fail=0 err=0 skip=0
com.ensemble.config.DynamoDbTableInitializerIT   tests=2 fail=0 err=0 skip=0
com.ensemble.EnsembleApplicationTests            tests=1 fail=0 err=0 skip=0
com.ensemble.health.HealthControllerTest         tests=1 fail=0 err=0 skip=0
com.ensemble.web.SpaForwardingTest               tests=5 fail=0 err=0 skip=0
com.ensemble.web.SpaPathResourceResolverTest     tests=4 fail=0 err=0 skip=0
```

Cases covered by `WardrobeRepositoryIT`:

- `save_thenFindById_returnsPersistedItemWithAllFields`
- `findById_whenMissing_returnsEmpty`
- `findAll_whenEmptyWardrobe_returnsEmptyList`
- `findAll_returnsEverySavedItem`
- `save_existingId_replacesTheItem`
- `deleteById_removesTheItem`

## Artifact: Coverage report (wardrobe package)

**What it proves:** The model and repository meet the backend-domain TDD standard.

**Why it matters:** AGENTS.md requires ≥90% line on backend-domain code; this
confirms it objectively.

**Command:**

```bash
./gradlew jacocoTestReport -PskipFrontend
```

**Result summary:** Both classes at 100% line coverage (no branches — the code
is straight-line).

```
WardrobeRepository   line=9/9 (100%)
Item                 line=37/37 (100%)
```

## Reviewer Conclusion

The wardrobe data model and repository are implemented and proven against real
DynamoDB Local: all CRUD operations round-trip correctly, edge cases return
empty safely, and coverage is at 100% line for the package.
