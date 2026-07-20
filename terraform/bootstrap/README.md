# `terraform/bootstrap/` — scoped Terraform-runner identity

This is a **one-time bootstrap module**, applied **once by an account admin** with
elevated credentials. It creates the scoped AWS identity that Claude/Terraform runs
*as* for issue #9's infrastructure work: a permissions-boundary policy, a scoped
permissions policy, and a dedicated `abreiss-ensemble-terraform` IAM user whose
access keys live only in the developer's local environment.

It is **not** part of #9's deploy pipeline and is not applied by CI. See
[`docs/AWS_ACCESS.md`](../../docs/AWS_ACCESS.md) for the full narrative (what it
creates, how the admin applies it, how the key reaches the local environment, and
rotation/revocation).

## What it creates

| Resource | Name | Purpose |
| --- | --- | --- |
| `aws_iam_policy` (boundary) | `abreiss-ensemble-boundary` | Ceiling for every role this identity creates |
| `aws_iam_policy` (scoped) | `abreiss-ensemble-terraform` | What Claude/Terraform may do (`abreiss-ensemble-*` only) |
| `aws_iam_user` | `abreiss-ensemble-terraform` | The local Terraform runner |
| `aws_iam_access_key` | (for the user) | Programmatic key, surfaced as a sensitive output |

## One-time apply (admin, elevated creds)

```bash
cd terraform/bootstrap
terraform init                 # downloads the AWS provider
terraform plan                 # review the two policies + user before applying
terraform apply                # creates the identity; access key lands in local state
```

Then hand the developer the key via the sensitive outputs (see `docs/AWS_ACCESS.md`
for the `terraform output` commands and how to load it into a local AWS profile).

## State is local and git-ignored — never committed

`terraform apply` writes the access-key **secret** into `terraform.tfstate`. The
repository `.gitignore` blocks `*.tfstate*`, `.terraform/`, and `*.tfvars`, and a
pre-commit secret scan blocks stray AWS keys. Do **not** add a remote state backend
here — the one-time bootstrap uses local, git-ignored state by design. A remote
backend (if any) is #9's concern.

## Region

Regional ARNs (DynamoDB, ECR, App Runner, Secrets Manager) default to `us-east-1`
via `var.aws_region`. IAM and S3-bucket ARNs are partition/global. Changing region
is a one-variable edit.
