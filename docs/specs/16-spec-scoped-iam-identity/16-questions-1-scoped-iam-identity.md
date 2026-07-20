# 16 Questions Round 1 - Scoped IAM Identity for Claude/Terraform

Please answer each question below (check one or more options, or add your own notes). Feel free to add additional context under any question. When you're done, save the file and tell me to continue.

These four decisions materially change the spec (deliverable shape, acceptance criteria, and how we prove it works), so I'm confirming them before writing the spec rather than guessing.

---

## 1. Credential mechanism for local Terraform runs

The issue says "a dedicated IAM user (**or assumable role**) ... access keys go into Claude's local environment only." How should the identity Claude/Terraform authenticates as actually work?

- [x] (A) **Dedicated IAM user with long-lived access keys.** Access key + secret live only in Claude's local env (e.g. `.env` / `~/.aws/credentials`, git-ignored). Simplest; matches the issue's "access keys go into Claude's local environment" wording directly.
- [ ] (B) **Base IAM user + a scoped role it assumes** (`sts:AssumeRole` → short-lived STS creds). The scoped permissions live on the role; the user only has permission to assume it. Slightly more moving parts, but the powerful permissions are only ever held as temporary credentials.
- [ ] (C) **AWS IAM Identity Center (SSO) profile** that maps to the scoped permission set. No stored keys at all; `aws sso login` mints short-lived creds. Best long-term, but requires Identity Center to already be set up in the account.
- [ ] (E) Other (describe)

**Current best-practice context:** Current AWS guidance discourages long-lived IAM user access keys in favor of temporary credentials (assume-role or Identity Center), because static keys are the most common credential-leak vector. That said, the permissions boundary + prefix scoping in this issue caps the blast radius either way, and for a single developer's local Terraform runs an IAM user with rotated keys is a widely accepted pragmatic choice.

**Recommended answer(s):** [(A)] — with (B) as the easy upgrade if you'd rather not hold static keys.

**Why these are recommended:**

- `(A)` matches the issue text verbatim ("access keys go into Claude's local environment only") and is the least-setup path for a single-user demo; the boundary + `abreiss-ensemble-*` scoping already bound the damage a leaked key could do.
- `(B)` is the security-purist option and only adds one indirection (assume-role); worth it if you specifically want to avoid static long-lived keys. The spec can be written for (A) and note (B) as a drop-in variant.
- `(C)` is the strongest but assumes Identity Center is already configured in this shared account, which the issue doesn't mention — likely out of reach for this task.

---

## 2. How is the scoped identity itself provisioned? (the bootstrap / chicken-and-egg)

The scoped identity **cannot create itself** — something with broader privilege has to create the boundary policy, the scoped policy, and the user/role once. The issue says to document it "so it's reviewable, not a one-off console click (e.g. `terraform/bootstrap/` or `docs/AWS_ACCESS.md`)." What's the deliverable?

- [ x] (A) **Terraform bootstrap module** (`terraform/bootstrap/`) — committed IaC that an account admin applies **once** with elevated creds to create the boundary policy + scoped policy + IAM user/role. Reviewable as code; consistent with #9 being Terraform. **Plus** a short `docs/AWS_ACCESS.md` explaining what it creates and how to run it.
- [ ] (B) **Docs only** (`docs/AWS_ACCESS.md`) — the policy JSON + boundary JSON committed as files, with step-by-step CLI/console instructions an admin follows manually. No Terraform for the bootstrap itself.
- [ ] (C) **Raw JSON policy files committed** (e.g. `terraform/bootstrap/policies/*.json`) referenced by both the docs and later reused by #9, but applied manually now.
- [ ] (E) Other (describe)

**Current best-practice context:** The issue explicitly frames the goal as "reviewable, not a one-off console click," and #9 is already a Terraform effort. Keeping the bootstrap as Terraform means the exact policy that will be enforced is version-controlled and diffable, and it can live in the same repo as the app it protects.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` best satisfies "reviewable, not a one-off console click," keeps the enforced policy diffable in git, and matches the repo's IaC direction for #9. The one-time admin apply is unavoidable regardless of option (someone privileged must create the first identity).
- `(B)` is acceptable but drifts from code-as-source-of-truth and is easier to apply inconsistently.
- `(C)` is a middle ground but splits the source of truth between raw JSON and whatever applies it.

---

## 3. How strict is "scoped," given AWS actions that can't be resource-restricted?

Acceptance criterion #1 says the policy should have "**no path to creating/reading/deleting any resource that doesn't start with `abreiss-ensemble-`**." Some required AWS actions **physically cannot** be scoped to a resource name — AWS only accepts `Resource: "*"` for them. Confirmed examples: `s3:ListAllMyBuckets`, `ecr:GetAuthorizationToken`, and most App Runner **create** actions (`apprunner:CreateService` etc. don't support resource-level ARNs). So AC#1 can't be met literally for those. How should the spec reconcile this?

- [ x] (A) **Prefix/ARN conditions everywhere the service supports them; unavoidable account-level actions allowed with `*` and documented as accepted exceptions.** These exceptions are read/list/token/create-without-name actions that don't read or mutate anyone else's *resource contents* (e.g. listing bucket names, getting an ECR auth token, creating an App Runner service that we then name/tag `abreiss-ensemble-*`). Add `aws:ResourceAccount` / tag conditions as defense-in-depth where possible. AC#1 is reworded to "no resource-level create/read/write/delete on non-prefixed resources; the small set of AWS-unavoidable `*` actions is enumerated and justified."
- [ ] (B) **Hard-line literal AC#1** — attempt to deny every non-prefixed action, including via `NotResource`/explicit denies, even if it means some services (App Runner create) can't be used by this identity. (May block #9's `terraform apply`.)
- [ ] (E) Other (describe)

**Current best-practice context:** The AWS Service Authorization Reference marks which actions support resource-level permissions; `s3:ListAllMyBuckets`, `ecr:GetAuthorizationToken`, and App Runner create actions are documented as **not** supporting ARN conditions and must use `"*"`. This is a known, unavoidable AWS constraint, not a policy-design shortcut. The standard mitigation is to enumerate and justify the wildcard actions and confirm they don't expose or mutate other resources' contents.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` is the only option that both scopes everything AWS *allows* to be scoped **and** lets #9's `terraform apply` actually succeed. The wildcard actions it permits are non-destructive (list names / get token / create-then-name-and-tag), so the isolation guarantee — "can't touch anyone else's *stuff*" — still holds in substance.
- `(B)` honors the literal wording but is infeasible: denying App Runner create would make the identity unable to provision the very service #9 needs, defeating the purpose.
- Choosing `(A)` means I'll reword AC#1 in the spec to the substance-preserving form above and list the exact `*` actions with a one-line justification each. Please confirm you're OK with that rewording.

---

## 4. Validation strategy — #9 doesn't exist yet, and we need a live account to prove denials

Two acceptance criteria require live AWS: "a test action against a non-prefixed resource is denied," "`iam:CreateRole` without the boundary is denied," and "**#9's `terraform apply` runs successfully using only this identity**." But **#9 is still open/unimplemented**, and proving denials needs real credentials. How do we validate #16?

- [ x] (A) **Validate now with what's provable; defer the #9 end-to-end gate.** Prove the policy with (1) **IAM Policy Simulator** results for the allow/deny matrix, and (2) **live AWS CLI allow/deny tests** using the scoped identity against a throwaway `abreiss-ensemble-*` resource vs an unrelated one (e.g. `aws s3 ls` on someone else's bucket → `AccessDenied`), and (3) a `CreateRole`-without-boundary attempt → denied. The "#9 `terraform apply` succeeds under this identity" criterion is recorded as a **cross-issue gate satisfied during #9's work** (since #16 blocks #9). Requires a real account + one-time admin bootstrap access to be available.
- [ ] (B) **Sequence part of #9 first** — stand up enough of #9's Terraform to run a real `terraform apply` under the scoped identity before closing #16, so all ACs (including the apply) are proven now.
- [ ] (C) **No live AWS account available right now** — deliver policy-as-code + Policy Simulator / static validation only; all live CLI/apply proofs are explicitly deferred and noted as unverified in the spec.
- [ ] (E) Other (describe)

**Current best-practice context:** The IAM Policy Simulator evaluates identity policies (including permissions boundaries) offline and is the standard way to prove an allow/deny matrix without touching live resources; pairing it with a few real CLI calls is the usual belt-and-suspenders for a policy like this.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` proves everything #16 can prove on its own (the scoping and boundary enforcement are entirely #16's responsibility) while honestly deferring the one criterion that depends on #9's code existing. It keeps #16 shippable and unblocks #9, which is the stated point of this issue.
- `(B)` gives the most complete proof but couples #16's completion to building part of #9, expanding scope beyond this issue.
- `(C)` is the fallback only if no AWS account/credentials are reachable at all — please tell me if that's the case, since it changes what "done" means for this spec.

**Please also confirm:** do you have access to a real AWS account where an admin (you, or someone) can run the one-time bootstrap, and is `us-east-1` the target region (matches `application.yml`)? If not, note it here.
