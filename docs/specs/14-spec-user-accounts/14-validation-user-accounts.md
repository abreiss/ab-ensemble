# 14-validation-user-accounts.md

Validation report for `14-spec-user-accounts.md`. Independent verification of the
implementation and proof artifacts against the spec and task list, per the SDD
Phase 4 quality gates.

## 1) Executive Summary

- **Overall: PASS** — no gates tripped (GATE A–F all pass).
- **Implementation Ready: Yes** — all four demoable units are implemented, every
  functional requirement is backed by a working proof artifact independently
  re-run in this session, and the two critical-logic coverage targets (100%
  branch) are met.
- **Key metrics:**
  - Requirements Verified: **100%** (all Unit 1–4 FRs → Verified; 0 Unknown, 0 Failed).
  - Proof Artifacts Working: **100%** — backend suite (376 tests) and frontend
    suite (374 tests) re-run green in this session; every cited JaCoCo number and
    test method re-confirmed; docs/Terraform diffs present; CLI round-trips match
    the wired code paths.
  - Files Changed vs Expected: **within scope** — every core change maps to a
    task/FR; supporting files (tests, docs, IaC) are linked. No unmapped
    out-of-scope core change.
- **Gates:** A ✅ · B ✅ · C ✅ · D ✅ · E ✅ · F ✅
- **Non-blocking findings:** 1 LOW (stale javadoc string), 1 informational (the
  audit's suggested `/api/me` GSI scale-path note was not added to
  `ARCHITECTURE.md`). Neither blocks the gate.

## 2) Coverage Matrix

### Functional Requirements

| Requirement | Status | Evidence |
| --- | --- | --- |
| **U1** `User` `@DynamoDbBean`, `email` partition key, normalized (trim+lowercase), fields `userId`/`passwordHash`/`createdAt`, no hash-echoing `toString` | Verified | `user/User.java:26,39,45-47`; `UserTest.emailIsNormalizedToLowercaseTrimmed` + `normalizeEmail_isNullSafe` pass |
| **U1** `UserRepository` — `findByEmail` (GetItem), `findByUserId` (scan), atomic `create` (`attribute_not_exists(email)` → `DuplicateEmailException`) | Verified | `user/UserRepository.java:48-54,58-61,72-74`; `UserRepositoryIT` 7/7 vs real DynamoDB Local; branch 2/2=100% |
| **U1** `PasswordHasher` bcrypt work factor **12**, `hash`/`matches` | Verified | `user/PasswordHasher.java:20,22`; `PasswordHasherTest` 3/3 (both `matches` outcomes); line 4/4=100% |
| **U1** `usersTableName` property (`ensemble-users` default) + dev auto-create; cloud table Terraform-owned | Verified | `application.yml:32`; `DynamoDbTableInitializer.java:45,59`; `data_stores.tf:62` `aws_dynamodb_table.users` (hash `email`) |
| **U3** `SessionTokenService` payload `base64url(userId:expiry).base64url(HMAC)`; `issue(userId)`; `verify → Optional<userId>`; rejects malformed/tampered/expired/old-identityless/empty-userId; constant-time compare | Verified | `security/SessionTokenService.java:48-49,60,65,74-95`; `SessionTokenServiceTest` 16/16; **branch 12/12=100%** |
| **U3** `POST /api/auth` = email/password login; generic `401` for unknown email **and** wrong password; dummy-hash bcrypt compare when email absent (timing) | Verified | `security/web/AuthController.java:54,59-68`; `AuthControllerTest` 4/4; **branch 6/6=100%** |
| **U3** `AuthRequest` record `{email,password}` (`@NotBlank @Email`/`@NotBlank`); passcode removed from login | Verified | `security/dto/AuthRequest.java:11` |
| **U3** `SessionAuthFilter` resolves `userId` → `ensemble.userId` attribute; open routes `POST /api/auth`, `GET /api/health`, `POST /api/accounts`; protected route without valid token → `401` pre-controller | Verified | `security/web/SessionAuthFilter.java:42,66-72,82-97`; `SessionAuthFilterTest` 9/9 |
| **U3** `@CurrentUserId` argument resolver registered via `WebMvcConfigurer` | Verified | `security/web/CurrentUserId.java`, `CurrentUserIdArgumentResolver.java:26`, `CurrentUserWebConfig.java:14-19` |
| **U3** `GET /api/me` (protected) → `{userId,email}` via `findByUserId` | Verified | `user/web/MeController.java:28,38-42`; `SessionAuthFilterTest.me_*`; live curl round-trip (task-02 proof) |
| **U2** `POST /api/accounts` signup: passcode checked **first** (no user created on fail) → hash → `create` → `201 {token}` auto-login | Verified | `user/web/AccountController.java:58,60-69`; `AccountControllerTest` 6/6; branch 2/2=100% |
| **U2** `SignupRequest {email,password,passcode}` — `@Email`, `@Size(min=8)` + `@MaxUtf8Bytes(72)`, `@NotBlank` passcode | Verified | `user/web/SignupRequest.java:18-21` |
| **U2** `@MaxUtf8Bytes` counts UTF-8 **bytes**; null defers to `@NotBlank` | Verified | `user/web/MaxUtf8BytesValidator.java:24-27`; `MaxUtf8BytesValidatorTest` 4/4; **branch 4/4=100%** |
| **U2** `SignupPasscodeVerifier` constant-time SHA-256; null/blank candidate → false; blank configured passcode → signup closed | Verified | `security/SignupPasscodeVerifier.java:38-44`; `SecurityProperties.passcodeConfigured()`; `SignupPasscodeVerifierTest` 4/4; **branch 6/6=100%** |
| **U4** `SeedAccountRunner` (`ApplicationRunner`): seed when both env set + absent; skip if exists; no-op unconfigured; bypasses passcode; ordered after table init; not gated on auto-create (runs in cloud) | Verified | `user/SeedAccountRunner.java:36-37,56-63`; `SeedAccountRunnerTest` 3/3; **line 21/21, branch 4/4=100%** |
| **U4** `SeedProperties` `@ConfigurationProperties("ensemble.seed")`, masked `toString` | Verified | `config/SeedProperties.java:19,38-44`; `SeedPropertiesTest` 6/6; branch 10/10=100% |
| **U4** `ApiExceptionHandler`: `AccountController`/`MeController` allow-listed; `DuplicateEmailException`→409 (`conflict`), `InvalidCredentialsException`→401 (`unauthorized`); binding→sanitized `400` no field echo | Verified | `wardrobe/web/ApiExceptionHandler.java:39-47,94-105,130-134,140-144` |
| **U4** Frontend `AuthGate` login↔signup toggle (Care Label); `api/auth.ts` `login`/`signup`; non-enumerating inline error copy for 401/409/400/signup-401 | Verified | `AuthGate.tsx`, `api/auth.ts`; `auth.test.ts` 8/8, `AuthGate.test.tsx` 10/10; four error strings asserted |
| **U4** Docs (`ARCHITECTURE.md`, `README.md`, `docs/DEVELOPMENT.md`) + `.env.example` + Terraform describe the account model with no committed secret | Verified | `ARCHITECTURE.md:77`; `README.md:194-242`; `.env.example:10-24`; `secrets.tf:23-30`, `apprunner.tf:58,67-68` |

### Repository Standards

| Standard Area | Status | Evidence & Notes |
| --- | --- | --- |
| Strict TDD (backend domain) | Verified | Task list shows RED→GREEN per unit; tests exist beside code; suite green (376) |
| Coverage (≥90% line, 100% branch on critical logic) | Verified | JaCoCo re-parsed: 100% branch on token verify/parse, login match/absent, dup-email conditional, signup-passcode, password-length validator |
| Layered architecture / DTOs at boundary | Verified | Controllers → services (`SessionTokenService`, `PasswordHasher`, `SignupPasscodeVerifier`) → `UserRepository`; records (`AuthRequest`/`SignupRequest`/`MeResponse`) at boundary; no enhanced-client/crypto leak |
| DynamoDB Enhanced Client, no JPA; TestContainers round-trip | Verified | `UserRepository` uses enhanced client; `UserRepositoryIT` runs on DynamoDB Local |
| `spring-security-crypto` utility only (no security starter/filter chain) | Verified | task-01 proof: single crypto jar on runtimeClasspath; hand-rolled filter untouched |
| Conventional commits, ~1 per unit | Verified | 4 `feat(accounts): …` commits, one per demoable unit |
| No secrets committed | Verified | GATE F scan clean; `.env.example`/Terraform placeholders + ARN refs only |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| 1.0 | `./gradlew test --tests 'com.ensemble.user.*'`; JaCoCo; Terraform `fmt`/`validate`; crypto-jar/log grep | Verified | Re-run: user-package tests green; `User`/`UserRepository`/`PasswordHasher` 100% line, branch where present; `data_stores.tf` users table present |
| 2.0 | `SessionTokenServiceTest`/`AuthControllerTest`/`SessionAuthFilterTest`; JaCoCo; live login→`/api/me` curl | Verified | Re-run: all green; SessionTokenService branch 12/12, AuthController 6/6; source confirms identity token + non-enumerating dummy-hash path |
| 3.0 | `SignupPasscodeVerifierTest`/`MaxUtf8BytesValidatorTest`/`AccountControllerTest`; JaCoCo; live signup/409/401/400 + login curl | Verified | Re-run: all green; SignupPasscodeVerifier branch 6/6, MaxUtf8BytesValidator 4/4; passcode-before-create confirmed in source |
| 4.0 | `SeedAccountRunnerTest`/`SeedPropertiesTest`; frontend `auth.test.ts`/`AuthGate.test.tsx`; docs+Terraform diff; secret scan | Verified | Re-run: backend seed tests 100% branch; frontend 374 green; docs/Terraform present; secret-scan hooks pass |
| 4.0 | Screenshots (login / signup / post-login) | Waived (accepted) | Explicitly waived by the project owner; behavior proven by 10 RTL cases + live round-trip. Consistent with project convention that proofs need tests + CLI evidence, not screenshots. Non-blocking. |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| LOW | Stale javadoc. `security/InvalidPasscodeException.java:3` still says the passcode is "submitted to `POST /api/auth`", but the gate moved to `POST /api/accounts` signup in this feature. Wiring is correct (`AccountController` → `SignupPasscodeVerifier`). | Documentation only; no functional effect | Update the javadoc to reference `POST /api/accounts` (signup) in a follow-up; not required to merge. |
| INFO | Planning-audit FLAG 2 suggested noting the `userId` GSI scale-path in `ARCHITECTURE.md` when `/api/me` uses a `findByUserId` scan. The scan is implemented as designed and correct at demo scale, but the suggested scale-path note was not added. | None at demo scale; deferred #15 concern | Optionally add a one-line "GSI is the scale path for frequent `userId` lookups" note to `ARCHITECTURE.md`. Non-blocking. |

Planning-audit FLAG 1 (token-format + `AuthRequest` regression surface) is **closed**: the full pre-existing backend suite (376) and frontend suite (374) are green, and the task-02 grep proof shows no stray callers of the old no-arg `issue()`/identity-less `verify` or `{passcode}` login body.

## 4) Evidence Appendix

**Git commits analyzed** (branch `feat/user-accounts`, 4 commits vs `main`, one per unit, conventional):
- `8ece5ac` feat(accounts): User record, UserRepository, PasswordHasher (Unit 1)
- `bf9c797` feat(accounts): identity-bearing session token, email/password login, /api/me (Units 2–3 token/login/principal)
- `ff8be89` feat(accounts): invite-only signup POST /api/accounts with auto-login (Unit 2 signup)
- `9667245` feat(accounts): seed account, login/sign-up UI, and account-model docs (Unit 4)

**File integrity (GATE D):** all core Java changes map to tasks/FRs. Two core files not in the planning "Relevant Files" table are linked and justified: `config/DynamoDbConfig.java` (adds `SeedProperties` to `@EnableConfigurationProperties` — Unit 4) and `security/SecurityConfig.java` (`logGateState()` wording — explicitly Task 3.2). The `CurrentUser*` files are the Task 2.4 resolver. Supporting changes — `*IT` `DynamoDbProperties` call-site updates (Task 1.6), `src/test/resources/application.yml`, frontend test files, `docs/DEVELOPMENT.md`, `terraform/deploy/secrets.tf` — are all task-linked. No unmapped out-of-scope core change → **D1 pass**.

**Commands re-run in this session:**
- `./gradlew test -PskipFrontend` → BUILD SUCCESSFUL, 376 tests, 0 failures/0 errors/0 skipped (52 suites; `UserRepositoryIT` ran on real DynamoDB Local via TestContainers).
- `./gradlew jacocoTestReport -PskipFrontend` → parsed with `ElementTree`; all 11 cited class coverage numbers match (100% branch on the five critical-logic classes).
- `cd frontend && npm run test -- --run` → 32 files, 374 tests passed. `npm run lint` → exit 0. `npx tsc -b` → clean.
- GATE F secret scan (`sk-ant`/`AKIA`/private-key/`aws_secret_access_key` patterns across proofs + `.env.example` + Terraform) → no real-secret patterns; placeholders/ARN refs only.

**Security (GATE F):** proof docs use throwaway demo values (`demo@example.com`, `correct-horse-battery`, `demo-invite-14`, etc.); no real credential, key, or passcode value is committed.

---

**Validation Completed:** 2026-07-23
**Validation Performed By:** Claude Opus 4.8 (1M context)
