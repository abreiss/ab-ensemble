# 09 Questions Round 1 - Infra + Deploy Pipeline (Terraform + App Runner + CI)

Please answer each question below (check one or more options, or add your own notes). Feel free to add additional context under any question. When you're done, save the file and tell me to continue.

These decisions materially change the spec — they determine the deliverable shape, the acceptance criteria, and (most importantly) **what counts as proof that "it works"**, since a live cloud deploy needs real AWS credentials + Claude spend that this environment may not have. I'm confirming them before writing the spec rather than guessing.

Context I'm working from (verified in the repo):
- Only `terraform/bootstrap/` (the scoped `abreiss-ensemble-terraform` identity from #16) exists — **no deploy module, no CI, no `S3PhotoStorage`**.
- `DynamoDbConfig` is hardwired for DynamoDB Local (forced `endpointOverride` + dummy static creds); it can't talk to real AWS yet.
- The #16 permissions boundary **already pre-authorizes a future CI role** for ECR push + `apprunner:StartDeployment`/`UpdateService`/`DescribeService` + one scoped `iam:PassRole` — but **not** broad Terraform permissions. That's a strong hint about how the pipeline should be split (see Q3).
- #16 explicitly deferred two things to #9: the **remote Terraform state backend** and **standing policy-lint (Access Analyzer) in CI**.

---

## 1. Scope — one spec, or split the backend code from the infra?

Issue #9 is large: it spans (a) **backend cloud-readiness code** — `S3PhotoStorage` + a storage bean selector, and making `DynamoDbConfig` work against real AWS (this is strict-TDD Java domain work); and (b) **infra + CI glue** — the Terraform deploy module (S3, DynamoDB, ECR, App Runner, Secrets Manager, instance role, CI OIDC role) plus the GitHub Actions pipeline (validated, not unit-tested). How should we structure it?

- [x ] (A) **One spec (`09-spec-deploy-pipeline`), broken into 4 demoable units**: (1) backend cloud-readiness code, (2) Terraform deploy module, (3) CI pipeline, (4) live deploy + golden-path verification. Matches the single MVP issue #9; the units still give incremental, independently-demoable progress.
- [ ] (B) **Split into two specs** — e.g. `09-spec-cloud-readiness` (the Java `S3PhotoStorage`/config work, strict TDD) and `10-spec-deploy-pipeline` (Terraform + CI). Cleaner TDD/infra separation, but adds a second spec + coordination, and the infra spec can't be fully proven without the code spec.
- [ ] (E) Other (describe)

**Current best-practice context:** SDD guidance flags very large, multi-technology features as split candidates. But #9's parts are tightly interdependent (App Runner can't run without the S3/Dynamo code, and the code can't be proven cloud-ready without the infra), and the "Demoable Units" structure exists precisely to break one spec into vertical slices. The repo already treats #9 as a single MVP issue.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` keeps the interdependent deploy work in one reviewable place while the demoable units preserve incremental progress and let the strict-TDD Java slice be validated on its own terms (unit 1) separately from the IaC slices (units 2–3, validated by `fmt`/`validate`/`plan`, not coverage).
- `(B)` is defensible if you want the Java code shipped and merged before any infra exists, but the infra spec would immediately depend on the code spec, so the split mostly adds ceremony.
- The TDD-vs-IaC distinction `(B)` is chasing is already handled *within* one spec by `docs/TESTING.md`'s coverage split — unit 1 gets strict TDD, the infra units don't.

---

## 2. Definition of "done" — does #9 include a **live** cloud deploy, and can this environment do it?

The issue's acceptance criteria require live cloud verification ("Public URL serves PWA; golden path works end-to-end in cloud"). That needs a **real AWS account, the scoped `abreiss-ensemble-terraform` credentials, a real Claude API key, and actual cloud spend** — and someone/something that can run `terraform apply` and the deploy. What should the spec require as proof, and who performs the live run?

- [ x] (A) **Deploy-ready + validated is the spec's core; you (operator) perform the live deploy and capture the public-URL proof.** Implementation delivers: the Terraform module (`fmt`/`validate`/`plan` clean, Access Analyzer clean), the CI workflow (validated / dry-run), and the backend code (strict TDD, all tests green). The live `terraform apply` + `git push` deploy + golden-path screenshots on the public URL are done **by you** against the real account and attached as proof artifacts (since this agent environment has no AWS creds / can't incur Claude spend). This keeps the TDD/IaC honest and doesn't block on cloud access.
- [ ] (B) **Full live deploy performed within this workflow.** If real `abreiss-ensemble-terraform` AWS credentials **and** a Claude key are made available to the working environment, the agent runs `terraform apply`, pushes to trigger CI, and captures the live public-URL golden-path itself. (Tell me if creds will be provided and spend is acceptable.)
- [ ] (C) **No live deploy at all** — deliver deploy-ready IaC + CI + code with `plan`/`validate`/dry-run proofs only; the live apply/deploy/golden-path is explicitly deferred (e.g. to #10 deliverables) and noted as unverified.
- [ ] (E) Other (describe)

**Please also confirm:** (i) Do you have working `abreiss-ensemble-terraform` credentials from #16's bootstrap? (ii) Is `us-east-1` still the target? (iii) Is live Claude/App Runner spend acceptable for the demo?

**Current best-practice context:** Infra work is normally validated with `terraform validate`/`plan` + policy-lint in the pipeline, with the actual `apply`/deploy gated behind human-held credentials — especially in a shared account. Separating "the code/IaC is correct and deploy-ready" from "a human ran the privileged apply" is the standard, auditable split.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` matches how this repo already handled #16 (it deferred the live "`terraform apply` succeeds under this identity" gate to #9 as an operator-run, cross-issue check) and keeps the spec's testable core independent of secrets this environment doesn't hold.
- `(B)` gives the most complete single-run proof but only if you're comfortable handing real scoped creds + a Claude key to the working environment and paying for the live resources; say so and I'll write the ACs to expect a live capture in-workflow.
- `(C)` is the fallback if no account is reachable at all — it changes what "done" means, so pick it only if a live deploy genuinely can't happen for this issue.

---

## 3. Pipeline shape — does **CI run Terraform**, where does **state** live, and how is App Runner **deployed**?

These three are one coupled architectural choice. The #16 boundary pre-authorized the CI role for **ECR + App Runner deploy + one PassRole only** (not Terraform CRUD), which points strongly at "CI ships images; Terraform is an operator step."

- [x ] (A) **CI ships images; Terraform is an operator-run step; state in a remote S3 backend.** Per-push GitHub Actions (via GitHub→AWS **OIDC**) does: build image → push to ECR (immutable git-SHA tag) → `aws apprunner start-deployment`. Infra changes (`terraform apply`) are run **by the operator locally** as the `abreiss-ensemble-terraform` user. Terraform state lives in a **remote S3 backend** (`abreiss-ensemble-tfstate` bucket + a DynamoDB lock table, both within the scoped prefix) so state is durable and shareable. CI also runs `terraform fmt -check`/`validate` + Access Analyzer policy-lint as read-only checks.
- [ ] (B) **Same CI/operator split as (A), but Terraform state stays LOCAL** (git-ignored), matching the bootstrap module. Simplest; fine for a single operator, but no locking / not shareable, and state lives only on your machine.
- [ ] (C) **CI runs `terraform apply` too** (full GitOps). Requires widening the CI role beyond what #16's boundary currently allows (a scoped policy addition) and a remote backend. More "hands-off," but grants the CI principal broad infra mutation rights — larger blast radius.
- [ ] (E) Other (describe)

**Current best-practice context:** App Runner supports both auto-deploy-on-ECR-push and explicit `StartDeployment`; explicit deploy from CI with immutable image tags (git SHA, not `latest`) is more traceable and matches the exact permissions #16 already granted the CI role. Keeping privileged `terraform apply` out of CI (operator-run) is the lower-blast-radius default for a shared account; a remote S3+Dynamo backend is the standard way to make Terraform state durable and lockable.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` fits the permissions #16 **already** provisioned for the CI role (ECR + App Runner deploy + one PassRole) with zero policy widening, keeps the privileged `apply` in human hands, and gives durable/lockable state — the most robust option.
- `(B)` is the pragmatic minimum and totally viable for a solo demo; choose it if you'd rather not stand up a state bucket (note the chicken-and-egg: the state bucket must be created before it can hold state — I'll document a one-time backend bootstrap either way).
- `(C)` maximizes automation but requires widening the CI role's blast radius in a shared account, which runs against #16's whole containment posture — not recommended unless you specifically want CI to own `apply`.

---

## 4. Secrets — how do the Claude key + passcode get into App Runner **without** landing in Terraform state?

The app reads `ENSEMBLE_ANTHROPIC_API_KEY` and `ENSEMBLE_PASSCODE` (today from `.env`). In cloud these come from Secrets Manager. #16's posture is emphatic that **secret values must never enter Terraform state or git**. How should the flow work?

- [ x] (A) **Terraform creates empty secret *containers*; values are set out-of-band; App Runner injects them as runtime env vars via the instance role.** Terraform declares `aws_secretsmanager_secret` resources (`abreiss-ensemble-anthropic-key`, `abreiss-ensemble-passcode`, and `abreiss-ensemble-session-secret`) but **not** their values (or with `lifecycle { ignore_changes = [secret string] }`); you populate the actual values once via console/CLI. App Runner's runtime config references the secret ARNs and injects them as `ENSEMBLE_*` env vars, resolved through the instance role — **no app code change to how secrets are read**, and **no plaintext in state/git**.
- [ ] (B) **Terraform manages the secret values too** (passed in as sensitive `.tfvars`/env). Simpler single source of truth, but the plaintext lands in Terraform state — which is exactly what #16 warns against (mitigated only if state is a locked, encrypted, private backend).
- [ ] (E) Other (describe)

**Current best-practice context:** The AWS-recommended pattern is to manage the secret *resource* in IaC but keep the *value* out of state (create-then-populate, or `ignore_changes`), and have the compute platform inject it at runtime by ARN. App Runner natively supports sourcing runtime environment variables from Secrets Manager ARNs via the instance role.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` upholds #16's "no secrets in state/git" guarantee, needs **no change** to how the Java app reads `ENSEMBLE_*` env vars (only where they're sourced from), and uses App Runner's native secret injection — the cleanest, lowest-risk path.
- `(B)` is simpler to reason about but puts plaintext secrets in Terraform state, contradicting the security posture this project has deliberately built; only acceptable with a locked encrypted remote backend, and still weaker than `(A)`.
