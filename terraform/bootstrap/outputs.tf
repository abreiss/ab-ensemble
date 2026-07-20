# Outputs for the one-time bootstrap apply.
#
# The access key id and secret are the ONLY place the runner's credentials are
# surfaced. Both are marked `sensitive` so Terraform never prints them in plan/apply
# logs; read them explicitly after apply and load them into a LOCAL AWS profile
# (see docs/AWS_ACCESS.md → "Access-key delivery"):
#
#   terraform -chdir=terraform/bootstrap output -raw terraform_runner_access_key_id
#   terraform -chdir=terraform/bootstrap output -raw terraform_runner_secret_access_key
#
# The secret is stored in local terraform.tfstate, which is git-ignored — never
# commit state. If the admin generates the key in the console instead (see
# identity.tf), comment out aws_iam_access_key and the two credential outputs below.

output "terraform_runner_user_arn" {
  description = "ARN of the scoped abreiss-ensemble-terraform IAM user."
  value       = aws_iam_user.terraform_runner.arn
}

output "terraform_runner_access_key_id" {
  description = "Access key id for the abreiss-ensemble-terraform user. Load into a local AWS profile; never commit."
  value       = aws_iam_access_key.terraform_runner.id
  sensitive   = true
}

output "terraform_runner_secret_access_key" {
  description = "Secret access key for the abreiss-ensemble-terraform user. Load into a local AWS profile; never commit. Stored only in git-ignored local state."
  value       = aws_iam_access_key.terraform_runner.secret
  sensitive   = true
}

output "scoped_policy_arn" {
  description = "ARN of the abreiss-ensemble-terraform scoped permissions policy attached to the runner."
  value       = aws_iam_policy.terraform_scoped.arn
}

output "boundary_policy_arn" {
  description = "ARN of the abreiss-ensemble-boundary permissions boundary. Pass this as --permissions-boundary when creating abreiss-ensemble-* roles."
  value       = aws_iam_policy.boundary.arn
}
