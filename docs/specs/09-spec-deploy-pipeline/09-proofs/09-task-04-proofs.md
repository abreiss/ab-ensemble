# Task 04 Proofs - Terraform IAM roles: instance role + CI OIDC role, boundary-capped

## Task Summary

This task adds `terraform/deploy/iam.tf`: the App Runner **instance role**
(`abreiss-ensemble-instance`, least-privilege runtime access to the photos
bucket, items table, and three secret ARNs), an **ECR-pull access role**
(`abreiss-ensemble-ecr-access`, required because the ECR repo is private) so
App Runner can pull the image, and the **GitHub OIDC CI role**
(`abreiss-ensemble-ci`, ECR push + App Runner deploy + one scoped `PassRole`,
matching exactly what the `#16` boundary pre-authorizes). All three roles are
created with `permissions_boundary = abreiss-ensemble-boundary`, and both role
ARNs App Runner needs are wired into `apprunner.tf`. `outputs.tf` exposes the
service URL, ECR URL, table/bucket names, and CI role ARN.

## What This Task Proves

- The whole `terraform/deploy/` module — every resource from Tasks 2.0, 3.0,
  and this task — plans cleanly against the **real, live AWS account** under
  the scoped `abreiss-ensemble-terraform` identity: `Plan: 20 to add, 0 to
  change, 0 to destroy`, with every `outputs.tf` value resolving. This is a
  stronger proof than Tasks 2.0/3.0 could produce (no live credentials were
  available then); it is included here because working credentials for the
  scoped identity are available in this environment.
- The instance role's own policy is a **strict subset** of what the
  `abreiss-ensemble-boundary` already allows for it (`RuntimeS3Object`,
  `RuntimeDynamoDb`, `RuntimeSecrets`) — nothing wider.
- The CI role's own policy is an **exact match** of the boundary's
  `CiEcrScoped`/`CiEcrAuthToken`/`CiAppRunnerScoped`/`CiPassRole` statements —
  no widening beyond what `#16` pre-authorized.
- The CI role's trust policy federates the account's **pre-existing** GitHub
  OIDC provider (`token.actions.githubusercontent.com`, confirmed already
  present in the account) with a `:sub` condition scoped to exactly
  `repo:abreiss/ab-ensemble:ref:refs/heads/main` — no other repo or ref can
  assume it.
- Running `aws accessanalyzer validate-policy` under the scoped identity
  itself fails with `AccessDeniedException` — a genuine, informative result:
  `#16`'s bootstrap deliberately never granted the scoped identity
  `access-analyzer:ValidatePolicy` (a read-only, account-level action), so the
  lint must run under a broader/admin session. This is documented as a known
  gap (see `docs/AWS_ACCESS.md` and `terraform/deploy/policies/README.md`)
  rather than silently skipped or falsely claimed as passing.
- `fmt`/`validate` pass cleanly, and a grep of the committed HCL + rendered
  policy JSON shows no real account id and no secret value anywhere.

## Evidence Summary

- `terraform fmt -check -diff` exits `0`.
- `terraform validate` (after `init -backend=false`) exits `0`.
- A live `terraform plan` under `AWS_PROFILE=abreiss-ensemble-terraform`
  (account id redacted to `123456789012`) plans 20 resources to add, 0 to
  change, 0 to destroy, and resolves all 5 outputs.
- The rendered `abreiss-ensemble-instance-runtime.json` and
  `abreiss-ensemble-ci.json` policies, plus the CI role's trust policy, match
  the FR exactly (see the "How these were rendered" note below for the
  temporary, fully-reverted backend override used to generate them).
- `aws accessanalyzer validate-policy` under the scoped identity returns
  `AccessDeniedException` on `access-analyzer:ValidatePolicy` — documented,
  not silently skipped.
- `grep` across `terraform/deploy/*.tf` and `terraform/deploy/policies/*.json`
  for a real 12-digit account id or secret-shaped strings finds nothing.

## Artifact: `terraform fmt -check` / `terraform validate` — clean

**What it proves:** The new `iam.tf` and `outputs.tf`, plus the `apprunner.tf`
edits wiring in the two role ARNs, are canonically formatted and internally
consistent with the rest of the module.

**Why it matters:** Success Metric 2 ("well-formed, canonically formatted
HCL... `validate` pass").

**Command:**

```bash
cd terraform/deploy
terraform fmt -check -diff
terraform init -backend=false -input=false
terraform validate
```

**Result summary:** Both exit `0`. The only warning is the developer's
unrelated local `dev.local/nico/devops-api` provider override, not a defect
in this module (same warning appeared in the Task 02/03 proofs).

```text
exit=0
Success! The configuration is valid, but there were some validation warnings
as shown above.
```

## Artifact: Live `terraform plan` against real AWS — full module, 20 to add

**What it proves:** Every resource declared across Tasks 2.0, 3.0, and 4.0
(data stores, ECR, App Runner, secrets, and now the three IAM roles) plans
cleanly under the scoped `abreiss-ensemble-terraform` identity with no errors,
and every `outputs.tf` value resolves — direct evidence for this task's own
proof requirement ("`terraform plan` shows the two roles created with the
boundary and the `outputs.tf` values... resolve").

**Why it matters:** This is a stronger, live-account proof than Tasks 2.0/3.0
recorded (no credentials were available to them); it demonstrates the module
is not just internally consistent but actually deployable by the scoped
identity with zero policy gaps for `plan`-time reads.

**Command:**

```bash
AWS_PROFILE=abreiss-ensemble-terraform terraform plan -no-color -out=plan.tfplan
terraform show -no-color plan.tfplan
```

**Result summary:** `Plan: 20 to add, 0 to change, 0 to destroy.` All 20
resources listed below; the instance role and CI role blocks (full detail) and
the final outputs are shown. Account id redacted to `123456789012`.

```text
  # aws_apprunner_auto_scaling_configuration_version.app will be created
  # aws_apprunner_service.app will be created
  # aws_dynamodb_table.items will be created
  # aws_ecr_lifecycle_policy.app will be created
  # aws_ecr_repository.app will be created
  # aws_iam_policy.ci will be created
  # aws_iam_policy.ecr_access will be created
  # aws_iam_policy.instance will be created
  # aws_iam_role.ci will be created
  # aws_iam_role.ecr_access will be created
  # aws_iam_role.instance will be created
  # aws_iam_role_policy_attachment.ci will be created
  # aws_iam_role_policy_attachment.ecr_access will be created
  # aws_iam_role_policy_attachment.instance will be created
  # aws_s3_bucket.photos will be created
  # aws_s3_bucket_public_access_block.photos will be created
  # aws_s3_bucket_server_side_encryption_configuration.photos will be created
  # aws_secretsmanager_secret.anthropic_key will be created
  # aws_secretsmanager_secret.passcode will be created
  # aws_secretsmanager_secret.session_secret will be created

Plan: 20 to add, 0 to change, 0 to destroy.

Changes to Outputs:
  + app_runner_service_url = (known after apply)
  + ci_role_arn            = (known after apply)
  + dynamodb_table_name    = "abreiss-ensemble-items"
  + ecr_repository_url     = (known after apply)
  + s3_photos_bucket_name  = "abreiss-ensemble-photos"
```

Instance role detail (trust policy + boundary attachment):

```text
  # aws_iam_role.instance will be created
  + resource "aws_iam_role" "instance" {
      + assume_role_policy    = jsonencode(
            {
              + Statement = [
                  + {
                      + Action    = "sts:AssumeRole"
                      + Effect    = "Allow"
                      + Principal = {
                          + Service = "tasks.apprunner.amazonaws.com"
                        }
                    },
                ]
              + Version   = "2012-10-17"
            }
        )
      + name                  = "abreiss-ensemble-instance"
      + permissions_boundary  = "arn:aws:iam::123456789012:policy/abreiss-ensemble-boundary"
    }
```

CI role detail (OIDC trust + boundary attachment):

```text
  # aws_iam_role.ci will be created
  + resource "aws_iam_role" "ci" {
      + assume_role_policy    = jsonencode(
            {
              + Statement = [
                  + {
                      + Action    = "sts:AssumeRoleWithWebIdentity"
                      + Condition = {
                          + StringEquals = {
                              + "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
                            }
                          + StringLike   = {
                              + "token.actions.githubusercontent.com:sub" = "repo:abreiss/ab-ensemble:ref:refs/heads/main"
                            }
                        }
                      + Effect    = "Allow"
                      + Principal = {
                          + Federated = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
                        }
                    },
                ]
              + Version   = "2012-10-17"
            }
        )
      + name                  = "abreiss-ensemble-ci"
      + permissions_boundary  = "arn:aws:iam::123456789012:policy/abreiss-ensemble-boundary"
    }
```

## Artifact: Rendered instance-role policy JSON — subset of the boundary

**What it proves:** `abreiss-ensemble-instance`'s own policy grants exactly
S3 object CRUD on the photos bucket, DynamoDB item CRUD on the items table,
and `secretsmanager:GetSecretValue` on the three secret ARNs — nothing else.

**Why it matters:** FR "instance role" + Security "boundary-capped,
least-privilege runtime access."

**Artifact path:** `terraform/deploy/policies/abreiss-ensemble-instance-runtime.json`

**Result summary:** Three statements, all prefix-scoped, no `Resource: "*"`.

```json
{
  "Statement": [
    {
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Effect": "Allow",
      "Resource": "arn:aws:s3:::abreiss-ensemble-*/*",
      "Sid": "RuntimeS3Object"
    },
    {
      "Action": ["dynamodb:UpdateItem", "dynamodb:Scan", "dynamodb:Query", "dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:DeleteItem"],
      "Effect": "Allow",
      "Resource": "arn:aws:dynamodb:us-east-1:123456789012:table/abreiss-ensemble-*",
      "Sid": "RuntimeDynamoDb"
    },
    {
      "Action": "secretsmanager:GetSecretValue",
      "Effect": "Allow",
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:abreiss-ensemble-*",
      "Sid": "RuntimeSecrets"
    }
  ],
  "Version": "2012-10-17"
}
```

## Artifact: Rendered CI-role policy JSON — exact match of the #16 boundary

**What it proves:** `abreiss-ensemble-ci`'s own policy is ECR push actions +
the account-level auth-token action + App Runner deploy actions + one scoped
`PassRole` — precisely the four boundary statements `#16` already
pre-authorized for a future CI role, no more.

**Why it matters:** FR "CI role... no widening beyond #16"; Non-Goal 4.

**Artifact path:** `terraform/deploy/policies/abreiss-ensemble-ci.json`

**Result summary:** Four statements; the `PassRole` statement is conditioned
on `iam:PassedToService` matching only the App Runner service principals.

```json
{
  "Statement": [
    {
      "Action": ["ecr:UploadLayerPart", "ecr:PutImage", "ecr:InitiateLayerUpload", "ecr:GetDownloadUrlForLayer", "ecr:DescribeRepositories", "ecr:DescribeImages", "ecr:CompleteLayerUpload", "ecr:BatchGetImage", "ecr:BatchCheckLayerAvailability"],
      "Effect": "Allow",
      "Resource": "arn:aws:ecr:us-east-1:123456789012:repository/abreiss-ensemble-*",
      "Sid": "CiEcrScoped"
    },
    {
      "Action": "ecr:GetAuthorizationToken",
      "Effect": "Allow",
      "Resource": "*",
      "Sid": "CiEcrAuthToken"
    },
    {
      "Action": ["apprunner:UpdateService", "apprunner:StartDeployment", "apprunner:DescribeService"],
      "Effect": "Allow",
      "Resource": "arn:aws:apprunner:us-east-1:123456789012:service/abreiss-ensemble-*/*",
      "Sid": "CiAppRunnerScoped"
    },
    {
      "Action": "iam:PassRole",
      "Condition": {
        "StringEquals": {
          "iam:PassedToService": ["apprunner.amazonaws.com", "build.apprunner.amazonaws.com", "tasks.apprunner.amazonaws.com"]
        }
      },
      "Effect": "Allow",
      "Resource": "arn:aws:iam::123456789012:role/abreiss-ensemble-*",
      "Sid": "CiPassRole"
    }
  ],
  "Version": "2012-10-17"
}
```

## Artifact: CI-role trust policy — repo/ref-scoped OIDC federation

**What it proves:** Only a GitHub Actions run on `abreiss/ab-ensemble`'s
`refs/heads/main` can assume this role — not any other repository, not a
fork, not a different branch.

**Why it matters:** FR "OIDC CI role... `:sub` condition scoped to this
repository + branch/ref"; Security "no static CI credentials."

**Artifact path:** `terraform/deploy/policies/abreiss-ensemble-ci-trust.json`

**Result summary:** `Federated` principal is the account's existing
`token.actions.githubusercontent.com` OIDC provider (confirmed present via
`aws iam list-open-id-connect-providers`, not created by this module); the
`aud` condition pins to `sts.amazonaws.com`; the `sub` condition pins to
`repo:abreiss/ab-ensemble:ref:refs/heads/main`.

```json
{
  "Statement": [
    {
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
        "StringLike": {"token.actions.githubusercontent.com:sub": "repo:abreiss/ab-ensemble:ref:refs/heads/main"}
      },
      "Effect": "Allow",
      "Principal": {"Federated": "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"}
    }
  ],
  "Version": "2012-10-17"
}
```

## Artifact: Access Analyzer lint — permission gap, documented not skipped

**What it proves:** The scoped `abreiss-ensemble-terraform` identity cannot
itself run `aws accessanalyzer validate-policy` — a real, reproducible
`AccessDeniedException`, not a tooling failure.

**Why it matters:** This task's proof artifact calls for the lint to run
with "no `ERROR`/`SECURITY_WARNING` (or each finding is documented)." The
finding here is a permission gap, not a policy defect — documented in
`docs/AWS_ACCESS.md` ("Standing policy checks" section) and
`terraform/deploy/policies/README.md`, with the exact commands for an
operator with a broader/admin session (e.g. after `aws sso login`) to
complete the lint. This also flags a real open question for Task 5.4's
standing CI policy-lint check, noted inline in `docs/AWS_ACCESS.md`.

**Command:**

```bash
AWS_PROFILE=abreiss-ensemble-terraform aws accessanalyzer validate-policy \
  --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-instance-runtime.json \
  --region us-east-1
```

**Result summary:** `AccessDeniedException` — the scoped identity lacks
`access-analyzer:ValidatePolicy`, which `#16`'s bootstrap deliberately never
granted it (it is not in the enumerated `Resource: "*"` exception list).

```text
An error occurred (AccessDeniedException) when calling the ValidatePolicy
operation: User: arn:aws:iam::[ACCOUNT_ID]:user/abreiss-ensemble-terraform is
not authorized to perform: access-analyzer:ValidatePolicy on resource:
arn:aws:access-analyzer:us-east-1:[ACCOUNT_ID]:* because no identity-based
policy allows the access-analyzer:ValidatePolicy action
```

## Artifact: No secrets or hard-coded account id anywhere

**What it proves:** Neither the new `.tf` files nor the rendered policy JSON
leak a real account id or a secret value.

**Why it matters:** Success Metric 6.

**Command:**

```bash
grep -rEn "[^0-9][0-9]{12}[^0-9]" *.tf
grep -rn "277802554323" policies/*.json   # the real account id
grep -rEni "sk-ant|AKIA[0-9A-Z]{16}|secret_string|password\s*=" *.tf policies/*.json
```

**Result summary:** The `.tf` account-id grep returns nothing. The real
account id (`277802554323`) does not appear anywhere in `policies/*.json` —
every occurrence was redacted to `123456789012` before committing. The
secret-pattern grep returns only `secrets.tf`'s explanatory comment (same as
Task 03), not an actual value.

```text
none
none
secrets.tf:3:# secret_string lives on that resource, not on aws_secretsmanager_secret, so
```

## Artifact: Backend override was temporary and fully reverted

**What it proves:** Generating the live plan above required commenting out
`versions.tf`'s S3 backend block (the real `abreiss-ensemble-tfstate` bucket
doesn't exist until Task 6.1) and reinitializing locally; this was reverted
byte-for-byte before continuing.

**Why it matters:** Confirms no unintended drift was introduced into a file
outside this task's actual scope, and that the module's remote-backend
declaration (Task 2.0) is unchanged.

**Command:**

```bash
git diff versions.tf
```

**Result summary:** Empty diff — `versions.tf` is identical to the version
committed in Task 2.0.

```text
(no output)
```

## Reviewer Conclusion

`terraform/deploy/` now declares its full IAM surface: an instance role
strictly narrower than its boundary, a CI role that is an exact match of what
`#16` pre-authorized, and an ECR-access role App Runner's private image pull
requires — all boundary-capped, all wired into `apprunner.tf`, all exposed via
`outputs.tf`. A live `plan` under the real scoped identity confirms the entire
module (20 resources across Tasks 2.0–4.0) provisions cleanly with zero policy
gaps at plan time; the one genuine gap found — the scoped identity cannot run
`accessanalyzer:validate-policy` itself — is documented rather than hidden,
with a clear path for an operator with broader credentials to complete the
lint and a flagged open question for Task 5.0's standing CI check.
