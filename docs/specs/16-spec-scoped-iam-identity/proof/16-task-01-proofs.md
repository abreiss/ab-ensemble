# Task 01 Proofs — bootstrap module scaffold + state/secret lockdown

## Task Summary

This task creates the repo's first `terraform/` directory as a version-pinned,
region-parameterized `terraform/bootstrap/` skeleton, **and** closes the
state/credential-leak hole before any `apply` can generate a secret-bearing state
file. It is sequenced first because the `*.tfstate` ignore + AWS-key secret-scan
backstop must exist before Task 3.0's `apply` runs.

## What This Task Proves

- The scaffolded module is well-formed, version-pinned HCL: `terraform fmt -check`
  and `terraform validate` both exit `0`, with `var.aws_region` (default
  `us-east-1`) and `data.aws_caller_identity` wired and no hard-coded account id.
- Terraform state can never be committed: `*.tfstate*` and `.terraform/` are
  git-ignored, while `.terraform.lock.hcl` stays committable for reproducible plans.
- The pre-commit secret scan now covers AWS credentials: a staged fixture holding
  AWS's published example key is blocked, and the repo passes clean once removed.

## Evidence Summary

| Check | Command | Result |
| --- | --- | --- |
| Well-formed HCL | `terraform fmt -check` / `validate` | both exit `0` |
| State un-committable | `git check-ignore` + `git status --porcelain -uall` | `*.tfstate` + `.terraform/` ignored; lock file committable |
| AWS-key backstop | `pre-commit run block-aws-keys` | fixture blocked (exit 1), clean repo passes (exit 0) |

## Artifact: `terraform validate` + `fmt -check` on the scaffold

**What it proves:** The module skeleton is syntactically valid, version-pinned HCL
with the `aws_region` variable and caller-identity data source in place (Unit 1
module FR).

**Why it matters:** Everything downstream (the two policies, the user, the proofs)
is authored inside this module; if the skeleton did not validate, nothing else
could be trusted.

**Commands:**

```bash
terraform -chdir=terraform/bootstrap init -backend=false -input=false
terraform -chdir=terraform/bootstrap fmt -check
terraform -chdir=terraform/bootstrap validate
```

**Result summary:** `init` succeeds, `fmt -check` exits `0` (clean), `validate`
returns `Success! The configuration is valid`. The only warning is an unrelated
provider dev-override in the operator's local `~/.terraformrc` (for
`dev.local/nico/devops-api`, not `hashicorp/aws`) — it does not affect this module.

```text
=== fmt -check ===
fmt exit: 0
=== validate ===
Warning: Provider development overrides are in effect
  - dev.local/nico/devops-api in /Users/nico/go/bin   (unrelated; not hashicorp/aws)
Success! The configuration is valid, but there were some validation warnings as shown above.
validate exit: 0
```

## Artifact: Terraform state cannot be committed

**What it proves:** `*.tfstate*` and `.terraform/` are git-ignored, so the
secret-bearing state file Task 3.0's `apply` produces can never be staged;
`.terraform.lock.hcl` is deliberately left committable (Unit 2 FR).

**Why it matters:** `aws_iam_access_key` writes the secret into `terraform.tfstate`.
This ignore rule is the primary defense against leaking that secret.

**Commands:**

```bash
git check-ignore -v terraform/bootstrap/terraform.tfstate terraform/bootstrap/.terraform
git check-ignore -v terraform/bootstrap/.terraform.lock.hcl
git status --porcelain -uall terraform/
```

**Result summary:** Both state paths resolve to explicit ignore rules; the lock
file matches the `!.terraform.lock.hcl` negation (committable). The expanded
untracked listing shows the `.tf` source files and the lock file only — the
`.terraform/` cache dir that exists on disk is not visible to git, and no
`*.tfstate` exists yet.

```text
.gitignore:69:*.tfstate    terraform/bootstrap/terraform.tfstate
.gitignore:71:.terraform/  terraform/bootstrap/.terraform
.gitignore:78:!.terraform.lock.hcl  terraform/bootstrap/.terraform.lock.hcl   (negation = NOT ignored)

git status --porcelain -uall terraform/:
?? terraform/bootstrap/.terraform.lock.hcl
?? terraform/bootstrap/README.md
?? terraform/bootstrap/identity.tf
?? terraform/bootstrap/outputs.tf
?? terraform/bootstrap/policies.tf
?? terraform/bootstrap/providers.tf
?? terraform/bootstrap/variables.tf
?? terraform/bootstrap/versions.tf
# (no .terraform/ and no *.tfstate in the list — grep for them returns nothing)
```

## Artifact: pre-commit secret scan blocks AWS credentials

**What it proves:** The new `block-aws-keys` pygrep hook blocks a staged fixture
holding AWS's published example credentials, then the repository passes clean once
the fixture is removed (Unit 2 FR). The fixture uses AWS's documented example key
only — no real credential.

**Why it matters:** This is the backstop that stops an accidental `terraform.tfstate`
or `~/.aws/credentials` paste from ever being committed. The `AWS_ACCESS.md` doc and
the spec-16 proof paths (which intentionally display the example key in transcripts)
are path-allowlisted, so real keys are still caught everywhere else.

**Commands:**

```bash
# fixture at a NON-allowlisted path (AWS published example credentials only)
printf '[default]\naws_access_key_id = AKIAIOSFODNN7EXAMPLE\naws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n' > aws-key-leak-fixture.txt
git add aws-key-leak-fixture.txt
pre-commit run block-aws-keys --files aws-key-leak-fixture.txt   # expect FAIL
git rm --cached aws-key-leak-fixture.txt && rm -f aws-key-leak-fixture.txt
pre-commit run block-aws-keys --all-files                        # expect PASS
```

**Result summary:** The staged fixture fails the hook (exit 1) with both the
access-key id and secret lines flagged; after removal the whole-repo scan passes
(exit 0). The fixture was never committed.

```text
Block committed AWS access keys..........................................Failed
- hook id: block-aws-keys
- exit code: 1
aws-key-leak-fixture.txt:2:aws_access_key_id = AKIAIOSFODNN7EXAMPLE
aws-key-leak-fixture.txt:3:aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
block-aws-keys exit: 1

Block committed AWS access keys..........................................Passed
block-aws-keys exit: 0
```

## Reviewer Conclusion

The bootstrap module skeleton is valid, version-pinned, region-parameterized HCL
with no hard-coded account id, and the two leak vectors that Task 3.0's `apply`
would otherwise open — committed state and committed AWS keys — are both closed and
demonstrably enforced before any `apply` runs.
