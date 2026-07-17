# Task 04 Proofs - Daily call cap: atomic counter, 429 & wardrobe scan filter

## Task Summary

This task adds the second security/cost guard: a global daily cap on the two
Claude-backed endpoints (`POST /api/style`, `POST /api/items/tag`). A `UsageProperties`
config record holds the configurable limit (default 100); a `UsageRepository` keeps an
atomic DynamoDB counter under a reserved `usage#<UTC-date>` key on the same
`ensemble-items` table (via the low-level `DynamoDbClient`, since the enhanced/bean
client cannot express an atomic `ADD`); a Clock-driven `CallCapService.reserve()`
increments that counter and throws `DailyCapExceededException` (→ `429`) once the
increment pushes the count past the limit; and `WardrobeRepository.findAll()` now
excludes `usage#`-prefixed rows so the counter never leaks into the wardrobe or the
stylist's `searchWardrobe` tool. All backend-domain pieces were built strict-TDD
(RED → GREEN), matching `docs/specs/07-spec-pwa-security-guards` Unit 3.

## What This Task Proves

- `CallCapService.reserve()` increments the shared counter for the current UTC date
  (via an injected `Clock`, not wall-clock time) and throws once the incremented count
  exceeds `ensemble.usage.daily-limit` — 100% branch coverage on the limit check.
- `UsageRepository.increment()` is a real atomic `ADD UpdateItem` against DynamoDB Local:
  two increments for the same date persist as `2`, and a different date starts its own
  counter at `1`.
- `WardrobeRepository.findAll()` excludes `usage#`-prefixed rows so a reserved
  counter row never appears as a wardrobe `Item` — 100% branch coverage on the filter.
- Both `POST /api/style` and `POST /api/items/tag` call `reserve()` before doing any
  Claude-bound work; once the cap is hit, they return `429` and the underlying
  `StylistService`/`TaggingService` is never invoked (`verifyNoInteractions`).
- A request rejected before it reaches the controller body (a `400` for a missing
  multipart part) does not touch the counter at all (`verifyNoInteractions(callCapService)`
  in `tag_missingPhotoPart_returns400`) — this locks the planning audit's FLAG-1 finding
  (non-Claude-reaching requests must not corrupt the counter).
- End-to-end, a real running instance with `ensemble.usage.daily-limit=2` allows two
  authenticated `POST /api/style` calls and rejects the third with `429`, and the live
  wardrobe listing stays empty (no `usage#` row leaks through) despite the counter having
  been written twice in the same shared table.
- No regressions: the full backend suite (specs 01-07) stays green.

## Evidence Summary

- `UsagePropertiesTest` (2 tests): the configured limit is kept; a non-positive limit
  falls back to the default of 100.
- `CallCapServiceTest` (4 tests): under-limit allows and increments; at-limit throws
  `DailyCapExceededException`; the UTC date is used as the counter key; a new UTC day
  uses a new key — this is the 100%-branch surface on the limit check.
- `UsageRepositoryIT` (2 tests, DynamoDB Local via TestContainers): two increments for
  the same date persist as `2` in the real table; a different date tracks its own count.
- `WardrobeRepositoryIT` (7 tests, including the new `findAll_excludesUsageRows`): a
  written `usage#<date>` row plus a real item — `findAll()` returns only the real item.
- `StyleControllerTest` / `TaggingControllerTest` (4 and 6 tests respectively): the new
  `postStyle_overDailyCap_returns429` / `tag_overDailyCap_returns429` cases return `429`
  with `{"error":"daily_cap_exceeded", ...}` and never call the underlying service; the
  existing `tag_missingPhotoPart_returns400` case now also asserts the cap service is
  never touched.
- Full suite: `./gradlew test` — **208 tests, 0 failures** across 34 backend test
  classes (specs 01-07) — no regressions from the cap.
- JaCoCo: `com.ensemble.usage` and `com.ensemble.wardrobe` packages both report
  **100% instruction and 100% branch** coverage (report paths below).
- Live `curl` proof against a real running instance (DynamoDB Local up, a throwaway demo
  passcode set only in the shell environment, `ensemble.usage.daily-limit=2`) confirms
  the cap end-to-end and that the wardrobe listing excludes the resulting counter row.

## Artifact: `UsagePropertiesTest` + `CallCapServiceTest` — the limit check, 100% branch

**What it proves:** The configured daily limit is honored (or defaulted to 100 when
non-positive); `reserve()` increments via the injected `Clock`'s UTC date, allows the
request under the limit, and throws once the incremented count exceeds it; a new UTC
day computes a new counter key.

**Why it matters:** This is the spec's explicit 100%-branch requirement on the
daily-limit check — the guard standing between the app and an unbounded Claude bill.

**Command:**

```bash
./gradlew test --tests '*UsagePropertiesTest' --tests '*CallCapServiceTest'
```

**Result summary:** All 6 tests passed.

```
UsagePropertiesTest > nonPositiveDailyLimit_fallsBackToDefault() PASSED
UsagePropertiesTest > configuredDailyLimit_isKept() PASSED

CallCapServiceTest > newUtcDay_resetsCount() PASSED
CallCapServiceTest > atLimit_blocksWith429Signal() PASSED
CallCapServiceTest > underLimit_allowsAndIncrements() PASSED
CallCapServiceTest > usesUtcDateKey() PASSED

BUILD SUCCESSFUL
```

## Artifact: `UsageRepositoryIT` — real atomic counter round-trip

**What it proves:** `UsageRepository.increment()` performs a real atomic `ADD
UpdateItem` against DynamoDB Local: two increments for the same UTC date persist as
`2` (verified both via the returned value and a raw `GetItem`), and a different date
starts its own independent counter at `1`.

**Why it matters:** The limit check is only meaningful if the underlying counter is
truly atomic and durable — this is the one test that exercises the real DynamoDB
`ADD` semantics rather than a mock.

**Command:**

```bash
./gradlew test --tests '*UsageRepositoryIT'
```

**Result summary:** Both tests passed against a fresh, uniquely-named DynamoDB Local
table (TestContainers).

```
UsageRepositoryIT > incrementForDifferentDates_tracksSeparateCounters() PASSED
UsageRepositoryIT > incrementIsAtomicAndPersists() PASSED

BUILD SUCCESSFUL
```

## Artifact: `WardrobeRepositoryIT` — scan filter excludes `usage#` rows, 100% branch

**What it proves:** `WardrobeRepository.findAll()` excludes any row whose `itemId`
starts with `usage#`, so the reserved counter row sharing the `ensemble-items` table
never appears as a wardrobe `Item`.

**Why it matters:** This is the critical guard called out in the spec — without it, the
daily-cap counter would leak into the wardrobe listing and the stylist's
`searchWardrobe` tool as a bogus "garment."

**Command:**

```bash
./gradlew test --tests '*WardrobeRepositoryIT'
```

**Result summary:** All 7 tests passed, including the new
`findAll_excludesUsageRows` (a `usage#2026-07-16` row + a real item → only the real
item is returned) alongside the pre-existing save/find/delete coverage.

```
WardrobeRepositoryIT > save_thenFindById_returnsPersistedItemWithAllFields() PASSED
WardrobeRepositoryIT > findAll_whenEmptyWardrobe_returnsEmptyList() PASSED
WardrobeRepositoryIT > findAll_excludesUsageRows() PASSED
WardrobeRepositoryIT > save_existingId_replacesTheItem() PASSED
WardrobeRepositoryIT > findAll_returnsEverySavedItem() PASSED
WardrobeRepositoryIT > findById_whenMissing_returnsEmpty() PASSED
WardrobeRepositoryIT > deleteById_removesTheItem() PASSED

BUILD SUCCESSFUL
```

## Artifact: Controller `429` contract — cap enforced before the Claude call

**What it proves:** With `CallCapService` mocked to throw, both
`POST /api/style` and `POST /api/items/tag` return `429` with
`{"error":"daily_cap_exceeded", ...}` and never invoke the underlying
`StylistService`/`TaggingService` (`verifyNoInteractions`). Separately, a request
rejected before the controller body runs (`tag_missingPhotoPart_returns400`) never
touches `CallCapService` at all — locking the planning audit's FLAG-1 finding that
non-Claude-reaching requests must not corrupt the counter.

**Why it matters:** This is the end of the guarantee chain: the cap must be checked
*before* any paid call, and a rejected/malformed request must not silently consume
budget it never used.

**Command:**

```bash
./gradlew test --tests '*StyleControllerTest' --tests '*TaggingControllerTest'
```

**Result summary:** All 10 tests passed (4 + 6), including the two new `429` cases
and the FLAG-1 non-corruption assertion.

```
StyleControllerTest > postStyle_upstreamFailure_returnsGracefulError() PASSED
StyleControllerTest > postStyle_valid_returns200WithOutfit() PASSED
StyleControllerTest > postStyle_emptyWardrobe_returnsFriendlyResponse() PASSED
StyleControllerTest > postStyle_overDailyCap_returns429() PASSED

TaggingControllerTest > tag_photoReadFails_throwsInvalidImage_andServiceNeverCalled() PASSED
TaggingControllerTest > tag_overDailyCap_returns429() PASSED
TaggingControllerTest > tag_goodSuggestion_returns200Json() PASSED
TaggingControllerTest > tag_missingPhotoPart_returns400() PASSED
TaggingControllerTest > tag_nonImage_serviceThrowsInvalidImage_returns400_sanitizedBody() PASSED
TaggingControllerTest > tag_degradedEmptySuggestion_stillReturns200() PASSED

BUILD SUCCESSFUL
```

## Artifact: Full backend suite — no regressions to specs 01-06

**What it proves:** Adding the daily-cap service, the new `usage` package, the
`WardrobeRepository` scan filter, and the controller wiring does not break any
pre-existing repository, service, controller-slice, or context test.

**Command:**

```bash
./gradlew test
```

**Result summary:** 208 tests across 34 backend test classes, 0 failures, 0 errors.

```
BUILD SUCCESSFUL in 12s
7 actionable tasks: 2 executed, 5 up-to-date

test classes: 34, total tests: 208, failures: 0, errors: 0
```

## Artifact: JaCoCo coverage — 100% branch on the limit check and the scan filter

**What it proves:** The `com.ensemble.usage` package (`UsageProperties`,
`CallCapService`, `UsageRepository`, `DailyCapExceededException`, `UsageConfig`) and
the `com.ensemble.wardrobe` package (including the `findAll()` scan filter) both
report 100% instruction and 100% branch coverage.

**Why it matters:** This is the spec's coverage metric (#5) for the daily-limit check
and the wardrobe scan filter specifically — not just an aggregate project number.

**Command:**

```bash
./gradlew test jacocoTestReport
```

**Report paths:**
`build/reports/jacoco/test/html/com.ensemble.usage/index.html`
`build/reports/jacoco/test/html/com.ensemble.wardrobe/index.html`

**Result summary:** Both packages show `0` missed instructions and `0` missed
branches.

```
com.ensemble.usage:    instructions 108/108 (100%), branches 4/4 (100%)
com.ensemble.wardrobe: instructions 281/281 (100%), branches 2/2 (100%)
```

## Artifact: End-to-end proof against a running instance (`daily-limit=2`)

**What it proves:** With DynamoDB Local running, a throwaway demo passcode set only as
a shell environment variable, and `ensemble.usage.daily-limit=2`, two authenticated
`POST /api/style` calls succeed and the third is rejected with `429`; the wardrobe
listing stays empty even though the counter row was written twice into the same
shared table (the scan filter holds live, not just in tests).

**Why it matters:** This is the only proof that exercises the real servlet container,
the real DynamoDB table, and the real counter/filter interaction together, not a
mocked test harness.

**Commands and results (passcode/token values are demo/throwaway, never real secrets):**

```bash
$ ./gradlew bootRun --args='--ensemble.usage.daily-limit=2'   # ENSEMBLE_PASSCODE set only in the shell

$ curl -s -X POST localhost:8080/api/auth \
    -H 'Content-Type: application/json' -d '{"passcode":"[DEMO_PASSCODE]"}'
200
# body: {"token":"[REDACTED_TOKEN]"}

$ for i in 1 2 3; do
    curl -s -o /dev/null -w 'call '$i' -> HTTP %{http_code}\n' -X POST localhost:8080/api/style \
      -H "X-Ensemble-Session: [REDACTED_TOKEN]" -H 'Content-Type: application/json' \
      -d '{"prompt":"streetwear today"}'
  done
call 1 -> HTTP 200
call 2 -> HTTP 200
call 3 -> HTTP 429
# call 3 body: {"error":"daily_cap_exceeded","message":"daily call limit reached, try again tomorrow"}

$ curl -s -H "X-Ensemble-Session: [REDACTED_TOKEN]" localhost:8080/api/items
[]
# confirms the two usage# rows written by the calls above never leak into the wardrobe listing
```

**Result summary:** The third call is rejected exactly at the configured limit of 2,
with the sanitized `429` body, and the live wardrobe listing stays clean of the
counter rows. The demo instance was torn down immediately after capturing this
evidence; no secret or live token is committed.

## Reviewer Conclusion

The daily call cap is implemented and verified at three levels: unit (the limit check
and UTC-day key derivation, 100% branch), slice (the `429` contract on both Claude
endpoints, plus the FLAG-1 non-corruption assertion on a rejected request), and live
end-to-end (a real running instance with a tiny limit, hitting the cap and confirming
the scan filter holds outside of tests too). No pre-existing test from specs 01-06
regressed.
