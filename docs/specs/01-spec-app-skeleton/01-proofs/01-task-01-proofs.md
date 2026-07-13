# Task 01 Proofs - Backend scaffold + health endpoint

## Task Summary

This task stands up the Gradle Spring Boot backend (`com.ensemble`, Java 21, Spring Boot 4.1.0) with JUnit 5 + JaCoCo, and implements `GET /api/health` test-first. It proves the backend boots and serves the API — the baseline every later feature builds on.

> Version note: the spec assumed Spring Boot 3.4.x as a bump-able placeholder; the current Initializr default is **4.1.0** (Java 21). Boot 4 renamed the web starter to `spring-boot-starter-webmvc` and moved `@WebMvcTest` to `org.springframework.boot.webmvc.test.autoconfigure`.

## What This Task Proves

- `GET /api/health` returns HTTP 200 with body `{"status":"ok"}`.
- The endpoint is covered by an automated test written before the implementation (TDD).
- The Spring Boot app boots locally and serves the API over HTTP.
- Test + coverage infrastructure (JUnit 5, JaCoCo) is wired.

## Evidence Summary

- RED: the test failed to compile before the controller existed (missing `HealthController`).
- GREEN: `./gradlew test jacocoTestReport` → `BUILD SUCCESSFUL`; `HealthControllerTest` = 1 test, 0 failures.
- Runtime: `curl localhost:8080/api/health` → `200` and `{"status":"ok"}`.

## Artifact: TDD RED — failing test before implementation

**What it proves:** The test was written first and failed because `HealthController` did not exist.

**Why it matters:** Confirms strict TDD (RED before GREEN), per `AGENTS.md`.

**Command:** `./gradlew test`

**Result summary:** Test compilation failed with `cannot find symbol: class HealthController`, the expected RED state.

```
> Task :compileTestJava FAILED
error: cannot find symbol
@WebMvcTest(HealthController.class)
            ^  symbol: class HealthController
BUILD FAILED
```

## Artifact: TDD GREEN — tests + coverage pass

**What it proves:** After implementing `HealthController`, the test passes and the coverage report generates.

**Why it matters:** The endpoint contract is met and verified automatically.

**Command:** `./gradlew test jacocoTestReport --console=plain`

**Result summary:** `BUILD SUCCESSFUL`; `HealthControllerTest` reports `tests="1" ... failures="0" errors="0"`. Reports at `build/reports/tests/test/index.html` and `build/reports/jacoco/test/html/index.html`.

```
> Task :test
> Task :jacocoTestReport
BUILD SUCCESSFUL in 7s
```

```
TEST-com.ensemble.health.HealthControllerTest.xml:
tests="1" skipped="0" failures="0" errors="0"
```

## Artifact: Runtime health check

**What it proves:** The running application serves `GET /api/health` over HTTP with the expected status and body.

**Why it matters:** This is the end-to-end proof that the backend boots and the API is reachable (Unit 1 FRs).

**Commands:**

```bash
./gradlew bootRun          # started in background
curl -s -w "%{http_code}" http://localhost:8080/api/health
```

**Result summary:** The endpoint returned HTTP `200` with body `{"status":"ok"}`.

```
HTTP status: 200
Body: {"status":"ok"}
```

## Reviewer Conclusion

The backend baseline works end-to-end: `/api/health` was built test-first (RED→GREEN), passes automated tests with coverage wired, and returns `200 {"status":"ok"}` from the running app. Ready for the frontend slice (task 2.0).
