# App Runner service running the Ensemble container. `instance_role_arn` and
# `access_role_arn` reference the two roles terraform/deploy/iam.tf (Task 4.0)
# declares -- the instance role the running container assumes, and the
# access role App Runner's build system assumes to pull the private ECR image.

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

    authentication_configuration {
      access_role_arn = aws_iam_role.ecr_access.arn
    }

    image_repository {
      image_repository_type = "ECR"
      # IMMUTABLE tags (ecr.tf) can never be overwritten, so :latest is pushed
      # exactly once as the bootstrap seed image (Task 6.1, before any git-SHA
      # tag exists). Every deploy after that repoints this service at a new
      # sha-<git-sha> tag via `aws apprunner update-service` (Task 5.0) -- never
      # a second push to :latest.
      #
      # After that one-time seed CI owns this tag, so the `lifecycle` block below
      # tells Terraform to ignore drift on it. Without that guard a later
      # `terraform apply` (which is operator-run, decoupled from CI) would revert
      # the live service to the stale :latest seed, rolling back CI-deployed code.
      image_identifier = "${aws_ecr_repository.app.repository_url}:latest"

      image_configuration {
        port = "8080"

        # Non-secret runtime config -- literals only, matching the app's
        # existing ensemble.* / ENSEMBLE_* keys (Unit 1). No code change to how
        # these are read. The cloud Spring profile (application-cloud.yml)
        # blanks the DynamoDB endpoint + disables table auto-create; it exists
        # because App Runner silently DROPS empty-string env values, so the
        # earlier ENSEMBLE_DYNAMODB_ENDPOINT = "" never reached the container
        # and the app dialed DynamoDB Local in the cloud (task 6.1 crash).
        runtime_environment_variables = {
          SPRING_PROFILES_ACTIVE       = "cloud"
          ENSEMBLE_PHOTOS_BACKEND      = "s3"
          ENSEMBLE_PHOTOS_S3_BUCKET    = aws_s3_bucket.photos.bucket
          ENSEMBLE_DYNAMODB_TABLE_NAME = aws_dynamodb_table.items.name
          ENSEMBLE_OUTFITS_TABLE_NAME  = aws_dynamodb_table.outfits.name
          ENSEMBLE_USERS_TABLE_NAME    = aws_dynamodb_table.users.name

          # One-time unowned-data purge (spec #15), off by default. Flip
          # var.purge_unowned_data to true for a single deploy to clear legacy
          # pre-ownership rows, then set it back. Emitted as an explicit
          # "true"/"false" literal because App Runner drops empty-string values.
          ENSEMBLE_MIGRATION_PURGE_UNOWNED = var.purge_unowned_data ? "true" : "false"
        }

        # Sourced by ARN, never by value -- App Runner resolves these via the
        # instance role's secretsmanager:GetSecretValue (Task 4.0). The
        # seed-account pair is merged in only when var.seed_account_enabled is true;
        # otherwise it is omitted entirely, so the revision never references an empty
        # container (an unresolvable secret fails the deployment and rolls it back).
        # Invite-only signup (POST /api/accounts) does not depend on seeding.
        runtime_environment_secrets = merge(
          {
            ENSEMBLE_ANTHROPIC_API_KEY = aws_secretsmanager_secret.anthropic_key.arn
            ENSEMBLE_PASSCODE          = aws_secretsmanager_secret.passcode.arn
            ENSEMBLE_SESSION_SECRET    = aws_secretsmanager_secret.session_secret.arn
          },
          var.seed_account_enabled ? {
            ENSEMBLE_SEED_USERNAME = aws_secretsmanager_secret.seed_username[0].arn
            ENSEMBLE_SEED_PASSWORD = aws_secretsmanager_secret.seed_password[0].arn
          } : {}
        )
      }
    }
  }

  instance_configuration {
    # Smallest combination that comfortably runs a JVM (Spring Boot) workload;
    # documented here as the one place to retune (spec Open Question 2).
    cpu    = "1024"
    memory = "2048"

    instance_role_arn = aws_iam_role.instance.arn
  }

  health_check_configuration {
    protocol = "HTTP"
    path     = "/api/health"
  }

  auto_scaling_configuration_arn = aws_apprunner_auto_scaling_configuration_version.app.arn

  # CI owns the image tag after the one-time :latest seed (it repoints the
  # service at each sha-<git-sha> via `aws apprunner update-service`). Ignore
  # drift on that one attribute so operator-run `terraform apply` keeps the
  # CI-deployed SHA and only applies the real diff -- Terraform owns everything
  # else on this resource.
  lifecycle {
    ignore_changes = [source_configuration[0].image_repository[0].image_identifier]
  }
}
