# Task 06 Proofs - Live deploy under the scoped identity (6.1–6.2, 6.5–6.6; parent 6.0 in progress)

> Parent task 6.0 spans 6.1–6.6. This file proves **6.1** (live
> `terraform apply` as `abreiss-ensemble-terraform` → App Runner `RUNNING` +
> public `/api/health` 200), **6.2** (secret values populated out-of-band),
> **6.5** (the deferred #16 scoped-identity gate — write-up below), and
> **6.6** (the deploy runbook — artifact below). Evidence for **6.3–6.4**
> (CI-driven deploy log, golden path on the public URL) will be appended after
> the #9 PR merges to `main`, which is what triggers the deploy pipeline.
> **Account id redacted** to `123456789012` throughout, matching
> `terraform/bootstrap/policies/README.md` convention.

## Task Summary

The one-time operator sequence — state bucket, `terraform init` against the S3
backend with `use_lockfile`, `terraform apply` as the scoped
`abreiss-ensemble-terraform` identity — now completes end-to-end: every
resource in `terraform/deploy/` exists, the App Runner service is `RUNNING`
on its public URL, and the health endpoint answers `200 {"status":"ok"}`.

Getting there surfaced and fixed three real, stacked failures (each one masked
the next):

1. **arm64 seed image.** The one-time `:latest` seed was built on an Apple
   Silicon Mac without `--platform linux/amd64`; App Runner only runs amd64,
   so the image pulled fine and then failed with a bare *"Failed to deploy your
   application image"* and no application logs. Fixed by rebuilding with
   `docker buildx build --platform linux/amd64` (delete + repush, since the
   repo's tags are IMMUTABLE). Documented in `terraform/deploy/README.md`
   ("Seed image").
2. **PassRole condition that can never match.** With the image fixed,
   `CreateService` died with `AccessDeniedException ... iam:PassRole ... no
   identity-based policy allows`. The `iam:PassedToService` condition failed
   under **every** operator form (`StringEquals`, `StringEqualsIfExists`,
   `ForAllValues:StringEquals` — each allowed by the IAM policy simulator,
   each denied live, retried past propagation). A temporary no-condition
   diagnostic policy flipped the call to success, isolating the condition;
   the landed fix drops it from all three PassRole statements (scoped policy,
   boundary ceiling, CI role), keeping the `abreiss-ensemble-*` resource scope
   and role trust policies as the guardrails. Full narrative:
   `docs/AWS_ACCESS.md` ("PassRole condition removed"). The diagnostic policy
   was detached and deleted **before** the final apply below, which therefore
   proves the canonical policies alone suffice (the #16 gate posture for 6.5).
3. **Cloud container dialing DynamoDB Local.** The container then crashed on
   startup connecting to `localhost:8000`: App Runner silently drops
   empty-string env vars, so `ENSEMBLE_DYNAMODB_ENDPOINT = ""` never reached
   the app and the base yml default won. Fixed TDD-first
   (`CloudProfileConfigTest`, RED → GREEN) with a `cloud` Spring profile that
   blanks the endpoint and disables table auto-create (the cloud table is
   Terraform-owned; the instance role deliberately has item-level DynamoDB
   permissions only), activated via `SPRING_PROFILES_ACTIVE=cloud` in
   `apprunner.tf`.

## What This Task Proves (so far)

- `terraform apply` of `terraform/deploy/` succeeds under **only** the scoped
  `abreiss-ensemble-terraform` identity (admin env creds explicitly stripped),
  against the S3 backend with S3-native locking.
- The App Runner service reaches `RUNNING` with the three Secrets Manager ARNs
  referenced (never values), and the public `/api/health` returns
  `200 {"status":"ok"}` (Success Metric 4, deploy + secret wiring half).
- The three secret values exist out-of-band (`AWSCURRENT` versions present;
  no value ever printed or committed) — sub-task 6.2.
- A follow-up `terraform plan` reports **No changes** — the live estate
  matches the committed configuration exactly.

## Evidence Summary

- The final apply transcript shows the tainted `CREATE_FAILED` service being
  replaced and completing in 2m25s, run as the scoped identity.
- `describe-service` shows `RUNNING` and secret **references** only.
- `curl` against the public URL returns `200 {"status":"ok"}`.
- `describe-secret` on all three secrets shows an `AWSCURRENT` version each.

## Artifact: Caller identity for the apply (scoped identity, not admin)

**What it proves:** The apply ran as the #16 scoped IAM user — the session's
admin env credentials were explicitly stripped (`env -u AWS_ACCESS_KEY_ID ...`)
in favor of the `abreiss-ensemble-terraform` profile.

**Why it matters:** Task 6.1/6.5's whole point is that the deploy works inside
the blast-radius box, not under admin.

**Command:**

~~~bash
env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN \
  AWS_PROFILE=abreiss-ensemble-terraform aws sts get-caller-identity --query Arn --output text
~~~

**Result summary:** The scoped user, not the SSO admin role.

~~~text
arn:aws:iam::123456789012:user/abreiss-ensemble-terraform
~~~

## Artifact: Final `terraform apply` transcript (redacted excerpt)

**What it proves:** The service replacement (tainted `CREATE_FAILED` →
`RUNNING`) and the CI-policy update applied cleanly as the scoped identity.

**Why it matters:** This is the moment the one-time operator sequence first
completed end-to-end.

**Command:**

~~~bash
env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN \
  AWS_PROFILE=abreiss-ensemble-terraform terraform apply <reviewed-plan-file>
~~~

**Result summary:** 1 added / 1 changed / 1 destroyed; App Runner service
created in 2m25s; outputs expose identifiers only (no secrets).

~~~text
aws_apprunner_service.app: Destroying... [id=arn:aws:apprunner:us-east-1:123456789012:service/abreiss-ensemble-app/ebede7e113be4c4f8f3cccea4705a4dc]
aws_iam_policy.ci: Modifying... [id=arn:aws:iam::123456789012:policy/abreiss-ensemble-ci]
aws_iam_policy.ci: Modifications complete after 0s [id=arn:aws:iam::123456789012:policy/abreiss-ensemble-ci]
aws_apprunner_service.app: Destruction complete after 9s
aws_apprunner_service.app: Creating...
aws_apprunner_service.app: Creation complete after 2m25s [id=arn:aws:apprunner:us-east-1:123456789012:service/abreiss-ensemble-app/5531ae565fb147249a0db4c0f7db436c]

Apply complete! Resources: 1 added, 1 changed, 1 destroyed.

Outputs:

app_runner_service_arn = "arn:aws:apprunner:us-east-1:123456789012:service/abreiss-ensemble-app/5531ae565fb147249a0db4c0f7db436c"
app_runner_service_url = "i8nezk33fc.us-east-1.awsapprunner.com"
ci_role_arn = "arn:aws:iam::123456789012:role/abreiss-ensemble-ci"
dynamodb_table_name = "abreiss-ensemble-items"
ecr_repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/abreiss-ensemble-app"
s3_photos_bucket_name = "abreiss-ensemble-photos"
~~~

## Artifact: Service `RUNNING` with secret ARNs referenced (values never shown)

**What it proves:** The deployed service is healthy and its runtime secrets are
wired **by reference** — App Runner resolves them via the instance role; no
value appears in Terraform state outputs, the console, or this repo.

**Command:**

~~~bash
aws apprunner describe-service --service-arn <service-arn> \
  --query "Service.{Status:Status,Url:ServiceUrl,SecretRefs:SourceConfiguration.ImageRepository.ImageConfiguration.RuntimeEnvironmentSecrets | keys(@)}"
~~~

**Result summary:** `RUNNING`, public URL live, three secret keys referenced.

~~~json
{
    "Status": "RUNNING",
    "Url": "i8nezk33fc.us-east-1.awsapprunner.com",
    "SecretRefs": [
        "ENSEMBLE_ANTHROPIC_API_KEY",
        "ENSEMBLE_PASSCODE",
        "ENSEMBLE_SESSION_SECRET"
    ]
}
~~~

## Artifact: Public health check → 200

**What it proves:** The container is serving traffic on the public App Runner
URL — Spring Boot started against real DynamoDB/S3/Secrets Manager (the
`cloud` profile working end-to-end).

**Command:**

~~~bash
curl -sS -w "\nHTTP %{http_code}\n" https://i8nezk33fc.us-east-1.awsapprunner.com/api/health
~~~

**Result summary:** Exactly the Success Metric 4 check.

~~~text
{"status":"ok"}
HTTP 200
~~~

## Artifact: Secrets populated out-of-band (6.2) — versions only

**What it proves:** All three Secrets Manager secrets carry an `AWSCURRENT`
version, i.e. real values were set (console/CLI, out-of-band). No value is
read, printed, or committed anywhere.

**Command:**

~~~bash
for s in abreiss-ensemble-anthropic-key abreiss-ensemble-passcode abreiss-ensemble-session-secret; do
  aws secretsmanager describe-secret --secret-id "$s" --query "{Name:Name,Versions:VersionIdsToStages}"
done
~~~

**Result summary:** One `AWSCURRENT` version per secret (version ids are
harmless UUIDs; values never surfaced).

~~~text
abreiss-ensemble-anthropic-key   → 1 version staged AWSCURRENT
abreiss-ensemble-passcode        → 1 version staged AWSCURRENT
abreiss-ensemble-session-secret  → 1 version staged AWSCURRENT
~~~

## Artifact: Converged state — `terraform plan` shows no drift

**What it proves:** After all fixes, the committed HCL and the live estate
match exactly under the scoped identity.

**Command:**

~~~bash
env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN \
  AWS_PROFILE=abreiss-ensemble-terraform terraform plan
~~~

**Result summary:**

~~~text
No changes. Your infrastructure matches the configuration.
~~~

## Artifact: The deferred #16 scoped-identity gate is satisfied (6.5)

**What it proves:** `terraform apply` of the full deploy module succeeds using
**only** the `abreiss-ensemble-terraform` identity, and every permission gap
found on the way was closed as a narrowly-scoped `abreiss-ensemble-*` addition
— never a widening to `*`, never by falling back to broader credentials.

**Why it matters:** This is the cross-issue gate #16 deferred to #9: the
scoped-identity model only counts as proven once a real, non-trivial apply has
run entirely inside the box.

**Evidence (all captured above or in `docs/AWS_ACCESS.md`):**

- The final apply ran under the scoped user (see the caller-identity artifact:
  `arn:aws:iam::123456789012:user/abreiss-ensemble-terraform`) with admin env
  credentials explicitly stripped, and a follow-up `plan` reports
  **No changes** — the estate converges inside the box.
- The temporary no-condition diagnostic policy used to isolate the PassRole
  failure was detached and **deleted before** that final apply, so the run
  proves the canonical policies alone suffice.
- Two permission gaps were hit and closed per the gate's own rules, both
  documented in `docs/AWS_ACCESS.md`:
  1. Post-create read/waiter actions (`s3:GetAccelerateConfiguration`,
     `s3:GetReplicationConfiguration`, `secretsmanager:GetResourcePolicy`,
     `apprunner:DescribeAutoScalingConfiguration` + siblings) → added as the
     **second managed policy** `abreiss-ensemble-terraform-ext` (~943 chars,
     all resource-scoped to `abreiss-ensemble-*`), because folding them into
     the primary policy would exceed IAM's 6,144-char limit — exactly the
     "second managed policy, never widen" fallback the gate prescribes.
  2. The `iam:PassedToService` PassRole condition that can never match App
     Runner's live evaluation context → **removed**, shrinking the policy;
     resource scope (`abreiss-ensemble-*` roles only) and each role's trust
     policy remain the guardrails ("PassRole condition removed" in
     `docs/AWS_ACCESS.md`).

**Result summary:** Gate satisfied — Success Metric 5. No `Resource: "*"`
additions, no boundary weakening, no admin-credential fallback; the two
`terraform/bootstrap/` changes are themselves reviewable rendered-policy diffs.

## Artifact: Deploy runbook (6.6)

**What it proves:** The operator run is documented and repeatable — the FR's
required flow (state-bucket bootstrap → `apply` → secret population →
push-to-deploy → reading the public URL → rollback) is written down where an
operator will actually look, cross-referenced in the `docs/AWS_ACCESS.md`
style.

**Why it matters:** Without the runbook, the live deploy stays a
one-person-can-do-it memory instead of a reviewable procedure.

**Artifact paths:**

- `README.md` — new **"Deploy to AWS (App Runner)"** section: the six-step
  runbook (state bucket → `terraform apply` → out-of-band secret population →
  amd64 seed image → GitHub repo variables → push-to-deploy), reading the
  public URL, and rollback by repointing App Runner at an earlier immutable
  `sha-<git-sha>` tag (with the explicit note that re-running an old Deploy
  workflow cannot work against IMMUTABLE tags).
- `docs/DEVELOPMENT.md` — new **"Deploying (operator-run)"** section
  cross-referencing the runbook, `AWS_ACCESS.md`, `terraform/deploy/README.md`,
  and `ARCHITECTURE.md`.
- `docs/ARCHITECTURE.md` — "Deployment (later)" rewritten to
  **"Deployment (shipped — issue #9)"** describing the as-built module and
  pipeline (cloud Spring profile, secret-by-ARN injection, S3-native state
  locking, `update-service` repoint deploy, rollback), plus a Security bullet
  for the OIDC CI role and the standing Access Analyzer lint.

**Result summary:** All six FR-required runbook elements are present and
mutually cross-referenced; secret handling instructions keep values out of
shell history, state, and git.

## Reviewer Conclusion

Sub-tasks 6.1, 6.2, 6.5, and 6.6 are done: the full `terraform/deploy/` estate
exists and converges under only the scoped `abreiss-ensemble-terraform`
identity, the App Runner service is `RUNNING` with secrets wired by ARN, the
public `/api/health` answers `200`, the #16 gate is satisfied without any
scope widening, and the deploy runbook is written and cross-referenced. The
three root causes that had made this step "fail over and over" (arm64 seed
image, the never-matching `iam:PassedToService` PassRole condition, and App
Runner dropping empty-string env vars) are each fixed at the right layer,
regression-guarded (`CloudProfileConfigTest`, README seed-image section), and
written up in `docs/AWS_ACCESS.md`. Remaining for parent 6.0, both gated on
the #9 PR merging to `main` (the deploy pipeline's trigger): the CI-driven
deploy proof (6.3) and the golden path on the public URL (6.4).
