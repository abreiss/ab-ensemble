# 09-spec-deploy-pipeline.md

## Introduction/Overview

Ensemble runs entirely local today: DynamoDB Local, on-disk photos, secrets from a git-ignored `.env`. Issue #9 ships it to AWS as a single **App Runner** container fronted by real **S3** (photos) and **DynamoDB** (items), provisioned by **Terraform** and deployed by **GitHub Actions**, running as the scoped `abreiss-ensemble-terraform` identity created in #16.

The deliverable has two halves: (1) the **backend cloud-readiness code** that makes the app talk to real AWS — an `S3PhotoStorage` behind the existing `PhotoStorage` interface, a storage-backend selector, and a `DynamoDbConfig` that works against a real table (this is strict-TDD Java domain work); and (2) the **infra + CI glue** — a self-contained Terraform deploy module (S3, DynamoDB, ECR, App Runner, Secrets Manager, an App Runner instance role, and a GitHub OIDC CI role) plus a GitHub Actions pipeline that builds the image, pushes it to ECR, and triggers an App Runner deployment. The privileged `terraform apply` and the live golden-path verification are performed by the **operator** (you) against the real account; the code, IaC, and CI are delivered deploy-ready and validated (`terraform fmt`/`validate`/`plan`, Access Analyzer, green tests) so the live run has no surprises.

## Goals

- Make the app **cloud-capable without a rewrite**: add `S3PhotoStorage` behind the existing `PhotoStorage` interface and a config switch (disk↔S3), and make `DynamoDbConfig` reach a real DynamoDB table via the App Runner instance role — the disk→S3 and local→real swaps are configuration, not code forks.
- Provision Ensemble's entire cloud footprint as a **reviewable Terraform deploy module** — S3, DynamoDB, ECR, App Runner, Secrets Manager, instance role, CI role — every resource named `abreiss-ensemble-*`, every created role carrying the `abreiss-ensemble-boundary` permissions boundary, applyable by the scoped #16 identity with no policy widening.
- Deliver a **push-to-deploy CI pipeline**: GitHub Actions authenticates to AWS via **OIDC** (no static keys), builds the multi-stage image, pushes it to ECR under an immutable git-SHA tag, and triggers an App Runner deployment — using exactly the ECR + App Runner + one `PassRole` permissions #16 pre-authorized for the CI role.
- Keep **secrets out of Terraform state and git**: Terraform declares the Secrets Manager secret *containers*; their values are populated out-of-band; App Runner injects them as `ENSEMBLE_*` runtime env vars via the instance role, with **no change to how the app reads them**.
- Prove the **golden path works end-to-end in the cloud** on a public URL, and add a deploy runbook so the operator run is repeatable and reviewable.

## User Stories

- **As the operator deploying Ensemble**, I want one `terraform apply` (as the scoped identity) plus a `git push` to stand up and update the whole app in AWS, so that going from laptop to a public URL is a documented, low-risk sequence rather than a pile of console clicks.
- **As a developer**, I want the app to select S3 vs local disk and real vs local DynamoDB purely by configuration, so that the same jar runs unchanged on my laptop and in App Runner and I can unit-test the selection logic.
- **As a security-conscious owner of a shared AWS account**, I want the deploy to run under the `abreiss-ensemble-*` scoped identity with every created role capped by the permissions boundary and every secret value kept out of Terraform state, so that a mistake in infra code can't reach another team's resources or leak a credential.
- **As a reviewer**, I want the CI pipeline to use short-lived OIDC credentials with least-privilege (ECR + App Runner deploy only), and to lint the IAM policies on every change, so that the deploy path itself is auditable and can't silently over-grant.
- **As the person giving the demo**, I want the golden path (add clothes → tag → vibe → grounded outfit → push back) to work on a real public URL from an iPhone, so that the AI-native story is demonstrable outside my laptop.

## Demoable Units of Work

### Unit 1: Backend cloud-readiness — `S3PhotoStorage`, storage selector, real-AWS DynamoDB config

**Purpose:** Make the Java backend able to run against real AWS, selectable by configuration, so the same artifact serves local dev and cloud. This is **backend domain code under strict TDD** (per `docs/TESTING.md`): the storage-selection and DynamoDB-client branches get unit tests with the critical-branch coverage the repo requires. No live AWS in tests — the AWS SDK clients are mocked.

**Functional Requirements:**
- The system shall add the `software.amazon.awssdk:s3` dependency (via the existing AWS SDK BOM `2.30.0`) to `build.gradle`, alongside the current `dynamodb-enhanced`.
- The system shall provide an `S3PhotoStorage` class implementing the existing `PhotoStorage` interface (`save(key, bytes)`, `load(key)`, `delete(key)`), reading/writing objects in a configured bucket, and preserving the same on-save behavior as `LocalDiskPhotoStorage` (the ≤800px JPEG compression via the existing `ImageProcessor`, and path-safe keys). It shall depend only on the `PhotoStorage` interface contract — no S3 types shall leak into services or controllers.
- The system shall select exactly one `PhotoStorage` bean by configuration property `ensemble.photos.backend` (`disk` = default, `s3` = cloud): when `s3`, `S3PhotoStorage` loads and reads `ensemble.photos.s3.bucket`; otherwise `LocalDiskPhotoStorage` loads. Both impls shall be guarded (e.g. `@ConditionalOnProperty` / `@ConditionalOnMissingBean`) so that no configuration produces zero or two `PhotoStorage` beans.
- The system shall make `DynamoDbConfig` cloud-capable: when `ensemble.dynamodb.endpoint` is **blank or absent**, the client shall **not** set `endpointOverride` and shall use the **default credential provider chain** (so the App Runner instance role supplies credentials); when the endpoint is **present**, it shall keep the local-dev behavior (endpoint override + the dummy static credentials) so local DynamoDB Local is unaffected. The region shall continue to come from `ensemble.dynamodb.region`.
- The system shall expose the new configuration keys through the existing `ensemble.*` properties pattern and via Spring relaxed-binding env vars (`ENSEMBLE_PHOTOS_BACKEND`, `ENSEMBLE_PHOTOS_S3_BUCKET`, `ENSEMBLE_DYNAMODB_ENDPOINT`), so App Runner can drive them without a code change, and shall keep the local defaults (`disk`, `http://localhost:8000`) intact so `./gradlew bootRun` and existing tests behave exactly as before.
- The system shall not change how any service reads the Claude key or passcode; those remain the same `ENSEMBLE_*` env vars (their *source* in cloud is Unit 2's concern, not a code change here).

**Proof Artifacts:**
- Test: `S3PhotoStorageTest` passes with a mocked `S3Client` — asserting `save`/`load`/`delete` build the correct `PutObjectRequest`/`GetObjectRequest`/`DeleteObjectRequest` (bucket, key, content type), and that compression is applied — demonstrates the S3 impl satisfies the `PhotoStorage` contract with no live network call.
- Test: storage-selector tests pass — with `ensemble.photos.backend=s3` exactly the `S3PhotoStorage` bean is present, and with the default exactly `LocalDiskPhotoStorage` — demonstrates 100% branch coverage on the impl-selection logic.
- Test: `DynamoDbConfig` tests pass for both branches — blank endpoint → no override + default credentials provider; present endpoint → override + local behavior — demonstrates the local↔cloud switch is covered.
- CLI: `./gradlew test jacocoTestReport` output showing all tests green and ≥90% line / 100% branch on the new selection/config logic — demonstrates the strict-TDD gate is met for this unit.

### Unit 2: Terraform deploy module — the `abreiss-ensemble-*` cloud footprint

**Purpose:** Declare Ensemble's entire runtime infrastructure as a self-contained, reviewable Terraform module that the scoped #16 identity can apply. IaC is **validated, not unit-tested** (per `docs/TESTING.md`): `fmt`/`validate`/`plan` + Access Analyzer policy-lint.

**Functional Requirements:**
- The system shall add a **new self-contained Terraform root module** (e.g. `terraform/deploy/`) alongside the existing `terraform/bootstrap/` without collision, declaring the `hashicorp/aws ~> 5.0` provider, `required_version >= 1.11.0` (needed for S3-native state locking, see Technical Considerations), a configurable `aws_region` defaulting to `us-east-1`, a `resource_prefix` defaulting to `abreiss-ensemble`, and deriving the account id from `data.aws_caller_identity` (never hard-coded), mirroring the bootstrap module's conventions.
- The module shall use a **remote S3 backend** for its own state with **S3-native locking** (`use_lockfile = true`, no DynamoDB lock table), targeting an `abreiss-ensemble-tfstate` bucket that is versioned, encrypted (SSE), and blocks all public access. The one-time creation of that state bucket (the chicken-and-egg step, since a module cannot manage its own backend bucket) shall be documented and reproducible (a minimal bootstrap step or `-backend-config` init flow), and the bucket name shall stay within the `abreiss-ensemble-*` prefix so the scoped identity can create it.
- The module shall declare the runtime data stores: an **S3 photos bucket** (`abreiss-ensemble-photos`, versioning optional, public access blocked, SSE) and a **DynamoDB table** (`abreiss-ensemble-items`, `itemId` partition key, on-demand billing) matching the app's single-item model.
- The module shall declare an **ECR repository** (`abreiss-ensemble-*`) with image scanning enabled and an immutable-tag or lifecycle policy suitable for git-SHA tags.
- The module shall declare an **App Runner service** (`abreiss-ensemble-*`) that: runs the ECR image on port `8080`; health-checks `/api/health`; sets `min`/`max` instances appropriate for a demo (small, documented, e.g. min 1 / max 2); sets non-secret runtime env vars (`ENSEMBLE_PHOTOS_BACKEND=s3`, `ENSEMBLE_PHOTOS_S3_BUCKET`, `ENSEMBLE_DYNAMODB_ENDPOINT=` empty, `ENSEMBLE_DYNAMODB_TABLE_NAME=abreiss-ensemble-items`, region); and sources the secret env vars (`ENSEMBLE_ANTHROPIC_API_KEY`, `ENSEMBLE_PASSCODE`, `ENSEMBLE_SESSION_SECRET`) from Secrets Manager **ARNs** (runtime secrets), not literal values.
- The module shall declare the **Secrets Manager secret containers** (`abreiss-ensemble-anthropic-key`, `abreiss-ensemble-passcode`, `abreiss-ensemble-session-secret`) **without managing their values** (omit `secret_string`, or `lifecycle { ignore_changes = [secret_string] }`), so plaintext never enters Terraform state.
- The module shall declare an **App Runner instance role** (`abreiss-ensemble-*`, created **with the `abreiss-ensemble-boundary` permissions boundary attached**) granting least-privilege runtime access: S3 get/put/delete on the photos bucket ARN, DynamoDB item CRUD on the table ARN, and `secretsmanager:GetSecretValue` on the three secret ARNs — all prefix-scoped. If App Runner requires an **access role** for private-ECR image pull, it shall likewise be prefix-named and boundary-attached.
- The module shall declare a **GitHub OIDC CI role** (`abreiss-ensemble-*`, boundary attached) whose trust policy federates to the account's **pre-existing** GitHub OIDC provider, scoped to this repository (and branch/ref condition), and whose permissions are exactly ECR push + `apprunner:StartDeployment`/`DescribeService`/`UpdateService` + the single scoped `iam:PassRole` to App Runner — matching what the #16 boundary already pre-authorizes (no policy widening).
- The module shall expose useful **outputs** (App Runner service URL, ECR repository URL, table/bucket names, CI role ARN) and shall be free of hard-coded account ids or secrets.

**Proof Artifacts:**
- Terraform output: `terraform -chdir=terraform/deploy fmt -check` and `validate` pass — demonstrates well-formed HCL.
- Terraform output: a `terraform plan` (run by the operator against the real account, secrets/account-id redacted) enumerating the S3 bucket, DynamoDB table, ECR repo, App Runner service, secret containers, instance role, and CI role — demonstrates the module provisions the full footprint.
- Policy lint: `aws accessanalyzer validate-policy` on the rendered instance-role and CI-role policy documents returns no `ERROR`/`SECURITY_WARNING` (or each is documented) — demonstrates the runtime roles are least-privilege and well-formed.
- File: the rendered instance-role and CI-role policy JSON showing prefix-scoped ARNs and the boundary attachment — demonstrates roles are capped to `abreiss-ensemble-*`.

### Unit 3: CI pipeline — OIDC build → ECR → App Runner deploy, plus standing checks

**Purpose:** Turn a push to `main` into a deployment using short-lived OIDC credentials and the least-privilege CI role, and add the standing quality/policy checks #16 deferred to #9. IaC/CI glue — validated, not unit-tested.

**Functional Requirements:**
- The system shall add a GitHub Actions workflow (e.g. `.github/workflows/deploy.yml`) triggered on push to `main` that declares `permissions: { id-token: write, contents: read }`, assumes the `abreiss-ensemble-*` CI role via `aws-actions/configure-aws-credentials` (OIDC, no stored static keys), builds the existing multi-stage `Dockerfile`, pushes the image to ECR under an **immutable git-SHA tag**, and triggers the rollout with `aws apprunner start-deployment`, then verifies success via `describe-service` / deployment status.
- The system shall **not** run `terraform apply` in CI and shall **not** place any long-lived AWS credential or app secret in the workflow or repo — all AWS auth is OIDC, all app secrets come from Secrets Manager at runtime.
- The system shall add a CI check (on pull requests and/or push) that runs the backend tests (`./gradlew test`), the frontend tests, `terraform -chdir=terraform/deploy fmt -check`/`validate`, and **IAM Access Analyzer policy validation** on the rendered role policies — establishing the standing policy-lint gate deferred from #16.
- The workflow shall be structured so image tags are traceable to commits and a previous image can be redeployed (rollback) by pointing App Runner at an earlier tag.

**Proof Artifacts:**
- File: the committed workflow YAML(s) showing OIDC auth (`id-token: write`, `role-to-assume`), the build→ECR→`start-deployment` steps with git-SHA tagging, and the separate test/validate/policy-lint job — demonstrates the pipeline shape and least-privilege auth.
- CLI/log: a CI run log (operator-triggered on a real push, redacted) showing successful OIDC role assumption, ECR push under a SHA tag, and a triggered App Runner deployment — demonstrates the pipeline works end-to-end.
- Grep/scan: pre-commit secret scan + a scan of the workflow files showing no static AWS keys or app secrets are present — demonstrates the "OIDC only, no secrets in CI" requirement.

### Unit 4: Live deploy + golden-path verification + runbook (operator-run)

**Purpose:** Prove the whole thing works in the cloud and leave a repeatable runbook. Per Q2, the privileged live run is performed by the **operator** and its outputs are captured as proof; the spec's testable core (Units 1–3) is already complete and validated before this unit.

**Functional Requirements:**
- The operator shall perform the documented one-time sequence: create the state bucket, `terraform init` (S3 backend + `use_lockfile`), `terraform apply` as the `abreiss-ensemble-terraform` identity, and populate the three Secrets Manager secret values out-of-band (console/CLI) — no plaintext committed.
- A push to `main` shall build and deploy the image via the Unit 3 pipeline, and the resulting **public App Runner URL** shall serve the PWA and return `200` from `/api/health`.
- The **golden path shall work end-to-end in the cloud**: add a garment (photo upload) → Haiku vision tagging returns editable tags → enter a vibe → the stylist returns a grounded outfit card rendering the user's **own** stored photos → pushback re-picks without repeating the last look. Photos shall be stored in the S3 bucket and items persisted in the real DynamoDB table; the Claude key and passcode shall be resolved from Secrets Manager (not from any committed source).
- The **deferred #16 cross-issue gate** shall be satisfied: `terraform apply` succeeds using **only** the scoped identity; any permission gap found shall be closed as a narrowly-scoped `abreiss-ensemble-*` addition to the #16 policy (never a widening to `*`; prefer a second managed policy if the policy-size limit is hit) — not by using broader credentials.
- A **deploy runbook** shall be added to the docs (README and/or `docs/DEVELOPMENT.md`, with the architecture/security narrative in `docs/ARCHITECTURE.md`) covering: state-bucket bootstrap, `terraform apply`, secret population, the push-to-deploy flow, how to read the public URL, and rollback.

**Proof Artifacts:**
- Screenshot: the golden path on the **public App Runner URL** (garment add → tags → vibe → grounded outfit card → pushback re-pick), captured from a browser/iPhone — demonstrates end-to-end cloud functionality.
- CLI: `curl https://<app-runner-url>/api/health` → `200 {"status":"ok"}` and `aws apprunner describe-service` showing the service `RUNNING` with secret ARNs referenced (values not shown) — demonstrates the public deploy and secret wiring.
- CLI: an S3 object listing for the photos bucket and a DynamoDB item read for a created item (redacted) — demonstrates photos land in S3 and items persist in real DynamoDB.
- File: the rendered deploy runbook section(s) — demonstrates the operator run is documented and repeatable.

## Non-Goals (Out of Scope)

1. **VPC / RDS / private networking** — App Runner reaches S3, DynamoDB, and Secrets Manager over the public AWS APIs via the instance role; no VPC connector, no relational DB (matches the issue and `docs/ARCHITECTURE.md`).
2. **WAF, custom domain, or TLS beyond App Runner's default** — the default App Runner HTTPS URL is sufficient for the demo.
3. **Multi-user / accounts / per-user data scoping** — still the single shared passcode gate (issues #14/#15 track that separately).
4. **CI running `terraform apply` (full GitOps)** — Q3 selected the operator-run `apply` split; CI only builds/pushes/deploys the image. Widening the CI role to run Terraform is explicitly excluded.
5. **Managing secret *values* in Terraform** — Q4 selected create-container-then-populate; Terraform never holds plaintext secrets.
6. **Blue/green or canary deployments, autoscaling tuning beyond a documented min/max**, and multi-region — single-region (`us-east-1`), simple rolling App Runner deploy.
7. **Changing how the app authenticates or reads config** beyond the disk↔S3 selector and the DynamoDB endpoint/credentials branch — no new auth, no framework changes.
8. **Re-architecting the container build** — the existing multi-stage `Dockerfile` (frontend build → Spring static assets → jar) is reused as-is.

## Design Considerations

No new application UI — the existing React/Vite PWA is what gets deployed. The "interfaces" this feature produces are: (a) the reviewable `terraform/deploy/` module, (b) the GitHub Actions workflow(s), (c) the new `ensemble.*` config keys, and (d) the deploy runbook + proof artifacts. The runbook must be clear enough for the operator to reproduce the deploy without re-deriving the AWS wiring, in the same cross-referenced style as `docs/AWS_ACCESS.md` and `docs/DEVELOPMENT.md`.

## Repository Standards

- **Coverage split (`docs/TESTING.md`).** Unit 1 is backend domain code → **strict TDD**: ≥90% line and **100% branch** on the storage-selection and DynamoDB endpoint/credentials logic; AWS SDK clients are mocked (no live network). Units 2–4 are Terraform/CI/ops glue → **validated, not coverage-gated** (`fmt`/`validate`/`plan` + Access Analyzer + a live smoke check), per the testing guide.
- **Interface discipline.** `S3PhotoStorage` depends only on the `PhotoStorage` interface contract; no S3/DynamoDB types leak into controllers or services (DTOs at the boundary), matching `AGENTS.md` data standards.
- **New Terraform module alongside bootstrap.** `terraform/deploy/` is self-contained and does not modify `terraform/bootstrap/`; it reuses the same prefix/region/provider conventions and the `abreiss-ensemble-boundary` on every role it creates.
- **Secrets never committed.** Reuse the existing `.gitignore` (`*.tfstate*`, `.terraform/`, AWS creds, `.env`) and the pre-commit secret scan; the remote backend keeps state out of git entirely.
- **Conventional commits, ~one per demoable unit** (e.g. `feat(storage): S3PhotoStorage + backend selector`, `feat(infra): app runner + ecr + data stores`, `ci: OIDC build/push/deploy pipeline`, `docs: deploy runbook`).

## Technical Considerations

- **DynamoDB client cloud switch (the key code change).** Today `DynamoDbConfig` always calls `endpointOverride(...)` with dummy static creds, so it cannot reach real AWS. The change makes `endpointOverride` conditional on a non-blank `ensemble.dynamodb.endpoint` and falls back to the **default credential provider chain** (App Runner instance role) otherwise. `application.yml` should express the endpoint as `${ENSEMBLE_DYNAMODB_ENDPOINT:http://localhost:8000}` so cloud sets it to empty and local keeps the default. The DynamoDB Enhanced client and table name plumbing are otherwise unchanged.
- **Storage selection.** Prefer `@ConditionalOnProperty(name="ensemble.photos.backend", havingValue="s3")` for `S3PhotoStorage` and make `LocalDiskPhotoStorage` the default (`havingValue="disk"` or `matchIfMissing=true` / `@ConditionalOnMissingBean`), guaranteeing exactly one bean. This is the branch that must hit 100% coverage.
- **Terraform S3-native state locking (refinement of Q3).** Q3 mentioned an S3 bucket + a DynamoDB lock table; current guidance (Terraform **1.11**, GA) is **S3-native locking** via `use_lockfile = true`, and `dynamodb_table` is **deprecated**. The deploy module therefore uses `use_lockfile` and **no** lock table, and sets `required_version >= 1.11.0` (the operator's Terraform CLI must be ≥1.11). This reduces the resource count and stays on the supported path.
- **App Runner secret injection.** App Runner reads runtime secrets from Secrets Manager by **ARN** and injects them as named env vars; the sensitive value is never shown in service config or logs. The **instance role** must grant `secretsmanager:GetSecretValue` on the three secret ARNs (plus KMS decrypt if a CMK is used). Env var names map to the app's existing `ENSEMBLE_*` keys, so no code reads change.
- **GitHub→AWS OIDC.** Use `aws-actions/configure-aws-credentials` with `role-to-assume` = the CI role ARN and `permissions: id-token: write`; the CI role's trust policy federates the **pre-existing** account OIDC provider (the scoped identity can only *reference* it — #16 denies provider mutation) and is conditioned to this repo/ref. Temporary creds only.
- **Image tagging & deploy trigger.** Push images under immutable **git-SHA** tags (not `latest`) for traceability and rollback; trigger rollout explicitly with `apprunner:StartDeployment` (matches the exact CI-role permission #16 pre-authorized), rather than relying on auto-deploy-on-push.
- **Consuming the #16 identity.** `terraform apply` runs locally as the `abreiss-ensemble-terraform` user (long-lived key in `~/.aws/credentials`); CI runs as the separate OIDC CI role this module creates under the boundary. Every created role is made **with** `permissions_boundary = <abreiss-ensemble-boundary ARN>`, or the scoped policy's `iam:CreateRole` condition will deny it.
- **AWS SDK v2.** Add `software.amazon.awssdk:s3` under the existing BOM (`2.30.0`); use the default region provider / instance-role credentials in cloud. S3 unit tests mock `S3Client`; a thin real-S3 integration check (LocalStack or a live smoke) is a deploy-time check per `docs/TESTING.md`, not a coverage-gated unit test.
- **Region.** `us-east-1` throughout (matches `application.yml` and the bootstrap module); a one-variable change moves it.

## Security Considerations

- **No secrets in state or git.** Terraform manages secret *containers* only; values are populated out-of-band and injected at runtime by ARN. The remote state bucket is private, versioned, and SSE-encrypted, so even state contents are protected.
- **Least-privilege runtime roles, boundary-capped.** The instance role is limited to the photos bucket, the items table, and the three secret ARNs (all `abreiss-ensemble-*`); the CI role is limited to ECR push + App Runner deploy + one scoped `PassRole`. Both are created with the `abreiss-ensemble-boundary`, so a compromise of the running app or the CI principal is contained to the `abreiss-ensemble-*` box.
- **No static CI credentials.** GitHub authenticates via OIDC with short-lived STS credentials scoped to this repo; there are no long-lived AWS keys in GitHub secrets.
- **Standing policy-lint.** Access Analyzer policy validation runs in CI on the rendered role policies (the #16-deferred standing gate), catching over-grants on every change, not just once.
- **Existing app-layer guards preserved.** The passcode gate and the ~100/day call cap ship as-is; the passcode/session secret now come from Secrets Manager instead of `.env`.
- **Proof artifacts must not leak.** `apply`/`plan` output, CI logs, and CLI transcripts are captured with the account id, access keys, secret values, and any Claude key redacted.

## Success Metrics

1. **Cloud-ready code, proven by tests:** Unit 1 ships with all backend + frontend tests green and ≥90% line / 100% branch on the storage-selection and DynamoDB endpoint/credentials logic (`jacocoTestReport`), with AWS clients mocked.
2. **Full footprint as reviewable IaC:** `terraform -chdir=terraform/deploy fmt -check`/`validate` pass; a `plan` provisions S3 + DynamoDB + ECR + App Runner + Secrets + instance role + CI role; Access Analyzer reports no unaddressed findings on the role policies.
3. **Push-to-deploy works:** a push to `main` authenticates via OIDC, pushes a git-SHA-tagged image to ECR, and triggers an App Runner deployment — shown in a CI run log — with no static keys or secrets in the workflow.
4. **Public golden path in the cloud:** the App Runner URL serves the PWA, `/api/health` returns `200`, and the add→tag→vibe→grounded-outfit→pushback flow works end-to-end, with photos in S3 and items in real DynamoDB, and secrets resolved from Secrets Manager.
5. **Scoped-identity gate satisfied:** `terraform apply` succeeds using only the `abreiss-ensemble-terraform` identity; any gap was closed as a narrowly-scoped `abreiss-ensemble-*` addition, never a widening to `*`.
6. **No secrets committed:** secret scan + `git status` show no `*.tfstate`, no AWS keys, and no app secret values tracked; secret values live only in Secrets Manager.

## Resolved Decisions (locked from questions round 1 + drafting)

1. **One spec, four demoable units** (Q1 → A): backend cloud-readiness, Terraform deploy module, CI pipeline, live deploy + verification — kept together because the parts are interdependent.
2. **Deploy-ready + validated is the spec's testable core; the operator performs the live deploy** (Q2 → A): Units 1–3 are completed and validated in-workflow (tests, `fmt`/`validate`/`plan`, Access Analyzer); Unit 4's `terraform apply`, secret population, and public-URL golden-path capture are operator-run and attached as proof.
3. **CI ships images; Terraform is operator-run; remote S3 backend** (Q3 → A), **refined to S3-native locking** (`use_lockfile`, no DynamoDB lock table) per current Terraform 1.11 guidance. CI = OIDC → build → ECR (git-SHA) → `apprunner start-deployment`; plus `fmt`/`validate` + Access Analyzer checks.
4. **Secrets: Terraform creates empty containers; values populated out-of-band; App Runner injects via the instance role** (Q4 → A) — no plaintext in state/git, no app code change to how `ENSEMBLE_*` are read.

## Open Questions

1. **Live-run prerequisites** — Q2's confirmations (working `abreiss-ensemble-terraform` credentials, `us-east-1` target, acceptable live Claude/App Runner spend) were not explicitly answered in the questions file. The spec assumes the operator holds the #16 credentials and accepts demo-scale spend at Unit 4 time; `us-east-1` is taken as confirmed (matches `application.yml`). Non-blocking for Units 1–3.
2. **App Runner instance sizing** — exact min/max instance count and CPU/memory are demo-tunable (assumed min 1 / max 2, smallest viable size); adjustable in one place in the module. Non-blocking.
3. **Private-ECR access role** — whether App Runner needs a separate image-pull access role (in addition to the instance role) depends on the ECR/service configuration; if required it is prefix-named and boundary-attached. Non-blocking implementation detail.
4. **State-bucket bootstrap mechanism** — the one-time state-bucket creation may be a tiny separate bootstrap or a documented CLI step; either is acceptable as long as it stays `abreiss-ensemble-*` and is reproducible. Non-blocking.
5. **ECR repository name suffix** — the exact repo name (e.g. `abreiss-ensemble-app`) is a naming detail; any `abreiss-ensemble-*` value keeps the #16 policy unchanged. Non-blocking.
