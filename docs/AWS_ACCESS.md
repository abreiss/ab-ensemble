# AWS Access — the scoped Terraform-runner identity

How Ensemble authenticates to AWS for infrastructure work, and how an account admin
sets it up **once**. Read [AGENTS.md](../AGENTS.md) and
[ARCHITECTURE.md](ARCHITECTURE.md) first for the deployment picture.

Issue #9 provisions Ensemble's cloud footprint (S3, DynamoDB, ECR, App Runner,
Secrets Manager, IAM roles) with Terraform in a **shared AWS account** that other
people also use. To contain blast radius, Terraform does not run as an admin — it
runs as a **dedicated, scoped IAM user** that can only touch resources named
`abreiss-ensemble-*`. This document explains that identity, how it is created, and
how its credentials reach the local environment.

> **The Terraform for this identity lives in [`terraform/bootstrap/`](../terraform/bootstrap/).**
> It is a one-time module applied once by an account admin — **not** part of #9's
> deploy pipeline and never run by CI.

## What the bootstrap creates

Applying [`terraform/bootstrap/`](../terraform/bootstrap/) creates three things:

| Resource | Name | Purpose |
| --- | --- | --- |
| `aws_iam_policy` (permissions boundary) | `abreiss-ensemble-boundary` | The **ceiling** for every IAM role the runner creates (App Runner instance role, CI role). A created role's effective permissions are the intersection of its own policy **and** this boundary. |
| `aws_iam_policy` (scoped permissions) | `abreiss-ensemble-terraform` | **What the runner may do**: CRUD on `abreiss-ensemble-*` resources only, IAM roles only when they carry the boundary, plus explicit anti-escalation denies. |
| `aws_iam_user` | `abreiss-ensemble-terraform` | The **local Terraform runner** identity, with the scoped policy attached and a programmatic access key. |

The scoping mechanism, statement by statement, is reviewable in
[`terraform/bootstrap/policies.tf`](../terraform/bootstrap/policies.tf) and in the
rendered, redacted JSON under
[`terraform/bootstrap/policies/`](../terraform/bootstrap/policies/).

### The self-referencing boundary (why roles stay in the box)

The scoped policy allows `iam:CreateRole` **only** when the request carries
`iam:PermissionsBoundary = <abreiss-ensemble-boundary ARN>` and the new role name
matches `abreiss-ensemble-*`, and it **explicitly denies** removing or weakening any
role's boundary. So even a bad prompt or a mistake cannot create a role more
powerful than the box the runner itself is confined to. This is the AWS-recommended
delegated-role-creation pattern — it is what makes "the boundary is enforced, not
applied by convention" true.

## The enumerated `Resource: "*"` exceptions

Almost everything in the scoped policy is restricted to `abreiss-ensemble-*` ARNs.
A small set of actions **cannot** take a resource ARN (AWS does not support
resource-level permissions for them), so they use `Resource: "*"`. Each is
**name/token/identity-only** and **cannot read or mutate the contents of any
resource**. Regional ones are pinned to `var.aws_region` via `aws:RequestedRegion`.

**Global (no region guard possible):**

| Action | Why it must be `*` / why it is safe |
| --- | --- |
| `s3:ListAllMyBuckets` | Lists bucket **names** only; no access to any bucket's contents. |
| `sts:GetCallerIdentity` | "Who am I" preflight; no resource, exposes no data. |

**Regional (guarded by `aws:RequestedRegion`):**

| Action | Why it must be `*` / why it is safe |
| --- | --- |
| `ecr:GetAuthorizationToken` | Docker-login token; account-level, AWS accepts no ARN. |
| `dynamodb:ListTables` | Lists table **names** only. |
| `secretsmanager:ListSecrets` | Lists secret metadata (name/ARN) only; no secret values. |
| `apprunner:ListServices` | Lists service summaries only. |
| `apprunner:ListAutoScalingConfigurations` | Lists config summaries only. |
| `apprunner:ListObservabilityConfigurations` | Lists config summaries only. |
| `apprunner:CreateService` | Create-then-name; AWS accepts no ARN at create time. |
| `apprunner:CreateAutoScalingConfiguration` | Create-then-name; AWS accepts no ARN at create time. |
| `apprunner:CreateObservabilityConfiguration` | Create-then-name; AWS accepts no ARN at create time. |

This list is also maintained inline in `policies.tf` and in
[`terraform/bootstrap/policies/README.md`](../terraform/bootstrap/policies/README.md).
If issue #9 needs an action not listed here, add a narrowly-scoped
`abreiss-ensemble-*` statement — **never** widen one of these to reach resource
contents.

## One-time apply (account admin, elevated credentials)

An admin with elevated (account-admin) credentials runs this **once**:

```bash
cd terraform/bootstrap
terraform init                 # downloads the pinned AWS provider
terraform plan                 # review the two policies + user before applying
terraform apply                # creates boundary policy, scoped policy, user, access key
```

`terraform plan` is the review gate: read exactly what the identity can do before
applying. Nothing here should require console clicks.

> **Region.** Regional ARNs default to `us-east-1` via `var.aws_region` (matches
> `application.yml`). Changing region is a one-variable edit:
> `terraform apply -var aws_region=us-west-2`.

## Access-key delivery (into the local environment only)

There are two supported ways to get the runner's key into the local environment.
**Both keep the secret out of git**; pick per how strict you want to be about state.

### Option A — sensitive Terraform output (default)

`aws_iam_access_key` surfaces the credential as two **sensitive** outputs. After the
apply, read them explicitly (they are never printed in plan/apply logs):

```bash
terraform -chdir=terraform/bootstrap output -raw terraform_runner_access_key_id
terraform -chdir=terraform/bootstrap output -raw terraform_runner_secret_access_key
```

Load them into a **local, named AWS profile** (recommended) — Terraform and the AWS
CLI then use `--profile abreiss-ensemble-terraform`:

```bash
aws configure --profile abreiss-ensemble-terraform
# AWS Access Key ID:     <paste the access_key_id output>
# AWS Secret Access Key: <paste the secret_access_key output>
# Default region name:   us-east-1
```

The profile lives in `~/.aws/credentials` (outside the repo). Alternatively, place
the values in a **git-ignored `.env`** as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`
(mirrors the `.env` handling for the Claude key in
[DEVELOPMENT.md](DEVELOPMENT.md)); `~/.aws/credentials` is preferred because it never
sits inside the working tree.

> **Trade-off:** with Option A the secret is written into `terraform.tfstate`. That
> file is git-ignored (see below), but it does contain the key. Use Option B if you
> want the secret to never touch state.

### Option B — admin generates the key in the console (no key in state)

The stricter path: comment out the `aws_iam_access_key` resource in
[`identity.tf`](../terraform/bootstrap/identity.tf) and its two outputs, apply to
create just the user + policies, then have the admin create the access key in the
IAM console (**IAM → Users → abreiss-ensemble-terraform → Security credentials →
Create access key**) and hand it over out-of-band. The secret never enters Terraform
state. Load it into the same local profile as in Option A.

## State is local and git-ignored — never committed

With Option A the access-key **secret** lands in `terraform.tfstate`. Therefore:

- The bootstrap uses **local** state — do **not** add a remote backend here. A shared
  remote backend (if any) is #9's concern.
- The repository `.gitignore` blocks `*.tfstate*`, `.terraform/`, `*.tfvars`, and AWS
  credential files.
- A **pre-commit secret scan** (`.pre-commit-config.yaml`) blocks a stray AWS access
  key (`AKIA…`) from being committed, mirroring the existing Claude-key guard.

If `terraform.tfstate` or a key ever shows up in `git status`, stop — it is
git-ignored by design and must never be staged.

## Rotating and revoking the key

The access key is a long-lived credential; rotate it periodically and revoke it
immediately if it may be exposed.

**Rotate (zero-downtime):**

1. Create a second key: `aws iam create-access-key --user-name abreiss-ensemble-terraform`
   (or `terraform apply` after adding a second `aws_iam_access_key`, or use the console).
2. Update the local profile / `.env` to the new key and confirm
   `aws sts get-caller-identity --profile abreiss-ensemble-terraform` still works.
3. Deactivate the old key: `aws iam update-access-key --user-name abreiss-ensemble-terraform --access-key-id <OLD_ID> --status Inactive`.
4. After confirming nothing broke, delete it:
   `aws iam delete-access-key --user-name abreiss-ensemble-terraform --access-key-id <OLD_ID>`.

**Revoke immediately (suspected exposure):** deactivate and delete the key as above,
then re-issue via Option A or B. Automated rotation / a secrets vault for this key is
out of scope (manual only) — see the spec's Non-Goals.

## Assume-role variant (Option, not built here)

This bootstrap uses a **dedicated IAM user with a long-lived access key** (the choice
locked in the spec). A common stricter alternative is to attach the scoped policy to
an **IAM role** and have the developer `sts:AssumeRole` into it (short-lived
credentials, no long-lived key). To switch later: replace `aws_iam_user` +
`aws_iam_access_key` with an `aws_iam_role` carrying a trust policy for the developer
principal, attach the same `abreiss-ensemble-terraform` scoped policy, and configure
a `role_arn`/`source_profile` in `~/.aws/config`. The scoped and boundary policies
are unchanged — only the identity wrapper differs. This is a documented drop-in, not
part of this issue.

## Standing policy checks (a #9 follow-up)

The policies are linted **once** here with IAM Access Analyzer
(`aws accessanalyzer validate-policy`) and their scoping is proven with the IAM
Policy Simulator + live CLI tests (see
[`docs/specs/16-spec-scoped-iam-identity/proof/`](specs/16-spec-scoped-iam-identity/proof/)).
This is a **one-time gate** — nothing yet prevents a future edit from widening a
statement to `Resource: "*"` or dropping a `Deny`. Wiring Access Analyzer / a policy
linter into CI as a standing guard is deferred to issue #9's CI work.

**Known gap surfaced while authoring `terraform/deploy/iam.tf` (#9 Task 4.0):**
`access-analyzer:ValidatePolicy` is a read-only, account-level action that
neither the scoped `abreiss-ensemble-terraform` identity nor the new
`abreiss-ensemble-ci` OIDC role were granted — the former by the enumerated
exception list above (never widened for this), the latter because #9's FR
requires the CI role's permissions to match #16's pre-authorization exactly
("no policy widening"). Rendering the two roles' policy JSON and running the
lint locally therefore needs a **broader/admin AWS session**, same as the
one-time bootstrap lint referenced above — see
`terraform/deploy/policies/README.md` for the exact commands. Issue #9's
standing CI policy-lint check (Task 5.4) will need to resolve this the same
way: either run under a session with `access-analyzer:ValidatePolicy` that is
**not** the `abreiss-ensemble-ci` role, or accept a narrowly-scoped addition of
just that one action (it is name/token-only, no resource access, matching the
existing `AccountLevelGlobalActions` exception pattern) — a decision for #9's
Unit 3 work, not resolved here.

## Related docs

- [`terraform/bootstrap/README.md`](../terraform/bootstrap/README.md) — module usage quick-start.
- [`terraform/bootstrap/policies/README.md`](../terraform/bootstrap/policies/README.md) — the rendered policy JSON + exception list.
- [ARCHITECTURE.md](ARCHITECTURE.md) — deployment & security overview.
- [DEVELOPMENT.md](DEVELOPMENT.md) — local setup & prerequisites.
