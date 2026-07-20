# Rendered policy JSON — review copies

These two files are the **rendered** output of the `aws_iam_policy_document` data
sources in [`../policies.tf`](../policies.tf), captured for human review and for
`aws accessanalyzer validate-policy`. `policies.tf` is the source of truth; these
JSON copies are regenerated from it (see "How these were rendered" below).

| File | Managed policy | Role |
| --- | --- | --- |
| `abreiss-ensemble-terraform.json` | `abreiss-ensemble-terraform` | Scoped permissions — what Claude/Terraform may do |
| `abreiss-ensemble-boundary.json` | `abreiss-ensemble-boundary` | Permissions boundary — the ceiling for roles the identity creates |

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

The scoped policy renders to ~6,038 characters (excluding whitespace), under IAM's
**6,144-character managed-policy limit**. `s3:GetBucket*`/`s3:PutBucket*` are
wildcarded over the bucket's config sub-resources (still fully contained to the
`abreiss-ensemble-*` bucket ARN) partly to stay within that limit. If issue #9
needs additional actions, prefer splitting into a second `abreiss-ensemble-*`
managed policy over widening scope.

## How these were rendered

```bash
terraform -chdir=terraform/bootstrap plan -out=plan.tfplan
terraform -chdir=terraform/bootstrap show -json plan.tfplan \
  | jq -r '.planned_values.root_module.resources[]
           | select(.address=="aws_iam_policy.boundary") | .values.policy' \
  | jq '.' | sed 's/<ACCOUNT_ID>/123456789012/g' > policies/abreiss-ensemble-boundary.json
# ...same for aws_iam_policy.terraform_scoped -> abreiss-ensemble-terraform.json

aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-boundary.json
```
