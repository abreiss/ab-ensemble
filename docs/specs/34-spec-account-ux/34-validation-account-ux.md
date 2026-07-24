# 34-validation-account-ux.md

> Validation report for [`34-spec-account-ux.md`](./34-spec-account-ux.md) — GitHub issue [#34](https://github.com/abreiss/ab-ensemble/issues/34).
> Branch `email-dropped-update`; baseline `main` (merge-base `cc5fe30`).

## 1) Executive Summary

- **Overall:** **PASS** — no gate tripped (GATE A/B/C/D/E/F all satisfied).
- **Implementation Ready:** **Yes** — all four Demoable Units are implemented under strict TDD, every Functional Requirement is backed by a passing, reproducible proof artifact, and the only open items are non-blocking traceability/cosmetic notes with documented dispositions.
- **Key metrics:**
  - **% Requirements Verified:** 100% (26/26 FRs Verified; 0 Failed; 0 Unknown).
  - **% Proof Artifacts Working:** 100% — backend suite **427/427** green (58 classes, incl. DynamoDB-Local TestContainers ITs); frontend suite **381/381** green (32 files); ESLint clean; live curl transcripts reproduced the wire contract.
  - **Critical-logic branch coverage:** **100%** on username validation path, login non-enumeration (`AuthController` 6/6), duplicate-username conditional (`UserRepository` 2/2), and seed idempotency (`SeedAccountRunner` 4/4, `SeedProperties` 10/10). Project-wide: 336/350 branches, 1082/1103 lines.
  - **Files changed vs expected:** 45 changed; all core files map to a requirement/task or a documented follow-up. 5 files fall outside the planning "Relevant Files" list; all have clear linkage (see GATE D / Issue V-1, V-2).

## 2) Coverage Matrix

### Functional Requirements

#### Unit 1 — Username-based accounts (backend)

| Requirement | Status | Evidence |
| --- | --- | --- |
| FR1.1 `username` is the `ensemble-users` partition key; `email` field removed; `normalizeUsername` (`trim().toLowerCase`, null-safe) for case-insensitive store/lookup | Verified | `UserTest.usernameIsNormalizedToLowercaseTrimmed` + `normalizeUsername_isNullSafe` PASS; `UserRepositoryIT.findByUsername_isCaseAndSpaceInsensitive` PASS; `DynamoDbTableInitializerIT` asserts users key = `"username"`; residual-`email` grep on `com.ensemble.user` → none; commit `d63e313` |
| FR1.2 `POST /api/accounts {username,password,passcode}` → `201 {token}` auto-login | Verified | `AccountControllerTest.validSignup_returns201WithToken` PASS; curl transcript step 1 → `HTTP 201` + token (proof 01) |
| FR1.3 Bean Validation on `SignupRequest` (`@NotBlank`+`@Size(3,30)`+`@Pattern`, no leading/trailing sep); sanitized `400` before persistence | Verified | `AccountControllerTest.invalidUsername_returns400` parameterized (blank / `ab` / 31-char / `"bad name"` / `_bad` / `bad_`), `blankOrShortPassword_returns400`, `passwordOver72Bytes_returns400` PASS; curl step 7 → `400 {"bad_request","invalid request"}` |
| FR1.4 `POST /api/auth {username,password}`; one non-enumerating `401`; dummy-hash timing defense retained | Verified | `AuthControllerTest.validLogin_returns200WithToken`, `wrongPassword_returns401Generic`, `unknownUsername_returns401Generic` PASS — the last asserts `verify(passwordHasher).matches(...)` (bcrypt runs on unknown user); curl steps 4 & 5 return identical `401` body |
| FR1.5 Atomic uniqueness via `attribute_not_exists(username)`; `DuplicateUsernameException` → `409`; first row not overwritten | Verified | `UserRepositoryIT.create_duplicateUsername_throwsDuplicateUsernameException` PASS; `AccountControllerTest.duplicateUsername_returns409` PASS; `DuplicateEmailException.java` renamed `R054` to `DuplicateUsernameException.java` |
| FR1.6 `GET /api/me` → `{userId,username}` (no `email`) | Verified | `SessionAuthFilterTest.me_withValidToken_returns200WithUserIdAndUsername` asserts `$.userId`, `$.username`, `$.email` doesNotExist; curl step 6 |
| FR1.7 Seed keyed on username; reads `ENSEMBLE_SEED_USERNAME`/`_PASSWORD`; idempotent via `findByUsername`; bypasses passcode | Verified | `SeedAccountRunnerTest` (seeds/skips/no-op) + `SeedPropertiesTest` (configured only when both set) PASS; `application.yml` binds `ensemble.seed.username: ${ENSEMBLE_SEED_USERNAME:}` |
| FR1.8 `DynamoDbTableInitializer.USERS_PARTITION_KEY = "username"`; users table GSI-free | Verified | `DynamoDbTableInitializerIT` asserts users key `"username"` and `indexNames(USERS_TABLE)` empty; PASS |
| FR1.9 Copy reworded email→username; `findByEmail`→`findByUsername`; raw password never stored/returned/logged | Verified | Grep on `com.ensemble.{user,security,config}` → 0 `email`/`@Email`; `ApiExceptionHandler` 401/409 messages reworded; `InvalidCredentialsException` message updated |

#### Unit 2 — Auth UI, client, docs, migration

| Requirement | Status | Evidence |
| --- | --- | --- |
| FR2.1 `AuthGate` Username field (label "Username", `id=auth-username`, `autoComplete=username`, `type=text`); state `email`→`username` | Verified | `AuthGate.tsx` L116–122; `AuthGate.test.tsx` queries `/^username$/i` (14 tests PASS) |
| FR2.2 `api/auth.ts` posts `{username,...}` bodies, no `email` key | Verified | `auth.test.ts` asserts `/api/auth` body `{username,password}` and `/api/accounts` body `{username,password,passcode}` (10 tests PASS) |
| FR2.3 Error copy reworded to username | Verified | `AuthGate.test.tsx` asserts "Invalid username or password.", "That username is already registered.", "Enter a valid username and password.", "Check your username, password, and signup code." |
| FR2.4 Migration recreates table; explicit + operator-confirmed; **no** auto-drop, **no** enumerate-and-delete | Verified | README "Migrating a local `ensemble-users` table" procedure (L234); grep confirms no `deleteTable`/scan-delete added to `src/main/java`; consistent with standing "only delete what I created" rule |
| FR2.5 Docs updated (`.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md`) to username + `ENSEMBLE_SEED_USERNAME` | Verified | `.env.example` L29 `ENSEMBLE_SEED_USERNAME=demo_user`; diffs in all four files (proof 02); ARCHITECTURE/DEVELOPMENT narrate username |

#### Unit 3 — Sign-out control

| Requirement | Status | Evidence |
| --- | --- | --- |
| FR3.1 Visible **Sign out** in app nav, only when signed in | Verified | `App.tsx` L51–53 (button in gated shell); `App.test.tsx` renders shell then finds control |
| FR3.2 Click calls `clearToken()` then dispatches `ensemble:auth-required` (reuses 401 machinery) | Verified | `App.tsx` `signOut()` → `clearToken()` + `signalAuthRequired()`; literal `ensemble:auth-required` occurs once (`api/http.ts`) — single-sourced |
| FR3.3 After sign-out `sessionStorage` no longer holds `ensemble.session.token` | Verified | `signOut_clearsTokenAndReturnsToLogin` asserts `getToken()` null AND `sessionStorage.getItem('ensemble.session.token')` null AND username login form shown; PASS |
| FR3.4 No backend change | Verified | Commit `61eec1b` touches only frontend files (git name-status) |

#### Unit 4 — Password confirmation on signup

| Requirement | Status | Evidence |
| --- | --- | --- |
| FR4.1 Confirm password field only in signup mode | Verified | `AuthGate.tsx` `{isSignup && (...)}` L142–162; test "shows the confirm-password field only in signup mode" PASS |
| FR4.2 Mismatch blocks submit + inline `field-error`; match proceeds | Verified | Tests: mismatch blocks + shows "Passwords don't match.", match submits once; PASS. `canSubmit` folds `confirmMismatch`; `handleSubmit` re-guards Enter-key submit |
| FR4.3 Confirmation value never sent; body stays `{username,password,passcode}` | Verified | `signup_body_excludesConfirmValue` asserts posted body `toEqual` 3-key object; `signup(...)` called with `(username,password,passcode)` only |
| FR4.4 Login mode unchanged (no confirm field) | Verified | `toggleMode` resets confirm state; login-mode absence test PASS |

**GATE B:** No `Unknown` entries — **satisfied**.

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Strict TDD (RED→GREEN→REFACTOR) | Verified | Backend task RED captures + GREEN in proofs 01; frontend RED/GREEN transcripts in proofs 02/04; tasks marked with explicit RED/GREEN phases |
| Coverage bar (≥90% line; 100% branch on critical logic) | Verified | JaCoCo XML: User 2/2, UserRepository 2/2, AuthController 6/6, SeedAccountRunner 4/4, SeedProperties 10/10, MaxUtf8BytesValidator 4/4 branches — all 100%; project line 1082/1103 (98%). No enforced Gradle/CI threshold — verified by reading the report, per documented precedence |
| Layered architecture / DTOs at boundary | Verified | DTO validation on `SignupRequest`/`AuthRequest` records; controllers return DTOs; no DynamoDB item or token internals leak |
| Mock external boundaries; DynamoDB Local (TestContainers); no live network | Verified | `UserRepositoryIT`/`DynamoDbTableInitializerIT` ran under Docker; Claude client mocked elsewhere; no live network in suite |
| Shared `ApiExceptionHandler` error shape; generic `400`, non-enumerating `401` | Verified | `400 {"bad_request","invalid request"}`, `401 {"unauthorized","invalid username or password"}`, `409 {"conflict","username already registered"}` |
| Conventional commits + `Co-Authored-By` trailer | Verified | 4 `feat(accounts)` + 1 `fix(deploy)`; every commit carries the project trailer |
| No secrets in proofs (secret scan) | Verified | Scan for `sk-ant-*`/`AKIA*`/private keys/`aws_secret_access_key` across the spec dir → none; tokens truncated + `REDACTED`; synthetic credentials only |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| 1.0 | `AccountControllerTest`, `AuthControllerTest`, `UserRepositoryIT`, `SeedAccountRunnerTest`, `SeedPropertiesTest`, `UserTest`, `DynamoDbTableInitializerIT`, `SessionAuthFilterTest` | Verified | 46/46 in the 8 classes PASS; full suite 427/427; re-run with `--rerun-tasks` (not cached) |
| 1.0 | JaCoCo report — 100% branch on critical logic | Verified | XML parsed: 0 missed branches on all critical classes |
| 1.0 | curl transcript (signup/duplicate/login/wrong-pw/unknown-user/`/api/me`/invalid/wrong-passcode) | Verified | Reproduces expected status+body; ran against a self-created throwaway `ensemble-users-proof34` table, deleted after (dev table untouched) |
| 2.0 | `auth.test.ts`, `AuthGate.test.tsx`, `App.test.tsx`; docs diffs; migration Doc/Log | Verified | Frontend suite 381/381; docs reworded; migration procedure + hygiene greps present |
| 3.0 | `signOut_clearsTokenAndReturnsToLogin` RTL test; single-source event grep | Verified | PASS; `ensemble:auth-required` literal single-sourced in `api/http.ts` |
| 4.0 | `AuthGate.test.tsx` confirm cases (mismatch/match/body-excludes/visibility) | Verified | 14/14 PASS; posted body carries no confirmation key |

**GATE C:** All proof artifacts accessible and functional — **satisfied**.

## 3) Validation Issues

No CRITICAL or HIGH issues (**GATE A satisfied**). The following non-blocking items are recorded for traceability.

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| MEDIUM | **Terraform `ENSEMBLE_SEED_EMAIL` not renamed.** `terraform/deploy/{apprunner,secrets,variables}.tf` still wire `ENSEMBLE_SEED_EMAIL` (now gated behind `seed_account_enabled`, default off), while the app reads `ENSEMBLE_SEED_USERNAME`. Evidence: grep item E in proof 02; audit FLAG 1. | If cloud seeding is ever enabled without the rename, the injected env var is ignored (silent no-op seed). No runtime break — seeding is opt-in default-off and invite signup is unaffected. | Documented as an out-of-band operator follow-up (`README.md` L255–258, "Operator follow-up (cloud, out-of-band)"). Perform the `ENSEMBLE_SEED_EMAIL`→`ENSEMBLE_SEED_USERNAME` Terraform/secret rename before enabling cloud seeding. Linkage is explicit — not scope creep. |
| LOW | **`fix(deploy)` commit rides along.** `3eef8e0` (terraform seeding opt-in) is tangential to spec 34's three stated features but touches the account-seed secrets. Evidence: git per-file attribution. | Traceability only — the commit message fully explains the App Runner empty-secret rollback it fixes. | Acceptable; the change is well-documented and within the account domain. No action required. |
| LOW | **Proof-artifact test names differ cosmetically.** The confirm-password tests use descriptive sentence names (e.g. "blocks signup and shows an inline error when the confirmation does not match") rather than the literal `signup_confirmMismatch_blocksSubmitAndShowsError` identifiers named in the spec/tasks. Evidence: frontend verification. | None — behavior covered 1:1; only a name-matching nuance for a reviewer grepping for the literal id. | Optional: align test names with the spec identifiers, or note the mapping. Non-blocking. |
| LOW | **Point-in-time frontend test counts vary across proofs** (376→377→381; proof 04 cites both "382" and "381"). Evidence: proofs 02–04. | Cosmetic — counts grew as each task added tests; current actual is **381** (re-verified). | None required; optionally normalize the final count in proof 04. |

## 4) Evidence Appendix

### Git commits analyzed (`main`..`email-dropped-update`)

| Commit | Unit | Files |
| --- | --- | --- |
| `d63e313` feat(accounts): replace email with username across the backend account domain | Unit 1 | `User`, `UserRepository`, `DuplicateUsernameException` (R from `DuplicateEmailException`), `SignupRequest`, `AuthRequest`, `AccountController`, `AuthController`, `InvalidCredentialsException`, `ApiExceptionHandler`, `SeedProperties`, `SeedAccountRunner`, `MeController`, `DynamoDbTableInitializer`, `application.yml`, session `SessionTokenService`/`SessionUserNotFoundException` (Javadoc reword), + backend tests |
| `3e52b68` feat(accounts): switch the auth UI, client, and docs to username | Unit 2 | `AuthGate.tsx/.test.tsx`, `api/auth.ts/.test.ts`, `App.test.tsx`, `.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md` |
| `61eec1b` feat(accounts): add a sign-out control to the app nav | Unit 3 | `App.tsx`, `api/http.ts`, `App.test.tsx` (frontend only) |
| `7811d10` feat(accounts): add confirm-password field to signup | Unit 4 | `AuthGate.tsx`, `AuthGate.test.tsx` |
| `3eef8e0` fix(deploy): make startup account seeding opt-in so signup deploys cleanly | (deploy fix; see V-1/V-2) | `terraform/deploy/{apprunner,secrets,variables}.tf` |

### File classification (GATE D)

- **Core files outside "Relevant Files":** `SessionTokenService.java`, `SessionUserNotFoundException.java` — **comment-only** email→username Javadoc rewords inside the Unit-1 migration commit; direct linkage to FR1.9. `terraform/deploy/*.tf` — infra; linked via the `fix(deploy)` commit message and the documented out-of-band follow-up (Issue V-1). **No unmapped out-of-scope core change → D1 (blocker) not tripped.**
- **Supporting files** (tests, proofs, docs) — all linked to a core change/requirement in tasks or commit messages (D2 satisfied).

### Commands executed (key results)

- `./gradlew test -PskipFrontend --rerun-tasks` → `BUILD SUCCESSFUL`; 58 classes, **427 tests, 0 failures/errors/skipped** (TestContainers ITs ran).
- Targeted `--tests` run of the 8 named classes → 46/46 PASS; `AuthControllerTest.unknownUsername_returns401Generic` asserts `verify(passwordHasher).matches(...)`.
- `./gradlew jacocoTestReport` → XML: 0 missed branches on User/UserRepository/AuthController/SeedAccountRunner/SeedProperties/MaxUtf8BytesValidator; project 336/350 branch, 1082/1103 line.
- `grep -rniE "email" src/main/java/com/ensemble/{user,security,config}` → **no matches**.
- `cd frontend && npm test -- --run` → **381/381** across 32 files; `npm run lint` → clean.
- `grep -rniE 'email' frontend/src` → **no matches**.
- Secret scan across `docs/specs/34-spec-account-ux/` → **no real credentials**.

---

**Validation Result:** **PASS** — Implementation Ready.

Before merging, do a final human code review of the implementation and this report.

**Validation Completed:** 2026-07-24
**Validation Performed By:** Claude Opus 4.8 (1M context)
