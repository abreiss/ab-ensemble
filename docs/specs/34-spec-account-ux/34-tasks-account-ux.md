# 34-tasks-account-ux.md

> Task list for [`34-spec-account-ux.md`](./34-spec-account-ux.md) — GitHub issue [#34](https://github.com/abreiss/ab-ensemble/issues/34).
> Parent tasks map one-to-one to the spec's four Demoable Units of Work; commit roughly one per parent task (conventional commits + project `Co-Authored-By` trailer).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/user/User.java` | Account model. Move `@DynamoDbPartitionKey` to `getUsername()`, drop `email`, `normalizeEmail` → `normalizeUsername`; `setUsername` normalizes. |
| `src/test/java/com/ensemble/user/UserTest.java` | Unit test for `User`; assert username lowercase/trim normalization + `normalizeUsername(null)` null-safety. |
| `src/main/java/com/ensemble/user/UserRepository.java` | `findByEmail` → `findByUsername`; conditional put `attribute_not_exists(username)`; throw `DuplicateUsernameException`. |
| `src/test/java/com/ensemble/user/UserRepositoryIT.java` | DynamoDB Local IT; username round-trip/duplicate/case-insensitive; fix hardcoded `"email"` key literal passed to `ensureTable(...)`. |
| `src/main/java/com/ensemble/user/DuplicateEmailException.java` | Rename to `DuplicateUsernameException.java` (class + filename). |
| `src/main/java/com/ensemble/security/InvalidCredentialsException.java` | No-arg message → `"invalid username or password"`. |
| `src/main/java/com/ensemble/user/web/SignupRequest.java` | Replace `@Email` with `@NotBlank @Size(min=3,max=30) @Pattern(...)` username; keep `password` `@Size(min=8)`+`@MaxUtf8Bytes(72)` and `passcode`. |
| `src/main/java/com/ensemble/security/dto/AuthRequest.java` | Replace `@Email` with the same username `@Size`/`@Pattern`. |
| `src/main/java/com/ensemble/user/web/AccountController.java` | `POST /api/accounts`; `user.setUsername(...)`, reword copy; still returns `201` + token. |
| `src/test/java/com/ensemble/user/web/AccountControllerTest.java` | Signup contract + every username-validation rejection branch. |
| `src/main/java/com/ensemble/security/web/AuthController.java` | `POST /api/auth`; `users.findByUsername(...)`, retain dummy-hash timing defense. |
| `src/test/java/com/ensemble/security/web/AuthControllerTest.java` | Non-enumerating `401`; `verify(passwordHasher).matches(...)` on unknown username. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | `401` message → username; `handleDuplicateEmail` → `handleDuplicateUsername` (`409` `"username already registered"`). |
| `src/main/java/com/ensemble/user/SeedAccountRunner.java` | Seed via `findByUsername`, `setUsername`; idempotent; bypasses passcode. |
| `src/test/java/com/ensemble/user/SeedAccountRunnerTest.java` | Username-keyed seed idempotency. |
| `src/main/java/com/ensemble/config/SeedProperties.java` | Record component `email` → `username`; `configured()`; toString mask; binds `ENSEMBLE_SEED_USERNAME`. |
| `src/test/java/com/ensemble/config/SeedPropertiesTest.java` | `configured()` true only when both set; toString masks username. |
| `src/main/java/com/ensemble/user/web/MeController.java` | `MeResponse(userId, username)`; drop `email`. |
| `src/main/java/com/ensemble/config/DynamoDbTableInitializer.java` | `USERS_PARTITION_KEY = "username"`; users table stays GSI-free. |
| `src/test/java/com/ensemble/config/DynamoDbTableInitializerIT.java` | Assert users table partition key is `"username"`, still no `userId-index`. |
| `src/test/java/com/ensemble/security/web/SessionAuthFilterTest.java` | `/api/me` shape (`userId`+`username`); open-route username request bodies. |
| `src/main/resources/application.yml` | `ensemble.seed.username: ${ENSEMBLE_SEED_USERNAME:}`. |
| `frontend/src/api/auth.ts` | `login(username,password)` / `signup(username,password,passcode)`; bodies `{username,...}`; `getToken`/`clearToken` unchanged. |
| `frontend/src/api/auth.test.ts` | Client posts username bodies to `/api/auth` + `/api/accounts` (no `email` key). |
| `frontend/src/components/AuthGate.tsx` | Username field (label "Username", `id=auth-username`, `autoComplete=username`); reworded errors; confirm-password (signup-only). |
| `frontend/src/components/AuthGate.test.tsx` | Username-label query; reworded copy; confirm-password mismatch/match/body cases. |
| `frontend/src/App.tsx` | Add **Sign out** control to `<nav className="app-nav">`; handler `clearToken()` + dispatch `ensemble:auth-required`. |
| `frontend/src/App.test.tsx` | Gated-route anchor uses username label; `signOut_clearsTokenAndReturnsToLogin`. |
| `frontend/src/api/http.ts` | Source of the `ensemble:auth-required` event name + `onAuthRequired` subscription reused by sign-out (reference; likely no change). |
| `frontend/src/components/TagForm.tsx`, `frontend/src/lib/tagValidation.ts` | Reference pattern for inline per-field validation (`touched` + `field-error` span + submit gate) that confirm-password mirrors. |
| `frontend/src/index.css` | `.field-error`, `.app-nav`, `.btn` styles reused; small additions only if needed. |
| `.env.example` | `ENSEMBLE_SEED_EMAIL` → `ENSEMBLE_SEED_USERNAME`. |
| `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md` | Reword account narrative email → username + `ENSEMBLE_SEED_USERNAME`; document the operator-confirmed local migration. |

### Notes

- **Backend tests** run with `./gradlew test -PskipFrontend`; a single class via `--tests '*AccountControllerTest'`. Coverage report via `./gradlew jacocoTestReport` (report only — there is no enforced JaCoCo threshold in `build.gradle`/CI, so the 100%-branch bar is verified by reading the report).
- **Frontend tests** run with `cd frontend && npm test -- --run` (Vitest + RTL); lint via `npm run lint`.
- **Strict TDD** (RED → GREEN → REFACTOR) on all of 1.0 (backend domain): ≥90% line; **100% branch** on username validation, login non-enumeration, the duplicate-username conditional, and seed idempotency. Frontend units (2.0–4.0) test meaningful logic only — do not over-test view plumbing.
- **Never call the live Claude API or a live network** in any test; mock external boundaries; use DynamoDB Local (TestContainers) for repository ITs.
- **Screenshots are supplementary, not a gate.** Test + CLI + diff artifacts are the acceptance evidence; UI screenshots may be attached but are not required.
- **Pre-commit gates** (backend tests, frontend tests, frontend lint, secret scans) must pass; all proofs use synthetic credentials only.

## Tasks

### [x] 1.0 Username-based accounts — backend (data model, DTOs, controllers, exceptions, seed, `/api/me`)

Replace email with username across the account domain: `User` partition key, `UserRepository`, DTO validation, controllers, exceptions, seed, table initializer, and `/api/me`. Strict-TDD backend core; email is removed entirely. Preserves the invite passcode, bcrypt hashing, the dummy-hash timing defense, and the opaque-`userId` session token. Demoable end-to-end via curl (signup → login → `/api/me`, plus duplicate/invalid/wrong-credential paths).

#### 1.0 Proof Artifact(s)

- Test: `AccountControllerTest` passes `validSignup_returns201WithToken`, `duplicateUsername_returns409`, `wrongSignupPasscode_returns401_noUserCreated`, `blankOrShortPassword_returns400`, `invalidUsername_returns400` (blank, too short, too long, illegal char, leading/trailing separator), `passwordOver72Bytes_returns400` — demonstrates the signup contract and every username-validation branch. (`./gradlew test -PskipFrontend --tests '*AccountControllerTest'`)
- Test: `AuthControllerTest` passes `validLogin_returns200WithToken`, `wrongPassword_returns401Generic`, `unknownUsername_returns401Generic` — same body `{"unauthorized","invalid username or password"}` **and** `verify(passwordHasher).matches(...)` proving the dummy-hash timing path runs on the unknown-username branch. Demonstrates non-enumerating login.
- Test: `UserRepositoryIT` (DynamoDB Local via TestContainers) passes `createThenFindByUsername`, `create_duplicateUsername_throwsDuplicateUsernameException` (first row not overwritten), `findByUsername_isCaseAndSpaceInsensitive` — demonstrates atomic conditional-put uniqueness on a real round-trip.
- Test: `SeedAccountRunnerTest` + `SeedPropertiesTest` pass `seedsWhenAbsentAndConfigured` (username normalized, hash not raw), `skipsWhenAccountExists` (idempotent via `findByUsername`), `noOpWhenUnconfigured`, `configured()` true only when both set — demonstrates username-keyed seed idempotency.
- Test: `UserTest.usernameIsNormalizedToLowercaseTrimmed` + `normalizeUsername_isNullSafe`, and `DynamoDbTableInitializerIT` asserting the users table partition key is `"username"` (still GSI-free) — demonstrates the model + table-key change.
- Test: `SessionAuthFilterTest.me_withValidToken_returns200WithUserIdAndUsername` asserts `$.userId` and `$.username` (no `$.email`) — demonstrates the new `/api/me` shape and that an existing token still resolves.
- CLI: `curl -s -X POST localhost:8080/api/accounts -d '{"username":"jane_doe","password":"correcthorse","passcode":"<invite>"}'` → `201` + token; repeating same username → `409`; `POST /api/auth {"username":"jane_doe","password":"correcthorse"}` → `200` + token; wrong password or unknown username → identical `401`. Synthetic credentials only. Demonstrates end-to-end username signup + login.
- Coverage: JaCoCo report (`./gradlew jacocoTestReport`) shows username validation, the login non-enumeration path, and the duplicate-username conditional at 100% branch — demonstrates the critical-logic coverage bar.

#### 1.0 Tasks

- [x] 1.1 (RED) Rewrite `UserTest`: assert `setUsername("  Foo_Bar  ")` → `getUsername()` == `"foo_bar"` and `User.normalizeUsername(null)` is null; remove the email-normalization assertions. Run `./gradlew test -PskipFrontend --tests '*UserTest'` and confirm it fails to compile/pass (RED).
- [x] 1.2 (GREEN) In `User.java`, add a `username` field with `@DynamoDbPartitionKey` on `getUsername()`, `setUsername` calling a new `public static String normalizeUsername(String)` (`trim().toLowerCase(Locale.ROOT)`, null-safe); remove the `email` field, its getter/setter, the `@DynamoDbPartitionKey` on `getEmail()`, and `normalizeEmail`; update the class Javadoc (partition key + `attribute_not_exists(username)`). Make `UserTest` green.
- [x] 1.3 (RED) Rename `DuplicateEmailException` → `DuplicateUsernameException` (class name + filename). Update `UserRepositoryIT`: `sample(String username)`, `createThenFindByUsername`, `create_duplicateUsername_throwsDuplicateUsernameException` (assert first row not overwritten), `findByUsername_isCaseAndSpaceInsensitive` (e.g. `"  Mixed_Name  "`), keep the `findByUserId*` cases (built via username), and change the hardcoded `ensureTable(usersTable, "email")` literal to `"username"`. Run the IT and confirm RED.
- [x] 1.4 (GREEN) In `UserRepository.java`: `findByEmail` → `findByUsername` (normalize via `User.normalizeUsername`), conditional expression `attribute_not_exists(username)`, throw `new DuplicateUsernameException("username already registered")`. Make `UserRepositoryIT` green.
- [x] 1.5 (RED) Update `AccountControllerTest`: username body helper; `validSignup_returns201WithToken` (mixed-case username stored normalized), `duplicateUsername_returns409` (body `"username already registered"`), `wrongSignupPasscode_returns401_noUserCreated`, `blankOrShortPassword_returns400`, `passwordOver72Bytes_returns400`, and a parameterized `invalidUsername_returns400` covering blank, `<3`, `>30`, illegal char (`"bad name"`), leading separator (`"_bad"`), trailing separator (`"bad_"`). Run and confirm RED.
- [x] 1.6 (GREEN) In `SignupRequest.java`, replace `@Email String email` with `@NotBlank @Size(min=3,max=30) @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{1,28}[A-Za-z0-9]$") String username` (keep password + passcode constraints). In `AccountController.java`, `user.setUsername(request.username())` and reword Javadoc/log copy. Make `AccountControllerTest` green.
- [x] 1.7 (RED) Update `AuthControllerTest`: `userWith(userId, username, hash)`; `validLogin_returns200WithToken`, `wrongPassword_returns401Generic` (body `"invalid username or password"`), `unknownUsername_returns401Generic` (same body **and** `verify(passwordHasher).matches(...)`). Run and confirm RED.
- [x] 1.8 (GREEN) In `AuthRequest.java`, replace `@Email` with `@NotBlank @Size(min=3,max=30) @Pattern(...)` username. In `AuthController.java`, `users.findByUsername(request.username())`, keep the `dummyHash` timing path. Make `AuthControllerTest` green.
- [x] 1.9 (GREEN) In `InvalidCredentialsException.java`, message → `"invalid username or password"`. In `ApiExceptionHandler.java`, set `handleInvalidCredentials` message to `"invalid username or password"`, and rename `handleDuplicateEmail` → `handleDuplicateUsername` bound to `DuplicateUsernameException` → `409` `"username already registered"`. Confirm controller tests stay green.
- [x] 1.10 (RED) Update `SeedPropertiesTest` (username-field variants: null→blank, both set → configured, only username → not configured, only password → not configured; toString masks username) and `SeedAccountRunnerTest` (`seedsWhenAbsentAndConfigured` via `findByUsername` + normalized username + hashed password, `skipsWhenAccountExists`, `noOpWhenUnconfigured`). Run and confirm RED.
- [x] 1.11 (GREEN) In `SeedProperties.java`, rename the record component `email` → `username`, update `configured()`, `toString()` mask, and Javadoc (`ENSEMBLE_SEED_USERNAME`). In `SeedAccountRunner.java`, use `findByUsername(props.username())` and `setUsername`. In `application.yml`, set `ensemble.seed.username: ${ENSEMBLE_SEED_USERNAME:}`. Make the seed tests green.
- [x] 1.12 (RED) Update `SessionAuthFilterTest`: `me_withValidToken_returns200WithUserIdAndUsername` (`$.userId` + `$.username`, assert no `$.email`), `authAndHealth_areOpen` (username body → `"invalid username or password"`), `accounts_isOpen` (username signup body). Run and confirm RED.
- [x] 1.13 (GREEN) In `MeController.java`, change `MeResponse` to `(String userId, String username)` and map `user.getUsername()`. Make `SessionAuthFilterTest` green.
- [x] 1.14 (RED→GREEN) Update `DynamoDbTableInitializerIT` to assert the users table partition key is `"username"` (still no `userId-index`); then set `DynamoDbTableInitializer.USERS_PARTITION_KEY = "username"`. Make the IT green.
- [x] 1.15 (REFACTOR) Grep `com.ensemble.user`, `com.ensemble.security`, and the account test tree for residual `email`/`Email`/`@Email`; confirm none remain in the account path. Run `./gradlew test -PskipFrontend` (all green) + `./gradlew jacocoTestReport`; confirm 100% branch on username validation, login non-enumeration, the duplicate-username conditional, and seed idempotency. Capture the curl proof transcript (synthetic credentials).

### [x] 2.0 Username-based accounts — auth UI, client, docs, and gated local migration

Surface `username` in `AuthGate`, drop email from the client (`api/auth.ts`) and all auth copy, update the docs/config to `ENSEMBLE_SEED_USERNAME`, and perform the account-table recreation safely at local demo scale — explicit, operator-confirmed, never an automatic drop and never row enumeration-and-delete against a shared store. Depends on 1.0 (backend contract). Demoable via the UI, the `/api/me` CLI proof, and the docs diff.

#### 2.0 Proof Artifact(s)

- Test: `frontend/src/api/auth.test.ts` passes updated cases asserting `login` posts `{ username, password }` to `/api/auth` and `signup` posts `{ username, password, passcode }` to `/api/accounts` (no `email` key) — demonstrates the client contract. (`cd frontend && npm test -- --run src/api/auth.test.ts`)
- Test: `frontend/src/components/AuthGate.test.tsx` passes with the field found via `getByLabelText(/^username$/i)` and asserts the reworded error copy (e.g. "Invalid username or password.", "That username is already registered.") — demonstrates the UI uses username.
- Test: `frontend/src/App.test.tsx` passes with the gated-route anchor query switched to the username label — demonstrates no regression in the auth gate.
- CLI: `curl -s localhost:8080/api/me -H 'X-Ensemble-Session: <token>'` → `{"userId":"…","username":"…"}` after a username login — demonstrates the migrated identity end-to-end.
- Diff: `.env.example`, `README.md`, `docs/DEVELOPMENT.md`, `docs/ARCHITECTURE.md` updated to username + `ENSEMBLE_SEED_USERNAME` — demonstrates docs/config reflect the account model.
- Doc/Log: a written, operator-confirmed local-migration procedure (drop the DynamoDB-Local `ensemble-users` table → dev startup auto-recreates with the username key → reseed via `ENSEMBLE_SEED_USERNAME`), with evidence that the implementation performs **no** automatic table drop and **no** row enumeration — demonstrates destructive-migration hygiene (Resolved Decision D2).

#### 2.0 Tasks

- [x] 2.1 (RED) Update `frontend/src/api/auth.test.ts`: `login` posts `{ username, password }` to `/api/auth`; `signup` posts `{ username, password, passcode }` to `/api/accounts` and the body has **no** `email` key; keep the HttpError/network-failure and `getToken`/`clearToken` cases. Run `npm test -- --run src/api/auth.test.ts` and confirm RED.
- [x] 2.2 (GREEN) In `frontend/src/api/auth.ts`, rename the `login`/`signup` params to `username` and build bodies `{ username, password }` / `{ username, password, passcode }` (leave `getToken`/`clearToken`/`SESSION_TOKEN_STORAGE_KEY` and the shared `postForToken` helper otherwise unchanged). Make the client test green.
- [x] 2.3 (RED) Update `frontend/src/components/AuthGate.test.tsx`: switch every `getByLabelText(/^email$/i)` anchor to `/^username$/i`; update the five error-copy assertions to the reworded strings — login `401` "Invalid username or password.", login `400` "Enter a valid username and password.", signup `409` "That username is already registered.", signup `400` "Check your username, password, and signup code.", signup `401` unchanged. Run and confirm RED.
- [x] 2.4 (GREEN) In `AuthGate.tsx`: rename the `email` state/setter to `username`; render a **Username** field (label "Username", `id="auth-username"`, `type="text"`, `autoComplete="username"`); update `canSubmit`, `handleSubmit`, and `toggleMode` to use `username`; reword the `errorMessageFor` strings. Make `AuthGate.test.tsx` green.
- [x] 2.5 (RED→GREEN) Update `frontend/src/App.test.tsx`: switch the gated-route anchor `findByLabelText(/^email$/i)` to `/^username$/i` (no `App.tsx` change required for this). Run `npm test -- --run` and confirm the full frontend suite is green; run `npm run lint`.
- [x] 2.6 (Docs) Update `.env.example` (`ENSEMBLE_SEED_EMAIL` → `ENSEMBLE_SEED_USERNAME`), and reword the account/seed narrative to username in `README.md`, `docs/DEVELOPMENT.md`, and `docs/ARCHITECTURE.md` (login/signup bodies now `{username,...}`, seed env var `ENSEMBLE_SEED_USERNAME`). Note the out-of-band Terraform follow-up (`ENSEMBLE_SEED_EMAIL` in `terraform/deploy/apprunner.tf`) in the migration doc if the audit FLAG is accepted.
- [x] 2.7 (Migration) Write the operator-confirmed local-migration procedure (in `README.md` and/or `docs/DEVELOPMENT.md`): stop the app → drop the DynamoDB-Local `ensemble-users` table (explicit operator command, e.g. `aws dynamodb delete-table` against the local endpoint) → restart so dev startup auto-recreates it with the username key → set `ENSEMBLE_SEED_USERNAME`/`ENSEMBLE_SEED_PASSWORD` and restart to reseed. Confirm by inspection/grep that the implementation adds **no** automatic table-drop code and **no** enumerate-and-delete of rows against the shared/dev store.
- [x] 2.8 (Verify) With DynamoDB Local recreated and a seed username configured, log in via the UI, then capture the `/api/me` curl proof returning `{"userId":"…","username":"…"}` (synthetic credentials). Re-run the backend suite to confirm no regressions.

### [x] 3.0 Sign-out control

Add a visible **Sign out** control to the app nav (`App.tsx`), shown only when signed in. Clicking it calls `clearToken()` then dispatches the existing `ensemble:auth-required` window event, reusing the exact 401 re-auth machinery so `AuthGate` returns to the login form. No backend change (stateless-token discard; Resolved Decision D3). Depends on 2.0 (login form is now username-anchored). Demoable via RTL.

#### 3.0 Proof Artifact(s)

- Test: an `App`/`AuthGate` RTL test passes `signOut_clearsTokenAndReturnsToLogin` — seeds a token, renders the app shell, clicks **Sign out**, and asserts `getToken()` is `null` and the login form (`getByLabelText(/^username$/i)`) is shown — demonstrates the client-side discard + gate return. (`cd frontend && npm test -- --run`)
- Test/assertion that after sign-out `sessionStorage` no longer holds `ensemble.session.token` — demonstrates the token is truly discarded.

#### 3.0 Tasks

- [x] 3.1 (RED) Add `signOut_clearsTokenAndReturnsToLogin` to `frontend/src/App.test.tsx`: seed a token (`sessionStorage.setItem(SESSION_TOKEN_STORAGE_KEY, 'test-token')`), render `<App>` in the router, assert the shell is shown, click the **Sign out** control (`getByRole('button', { name: /sign out/i })`), then assert `getToken()` is `null`, `sessionStorage.getItem('ensemble.session.token')` is `null`, and `findByLabelText(/^username$/i)` (the login form) is shown. Run and confirm RED.
- [x] 3.2 (GREEN) In `App.tsx`, add a **Sign out** control to `<nav className="app-nav">` (styled with the existing `.btn` class), rendered as part of the gated shell (only reachable when signed in). Its `onClick` calls `clearToken()` (imported from `api/auth`) then `window.dispatchEvent(new CustomEvent('ensemble:auth-required'))`, using the exact event name `api/http.ts` already dispatches/subscribes to. Make the test green.
- [x] 3.3 (REFACTOR) Confirm the event-name string matches the single source in `api/http.ts` (reuse an exported constant if one exists rather than duplicating the literal); add minimal CSS only if the button needs it. Run `npm test -- --run` + `npm run lint`.

### [x] 4.0 Password confirmation on signup

Add a **Confirm password** field shown only in signup mode, mirroring the `TagForm`/`tagValidation` inline per-field pattern (per-field error, `touched` gate, submit blocked while mismatched). The confirmation value is never sent to the API — the `POST /api/accounts` body stays `{ username, password, passcode }` (no backend change; Resolved Decision D4). Login mode unchanged. Depends on 2.0. Demoable via RTL.

#### 4.0 Proof Artifact(s)

- Test: `frontend/src/components/AuthGate.test.tsx` passes `signup_confirmMismatch_blocksSubmitAndShowsError`, `signup_confirmMatch_submits`, and `signup_body_excludesConfirmValue` (posted body has no confirmation key) — demonstrates the client-side check and the unchanged API contract. (`cd frontend && npm test -- --run src/components/AuthGate.test.tsx`)
- Test/assertion that the Confirm-password field is absent in login mode and present in signup mode — demonstrates it is signup-only.

#### 4.0 Tasks

- [x] 4.1 (RED) Add to `AuthGate.test.tsx`: `signup_confirmMismatch_blocksSubmitAndShowsError` (switch to signup, fill username/password/passcode + a mismatched confirm, assert an inline `field-error` is shown, the submit is blocked, and `signup` is not called), `signup_confirmMatch_submits` (matching confirm → `signup` called once), `signup_body_excludesConfirmValue` (mock fetch/`signup`, assert the posted body / call args carry no confirmation key), and a case asserting the confirm field is absent in login mode. Run and confirm RED.
- [x] 4.2 (GREEN) In `AuthGate.tsx`, add `confirmPassword` state and a `touched` flag; render a **Confirm password** field only when `isSignup` (label "Confirm password", `id="auth-confirm-password"`, `type="password"`, `autoComplete="new-password"`); mirror the `TagForm`/`tagValidation` pattern — show a `<span className="field-error">` after interaction when values mismatch and block submit (extend `canSubmit`/`handleSubmit`). Do **not** thread the confirm value into `signup(...)`. Make the confirm tests green.
- [x] 4.3 (REFACTOR) Reset `confirmPassword` and `touched` in `toggleMode`; verify login mode renders no confirm field. Run `npm test -- --run` + `npm run lint`.
