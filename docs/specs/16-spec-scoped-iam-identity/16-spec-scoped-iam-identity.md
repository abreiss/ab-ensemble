# 16-spec-scoped-iam-identity.md

## Introduction/Overview

Issue #9 will provision Ensemble's cloud footprint (S3, DynamoDB, ECR, App Runner, Secrets Manager, IAM roles) with Terraform in a **shared AWS account** that other people also use. This feature creates the **scoped AWS identity** that Claude/Terraform runs *as*, so that infra work can only create, read, or modify resources named `abreiss-ensemble-*` and can never touch anyone else's resources in that account.

The deliverable is a one-time, reviewable **Terraform bootstrap module** (`terraform/bootstrap/`) that an account admin applies once to create: a **permissions boundary** managed policy, a **scoped permissions** managed policy, and a **dedicated IAM user** (`abreiss-ensemble-terraform`) whose access keys live only in Claude's local environment. The primary goal is *blast-radius containment*: prefix-based isolation enforced by IAM conditions, plus a permissions boundary that forces every role this identity creates to stay inside the same box — proven with the IAM Policy Simulator and live allow/deny CLI tests.

## Goals

- Deliver a scoped IAM permissions policy that allows Ensemble infra actions **only** on `abreiss-ensemble-*` resources, across every resource type #9 creates (S3, DynamoDB, ECR, App Runner, Secrets Manager, IAM).
- Enforce a **permissions boundary** so any IAM role this identity creates (App Runner instance role, GitHub OIDC/CI role) is capped to the same `abreiss-ensemble-*` box and cannot be created without the boundary attached.
- Restrict `iam:PassRole` to only the `abreiss-ensemble-*` roles this identity creates, passed only to App Runner services.
- Provision a **dedicated IAM user** for local Terraform runs whose access keys go only into Claude's local environment and are never committed.
- Make the whole thing **reviewable as code** — a committed `terraform/bootstrap/` module plus a `docs/AWS_ACCESS.md` explaining it — and **prove the isolation** with a Policy Simulator allow/deny matrix and live CLI tests.

## User Stories

- **As the developer driving Terraform in a shared AWS account**, I want an identity that can only manage `abreiss-ensemble-*` resources, so that a mistake in my infra code (or a bad prompt) cannot delete or read another team's buckets, tables, or secrets.
- **As an account admin**, I want the scoped identity, its policy, and its permissions boundary defined as reviewable Terraform, so that I can read exactly what this identity can do before I apply it once with my elevated credentials — no undocumented console clicks.
- **As the developer**, I want every IAM role my Terraform creates to automatically carry a permissions boundary, so that I cannot accidentally (or a compromised run cannot deliberately) create a role more powerful than the box I'm confined to.
- **As a security reviewer**, I want a demonstrable allow/deny matrix — prefixed resources allowed, everything else denied — so that "it's scoped" is proven, not asserted.
- **As the owner of issue #9**, I want this identity to have exactly the permissions #9's `terraform apply` needs (and no more), so that deploy works end-to-end without either over-privileging or blocking the pipeline.

## Demoable Units of Work

### Unit 1: Permissions boundary + scoped policy, as reviewable Terraform

**Purpose:** Define the two managed policies — the **scoped permissions policy** (what Claude/Terraform may do) and the **permissions boundary** (the ceiling for roles it creates) — as `terraform/bootstrap/` code with correct `abreiss-ensemble-*` scoping. This is infra-as-code: validated and policy-linted, not unit-tested (per `docs/TESTING.md`).

**Functional Requirements:**
- The system shall define a Terraform bootstrap module under `terraform/bootstrap/` that declares an AWS provider (version-pinned, `required_version` set) with a configurable `aws_region` variable defaulting to `us-east-1`, and derives the account id from `data.aws_caller_identity` rather than hard-coding it.
- The system shall define a **scoped permissions policy** (`abreiss-ensemble-terraform`) granting create/read/update/delete for each resource type #9 provisions, restricted by ARN/resource-name condition to the `abreiss-ensemble-*` namespace **wherever the service supports resource-level permissions**:
  - **S3** — bucket + object actions on `arn:aws:s3:::abreiss-ensemble-*` and `.../*`.
  - **DynamoDB** — table actions on `table/abreiss-ensemble-*` (in `aws_region`).
  - **ECR** — repository actions on `repository/abreiss-ensemble-*`.
  - **App Runner** — service read/update/delete on `service/abreiss-ensemble-*/*`; create actions scoped where possible (see the enumerated `*` exceptions below).
  - **Secrets Manager** — secret actions on `secret:abreiss-ensemble-*` (the prefix covers Secrets Manager's random ARN suffix).
- The system shall grant the small set of **AWS-unavoidable account-level actions with `Resource: "*"`**, each enumerated with a one-line justification in code comments and in `docs/AWS_ACCESS.md`. At minimum this set includes `s3:ListAllMyBuckets`, `ecr:GetAuthorizationToken`, App Runner create actions that AWS does not allow ARN-scoping, `sts:GetCallerIdentity`, and service `List*`/`Describe*` actions that AWS defines as account-level. No `*`-resource action in this set shall be able to read or mutate the *contents* of a non-`abreiss-ensemble-*` resource. As defense-in-depth, regional statements shall carry an `aws:RequestedRegion` condition and, where the service supports it, an `aws:ResourceAccount` = this-account condition.
- The system shall define a **permissions boundary** managed policy (`abreiss-ensemble-boundary`) that caps any role created by the scoped identity to `abreiss-ensemble-*` runtime permissions (the App Runner instance role's S3/DynamoDB/Secrets access; the CI role's ECR/App Runner/PassRole access) and shall **explicitly deny** any `iam:*PermissionsBoundary*` action and any modification of the boundary policy itself, so a created role can neither remove nor weaken the boundary.
- The scoped policy's `iam:CreateRole` shall be allowed **only** when the request includes `iam:PermissionsBoundary` equal to the boundary policy ARN **and** the new role name matches `abreiss-ensemble-*`; the policy shall additionally **explicitly deny** `iam:DeleteRolePermissionsBoundary` and `iam:PutRolePermissionsBoundary` when the boundary is not the correct ARN, and deny any write to the boundary policy and to the scoped identity's own user/policy (no self-escalation).
- The scoped policy shall restrict `iam:PassRole` to `role/abreiss-ensemble-*` **and** condition it with `iam:PassedToService` limited to App Runner service principals; all other IAM write actions (`AttachRolePolicy`, `PutRolePolicy`, `TagRole`, `DeleteRole`, `CreatePolicy`, etc.) shall be scoped to `abreiss-ensemble-*` role/policy ARNs.
- The scoped policy shall include **no** `iam:CreateOpenIDConnectProvider` and **no** OIDC-provider `Delete*`/`Update*`; read-only reference (`iam:GetOpenIDConnectProvider`, `iam:ListOpenIDConnectProviders`) is permitted so #9 can attach a CI role's trust policy to the pre-existing provider.

**Proof Artifacts:**
- Terraform output: `terraform -chdir=terraform/bootstrap validate` and `terraform fmt -check` pass — demonstrates the module is well-formed HCL.
- Policy lint: `aws accessanalyzer validate-policy` (IAM Access Analyzer) run on both rendered policy JSON documents returns no `ERROR`/`SECURITY_WARNING` findings (or documents each accepted finding) — demonstrates the policies are valid and free of obvious over-grants.
- File: the rendered `abreiss-ensemble-terraform` and `abreiss-ensemble-boundary` policy JSON (from `terraform plan`/`terraform show` or committed `.json` templates) with the enumerated `*`-exception list and its justifications — demonstrates the scoping and the documented exceptions.

### Unit 2: Dedicated Terraform-runner IAM user + one-time bootstrap apply + docs

**Purpose:** Create the `abreiss-ensemble-terraform` IAM user (scoped policy attached) via the one-time admin apply, deliver its access keys to Claude's local environment only, and document the whole setup in `docs/AWS_ACCESS.md`.

**Functional Requirements:**
- The bootstrap module shall create an IAM user `abreiss-ensemble-terraform` (path/name within the `abreiss-ensemble-*` namespace) with the `abreiss-ensemble-terraform` scoped policy attached, and shall produce a programmatic access key.
- The access key secret shall be surfaced only as a **sensitive Terraform output** (or, at the admin's option documented in `AWS_ACCESS.md`, generated in the console) and shall be transferred into Claude's local environment (`~/.aws/credentials` profile or a git-ignored `.env`) — **never committed**. The module's Terraform **state must not be committed** (it can contain the secret); `terraform/bootstrap/` state is local/git-ignored and the apply is performed once by an admin.
- `docs/AWS_ACCESS.md` shall document: what the bootstrap creates (user, scoped policy, boundary), how an admin applies it once with elevated credentials, how the access keys reach the local environment, how to rotate/revoke keys, and the enumerated `*`-exception list from Unit 1. `docs/DEVELOPMENT.md` (and the deployment section of `docs/ARCHITECTURE.md`) shall link to it.
- The repository's `.gitignore` and pre-commit **secret scan** shall be confirmed to cover Terraform state and AWS access keys, so an accidental `terraform.tfstate` or credential commit is blocked.

**Proof Artifacts:**
- Terraform output: `terraform -chdir=terraform/bootstrap apply` summary (credentials redacted) showing the IAM user, policy attachment, and boundary policy created — demonstrates the one-time bootstrap provisions the identity.
- CLI: `aws sts get-caller-identity` using the new identity's profile returns the `abreiss-ensemble-terraform` user ARN — demonstrates the identity is usable locally.
- Grep/scan: `git status` + pre-commit secret-scan run showing no `*.tfstate` and no access key is tracked/committed — demonstrates the "never committed" requirement.
- File: rendered `docs/AWS_ACCESS.md` — demonstrates the setup is reviewable and reproducible, not a one-off console click.

### Unit 3: Prove the isolation — Policy Simulator matrix + live allow/deny tests

**Purpose:** Demonstrate that the scoping and boundary actually hold: prefixed resources are allowed, everything else is denied, and roles cannot be created without the boundary.

**Functional Requirements:**
- The system shall provide an allow/deny matrix produced by the **IAM Policy Simulator** (`aws iam simulate-principal-policy` against the `abreiss-ensemble-terraform` user, or `simulate-custom-policy` against the rendered documents) covering, per resource type: an allowed action on an `abreiss-ensemble-*` ARN, and the same action **denied** on a non-prefixed ARN.
- The system shall demonstrate, with **live AWS CLI calls** made as the scoped identity, at least: (a) an action on a non-prefixed resource is denied — e.g. `aws s3 ls s3://<unrelated-bucket>` → `AccessDenied`; (b) the equivalent action on an `abreiss-ensemble-*` resource succeeds — e.g. create/list a throwaway `abreiss-ensemble-*` bucket; (c) `aws iam create-role` **without** the permissions-boundary condition is **denied**, and **with** the correct boundary + `abreiss-ensemble-*` name **succeeds**; (d) `aws iam create-open-id-connect-provider` is **denied**.
- Every live test that creates a throwaway resource shall clean it up (or use a clearly-named `abreiss-ensemble-test-*` resource that is deleted), so the proof run leaves no residue.

**Proof Artifacts:**
- Simulator output: saved `simulate-principal-policy` results (JSON or table) showing `allowed` on prefixed ARNs and `implicitDeny`/`explicitDeny` on non-prefixed ARNs for each service — demonstrates the scoping matrix.
- CLI transcript: the live allow/deny session — unrelated-bucket `AccessDenied`, prefixed-bucket success, `CreateRole`-without-boundary denied then with-boundary success, OIDC-provider-create denied — demonstrates enforcement against real AWS.
- CLI: the boundary-enforcement proof specifically (`create-role` denied without `--permissions-boundary`, allowed with it) called out separately — demonstrates acceptance criterion "boundary is enforced, not just applied by convention."

## Non-Goals (Out of Scope)

1. **Building #9's actual infrastructure** — this feature creates only the *identity and its guardrails*. The S3 bucket, DynamoDB prod table, ECR repo, App Runner service, Secrets, instance/CI roles, `S3PhotoStorage`, and the CI pipeline are all #9's work.
2. **Running #9's full `terraform apply` end-to-end** — #9 is still open. "#9's `terraform apply` runs successfully using only this identity" is recorded as a **deferred cross-issue acceptance gate**, satisfied during #9's own implementation (see Success Metrics #6).
3. **Creating or modifying the account's GitHub OIDC provider** — treated as pre-existing/shared account infra. The scoped identity has no create/delete on it; #9's CI role only attaches to it via trust policy.
4. **Least-privilege audit of other people's existing resources** in the shared account.
5. **Multi-account / AWS Organizations / SCPs** — single account, prefix-based isolation only.
6. **AWS IAM Identity Center (SSO) or assume-role indirection** — Q1 selected a dedicated IAM user with local access keys (option A). Assume-role (option B) is noted as a drop-in variant in `AWS_ACCESS.md` but is not built here.
7. **Automated key rotation / a secrets vault for the access keys** — manual rotation is documented; automation is out of scope.
8. **Remote Terraform state backend** for the bootstrap module — the one-time bootstrap uses local, git-ignored state; a shared remote backend (if any) is #9's concern.

## Design Considerations

No UI. This is an infrastructure/security feature. The "interface" is (a) the reviewable Terraform in `terraform/bootstrap/`, (b) `docs/AWS_ACCESS.md`, and (c) the proof artifacts (Policy Simulator matrix + CLI transcript). Documentation should be clear enough for an account admin who has never seen this repo to apply the bootstrap once and hand the developer working credentials.

## Repository Standards

- **Infra is not under strict TDD.** Per `docs/TESTING.md`, Terraform/IaC is validated with `terraform fmt`/`validate`/`plan` + a policy-simulator/CLI smoke check, **not** unit-tested for 90% coverage. There is no backend-domain (Java) code in this feature, so the strict-TDD coverage gates do not apply here.
- **New `terraform/` top-level directory** — this is the repo's first IaC. Use `terraform/bootstrap/` for this one-time module; keep it self-contained so #9 can add its own `terraform/` root module alongside without collision.
- **Documentation map** — add `docs/AWS_ACCESS.md` and link it from `docs/DEVELOPMENT.md` (prerequisites/credentials) and `docs/ARCHITECTURE.md` (deployment/security section), matching how the existing docs cross-reference each other.
- **Secrets never committed** — reuse the existing pre-commit secret scan; extend `.gitignore` to cover `*.tfstate*`, `.terraform/`, and any AWS credential files. This mirrors the `.env` handling established in spec 07.
- **Conventional commits**, roughly one per demoable unit (`feat(infra): ...`, `docs(aws-access): ...`).

## Technical Considerations

- **Self-referencing boundary pattern (the core mechanism).** The scoped policy allows `iam:CreateRole` only with `StringEquals { "iam:PermissionsBoundary": "<abreiss-ensemble-boundary ARN>" }` and `StringLike { resource role name: "abreiss-ensemble-*" }`, and explicitly denies the boundary-removal actions. This is the AWS-recommended delegated-role-creation pattern; it is what makes acceptance criterion "boundary is enforced, not just applied by convention" true.
- **Per-service resource-level support is uneven — this is the crux (Q3 → A).** Confirmed against the AWS Service Authorization Reference: `s3:ListAllMyBuckets`, `ecr:GetAuthorizationToken`, and most App Runner *create* actions do **not** accept resource ARNs and must use `Resource: "*"`. The policy therefore splits into (1) resource-scoped statements (`abreiss-ensemble-*`) for everything that supports it, and (2) one clearly-commented `*`-resource statement enumerating the unavoidable account-level actions, each justified as non-destructive (list-names / get-token / create-then-name-and-tag). Acceptance criterion #1 is interpreted in its substance-preserving form: *no resource-level create/read/write/delete on non-prefixed resources; the enumerated `*` actions cannot expose or mutate another resource's contents.*
- **`iam:PassRole` scoping.** App Runner needs an instance role and (for image pull) an access role; the CI role needs to pass the instance role. `PassRole` is limited to `role/abreiss-ensemble-*` with `iam:PassedToService` constrained to the App Runner principals (`apprunner.amazonaws.com`, `build.apprunner.amazonaws.com`, `tasks.apprunner.amazonaws.com` as applicable) so this identity cannot pass arbitrary roles to arbitrary services.
- **Access-key handling and state sensitivity.** If the key is created via `aws_iam_access_key`, the secret lands in Terraform state — so the bootstrap state must be local and git-ignored, and `AWS_ACCESS.md` must say so. The alternative (admin generates the key in the console, no key in state) is documented as the stricter option.
- **Region.** Regional ARNs (DynamoDB, App Runner, ECR, Secrets Manager, Logs) default to `us-east-1` via the `aws_region` variable (matches `application.yml`); IAM and S3-bucket ARNs are partition/global. Changing region is a one-variable edit.
- **Validation tooling.** Use the IAM Policy Simulator (`simulate-principal-policy`/`simulate-custom-policy`) for the offline matrix and IAM Access Analyzer `validate-policy` for linting; both are scriptable and require only read/simulate permissions.
- **What #9 will consume.** #9 references this identity's credentials (local `terraform apply`) and, in CI, the GitHub OIDC role that this identity creates under the boundary. The scoped policy must permit exactly the CRUD #9 needs on `abreiss-ensemble-*` — no more. Any gap found during #9 is a follow-up scoped addition, not a widening to `*`.

## Security Considerations

- **Blast-radius containment is the whole point.** Prefix-scoped ARNs + `aws:RequestedRegion`/`aws:ResourceAccount` conditions mean a wrong resource name or a bad prompt cannot reach another user's resources. The permissions boundary means even the *roles this identity creates* cannot exceed the box.
- **No privilege escalation.** The scoped policy cannot: create a role without the boundary, remove/alter the boundary, edit its own user/policy, pass arbitrary roles, or create/delete the OIDC provider. These are explicit denies, not just absent allows, so an added allow elsewhere cannot silently override them.
- **No secrets committed.** Access keys go only into Claude's local environment (`~/.aws/credentials` or git-ignored `.env`); Terraform state (which may hold the key) is git-ignored; the pre-commit secret scan is the backstop. `AWS_ACCESS.md` documents rotation/revocation.
- **Least privilege for the runtime roles too.** The boundary caps the App Runner instance role to `abreiss-ensemble-*` S3/DynamoDB/Secrets and the CI role to `abreiss-ensemble-*` ECR/App Runner + the one `PassRole` — so a compromise of the *deployed app's* role is also contained.
- **Proof artifacts must not leak.** The `apply` output, simulator results, and CLI transcript are captured with the access key and account-specific secrets redacted; throwaway test resources are named `abreiss-ensemble-test-*` and deleted.

## Success Metrics

1. **Reviewable as code:** `terraform fmt -check` + `validate` pass on `terraform/bootstrap/`; IAM Access Analyzer `validate-policy` reports no unaddressed errors/security-warnings on both policies.
2. **Isolation proven:** for every resource type (#9's five services + IAM), a create/read/write/delete on a non-`abreiss-ensemble-*` resource is denied and the prefixed equivalent is allowed — shown in the Policy Simulator matrix **and** a live CLI test (e.g. `aws s3 ls` on an unrelated bucket → `AccessDenied`).
3. **Boundary enforced:** `iam:CreateRole` without the permissions-boundary condition is denied; with the boundary + `abreiss-ensemble-*` name it succeeds (live CLI proof).
4. **OIDC provider untouched:** the scoped identity has no `iam:CreateOpenIDConnectProvider`/`Delete*`; `create-open-id-connect-provider` is denied in the live test, and #9 is confirmed to reference (not create) the provider.
5. **No secrets committed:** secret scan + `git status` show no `*.tfstate` and no access key tracked; credentials documented in `docs/AWS_ACCESS.md`.
6. **Deferred cross-issue gate:** "#9's `terraform apply` runs successfully using only this identity" is tracked and satisfied during #9's implementation; any permission gap found there is closed as a narrowly-scoped `abreiss-ensemble-*` addition.

## Open Questions

1. **Exact prod resource sub-names** (bucket, table, ECR repo, App Runner service, secret, role names) are #9's detail. This spec assumes they all conform to the `abreiss-ensemble-*` prefix (e.g. `abreiss-ensemble-photos`, `abreiss-ensemble-items`, `abreiss-ensemble-anthropic-key`), so the policy needs no change as long as #9 honors the prefix. Non-blocking.
2. **Access-key creation path** — default is a sensitive Terraform output (`aws_iam_access_key`) with git-ignored state; the admin may instead generate the key in the console (no key in state). Either satisfies "never committed." Non-blocking; documented in `AWS_ACCESS.md`.
3. **Region** — defaults to `us-east-1` (matches `application.yml`), parameterized via `aws_region`. Change is a one-variable edit. Non-blocking.
4. **Standing policy checks** — whether to wire IAM Access Analyzer / a policy linter into CI as an ongoing gate (vs. a one-time check here) can be decided with #9's CI work. Non-blocking.
