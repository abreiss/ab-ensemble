# Version pins for the one-time bootstrap module. Pinning both the Terraform
# core floor and the AWS provider keeps the rendered policy JSON stable across
# machines, so the committed policies/*.json review copies stay reproducible.
terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
