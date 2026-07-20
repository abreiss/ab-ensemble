# Rendered policy JSON — review copies

These files are the **rendered** output of the `aws_iam_policy_document` data
sources in [`../iam.tf`](../iam.tf), captured for human review and for
`aws accessanalyzer validate-policy`. `iam.tf` is the source of truth; these
JSON copies are regenerated from it (see "How these were rendered" below).

| File | Role | Purpose |
| --- | --- | --- |
| `abreiss-ensemble-instance-runtime.json` | `abreiss-ensemble-instance` | App Runner instance role — what the running app may touch (photos bucket, items table, the three secret ARNs). |
| `abreiss-ensemble-ci.json` | `abreiss-ensemble-ci` | GitHub OIDC CI role — ECR push + App Runner deploy + one scoped `PassRole`, matching exactly what the `abreiss-ensemble-boundary` (#16) pre-authorizes. |
| `abreiss-ensemble-ci-trust.json` | `abreiss-ensemble-ci` (trust policy) | Federates the account's pre-existing GitHub OIDC provider, scoped to this repo + ref via the `token.actions.githubusercontent.com:sub` condition. |

The `abreiss-ensemble-ecr-access` role (private-ECR image pull for App Runner,
`iam.tf`) is intentionally not rendered here — it is a narrow 3-action ECR-pull
policy, already fully visible inline in `iam.tf`, and is not one of the two
roles Task 4.0's proof artifacts call out.

**Account id is redacted.** Every ARN uses the placeholder account
`123456789012`; the live values come from `data.aws_caller_identity` at apply
time. Do not treat these as the applied policies — they are a redacted,
reviewable representation.

## Boundary-capped, not a re-statement of the boundary

Every role above is created with `permissions_boundary = abreiss-ensemble-boundary`
(see [`docs/AWS_ACCESS.md`](../../../docs/AWS_ACCESS.md)), so a role's *effective*
permissions are the intersection of its own policy (rendered here) **and** that
boundary — never wider than either. The instance role's statements are a subset
of the boundary's `RuntimeS3Object`/`RuntimeDynamoDb`/`RuntimeSecrets`
statements; the CI role's statements are an exact match of the boundary's
`CiEcrScoped`/`CiEcrAuthToken`/`CiAppRunnerScoped`/`CiPassRole` statements — no
action appears here that the boundary does not already allow.

## How these were rendered

The module's own remote state bucket (`abreiss-ensemble-tfstate`) does not
exist yet at authoring time (it is created in Task 6.1), so the S3 backend
block in `versions.tf` was **temporarily commented out** to run a live `plan`
against a local backend, then restored byte-for-byte (`git diff versions.tf`
confirmed empty before continuing):

```bash
cd terraform/deploy
# versions.tf backend "s3" {...} temporarily commented out
terraform init -reconfigure -input=false
AWS_PROFILE=abreiss-ensemble-terraform terraform plan -input=false -out=plan.tfplan
terraform show -json plan.tfplan > plan.json
# versions.tf backend restored; re-init with -backend=false

jq -r '.planned_values.root_module.resources[]
       | select(.address=="aws_iam_policy.instance") | .values.policy' plan.json \
  | jq '.' | sed 's/<ACCOUNT_ID>/123456789012/g' > policies/abreiss-ensemble-instance-runtime.json
# ...same pattern for aws_iam_policy.ci -> abreiss-ensemble-ci.json
# ...and aws_iam_role.ci's .values.assume_role_policy -> abreiss-ensemble-ci-trust.json

aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-instance-runtime.json
```

See [`09-task-04-proofs.md`](../../../docs/specs/09-spec-deploy-pipeline/09-proofs/09-task-04-proofs.md)
for the full transcript, including the `access-analyzer:ValidatePolicy`
permission gap the scoped `abreiss-ensemble-terraform` identity hit (expected —
it is a read-only, account-level action `#16` never granted the runtime
identity; the lint itself must be run under a broader/admin session, same as
the one-time bootstrap lint in `terraform/bootstrap/policies/README.md`).
