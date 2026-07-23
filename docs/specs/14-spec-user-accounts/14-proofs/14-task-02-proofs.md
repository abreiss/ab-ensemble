# Task 02 Proofs — Identity-bearing session token, email/password login, resolved principal (`/api/me`)

## Task Summary

This task turns authentication into **identity**. `SessionTokenService` now signs a
`userId:expiry` payload (`issue(userId)` / `verify(token) → Optional<String>`), so a token
carries *who* the caller is (an opaque `userId`, no PII) as well as *until when* it is valid.
`POST /api/auth` is repurposed from a shared passcode to **email + password** login with a
**non-enumerating** generic `401` (a bcrypt comparison runs even for an unknown email, closing
the timing side-channel). `SessionAuthFilter` resolves the `userId` from a valid token and
exposes it as a request attribute, surfaced to controllers via a `@CurrentUserId` argument
resolver. The new **`GET /api/me`** returns `{ userId, email }` — the demoable proof that the
gate resolves a concrete principal end-to-end.

## What This Task Proves

- **Identity-bearing token, 100% branch on verify/parse.** A well-formed/untampered/unexpired
  token yields its `userId`; null, malformed, tampered, expired, non-numeric-expiry,
  empty-userId, and **old identity-less** (validly signed, no `userId:` prefix) tokens all yield
  `Optional.empty()`.
- **Non-enumerating login, 100% branch on the match/absent path.** Valid creds → `200 { token }`
  carrying the `userId`; unknown email **and** wrong password return the *identical* generic
  `401 ("invalid email or password")`; a bcrypt compare runs on the unknown-email path too.
- **Bounded input.** `AuthRequest{email,password}` is `@Valid` (`@NotBlank @Email` / `@NotBlank`);
  a blank/malformed body is rejected with the shared sanitized `400` before any repository/crypto
  work.
- **Resolved principal.** A valid token passes the gate with `ensemble.userId` set; `GET /api/me`
  reads it via `@CurrentUserId` and returns the caller's `{ userId, email }`; no token → `401`.
- **No regressions.** The full backend suite (352 tests) is green; the only `issue()`/`verify()`
  callers and the only `{passcode}`-shaped `AuthRequest` in the backend were rewritten (grep
  clean).

## Evidence Summary

- `./gradlew test -PskipFrontend` → **BUILD SUCCESSFUL**, **352 tests, 0 failures, 0 errors**.
- JaCoCo: **`SessionTokenService` branch 12/12 = 100%** (verify/parse) and **`AuthController`
  branch 6/6 = 100%** (login match/absent path) — the two critical-logic paths this task owns.
  `MeController`/`InvalidCredentialsException` are 100% line.
- Live end-to-end round-trip against DynamoDB Local (throwaway seeded row): login returns a token
  whose payload base64url-decodes to `demo-user-14:<expiry>`; `GET /api/me` with the header
  returns `{userId,email}`; without the header returns `401`; wrong-password and unknown-email
  return an **identical** generic `401`.

## Artifact: Token + login + `/api/me` test suites

**What it proves:** The identity token, the login contract + non-enumeration, and the
filter's principal resolution + gate scope all behave per spec Unit 3.

**Why it matters:** These are the backend-domain behaviors (with the mandated 100%-branch
critical logic) that account-scoped features (#15) will depend on.

**Command:**

```bash
./gradlew test -PskipFrontend \
  --tests 'com.ensemble.security.SessionTokenServiceTest' \
  --tests 'com.ensemble.security.web.AuthControllerTest' \
  --tests 'com.ensemble.security.web.SessionAuthFilterTest'
```

**Result summary:** All green. Test methods (from `build/test-results`):

```
SessionTokenServiceTest:
  issuesTokenCarryingUserId, verifyReturnsUserId, rejectsTamperedToken,
  rejectsTamperedSignature, rejectsExpiredToken, acceptsTokenJustBeforeExpiry,
  rejectsNullToken, rejectsEmptyToken, rejectsMalformedToken, rejectsTokenWithMultipleDots,
  rejectsTokenWithBadBase64Payload, rejectsTokenWithBadBase64Signature,
  rejectsTokenWithNonNumericExpiry, rejectsOldIdentitylessToken, rejectsTokenWithEmptyUserId,
  differentSecrets_produceIncompatibleTokens
AuthControllerTest:
  validLogin_returns200WithToken, wrongPassword_returns401Generic,
  unknownEmail_returns401Generic, malformedBody_returns400
SessionAuthFilterTest:
  validToken_resolvesUserId_andPassesThrough, authAndHealth_areOpen,
  me_withoutToken_returns401, me_withValidToken_returns200WithUserIdAndEmail,
  me_withValidTokenButUnknownUser_returns401, protectedApi_withoutToken_returns401,
  protectedApi_withValidQueryToken_passesThrough, protectedApi_withInvalidToken_returns401
```

## Artifact: JaCoCo — 100% branch on the critical logic

**What it proves:** Token verify/parse and the login match/absent path meet the
`AGENTS.md`/`docs/TESTING.md` 100%-branch requirement for critical backend-domain logic.

**Why it matters:** These are the paths where a missed branch is a real auth defect (a token
that verifies when it shouldn't, or an enumeration/bypass on login).

**Command:**

```bash
./gradlew test -PskipFrontend jacocoTestReport
# parsed from build/reports/jacoco/test/jacocoTestReport.xml
```

**Result summary:** Both critical classes are 100% branch. (The 2 missed `SessionTokenService`
lines are the unreachable `IllegalStateException` in the HMAC-unavailable catch; the filter/
resolver residual branches are defensive web-plumbing paths, outside the mandated critical set.)

```
SessionTokenService            line=35/37   branch=12/12=100%   (verify/parse — critical)
AuthController                 line=12/12    branch=6/6=100%    (login match/absent — critical)
MeController                   line=6/6=100%  branch=n/a
InvalidCredentialsException    line=2/2=100%  branch=n/a
```

## Artifact: Live end-to-end round-trip (login → `/api/me`)

**What it proves:** A concrete user authenticates and the filter resolves the principal
end-to-end, against real DynamoDB Local — and the token really carries the `userId`.

**Why it matters:** This is the demoable behavior behind Unit 3; the token-decode confirms the
identity is embedded (not re-looked-up), and D/E confirm non-enumeration on the wire.

**Setup (throwaway values only — no real credentials):** the app was booted on port 8090
(the dev instance on 8080 was left untouched), a throwaway user was seeded directly into the
`ensemble-users` table with a bcrypt(12) hash generated by `htpasswd -bnBC 12` (proving
`$2y$`↔`BCryptPasswordEncoder` compatibility), then the row was deleted afterward.

**Commands & results:**

```bash
# A. login -> 200 + token
curl -s -X POST localhost:8090/api/auth -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"correct-horse-battery"}'
# {"token":"ZGVtby11c2VyLTE0OjE3ODQ4ODQzMDM.Kmnkjw9I5AlkV9IXS43NbzlVxO9nXKHDmNiLJxSct-g"}  HTTP 200

# token payload base64url-decodes to the embedded identity:
#   demo-user-14:1784884303        (userId : expiryEpochSeconds)

# B. /api/me WITH header -> 200 principal
curl -s localhost:8090/api/me -H "X-Ensemble-Session: <token>"
# {"userId":"demo-user-14","email":"demo@example.com"}  HTTP 200

# C. /api/me WITHOUT header -> 401 (gate)
# {"error":"unauthorized","message":"authentication required"}  HTTP 401

# D. wrong password -> generic 401
# {"error":"unauthorized","message":"invalid email or password"}  HTTP 401

# E. unknown email -> IDENTICAL generic 401 (non-enumeration)
# {"error":"unauthorized","message":"invalid email or password"}  HTTP 401
```

**Result summary:** A→200 + identity-bearing token; B→200 principal; C→401 gate; D and E return
byte-identical generic `401` bodies, so the response never reveals whether an email is registered.

## Artifact: No stray old-API callers (regression check)

**What it proves:** The token-format + `AuthRequest` change left no caller on the old API — the
FLAG-1 regression surface from the planning audit is closed.

**Why it matters:** Success Metric "no regressions" requires the pre-existing suites to stay
green, which they do (352 tests), and no code path may still call the removed signatures.

**Command:**

```bash
grep -rn "\.issue()" src/                         # -> none (no no-arg issue() calls)
grep -rn "tokenService.verify" src/main/           # -> only SessionAuthFilter, as Optional
grep -rn "\.passcode()" src/                        # -> only SecurityProperties.passcode() (unrelated)
```

**Result summary:** No backend caller references the old identity-less token API. (The frontend
`api/auth.ts` still posts `{passcode}` — that is deliberately Task 4.0's UI rework per the
sequencing note; its fetch-mocked Vitest suite stays green.)

## Reviewer Conclusion

Authentication now carries identity: an HMAC-signed `userId:expiry` token with 100%-branch
verify/parse coverage, a non-enumerating email/password login with 100%-branch coverage of the
match/absent path, a filter that resolves the caller onto the request, and a working
`GET /api/me` that returns the concrete principal end-to-end. The full backend suite is green
with no regressions. Ready for Task 3.0 (sign-up `POST /api/accounts`, auto-login).
