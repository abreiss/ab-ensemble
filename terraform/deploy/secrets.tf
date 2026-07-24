# Secrets Manager secret *containers* only (Resolved Decision Q4). No
# aws_secretsmanager_secret_version resource is declared for any of these --
# secret_string lives on that resource, not on aws_secretsmanager_secret, so
# omitting it entirely is what keeps plaintext out of Terraform state. Values
# are populated out-of-band by the operator (Task 6.2); App Runner (3.3) reads
# them at runtime by ARN via runtime_environment_secrets.

resource "aws_secretsmanager_secret" "anthropic_key" {
  name        = "${local.prefix}-anthropic-key"
  description = "Claude API key for Ensemble's vision tagging + stylist calls. Terraform manages the container only; the value is set out-of-band."
}

resource "aws_secretsmanager_secret" "passcode" {
  name        = "${local.prefix}-passcode"
  description = "Demo passcode gating /api/**. Terraform manages the container only; the value is set out-of-band."
}

resource "aws_secretsmanager_secret" "session_secret" {
  name        = "${local.prefix}-session-secret"
  description = "HMAC key for signing session tokens. Terraform manages the container only; the value is set out-of-band."
}

# Seed-account secrets are provisioned only when var.seed_account_enabled is true
# (opt-in, default off). Omitted otherwise so the service's
# runtime_environment_secrets never references an empty container -- an
# unresolvable secret fails the App Runner revision and rolls the service back.
# See apprunner.tf and variables.tf.
resource "aws_secretsmanager_secret" "seed_email" {
  count       = var.seed_account_enabled ? 1 : 0
  name        = "${local.prefix}-seed-email"
  description = "Email for the idempotent startup-seeded account (issue #14). Terraform manages the container only; the value is set out-of-band."
}

resource "aws_secretsmanager_secret" "seed_password" {
  count       = var.seed_account_enabled ? 1 : 0
  name        = "${local.prefix}-seed-password"
  description = "Password for the idempotent startup-seeded account (issue #14). Terraform manages the container only; the value is set out-of-band."
}
