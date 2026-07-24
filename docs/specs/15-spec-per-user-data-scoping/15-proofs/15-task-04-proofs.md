# Task 04 Proofs — User-scoped stylist + cross-user grounding guardrail

## Task Summary

This task scopes the stylist to the authenticated caller's wardrobe. `StylistService.style`
and `StyleController` now take the caller's `userId`, read the wardrobe via the scoped
`WardrobeService.list(userId)` (the sparse `userId-index` GSI), and build `validIds` from
that scoped list. As a result an item id owned by a **different** user is never in
`validIds`, so the existing grounding guardrail rejects it, feeds it back, retries exactly
once, and drops it — **identically to a hallucinated id**. This is the cross-user privacy
boundary. No image bytes ever reach the model, and the server stays stateless.

With the stylist migrated, the last unscoped readers — `WardrobeService.list()` and
`WardrobeRepository.findAll()` — were removed, so no full-table wardrobe read remains
anywhere in the wardrobe/outfit/stylist code (the Unit 5 `findUnowned` migration scan is the
only sanctioned full-table read, added in a later task).

## What This Task Proves

- The stylist builds outfits only from the caller's wardrobe (`style(userId, …)` reads
  `wardrobe.list(userId)`).
- A **real item owned by another user** is rejected exactly like a hallucinated id — fed
  back, retried once, then dropped — so it never surfaces to the caller (privacy boundary).
- The whole existing grounding/id-validation suite still passes against the scoped signature.
- No image bytes reach the model; the re-pick / statelessness invariants are unchanged.
- Grounding / id-validation is at **100% branch coverage**, now including the cross-user path.
- The last unscoped wardrobe reader is gone; a grep-guard confirms no unscoped reader remains.

## Evidence Summary

- **RED:** the new cross-user test fails to compile against the old unscoped signature.
- **GREEN:** the migrated `StylistServiceTest` + `StyleControllerTest` pass, and the full
  backend suite is green at **395 tests, 0 failures, 0 errors**.
- **Coverage:** `StylistService` branch coverage is **18/18 (100%)**; the grounding methods
  (`style` 10/10, `pickWithOneRetry` 2/2, the id-validation predicate 2/2) have zero missed
  branches.
- **Grep-guard:** no `.findAll()` / no-arg `.list()` caller remains under
  `wardrobe|outfit|stylist`.

## Artifact: RED — cross-user test rejects the unscoped signature

**What it proves:** the new test `styleRequest_withOtherUsersId_retriesOnceThenRejects`
defines behavior (a `userId`-scoped `style`) that the pre-task code did not support.

**Why it matters:** strict TDD — the failing test is written before the production change.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*StylistServiceTest'
```

**Result summary:** compilation failed because `service.style(USER, "date night")` has no
matching overload on the old `style(String vibe, List<StylistMessage> history)` signature.

```text
src/test/java/com/ensemble/stylist/StylistServiceTest.java:185: error: incompatible types: String cannot be converted to List<StylistMessage>
BUILD FAILED
> Compilation failed; see the compiler output below.
```

## Artifact: The cross-user privacy test

**What it proves:** an id owned by another user (`b-userB`) is dropped through the same
one-retry grounding path as a hallucinated id; only the caller's grounded id (`a`) survives.

**Why it matters:** this is the headline acceptance criterion — the stylist can never render
or leak another user's item.

**Artifact path:** `src/test/java/com/ensemble/stylist/StylistServiceTest.java`

**Result summary:** with `wardrobe.list("userA")` stubbed to hold only `a`, the model first
returns `b-userB`; the service feeds the invalid id back, retries once (`times(2)`), and
returns only `a`.

```java
@Test
void styleRequest_withOtherUsersId_retriesOnceThenRejects() {
    when(wardrobe.list(USER)).thenReturn(List.of(item("a")));
    when(model.proposeOutfit(anyString(), anyList()))
        .thenReturn(pick("first", "a", "b-userB"))
        .thenReturn(pick("second", "a"));

    Outfit outfit = service.style(USER, "date night");

    assertThat(outfit.itemIds()).containsExactly("a");
    assertThat(outfit.reason()).isEqualTo("second");
    verify(model, times(2)).proposeOutfit(anyString(), anyList());
}
```

## Artifact: GREEN — full backend suite passes

**What it proves:** the scoped signature migration (service + controller + tests) and the
removal of the unscoped readers leave the whole backend suite green.

**Why it matters:** confirms no regression in grounding, statelessness, re-pick, input caps,
ownership scoping, or photo isolation.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `BUILD SUCCESSFUL`; aggregated JUnit results across all suites show
**395 tests, 0 failures, 0 errors, 0 skipped**.

```text
> Task :test
BUILD SUCCESSFUL in 18s

tests=395 failures=0 errors=0 skipped=0
```

## Artifact: Grounding / id-validation at 100% branch coverage

**What it proves:** every branch in `StylistService` — including the cross-user rejection
path — is exercised.

**Why it matters:** the grounding guardrail is critical logic requiring 100% branch coverage;
the cross-user id reuses the hallucinated-id branch, so no branch is left uncovered.

**Command:**

```bash
./gradlew jacocoTestReport -PskipFrontend
# report: build/reports/jacoco/test/html/index.html
```

**Result summary:** `StylistService` class branch counter is **18 covered / 0 missed
(100%)**; the grounding methods have zero missed branches.

```text
CLASS StylistService  [BRANCH] missed=0 covered=18   [LINE] missed=0 covered=53
  style               branch 10/10 (missed 0)
  pickWithOneRetry    branch 2/2   (missed 0)
  lambda$invalidIds$0 branch 2/2   (missed 0)   # the !validIds.contains(id) id-validation predicate
  renderWardrobe      branch 2/2   (missed 0)
  nullSafe            branch 2/2   (missed 0)
```

## Artifact: No unscoped wardrobe reader remains

**What it proves:** the last full-table wardrobe reads (`WardrobeService.list()` /
`WardrobeRepository.findAll()`) are removed; every remaining wardrobe/outfit read is
`userId`-scoped.

**Why it matters:** closes the possibility of an accidental cross-user read being
reintroduced through an unscoped list.

**Command:**

```bash
grep -rn "\.findAll()\|\.list()" \
  src/main/java/com/ensemble/wardrobe \
  src/main/java/com/ensemble/outfit \
  src/main/java/com/ensemble/stylist
```

**Result summary:** no matches — clean.

```text
>>> clean (no unscoped readers)
```

## Reviewer Conclusion

The stylist is fully user-scoped: it reasons only over the caller's wardrobe, rejects a
different user's item id through the same grounded one-retry path as a hallucinated id (never
leaking it), sends no image bytes, and stays stateless. Grounding/id-validation remains at
100% branch coverage including the new cross-user path, the full backend suite is green at
395 tests, and no unscoped wardrobe reader remains in the codebase.
