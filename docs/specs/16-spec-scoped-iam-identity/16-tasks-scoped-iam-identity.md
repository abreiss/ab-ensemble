# 16-tasks-scoped-iam-identity.md

> Derived from `16-spec-scoped-iam-identity.md`. This is an **infrastructure/security
> feature** — per `AGENTS.md` and `docs/TESTING.md`, Terraform/IaC is proven with
> `fmt`/`validate` + IAM Access Analyzer + Policy Simulator + live CLI smoke tests,
> **not** unit-tested for coverage. There is no Java/backend-domain code here, so the
> strict-TDD RED/GREEN gates and the 90% line target do not apply. The "test artifacts"
> in each proof set are these validation/simulator/CLI checks.

## Spec Coverage Map

| Spec Unit / Requirement | Parent Task |
| --- | --- |
| Unit 1 — module scaffold (provider, `required_version`, `aws_region`, caller-identity) | 1.0 |
| Unit 2 — `.gitignore` + pre-commit secret scan cover `*.tfstate*` / `.terraform/` / AWS creds | 1.0 |
| Unit 1 — scoped permissions policy (S3/DynamoDB/ECR/App Runner/Secrets), enumerated `*` exceptions, `RequestedRegion`/`ResourceAccount` | 2.0 |
| Unit 1 — permissions-boundary policy + self-referencing `CreateRole` condition + explicit denies + `PassRole` scoping + no-OIDC-create | 2.0 |
| Unit 2 — dedicated IAM user, access-key sensitive output, one-time admin apply, `docs/AWS_ACCESS.md` + cross-links | 3.0 |
| Unit 3 — Policy Simulator matrix + live allow/deny CLI + boundary-enforcement + OIDC-deny + cleanup | 4.0 |

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `terraform/bootstrap/versions.tf` | Pins `required_version` and the `hashicorp/aws` provider version — the module's HCL-well-formedness foundation. |
| `terraform/bootstrap/variables.tf` | Declares `aws_region` (default `us-east-1`) and the `abreiss-ensemble` name-prefix input; the one-variable region knob. |
| `terraform/bootstrap/providers.tf` | Configures `provider "aws"` from `var.aws_region`; `data.aws_caller_identity`/`aws_partition`/`aws_region` for ARN construction (no hard-coded account id). |
| `terraform/bootstrap/policies.tf` | The two `aws_iam_policy_document` data sources + `aws_iam_policy` resources: scoped `abreiss-ensemble-terraform` and boundary `abreiss-ensemble-boundary`. |
| `terraform/bootstrap/identity.tf` | The `aws_iam_user`, scoped-policy attachment, and `aws_iam_access_key` for the Terraform runner. |
| `terraform/bootstrap/outputs.tf` | Sensitive outputs for the access key id/secret (console-generation alternative documented in `AWS_ACCESS.md`). |
| `terraform/bootstrap/policies/*.json` | Rendered policy JSON (scoped + boundary) committed for review + the enumerated `*`-exception list. |
| `terraform/bootstrap/README.md` | Module usage note: one-time admin apply, local git-ignored state. |
| `docs/AWS_ACCESS.md` | **New** — what the bootstrap creates, one-time apply, key delivery/rotation, the `*`-exception list, assume-role (option B) note. |
| `docs/DEVELOPMENT.md` | Add a prerequisites/credentials cross-link to `AWS_ACCESS.md`. |
| `docs/ARCHITECTURE.md` | Add a deployment/security cross-link to `AWS_ACCESS.md`. |
| `.gitignore` | Add `*.tfstate*`, `.terraform/`, and AWS credential-file ignores. |
| `.pre-commit-config.yaml` | Add an AWS access-key secret-scan `pygrep` hook mirroring `block-anthropic-keys`. |
| `docs/specs/16-spec-scoped-iam-identity/proof/` | Committed, redacted proof artifacts: apply summary, Access Analyzer output, Policy Simulator matrix, live CLI transcript, cleanup. |

### Notes

- **No unit-test files** — IaC is not unit-tested here (per `docs/TESTING.md`). Verification is `terraform fmt -check`/`validate`, `aws accessanalyzer validate-policy`, `aws iam simulate-principal-policy`, and live `aws` CLI calls.
- `terraform validate` requires `terraform -chdir=terraform/bootstrap init` (provider download) first; use `-backend=false` for a local, stateless validate.
- Prefer `data.aws_iam_policy_document` blocks (idiomatic, diffable) and capture rendered JSON via `terraform show -json`/`terraform plan` for the committed `policies/*.json` review copies.
- **Live-account assumption (from spec Q4 = option A):** Tasks 3.5 and 4.2–4.5 assume a real AWS account with one-time admin bootstrap access. If none is reachable, they fall back to spec Q4 **option C** — Policy Simulator + static validation only, with live CLI/apply proofs explicitly recorded as deferred. The `#9 terraform apply` end-to-end gate is deferred regardless (Non-Goal 2, Success Metric 6).
- **Secrets never committed:** access keys go only to `~/.aws/credentials`/git-ignored `.env`; bootstrap state stays local. All captured proof artifacts are redacted (no key, no account id).
- Conventional commits, roughly one per parent task: `feat(infra): ...`, `docs(aws-access): ...`, `chore(infra): ...`.

## Tasks

### [x] 1.0 Scaffold the `terraform/bootstrap/` module and lock down state/secret safety

Foundation task: create the repo's first `terraform/` directory with a version-pinned,
region-parameterized bootstrap module skeleton, **and** close the state/credential-leak
hole before any `apply` can generate a secret-bearing state file. Sequenced first because
the `*.tfstate` ignore + secret-scan backstop must exist before Task 3.0's apply runs.

#### 1.0 Proof Artifact(s)

- CLI: `terraform -chdir=terraform/bootstrap fmt -check` and `terraform -chdir=terraform/bootstrap validate` both exit `0` on the scaffolded module — demonstrates well-formed, version-pinned HCL with the `aws_region` variable (default `us-east-1`) and `data.aws_caller_identity` wired (Unit 1 module FR).
- CLI: `git check-ignore terraform/bootstrap/terraform.tfstate terraform/bootstrap/.terraform` echoes both paths and `git status --porcelain` shows no `*.tfstate*`/`.terraform/` tracked — demonstrates state cannot be committed (Unit 2 FR).
- CLI: `pre-commit run --all-files` blocks a staged fixture holding the placeholder `AKIAIOSFODNN7EXAMPLE` key, then passes once removed — demonstrates the secret-scan backstop covers AWS credentials (Unit 2 FR). Fixture uses AWS's published example key only; no real credential.
- Diff: `.gitignore` additions (`*.tfstate*`, `.terraform/`, AWS credential files) — demonstrates the ignore rules are committed.

#### 1.0 Tasks

- [x] 1.1 Create `terraform/bootstrap/versions.tf` with a `terraform {}` block: `required_version = ">= 1.6.0"` (or repo's chosen floor) and `required_providers { aws = { source = "hashicorp/aws", version = "~> 5.0" } }`.
- [x] 1.2 Create `terraform/bootstrap/variables.tf` with `variable "aws_region"` (default `"us-east-1"`, description) and `variable "resource_prefix"` (default `"abreiss-ensemble"`) so the prefix is defined once.
- [x] 1.3 Create `terraform/bootstrap/providers.tf`: `provider "aws" { region = var.aws_region }` plus `data "aws_caller_identity" "current" {}`, `data "aws_partition" "current" {}`, `data "aws_region" "current" {}`; add `locals` deriving `account_id`/`partition`/prefix for ARN building — no hard-coded account id.
- [x] 1.4 Add `terraform/bootstrap/README.md` documenting that this is a one-time admin apply with local, git-ignored state, and stub `policies.tf`/`identity.tf`/`outputs.tf` (empty or commented) so `init -backend=false` + `validate` pass on the skeleton.
- [x] 1.5 Update root `.gitignore`: add `*.tfstate`, `*.tfstate.*`, `.terraform/`, `crash.log`, `*.tfvars` (may hold secrets), and AWS credential files (e.g. `.aws-credentials`); leave `.terraform.lock.hcl` committable.
- [x] 1.6 Add an AWS-key secret-scan hook to `.pre-commit-config.yaml` mirroring `block-anthropic-keys`: a `pygrep` entry matching `AKIA[0-9A-Z]{16}` (and/or `aws_secret_access_key\s*=`), with the documented example key `AKIAIOSFODNN7EXAMPLE` allowlisted if it appears in docs/fixtures.
- [x] 1.7 Run `terraform -chdir=terraform/bootstrap init -backend=false`, `fmt -check`, `validate`; run `git check-ignore` on the tfstate paths; run `pre-commit run --all-files` against a temporary placeholder-key fixture. Capture the 1.0 proof artifacts (redacted) under `docs/specs/16-spec-scoped-iam-identity/proof/`.

### [x] 2.0 Author the scoped permissions policy and the self-referencing permissions boundary

The heart of the feature: the two managed policies as reviewable HCL — the
**scoped permissions policy** (`abreiss-ensemble-terraform`) with per-service ARN scoping
plus the enumerated account-level `*` exceptions, and the **permissions boundary**
(`abreiss-ensemble-boundary`) with the self-referencing `CreateRole` condition, explicit
anti-escalation denies, scoped `PassRole`, and no OIDC-provider create/delete.

#### 2.0 Proof Artifact(s)

- CLI: `terraform -chdir=terraform/bootstrap validate` + `fmt -check` pass with both `aws_iam_policy` documents declared — demonstrates the policies are well-formed HCL.
- CLI: `aws accessanalyzer validate-policy --policy-document file://<rendered>.json --policy-type IDENTITY_POLICY` on **both** rendered policy JSON documents returns no `ERROR`/`SECURITY_WARNING` findings, or each accepted finding is documented inline — demonstrates the policies are valid and free of obvious over-grants (Success Metric 1).
- File: the rendered `abreiss-ensemble-terraform` and `abreiss-ensemble-boundary` policy JSON committed under `terraform/bootstrap/policies/`, including the enumerated `*`-exception statement with a one-line justification per action, the `iam:PermissionsBoundary`-conditioned `CreateRole`, the `iam:PassedToService`-scoped `PassRole`, and the explicit anti-escalation denies — demonstrates the scoping, the documented exceptions, and the boundary mechanism (Unit 1 FRs; Security Considerations).

#### 2.0 Tasks

- [x] 2.1 In `policies.tf`, build the scoped-policy resource-scoped `Allow` statements per service, each restricted to the `abreiss-ensemble-*` namespace: S3 bucket+object on `arn:aws:s3:::abreiss-ensemble-*` and `.../*`; DynamoDB on `table/abreiss-ensemble-*`; ECR on `repository/abreiss-ensemble-*`; App Runner read/update/delete on `service/abreiss-ensemble-*/*`; Secrets Manager on `secret:abreiss-ensemble-*`. Add `aws:RequestedRegion == var.aws_region` on regional statements and `aws:ResourceAccount == local.account_id` where the service supports it.
- [x] 2.2 Add the single enumerated account-level `*`-resource `Allow` statement: `s3:ListAllMyBuckets`, `ecr:GetAuthorizationToken`, the App Runner create actions AWS cannot ARN-scope, `sts:GetCallerIdentity`, and the service `List*`/`Describe*` actions AWS defines as account-level — each with a one-line justification comment; guard regional ones with `aws:RequestedRegion`. _(Split into `AccountLevelGlobalActions` + `AccountLevelRegionalActions` because `aws:RequestedRegion` cannot guard truly-global calls; both are Resource:"*" enumerated statements.)_
- [x] 2.3 Add the scoped policy's IAM statements: `iam:CreateRole` allowed **only** with `StringEquals { iam:PermissionsBoundary = aws_iam_policy.boundary.arn }` **and** `StringLike { role name = abreiss-ensemble-* }`; scope `AttachRolePolicy`/`PutRolePolicy`/`TagRole`/`DeleteRole`/`CreatePolicy`/etc. to `abreiss-ensemble-*` role/policy ARNs; `iam:PassRole` limited to `role/abreiss-ensemble-*` with `iam:PassedToService` ∈ App Runner principals; allow read-only `iam:GetOpenIDConnectProvider`/`ListOpenIDConnectProviders` only. _(Role-name prefix enforced by the `role/abreiss-ensemble-*` resource ARN rather than a separate StringLike.)_
- [x] 2.4 Add the scoped policy's explicit `Deny` statements (anti-escalation): deny `iam:DeleteRolePermissionsBoundary`/`iam:PutRolePermissionsBoundary` when boundary ≠ the correct ARN; deny any write to the boundary policy ARN; deny any write to the runner user + its own policy; deny `iam:CreateOpenIDConnectProvider` and OIDC `Delete*`/`Update*`.
- [x] 2.5 Build the boundary policy document (`abreiss-ensemble-boundary`): allow the union of runtime perms a created role may need — App Runner instance role's S3/DynamoDB/Secrets on `abreiss-ensemble-*`; CI role's ECR/App Runner + the one `PassRole` — all capped to `abreiss-ensemble-*`; **explicitly deny** any `iam:*PermissionsBoundary*` action and any modification of the boundary policy itself.
- [x] 2.6 Wire both documents into `aws_iam_policy.terraform_scoped` and `aws_iam_policy.boundary`; reference `aws_iam_policy.boundary.arn` in the scoped policy's `CreateRole` condition (self-reference). _(Implemented as `local.boundary_policy_arn` (identical computed ARN) + `depends_on = [aws_iam_policy.boundary]` to keep the review JSON renderable without a live apply and avoid a data-source ⇄ resource cycle; ordering preserved.)_
- [x] 2.7 Render both policies to JSON (`terraform plan` + `terraform show -json`, or a committed template) into `terraform/bootstrap/policies/`; run `aws accessanalyzer validate-policy` on both; document any accepted finding. Capture the 2.0 proof artifacts. _(Both return zero findings; account id redacted to `123456789012` in the committed JSON.)_

### [ ] 3.0 Provision the Terraform-runner IAM user via one-time bootstrap apply and document it

Run the one-time admin `apply` that creates the `abreiss-ensemble-terraform` user with the
scoped policy attached and the boundary policy present, deliver its access key to Claude's
local environment only (never committed), and write `docs/AWS_ACCESS.md`, cross-linked from
`docs/DEVELOPMENT.md` and `docs/ARCHITECTURE.md`.

#### 3.0 Proof Artifact(s)

- CLI: `terraform -chdir=terraform/bootstrap apply` summary **with the access-key secret redacted**, showing the `abreiss-ensemble-terraform` user, the scoped-policy attachment, and the `abreiss-ensemble-boundary` policy created — demonstrates the one-time bootstrap provisions the identity (Unit 2 FR).
- CLI: `aws sts get-caller-identity --profile <ensemble-terraform-profile>` returns the `abreiss-ensemble-terraform` user ARN (account id redacted) — demonstrates the identity is usable locally (Unit 2 FR).
- CLI/scan: `git status --porcelain` + `pre-commit run` after the apply show no `*.tfstate` and no access key tracked or committed — demonstrates the "never committed" requirement (Success Metric 5).
- File: rendered `docs/AWS_ACCESS.md` plus the added cross-reference links in `docs/DEVELOPMENT.md` and `docs/ARCHITECTURE.md` — demonstrates the setup is reviewable and reproducible, matching the repo's docs-map convention (Unit 2 FR; Repository Standards).

#### 3.0 Tasks

- [ ] 3.1 In `identity.tf`, add `aws_iam_user "terraform_runner"` (name `abreiss-ensemble-terraform`, within the namespace) and an `aws_iam_user_policy_attachment` attaching the scoped policy.
- [ ] 3.2 Add `aws_iam_access_key` for the user and expose `access_key_id` + `secret` as `sensitive = true` outputs in `outputs.tf`; note the console-generated alternative (no key in state) for `AWS_ACCESS.md`.
- [ ] 3.3 Write `docs/AWS_ACCESS.md`: what the bootstrap creates (user, scoped policy, boundary); the one-time admin apply steps with elevated creds; how the key reaches the local env (`~/.aws/credentials` profile or git-ignored `.env`); rotation/revocation; the enumerated `*`-exception list from Unit 1; the assume-role (option B) drop-in note; the "state is local/git-ignored" warning.
- [ ] 3.4 Add cross-links: a prerequisites/credentials pointer to `AWS_ACCESS.md` in `docs/DEVELOPMENT.md`, and a deployment/security pointer in `docs/ARCHITECTURE.md`.
- [ ] 3.5 Perform the one-time admin `apply` with elevated creds; configure the local `<ensemble-terraform>` profile from the outputs; run `aws sts get-caller-identity` as the new identity. (If no live account: record the deferred status per the Live-account note and skip to Task 4.1's simulator-only path.) Capture the redacted apply summary + `get-caller-identity` output.
- [ ] 3.6 Verify no secrets committed: `git status --porcelain`, `pre-commit run --all-files`, confirm no `*.tfstate` / access key is tracked. Capture the 3.0 proof artifacts (redacted).

### [ ] 4.0 Prove the isolation: Policy Simulator matrix + live allow/deny CLI tests

Demonstrate the scoping and boundary actually hold — offline via the IAM Policy Simulator
and against real AWS via the scoped identity — including the boundary-enforcement pair and
the OIDC-create denial, cleaning up every throwaway `abreiss-ensemble-test-*` resource.

#### 4.0 Proof Artifact(s)

- Simulator output: saved `aws iam simulate-principal-policy` (or `simulate-custom-policy`) results (JSON or table) showing `allowed` on `abreiss-ensemble-*` ARNs and `explicitDeny`/`implicitDeny` on non-prefixed ARNs for **each** of S3, DynamoDB, ECR, App Runner, Secrets Manager, and IAM — demonstrates the scoping matrix (account id redacted) (Success Metric 2).
- CLI transcript: the live session as the scoped identity — `aws s3 ls s3://<unrelated-bucket>` → `AccessDenied`; create/list a throwaway `abreiss-ensemble-test-*` bucket succeeds; `aws iam create-role` **without** `--permissions-boundary` denied then **with** the correct boundary + `abreiss-ensemble-*` name succeeds; `aws iam create-open-id-connect-provider` denied — demonstrates enforcement against real AWS (secrets/account redacted) (Success Metrics 2, 3, 4).
- CLI: the boundary-enforcement pair called out separately (`create-role` denied without `--permissions-boundary`, allowed with it) — demonstrates "boundary is enforced, not just applied by convention" (Success Metric 3).
- CLI: cleanup commands + a follow-up `aws s3 ls` / list showing every `abreiss-ensemble-test-*` throwaway resource was deleted — demonstrates the proof run leaves no residue (Unit 3 FR).

#### 4.0 Tasks

- [ ] 4.1 Write a Policy Simulator script (`simulate-principal-policy` against the user, or `simulate-custom-policy` on the rendered docs) covering, per service (S3/DynamoDB/ECR/App Runner/Secrets/IAM), an allowed action on a prefixed ARN and the same action denied on a non-prefixed ARN; save results (JSON/table) to `docs/specs/16-spec-scoped-iam-identity/proof/`.
- [ ] 4.2 Run the live allow/deny CLI session as the scoped identity: `aws s3 ls s3://<unrelated-bucket>` → `AccessDenied`; create + list a throwaway `abreiss-ensemble-test-*` bucket → success. Capture the transcript (account/secrets redacted).
- [ ] 4.3 Run the boundary-enforcement pair: `aws iam create-role` **without** `--permissions-boundary` → denied; **with** the correct boundary ARN + `abreiss-ensemble-*` role name → success. Capture separately.
- [ ] 4.4 Run `aws iam create-open-id-connect-provider ...` → denied; capture the `AccessDenied`.
- [ ] 4.5 Clean up every throwaway resource (`aws s3 rb` the test bucket, delete the test role, etc.) and verify with a follow-up list showing no `abreiss-ensemble-test-*` residue. Capture the cleanup transcript.
- [ ] 4.6 Assemble the simulator matrix + CLI transcripts into `docs/specs/16-spec-scoped-iam-identity/proof/` and record the deferred `#9 terraform apply` cross-issue gate (Non-Goal 2, Success Metric 6).
