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

variable "purge_unowned_data" {
  description = "One-time, opt-in cleanup (spec #15). When true, the deployed container's startup runner purges every pre-existing 'unowned' item/outfit (rows written before per-user ownership, so they carry no userId) plus each such item's photo, leaving owned rows and the reserved usage#<date> counters untouched. The purge is idempotent. Set true for a single deploy to clear legacy data, then set back to false (App Runner drops empty-string env values, so this is wired as an explicit \"true\"/\"false\" literal)."
  type        = bool
  default     = false
}

variable "seed_account_enabled" {
  description = "Opt-in startup seeding of a single default account (issue #14). When true, this module provisions the ENSEMBLE_SEED_EMAIL / ENSEMBLE_SEED_PASSWORD secret containers (values populated out-of-band) and wires them into the service, so SeedAccountRunner seeds an account on boot. When false (default) the seed secrets are neither created nor referenced -- the service never depends on out-of-band seed values, and invite-only signup (POST /api/accounts, gated by ENSEMBLE_PASSCODE) is the only account-creation path. Enabling REQUIRES populating both secret values, or the App Runner revision fails to resolve them and rolls back."
  type        = bool
  default     = false
}
