# ECR repository for the app image. Tags are immutable so a git-SHA tag can
# never be silently overwritten (traceability + safe rollback, see
# docs/ARCHITECTURE.md "Deployment"); the lifecycle policy keeps the repo from
# growing unbounded as CI (Task 5.0) pushes a new sha-<git-sha> tag per deploy.

resource "aws_ecr_repository" "app" {
  name                 = "${local.prefix}-app"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images (failed/orphaned pushes) after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep only the 10 most recent sha-tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["sha-"]
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
