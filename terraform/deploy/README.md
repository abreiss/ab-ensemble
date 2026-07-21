# `terraform/deploy/` — Ensemble's cloud footprint

Self-contained root module provisioning Ensemble's deploy-time AWS resources: the
S3 photos bucket, the DynamoDB items table, ECR, App Runner, Secrets Manager
containers, and the IAM roles that run them. It is separate from
[`terraform/bootstrap/`](../bootstrap/) (which creates the scoped
`abreiss-ensemble-terraform` identity Terraform runs *as* — see
[`docs/AWS_ACCESS.md`](../../docs/AWS_ACCESS.md)) and never modifies it.

## State bucket bootstrap (one-time, chicken-and-egg)

This module's own remote state lives in S3 (`abreiss-ensemble-tfstate`,
S3-native locking via `use_lockfile`, see `versions.tf`). Terraform cannot create
that bucket via `apply` before it has a backend to initialize against, so the
bucket is created once, out of band, by the same scoped identity:

```bash
AWS_PROFILE=abreiss-ensemble-terraform ./create-state-bucket.sh
```

This creates `abreiss-ensemble-tfstate` (versioned, SSE-encrypted, all public
access blocked) — permitted today by the `abreiss-ensemble-terraform` identity's
existing `S3BucketScoped` policy statement (any `abreiss-ensemble-*` bucket), no
policy change required.

## Init / plan / apply

```bash
cd terraform/deploy
terraform init      # -backend=false locally if the state bucket doesn't exist yet
terraform fmt -check
terraform validate
terraform plan       # review before applying
terraform apply       # as the abreiss-ensemble-terraform identity
```

## Seed image (one-time, before the first successful `apply`)

`apprunner.tf` pins the service to `<ecr-repo>:latest`, pushed exactly once as
the bootstrap seed before CI ever runs (every later deploy repoints the service
at an immutable `sha-<git-sha>` tag instead). **App Runner only runs
`linux/amd64` images** — a seed built on an Apple Silicon Mac without a
platform flag is `arm64`, pulls fine, and then fails the deploy with a bare
`Failed to deploy your application image` and no application logs (this
exact failure cost a full debugging round in Task 6.1). Always build the seed
as:

```bash
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker buildx build --platform linux/amd64 --provenance=false \
  -t <account>.dkr.ecr.us-east-1.amazonaws.com/abreiss-ensemble-app:latest --load .
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/abreiss-ensemble-app:latest
```

The repo's tags are IMMUTABLE: a wrong `:latest` cannot be overwritten — delete
the image (`aws ecr batch-delete-image --image-ids imageTag=latest`) and push
again.

## What it creates

| File | Resources |
| --- | --- |
| `data_stores.tf` | S3 photos bucket (`abreiss-ensemble-photos`, public-access-blocked, SSE) + DynamoDB `abreiss-ensemble-items` table |
| `ecr.tf` | ECR repository for the app image |
| `secrets.tf` | Secrets Manager containers (no values — populated out-of-band) |
| `apprunner.tf` | The App Runner service running the app |
| `iam.tf` | App Runner instance role, ECR-pull access role, and the GitHub OIDC CI role — all boundary-capped |
| `outputs.tf` | Service URL, ECR repo URL, table/bucket names, CI role ARN |
| `policies/` | Rendered instance-role + CI-role policy JSON, for human review and `aws accessanalyzer validate-policy` (see `policies/README.md`) |

See [`docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) for the deploy narrative
and [`docs/AWS_ACCESS.md`](../../docs/AWS_ACCESS.md) for the identity this module
runs as.

## Wiring outputs into GitHub Actions

`.github/workflows/deploy.yml` and `ci.yml`'s `policy-lint` job read this
module's outputs as **repository variables** (Settings > Secrets and
variables > Actions > Variables) — not secrets, since none of them are
credential values, just resource identifiers. After `terraform apply`
(Task 6.1), the operator sets:

| Repo variable | Source output |
| --- | --- |
| `AWS_CI_ROLE_ARN` | `ci_role_arn` |
| `AWS_ECR_REPOSITORY_URL` | `ecr_repository_url` |
| `AWS_APPRUNNER_SERVICE_ARN` | `app_runner_service_arn` |

## Policy lint (`aws accessanalyzer validate-policy`)

The rendered instance-role and CI-role policies are committed under
[`policies/`](policies/). `access-analyzer:ValidatePolicy` is a read-only,
account-level action the scoped `abreiss-ensemble-terraform` identity was
**not** granted by `#16`'s bootstrap (by design — see `docs/AWS_ACCESS.md`'s
enumerated exception list). Run the lint under a broader/admin AWS session:

```bash
aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-instance-runtime.json
aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://policies/abreiss-ensemble-ci.json
```
