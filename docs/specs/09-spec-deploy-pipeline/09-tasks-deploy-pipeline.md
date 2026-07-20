# 09-tasks-deploy-pipeline.md

Task list for `09-spec-deploy-pipeline`. Parent tasks 1.0–5.0 are the spec's
**testable core** (delivered and validated in-workflow); parent task 6.0 is the
**operator-run** live deploy whose outputs are captured as proof (resolved
decision Q2 → A). Each parent task is a demoable, independently reviewable slice.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `build.gradle` | Add `software.amazon.awssdk:s3` under the existing AWS BOM `2.30.0`. |
| `src/main/java/com/ensemble/storage/S3PhotoStorage.java` | **New** — S3 implementation of `PhotoStorage` (compress-on-save, S3 CRUD). |
| `src/test/java/com/ensemble/storage/S3PhotoStorageTest.java` | **New** — unit test with a mocked `S3Client` (no live network). |
| `src/main/java/com/ensemble/storage/LocalDiskPhotoStorage.java` | Add `@ConditionalOnProperty(...havingValue="disk", matchIfMissing=true)` so it is the default bean. |
| `src/main/java/com/ensemble/config/PhotoProperties.java` | Add `backend` (`disk`/`s3`) and the `s3.bucket` binding. |
| `src/main/java/com/ensemble/config/StorageConfig.java` | **New** — conditional `S3Client` bean (default credential chain + region), guarded on `backend=s3`. |
| `src/test/java/com/ensemble/config/PhotoStorageSelectorTest.java` | **New** — `ApplicationContextRunner` selector test (exactly one bean per config). |
| `src/main/java/com/ensemble/config/DynamoDbConfig.java` | Make `endpointOverride` + dummy creds conditional on a non-blank endpoint. |
| `src/test/java/com/ensemble/config/DynamoDbConfigTest.java` | **New** — both-branch test (blank vs present endpoint). |
| `src/main/resources/application.yml` | Add `ensemble.photos.backend`/`s3.bucket`, `${ENSEMBLE_DYNAMODB_ENDPOINT:...}`, `${ENSEMBLE_DYNAMODB_TABLE_NAME:...}`; keep local defaults. |
| `src/test/resources/application.yml` | Confirm disk default + local endpoint so existing tests behave unchanged. |
| `.env.example` | Document the new `ENSEMBLE_PHOTOS_BACKEND`/`_S3_BUCKET`/`_DYNAMODB_ENDPOINT` env vars (no values). |
| `terraform/deploy/versions.tf` | **New** — `required_version >= 1.11.0`, `aws ~> 5.0`, S3 backend with `use_lockfile = true`. |
| `terraform/deploy/providers.tf` | **New** — provider + `aws_caller_identity`/`partition`/`region` data + `abreiss-ensemble-*` ARN locals. |
| `terraform/deploy/variables.tf` | **New** — `aws_region`, `resource_prefix`, GitHub repo/ref, App Runner min/max + size. |
| `terraform/deploy/data_stores.tf` | **New** — S3 photos bucket (public-access-block + SSE) + DynamoDB `abreiss-ensemble-items` table. |
| `terraform/deploy/ecr.tf` | **New** — ECR repo (scan-on-push, immutable tags, lifecycle policy). |
| `terraform/deploy/secrets.tf` | **New** — three Secrets Manager containers, no `secret_string`. |
| `terraform/deploy/apprunner.tf` | **New** — App Runner service (port 8080, `/api/health`, env vars + secret ARNs). |
| `terraform/deploy/iam.tf` | **New** — instance role + CI OIDC role + optional ECR access role, all boundary-attached. |
| `terraform/deploy/outputs.tf` | **New** — service URL, ECR URL, table/bucket names, CI role ARN. |
| `terraform/deploy/policies/*.json` | **New** — rendered instance-role + CI-role policy review copies (mirrors `terraform/bootstrap/policies/`). |
| `terraform/deploy/README.md` | **New** — module doc incl. the one-time state-bucket bootstrap + `init` flow. |
| `.github/workflows/deploy.yml` | **New** — OIDC build → ECR (git-SHA tag) → App Runner `start-deployment` → verify. |
| `.github/workflows/ci.yml` | **New** — backend + frontend tests, `terraform fmt -check`/`validate`, Access Analyzer policy-lint. |
| `README.md` | Deploy runbook (bootstrap → apply → secrets → push-to-deploy → URL → rollback). |
| `docs/DEVELOPMENT.md` | Operator deploy steps cross-referenced from the runbook. |
| `docs/ARCHITECTURE.md` | Update the deploy/security narrative to the shipped module + pipeline. |
| `docs/AWS_ACCESS.md` | Note the CI role + any narrowly-scoped `abreiss-ensemble-*` policy addition (if a gap is found). |

### Notes

- Unit tests sit alongside the code they test under `src/test/java/...`; run backend tests with `./gradlew test -PskipFrontend` (no Node needed) and coverage with `jacocoTestReport`.
- Follow strict TDD for parent task 1.0 (backend domain): write the failing test first, minimum code to pass, then refactor. Parent tasks 2.0–6.0 are Terraform/CI/ops glue → validated (`fmt`/`validate`/`plan` + Access Analyzer + live smoke), not coverage-gated (per `docs/TESTING.md`).
- Mock all AWS SDK clients in tests; **never** call live AWS or Claude in the `test` task.
- `terraform/deploy/` is self-contained and must not modify `terraform/bootstrap/`; reuse the same prefix/region/provider conventions and attach the `abreiss-ensemble-boundary` to every role it creates.
- **Cross-task dependency:** the App Runner service (3.0) references the instance role and optional access role authored in 4.0. Terraform is declarative, so authoring order across tasks does not matter, but both must exist before `plan`/`apply`; keep the ECR pull/image dependency in mind when the operator runs 6.0.
- Conventional commits, ~one per demoable unit (e.g. `feat(storage): S3PhotoStorage + backend selector`, `feat(infra): app runner + ecr + data stores`, `ci: OIDC build/push/deploy pipeline`, `docs: deploy runbook`).

## Tasks

### [x] 1.0 Backend cloud-readiness: `S3PhotoStorage`, storage-backend selector, cloud DynamoDB config (Unit 1 — strict TDD)

Make the same jar run against real AWS or local, selectable purely by configuration:
add `S3PhotoStorage` behind the existing `PhotoStorage` interface, a `@ConditionalOnProperty`
bean selector guaranteeing exactly one `PhotoStorage` bean, and a `DynamoDbConfig` that
drops `endpointOverride` + dummy creds when `ensemble.dynamodb.endpoint` is blank (falling
back to the default credential provider chain / App Runner instance role). AWS SDK clients
are mocked — no live network in tests. Local `bootRun` and existing tests behave exactly as before.

#### 1.0 Proof Artifact(s)

- Test: `S3PhotoStorageTest` passes with a mocked `S3Client` — asserts `save`/`load`/`delete` build the correct `PutObjectRequest`/`GetObjectRequest`/`DeleteObjectRequest` (bucket, key, `image/jpeg` content type) and that `ImageProcessor` compression is applied on save — demonstrates the S3 impl satisfies the `PhotoStorage` contract with no live call (FR: S3PhotoStorage).
- Test: storage-selector tests pass — with `ensemble.photos.backend=s3` exactly the `S3PhotoStorage` bean is present; with the default/`disk` exactly `LocalDiskPhotoStorage` — demonstrates 100% branch coverage on bean selection, no config yields zero or two beans (FR: backend selector).
- Test: `DynamoDbConfigTest` passes for both branches — blank/absent endpoint → no `endpointOverride`, default credentials provider; present endpoint → override + local dummy creds — demonstrates the local↔cloud switch is fully covered (FR: DynamoDbConfig cloud switch).
- CLI: `./gradlew test jacocoTestReport -PskipFrontend` is green and the JaCoCo report shows ≥90% line and 100% branch on the selector + DynamoDB endpoint/credentials logic — demonstrates the strict-TDD gate is met (Success Metric 1).
- Diff: `build.gradle` adds `software.amazon.awssdk:s3` under the existing BOM `2.30.0`; `application.yml` expresses `ensemble.photos.backend`, `ensemble.photos.s3.bucket`, and `endpoint: ${ENSEMBLE_DYNAMODB_ENDPOINT:http://localhost:8000}` with local defaults intact — demonstrates config-only cloud enablement (FR: config keys + relaxed-binding env vars).

#### 1.0 Tasks

- [x] 1.1 Add `software.amazon.awssdk:s3` to `build.gradle` under the existing `platform('software.amazon.awssdk:bom:2.30.0')`, alongside `dynamodb-enhanced`; refresh dependencies so `S3Client` resolves.
- [x] 1.2 Extend `PhotoProperties` with `backend` (default `disk`) and an `s3` bucket binding for `ensemble.photos.s3.bucket`, preserving the existing `dir`/`maxUploadPixels` fields and their defaults.
- [x] 1.3 RED — write `S3PhotoStorageTest` with a mocked `S3Client` (Mockito): `save` builds a `PutObjectRequest` (correct bucket, key, `image/jpeg`) carrying `ImageProcessor`-compressed bytes; `load` builds a `GetObjectRequest` and returns bytes; `delete` builds a `DeleteObjectRequest`; a missing key (S3 `NoSuchKeyException`) → `PhotoNotFoundException`. Tests fail (class absent).
- [x] 1.4 GREEN — implement `S3PhotoStorage implements PhotoStorage`, depending only on `S3Client`, `PhotoProperties` (bucket), and `ImageProcessor`; compress on `save`, map `NoSuchKeyException` → `PhotoNotFoundException`; keep all S3 types inside the class (no leak to services/controllers). Make 1.3 pass.
- [x] 1.5 Guard the beans: annotate `LocalDiskPhotoStorage` `@ConditionalOnProperty(name="ensemble.photos.backend", havingValue="disk", matchIfMissing=true)` and `S3PhotoStorage` `havingValue="s3"`; add `StorageConfig` providing a `S3Client` bean (default credential provider chain + region from config) guarded on `backend=s3` so no client is built in disk mode.
- [x] 1.6 RED→GREEN — `PhotoStorageSelectorTest` using `ApplicationContextRunner`: `backend=s3` → exactly one `PhotoStorage`, the S3 impl; default and `disk` → exactly `LocalDiskPhotoStorage`; assert the map size is always 1 (covers both conditional branches).
- [x] 1.7 RED — `DynamoDbConfigTest`: build the client via the bean method and assert (via `serviceClientConfiguration()`) that a blank/absent endpoint → empty `endpointOverride` + a default credentials provider, and a present endpoint → set `endpointOverride` + static creds. Tests fail against the current always-override config.
- [x] 1.8 GREEN — extract the endpoint-blank decision into a testable seam in `DynamoDbConfig` and branch: non-blank endpoint keeps override + dummy static creds; blank/absent uses `DefaultCredentialsProvider` and no override; region always from props. Make both branches pass.
- [x] 1.9 Wire config + defaults: in `application.yml` set `ensemble.photos.backend: ${ENSEMBLE_PHOTOS_BACKEND:disk}`, `ensemble.photos.s3.bucket: ${ENSEMBLE_PHOTOS_S3_BUCKET:}`, `endpoint: ${ENSEMBLE_DYNAMODB_ENDPOINT:http://localhost:8000}`, `table-name: ${ENSEMBLE_DYNAMODB_TABLE_NAME:ensemble-items}`; keep `src/test/resources/application.yml` on disk defaults; document the three new vars in `.env.example`; confirm `./gradlew bootRun` behaves as before.
- [x] 1.10 Run `./gradlew test jacocoTestReport -PskipFrontend`; confirm all tests green and ≥90% line / 100% branch on the selector + DynamoDB endpoint/credentials logic; record the JaCoCo report path as the proof artifact.

### [x] 2.0 Terraform deploy module scaffold + runtime data stores (Unit 2a — validated IaC)

Add a new self-contained `terraform/deploy/` root module alongside `terraform/bootstrap/`
(no collision, same prefix/region/provider conventions): `hashicorp/aws ~> 5.0`,
`required_version >= 1.11.0`, `aws_region` default `us-east-1`, `resource_prefix` default
`abreiss-ensemble`, account id from `data.aws_caller_identity` (never hard-coded). Configure
the **remote S3 backend with S3-native locking** (`use_lockfile = true`, no DynamoDB lock
table) targeting `abreiss-ensemble-tfstate`, and document/script the one-time state-bucket
bootstrap. Declare the runtime data stores: the S3 photos bucket (`abreiss-ensemble-photos`,
public access blocked, SSE) and the DynamoDB items table (`abreiss-ensemble-items`, `itemId`
PK, on-demand billing).

#### 2.0 Proof Artifact(s)

- CLI: `terraform -chdir=terraform/deploy fmt -check` and `terraform -chdir=terraform/deploy validate` exit `0` — demonstrates well-formed, canonically-formatted HCL (Success Metric 2).
- Terraform output: a `terraform plan` (operator-run, account-id redacted) enumerating the S3 photos bucket (public-access-block + SSE) and the DynamoDB `abreiss-ensemble-items` table (`itemId` PK, PAY_PER_REQUEST) — demonstrates the data-store footprint (FR: data stores).
- File: `versions.tf`/`backend`/`providers.tf` showing `required_version >= 1.11.0`, `use_lockfile = true` (no `dynamodb_table`), and the `abreiss-ensemble-tfstate` backend config — demonstrates S3-native locking on the supported path (FR: remote backend).
- File: the state-bucket bootstrap step (doc section or minimal script) creating the versioned/encrypted/public-access-blocked `abreiss-ensemble-tfstate` bucket within the prefix — demonstrates the chicken-and-egg step is reproducible by the scoped identity (FR: state bucket).

#### 2.0 Tasks

- [x] 2.1 Create `terraform/deploy/versions.tf`: `required_version >= 1.11.0`, `hashicorp/aws ~> 5.0`, and a `backend "s3"` block with `bucket = "abreiss-ensemble-tfstate"`, a `key`, `region`, and `use_lockfile = true` (no `dynamodb_table`).
- [x] 2.2 Add `providers.tf` (`provider "aws"` on `var.aws_region`; `data.aws_caller_identity`/`aws_partition`/`aws_region`; `locals` for the `abreiss-ensemble-*` bucket/table/ecr/apprunner/secret/role ARNs derived from account id + prefix) and `variables.tf` (`aws_region` default `us-east-1`, `resource_prefix` default `abreiss-ensemble`), mirroring `terraform/bootstrap/`; no hard-coded account id.
- [x] 2.3 Add the state-bucket bootstrap in `terraform/deploy/README.md` (and an optional `create-state-bucket.sh`): a reproducible one-time step creating `abreiss-ensemble-tfstate` (versioning on, SSE, all public access blocked) within the prefix using the scoped identity, plus the `terraform init` against the S3 backend.
- [x] 2.4 In `data_stores.tf` declare the S3 photos bucket `abreiss-ensemble-photos` with `aws_s3_bucket_public_access_block` (all `true`) and `aws_s3_bucket_server_side_encryption_configuration` (SSE); versioning optional.
- [x] 2.5 In `data_stores.tf` declare `aws_dynamodb_table` `abreiss-ensemble-items` (`hash_key = "itemId"`, attribute `itemId` type `S`, `billing_mode = PAY_PER_REQUEST`) matching the single-item model.
- [x] 2.6 Run `terraform -chdir=terraform/deploy fmt` and `validate` (init with `-backend=false` locally) until both exit `0`; capture the output as the proof artifact and note the full `plan` is operator-run in 6.0.

### [ ] 3.0 Terraform app-delivery resources: ECR, App Runner, Secrets Manager containers (Unit 2b — validated IaC)

Declare the ECR repository (`abreiss-ensemble-*`, image scanning on, immutable-tag/lifecycle
policy for git-SHA tags); the App Runner service (`abreiss-ensemble-*`) running the ECR image
on port 8080, health-checking `/api/health`, small documented min/max (e.g. 1/2), non-secret
runtime env vars (`ENSEMBLE_PHOTOS_BACKEND=s3`, `ENSEMBLE_PHOTOS_S3_BUCKET`,
`ENSEMBLE_DYNAMODB_ENDPOINT=` empty, `ENSEMBLE_DYNAMODB_TABLE_NAME=abreiss-ensemble-items`,
region), and the three secret env vars sourced from Secrets Manager **ARNs**; and the Secrets
Manager secret **containers** (`abreiss-ensemble-anthropic-key`, `-passcode`, `-session-secret`)
declared **without values** so plaintext never enters state.

#### 3.0 Proof Artifact(s)

- Terraform output: a `terraform plan` (operator-run, redacted) enumerating the ECR repo (scanOnPush + immutable tags), the App Runner service (port 8080, `/api/health` health check, min/max, env vars, secret refs by ARN), and the three secret containers — demonstrates the app-delivery footprint (FR: ECR, App Runner, secrets).
- File: the App Runner service HCL showing non-secret env vars as literals and the three `ENSEMBLE_*` secrets referenced by ARN (not literal values) — demonstrates runtime secret injection with no plaintext (FR: App Runner secret sourcing; Security: no secrets in config).
- File: the `aws_secretsmanager_secret` declarations with no `secret_string` (and `ignore_changes` guarding value drift) — demonstrates Terraform manages containers only (FR: secret containers; Resolved Decision Q4).
- Grep: `terraform -chdir=terraform/deploy plan` output + committed HCL scanned show no secret values and no hard-coded account id — demonstrates the "no secrets/account-id in IaC" requirement (Success Metric 6).

#### 3.0 Tasks

- [ ] 3.1 In `ecr.tf` declare `aws_ecr_repository` (`abreiss-ensemble-app`) with `image_scanning_configuration { scan_on_push = true }`, `image_tag_mutability = "IMMUTABLE"`, and an `aws_ecr_lifecycle_policy` expiring old/untagged images (git-SHA tag scheme).
- [ ] 3.2 In `secrets.tf` declare three `aws_secretsmanager_secret` containers (`abreiss-ensemble-anthropic-key`, `-passcode`, `-session-secret`) with **no** `secret_string` and `lifecycle { ignore_changes = [...] }` guarding any out-of-band value — no plaintext in state.
- [ ] 3.3 In `apprunner.tf` declare the App Runner service (`abreiss-ensemble-app`): ECR image source, port `8080`, `health_check_configuration { path = "/api/health" }`, `instance_configuration` with documented cpu/memory + `instance_role_arn` (from 4.0), an `aws_apprunner_auto_scaling_configuration_version` (min 1 / max 2), non-secret `runtime_environment_variables` (`ENSEMBLE_PHOTOS_BACKEND=s3`, `ENSEMBLE_PHOTOS_S3_BUCKET`, `ENSEMBLE_DYNAMODB_ENDPOINT=""`, `ENSEMBLE_DYNAMODB_TABLE_NAME=abreiss-ensemble-items`, region), and `runtime_environment_secrets` referencing the three secret ARNs; wire `authentication_configuration.access_role_arn` (from 4.0) if private-ECR pull requires it.
- [ ] 3.4 Run `fmt`/`validate` clean; grep the rendered config and committed HCL to confirm no secret values and no hard-coded account id; capture as the proof artifact.

### [ ] 4.0 Terraform IAM roles: instance role + CI OIDC role, boundary-capped + Access Analyzer clean (Unit 2c — validated IaC)

Declare the App Runner **instance role** (`abreiss-ensemble-*`, created **with** the
`abreiss-ensemble-boundary` permissions boundary) granting least-privilege runtime access:
S3 get/put/delete on the photos-bucket ARN, DynamoDB item CRUD on the table ARN,
`secretsmanager:GetSecretValue` on the three secret ARNs — all prefix-scoped; an ECR-pull
access role if App Runner requires one (likewise prefix-named + boundary-attached). Declare
the **GitHub OIDC CI role** (`abreiss-ensemble-*`, boundary-attached) federating the account's
**pre-existing** OIDC provider, scoped to this repo + branch/ref, permissions exactly ECR push
+ `apprunner:StartDeployment`/`DescribeService`/`UpdateService` + one scoped `iam:PassRole` to
App Runner — matching what #16 pre-authorized (no policy widening). Expose module outputs.

#### 4.0 Proof Artifact(s)

- File: the rendered instance-role and CI-role policy JSON showing prefix-scoped `abreiss-ensemble-*` ARNs and the `permissions_boundary` attachment on each `aws_iam_role` — demonstrates roles are capped to the box (FR: instance role, CI role; Security: boundary-capped).
- Policy lint: `aws accessanalyzer validate-policy` on the rendered instance-role and CI-role policy documents returns no `ERROR`/`SECURITY_WARNING` (or each finding is documented) — demonstrates least-privilege, well-formed policies (Success Metric 2; #16-deferred standing gate).
- File: the CI-role trust policy federating the pre-existing OIDC provider with a `token.actions.githubusercontent.com:sub` repo/ref condition — demonstrates repo-scoped, no-static-key auth (FR: OIDC CI role).
- Terraform output: `terraform plan` shows the two roles created with the boundary and the `outputs.tf` values (service URL, ECR URL, names, CI role ARN) resolve — demonstrates the module exposes a usable, boundary-capped surface (FR: outputs).

#### 4.0 Tasks

- [ ] 4.1 In `iam.tf` declare the App Runner **instance role** `aws_iam_role` (`abreiss-ensemble-instance`) with `permissions_boundary` = the computed `abreiss-ensemble-boundary` policy ARN (account id + prefix, like bootstrap) and a trust policy for `tasks.apprunner.amazonaws.com`.
- [ ] 4.2 Attach a least-privilege policy to the instance role: S3 `GetObject`/`PutObject`/`DeleteObject` on `arn:...:abreiss-ensemble-photos/*` (+ `ListBucket` on the bucket ARN if needed), DynamoDB `GetItem`/`PutItem`/`UpdateItem`/`DeleteItem`/`Query`/`Scan` on the table ARN, and `secretsmanager:GetSecretValue` on the three secret ARNs — all prefix-scoped.
- [ ] 4.3 If App Runner requires a private-ECR **access role**, declare `abreiss-ensemble-ecr-access` (boundary-attached, trust `build.apprunner.amazonaws.com`) with a scoped ECR-pull policy (or `AWSAppRunnerServicePolicyForECRAccess`) and wire it into the service `authentication_configuration` (3.3).
- [ ] 4.4 Declare the GitHub OIDC **CI role** `abreiss-ensemble-ci` (boundary-attached): trust policy federating the pre-existing OIDC provider (`data` lookup — #16 allows `GetOpenIDConnectProvider`) with a `:sub` condition scoped to this repo + branch/ref; permissions exactly ECR push (`GetAuthorizationToken` + repo push/pull on the ECR ARN), `apprunner:StartDeployment`/`DescribeService`/`UpdateService` on the service ARN, and one `iam:PassRole` scoped to `abreiss-ensemble-*` for App Runner — no widening beyond #16.
- [ ] 4.5 Render the instance-role and CI-role policy JSON to committed review copies under `terraform/deploy/policies/` (mirrors `terraform/bootstrap/policies/`); run `aws accessanalyzer validate-policy` on each and confirm no `ERROR`/`SECURITY_WARNING` (or document each). Capture the JSON + lint output as proof.
- [ ] 4.6 Add `outputs.tf` exposing the App Runner service URL, ECR repo URL, table + bucket names, and CI role ARN; ensure no secrets/account-id are output; `fmt`/`validate` clean.

### [ ] 5.0 CI pipeline: OIDC build → ECR → App Runner deploy, plus standing checks (Unit 3 — validated CI)

Add `.github/workflows/deploy.yml` triggered on push to `main`: `permissions: { id-token: write,
contents: read }`, assume the CI role via `aws-actions/configure-aws-credentials` (OIDC, no
stored keys), build the existing multi-stage `Dockerfile`, push to ECR under an **immutable
git-SHA tag**, trigger `aws apprunner start-deployment`, then verify via `describe-service`.
Add a PR/push checks workflow running backend tests, frontend tests, `terraform fmt -check`/`validate`,
and IAM Access Analyzer policy-lint (the #16-deferred standing gate). No `terraform apply` in
CI; no long-lived AWS key or app secret anywhere in the workflows.

#### 5.0 Proof Artifact(s)

- File: the committed `deploy.yml` showing OIDC auth (`id-token: write`, `role-to-assume`), build→ECR→`start-deployment` with git-SHA tagging, and `describe-service` verification — demonstrates the pipeline shape and least-privilege auth (FR: deploy workflow; Success Metric 3).
- File: the committed checks workflow running backend + frontend tests, `terraform fmt -check`/`validate`, and Access Analyzer policy validation on PR/push — demonstrates the standing quality/policy-lint gate (FR: standing checks).
- Grep/scan: pre-commit secret scan + a grep of `.github/workflows/*` show no `AKIA*`, no `aws_secret_access_key`, and no app secret values — demonstrates "OIDC only, no secrets in CI" (FR: no static creds; Success Metric 6).
- CLI/log: (operator-run on a real push, redacted) a CI run log showing successful OIDC role assumption, ECR push under a SHA tag, and a triggered App Runner deployment — demonstrates the pipeline works end-to-end (Success Metric 3).

#### 5.0 Tasks

- [ ] 5.1 Add `.github/workflows/deploy.yml` on `push: branches: [main]` with `permissions: { id-token: write, contents: read }`; first step `aws-actions/configure-aws-credentials` assuming the CI role via `role-to-assume` (OIDC, no static keys), region `us-east-1`.
- [ ] 5.2 Build + push: `aws ecr get-login-password | docker login`, `docker build` the existing multi-stage `Dockerfile`, tag with the immutable git SHA (`${{ github.sha }}`), and push to the ECR repo URL (never `latest`-only).
- [ ] 5.3 Trigger + verify rollout: `aws apprunner start-deployment --service-arn ...`, then poll `describe-service`/`list-operations` until the deployment succeeds, failing the job on a failed operation.
- [ ] 5.4 Add `.github/workflows/ci.yml` (PR + push) running backend tests (`./gradlew test -PskipFrontend`), frontend tests (`cd frontend && npm ci && npm run test -- --run`), `terraform -chdir=terraform/deploy fmt -check` + `validate`, and IAM Access Analyzer `validate-policy` on the rendered role policies (the #16-deferred standing gate).
- [ ] 5.5 Confirm no secrets in CI: no `terraform apply`, no static AWS keys, no app-secret values in either workflow; run the pre-commit secret scan + grep `.github/workflows/*`; capture as proof (the operator later attaches a redacted real CI run log per 6.3).

### [ ] 6.0 Live deploy + golden-path verification + deploy runbook (Unit 4 — operator-run)

The operator performs the documented one-time sequence (create state bucket, `terraform init`
S3 backend + `use_lockfile`, `terraform apply` as `abreiss-ensemble-terraform`, populate the
three secret values out-of-band), a push to `main` deploys via the Unit 3 pipeline, and the
public App Runner URL serves the PWA. Prove the golden path end-to-end in the cloud
(add garment → Haiku tags → vibe → grounded outfit rendering the user's own S3 photos → pushback
re-picks), with items in real DynamoDB and secrets resolved from Secrets Manager. Satisfy the
deferred #16 gate and add a deploy runbook.

#### 6.0 Proof Artifact(s)

- Screenshot: the golden path on the **public App Runner URL** (garment add → tags → vibe → grounded outfit card → pushback re-pick), captured from a browser/iPhone — demonstrates end-to-end cloud functionality (Success Metric 4).
- CLI: `curl https://<app-runner-url>/api/health` → `200 {"status":"ok"}` and `aws apprunner describe-service` showing `RUNNING` with secret ARNs referenced (values not shown) — demonstrates the public deploy and secret wiring (Success Metric 4).
- CLI: an S3 object listing for the photos bucket and a DynamoDB item read for a created item (redacted) — demonstrates photos land in S3 and items persist in real DynamoDB (Success Metric 4).
- File/CLI: a redacted `terraform apply` transcript confirming success under the `abreiss-ensemble-terraform` identity (and any scoped `abreiss-ensemble-*` policy addition, if a gap was found) — demonstrates the #16 scoped-identity gate is satisfied without widening (Success Metric 5).
- File: the rendered deploy runbook section(s) in README/`docs/DEVELOPMENT.md` (+ `docs/ARCHITECTURE.md` narrative) covering state-bucket bootstrap, `apply`, secret population, push-to-deploy, reading the public URL, and rollback — demonstrates the operator run is documented and repeatable.

#### 6.0 Tasks

- [ ] 6.1 Operator: create the `abreiss-ensemble-tfstate` bucket (2.3), `terraform init` with the S3 backend + `use_lockfile`, and `terraform apply` as the `abreiss-ensemble-terraform` identity; capture a redacted apply transcript.
- [ ] 6.2 Operator: populate the three Secrets Manager values out-of-band (console/CLI) — Claude key, passcode, session secret — with no plaintext committed.
- [ ] 6.3 Operator: push to `main`; confirm the pipeline builds/pushes the SHA-tagged image and triggers the App Runner deployment; capture a redacted CI run log; `curl https://<url>/api/health` → `200`.
- [ ] 6.4 Operator: run the golden path on the public URL from a browser/iPhone (add garment → Haiku tags → vibe → grounded outfit rendering own S3 photos → pushback re-pick); capture screenshots; verify an S3 object listing + a DynamoDB item read (redacted).
- [ ] 6.5 Operator: confirm the deferred #16 gate — `apply` succeeds under only the scoped identity; if a permission gap is hit, close it as a narrowly-scoped `abreiss-ensemble-*` addition to the #16 policy (never `*`; a second managed policy if the size limit is hit) and note it in `docs/AWS_ACCESS.md`.
- [ ] 6.6 Write the deploy runbook: README/`docs/DEVELOPMENT.md` steps (state-bucket bootstrap → apply → secret population → push-to-deploy → reading the URL → rollback by redeploying an earlier SHA tag) plus the architecture/security narrative in `docs/ARCHITECTURE.md`, cross-referenced in the `docs/AWS_ACCESS.md` style.
