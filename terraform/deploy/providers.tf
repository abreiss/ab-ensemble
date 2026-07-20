# Provider + caller-identity data sources, mirroring terraform/bootstrap/providers.tf.
# The account id is derived at plan time from the applying identity rather than
# hard-coded, so the module is portable across accounts and leaks no account id
# into the committed HCL.

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

data "aws_region" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition
  region     = data.aws_region.current.name
  prefix     = var.resource_prefix

  # Reusable ARN fragments for this module's resources and policy statements,
  # spanning tasks 2.0-4.0. Kept here so every reference shares one prefix and one
  # account instead of re-deriving the pattern per file.
  s3_bucket_arn = "arn:${local.partition}:s3:::${local.prefix}-*"
  s3_object_arn = "arn:${local.partition}:s3:::${local.prefix}-*/*"
  dynamodb_arn  = "arn:${local.partition}:dynamodb:${var.aws_region}:${local.account_id}:table/${local.prefix}-*"
  ecr_arn       = "arn:${local.partition}:ecr:${var.aws_region}:${local.account_id}:repository/${local.prefix}-*"
  apprunner_arn = "arn:${local.partition}:apprunner:${var.aws_region}:${local.account_id}:service/${local.prefix}-*/*"
  secrets_arn   = "arn:${local.partition}:secretsmanager:${var.aws_region}:${local.account_id}:secret:${local.prefix}-*"
  iam_role_arn  = "arn:${local.partition}:iam::${local.account_id}:role/${local.prefix}-*"

  # The abreiss-ensemble-boundary ARN, computed rather than a data lookup: the
  # instance/CI/ecr-access roles this module creates (iam.tf, Task 4.0) must
  # name this exact ARN as their permissions_boundary or the abreiss-ensemble-
  # terraform identity's IamCreateRoleWithBoundaryOnly condition (bootstrap
  # policies.tf) denies iam:CreateRole.
  boundary_policy_arn = "arn:${local.partition}:iam::${local.account_id}:policy/${local.prefix}-boundary"

  # Service principals iam:PassRole may target, mirroring bootstrap's
  # apprunner_pass_principals so the CI role's PassRole condition matches
  # exactly what the #16 boundary already allows.
  apprunner_pass_principals = [
    "apprunner.amazonaws.com",
    "build.apprunner.amazonaws.com",
    "tasks.apprunner.amazonaws.com",
  ]
}
