# Task 03 Proofs — provision the scoped Terraform-runner identity + document it

## Task Summary

This task provisions the `abreiss-ensemble-terraform` IAM identity via the one-time
bootstrap `apply` and documents the whole setup. It adds:

- `terraform/bootstrap/identity.tf` — the `aws_iam_user`, the scoped-policy attachment,
  and the programmatic `aws_iam_access_key`.
- `terraform/bootstrap/outputs.tf` — the access-key id/secret as **sensitive** outputs
  plus the user/scoped/boundary ARNs.
- `docs/AWS_ACCESS.md` — the reviewable narrative (what the bootstrap creates, one-time
  admin apply, key delivery Options A/B, rotation/revocation, the enumerated `*`-exception
  list, the assume-role variant, and the local-state warning), cross-linked from
  `docs/DEVELOPMENT.md` (prerequisites) and `docs/ARCHITECTURE.md` (deployment + security).

The one-time `apply` was run against a live AWS account; all captured output is
**redacted** (no account id, no access key, no secret).

## What This Task Proves

- The bootstrap `apply` **provisions the identity**: it creates the boundary policy,
  the scoped policy, the `abreiss-ensemble-terraform` user, the scoped-policy attachment,
  and an access key (Unit 2 FR).
- The identity is **usable locally**: `aws sts get-caller-identity` under the new
  profile returns the `abreiss-ensemble-terraform` user ARN (Unit 2 FR).
- **No secret is committed** (Success Metric 5): the access-key secret lives only in
  local, git-ignored `terraform.tfstate` and in `~/.aws/credentials`; `git status`
  shows no state file, `git check-ignore` confirms the ignore, and the `block-aws-keys`
  pre-commit scan passes.
- The setup is **reviewable and reproducible**: `docs/AWS_ACCESS.md` exists and is
  cross-linked, matching the repo's docs-map convention (Unit 2 FR).

## Evidence Summary

| Check | Command | Result |
| --- | --- | --- |
| Plan is exactly the identity | `terraform plan` | `Plan: 5 to add, 0 to change, 0 to destroy` |
| Apply provisions the identity | `terraform apply -auto-approve` | `Apply complete!` — boundary + scoped policy, user, attachment, access key |
| Identity usable locally | `aws --profile abreiss-ensemble-terraform sts get-caller-identity` | `…:user/abreiss-ensemble-terraform` |
| Scoped policy attached | `aws iam list-attached-user-policies` | `abreiss-ensemble-terraform` attached |
| Boundary policy present | `aws iam list-policies --scope Local` | `abreiss-ensemble-boundary` exists, attachable |
| No state tracked | `git status --porcelain` | only code/docs/proof; no `*.tfstate` |
| State git-ignored | `git check-ignore …/terraform.tfstate` | path echoed (ignored) |
| Secret-scan backstop | `pre-commit run block-aws-keys --all-files` | `Passed` |

## Artifact: one-time `terraform apply` provisions the identity

**What it proves:** The bootstrap module, applied once with elevated credentials,
creates exactly the scoped identity and its two policies — nothing else.

**Why it matters:** This is Unit 2's core deliverable: the identity Claude/Terraform
runs as for issue 9's infra work exists and carries the scoped policy.

**Commands:**

```bash
terraform -chdir=terraform/bootstrap init -input=false
terraform -chdir=terraform/bootstrap plan  -input=false     # review before applying
terraform -chdir=terraform/bootstrap apply -input=false -auto-approve
```

**Result summary:** `plan` reported `5 to add, 0 to change, 0 to destroy`. Apply created
all five resources. (The first apply attempt failed on an IAM tag-value validation —
IAM tag values disallow `#`, so the `Purpose` tag "issue #9" was corrected to "issue 9";
the two policies were already created, so the follow-up apply added the remaining three.
This is captured here for reviewer transparency.) Account id, access-key id, and secret
are redacted.

```text
Plan: 5 to add, 0 to change, 0 to destroy.

aws_iam_policy.boundary:        Creation complete [id=arn:aws:iam::REDACTED_ACCT:policy/abreiss-ensemble-boundary]
aws_iam_policy.terraform_scoped:Creation complete [id=arn:aws:iam::REDACTED_ACCT:policy/abreiss-ensemble-terraform]
aws_iam_user.terraform_runner:  Creation complete [id=abreiss-ensemble-terraform]
aws_iam_access_key.terraform_runner: Creation complete [id=AKIA...REDACTED]
aws_iam_user_policy_attachment.terraform_runner_scoped: Creation complete
Apply complete! Resources: 3 added, 0 changed, 0 destroyed.

Outputs:
boundary_policy_arn       = "arn:aws:iam::REDACTED_ACCT:policy/abreiss-ensemble-boundary"
scoped_policy_arn         = "arn:aws:iam::REDACTED_ACCT:policy/abreiss-ensemble-terraform"
terraform_runner_user_arn = "arn:aws:iam::REDACTED_ACCT:user/abreiss-ensemble-terraform"
# terraform_runner_access_key_id / _secret_access_key are sensitive — not printed
```

## Artifact: the new identity is usable locally

**What it proves:** The access key surfaced by the sensitive outputs, loaded into a
local `~/.aws/credentials` profile, authenticates as the scoped user.

**Why it matters:** A provisioned-but-unusable identity would not let #9's Terraform
run. `sts:GetCallerIdentity` is the one allowed account-level preflight in the scoped
policy, so this also exercises the policy.

**Commands:**

```bash
# Secret piped straight from the sensitive output into ~/.aws/credentials; never printed:
terraform -chdir=terraform/bootstrap output -raw terraform_runner_access_key_id     | ...
terraform -chdir=terraform/bootstrap output -raw terraform_runner_secret_access_key | ...
aws configure set ... --profile abreiss-ensemble-terraform
aws --profile abreiss-ensemble-terraform sts get-caller-identity
```

**Result summary:** Returns the `abreiss-ensemble-terraform` user ARN (account and
user unique-id redacted).

```json
{
  "UserId": "AIDA_REDACTED",
  "Account": "REDACTED_ACCT",
  "Arn": "arn:aws:iam::REDACTED_ACCT:user/abreiss-ensemble-terraform"
}
```

## Artifact: no secret is committed

**What it proves:** The access-key secret exists only in local, git-ignored state and
in `~/.aws/credentials` — never in a tracked file — and the pre-commit scan blocks a
stray AWS key.

**Why it matters:** Success Metric 5 and the whole "never committed" requirement. The
state file genuinely contains the secret, which is exactly why it must be ignored.

**Commands:**

```bash
git status --porcelain
ls -la terraform/bootstrap/*.tfstate*
git check-ignore terraform/bootstrap/terraform.tfstate terraform/bootstrap/.terraform
grep -oE '"secret"' terraform/bootstrap/terraform.tfstate | sort -u
pre-commit run block-aws-keys --all-files
```

**Result summary:** `git status` lists only code/docs/proof files — no `*.tfstate`.
`terraform.tfstate` exists locally and contains a `"secret"` field, and
`git check-ignore` confirms both it and `.terraform/` are ignored. Nothing matching a
tfstate or AWS key is tracked. The `block-aws-keys` hook passes.

```text
# git status --porcelain (no tfstate present)
 M terraform/bootstrap/identity.tf
 M terraform/bootstrap/outputs.tf
 M docs/ARCHITECTURE.md
 M docs/DEVELOPMENT.md
?? docs/AWS_ACCESS.md
 ...

terraform.tfstate exists locally  →  contains "secret"
git check-ignore  →  terraform/bootstrap/terraform.tfstate
                     terraform/bootstrap/.terraform
git ls-files | grep tfstate  →  none tracked

Block committed AWS access keys..........................................Passed
```

## Artifact: scoped policy attached + boundary present

**What it proves:** The user carries the scoped policy, and the boundary policy exists
and is attachable (so #9's `CreateRole` can reference it).

**Command:**

```bash
aws iam list-attached-user-policies --user-name abreiss-ensemble-terraform
aws iam list-policies --scope Local --query "Policies[?PolicyName=='abreiss-ensemble-boundary']"
```

**Result summary:** The user has exactly `abreiss-ensemble-terraform` attached; the
`abreiss-ensemble-boundary` policy exists and is attachable.

```json
{ "AttachedPolicies": [ { "PolicyName": "abreiss-ensemble-terraform",
    "PolicyArn": "arn:aws:iam::REDACTED_ACCT:policy/abreiss-ensemble-terraform" } ] }

[ { "Name": "abreiss-ensemble-boundary", "Attachable": true } ]
```

## Artifact: `docs/AWS_ACCESS.md` + cross-links

**What it proves:** The setup is documented and reviewable, cross-linked from the two
docs the spec names.

**Artifact paths:** `docs/AWS_ACCESS.md`; the added links in `docs/DEVELOPMENT.md`
(Prerequisites) and `docs/ARCHITECTURE.md` (Deployment + Security).

**Result summary:** `AWS_ACCESS.md` documents what the bootstrap creates, the one-time
admin apply, key delivery (Option A sensitive output / Option B console), rotation and
revocation, the enumerated `Resource:"*"` exception list, the assume-role drop-in note,
and the local-state warning. It also records (per audit finding F1) that Access Analyzer
is a one-time gate and CI enforcement is a #9 follow-up.

## Reviewer Conclusion

The one-time bootstrap `apply` provisions exactly the scoped `abreiss-ensemble-terraform`
identity (user + scoped policy + boundary + access key), the identity authenticates
locally as itself, its secret never leaves local git-ignored state / `~/.aws/credentials`
(confirmed by `git status`, `git check-ignore`, and the passing `block-aws-keys` scan),
and the whole setup is documented in `docs/AWS_ACCESS.md` with the required cross-links.
