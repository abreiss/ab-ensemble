# App Runner service running the Ensemble container. `instance_role_arn`
# (instance_configuration) and `access_role_arn` (authentication_configuration)
# are added once terraform/deploy/iam.tf (Task 4.0) declares those roles --
# both are Optional in the AWS provider schema, so this file `fmt`/`validate`s
# cleanly on its own; see the Task 3.0 cross-task dependency note in
# 09-tasks-deploy-pipeline.md. `plan`/`apply` need both tasks present.

resource "aws_apprunner_auto_scaling_configuration_version" "app" {
  auto_scaling_configuration_name = "${local.prefix}-app"

  # Demo-scale sizing (spec Open Question 2): smallest viable ceiling, tunable
  # in this one place, not tuned for production load.
  min_size = 1
  max_size = 2
}

resource "aws_apprunner_service" "app" {
  service_name = "${local.prefix}-app"

  source_configuration {
    # CI (Task 5.0) drives deploys explicitly via apprunner:UpdateService +
    # StartDeployment after each ECR push; App Runner must not auto-redeploy
    # on a schedule/poll of its own.
    auto_deployments_enabled = false

    image_repository {
      image_repository_type = "ECR"
      # IMMUTABLE tags (ecr.tf) can never be overwritten, so :latest is pushed
      # exactly once as the bootstrap seed image (Task 6.1, before any git-SHA
      # tag exists). Every deploy after that repoints this service at a new
      # sha-<git-sha> tag via `aws apprunner update-service` (Task 5.0) -- never
      # a second push to :latest.
      image_identifier = "${aws_ecr_repository.app.repository_url}:latest"

      image_configuration {
        port = "8080"

        # Non-secret runtime config -- literals only, matching the app's
        # existing ensemble.* / ENSEMBLE_* keys (Unit 1). No code change to how
        # these are read.
        runtime_environment_variables = {
          ENSEMBLE_PHOTOS_BACKEND      = "s3"
          ENSEMBLE_PHOTOS_S3_BUCKET    = aws_s3_bucket.photos.bucket
          ENSEMBLE_DYNAMODB_ENDPOINT   = ""
          ENSEMBLE_DYNAMODB_TABLE_NAME = aws_dynamodb_table.items.name
        }

        # Sourced by ARN, never by value -- App Runner resolves these via the
        # instance role's secretsmanager:GetSecretValue (Task 4.0).
        runtime_environment_secrets = {
          ENSEMBLE_ANTHROPIC_API_KEY = aws_secretsmanager_secret.anthropic_key.arn
          ENSEMBLE_PASSCODE          = aws_secretsmanager_secret.passcode.arn
          ENSEMBLE_SESSION_SECRET    = aws_secretsmanager_secret.session_secret.arn
        }
      }
    }
  }

  instance_configuration {
    # Smallest combination that comfortably runs a JVM (Spring Boot) workload;
    # documented here as the one place to retune (spec Open Question 2).
    cpu    = "1024"
    memory = "2048"
  }

  health_check_configuration {
    protocol = "HTTP"
    path     = "/api/health"
  }

  auto_scaling_configuration_arn = aws_apprunner_auto_scaling_configuration_version.app.arn
}
