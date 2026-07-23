# Task 03 Proofs — Sign-up endpoint `POST /api/accounts` behind the signup passcode (auto-login)

## Task Summary

This task adds **invite-only sign-up**. `POST /api/accounts` accepts `{ email, password, passcode }`,
validates the input on bind (`@Email`, password **8–72 UTF-8 bytes**, non-blank passcode), verifies
the shared **signup passcode** with a constant-time SHA-256 compare (`SignupPasscodeVerifier`,
factored out of the retired shared-passcode login), hashes the password with bcrypt, and creates the
account with the repository's atomic `attribute_not_exists(email)` guard. On success it
**auto-logs the user in** — minting a `userId`-bearing session token (Task 2.0's
`SessionTokenService.issue(userId)`) and returning `201 { token }`. The filter's open-route list is
extended so the endpoint is reachable token-free, and the shared `ApiExceptionHandler` now maps a
duplicate email to `409`.

## What This Task Proves

- **Invite gate, checked first (100% branch).** `SignupPasscodeVerifier.matches` matches a correct
  candidate, rejects a wrong one, rejects null/blank, and treats a **blank configured passcode as
  "signup closed"** (no candidate can match). A wrong passcode returns `401` and **creates no user** —
  the check runs before any hashing or persistence.
- **72-byte password ceiling (100% branch).** The custom `@MaxUtf8Bytes` constraint counts UTF-8
  **bytes**, not characters, so a multi-byte password can never be silently truncated past bcrypt's
  72-byte input limit; null defers to `@NotBlank`.
- **Signup contract.** Valid input → `201` + an auto-login token carrying the new `userId`; duplicate
  email → `409 ("conflict","email already registered")`; short/blank password, invalid email, and
  over-72-byte password → the shared sanitized `400`.
- **Password safety.** The persisted `User` carries the **bcrypt hash**, never the raw password; the
  raw value is never logged.
- **No regressions.** The full backend suite (367 tests) is green; `POST /api/accounts` joins
  `POST /api/auth` and `GET /api/health` as a token-free entry point without loosening any other route.

## Evidence Summary

- `./gradlew test -PskipFrontend` → **BUILD SUCCESSFUL**, **367 tests, 0 failures, 0 errors**.
- JaCoCo branch coverage on the two mandated critical-logic paths: **`SignupPasscodeVerifier` branch
  6/6 = 100%** (signup-passcode check) and **`MaxUtf8BytesValidator` branch 4/4 = 100%** (password
  length). `AccountController` is 100% line + branch; `SignupRequest` 100% line.
- Live end-to-end round-trip against DynamoDB Local (throwaway instance on port 8081, isolated
  `ensemble-users-proof14` table, throwaway creds): signup `201` → duplicate `409` → wrong passcode
  `401` (no user created) → short password `400` → login `200` → `GET /api/me` returns the new
  `userId`/email → wrong password `401` generic.

## Artifact: Signup + validation + gate-scope test suites

**What it proves:** The signup-passcode branches, the 72-byte password guard, the six-case signup
contract, and the extended open-route scope all behave per spec Unit 2.

**Why it matters:** These are the backend-domain behaviors under strict TDD (with the mandated
100%-branch critical logic) that gate account creation and the AI-cost blast radius.

**Command:**

```bash
./gradlew test -PskipFrontend \
  --tests 'com.ensemble.security.SignupPasscodeVerifierTest' \
  --tests 'com.ensemble.user.web.MaxUtf8BytesValidatorTest' \
  --tests 'com.ensemble.user.web.AccountControllerTest' \
  --tests 'com.ensemble.security.web.SessionAuthFilterTest'
```

**Result summary:** All green. `SignupPasscodeVerifierTest` (4), `MaxUtf8BytesValidatorTest` (4), and
`AccountControllerTest` (6) cover the backend-domain logic; `SessionAuthFilterTest`'s new
`accounts_isOpen` case proves the endpoint passes the gate in a full application context.

## Artifact: JaCoCo — 100% branch on the critical logic

**What it proves:** The signup-passcode check and the password-length validator each hit every
decision branch, meeting the `docs/TESTING.md` 100%-branch bar for critical logic.

**Why it matters:** These two gates decide who may register and bound the password bcrypt hashes,
so a missed branch is a real security/robustness gap.

**Command:**

```bash
./gradlew test -PskipFrontend jacocoTestReport
# parsed from build/reports/jacoco/test/jacocoTestReport.xml
```

**Result summary:**

```
AccountController        LINE 15/15 (100%)   BRANCH 2/2 (100%)
MaxUtf8BytesValidator    LINE 6/6 (100%)     BRANCH 4/4 (100%)
SignupRequest            LINE 1/1 (100%)     BRANCH n/a
SignupPasscodeVerifier   LINE 9/11 (81%)     BRANCH 6/6 (100%)
```

`SignupPasscodeVerifier` line coverage is 81% only because of the unreachable
`catch (NoSuchAlgorithmException)` in `sha256(...)` — SHA-256 is always present in the JVM — the same
defensive pattern the retired `AuthController.sha256` carried. All decision branches (the critical
logic) are covered.

## Artifact: Live end-to-end sign-up + auto-login round-trip

**What it proves:** Sign-up, the duplicate/invite/validation rejections, auto-login, and principal
resolution all work end-to-end against real DynamoDB Local, not just against mocks.

**Why it matters:** The mock-seam unit tests prove *our handling*; this proves the wired app — bcrypt,
the conditional-put uniqueness guard, token issue, and the filter — actually behaves as specified.

**Command** (throwaway instance; `<throwaway-signup-code>` / `<throwaway-password>` were disposable
demo values, and the instance + its isolated `ensemble-users-proof14` table were torn down after):

```bash
SERVER_PORT=8081 ENSEMBLE_PASSCODE='<throwaway-signup-code>' \
  ENSEMBLE_USERS_TABLE_NAME='ensemble-users-proof14' ./gradlew bootRun -PskipFrontend &
BASE=http://localhost:8081
# 1) valid signup   2) same email   3) wrong passcode   4a) short password
# 4b) login         5) /api/me      6) wrong password
```

**Result summary:** Every status code matched the contract; the `/api/me` `userId` equals the id
embedded in the auto-login token, confirming the token carries the new account's identity.

```
== 1. signup (valid) -> 201 + token ==
{"token":"Y2FlM2RjYjIt…"}                                   HTTP 201

== 2. signup (same email again) -> 409 ==
{"error":"conflict","message":"email already registered"}   HTTP 409

== 3. signup (wrong passcode, new email) -> 401, no user created ==
{"error":"unauthorized","message":"invalid passcode"}        HTTP 401

== 4a. signup (short password) -> 400 ==
{"error":"bad_request","message":"invalid request"}          HTTP 400

== 4b. login with created creds -> 200 + token (auto-login round-trip) ==
{"token":"Y2FlM2RjYjIt…"}                                    (login OK)

== 5. GET /api/me with token -> {userId,email} ==
{"userId":"cae3dcb2-b39f-4c91-ba99-59707c4fa391","email":"proof-demo@example.com"}  HTTP 200

== 6. login with wrong password -> 401 generic ==
{"error":"unauthorized","message":"invalid email or password"}  HTTP 401
```

## Reviewer Conclusion

`POST /api/accounts` is a spec-compliant, invite-only sign-up: bounded input, a constant-time
signup-passcode gate that closes when unconfigured and creates no user on failure, a byte-accurate
72-byte password ceiling, an atomic duplicate-email `409`, and `201` auto-login carrying the new
`userId` — all covered by strict-TDD unit tests (100% branch on the two critical gates) and confirmed
end-to-end against DynamoDB Local. No secrets are committed; all demo values are throwaway.
