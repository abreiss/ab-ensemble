# =============================================================================
# The two managed policies at the heart of this feature.
#
#   * data.aws_iam_policy_document.terraform_scoped  -> aws_iam_policy.terraform_scoped
#       What Claude/Terraform may DO: create/read/update/delete on abreiss-ensemble-*
#       resources only, IAM roles only when they carry the boundary, plus explicit
#       anti-escalation denies.
#
#   * data.aws_iam_policy_document.boundary          -> aws_iam_policy.boundary
#       The CEILING for every role the scoped identity creates: the union of runtime
#       permissions those roles may need, all capped to abreiss-ensemble-*, and an
#       explicit deny on any permissions-boundary tampering.
#
# Isolation is enforced first by the abreiss-ensemble-* ARN prefix on every
# resource-scoped statement; aws:RequestedRegion / aws:ResourceAccount are
# defense-in-depth. The handful of AWS actions that cannot be ARN-scoped are
# isolated into their own Resource:"*" statements, each justified inline.
# =============================================================================

# -----------------------------------------------------------------------------
# Scoped permissions policy (abreiss-ensemble-terraform)
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "terraform_scoped" {

  # === 2.1 Resource-scoped Allow — S3 =========================================
  # Bucket-level provisioning of the photos bucket (any abreiss-ensemble-* bucket).
  # s3:GetBucket*/PutBucket* are deliberately wildcarded over the bucket's config
  # sub-resources (policy, versioning, public-access-block, CORS, tagging,
  # ownership-controls, ...): they stay fully contained to the abreiss-ensemble-*
  # bucket ARN and keep the policy under IAM's 6,144-char managed-policy limit.
  # S3 ARNs are partition-global, so there is no aws:RequestedRegion; aws:ResourceAccount
  # pins every bucket to this account as the cross-account guard.
  statement {
    sid    = "S3BucketScoped"
    effect = "Allow"
    actions = [
      "s3:CreateBucket",
      "s3:DeleteBucket",
      "s3:DeleteBucketPolicy",
      "s3:ListBucket",
      "s3:GetBucket*",
      "s3:PutBucket*",
      "s3:GetEncryptionConfiguration",
      "s3:PutEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:PutLifecycleConfiguration",
    ]
    resources = [local.s3_bucket_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # Object-level access for the photos stored under the bucket.
  statement {
    sid    = "S3ObjectScoped"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:GetObjectTagging",
      "s3:PutObjectTagging",
    ]
    resources = [local.s3_object_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # === 2.1 Resource-scoped Allow — DynamoDB ===================================
  statement {
    sid    = "DynamoDbScoped"
    effect = "Allow"
    actions = [
      "dynamodb:CreateTable",
      "dynamodb:DeleteTable",
      "dynamodb:DescribeTable",
      "dynamodb:UpdateTable",
      "dynamodb:DescribeTimeToLive",
      "dynamodb:UpdateTimeToLive",
      "dynamodb:DescribeContinuousBackups",
      "dynamodb:UpdateContinuousBackups",
      "dynamodb:TagResource",
      "dynamodb:UntagResource",
      "dynamodb:ListTagsOfResource",
    ]
    resources = [local.dynamodb_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # === 2.1 Resource-scoped Allow — ECR ========================================
  statement {
    sid    = "EcrRepositoryScoped"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository",
      "ecr:DeleteRepository",
      "ecr:DescribeRepositories",
      "ecr:ListTagsForResource",
      "ecr:TagResource",
      "ecr:UntagResource",
      "ecr:GetRepositoryPolicy",
      "ecr:SetRepositoryPolicy",
      "ecr:DeleteRepositoryPolicy",
      "ecr:GetLifecyclePolicy",
      "ecr:PutLifecyclePolicy",
      "ecr:DeleteLifecyclePolicy",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutImageTagMutability",
    ]
    resources = [local.ecr_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # === 2.1 Resource-scoped Allow — App Runner (read/update/delete) ============
  # Create actions cannot be ARN-scoped and live in the Resource:"*" statement below.
  statement {
    sid    = "AppRunnerServiceScoped"
    effect = "Allow"
    actions = [
      "apprunner:DescribeService",
      "apprunner:UpdateService",
      "apprunner:DeleteService",
      "apprunner:StartDeployment",
      "apprunner:ListOperations",
      "apprunner:DescribeOperation",
      "apprunner:ListTagsForResource",
      "apprunner:TagResource",
      "apprunner:UntagResource",
    ]
    resources = [local.apprunner_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  # === 2.1 Resource-scoped Allow — Secrets Manager ============================
  # The abreiss-ensemble-* prefix covers Secrets Manager's random 6-char ARN suffix.
  statement {
    sid    = "SecretsManagerScoped"
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret",
      "secretsmanager:DeleteSecret",
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:PutSecretValue",
      "secretsmanager:UpdateSecret",
      "secretsmanager:TagResource",
      "secretsmanager:UntagResource",
      "secretsmanager:ListSecretVersionIds",
    ]
    resources = [local.secrets_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # === 2.2 Enumerated account-level Allow (Resource:"*") — GLOBAL =============
  # AWS does not accept a resource ARN for these; each lists names/tokens/identity
  # only and cannot read or mutate the CONTENTS of any resource. Split from the
  # regional block below because aws:RequestedRegion cannot guard truly-global calls.
  statement {
    sid    = "AccountLevelGlobalActions"
    effect = "Allow"
    actions = [
      "s3:ListAllMyBuckets",   # list bucket NAMES only; no access to any bucket's contents
      "sts:GetCallerIdentity", # "who am I" preflight; no resource, no data exposure
    ]
    resources = ["*"]
  }

  # === 2.2 Enumerated account-level Allow (Resource:"*") — REGIONAL ===========
  # Same rule (non-scopable, name/token-only), but regional, so each call is pinned
  # to var.aws_region as defense-in-depth.
  statement {
    sid    = "AccountLevelRegionalActions"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",                  # docker-login token; account-level, no ARN
      "dynamodb:ListTables",                        # list table NAMES only
      "secretsmanager:ListSecrets",                 # list secret metadata (name/ARN) only
      "apprunner:ListServices",                     # list service summaries only
      "apprunner:ListAutoScalingConfigurations",    # list config summaries only
      "apprunner:ListObservabilityConfigurations",  # list config summaries only
      "apprunner:CreateService",                    # create-then-name; AWS accepts no ARN at create
      "apprunner:CreateAutoScalingConfiguration",   # create-then-name; AWS accepts no ARN at create
      "apprunner:CreateObservabilityConfiguration", # create-then-name; AWS accepts no ARN at create
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [var.aws_region]
    }
  }

  # === 2.3 IAM — CreateRole only with the boundary + an abreiss-ensemble-* name =
  # The self-referencing boundary pattern: a role can be created ONLY when its
  # permissions boundary is exactly abreiss-ensemble-boundary. The role-name prefix
  # is enforced by the resource ARN (role/abreiss-ensemble-*).
  statement {
    sid       = "IamCreateRoleWithBoundaryOnly"
    effect    = "Allow"
    actions   = ["iam:CreateRole"]
    resources = [local.iam_role_arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [local.boundary_policy_arn]
    }
  }

  # === 2.3 IAM — role management, scoped to abreiss-ensemble-* roles ===========
  statement {
    sid    = "IamRoleManagementScoped"
    effect = "Allow"
    actions = [
      "iam:GetRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListRoleTags",
      "iam:PutRolePermissionsBoundary",
    ]
    resources = [local.iam_role_arn]
  }

  # === 2.3 IAM — customer-managed policy management, scoped to abreiss-ensemble-* =
  # This intentionally matches the boundary and scoped policies too; the explicit
  # denies below claw those two ARNs back so the identity cannot edit its own
  # permissions or weaken the boundary (Deny beats Allow).
  statement {
    sid    = "IamPolicyManagementScoped"
    effect = "Allow"
    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListPolicyVersions",
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:SetDefaultPolicyVersion",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = [local.iam_policy_arn]
  }

  # === 2.3 IAM — PassRole limited to abreiss-ensemble-* roles, App Runner only ==
  statement {
    sid       = "IamPassRoleToAppRunnerOnly"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.iam_role_arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = local.apprunner_pass_principals
    }
  }

  # === 2.3 IAM — OIDC provider is read-only (created/owned by the account admin) =
  # #9 attaches a CI role's trust policy to the pre-existing provider; it never
  # creates, updates, or deletes it (see the explicit deny below).
  statement {
    sid    = "IamOidcReadOnly"
    effect = "Allow"
    actions = [
      "iam:GetOpenIDConnectProvider",
      "iam:ListOpenIDConnectProviders",
    ]
    resources = ["*"]
  }

  # === 2.4 Anti-escalation Denies (Deny beats every Allow) ====================
  # Never remove a role's boundary, and never swap it for anything but the correct ARN.
  statement {
    sid       = "DenyRemovingPermissionsBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["*"]
  }
  statement {
    sid       = "DenyWeakeningPermissionsBoundary"
    effect    = "Deny"
    actions   = ["iam:PutRolePermissionsBoundary"]
    resources = ["*"]
    condition {
      test     = "StringNotEquals"
      variable = "iam:PermissionsBoundary"
      values   = [local.boundary_policy_arn]
    }
  }

  # Never modify the boundary policy document itself.
  statement {
    sid    = "DenyBoundaryPolicyModification"
    effect = "Deny"
    actions = [
      "iam:CreatePolicyVersion",
      "iam:DeletePolicyVersion",
      "iam:SetDefaultPolicyVersion",
      "iam:DeletePolicy",
      "iam:TagPolicy",
      "iam:UntagPolicy",
    ]
    resources = [local.boundary_policy_arn]
  }

  # Never modify the runner user or its own scoped policy (no self-escalation).
  statement {
    sid       = "DenySelfModification"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = [local.iam_user_arn, local.scoped_policy_arn]
  }

  # Never create/alter/delete the account's OIDC provider (Non-Goal 3).
  statement {
    sid    = "DenyOidcProviderMutation"
    effect = "Deny"
    actions = [
      "iam:CreateOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:UpdateOpenIDConnectProviderThumbprint",
      "iam:AddClientIDToOpenIDConnectProvider",
      "iam:RemoveClientIDFromOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
      "iam:UntagOpenIDConnectProvider",
    ]
    resources = ["*"]
  }
}

# -----------------------------------------------------------------------------
# 2.5 Permissions boundary (abreiss-ensemble-boundary)
# The ceiling for every role the scoped identity creates. A role's effective
# permissions are the intersection of its own policy AND this boundary, and an
# explicit Deny here denies the action for any role carrying the boundary.
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "boundary" {

  # --- App Runner instance role: runtime data access ---
  # Bucket-level and object-level are separate statements so each action targets
  # only the ARN it applies to (ListBucket -> bucket, GetObject -> object). Keeping
  # them apart also avoids Access Analyzer's REDUNDANT_RESOURCE finding, since the
  # abreiss-ensemble-* bucket pattern already matches object paths.
  statement {
    sid    = "RuntimeS3Bucket"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation",
    ]
    resources = [local.s3_bucket_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }
  statement {
    sid    = "RuntimeS3Object"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = [local.s3_object_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
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
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:DescribeTable",
    ]
    resources = [local.dynamodb_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }
  statement {
    sid    = "RuntimeSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [local.secrets_arn]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [local.account_id]
    }
  }

  # --- CI/deploy role: push images + trigger deploys ---
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
    sid       = "CiAppRunnerList"
    effect    = "Allow"
    actions   = ["apprunner:ListServices"] # account-level list, no ARN
    resources = ["*"]
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

  # --- Boundary self-protection: a role carrying this boundary can never remove,
  # weaken, or edit any permissions boundary, nor touch the boundary policy itself. ---
  statement {
    sid    = "DenyAnyPermissionsBoundaryAction"
    effect = "Deny"
    actions = [
      "iam:PutRolePermissionsBoundary",
      "iam:DeleteRolePermissionsBoundary",
      "iam:PutUserPermissionsBoundary",
      "iam:DeleteUserPermissionsBoundary",
    ]
    resources = ["*"]
  }
  statement {
    sid       = "DenyBoundaryPolicySelfModification"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = [local.boundary_policy_arn]
  }
}

# -----------------------------------------------------------------------------
# 2.6 Managed policy resources
# -----------------------------------------------------------------------------
resource "aws_iam_policy" "boundary" {
  name        = "${var.resource_prefix}-boundary"
  description = "Permissions boundary capping every role the ${var.resource_prefix}-terraform identity creates to the ${var.resource_prefix}-* box."
  policy      = data.aws_iam_policy_document.boundary.json
}

resource "aws_iam_policy" "terraform_scoped" {
  name        = "${var.resource_prefix}-terraform"
  description = "Scoped permissions for the ${var.resource_prefix}-terraform identity: CRUD on ${var.resource_prefix}-* resources only; roles must carry the ${var.resource_prefix}-boundary."
  policy      = data.aws_iam_policy_document.terraform_scoped.json

  # The scoped policy's CreateRole condition names the boundary ARN, so the boundary
  # must exist first. (The ARN is computed as a local for renderability; this edge
  # keeps apply-time ordering correct.)
  depends_on = [aws_iam_policy.boundary]
}
