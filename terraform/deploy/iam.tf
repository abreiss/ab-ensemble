# The two runtime roles this module's resources need (App Runner instance +
# ECR image pull) plus the GitHub OIDC CI role, all created WITH
# permissions_boundary = local.boundary_policy_arn (the abreiss-ensemble-boundary
# policy from terraform/bootstrap) -- the abreiss-ensemble-terraform identity's
# IamCreateRoleWithBoundaryOnly condition denies iam:CreateRole otherwise. See
# docs/AWS_ACCESS.md for the full boundary narrative; this file only consumes it.

# -----------------------------------------------------------------------------
# App Runner instance role -- what the RUNNING APP is allowed to touch.
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "instance_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["tasks.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name                 = "${local.prefix}-instance"
  assume_role_policy   = data.aws_iam_policy_document.instance_trust.json
  permissions_boundary = local.boundary_policy_arn
}

data "aws_iam_policy_document" "instance_permissions" {
  # No s3:ListBucket -- S3PhotoStorage (Unit 1) only ever calls
  # PutObject/GetObject/DeleteObject by key, never ListObjects.
  statement {
    sid    = "RuntimeS3Object"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = [local.s3_object_arn]
  }

  statement {
    sid    = "RuntimeDynamoDb"
    effect = "Allow"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Query",
      "dynamodb:Scan",
    ]
    resources = [local.dynamodb_arn]
  }

  statement {
    sid       = "RuntimeSecrets"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [local.secrets_arn]
  }
}

resource "aws_iam_policy" "instance" {
  name        = "${local.prefix}-instance-runtime"
  description = "Least-privilege runtime access for the App Runner instance role: the photos bucket, the items table, and the three secret ARNs."
  policy      = data.aws_iam_policy_document.instance_permissions.json
}

resource "aws_iam_role_policy_attachment" "instance" {
  role       = aws_iam_role.instance.name
  policy_arn = aws_iam_policy.instance.arn
}

# -----------------------------------------------------------------------------
# ECR access role -- lets App Runner PULL the private image at deploy time.
# Distinct from the instance role (which is assumed by the running container,
# principal tasks.apprunner.amazonaws.com); this one is assumed by the App
# Runner build/pull system itself (principal build.apprunner.amazonaws.com) and
# is wired into apprunner.tf's authentication_configuration.access_role_arn.
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "ecr_access_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["build.apprunner.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecr_access" {
  name                 = "${local.prefix}-ecr-access"
  assume_role_policy   = data.aws_iam_policy_document.ecr_access_trust.json
  permissions_boundary = local.boundary_policy_arn
}

data "aws_iam_policy_document" "ecr_access_permissions" {
  statement {
    sid    = "EcrPullScoped"
    effect = "Allow"
    actions = [
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
    ]
    resources = [local.ecr_arn]
  }

  statement {
    sid       = "EcrAuthToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"] # account-level docker-login token, no ARN
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ecr_access" {
  name        = "${local.prefix}-ecr-access"
  description = "Scoped ECR image-pull access for App Runner's authentication_configuration.access_role_arn."
  policy      = data.aws_iam_policy_document.ecr_access_permissions.json
}

resource "aws_iam_role_policy_attachment" "ecr_access" {
  role       = aws_iam_role.ecr_access.name
  policy_arn = aws_iam_policy.ecr_access.arn
}

# -----------------------------------------------------------------------------
# GitHub OIDC CI role -- what the DEPLOY WORKFLOW (Task 5.0) is allowed to do.
# Federates the account's pre-existing GitHub OIDC provider (this identity may
# only read it -- bootstrap's DenyOidcProviderMutation/IamOidcReadOnly), never
# creates or mutates the provider itself.
# -----------------------------------------------------------------------------
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "ci_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # StringLike (not StringEquals) so var.github_ref can express a
    # branch/tag/PR pattern; the default pins to exactly refs/heads/main.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:${var.github_ref}"]
    }
  }
}

resource "aws_iam_role" "ci" {
  name                 = "${local.prefix}-ci"
  assume_role_policy   = data.aws_iam_policy_document.ci_trust.json
  permissions_boundary = local.boundary_policy_arn
}

data "aws_iam_policy_document" "ci_permissions" {
  # Matches bootstrap policies.tf's CiEcrScoped exactly -- the #16 boundary
  # already caps this identity to these actions on the ecr_arn, so mirroring
  # the list here (rather than widening) keeps the CI role's own policy a
  # true subset of what it could ever be granted.
  statement {
    sid    = "CiEcrScoped"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeRepositories",
      "ecr:DescribeImages",
    ]
    resources = [local.ecr_arn]
  }

  statement {
    sid       = "CiEcrAuthToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"] # account-level docker-login token, no ARN
    resources = ["*"]
  }

  statement {
    sid    = "CiAppRunnerScoped"
    effect = "Allow"
    actions = [
      "apprunner:StartDeployment",
      "apprunner:DescribeService",
      "apprunner:UpdateService",
    ]
    resources = [local.apprunner_arn]
  }

  statement {
    sid       = "CiPassRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.iam_role_arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = local.apprunner_pass_principals
    }
  }
}

resource "aws_iam_policy" "ci" {
  name        = "${local.prefix}-ci"
  description = "Exactly what #16 pre-authorized for the CI role: ECR push + App Runner deploy + one scoped PassRole. No widening."
  policy      = data.aws_iam_policy_document.ci_permissions.json
}

resource "aws_iam_role_policy_attachment" "ci" {
  role       = aws_iam_role.ci.name
  policy_arn = aws_iam_policy.ci.arn
}
