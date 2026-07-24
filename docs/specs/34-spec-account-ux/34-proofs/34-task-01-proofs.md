# Task 01 Proofs — Username-based accounts (backend)

## Task Summary

This task replaces **email with username** across the entire account domain — the `User`
DynamoDB partition key, `UserRepository`, DTO validation, both auth controllers, exceptions,
the startup seed, the table initializer, and `GET /api/me`. Email is removed entirely. The
invite passcode, bcrypt hashing, the dummy-hash **timing defense**, the non-enumerating `401`,
the duplicate `409`, and the opaque-`userId` HMAC session token are all preserved unchanged.
Delivered under strict TDD (RED → GREEN → REFACTOR).

## What This Task Proves

- Signup (`POST /api/accounts`) accepts `{ username, password, passcode }`, normalizes the
  username (case/space-insensitive), and returns `201` + an auto-login token.
- Username uniqueness is **atomic** (`attribute_not_exists(username)` conditional put): a
  duplicate returns `409 "username already registered"` without overwriting the first row.
- Login (`POST /api/auth`) is **non-enumerating**: unknown username and wrong password return
  one identical `401 "invalid username or password"`, and the dummy-hash timing path still runs
  on the unknown-username branch (`verify(passwordHasher).matches(...)`).
- Bean-Validation rejects every bad username (blank, `<3`, `>30`, illegal char, leading/trailing
  separator) with the shared sanitized `400 "invalid request"` **before** any persistence.
- `GET /api/me` now returns `{ userId, username }` (no `email`); an existing valid token still
  resolves (token contract unchanged).
- The account seed is keyed on username (`ENSEMBLE_SEED_USERNAME`), idempotent via
  `findByUsername`, and bypasses the passcode.
- The `ensemble-users` table partition key is `"username"` (still GSI-free).

## Evidence Summary

- Full backend suite green: **427 tests, 0 failures, 0 errors, 0 skipped** (58 classes),
  including DynamoDB-Local TestContainers ITs.
- JaCoCo **100% branch** on all four critical areas: login non-enumeration (`AuthController`
  6/6), duplicate-username conditional (`UserRepository` 2/2), seed idempotency
  (`SeedAccountRunner` 4/4, `SeedProperties` 10/10).
- End-to-end `curl` transcript against a live backend proves the wire behavior for signup,
  duplicate, login, wrong-password, unknown-username, `/api/me`, invalid-username, and
  wrong-passcode paths — all with synthetic credentials.
- Grep confirms no residual `email` in the production account path; migration-safety confirmed
  (no automatic table-drop / enumerate-and-delete code added).

---

## Artifact: Full backend test suite

**What it proves:** Every account behavior (model, repository IT, both controllers, seed,
`/api/me`, table key) passes, and no pre-existing suite regressed.

**Why it matters:** The MockMvc controller/IT tests are the acceptance evidence for the
non-enumeration, duplicate-uniqueness, and validation-branch requirements.

**Command:** `./gradlew test jacocoTestReport -PskipFrontend`

**Result summary:** `BUILD SUCCESSFUL`. Aggregated across all 58 result XMLs:
**tests=427 failures=0 errors=0 skipped=0**. TestContainers ITs executed (`UserRepositoryIT`,
`DynamoDbTableInitializerIT`, plus the outfit/wardrobe/usage ITs).

```
> Task :test
BUILD SUCCESSFUL
tests=427 failures=0 errors=0 skipped=0 across 58 classes
```

## Artifact: JaCoCo branch coverage on critical logic

**What it proves:** The critical-logic 100%-branch bar (username validation, login
non-enumeration, duplicate-username conditional, seed idempotency) is met.

**Why it matters:** `AGENTS.md` / `docs/TESTING.md` mandate 100% branch on this logic; there is
no enforced JaCoCo gate in `build.gradle`/CI, so the bar is verified by reading the report.

**Artifact path:** `build/reports/jacoco/test/jacocoTestReport.xml`

**Result summary:** All four areas at 100% branch. The two validation DTOs are Java records
carrying only Jakarta annotations, so they compile to **no branch instructions** (line 100%);
their size/pattern/blank rejection branches live in the Bean-Validation framework and are fully
exercised via `AccountControllerTest.invalidUsername_returns400` (6 cases) + the password/passcode
`400` cases.

```
AuthController       branch=100% (6/6)    line=100%   # login non-enumeration
UserRepository       branch=100% (2/2)    line=100%   # duplicate-username conditional put
SeedAccountRunner    branch=100% (4/4)    line=100%   # seed idempotency
SeedProperties       branch=100% (10/10)  line=100%   # configured()/toString mask
User                 branch=100% (2/2)    line=100%   # normalizeUsername null-safe
AccountController     branch=100% (2/2)    line=100%
SignupRequest        no branches          line=100%   # record; validation branches in framework
AuthRequest          no branches          line=100%   # record; validation branches in framework
```

## Artifact: End-to-end curl transcript

**What it proves:** The real HTTP wire behavior of signup → duplicate → login → wrong-password →
unknown-username → `/api/me` → invalid-username → wrong-passcode.

**Why it matters:** Confirms the MockMvc contracts hold against a running Spring Boot process
backed by DynamoDB Local, end to end.

**Setup (non-destructive):** The dev `ensemble-users` table is email-keyed with live data, so —
per Resolved Decision D2 and the standing "only delete what I created" rule — the proof ran
against a **fresh, self-created `ensemble-users-proof34`** table (auto-created with the username
key on startup, matching the repo's existing `ensemble-users-proof14` precedent), then deleted.
The dev table was never touched. Tokens are truncated + `REDACTED`; all credentials synthetic.

**Commands & results:**

```
### 1) Signup (mixed-case "Jane_Doe" -> stored normalized) — expect 201 + token
HTTP 201    body: {"token":"OGJlYTRmMjct…REDACTED"}

### 2) Duplicate signup (same username) — expect 409
HTTP 409    body: {"error":"conflict","message":"username already registered"}

### 3) Login (normalized "jane_doe") — expect 200 + token
HTTP 200    body: {"token":"OGJlYTRmMjct…REDACTED"}

### 4) Wrong password — expect 401
HTTP 401    body: {"error":"unauthorized","message":"invalid username or password"}

### 5) Unknown username — expect IDENTICAL 401 (non-enumerating)
HTTP 401    body: {"error":"unauthorized","message":"invalid username or password"}

### 6) GET /api/me with login token — expect {userId, username}, no email
HTTP 200    body: {"userId":"8bea4f27-5601-4b60-be0e-c513dd73a84d","username":"jane_doe"}

### 7) Invalid username (leading separator "_bad") — expect 400 sanitized
HTTP 400    body: {"error":"bad_request","message":"invalid request"}

### 8) Wrong invite passcode — expect 401, no account created
HTTP 401    body: {"error":"unauthorized","message":"invalid passcode"}
```

Note steps 1 and 3 return the same token prefix and step 6 resolves the same `userId`,
confirming the mixed-case signup normalized to `jane_doe` and that one token round-trips.

## Artifact: Rename completeness + migration-safety grep

**What it proves:** No `email` remains in the production account path, and the destructive-migration
safety property (D2) holds — no auto-drop / enumerate-and-delete code was added.

**Why it matters:** Closes audit FLAG 2 (migration hygiene) by inspection, per the project's
testing split (migration/infra is not unit-tested).

**Commands:**

```
grep -rniE 'email|@Email|normalizeEmail|findByEmail|SEED_EMAIL' \
  src/main/java/com/ensemble/user src/main/java/com/ensemble/security \
  src/main/java/com/ensemble/config/SeedProperties.java src/main/resources/application.yml
# -> (none)

# Account test tree: only the intentional negative assertion remains
grep -rniE 'email' src/test/java/com/ensemble/user src/test/java/com/ensemble/security ...
# -> SessionAuthFilterTest.java:129: .andExpect(jsonPath("$.email").doesNotExist());

# DuplicateEmailException deleted; DuplicateUsernameException added
ls src/main/java/com/ensemble/user/Duplicate*.java
# -> src/main/java/com/ensemble/user/DuplicateUsernameException.java
```

**Result summary:** The only surviving reference to `email` in the account path is the
`$.email` **doesNotExist** assertion in `SessionAuthFilterTest` (proving the field is gone from
`/api/me`). No production code drops a table or enumerates-and-deletes rows; the local table
recreation is deferred to the operator-confirmed migration procedure in Task 2.0.

## Reviewer Conclusion

The backend account domain is username-based end to end: signup, atomic-unique storage,
non-enumerating login with the timing defense intact, validated input, the new `/api/me` shape,
and the username-keyed seed — all proven by 427 green tests, 100% branch coverage on the four
critical areas, and a live curl transcript. Email is fully removed; the switch is
token-compatible and migration-safe.
