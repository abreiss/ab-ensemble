# Task 01 Proofs - Backend cloud-readiness: `S3PhotoStorage`, storage-backend selector, cloud DynamoDB config

## Task Summary

This task makes the same jar runnable against real AWS or local infrastructure, selectable purely
by configuration. It adds `S3PhotoStorage` behind the existing `PhotoStorage` interface, a
`@ConditionalOnProperty` bean selector that guarantees exactly one `PhotoStorage` bean is ever
registered, and a `DynamoDbConfig` that drops the always-on endpoint override + dummy static
credentials when `ensemble.dynamodb.endpoint` is blank, falling back to the default AWS credential
provider chain (the App Runner instance role at deploy).

## What This Task Proves

- `S3PhotoStorage` satisfies the `PhotoStorage` contract (save/load/delete) against a mocked
  `S3Client` — no live AWS network call in tests — and reuses the same `ImageProcessor`
  compression as the disk implementation.
- Exactly one `PhotoStorage` bean exists for every value of `ensemble.photos.backend`: the
  unset/`disk` default keeps `LocalDiskPhotoStorage`; `s3` swaps in `S3PhotoStorage`. Never zero,
  never two.
- `DynamoDbConfig` branches correctly on whether `ensemble.dynamodb.endpoint` is set: blank/absent
  uses the default credential chain with no endpoint override (real AWS); present overrides the
  endpoint and uses static dummy credentials (DynamoDB Local), exactly as before.
- Local `./gradlew bootRun` and the full pre-existing test suite behave exactly as before — this
  is a config-only cloud-enablement, not a behavior change for local dev.

## Evidence Summary

- `./gradlew test jacocoTestReport -PskipFrontend` is green: **261 tests, 0 failures, 0 errors**.
- JaCoCo shows **100% line and 100% branch coverage** on every class this task touched
  (`DynamoDbConfig`, `PhotoProperties`, `StorageConfig`, `S3PhotoStorage`,
  `LocalDiskPhotoStorage`) — comfortably inside the ≥90% line / 100% branch gate on critical
  selector + DynamoDB logic.
- A live `bootRun` smoke check confirms local dev is unaffected: the app starts in disk mode,
  finds the existing DynamoDB Local table, and `/api/health` returns `200 {"status":"ok"}`.

## Artifact: RED — `S3PhotoStorageTest` fails before `S3PhotoStorage` exists

**What it proves:** the test was written before the implementation (strict TDD), and fails for
the right reason — the class does not exist yet.

**Why it matters:** demonstrates the RED phase actually ran, not just that the final suite is
green.

**Command:**

```bash
./gradlew compileTestJava -PskipFrontend
```

**Result summary:** compilation fails with `cannot find symbol: class S3PhotoStorage` at both
call sites in the test — confirming RED.

```
/…/src/test/java/com/ensemble/storage/S3PhotoStorageTest.java:54: error: cannot find symbol
	private S3PhotoStorage storage;
	        ^
  symbol:   class S3PhotoStorage
  location: class S3PhotoStorageTest
/…/src/test/java/com/ensemble/storage/S3PhotoStorageTest.java:60: error: cannot find symbol
		storage = new S3PhotoStorage(s3Client, props, new ImageProcessor(props));
		              ^
  symbol:   class S3PhotoStorage
  location: class S3PhotoStorageTest
2 errors
```

## Artifact: RED — `DynamoDbConfigTest` fails against the always-override implementation

**What it proves:** before the branch was introduced, a blank/absent endpoint still forced an
`endpointOverride` (or threw `NullPointerException` on a null endpoint) and always used static
dummy credentials — exactly the bug this task fixes.

**Why it matters:** proves the cloud-readiness gap was real, not hypothetical, and that the new
test would have caught it.

**Command:**

```bash
./gradlew test -PskipFrontend --tests "com.ensemble.config.DynamoDbConfigTest"
```

**Result summary:** 2 of 3 tests failed with `NullPointerException` (blank/absent endpoint cases);
only the present-endpoint case passed, matching the pre-existing behavior.

```
DynamoDbConfigTest > blankEndpoint_usesDefaultCredentialsWithNoOverride() FAILED
    java.lang.NullPointerException at DynamoDbConfigTest.java:31

DynamoDbConfigTest > absentEndpoint_usesDefaultCredentialsWithNoOverride() FAILED
    java.lang.NullPointerException at DynamoDbConfigTest.java:42

3 tests completed, 2 failed
```

## Artifact: GREEN — full backend suite + JaCoCo coverage

**What it proves:** after implementing `S3PhotoStorage`, the bean selector, and the
`DynamoDbConfig` branch, every test in the repository passes, including the two new RED tests
above and all pre-existing tests (the storage-bean conditional change touches a path every
existing test relies on).

**Why it matters:** this is the strict-TDD gate required by the task (Success Metric 1) and the
regression check flagged as a risk in the planning audit.

**Command:**

```bash
./gradlew test jacocoTestReport -PskipFrontend
```

**Result summary:** `BUILD SUCCESSFUL`, 261 tests / 0 failures / 0 errors. JaCoCo report at
`build/reports/jacoco/test/html/index.html`; per-class coverage on the classes this task touched:

| Class | Line coverage | Branch coverage |
| --- | --- | --- |
| `DynamoDbConfig` | 12/12 (100%) | 6/6 (100%) |
| `PhotoProperties` | 8/8 (100%) | 8/8 (100%) |
| `StorageConfig` | 5/5 (100%) | n/a (no branches) |
| `S3PhotoStorage` | 26/26 (100%) | n/a (no branches) |
| `LocalDiskPhotoStorage` | 23/23 (100%) | 4/4 (100%) |

Project-wide totals (all 57 classes): 733/755 lines (97.1%), 218/228 branches (95.6%) — well above
the ≥90% line gate.

## Artifact: `PhotoStorageSelectorTest` — exactly one `PhotoStorage` bean per config

**What it proves:** an `ApplicationContextRunner` test exercises both conditional branches —
unset/`disk` backend registers only `LocalDiskPhotoStorage`; `s3` registers only `S3PhotoStorage`
(with its `S3Client` bean built only in that mode) — and the bean count is asserted to be exactly
1 in every case, ruling out both the zero-bean and two-bean failure modes.

**Why it matters:** this is the regression-risk the audit flagged (converting
`LocalDiskPhotoStorage` from unconditional `@Component` to conditional could silently drop the
bean or, worse, leave two competing beans that only fail at runtime for any autowiring consumer).

**Command:**

```bash
./gradlew test -PskipFrontend --tests "com.ensemble.config.PhotoStorageSelectorTest"
```

**Result summary:** all 3 scenarios pass — `unsetBackend_registersOnlyLocalDiskPhotoStorage`,
`diskBackend_registersOnlyLocalDiskPhotoStorage`, `s3Backend_registersOnlyS3PhotoStorage`.

```
<testsuite name="com.ensemble.config.PhotoStorageSelectorTest" tests="3" skipped="0" failures="0" errors="0" .../>
  <testcase name="s3Backend_registersOnlyS3PhotoStorage()" .../>
  <testcase name="unsetBackend_registersOnlyLocalDiskPhotoStorage()" .../>
  <testcase name="diskBackend_registersOnlyLocalDiskPhotoStorage()" .../>
```

## Artifact: local dev is unaffected — `bootRun` smoke check

**What it proves:** with no env vars set (disk defaults), the app starts exactly as before:
Tomcat on 8080, the existing DynamoDB Local table is detected, and the health endpoint responds.

**Why it matters:** the task's core constraint is that this is a config-only cloud-enablement —
zero behavior change for local dev.

**Command:**

```bash
./gradlew bootRun -PskipFrontend &
curl -s -w "\nHTTP_STATUS:%{http_code}\n" localhost:8080/api/health
```

**Result summary:** `200 {"status":"ok"}`; startup log shows disk-mode boot with no errors.

```
{"status":"ok"}
HTTP_STATUS:200

...
Started EnsembleApplication in 0.774 seconds (process running for 0.883)
c.e.config.DynamoDbTableInitializer      : DynamoDB table 'ensemble-items' already exists
```

## Artifact: config-only cloud enablement (diff excerpt)

**What it proves:** the S3 SDK dependency and the new config keys are added without changing any
existing default behavior — `backend` defaults to `disk`, `endpoint` still defaults to
`http://localhost:8000`, `table-name` still defaults to `ensemble-items`.

**Why it matters:** demonstrates the deploy switch is achieved through configuration, not a
rewrite, per the `PhotoStorage` interface's original design intent.

**`build.gradle`:**

```groovy
implementation platform('software.amazon.awssdk:bom:2.30.0')
implementation 'software.amazon.awssdk:dynamodb-enhanced'
// S3 — deploy-time PhotoStorage backend (S3PhotoStorage), selected via
// ensemble.photos.backend=s3; disk remains the local-dev default.
implementation 'software.amazon.awssdk:s3'
```

**`src/main/resources/application.yml`:**

```yaml
ensemble:
  dynamodb:
    endpoint: ${ENSEMBLE_DYNAMODB_ENDPOINT:http://localhost:8000}
    region: us-east-1
    table-name: ${ENSEMBLE_DYNAMODB_TABLE_NAME:ensemble-items}
    auto-create-table: true
  photos:
    dir: ./data/photos
    max-upload-pixels: 50000000
    backend: ${ENSEMBLE_PHOTOS_BACKEND:disk}
    s3:
      bucket: ${ENSEMBLE_PHOTOS_S3_BUCKET:}
```

## Reviewer Conclusion

The evidence shows strict TDD ran (RED confirmed before GREEN for both `S3PhotoStorageTest` and
`DynamoDbConfigTest`), the full pre-existing suite plus new tests are green with 100% line/branch
coverage on every touched class, the bean selector provably yields exactly one `PhotoStorage` bean
per configuration, and a live `bootRun` confirms local dev behavior is unchanged. The jar is now
switchable between local disk/DynamoDB-Local and real S3/DynamoDB purely via environment
variables, satisfying Success Metric 1 and the FRs for `S3PhotoStorage`, the backend selector, and
the `DynamoDbConfig` cloud switch.
