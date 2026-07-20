#!/usr/bin/env bash
#
# create-state-bucket.sh — one-time bootstrap of the abreiss-ensemble-tfstate
# remote-state bucket, reproducible by the scoped abreiss-ensemble-terraform
# identity (its policy already allows s3:CreateBucket/PutBucket* on any
# abreiss-ensemble-* bucket — see terraform/bootstrap/policies.tf). This is the
# only resource this module cannot create via `terraform apply` itself, since
# Terraform needs the bucket to exist before it can initialize the S3 backend.
#
# Usage:
#   AWS_PROFILE=abreiss-ensemble-terraform ./create-state-bucket.sh
#
# Env overrides: BUCKET, REGION, AWS_PROFILE
set -euo pipefail

BUCKET="${BUCKET:-abreiss-ensemble-tfstate}"
REGION="${REGION:-us-east-1}"

echo "Creating state bucket: s3://${BUCKET} (region ${REGION})"

if [ "${REGION}" = "us-east-1" ]; then
  aws s3api create-bucket --bucket "${BUCKET}" --region "${REGION}"
else
  aws s3api create-bucket --bucket "${BUCKET}" --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}"
fi

aws s3api put-bucket-versioning --bucket "${BUCKET}" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption --bucket "${BUCKET}" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block --bucket "${BUCKET}" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

echo "Done. Next: terraform -chdir=terraform/deploy init"
