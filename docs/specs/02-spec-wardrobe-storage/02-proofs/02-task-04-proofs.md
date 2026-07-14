# Task 04 Proofs — Wardrobe CRUD API (service + controller + DTOs + validation)

## Task Summary

This task exposes the wardrobe over HTTP: a service that coordinates the
repository and photo storage, DTOs at the boundary, and a controller with
create/list/get/get-photo/update-tags/delete plus the 404 (unknown id) and 400
(bad input) error paths. It proves the API contract and error handling with
MockMvc and unit tests, keeping `Item`/storage internals off the boundary.

## What This Task Proves

- Create stores a photo and item under a server-generated id and returns a DTO
  with a `photoUrl` and `Location` header (photo bytes never embedded).
- List/get/get-photo/update-tags/delete behave per contract; get-photo returns
  `image/jpeg` bytes.
- Unknown ids return `404`; out-of-range tags (`formality`/`warmth`) and a
  missing/invalid photo return `400` — the task's critical validation paths.
- The boundary is DTO-only: the service is unit-tested against a mocked
  `PhotoStorage` interface and `WardrobeRepository`, proving the seam.

## Evidence Summary

- Full suite green: 53 tests, 0 failures — `WardrobeControllerTest` (13),
  `WardrobeServiceTest` (10), `ItemMapperTest` (2), plus earlier tasks.
- JaCoCo: every wardrobe class (service, controller, mapper, DTOs, exception
  handler) at 100% line coverage.
- Error-path coverage: `404`×3 (get/update/delete unknown id), `400`×2
  (formality and warmth out of range), `400`×2 (missing photo / invalid image).

## Artifact: API contract + error-path tests (MockMvc)

**What it proves:** Each endpoint returns the right status/body, and the 404/400
paths behave as specified.

**Why it matters:** These are the request/response contracts later UI (#5) and
stylist (#6) issues call; the 404/400 paths are the critical validation logic.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** 53 tests, 0 failures. New suites:

```
com.ensemble.wardrobe.web.WardrobeControllerTest   tests=13 fail=0 err=0
com.ensemble.wardrobe.WardrobeServiceTest          tests=10 fail=0 err=0
com.ensemble.wardrobe.dto.ItemMapperTest           tests=2  fail=0 err=0
```

Controller cases include: create→201 (+Location, photoUrl), list, get, get-photo
(`image/jpeg`), update-tags→200, delete→204, `get/update/delete unknown id`→404,
`formality=9`→400, `warmth=9`→400, missing photo→400, invalid image→400.

## Artifact: Coverage report (wardrobe API packages)

**What it proves:** Service, controller, mapper, DTOs, and the exception handler
meet the backend-domain coverage bar.

**Why it matters:** Objective evidence for ≥90% line (and the critical 404/400
paths being exercised).

**Command:**

```bash
./gradlew jacocoTestReport -PskipFrontend
```

**Result summary:** All classes at 100% line.

```
wardrobe/WardrobeService     line=25/25 (100%)
web/WardrobeController       line=15/15 (100%)
web/ApiExceptionHandler      line=3/3  (100%)
dto/ItemMapper               line=21/21 (100%)
dto/ItemResponse, TagRequest line=1/1  (100%)   (record members filtered by JaCoCo)
```

Tag-range validation is enforced by Bean Validation (`@Min/@Max` on
`TagRequest`) and the 400 paths are proven via MockMvc; id-validation lives in
`WardrobeService.get(...)` (not-found → `ItemNotFoundException` → 404), exercised
by both service and controller tests.

## Reviewer Conclusion

The wardrobe CRUD API is implemented and proven end to end at the HTTP layer:
all six operations meet their contract, unknown ids yield 404, invalid input
yields 400, and internals stay behind DTOs — all at 100% line coverage.
