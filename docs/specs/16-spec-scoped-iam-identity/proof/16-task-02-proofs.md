# Task 02 Proofs — scoped permissions policy + self-referencing permissions boundary

## Task Summary

This task authors the two managed policies at the heart of the feature as
reviewable HCL in `terraform/bootstrap/policies.tf`:

- **`abreiss-ensemble-terraform`** (scoped permissions) — CRUD on `abreiss-ensemble-*`
  resources across S3, DynamoDB, ECR, App Runner, Secrets Manager and IAM, with the
  handful of AWS-unavoidable account-level actions isolated into enumerated
  `Resource: "*"` statements, plus explicit anti-escalation `Deny`s.
- **`abreiss-ensemble-boundary`** (permissions boundary) — the ceiling for every role
  the scoped identity creates, capped to `abreiss-ensemble-*`, with an explicit deny
  on any permissions-boundary tampering.

Both are rendered to committed JSON review copies under `policies/` (account id
redacted) and linted with IAM Access Analyzer.

## What This Task Proves

- Both policies are **well-formed HCL**: `terraform fmt -check` and `terraform validate`
  both exit `0`.
- Both policies are **valid and free of over-grants**: `aws accessanalyzer validate-policy`
  returns **zero findings** (no `ERROR`, no `SECURITY_WARNING`, not even a suggestion)
  on each rendered document (Success Metric 1).
- The **self-referencing boundary mechanism** is present: the scoped policy's
  `iam:CreateRole` is allowed only when `iam:PermissionsBoundary` equals the
  `abreiss-ensemble-boundary` ARN, and both policies explicitly deny weakening or
  removing that boundary (Security Considerations; Success Metric 3 precondition).
- `iam:PassRole` is restricted to `role/abreiss-ensemble-*` **and** conditioned to App
  Runner service principals; the OIDC provider has **no** create/update/delete.
- The scoped policy fits IAM's **6,144-character managed-policy limit** (~6,038 chars),
  so Task 3.0's `apply` will not be rejected for size.

## Evidence Summary

| Check | Command | Result |
| --- | --- | --- |
| Well-formed HCL | `terraform fmt -check` / `validate` | both exit `0`; "configuration is valid" |
| No over-grants (boundary) | `aws accessanalyzer validate-policy … boundary.json` | `[]` — zero findings |
| No over-grants (scoped) | `aws accessanalyzer validate-policy … terraform.json` | `[]` — zero findings |
| Under IAM size limit | `jq -c . \| tr -d ' \n\t' \| wc -c` | scoped 6,038 / boundary 2,519 (limit 6,144) |
| Self-referencing boundary | `jq '.Statement[] \| select(.Sid=="IamCreateRoleWithBoundaryOnly")'` | `CreateRole` gated on the boundary ARN |
| Account id redacted | `grep -rc <acct> policies/` | not present; placeholder `123456789012` used |

## Artifact: `terraform fmt -check` + `validate`

**What it proves:** The two `aws_iam_policy_document` data sources and their
`aws_iam_policy` resources are syntactically valid, canonically formatted HCL.

**Why it matters:** This is the module's HCL-well-formedness gate; the rendered JSON
below is only trustworthy if the source config validates.

**Commands:**

```bash
terraform -chdir=terraform/bootstrap fmt -check
terraform -chdir=terraform/bootstrap validate
```

**Result summary:** `fmt -check` exits `0` (clean); `validate` returns
`Success! The configuration is valid`. The only warning is the operator's unrelated
local provider dev-override (`dev.local/nico/devops-api`), not `hashicorp/aws`.

```text
fmt exit: 0
Success! The configuration is valid, but there were some validation warnings
validate exit: 0
```

## Artifact: IAM Access Analyzer `validate-policy` — zero findings on both

**What it proves:** Both rendered policy documents are valid IAM and carry no
over-grant findings — the substance of Success Metric 1.

**Why it matters:** `terraform validate` checks HCL, not IAM semantics. Access
Analyzer is what proves the policies are valid IAM and free of obvious over-grants
(e.g. `PassRole` with `*`, public-exposure warnings). An earlier iteration's only
finding was a `REDUNDANT_RESOURCE` **suggestion** on the boundary's combined
S3 bucket+object statement; it was removed by splitting that into precise
bucket-level and object-level statements, so the final result is empty on both.

**Commands:**

```bash
aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://terraform/bootstrap/policies/abreiss-ensemble-boundary.json --query 'findings'
aws accessanalyzer validate-policy --policy-type IDENTITY_POLICY \
  --policy-document file://terraform/bootstrap/policies/abreiss-ensemble-terraform.json --query 'findings'
```

**Result summary:** Both return `[]` — no `ERROR`, `SECURITY_WARNING`, `WARNING`, or
`SUGGESTION`. No accepted-finding documentation is needed.

```text
BOUNDARY: []
SCOPED:   []
```

## Artifact: managed-policy size under the 6,144-char limit

**What it proves:** The scoped policy renders to 6,038 characters (excluding
whitespace), within IAM's hard 6,144-character managed-policy limit.

**Why it matters:** IAM rejects an over-limit managed policy at create time, so a
policy that only "validates" but exceeds the limit would fail Task 3.0's `apply`.
`s3:GetBucket*`/`s3:PutBucket*` are wildcarded over the bucket's config
sub-resources (still fully contained to the `abreiss-ensemble-*` bucket ARN) partly
to stay within this budget.

**Command:**

```bash
for f in terraform/bootstrap/policies/*.json; do
  echo "$(basename $f): $(jq -c . "$f" | tr -d ' \n\t' | wc -c) chars"
done
```

**Result summary:** Scoped 6,038 / boundary 2,519 — both under 6,144.

```text
abreiss-ensemble-boundary.json: 2519 chars
abreiss-ensemble-terraform.json: 6038 chars
```

## Artifact: the security mechanisms are present in the rendered JSON

**What it proves:** The committed review JSON contains the exact scoping and
anti-escalation constructs the spec requires — the self-referencing `CreateRole`
condition, scoped `PassRole`, the anti-escalation denies, and no OIDC create.

**Why it matters:** These are the constructs that make "it's scoped" and "the
boundary is enforced, not applied by convention" true rather than asserted.

**Command:**

```bash
jq '.Statement[] | select(.Sid=="IamCreateRoleWithBoundaryOnly")' \
  terraform/bootstrap/policies/abreiss-ensemble-terraform.json
jq -r '.Statement[] | select(.Effect=="Deny") | .Sid' \
  terraform/bootstrap/policies/abreiss-ensemble-terraform.json
```

**Result summary:** `CreateRole` is gated on the boundary ARN; `PassRole` is scoped
to `role/abreiss-ensemble-*` with `iam:PassedToService` ∈ the App Runner principals;
five anti-escalation denies are present in the scoped policy and two boundary
self-protection denies in the boundary policy; no `iam:CreateOpenIDConnectProvider`
appears as an `Allow`.

```text
# Scoped: CreateRole condition (account id redacted to 123456789012)
"Condition": { "StringEquals": {
  "iam:PermissionsBoundary": "arn:aws:iam::123456789012:policy/abreiss-ensemble-boundary" } }
"Resource": "arn:aws:iam::123456789012:role/abreiss-ensemble-*"

# Scoped: explicit Deny SIDs
DenyRemovingPermissionsBoundary
DenyWeakeningPermissionsBoundary
DenyBoundaryPolicyModification
DenySelfModification
DenyOidcProviderMutation

# Scoped: OIDC create as Allow -> none (correct)

# Boundary: explicit Deny SIDs
DenyAnyPermissionsBoundaryAction
DenyBoundaryPolicySelfModification
```

## Reviewer notes

- **Boundary ARN is a computed local, not `aws_iam_policy.boundary.arn`.** The scoped
  policy's `CreateRole` condition and the boundary's own self-protection deny name the
  boundary ARN via `local.boundary_policy_arn` (computed from name). This keeps both
  documents renderable to committed review JSON without a live apply and avoids a
  data-source ⇄ resource cycle; `depends_on = [aws_iam_policy.boundary]` on the scoped
  policy preserves apply-time create ordering. The resulting ARN is identical to
  `aws_iam_policy.boundary.arn`.
- **Account id redaction.** Committed JSON uses placeholder account `123456789012`;
  live values come from `data.aws_caller_identity` at apply time.
- **The enumerated `*`-exception list** with per-action justification lives inline in
  `policies.tf` and in [`terraform/bootstrap/policies/README.md`](../../../../terraform/bootstrap/policies/README.md).
  It will also be surfaced in `docs/AWS_ACCESS.md` in Task 3.0.

## Reviewer Conclusion

The two managed policies are well-formed, canonically-formatted HCL that pass IAM
Access Analyzer with zero findings, fit within IAM's managed-policy size limit, and
encode the scoping and self-referencing-boundary mechanisms the spec requires —
proven against the rendered, redacted JSON committed for review.
