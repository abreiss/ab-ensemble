# The two knobs shared by every resource in this module, mirroring
# terraform/bootstrap/variables.tf so the abreiss-ensemble-* scoping conventions
# never drift between the two roots.

variable "aws_region" {
  description = "AWS region for regional resources (DynamoDB, ECR, App Runner, Secrets Manager). Matches application.yml; IAM and S3-bucket ARNs are partition/global."
  type        = string
  default     = "us-east-1"
}

variable "resource_prefix" {
  description = "Name prefix scoping every resource this module creates. All resources must be named <prefix>-* (e.g. abreiss-ensemble-photos), matching the abreiss-ensemble-terraform identity's scoping in terraform/bootstrap."
  type        = string
  default     = "abreiss-ensemble"
}

variable "github_repository" {
  description = "GitHub \"owner/repo\" the CI OIDC role's trust policy is scoped to (the sts:sub condition only matches tokens minted for this repository)."
  type        = string
  default     = "abreiss/ab-ensemble"
}

variable "github_repository_immutable" {
  description = "ID-stamped \"owner@ownerId/repo@repoId\" form of github_repository. This enterprise's GitHub OIDC tokens carry the ID-stamped (\"immutable\") subject -- verified live: sub = repo:abreiss@45674553/ab-ensemble@1306743058:ref:... -- so the trust policy must match it; the plain form is kept alongside in case stamping is ever toggled off upstream. Both forms pin the same repository."
  type        = string
  default     = "abreiss@45674553/ab-ensemble@1306743058"
}

variable "github_ref" {
  description = "Git ref the CI OIDC role's trust policy is scoped to, in GitHub Actions' OIDC sub-claim format (e.g. refs/heads/main). Only workflow runs on this ref may assume the CI role."
  type        = string
  default     = "refs/heads/main"
}
