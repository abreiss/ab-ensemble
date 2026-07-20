# Version pins + remote state backend for the deploy module. required_version is
# bumped past bootstrap's ">= 1.6.0" floor because S3-native locking (use_lockfile)
# needs Terraform >= 1.11.
terraform {
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state lives in its own abreiss-ensemble-tfstate bucket (created once via
  # the state-bucket bootstrap in README.md), locked natively by S3 (use_lockfile)
  # instead of a DynamoDB lock table. Backend blocks cannot reference variables, so
  # bucket/key/region are literals here, not var.resource_prefix / var.aws_region.
  backend "s3" {
    bucket       = "abreiss-ensemble-tfstate"
    key          = "deploy/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
  }
}
