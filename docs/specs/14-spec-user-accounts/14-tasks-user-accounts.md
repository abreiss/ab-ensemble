# 14-tasks-user-accounts.md

Task list for `14-spec-user-accounts.md`. Parent tasks are demoable units of
work. Backend domain code follows strict TDD (RED → GREEN → REFACTOR) per
`AGENTS.md` / `docs/TESTING.md`: ≥90% line and 100% branch on the critical logic
(token verify/parse, password verification, the duplicate-email conditional, the
signup-passcode check, and password strength/length validation).

## Sequencing note (spec Units 2↔3 swapped)

The spec numbers sign-up as Unit 2 and login as Unit 3. With **auto-login**
(spec Open Question #1, confirmed), sign-up mints a `userId`-bearing token, which
depends on the `SessionTokenService` identity change **and** the `AuthController`
login rewrite (the old passcode login has no `userId` to embed, so it cannot
survive the token-format change). To keep every task in a working, spec-compliant
state, the **login/token core is Task 2.0** and **sign-up is Task 3.0**. All spec
requirements are still covered; only the implementation order changed.

## `/api/me` lookup note

The session token carries an **opaque `userId` only** (no email/PII — spec
"Security"). `GET /api/me` returns `{ userId, email }`, so it needs a
`userId → user` lookup, but the users table is keyed by `email` with **no GSI**
(Q2-A). At demo scale (~1–20 users) this is a `UserRepository.findByUserId(...)`
**scan** with a filter expression — the same full-scan approach
`WardrobeRepository.findAll()` already uses — rather than adding a GSI. This is a
minor, deliberate refinement of Q2-A's "never needs a `userId → row` lookup"
note (that holds for request auth, but `/api/me`'s response contract needs one).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `build.gradle` | Add `org.springframework.security:spring-security-crypto` (BOM-managed; no full security starter). |
| `src/main/java/com/ensemble/user/User.java` | New `@DynamoDbBean`; partition key `email` (normalized), attrs `userId`/`passwordHash`/`createdAt`. |
| `src/test/java/com/ensemble/user/UserTest.java` | Unit test for email normalization. |
| `src/main/java/com/ensemble/user/UserRepository.java` | New repo: `findByEmail`, `findByUserId` (scan), conditional `create`. |
| `src/test/java/com/ensemble/user/UserRepositoryIT.java` | DynamoDB Local round-trip + atomic duplicate-email guard (mirrors `OutfitRepositoryIT`). |
| `src/main/java/com/ensemble/user/DuplicateEmailException.java` | New runtime exception → mapped to `409`. |
| `src/main/java/com/ensemble/user/PasswordHasher.java` | New `@Component` wrapping `BCryptPasswordEncoder(12)`: `hash`/`matches`. |
| `src/test/java/com/ensemble/user/PasswordHasherTest.java` | Unit test for bcrypt hash/verify + distinct salts (100% branch on `matches`). |
| `src/main/java/com/ensemble/config/DynamoDbProperties.java` | Add `usersTableName` (`${ENSEMBLE_USERS_TABLE_NAME:ensemble-users}`). |
| `src/main/java/com/ensemble/config/DynamoDbTableInitializer.java` | Add users-table `ensureTable(..., "email")`. |
| `src/main/resources/application.yml` | Add `ensemble.dynamodb.users-table-name` + `ensemble.seed.*`. |
| `src/main/java/com/ensemble/security/SessionTokenService.java` | Payload → `userId:expiry`; `issue(userId)`; `verify` → `Optional<String>`. |
| `src/test/java/com/ensemble/security/SessionTokenServiceTest.java` | Identity-bearing token, tamper/expiry/malformed/old-format (100% branch). |
| `src/main/java/com/ensemble/security/web/SessionAuthFilter.java` | Adapt to `Optional` verify; expose `userId` attribute; open-route `POST /api/accounts`. |
| `src/test/java/com/ensemble/security/web/SessionAuthFilterTest.java` | Full-context: principal resolution, open routes, `/api/me` gate. |
| `src/main/java/com/ensemble/security/web/AuthController.java` | Repurpose `POST /api/auth` to email/password login (generic `401`). |
| `src/test/java/com/ensemble/security/web/AuthControllerTest.java` | Login contract + non-enumeration + dummy-hash path. |
| `src/main/java/com/ensemble/security/dto/AuthRequest.java` | Change to `{ email, password }` with validation. |
| `src/main/java/com/ensemble/security/InvalidCredentialsException.java` | New → mapped to generic `401`. |
| `src/main/java/com/ensemble/security/SignupPasscodeVerifier.java` | Constant-time SHA-256 signup-passcode compare (factored out of `AuthController`). |
| `src/test/java/com/ensemble/security/SignupPasscodeVerifierTest.java` | Passcode check branches (100% branch), incl. closed-when-unconfigured. |
| `src/main/java/com/ensemble/user/web/MeController.java` | New `GET /api/me` → `{ userId, email }`. |
| `src/main/java/com/ensemble/user/web/AccountController.java` | New `POST /api/accounts` (signup, auto-login). |
| `src/test/java/com/ensemble/user/web/AccountControllerTest.java` | Signup contract + validation branches. |
| `src/main/java/com/ensemble/user/web/SignupRequest.java` | New `{ email, password, passcode }` with validation annotations. |
| `src/main/java/com/ensemble/user/web/MaxUtf8Bytes.java` + `MaxUtf8BytesValidator.java` | Custom constraint: password ≤ 72 UTF-8 bytes (bcrypt limit). |
| `src/test/java/com/ensemble/user/web/MaxUtf8BytesValidatorTest.java` | Byte-length boundary (100% branch). |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Add `AccountController`/`MeController` to `assignableTypes`; map `DuplicateEmailException`→409, `InvalidCredentialsException`→401. |
| `src/main/java/com/ensemble/user/SeedAccountRunner.java` | New `ApplicationRunner`: idempotent env-seeded default account. |
| `src/test/java/com/ensemble/user/SeedAccountRunnerTest.java` | Seed/skip/no-op branches. |
| `src/main/java/com/ensemble/config/SeedProperties.java` | `@ConfigurationProperties("ensemble.seed")`, masked `toString`. |
| `frontend/src/api/auth.ts` | Add `login(email,password)` + `signup(email,password,passcode)`; keep token transport. |
| `frontend/src/api/auth.test.ts` | Login/signup POST bodies + token storage + error paths. |
| `frontend/src/components/AuthGate.tsx` | Rework into toggleable login / sign-up form (Care Label). |
| `frontend/src/components/AuthGate.test.tsx` | Auth-state logic + inline error mapping (`401`/`409`/`400`). |
| `terraform/deploy/data_stores.tf` | Add `aws_dynamodb_table.users` (hash `email`). |
| `terraform/deploy/apprunner.tf` | Non-secret `ENSEMBLE_USERS_TABLE_NAME`; seed creds as empty `runtime_environment_secrets`. |
| `docs/ARCHITECTURE.md`, `README.md`, `.env.example` | Document the account model, signup-passcode role, seed account. |

### Notes

- Tests live beside the code under test in `src/test/java/com/ensemble/...`;
  integration tests (`*IT`) use DynamoDB Local via TestContainers (no Spring
  context), mirroring `OutfitRepositoryIT`/`WardrobeRepositoryIT`.
- Run backend fast tests with `./gradlew test -PskipFrontend`; frontend with
  `cd frontend && npm run test -- --run`; coverage via `./gradlew jacocoTestReport`.
- `spring-security-crypto` is a plain utility jar — it does **not** enable any
  Spring Security auto-config or filter chain, so the hand-rolled
  `SessionAuthFilter` is untouched.
- Never commit a real password, passcode, or seed credential; all proof
  artifacts use throwaway demo values. The pre-commit secret scan must pass.

## Tasks

### [x] 1.0 `User` record, `UserRepository`, and `PasswordHasher` (data + crypto foundation)

Adds the persistence + hashing foundation every later unit builds on: a `User`
`@DynamoDbBean` keyed on normalized `email`, a `UserRepository` with an atomic
conditional-put uniqueness guard, and a bcrypt `PasswordHasher` (work factor 12)
via the standalone `spring-security-crypto` dependency. No user-facing surface —
proof is via tests + the cloud table declaration.

#### 1.0 Proof Artifact(s)

- Test: `UserRepositoryIT` (DynamoDB Local via TestContainers) passes `createThenFindByEmail`, `create_duplicateEmail_throwsDuplicateEmailException`, and `findByEmail_isCaseAndSpaceInsensitive` — demonstrates the round-trip and the atomic `attribute_not_exists(email)` uniqueness guard.
- Test: `PasswordHasherTest` passes `hashVerifiesAgainstRawPassword`, `wrongPasswordDoesNotMatch`, and `sameInputProducesDifferentSalts` — demonstrates salted bcrypt storage with 100% branch on the `matches` path.
- Test: `UserTest` passes `emailIsNormalizedToLowercaseTrimmed` — demonstrates key normalization (`Foo@X.com` → `foo@x.com`).
- CLI: `./gradlew test -PskipFrontend --tests 'com.ensemble.user.*'` returns `BUILD SUCCESSFUL` — demonstrates the data/crypto layer is green.
- Diff: `terraform/deploy/data_stores.tf` adds `aws_dynamodb_table.users` (hash key `email`, `PAY_PER_REQUEST`) and `terraform fmt -check` + `terraform validate` pass — demonstrates the cloud table is declared with no IAM change (existing `table/${prefix}-*` wildcard covers it).

#### 1.0 Tasks

- [x] 1.1 Add `org.springframework.security:spring-security-crypto` to `build.gradle` (version via the Spring Boot BOM; no full security starter). Confirm the app still boots with the hand-rolled filter and no Spring Security auto-config activates.
- [x] 1.2 (RED) Write `PasswordHasherTest` (`src/test/java/com/ensemble/user/PasswordHasherTest.java`): `hashVerifiesAgainstRawPassword`, `wrongPasswordDoesNotMatch`, `sameInputProducesDifferentSalts`. Run → fails (no `PasswordHasher`).
- [x] 1.3 (GREEN) Implement `PasswordHasher` (`user/PasswordHasher.java`) as a `@Component` wrapping `new BCryptPasswordEncoder(12)`; expose `String hash(String raw)` and `boolean matches(String raw, String hash)`. Run → green; 100% branch on `matches`.
- [x] 1.4 (RED) Write `UserTest` (`src/test/java/com/ensemble/user/UserTest.java`): `emailIsNormalizedToLowercaseTrimmed`. Run → fails.
- [x] 1.5 (GREEN) Implement `User` `@DynamoDbBean` (`user/User.java`) mirroring `SavedOutfit`: no-arg ctor + getters/setters; `@DynamoDbPartitionKey` on `getEmail()`; `setEmail` normalizes via static `normalizeEmail(String)` (null-safe trim+lowercase); fields `email`, `userId`, `passwordHash`, `createdAt` (`Instant`). No `toString()` that echoes `passwordHash`. Run → green.
- [x] 1.6 Add `usersTableName` to `DynamoDbProperties` and `users-table-name: ${ENSEMBLE_USERS_TABLE_NAME:ensemble-users}` to `application.yml`; update every `new DynamoDbProperties(...)` call site (notably the `*IT` `setUp()` methods) for the new component.
- [x] 1.7 (RED→GREEN) Write `UserRepositoryIT` (`src/test/java/com/ensemble/user/UserRepositoryIT.java`) mirroring `OutfitRepositoryIT`: `createThenFindByEmail`, `create_duplicateEmail_throwsDuplicateEmailException`, `findByEmail_isCaseAndSpaceInsensitive`, `findByUserId_returnsUser`. Then implement `UserRepository` (`user/UserRepository.java`, `@Repository`) binding `enhancedClient.table(props.usersTableName(), TableSchema.fromBean(User.class))` with `Optional<User> findByEmail(String)` (normalize → GetItem), `Optional<User> findByUserId(String)` (scan + filter — see `/api/me` note), and `void create(User)` (conditional `putItem`, `attribute_not_exists(email)`, catching `ConditionalCheckFailedException` → `DuplicateEmailException`). Add `DuplicateEmailException` (`user/DuplicateEmailException.java`). Run → green.
- [x] 1.8 (GREEN) Extend `DynamoDbTableInitializer` (`config/DynamoDbTableInitializer.java`): add `USERS_PARTITION_KEY = "email"` and an `ensureTable(props.usersTableName(), USERS_PARTITION_KEY)` call in `run(...)`; add a `DynamoDbTableInitializerIT` assertion that the users table is created. Run → green.
- [x] 1.9 Terraform: add `aws_dynamodb_table.users` (hash `email` type `S`, `PAY_PER_REQUEST`) to `terraform/deploy/data_stores.tf` and `ENSEMBLE_USERS_TABLE_NAME = aws_dynamodb_table.users.name` to the non-secret env map in `terraform/deploy/apprunner.tf`. Run `terraform fmt -check -recursive` and `terraform init -backend=false && terraform validate` (in `terraform/deploy`) → pass. No IAM change.
- [x] 1.10 (REFACTOR) Run `./gradlew test -PskipFrontend` (all green) + `jacocoTestReport`; confirm ≥90% line and 100% branch on `PasswordHasher.matches` and the duplicate-email conditional; confirm `passwordHash` never appears in logs/serialization. Capture the 1.0 proof artifacts.

### [x] 2.0 Identity-bearing session token, email/password login (`POST /api/auth`), resolved principal (`GET /api/me`)

Turns auth into identity. `SessionTokenService`'s payload becomes `userId:expiry`
(HMAC-signed); `verify` returns the `userId`. `AuthController` is rewritten to
`{ email, password }` with a generic non-enumerating `401` (bcrypt compare even
when the email is absent). `SessionAuthFilter` resolves the `userId` onto the
request and exposes it via a current-user accessor; `GET /api/me` returns
`{ userId, email }` as the demoable proof.

#### 2.0 Proof Artifact(s)

- Test: `SessionTokenServiceTest` passes `issuesTokenCarryingUserId`, `verifyReturnsUserId`, `rejectsTamperedToken`, `rejectsExpiredToken`, `rejectsMalformedToken`, `rejectsOldIdentitylessToken` — demonstrates the identity-bearing token with 100% branch on verify/parse.
- Test: `AuthControllerTest` (rewritten) passes `validLogin_returns200WithToken`, `wrongPassword_returns401Generic`, `unknownEmail_returns401Generic`, `malformedBody_returns400` — demonstrates the login contract and non-enumeration (unknown email and wrong password return the identical `401`, with the dummy-hash timing-equalization path exercised).
- Test: `SessionAuthFilterTest` (full context) passes `validToken_resolvesUserId_andPassesThrough`, `authAndHealth_areOpen`, `me_withoutToken_returns401`, `me_withValidToken_returns200WithUserIdAndEmail` — demonstrates the resolved principal and the open-route scope.
- CLI: `curl -s -X POST localhost:8080/api/auth -H 'Content-Type: application/json' -d '{"email":"demo@example.com","password":"<demo-pass>"}'` returns a token (throwaway demo creds), then `curl -s localhost:8080/api/me -H "X-Ensemble-Session: <token>"` returns `{"userId":"...","email":"demo@example.com"}`; the same `/api/me` call without the header returns `401` — demonstrates the concrete principal resolved end-to-end.

#### 2.0 Tasks

- [x] 2.1 (RED) Rewrite `SessionTokenServiceTest` for the identity-bearing token: `issuesTokenCarryingUserId`, `verifyReturnsUserId`, `rejectsTamperedToken`, `rejectsExpiredToken`, `rejectsMalformedToken`, `rejectsOldIdentitylessToken` (payload with no `:` userId separator). Run → fails.
- [x] 2.2 (GREEN) Change `SessionTokenService`: payload = `userId + ":" + expiryEpochSeconds`; `String issue(String userId)`; `Optional<String> verify(String token)` returning the `userId` for a well-formed/untampered/unexpired token and `Optional.empty()` otherwise (retain constant-time `MessageDigest.isEqual` HMAC compare; missing `:` / empty userId → empty). Run → green; 100% branch on verify/parse.
- [x] 2.3 (GREEN) Update `SessionAuthFilter`: gate on `tokenService.verify(token).isPresent()`; on a valid token set request attribute `ensemble.userId`. Keep `HIGHEST_PRECEDENCE` registration. (Do not leave the old no-arg `issue()` referenced anywhere — `AuthController` is rewritten in 2.5–2.6.)
- [x] 2.4 (GREEN) Add a current-user accessor: a `HandlerMethodArgumentResolver` (e.g. `@CurrentUserId String`) reading the `ensemble.userId` attribute, registered via a `WebMvcConfigurer`.
- [x] 2.5 (RED) Rewrite `AuthControllerTest` for email/password login: `validLogin_returns200WithToken`, `wrongPassword_returns401Generic`, `unknownEmail_returns401Generic`, `malformedBody_returns400` (mock `UserRepository`, `PasswordHasher`, `SessionTokenService`). Add the `/api/me` cases to the full-context `SessionAuthFilterTest`. Run → fails.
- [x] 2.6 (GREEN) Repurpose `AuthController` (`POST /api/auth`): change `AuthRequest` to `record AuthRequest(@NotBlank @Email String email, @NotBlank String password)`; `@Valid` bind → `findByEmail` → `PasswordHasher.matches` (compare against a fixed dummy bcrypt hash when the user is absent) → success `tokenService.issue(user.getUserId())` → `200 { token }`; unknown-email OR wrong-password → `InvalidCredentialsException`. Remove the passcode check from `AuthController`. Run → green; 100% branch on the match/absent path.
- [x] 2.7 (GREEN) Implement `MeController` (`user/web/MeController.java`, `GET /api/me`) using the `@CurrentUserId` resolver → `UserRepository.findByUserId(userId)` → `MeResponse(userId, email)`. Add `MeController` to `ApiExceptionHandler` `assignableTypes`. Run the full-context `/api/me` tests → green.
- [x] 2.8 (GREEN) Add `InvalidCredentialsException` (`security/InvalidCredentialsException.java`) and map it in `ApiExceptionHandler` → `401 ("unauthorized","invalid email or password")`. Run → green.
- [x] 2.9 (REFACTOR + proof) Run `./gradlew test -PskipFrontend` (green) + JaCoCo (100% branch on token verify/parse + login match path). Capture the CLI proof (`POST /api/auth` → token; `GET /api/me` with/without header) using a throwaway local user (a directly-seeded users-table row, or after Task 3.0's signup).

### [x] 3.0 Sign-up endpoint `POST /api/accounts` behind the signup passcode (auto-login)

Lets a user create an account, invite-only. `AccountController` validates input,
verifies the shared signup passcode with a constant-time SHA-256 compare
(`SignupPasscodeVerifier`), enforces the 72-byte bcrypt limit, hashes the
password, creates the row, and returns `201 { token }` (auto-login via
`tokenService.issue(userId)` from Task 2.0). The filter's open-route list is
extended so the endpoint is reachable token-free.

#### 3.0 Proof Artifact(s)

- Test: `SignupPasscodeVerifierTest` passes `matchesConfiguredPasscode`, `rejectsWrongPasscode`, `rejectsBlankOrNullCandidate`, `signupClosedWhenPasscodeUnconfigured` — demonstrates the constant-time signup-passcode check with 100% branch (incl. the "blank configured passcode → gate closed" case).
- Test: `MaxUtf8BytesValidatorTest` passes `accepts72Bytes`, `rejects73Bytes`, `countsMultiByteCharsAsBytes`, `nullIsValidDelegatedToNotBlank` — demonstrates the ≤72-byte password guard with 100% branch.
- Test: `AccountControllerTest` (MockMvc) passes `validSignup_returns201WithToken`, `duplicateEmail_returns409`, `wrongSignupPasscode_returns401_noUserCreated`, `blankOrShortPassword_returns400`, `invalidEmail_returns400`, `passwordOver72Bytes_returns400` — demonstrates the signup contract and validation branches, and that a rejected signup creates no user.
- CLI: `curl -s -o /dev/null -w '%{http_code}' -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' -d '{"email":"demo@example.com","password":"<demo-pass>","passcode":"<signup-code>"}'` prints `201`; repeating the same email prints `409`; a wrong passcode prints `401`; then `POST /api/auth` with the new creds returns `200` (auto-login round-trip) — throwaway demo values only.

#### 3.0 Tasks

- [x] 3.1 (RED) Write `SignupPasscodeVerifierTest` (`security/SignupPasscodeVerifierTest.java`): `matchesConfiguredPasscode`, `rejectsWrongPasscode`, `rejectsBlankOrNullCandidate`, `signupClosedWhenPasscodeUnconfigured`. Run → fails.
- [x] 3.2 (GREEN) Implement `SignupPasscodeVerifier` (`security/SignupPasscodeVerifier.java`, `@Component`) holding `SecurityProperties`; `boolean matches(String candidate)` using the constant-time SHA-256 compare factored out of the old `AuthController` (null/blank candidate → false; blank configured passcode → always false). Update `SecurityConfig.logGateState()` wording to reflect the signup gate. Run → green; 100% branch.
- [x] 3.3 (RED) Write `MaxUtf8BytesValidatorTest` (`user/web/MaxUtf8BytesValidatorTest.java`): 72-byte valid, 73-byte invalid, multi-byte UTF-8 counted by bytes, null valid (delegated to `@NotBlank`). Run → fails.
- [x] 3.4 (GREEN) Implement `@MaxUtf8Bytes` + `MaxUtf8BytesValidator` (`user/web/`); create `SignupRequest` (`user/web/SignupRequest.java`): `@NotBlank @Email String email`, `@NotBlank @Size(min = 8) @MaxUtf8Bytes(72) String password`, `@NotBlank String passcode`. Run → green; 100% branch on the validator.
- [x] 3.5 (RED) Write `AccountControllerTest` (`user/web/AccountControllerTest.java`, `@WebMvcTest(AccountController.class)`): the six cases above; mock `UserRepository`, `PasswordHasher`, `SignupPasscodeVerifier`, `SessionTokenService`; assert no `create(...)` on the passcode-fail path. Run → fails.
- [x] 3.6 (GREEN) Implement `AccountController` (`user/web/AccountController.java`, `POST /api/accounts`): `@Valid @RequestBody SignupRequest` → `SignupPasscodeVerifier.matches` (else `InvalidPasscodeException`) → build `User` (UUID `userId`, `createdAt`, `passwordHash = hasher.hash(password)`, normalized email) → `UserRepository.create` (`DuplicateEmailException` → 409) → `tokenService.issue(userId)` → `201 { token }` (reuse `AuthResponse`). Run → green.
- [x] 3.7 (GREEN) Extend `SessionAuthFilter.isOpen(...)` to allow `POST /api/accounts`; add a `SessionAuthFilterTest` `accounts_isOpen` case. Register `AccountController` in `ApiExceptionHandler` `assignableTypes`; map `DuplicateEmailException` → `409 ("conflict","email already registered")` (+ handler test). Run → green.
- [x] 3.8 (REFACTOR + proof) Run `./gradlew test -PskipFrontend` (green) + JaCoCo (100% branch on signup-passcode check + password validation). Capture the CLI proof (signup 201 / duplicate 409 / wrong passcode 401 / login round-trip) with throwaway values.

### [x] 4.0 Seed account, login/sign-up UI, and documentation

Makes the change usable and provides the transition path. A `SeedAccountRunner`
(`ApplicationRunner`) idempotently creates a default account from
`ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` (bypassing the signup passcode,
server-side). The frontend `AuthGate` becomes a toggleable login / sign-up form
in the Care Label design system, with `api/auth.ts` gaining `login`/`signup`.
Docs, `.env.example`, and Terraform secret wiring describe the account model.

#### 4.0 Proof Artifact(s)

- Test: `SeedAccountRunnerTest` passes `seedsWhenAbsentAndConfigured`, `skipsWhenAccountExists`, `noOpWhenUnconfigured` — demonstrates the idempotent, env-gated transition path.
- Test: frontend `api/auth.test.ts` + `components/AuthGate.test.tsx` (Vitest/RTL) pass — no token → login screen; toggle to sign-up; submit login/sign-up → token stored → app renders; a `401`/`409`/`400` response shows the correct non-enumerating inline error — demonstrates the client auth-state and API logic (view plumbing kept light per `docs/TESTING.md`).
- Screenshot: the login screen, the sign-up screen (both Care Label styling — `--paper`/`--ink`/`--accent`, mobile-first ≤30rem), and the app rendered after a successful login, captured with throwaway demo values — demonstrates the end-to-end UI.
- Diff: `docs/ARCHITECTURE.md` ("Security"), `README.md` (passcode-gate section), `.env.example` (`ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` placeholders only), and `terraform/deploy/apprunner.tf` (seed creds as empty `runtime_environment_secrets` containers) updated; `terraform fmt -check` + `validate` pass — demonstrates docs + config reflect the account model with no committed secret.

#### 4.0 Tasks

- [x] 4.1 (RED) Write `SeedAccountRunnerTest` (`src/test/java/com/ensemble/user/SeedAccountRunnerTest.java`): `seedsWhenAbsentAndConfigured` (env set, no existing user → `create` with hashed pw), `skipsWhenAccountExists` (`findByEmail` present → no `create`), `noOpWhenUnconfigured` (blank env → no repo calls, logged). Mock `UserRepository` + `PasswordHasher`. Run → fails.
- [x] 4.2 (GREEN) Add `SeedProperties` (`config/SeedProperties.java`, `@ConfigurationProperties("ensemble.seed")`, masked `toString`) + `application.yml` `ensemble.seed.email/password: ${ENSEMBLE_SEED_EMAIL:}/${ENSEMBLE_SEED_PASSWORD:}`. Implement `SeedAccountRunner` (`user/SeedAccountRunner.java`, `ApplicationRunner`, `@Order` after `DynamoDbTableInitializer`, **not** gated on `auto-create-table` so it runs in cloud): create the hashed account when both env vars set and `findByEmail` empty; idempotent skip; no-op + log when unset. Run → green.
- [x] 4.3 (RED) Update `frontend/src/api/auth.test.ts`: `login(email,password)` POSTs `{email,password}` to `/api/auth` + stores token; `signup(email,password,passcode)` POSTs `{email,password,passcode}` to `/api/accounts` + stores token; each throws + stores nothing on a non-2xx. Run → fails.
- [x] 4.4 (GREEN) Extend `frontend/src/api/auth.ts`: add `login`/`signup` (both store `{token}`), keep `getToken`/`clearToken`/`SESSION_TOKEN_STORAGE_KEY`. (`http.ts` transport unchanged.) Run → green.
- [x] 4.5 (RED) Update `frontend/src/components/AuthGate.test.tsx`: no token → login screen; toggle → sign-up form; submit login/sign-up → token stored → children render; error copy — `401`→"Invalid email or password.", `409`→"That email is already registered.", `400`→"Password must be at least 8 characters.", signup `401`→"That signup code isn't valid." Run → fails.
- [x] 4.6 (GREEN) Rework `AuthGate.tsx` into a toggleable login / sign-up form using Care Label tokens (`--paper`/`--paper-raised`/`--ink`/`--accent`, Bricolage/Space Mono, ≤30rem, ≥44px targets, `type=email`/`password` + correct `autoComplete`, `:focus-visible`/`prefers-reduced-motion`), mapping status codes to the inline error copy. Run → green.
- [x] 4.7 Docs + config: update `docs/ARCHITECTURE.md` ("Security") + `README.md` (passcode-gate section) to describe email/password accounts, the signup-passcode's new role, the seed account, and retirement of single-passcode login; add `ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD` (placeholders) to `.env.example` and note `ENSEMBLE_PASSCODE` is now the signup gate; add the two seed secrets as empty `runtime_environment_secrets` containers (+ `aws_secretsmanager_secret` declarations) in `terraform/deploy/apprunner.tf`. `terraform fmt -check` + `validate` pass.
- [x] 4.8 (proof) Run `./gradlew test -PskipFrontend` + `cd frontend && npm run test -- --run` + `npm run lint` (all green); run the app and capture the login / sign-up / post-login screenshots (throwaway values). Confirm the pre-commit secret scan passes (no committed password/passcode/seed). _(Screenshots waived by the project owner; behavior proven by the 10 `AuthGate.test.tsx` RTL cases + a live end-to-end auth round-trip against a fresh current-build server — see `14-proofs/14-task-04-proofs.md`.)_
