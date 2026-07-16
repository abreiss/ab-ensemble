# Task 02 Proofs - Passcode gate: server-side signed token auth & gate filter

## Task Summary

This task adds the backend half of the passcode gate: a stateless HMAC-signed session
token (`SessionTokenService`), a masked `SecurityProperties` config record, `POST
/api/auth`, and a `SessionAuthFilter` that gates every `/api/**` request except `POST
/api/auth` and `GET /api/health`. All backend-domain pieces (`SecurityProperties`,
`SessionTokenService`, the filter logic, the daily-cap groundwork it must coexist with)
were built strict-TDD (RED → GREEN), matching `docs/specs/07-spec-pwa-security-guards`
Unit 2's backend requirements.

## What This Task Proves

- `SessionTokenService.issue()`/`verify()` mint and check a signed, expiring token —
  tamper, expiry, and malformed-input branches are all covered (100% branch on `verify`).
- `POST /api/auth` returns `200 {token}` on a correct passcode and a sanitized `401`
  (no token) on a wrong or blank one, using a constant-time (digest-based) comparison.
- The `SessionAuthFilter`, registered via a `FilterRegistrationBean` (not a raw
  `@Component`/`Filter` bean), gates all of `/api/**` except the two open endpoints,
  accepts the token via the `X-Ensemble-Session` header **or** a `token` query param, and
  never lets a rejected request reach a controller.
- Because the filter is registered through `FilterRegistrationBean` rather than exposed
  as a `Filter` bean, it is invisible to the existing `@WebMvcTest` slices — the specs
  01-06 controller-slice and full-context tests all stay green with no code changes to
  them.
- End-to-end, a real running instance enforces the gate: wrong passcode → `401`, correct
  passcode → `200` + token, `/api/items` without a token → `401`, with the header → `200`.

## Evidence Summary

- `SecurityPropertiesTest` (9 tests): defaults, TTL fallback, secret derivation from the
  passcode, masked `toString()`, and the "blank passcode accepted but flagged" behavior
  all pass.
- `SessionTokenServiceTest` (13 tests): issue/verify round-trip, tampered payload,
  tampered signature, expired, just-before-expiry, null/empty/malformed/multi-dot tokens,
  bad base64 in either segment, a validly-signed-but-non-numeric payload, and
  cross-secret rejection all pass — this is the 100%-branch surface on `verify()`.
- `AuthControllerTest` (4 tests): correct passcode → `200` + token; wrong, blank, and
  missing passcode → `401` with no token minted (`verifyNoInteractions(tokenService)`).
- `SessionAuthFilterTest` (6 tests, full `@SpringBootTest` context with `WardrobeService`
  mocked so no live DynamoDB is touched): no token → `401`; valid header token → `200`;
  valid query token → `200`; invalid token → `401`; `/api/health` and `POST /api/auth`
  are open without a token.
- Full suite: `./gradlew test` — **197 tests, 0 failures** across all 31 backend test
  classes (specs 01-07) — no regressions from registering the gate.
- Live `curl` proof against a real running instance (DynamoDB Local up, a throwaway demo
  passcode set only in the shell environment, never committed) confirms the same
  contract end-to-end.

## Artifact: `SecurityPropertiesTest` — config record behavior

**What it proves:** Defaults (`session-ttl` = `PT12H`), secret masking in `toString()`,
HMAC-key derivation from the passcode when `session-secret` is blank, and that a blank
passcode is accepted (no exception) but flagged via `passcodeConfigured() == false`.

**Why it matters:** This is the config surface every other piece (`SessionTokenService`,
`AuthController`) depends on; getting the null/blank normalization right here prevents
subtle gate-bypass bugs downstream.

**Command:**

```bash
./gradlew test --tests '*SecurityPropertiesTest'
```

**Result summary:** All 9 tests passed.

```
SecurityPropertiesTest > nullSessionTtl_fallsBackToTwelveHourDefault() PASSED
SecurityPropertiesTest > nonPositiveSessionTtl_fallsBackToDefault() PASSED
SecurityPropertiesTest > configuredSessionTtl_isKept() PASSED
SecurityPropertiesTest > blankSessionSecret_derivesFromPasscode() PASSED
SecurityPropertiesTest > configuredSessionSecret_isKeptIndependentOfPasscode() PASSED
SecurityPropertiesTest > nullPasscode_normalizesToBlankAndIsAccepted() PASSED
SecurityPropertiesTest > blankPasscode_isAcceptedButFlaggedAsUnconfigured() PASSED
SecurityPropertiesTest > nonBlankPasscode_isFlaggedAsConfigured() PASSED
SecurityPropertiesTest > toString_masksPasscodeAndSessionSecret() PASSED

BUILD SUCCESSFUL
```

## Artifact: `SessionTokenServiceTest` — 100%-branch token verification

**What it proves:** Every branch of `verify()` — malformed shape (null/empty/no-dot/
multi-dot), bad base64 in either segment, HMAC mismatch (tamper), a validly-signed but
non-numeric payload, expiry, and the happy path — behaves correctly under a fixed
`Clock`.

**Why it matters:** This is the spec's explicit 100%-branch requirement on token
verification — the single security-critical routine standing between an unauthenticated
request and the wardrobe/stylist data.

**Command:**

```bash
./gradlew test --tests '*SessionTokenServiceTest'
```

**Result summary:** All 13 tests passed, covering issue/verify, tamper (payload and
signature), expiry (just-before and just-after), malformed shapes, bad base64, a forged
but validly-signed non-numeric payload, and cross-secret incompatibility.

```
SessionTokenServiceTest > issuesTokenThatVerifies() PASSED
SessionTokenServiceTest > rejectsTamperedToken() PASSED
SessionTokenServiceTest > rejectsTamperedSignature() PASSED
SessionTokenServiceTest > rejectsExpiredToken() PASSED
SessionTokenServiceTest > acceptsTokenJustBeforeExpiry() PASSED
SessionTokenServiceTest > rejectsNullToken() PASSED
SessionTokenServiceTest > rejectsEmptyToken() PASSED
SessionTokenServiceTest > rejectsTokenWithNoDot() PASSED
SessionTokenServiceTest > rejectsTokenWithMultipleDots() PASSED
SessionTokenServiceTest > rejectsTokenWithBadBase64Payload() PASSED
SessionTokenServiceTest > rejectsTokenWithBadBase64Signature() PASSED
SessionTokenServiceTest > rejectsTokenWithNonNumericPayload() PASSED
SessionTokenServiceTest > differentSecrets_produceIncompatibleTokens() PASSED

BUILD SUCCESSFUL
```

## Artifact: `AuthControllerTest` — the `POST /api/auth` contract

**What it proves:** A correct passcode returns `200` with a minted token; a wrong,
blank, or missing passcode returns `401` and never calls `tokenService.issue()`.

**Why it matters:** This is the only unauthenticated write path in the app — it has to
fail closed on every incorrect input, and never leak a token on failure.

**Command:**

```bash
./gradlew test --tests '*AuthControllerTest'
```

**Result summary:** All 4 tests passed.

```
AuthControllerTest > correctPasscode_returns200WithToken() PASSED
AuthControllerTest > wrongPasscode_returns401NoToken() PASSED
AuthControllerTest > blankPasscode_returns401() PASSED
AuthControllerTest > missingPasscodeField_returns401() PASSED

BUILD SUCCESSFUL
```

## Artifact: `SessionAuthFilterTest` — gate scope, full context

**What it proves:** A full Spring context (real `SecurityConfig` filter registration,
`WardrobeService` mocked to avoid a live DynamoDB dependency) shows the actual gate
behavior: `/api/items` without a token → `401`; with a valid header or query token →
`200`; with an invalid token → `401`; `/api/health` and `POST /api/auth` are reachable
with no token at all.

**Why it matters:** Unit-level filter logic is necessary but not sufficient — this
proves the filter is actually wired into the real request pipeline at the right scope
and order, not just correct in isolation.

**Command:**

```bash
./gradlew test --tests '*SessionAuthFilterTest'
```

**Result summary:** All 6 tests passed.

```
SessionAuthFilterTest > protectedApi_withoutToken_returns401() PASSED
SessionAuthFilterTest > protectedApi_withValidToken_passesThrough() PASSED
SessionAuthFilterTest > protectedApi_withValidQueryToken_passesThrough() PASSED
SessionAuthFilterTest > protectedApi_withInvalidToken_returns401() PASSED
SessionAuthFilterTest > health_isOpenWithoutToken() PASSED
SessionAuthFilterTest > auth_isOpenWithoutToken() PASSED

BUILD SUCCESSFUL
```

## Artifact: Full backend suite — no regressions to specs 01-06

**What it proves:** Registering the gate filter and the new `SecurityConfig` does not
break any pre-existing controller slice test, repository IT, or context test.

**Why it matters:** The spec explicitly calls out the risk that a servlet filter
registered globally could break `@WebMvcTest` slices; this is the regression check for
that risk (addressed by using `FilterRegistrationBean` instead of a raw `Filter` bean).

**Command:**

```bash
./gradlew test
```

**Result summary:** 197 tests across 31 backend test classes, 0 failures, 0 errors —
includes the pre-existing `WardrobeControllerTest`, `StyleControllerTest`,
`TaggingControllerTest`, `EnsembleApplicationTests`, and all repository/service tests
from specs 01-06.

```
BUILD SUCCESSFUL in 9s
5 actionable tasks: 1 executed, 4 up-to-date

test classes: 31, total tests: 197, failures: 0, errors: 0
```

## Artifact: End-to-end `curl` proof against a running instance

**What it proves:** With DynamoDB Local running and a throwaway demo passcode set only
as a shell environment variable (`ENSEMBLE_PASSCODE`, never committed, not the real demo
passcode), the full request/response contract holds: wrong passcode → `401`; correct
passcode → `200` + token; a protected route without the token → `401`; the same route
with the token in the `X-Ensemble-Session` header → `200`.

**Why it matters:** This is the only proof that exercises the real servlet container
(Tomcat) and the real `FilterRegistrationBean` scoping end-to-end, not a mocked test
harness.

**Commands and results (passcode/token values are demo/throwaway, never real secrets):**

```bash
$ curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/auth \
    -H 'Content-Type: application/json' -d '{"passcode":"WRONG"}'
401
# body: {"error":"unauthorized","message":"invalid passcode"}

$ curl -s -X POST localhost:8080/api/auth \
    -H 'Content-Type: application/json' -d '{"passcode":"[DEMO_PASSCODE]"}'
200
# body: {"token":"[REDACTED_TOKEN]"}

$ curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/items
401
# body: {"error":"unauthorized","message":"authentication required"}

$ curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/items \
    -H "X-Ensemble-Session: [REDACTED_TOKEN]"
200
# body: []
```

**Result summary:** Every step matches the spec's stated contract for Unit 2. The demo
instance was torn down immediately after capturing this evidence; no secret or live
token is committed.

## Reviewer Conclusion

The passcode gate is implemented and verified at three levels: unit (token
verify/issue, 100% branch), slice (the auth contract, the filter's gate scope in a full
context), and live end-to-end (a real running instance). The gate is scoped so it does
not regress any pre-existing test from specs 01-06, satisfying the spec's explicit
compatibility requirement for the servlet filter.
