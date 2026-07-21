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
`terraform/deploy/policies/README.md` for the exact commands.

**Resolution for the standing CI gate (#9 Task 5.4):** the gap is structural,
not fixable from `terraform/deploy/` alone — the `abreiss-ensemble-boundary`
(created in `terraform/bootstrap/`, out of scope for `terraform/deploy/`) does
not allow `access-analyzer:ValidatePolicy` for *any* role it bounds, so no new
narrowly-scoped role created under that boundary would help, and widening the
`abreiss-ensemble-ci` role's own policy would both still be denied by the
boundary and violate Task 4.0's "matches #16 exactly, no widening"
requirement. `.github/workflows/ci.yml`'s `policy-lint` job therefore assumes
the same `abreiss-ensemble-ci` role as every other check, runs the lint
anyway, and marks the two `aws accessanalyzer validate-policy` steps
`continue-on-error: true` — the check is wired up and runs on every PR/push
(satisfying the standing-gate requirement), but does not perpetually block
merges on a known, documented, structural permission gap. Closing the gap for
real means a deliberate, narrowly-scoped `access-analyzer:ValidatePolicy`
addition to the boundary itself — a future, separate change to
`terraform/bootstrap/`, not part of #9.

## Post-bootstrap addition: `abreiss-ensemble-terraform-ext` (#9 Task 6.1)

The live `terraform apply` of `terraform/deploy/` (Task 6.1) surfaced several
read-only, resource-scoped permission gaps the AWS provider needs immediately
after creating a resource — none can read or mutate a resource's actual
contents, and none is `Resource: "*"`:

| Action | Resource scope | Why it's needed |
| --- | --- | --- |
| `s3:GetAccelerateConfiguration` | `abreiss-ensemble-*` bucket | Provider reads this computed attribute right after `aws_s3_bucket` create. IAM action name doesn't match the `GetBucketAccelerateConfiguration` API op, so `terraform_scoped`'s `s3:GetBucket*` wildcard doesn't cover it. |
| `s3:GetReplicationConfiguration` | `abreiss-ensemble-*` bucket | Same shape — API op is `GetBucketReplication`, IAM action is `GetReplicationConfiguration`, another `s3:GetBucket*` miss. Surfaced in a second round after the first fix, once the accelerate-config read succeeded and the provider moved to the next computed attribute. |
| `secretsmanager:GetResourcePolicy` | `abreiss-ensemble-*` secret | Same — read right after `aws_secretsmanager_secret` create. |
| `apprunner:DescribeAutoScalingConfiguration` (+ `Delete`/tag siblings) | `abreiss-ensemble-*` auto-scaling config | The **create waiter** for `aws_apprunner_auto_scaling_configuration_version` polls this until `ACTIVE`; without it, `terraform apply` cannot finish creating the App Runner service itself. |

**Operational note:** the first, partial `apply` (before this fix) still
*created* the S3 bucket, the auto-scaling config, and the 3 secrets
successfully in AWS — only the post-create read failed, which Terraform
records by marking the resource **tainted** (destroy-and-recreate on the next
apply). For the 3 secrets this would have been actively harmful: none sets
`recovery_window_in_days = 0`, so a destroy only schedules deletion, and
recreating a secret with the same name within that window fails outright.
The fix was `terraform untaint` on all 5 affected resources (not a replace)
once the underlying permission gap was closed, confirmed safe here because
each resource's actual AWS state was already correct — only Terraform's
bookkeeping was wrong.

Folding these into the existing `abreiss-ensemble-terraform` policy would have
pushed it to ~6,503 characters, over IAM's 6,144-character managed-policy
limit (that policy already runs close to the limit — see
`terraform/bootstrap/policies/README.md`'s size note). Per that note's own
guidance ("prefer splitting into a second `abreiss-ensemble-*` managed policy
over widening scope"), the fix is a **second** managed policy,
`abreiss-ensemble-terraform-ext` (~943 characters), attached to the same
`abreiss-ensemble-terraform` user alongside the first — not a wider statement,
not `Resource: "*"`. The identity's own `DenySelfModification` statement was
extended to cover the new policy's ARN too, so the identity still cannot edit
either of its own managed policies.

This is a `terraform/bootstrap/` change (the module an account admin applies
with elevated credentials — the scoped identity itself cannot touch its own
policy, by design). See `data.aws_iam_policy_document.terraform_scoped_ext` in
[`terraform/bootstrap/policies.tf`](../terraform/bootstrap/policies.tf) and the
rendered review copy at
[`terraform/bootstrap/policies/abreiss-ensemble-terraform-ext.json`](../terraform/bootstrap/policies/abreiss-ensemble-terraform-ext.json).

## PassRole condition removed: `iam:PassedToService` never matches App Runner (#9 Task 6.1)

The same live apply surfaced one more gap, of a different kind — not a missing
action but a condition that can never match. The scoped policy's PassRole
statement allowed `iam:PassRole` on `abreiss-ensemble-*` roles **only when**
`iam:PassedToService` named one of the three App Runner service principals
(`apprunner` / `build.apprunner` / `tasks.apprunner` `.amazonaws.com`) — the
exact shape AWS's own `AWSAppRunnerFullAccess` managed policy documents. Live,
App Runner's `CreateService` failed that check with an implicit deny
(`... no identity-based policy allows the iam:PassRole action`) under **every**
operator form of the condition:

- `StringEquals` (original) — failed;
- `StringEqualsIfExists` (absent-key hypothesis) — failed, retried well past
  IAM propagation;
- `ForAllValues:StringEquals` (multivalued-key hypothesis) — failed, retried
  ~8 minutes after the policy version went live.

Meanwhile `simulate-principal-policy` against the live principal **allowed**
each of those variants, so the real service-side evaluation context must
populate `iam:PassedToService` with something the simulator cannot reproduce
and AWS does not document. A temporary no-condition diagnostic policy
(`iam:PassRole` on `abreiss-ensemble-*` roles, nothing else, attached with
admin credentials and deleted after the diagnosis) flipped `CreateService`
from `AccessDeniedException` to success with no other change — isolating the
condition itself as the culprit.

The landed fix drops the `iam:PassedToService` condition from all three
PassRole statements (the scoped identity's `IamPassRoleScopedRolesOnly`, the
boundary's `CiPassRole` ceiling, and the CI role's own `CiPassRole` in
`terraform/deploy/iam.tf` — the CI role passes the same roles on every
`apprunner:UpdateService` deploy and would have failed identically at Task
6.3). What still constrains PassRole, deliberately:

1. **Resource scope** — only `abreiss-ensemble-*` roles can ever be passed;
2. **Trust policies** — each passable role's own trust policy names the only
   principals that can actually assume it (App Runner service principals for
   the instance/ECR-access roles, the GitHub OIDC provider for the CI role),
   so passing a role to a service that cannot assume it grants nothing.

No new actions, no wider resources; the policy shrinks. If AWS ever documents
the context App Runner supplies here, the condition can be reinstated in one
place per policy.

## Related docs

- [`terraform/bootstrap/README.md`](../terraform/bootstrap/README.md) — module usage quick-start.
- [`terraform/bootstrap/policies/README.md`](../terraform/bootstrap/policies/README.md) — the rendered policy JSON + exception list.
- [ARCHITECTURE.md](ARCHITECTURE.md) — deployment & security overview.
- [DEVELOPMENT.md](DEVELOPMENT.md) — local setup & prerequisites.
