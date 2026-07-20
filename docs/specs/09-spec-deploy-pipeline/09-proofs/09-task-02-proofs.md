# Task 02 Proofs - Terraform deploy module scaffold + runtime data stores

## Task Summary

This task adds a new, self-contained `terraform/deploy/` root module alongside
`terraform/bootstrap/` (no collision, same prefix/region/provider conventions):
`hashicorp/aws ~> 5.0`, `required_version >= 1.11.0`, an S3 remote backend with
**S3-native locking** (`use_lockfile = true`, no DynamoDB lock table) targeting
`abreiss-ensemble-tfstate`, and the runtime data stores — the S3 photos bucket
(`abreiss-ensemble-photos`, public-access-blocked, SSE) and the DynamoDB items
table (`abreiss-ensemble-items`, `itemId` partition key, on-demand billing).

## What This Task Proves

- The module is well-formed, canonically-formatted HCL (`fmt -check` / `validate`
  both exit `0`).
- The remote backend is configured for S3-native locking on the supported path
  (`use_lockfile = true`, no `dynamodb_table`), gated on Terraform `>= 1.11.0`.
- The state-bucket chicken-and-egg problem (Terraform needs a backend to
  initialize, but can't create its own backend bucket) has a documented,
  reproducible one-time bootstrap step usable by the already-scoped
  `abreiss-ensemble-terraform` identity — no policy widening required, since its
  existing `S3BucketScoped` statement already covers any `abreiss-ensemble-*`
  bucket.
- The data-store resources (S3 photos bucket + DynamoDB items table) are declared
  matching the app's single-item model, with no relational modeling.

## Evidence Summary

- `terraform fmt -check -diff` exits `0` — no formatting diffs.
- `terraform init -backend=false` and `terraform validate` both exit `0`.
- `versions.tf` shows `required_version = ">= 1.11.0"`, `aws ~> 5.0`, and a
  `backend "s3"` block with `use_lockfile = true` and no `dynamodb_table`.
- `data_stores.tf` declares the public-access-blocked, SSE-encrypted photos
  bucket and the `itemId`-keyed, `PAY_PER_REQUEST` DynamoDB table.
- The full `terraform plan` against real AWS (enumerating these resources) is
  **operator-run in task 6.0** per the task's own design — this module's backend
  requires the `abreiss-ensemble-tfstate` bucket and live AWS credentials, both
  out of scope for a local, credential-free implementation pass.

## Artifact: `terraform fmt -check` — canonical formatting

**What it proves:** The committed HCL is already in Terraform's canonical
format; no diffs.

**Why it matters:** This is Success Metric 2 from the spec — "well-formed,
canonically-formatted HCL."

**Command:**

```bash
cd terraform/deploy && terraform fmt -check -diff
```

**Result summary:** Exit code `0`, no output (no formatting changes needed).

```text
exit=0
```

## Artifact: `terraform init -backend=false` + `terraform validate`

**What it proves:** The module's provider requirements resolve and the HCL is
internally consistent (valid resource/attribute references, no syntax errors).

**Why it matters:** This is the local, credential-free half of Success Metric 2;
the S3 backend itself can only be initialized once the `abreiss-ensemble-tfstate`
bucket exists (task 6.1, operator-run).

**Command:**

```bash
terraform init -backend=false -input=false
terraform validate
```

**Result summary:** Both commands exit `0`. Terraform reports "The
configuration is valid." The only warning shown
(`Provider development overrides are in effect`) originates from the
developer's local `~/.terraformrc` CLI configuration pointing at an unrelated
`dev.local/nico/devops-api` override — it is environment noise, not a defect in
this module, and does not appear for other Terraform users/CI.

```text
Success! The configuration is valid, but there were some
validation warnings as shown above.
exit=0
```

## Artifact: `versions.tf` — remote backend with S3-native locking

**What it proves:** The remote-state backend uses `use_lockfile = true` (S3
native locking, GA in Terraform 1.11+) instead of the deprecated
DynamoDB-lock-table pattern, and the module refines bootstrap's
`>= 1.6.0` floor to `>= 1.11.0` to require that capability.

**Why it matters:** Directly satisfies FR "remote backend" and keeps the module
on the currently-supported locking path (no separate lock table to provision or
reason about).

**Artifact path:** `terraform/deploy/versions.tf`

**Result summary:** `required_version = ">= 1.11.0"`; `aws ~> 5.0`; `backend "s3"`
block sets `bucket = "abreiss-ensemble-tfstate"`, `key = "deploy/terraform.tfstate"`,
`region = "us-east-1"`, `use_lockfile = true` — no `dynamodb_table` argument.

## Artifact: State-bucket bootstrap (`create-state-bucket.sh` + README)

**What it proves:** The one-time step that creates the `abreiss-ensemble-tfstate`
bucket (versioned, SSE-encrypted, all public access blocked) is scripted and
reproducible, and is achievable by the already-scoped `abreiss-ensemble-terraform`
identity with no policy changes.

**Why it matters:** Without this, the module's own remote backend could never be
initialized the first time (`terraform init` needs the bucket to exist first).

**Artifact path:** `terraform/deploy/create-state-bucket.sh`,
`terraform/deploy/README.md` ("State bucket bootstrap" section)

**Result summary:** The script calls `aws s3api create-bucket` /
`put-bucket-versioning` / `put-bucket-encryption` / `put-public-access-block`
against `abreiss-ensemble-tfstate`, all of which are already permitted by the
`abreiss-ensemble-terraform` identity's existing `S3BucketScoped` policy
statement in `terraform/bootstrap/policies.tf` (any `abreiss-ensemble-*` bucket).

## Artifact: Data stores (`data_stores.tf`)

**What it proves:** The S3 photos bucket and DynamoDB items table match the
spec's exact naming, access, and schema requirements.

**Why it matters:** These are the two runtime data stores the deployed app
depends on (`S3PhotoStorage` and the DynamoDB Enhanced Client from task 1.0).

**Artifact path:** `terraform/deploy/data_stores.tf`

**Result summary:** `aws_s3_bucket.photos` (`abreiss-ensemble-photos`) has
`aws_s3_bucket_public_access_block` with all four block flags `true` and
`aws_s3_bucket_server_side_encryption_configuration` set to `AES256`.
`aws_dynamodb_table.items` (`abreiss-ensemble-items`) uses `hash_key = "itemId"`
(type `S`) and `billing_mode = "PAY_PER_REQUEST"` — matching the single-item
model with no other attributes declared (DynamoDB only requires key attributes
in the schema; the rest of the item's fields are schemaless).

## Reviewer Conclusion

`terraform/deploy/` now exists as a self-contained, validated module: pinned
versions, an S3-native-locked remote backend, a reproducible state-bucket
bootstrap, and the two runtime data stores declared to spec. `fmt`/`validate`
pass locally with no live AWS access; the full `plan` enumeration against real
AWS is deferred to the operator-run task 6.0, as the task list itself
specifies.
