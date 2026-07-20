# Task 03 Proofs - Terraform app-delivery resources: ECR, App Runner, Secrets Manager containers

## Task Summary

This task adds the app-delivery half of `terraform/deploy/`: an ECR repository
(`abreiss-ensemble-app`, immutable tags + image scanning + a lifecycle policy),
the App Runner service (`abreiss-ensemble-app`, port `8080`, `/api/health`
health check, min 1 / max 2 instances, non-secret runtime env vars), and three
Secrets Manager secret *containers* with no value ever entering Terraform
state.

## What This Task Proves

- The ECR repository enforces `IMMUTABLE` tags and scans on push, with a
  lifecycle policy that expires stale untagged images and caps tagged
  (`sha-*`) images at 10 — traceable, bounded image storage.
- The App Runner service runs the ECR image on port `8080`, health-checks
  `/api/health`, is capped at 1–2 instances via a dedicated auto-scaling
  configuration, and sources its four non-secret `ENSEMBLE_*` env vars as
  literals/references while its three secret env vars are sourced **only by
  Secrets Manager ARN** — no plaintext ever appears in the App Runner
  configuration.
- The three Secrets Manager containers declare no `secret_string` (that
  attribute lives on the separate `aws_secretsmanager_secret_version`
  resource, which is never created here), so no secret value can ever land in
  Terraform state from this module.
- The module `fmt`/`validate`s cleanly on its own even though `apprunner.tf`
  is written for the instance role + ECR-access role that Task 4.0 declares
  next — both `instance_role_arn` and `access_role_arn` are Optional in the
  AWS provider schema, exactly as the task list's cross-task dependency note
  anticipated.
- A grep of the committed HCL shows no hard-coded account id and no secret
  value anywhere in `terraform/deploy/`.

## Evidence Summary

- `terraform fmt -check -diff` exits `0` — no formatting diffs across the
  three new files.
- `terraform validate` (after `init -backend=false`, same as Task 2.0) exits
  `0`: "The configuration is valid." The only warning is the developer's
  unrelated local `dev.local/nico/devops-api` CLI override, not a defect in
  this module.
- `grep` over `terraform/deploy/*.tf` for a 12-digit account id and for
  secret-shaped strings (`sk-ant`, `AKIA...`, `secret_string =`, `password =`)
  finds nothing except the comment in `secrets.tf` *explaining* why
  `secret_string` is absent.
- The full `terraform plan` against real AWS (enumerating the ECR repo, App
  Runner service, and secret containers) is **operator-run in task 6.0**, per
  the task's own design — this module's remote backend needs the
  `abreiss-ensemble-tfstate` bucket and live credentials, same constraint
  documented in the Task 02 proofs.

## Artifact: `terraform fmt -check` — canonical formatting

**What it proves:** `ecr.tf`, `secrets.tf`, and `apprunner.tf` are already in
Terraform's canonical format.

**Why it matters:** Half of Success Metric 2 ("well-formed, canonically
formatted HCL").

**Command:**

```bash
cd terraform/deploy && terraform fmt -check -diff
```

**Result summary:** Exit code `0`, no output — no formatting changes needed.

```text
exit=0
```

## Artifact: `terraform validate` — internally consistent configuration

**What it proves:** Every resource/attribute reference in the module resolves
— including `apprunner.tf`'s references to `aws_ecr_repository.app`,
`aws_s3_bucket.photos`, `aws_dynamodb_table.items`, and the three
`aws_secretsmanager_secret` resources declared in this task.

**Why it matters:** This is the other half of Success Metric 2, and confirms
the deliberate design choice below did not silently break the module.

**Command:**

```bash
terraform init -backend=false -input=false
terraform validate
```

**Result summary:** Both commands exit `0`.

```text
Success! The configuration is valid, but there were some validation warnings
as shown above.
exit=0
```

## Artifact: Forward-reference design for the instance role / access role

**What it proves:** `apprunner.tf`'s `instance_configuration` and
`source_configuration.authentication_configuration` intentionally omit
`instance_role_arn` and `access_role_arn` in this task, because both roles are
declared next in Task 4.0's `iam.tf`. Both attributes are **Optional** in the
`aws_apprunner_service` schema (confirmed against the `hashicorp/aws`
v5.100.0 provider docs), so omitting them lets this task's own `validate`
pass standalone; Task 4.0 will add the two ARN references once the roles
exist, and both must be present before the operator's `plan`/`apply` in
task 6.0 — exactly the ordering the task list's "Cross-task dependency" note
describes.

**Why it matters:** Confirms the module doesn't need a stub/placeholder IAM
role checked into this task just to satisfy `validate`, and that the deferred
wiring is a deliberate, schema-backed choice rather than an oversight.

**Artifact path:** `terraform/deploy/apprunner.tf` (top-of-file comment +
`instance_configuration` / `source_configuration` blocks)

**Result summary:** `terraform validate` above already demonstrates this
holds — the whole module (all `.tf` files, including the not-yet-authored
`iam.tf`) validates without error today.

## Artifact: No secrets or hard-coded account id in the committed HCL

**What it proves:** Nothing in the three new files leaks a secret value or a
literal AWS account id.

**Why it matters:** Directly satisfies Success Metric 6 ("no secrets
committed") and the FR requirement that Secrets Manager values are sourced by
ARN only.

**Command:**

```bash
grep -rEn "[^0-9][0-9]{12}[^0-9]" *.tf
grep -rEni "sk-ant|AKIA[0-9A-Z]{16}|secret_string|password\s*=" *.tf
```

**Result summary:** The account-id grep returns nothing. The secret-pattern
grep returns exactly one line — `secrets.tf`'s comment explaining that
`secret_string` is deliberately not declared anywhere in this module; no
actual secret value or key is present.

```text
secrets.tf:3:# secret_string lives on that resource, not on aws_secretsmanager_secret, so
```

## Artifact: `apprunner.tf` — service, health check, env vars, secret ARNs

**What it proves:** The App Runner service matches the spec's exact shape:
ECR image source (`ECR`, not `ECR_PUBLIC`), port `8080`, `/api/health` HTTP
health check, a dedicated `aws_apprunner_auto_scaling_configuration_version`
(min 1 / max 2), the four non-secret env vars, and the three secret env vars
sourced by ARN.

**Why it matters:** This is the FR "App Runner service" and "App Runner
secret sourcing" requirement in one file.

**Artifact path:** `terraform/deploy/apprunner.tf`

**Result summary:** `runtime_environment_variables` sets
`ENSEMBLE_PHOTOS_BACKEND = "s3"`, `ENSEMBLE_PHOTOS_S3_BUCKET =
aws_s3_bucket.photos.bucket`, `ENSEMBLE_DYNAMODB_ENDPOINT = ""`, and
`ENSEMBLE_DYNAMODB_TABLE_NAME = aws_dynamodb_table.items.name` — all literals
or resource references, never a secret. `runtime_environment_secrets` maps
`ENSEMBLE_ANTHROPIC_API_KEY` / `ENSEMBLE_PASSCODE` / `ENSEMBLE_SESSION_SECRET`
to the three `aws_secretsmanager_secret.*.arn` values declared in `secrets.tf`.
`image_identifier` points at `${aws_ecr_repository.app.repository_url}:latest`
— the immutable `:latest` tag is pushed exactly once as the Task 6.1 bootstrap
seed image; every later deploy repoints the service at a new `sha-<git-sha>`
tag via `aws apprunner update-service` (Task 5.0), never a second push to
`:latest`.

## Artifact: `ecr.tf` — immutable, scanned repository with a bounded lifecycle

**What it proves:** The ECR repository matches the FR: `IMMUTABLE` tag
mutability, `scan_on_push = true`, and a lifecycle policy suited to the
`sha-<git-sha>` tagging scheme Task 5.0 will use.

**Why it matters:** Immutable tags are what makes rollback-by-redeploying-an
-earlier-tag (task 6.6) safe, and scanning + the lifecycle policy keep the
repo from silently accumulating unscanned or unbounded images.

**Artifact path:** `terraform/deploy/ecr.tf`

**Result summary:** `aws_ecr_repository.app` sets
`image_tag_mutability = "IMMUTABLE"` and
`image_scanning_configuration { scan_on_push = true }`.
`aws_ecr_lifecycle_policy.app` expires untagged images after 1 day and keeps
only the 10 most recent `sha-`-prefixed tagged images.

## Artifact: `secrets.tf` — three value-less secret containers

**What it proves:** The three secret containers
(`abreiss-ensemble-anthropic-key`, `-passcode`, `-session-secret`) are
declared with no value-bearing resource at all.

**Why it matters:** Directly satisfies Resolved Decision Q4 and the FR
requirement that Terraform never holds plaintext secret values.

**Artifact path:** `terraform/deploy/secrets.tf`

**Result summary:** Three `aws_secretsmanager_secret` resources, each with
only a `name` and a `description`; no `aws_secretsmanager_secret_version`
resource exists anywhere in the module.

## Reviewer Conclusion

`terraform/deploy/` now declares its full app-delivery surface — ECR, App
Runner, and the secret containers — matching the spec's naming, security, and
env-var requirements. `fmt`/`validate` pass locally with no live AWS access,
including the deliberately-deferred instance-role/access-role wiring that
Task 4.0 completes next; a grep of the committed HCL confirms no secret value
or hard-coded account id anywhere. The full `plan` enumeration against real
AWS remains operator-run in task 6.0, as designed.
