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
