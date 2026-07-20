# Provider + caller-identity data sources. The account id is derived at plan time
# from the caller (whichever elevated admin runs the one-time apply) rather than
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

  # Reusable ARN fragments for the resource-scoped policy statements. Kept here so
  # every statement references one prefix and one account, never a literal.
  s3_bucket_arn  = "arn:${local.partition}:s3:::${local.prefix}-*"
  s3_object_arn  = "arn:${local.partition}:s3:::${local.prefix}-*/*"
  dynamodb_arn   = "arn:${local.partition}:dynamodb:${var.aws_region}:${local.account_id}:table/${local.prefix}-*"
  ecr_arn        = "arn:${local.partition}:ecr:${var.aws_region}:${local.account_id}:repository/${local.prefix}-*"
  apprunner_arn  = "arn:${local.partition}:apprunner:${var.aws_region}:${local.account_id}:service/${local.prefix}-*/*"
  secrets_arn    = "arn:${local.partition}:secretsmanager:${var.aws_region}:${local.account_id}:secret:${local.prefix}-*"
  iam_role_arn   = "arn:${local.partition}:iam::${local.account_id}:role/${local.prefix}-*"
  iam_policy_arn = "arn:${local.partition}:iam::${local.account_id}:policy/${local.prefix}-*"
  iam_user_arn   = "arn:${local.partition}:iam::${local.account_id}:user/${local.prefix}-terraform"
}
