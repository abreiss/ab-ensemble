# Rendered policy JSON — review copies

These files are the **rendered** output of the `aws_iam_policy_document` data
sources in [`../policies.tf`](../policies.tf), captured for human review and for
`aws accessanalyzer validate-policy`. `policies.tf` is the source of truth; these
JSON copies are regenerated from it (see "How these were rendered" below).

| File | Managed policy | Role |
| --- | --- | --- |
| `abreiss-ensemble-terraform.json` | `abreiss-ensemble-terraform` | Scoped permissions — what Claude/Terraform may do |
| `abreiss-ensemble-terraform-ext.json` | `abreiss-ensemble-terraform-ext` | Second scoped-permissions policy — read-only gap-fix grants that would push the first policy over IAM's size limit (see "Size note") |
| `abreiss-ensemble-boundary.json` | `abreiss-ensemble-boundary` | Permissions boundary — the ceiling for roles the identity creates |

Both scoped-permissions policies are attached to the `abreiss-ensemble-terraform`
user (`../identity.tf`); the identity's effective permissions are their union.

**Account id is redacted.** Every ARN uses the placeholder account `123456789012`;
the live values come from `data.aws_caller_identity` at apply time. Do not treat
these as the applied policies — they are a redacted, reviewable representation.

## The enumerated `Resource: "*"` exceptions (scoped policy)

AWS does not accept a resource ARN for the actions below, so they cannot be scoped
to `abreiss-ensemble-*`. Each is name/token/identity-only and **cannot read or
mutate the contents of any resource**. Regional ones are pinned to `var.aws_region`
via `aws:RequestedRegion`.

**Global (`AccountLevelGlobalActions`) — no region guard possible:**

| Action | Why it must be `*` / why it is safe |
| --- | --- |
| `s3:ListAllMyBuckets` | Lists bucket **names** only; grants no access to any bucket's contents. |
| `sts:GetCallerIdentity` | "Who am I" preflight; no resource, exposes no data. |

**Regional (`AccountLevelRegionalActions`) — guarded by `aws:RequestedRegion`:**

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

Everything else in the scoped policy is restricted to `abreiss-ensemble-*` ARNs.
The boundary policy likewise scopes to `abreiss-ensemble-*` except for the single
`ecr:GetAuthorizationToken` (same account-level token reason) and `apprunner:ListServices`.

## Size note

The scoped policy (`abreiss-ensemble-terraform`) renders to ~5,959 characters
(after #9's PassRole-condition removal bought back ~200), under IAM's
**6,144-character managed-policy limit**. `s3:GetBucket*`/
`s3:PutBucket*` are wildcarded over the bucket's config sub-resources (still
fully contained to the `abreiss-ensemble-*` bucket ARN) partly to stay within
that limit.

Issue #9 (the deploy pipeline) hit exactly this limit: read-only,
resource-scoped grants the AWS provider needs post-create
(`s3:GetAccelerateConfiguration`, `s3:GetReplicationConfiguration`,
`secretsmanager:GetResourcePolicy`, `apprunner:DescribeAutoScalingConfiguration`
+ its Delete/tag siblings) would have pushed the single policy well over
6,144 characters. Per this note's own guidance, they were split into the
second managed policy (`abreiss-ensemble-terraform-ext`, ~968 characters)
instead of widening scope or trimming existing statements. The two S3 actions
exist because the IAM action name doesn't match the S3 API operation name it
guards (`GetBucketAccelerateConfiguration`/`GetBucketReplication` are the API
ops; `GetAccelerateConfiguration`/`GetReplicationConfiguration` are the IAM
actions) — they are real gaps in `terraform_scoped`'s `s3:GetBucket*`
wildcard, not redundant with it. If a future issue needs still more, keep
splitting into additional `abreiss-ensemble-*` managed policies rather than
reaching for a broader action.

## How these were rendered

The identity's own `DenySelfModification` statement blocks it from reading its
own managed policies (`iam:GetPolicy`/`GetPolicyVersion` on its own ARNs), so a
normal `terraform plan` under the scoped identity fails outright. Every ARN
these documents depend on (`account_id`, `partition`, `region`, `resource_prefix`)
is a local/data value, not a managed-resource attribute, so `-refresh=false`
renders them without ever touching the denied resources:

```bash
terraform -chdir=terraform/bootstrap plan -refresh=false -out=plan.tfplan
terraform -chdir=terraform/bootstrap show -json plan.tfplan \
  | jq -r '.resource_changes[] | select(.address=="aws_iam_policy.boundary") | .change.after.policy' \
  | jq '.' | sed 's/<ACCOUNT_ID>/123456789012/g' > policies/abreiss-ensemble-boundary.json
# ...same for aws_iam_policy.terraform_scoped -> abreiss-ensemble-terraform.json
# ...same for aws_iam_policy.terraform_scoped_ext -> abreiss-ensemble-terraform-ext.json

aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-boundary.json
```

An account admin applying a fresh (never-yet-applied) bootstrap can use a plain
`terraform plan -out=plan.tfplan` instead — `-refresh=false` is only needed to
render these from an *already-applied* state under the scoped identity itself.
