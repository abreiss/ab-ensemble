#!/usr/bin/env bash
#
# simulate-scoping.sh — prove the abreiss-ensemble-terraform scoping with the IAM
# Policy Simulator. For every resource type #9 provisions (S3, DynamoDB, ECR, App
# Runner, Secrets Manager) plus IAM, it checks that an action is ALLOWED on an
# `abreiss-ensemble-*` ARN and DENIED on the same action against a non-prefixed ARN.
#
# Two modes (read-only either way — the simulator mutates nothing):
#
#   custom     (default) — evaluate the committed rendered scoped policy JSON with
#                          `aws iam simulate-custom-policy`. Works WITHOUT the
#                          bootstrap having been applied; account id is the redacted
#                          placeholder in the JSON (123456789012). Proves the policy
#                          LOGIC scopes correctly.
#
#   principal            — evaluate the LIVE attached policy of the applied user with
#                          `aws iam simulate-principal-policy`. Requires Task 3.0's
#                          apply to have run and PRINCIPAL_ARN to be set (or derived
#                          from the abreiss-ensemble-terraform profile). Proves the
#                          scoping against what AWS actually stored.
#
# Usage:
#   ./simulate-scoping.sh                      # custom mode, writes JSON + table to proof/
#   MODE=principal PRINCIPAL_ARN=arn:aws:iam::<acct>:user/abreiss-ensemble-terraform \
#     PROFILE=abreiss-ensemble-terraform ./simulate-scoping.sh
#
# Env overrides: MODE, ACCOUNT, REGION, POLICY_FILE, PRINCIPAL_ARN, PROFILE, OUT_DIR
#
# Output (account id redacted in the committed copies):
#   <OUT_DIR>/simulate-matrix.json   — one record per check (service/action/arn/decision/expected/pass)
#   <OUT_DIR>/simulate-matrix.txt    — human-readable PASS/FAIL table
set -euo pipefail

MODE="${MODE:-custom}"
REGION="${REGION:-us-east-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_FILE="${POLICY_FILE:-$SCRIPT_DIR/policies/abreiss-ensemble-terraform.json}"
OUT_DIR="${OUT_DIR:-$SCRIPT_DIR/../../docs/specs/16-spec-scoped-iam-identity/proof}"
PROFILE="${PROFILE:-}"

aws_cli() {
  if [[ -n "$PROFILE" ]]; then aws --profile "$PROFILE" "$@"; else aws "$@"; fi
}

# Account id: placeholder for custom mode (matches the committed JSON), live for principal.
if [[ "$MODE" == "principal" ]]; then
  ACCOUNT="${ACCOUNT:-$(aws_cli sts get-caller-identity --query Account --output text)}"
  PRINCIPAL_ARN="${PRINCIPAL_ARN:-arn:aws:iam::${ACCOUNT}:user/abreiss-ensemble-terraform}"
else
  ACCOUNT="${ACCOUNT:-123456789012}"
fi

# --- Test matrix: service | action | allowed (prefixed) ARN | denied (non-prefixed) ARN --------
# Each action exists in the scoped policy; the allowed ARN is inside abreiss-ensemble-*,
# the denied ARN is an unrelated resource of the same type.
MATRIX=(
  "S3|s3:PutObject|arn:aws:s3:::abreiss-ensemble-photos/demo.jpg|arn:aws:s3:::someone-elses-bucket/demo.jpg"
  "DynamoDB|dynamodb:CreateTable|arn:aws:dynamodb:${REGION}:${ACCOUNT}:table/abreiss-ensemble-items|arn:aws:dynamodb:${REGION}:${ACCOUNT}:table/prod-billing"
  "ECR|ecr:CreateRepository|arn:aws:ecr:${REGION}:${ACCOUNT}:repository/abreiss-ensemble-app|arn:aws:ecr:${REGION}:${ACCOUNT}:repository/other-team-app"
  "AppRunner|apprunner:UpdateService|arn:aws:apprunner:${REGION}:${ACCOUNT}:service/abreiss-ensemble-web/abc123|arn:aws:apprunner:${REGION}:${ACCOUNT}:service/prod-payments/def456"
  "SecretsManager|secretsmanager:GetSecretValue|arn:aws:secretsmanager:${REGION}:${ACCOUNT}:secret:abreiss-ensemble-anthropic-key|arn:aws:secretsmanager:${REGION}:${ACCOUNT}:secret:prod-db-password"
  "IAM|iam:DeleteRole|arn:aws:iam::${ACCOUNT}:role/abreiss-ensemble-apprunner|arn:aws:iam::${ACCOUNT}:role/OrganizationAccountAccessRole"
)

# Context entries satisfy the aws:RequestedRegion / aws:ResourceAccount conditions so the
# ALLOWED cases are not denied for a missing condition. The DENIED cases still deny because
# the non-prefixed ARN never matches the policy's abreiss-ensemble-* Resource.
CONTEXT=(
  "ContextKeyName=aws:RequestedRegion,ContextKeyValues=${REGION},ContextKeyType=string"
  "ContextKeyName=aws:ResourceAccount,ContextKeyValues=${ACCOUNT},ContextKeyType=string"
)

# `simulate-custom-policy` caps each --policy-input-list member at 2000 chars, and the
# scoped policy is ~6 KB. IAM evaluates a list of policy documents cumulatively (exactly
# like multiple attached policies: an explicit Deny in ANY member still beats an Allow in
# another), so we pack the policy's statements into several <=1900-char documents that
# together preserve the full allow/deny semantics.
# bash 3.2-safe (macOS default): no `mapfile`, no empty-array expansion under set -u.
# `acc` is a newline-separated string of compact statement objects; `jq -s` slurps
# those lines back into a Statement array when wrapping each member document.
build_custom_members() {
  CUSTOM_MEMBERS=()
  local line acc="" cand doc
  wrap() { printf '%s\n' "$1" | jq -s -c '{Version:"2012-10-17",Statement:.}'; }
  while IFS= read -r line; do
    if [[ -z "$acc" ]]; then cand="$line"; else cand="$acc"$'\n'"$line"; fi
    doc="$(wrap "$cand")"
    if [[ ${#doc} -gt 1900 && -n "$acc" ]]; then
      CUSTOM_MEMBERS+=("$(wrap "$acc")")
      acc="$line"
    else
      acc="$cand"
    fi
  done < <(jq -c '.Statement[]' "$POLICY_FILE")
  [[ -n "$acc" ]] && CUSTOM_MEMBERS+=("$(wrap "$acc")")
}

simulate() {
  # $1 action, $2 resource arn -> prints EvalDecision
  local action="$1" arn="$2"
  if [[ "$MODE" == "principal" ]]; then
    aws_cli iam simulate-principal-policy \
      --policy-source-arn "$PRINCIPAL_ARN" \
      --action-names "$action" \
      --resource-arns "$arn" \
      --context-entries "${CONTEXT[@]}" \
      --query 'EvaluationResults[0].EvalDecision' --output text
  else
    aws_cli iam simulate-custom-policy \
      --policy-input-list "${CUSTOM_MEMBERS[@]}" \
      --action-names "$action" \
      --resource-arns "$arn" \
      --context-entries "${CONTEXT[@]}" \
      --query 'EvaluationResults[0].EvalDecision' --output text
  fi
}

[[ "$MODE" == "custom" ]] && build_custom_members

mkdir -p "$OUT_DIR"
JSON_OUT="$OUT_DIR/simulate-matrix.json"
TXT_OUT="$OUT_DIR/simulate-matrix.txt"

records=()
overall=0
printf '%-14s %-28s %-12s %-8s %s\n' "SERVICE" "ACTION" "EXPECT" "DECISION" "RESULT" >"$TXT_OUT"
printf '%-14s %-28s %-12s %-8s %s\n' "-------" "------" "------" "--------" "------" >>"$TXT_OUT"

for row in "${MATRIX[@]}"; do
  IFS='|' read -r svc action allow_arn deny_arn <<<"$row"

  for kind in allow deny; do
    if [[ "$kind" == "allow" ]]; then arn="$allow_arn"; expect="allowed"; else arn="$deny_arn"; expect="denied"; fi
    decision="$(simulate "$action" "$arn")"

    # Pass if allowed-case is "allowed", or denied-case is any *Deny.
    if { [[ "$expect" == "allowed" && "$decision" == "allowed" ]]; } || \
       { [[ "$expect" == "denied" && "$decision" != "allowed" ]]; }; then
      result="PASS"
    else
      result="FAIL"; overall=1
    fi

    printf '%-14s %-28s %-12s %-8s %s\n' "$svc" "$action" "$expect" "$decision" "$result" >>"$TXT_OUT"
    records+=("$(printf '{"service":"%s","action":"%s","resource":"%s","expected":"%s","decision":"%s","result":"%s"}' \
      "$svc" "$action" "$arn" "$expect" "$decision" "$result")")
  done
done

# Emit the JSON array (account id left as-is; committed copy is redacted separately).
{ echo '['; (IFS=,; echo "${records[*]}"); echo ']'; } | jq '.' >"$JSON_OUT"

echo
echo "Mode: $MODE   Account: $ACCOUNT   Region: $REGION"
cat "$TXT_OUT"
echo
if [[ "$overall" -eq 0 ]]; then
  echo "ALL CHECKS PASSED — every abreiss-ensemble-* action allowed, every non-prefixed action denied."
else
  echo "SOME CHECKS FAILED — review $TXT_OUT" >&2
fi
echo "Wrote: $JSON_OUT and $TXT_OUT"
exit "$overall"
