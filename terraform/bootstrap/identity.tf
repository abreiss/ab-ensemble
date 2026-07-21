# =============================================================================
# The scoped Terraform-runner identity.
#
# A dedicated IAM user (abreiss-ensemble-terraform) with ONLY the scoped
# permissions policy attached — the identity Claude/Terraform authenticates as for
# issue #9's infra work. Its programmatic access key lives only in the developer's
# local environment (see outputs.tf + docs/AWS_ACCESS.md); it is never committed.
#
# The user is inside the abreiss-ensemble-* namespace and is self-protected: the
# scoped policy's DenySelfModification statement denies iam:* on this user's ARN
# (local.iam_user_arn), so the identity cannot re-permission or delete itself.
#
# No permissions boundary is attached to the USER — the boundary is the ceiling for
# the ROLES this user creates (App Runner instance role, CI role), enforced by the
# scoped policy's IamCreateRoleWithBoundaryOnly condition.
# =============================================================================

resource "aws_iam_user" "terraform_runner" {
  name = "${var.resource_prefix}-terraform"

  # IAM tag values allow only [\p{L}\p{Z}\p{N}_.:/=+\-@] — no '#', so "issue 9" not "issue #9".
  tags = {
    Purpose   = "Scoped Terraform runner for Ensemble issue 9 infrastructure"
    ManagedBy = "terraform/bootstrap"
    Spec      = "docs/specs/16-spec-scoped-iam-identity"
  }
}

resource "aws_iam_user_policy_attachment" "terraform_runner_scoped" {
  user       = aws_iam_user.terraform_runner.name
  policy_arn = aws_iam_policy.terraform_scoped.arn
}

# Second policy attachment — terraform_scoped_ext (policies.tf) exists only
# because terraform_scoped alone would exceed IAM's managed-policy size limit.
resource "aws_iam_user_policy_attachment" "terraform_runner_scoped_ext" {
  user       = aws_iam_user.terraform_runner.name
  policy_arn = aws_iam_policy.terraform_scoped_ext.arn
}

# Programmatic access key for local `terraform apply`. The secret is surfaced only
# through the sensitive outputs in outputs.tf and is written into local
# terraform.tfstate (git-ignored) — see the "state is local" warning in the module
# README and docs/AWS_ACCESS.md.
#
# Stricter alternative (no secret in state): comment this resource and the matching
# outputs out and have the admin generate the key in the IAM console instead. Both
# paths satisfy "never committed"; the console path keeps the secret out of state
# entirely. See docs/AWS_ACCESS.md → "Access-key delivery".
resource "aws_iam_access_key" "terraform_runner" {
  user = aws_iam_user.terraform_runner.name
}
