# Task 01 Proofs — Persistence foundation (AWS SDK v2 + DynamoDB Local wiring)

## Task Summary

This task stands up the persistence plumbing every later wardrobe task builds
on: the AWS SDK v2 DynamoDB Enhanced Client wired against DynamoDB Local, a
`docker compose` service for the local DB, and a startup runner that
auto-creates the table if absent. It proves the app can connect to DynamoDB
Local and get a ready-to-use table with no manual step — while keeping the
existing skeleton tests green.

## What This Task Proves

- The AWS SDK v2 (`dynamodb-enhanced`) + TestContainers + Thumbnailator
  dependencies resolve and compile on the Boot 4.1 / Java 21 / Gradle 9 build.
- The `DynamoDbTableInitializer` creates the table against a real DynamoDB Local
  (TestContainers) and is idempotent on a second run.
- A full `bootRun` against the `docker compose` DynamoDB Local connects and
  auto-creates the `ensemble-items` table on startup.
- The new persistence beans do **not** break the existing `@SpringBootTest`
  context test — the auto-create runner is disabled in tests (audit FLAG
  addressed).

## Evidence Summary

- `./gradlew test -PskipFrontend` is green: 13 tests across 5 suites, 0 failures,
  including the DynamoDB Local IT and the unchanged skeleton tests.
- `docker compose up -d dynamodb` starts `ensemble-dynamodb` and it is `Up`.
- `bootRun` logs `Creating DynamoDB table 'ensemble-items'` → `created`, and
  `GET /api/health` returns `{"status":"ok"}`.

## Artifact: Full test suite (incl. DynamoDB Local IT), no regression

**What it proves:** Dependencies compile; the table bootstrap works against real
DynamoDB Local; the skeleton tests still pass.

**Why it matters:** Confirms the foundation is correct and the audit's
regression FLAG (context-load test needing a live DB) is handled.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `BUILD SUCCESSFUL`. Per-suite counts below — note
`DynamoDbTableInitializerIT` ran 2 tests (17.8s, real container) and
`EnsembleApplicationTests` (full `@SpringBootTest` context) passed with the new
beans present.

```
com.ensemble.config.DynamoDbTableInitializerIT   tests=2 fail=0 err=0 skip=0  (17.8s)
com.ensemble.EnsembleApplicationTests            tests=1 fail=0 err=0 skip=0
com.ensemble.health.HealthControllerTest         tests=1 fail=0 err=0 skip=0
com.ensemble.web.SpaForwardingTest               tests=5 fail=0 err=0 skip=0
com.ensemble.web.SpaPathResourceResolverTest     tests=4 fail=0 err=0 skip=0
```

## Artifact: DynamoDB Local via docker compose

**What it proves:** The local data dependency runs from the committed
`docker-compose.yml`.

**Why it matters:** A new developer can start the DB with one command; this is
the environment `bootRun` and the task-05 e2e flow use.

**Command:**

```bash
docker compose up -d dynamodb
docker compose ps
```

**Result summary:** `ensemble-dynamodb` (`amazon/dynamodb-local`) is `Up`, port
`8000` published.

```
NAME                IMAGE                          SERVICE    STATUS         PORTS
ensemble-dynamodb   amazon/dynamodb-local:latest   dynamodb   Up             0.0.0.0:8000->8000/tcp
```

## Artifact: bootRun auto-creates the table + health OK

**What it proves:** The Enhanced Client connects to DynamoDB Local on real boot
and the startup runner creates the table.

**Why it matters:** This is the end-to-end runtime proof of the foundation
(client config + table bootstrap), not just an isolated test.

**Command:**

```bash
docker compose up -d dynamodb
./gradlew bootRun -PskipFrontend      # (backgrounded)
curl -s http://localhost:8080/api/health
```

**Result summary:** Health returned `{"status":"ok"}`; the startup log shows the
table being created.

```
Started EnsembleApplication in 1.35 seconds
c.e.config.DynamoDbTableInitializer : Creating DynamoDB table 'ensemble-items'
c.e.config.DynamoDbTableInitializer : DynamoDB table 'ensemble-items' created
```

```
GET /api/health -> {"status":"ok"}
```

## Artifact: Configuration diffs

**What it proves:** The build and config carry the DynamoDB Local settings and
local-data hygiene.

**Why it matters:** Documents exactly what changed to enable persistence.

**Result summary:**

- `build.gradle`: adds `software.amazon.awssdk:bom:2.30.0` + `dynamodb-enhanced`,
  `net.coobird:thumbnailator:0.4.20`, and TestContainers (`junit-jupiter`).
- `application.yml`: adds `ensemble.dynamodb.{endpoint,region,table-name,auto-create-table}`,
  `ensemble.photos.dir`, and a multipart upload-size cap.
- `src/test/resources/application.yml`: sets `auto-create-table: false` so tests
  need no live DynamoDB.
- `.gitignore`: adds `data/` (local photos + DynamoDB Local data).

## Reviewer Conclusion

The persistence foundation is in place and verified end to end: dependencies
build, the table auto-creates against real DynamoDB Local (both via
TestContainers and a live `bootRun`), and the existing skeleton tests remain
green with the auto-create runner safely disabled in tests.
