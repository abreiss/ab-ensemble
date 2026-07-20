# Outputs for the deploy-module apply. None of these are sensitive -- they are
# names/URLs/ARNs, never secret values or a literal account id.

output "app_runner_service_url" {
  description = "Public HTTPS URL the App Runner service serves the PWA on (Task 6.3/6.4's golden-path URL)."
  value       = aws_apprunner_service.app.service_url
}

output "ecr_repository_url" {
  description = "ECR repository URL the deploy workflow (Task 5.0) pushes git-SHA-tagged images to."
  value       = aws_ecr_repository.app.repository_url
}

output "app_runner_service_arn" {
  description = "ARN of the App Runner service -- the deploy workflow (Task 5.0) passes this to `aws apprunner update-service`/`describe-service` to repoint the service at each new git-SHA image tag and poll rollout status."
  value       = aws_apprunner_service.app.arn
}

output "dynamodb_table_name" {
  description = "Name of the DynamoDB items table, for ENSEMBLE_DYNAMODB_TABLE_NAME / operator verification."
  value       = aws_dynamodb_table.items.name
}

output "s3_photos_bucket_name" {
  description = "Name of the S3 photos bucket, for ENSEMBLE_PHOTOS_S3_BUCKET / operator verification."
  value       = aws_s3_bucket.photos.bucket
}

output "ci_role_arn" {
  description = "ARN of the GitHub OIDC CI role -- goes into the deploy workflow's role-to-assume (Task 5.0)."
  value       = aws_iam_role.ci.arn
}
