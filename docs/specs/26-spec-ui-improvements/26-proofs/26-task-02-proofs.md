# Task 02 Proofs — Cloud provisioning for the outfits table (Terraform + App Runner env var, no IAM diff)

## Task Summary

This task adds the minimal, pre-authorized cloud delta so that a deploy after
merge writes saved outfits to a real table instead of hitting a 5xx landmine: a
dedicated `aws_dynamodb_table "outfits"` (`${local.prefix}-outfits`, hash key
`outfitId`, `PAY_PER_REQUEST`) in `data_stores.tf`, and the
`ENSEMBLE_OUTFITS_TABLE_NAME` runtime env var wired into App Runner in
`apprunner.tf`. The instance-role DynamoDB grant is **unchanged** — it is already
scoped to `table/${local.prefix}-*`, a wildcard that covers the new table — so
there is **no IAM diff**. Per `docs/TESTING.md`, IaC is validated by
`fmt`/`validate`/`plan`, not unit tests.

## What This Task Proves

- The Terraform is well-formed (`fmt -check` clean) and internally valid (`validate` succeeds).
- A real `terraform plan` against the live account produces exactly the intended delta: the outfits table is **created** and the `ENSEMBLE_OUTFITS_TABLE_NAME` env var is **added** to the App Runner service in-place.
- **No `aws_iam_*` resource is added, changed, or destroyed** — success metric #6 ("no IAM diff") holds, because the instance-role grant already matches `table/${local.prefix}-*`.
- The env var resolves to the created table's name (`abreiss-ensemble-outfits`), so the running container reaches the right table with no code change to how `ENSEMBLE_*` keys are read.

## Evidence Summary

- `terraform fmt -check` → clean (no files need formatting).
- `terraform validate` → `Success! The configuration is valid` (the only message is an environment-specific provider-dev-override warning, unrelated to this change).
- `terraform plan` → **`Plan: 1 to add, 1 to change, 0 to destroy`**; the sole actions are `aws_dynamodb_table.outfits` (add) and `aws_apprunner_service.app` (in-place: `+ ENSEMBLE_OUTFITS_TABLE_NAME = "abreiss-ensemble-outfits"`).
- No `aws_iam_*` resource appears in the plan's action set (every `aws_iam_*` line in the run is a `Refreshing state…` / data-source `Reading…` line, not a planned change).

## Artifact: `terraform fmt -check` and `validate`

**What it proves:** The added HCL is canonically formatted and internally valid.

**Why it matters:** These are the two gates `docs/TESTING.md` requires for IaC (which is not unit-tested); they are the cheap, deterministic correctness signal before a plan.

**Command:**

~~~bash
terraform -chdir=terraform/deploy fmt -check   # exit 0, no output
terraform -chdir=terraform/deploy validate
~~~

**Result summary:** `fmt -check` exits `0` with no files listed (already formatted). `validate` prints `Success! The configuration is valid.` The only accompanying note is a `Provider development overrides are in effect` warning — an environment-local provider mirror, not a property of this change.

## Artifact: `terraform plan` — the intended delta only (account id / git SHA redacted)

**What it proves:** Against the live account+state, this change adds the outfits table and the env var and nothing else; no IAM resource is touched.

**Why it matters:** This is the binding proof for the task's success metric ("no IAM diff") and for the claim that the cloud delta is exactly the table + env var.

**Command:**

~~~bash
terraform -chdir=terraform/deploy plan -no-color -input=false
~~~

**Result summary:** `Plan: 1 to add, 1 to change, 0 to destroy`. The outfits table is created; the App Runner service gains `ENSEMBLE_OUTFITS_TABLE_NAME = "abreiss-ensemble-outfits"` in place. No `aws_iam_*` resource is in the action set.

~~~text
Terraform will perform the following actions:

  # aws_apprunner_service.app will be updated in-place
  ~ resource "aws_apprunner_service" "app" {
        id  = "arn:aws:apprunner:us-east-1:<ACCOUNT_ID>:service/abreiss-ensemble-app/5531ae565fb147249a0db4c0f7db436c"
      ~ source_configuration {
          ~ image_repository {
              ~ image_identifier = ".../abreiss-ensemble-app:sha-<GIT_SHA>" -> ".../abreiss-ensemble-app:latest"   # pre-existing drift — see note below
              ~ image_configuration {
                  ~ runtime_environment_variables = {
                      + "ENSEMBLE_OUTFITS_TABLE_NAME"  = "abreiss-ensemble-outfits"
                        # (4 unchanged elements hidden)
                    }
                }
            }
        }
    }

  # aws_dynamodb_table.outfits will be created
  + resource "aws_dynamodb_table" "outfits" {
      + arn          = (known after apply)
      + billing_mode = "PAY_PER_REQUEST"
      + hash_key     = "outfitId"
      + id           = (known after apply)
      + name         = "abreiss-ensemble-outfits"
      + ...
      + attribute {
          + name = "outfitId"
          + type = "S"
        }
    }

Plan: 1 to add, 1 to change, 0 to destroy.
~~~

**Pre-existing drift disclosure (operator honesty):** the same App Runner block
also shows `image_identifier` reverting `:sha-<git-sha>` → `:latest`. This is **not
part of this task**. It is the accepted, documented drift called out in
`apprunner.tf`: deploys are driven by CI via `aws apprunner update-service` to an
immutable `sha-<git-sha>` tag, while the Terraform config intentionally pins
`:latest` (the one-time bootstrap seed). Operators never run `terraform apply` to
deploy, so this drift is never reconciled by Terraform and is orthogonal to the
outfits change.

## Artifact: No IAM diff (success metric #6) — and why the description was left unchanged

**What it proves:** Adding the outfits table produces zero IAM churn.

**Why it matters:** The instance role is a live, attached, least-privilege policy; the task's success metric requires that saved outfits ride the existing `table/${local.prefix}-*` grant with no IAM change.

**Result summary:** `local.dynamodb_arn` (providers.tf) is
`arn:…:table/${local.prefix}-*` — already covering `abreiss-ensemble-outfits` — so
the `RuntimeDynamoDb` statement needs no edit. The outfits mention was therefore
carried in an **HCL comment** above that statement, not in the policy's live
`description` string.

**Deviation from the task's literal wording (and why it is correct):** Task 2.3
said to update the policy "description/comment." An initial attempt that edited the
`aws_iam_policy.instance` **`description`** flipped the plan from `1 add/1 change`
to **`3 add/2 destroy`**, with the plan annotating the description line
`# forces replacement`:

~~~text
# aws_iam_policy.instance must be replaced
  ~ description = "...items table..." -> "...items and outfits tables..." # forces replacement
# aws_iam_role_policy_attachment.instance must be replaced
~~~

An `aws_iam_policy` `description` is immutable in AWS, so any change forces a
destroy+recreate of the policy and cascades to its role attachment — a real IAM
diff on a live attached role. Reverting the string (keeping the mention as an HCL
comment, which has no plan effect) restores the `1 add/1 change/0 destroy` plan and
preserves the "no IAM diff" guarantee. The task explicitly allowed
"description/**comment**," so the comment path is spec-compliant and is the safer
choice.

## Reviewer Conclusion

The cloud delta for saved outfits is exactly what was intended and pre-authorized:
a dedicated `abreiss-ensemble-outfits` DynamoDB table plus one App Runner env var,
riding the existing wildcard-scoped instance-role grant with **zero IAM churn**.
`fmt`/`validate` pass and the live `plan` confirms `1 to add, 1 to change, 0 to
destroy` with no `aws_iam_*` action. The only other line in the plan is the
long-standing, documented CI-vs-config `image_identifier` drift, which this task
neither introduces nor applies.
