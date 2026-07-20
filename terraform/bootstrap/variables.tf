# The two knobs for the bootstrap. Region is the only value expected to change
# between environments; the prefix is defined once here and reused everywhere so
# the `abreiss-ensemble-*` scoping can never drift between statements.

variable "aws_region" {
  description = "AWS region for regional resources (DynamoDB, ECR, App Runner, Secrets Manager, Logs). Matches application.yml; IAM and S3-bucket ARNs are partition/global. Changing region is a one-variable edit."
  type        = string
  default     = "us-east-1"
}

variable "resource_prefix" {
  description = "Name prefix that scopes every resource this identity may touch. All Ensemble infra must be named <prefix>-* (e.g. abreiss-ensemble-photos). Defined once so all policy statements share one source of truth."
  type        = string
  default     = "abreiss-ensemble"
}
