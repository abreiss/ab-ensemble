# 09-validation-deploy-pipeline.md

Validation report for `09-spec-deploy-pipeline` against the spec, task list,
and proof artifacts. Account ids in cited evidence are redacted to
`123456789012` per repo convention.

## 1) Executive Summary

- **Overall: PASS** — now for the **full spec**, all four units. The first
  validation pass (2026-07-20 17:55) passed the testable core with tasks
  6.3/6.4 deferred-by-design (Resolved Decision Q2); this update closes them:
  the #9 work merged to `main`, the push-to-deploy pipeline has since produced
  **two consecutive green CI-driven deploys**, and the golden path is proven
  live against the public URL (API-level transcript + operator browser
  screenshot), with data verified in real S3/DynamoDB. No gate tripped.
- **Implementation Ready: Yes** — code, IaC, CI, docs, live apply, live
  pipeline, and live golden path all exist and verify.
- **Key metrics:**
  - Functional requirements verified: **24/24** (0 deferred, 0 failed,
    0 unknown).
  - Proof artifacts working: **6/6 proof files** exist and verify; every
    re-runnable check re-run fresh during validation passed.
  - Files changed vs expected: all changed files map to the task list's
    Relevant Files or carry explicit task linkage (see Gate D notes).
  - Fresh test evidence (2026-07-20, first pass): backend `./gradlew test
    jacocoTestReport -PskipFrontend --rerun-tasks` → **264 tests, 0 failures**;
    frontend `npm test -- --run` → **177 tests, 0 failures**; `terraform fmt
    -check` + `validate` → clean. Since then, CI re-ran both suites green on
    every push to `main` (runs `29792951459`, `29793550720`).
  - Fresh live evidence (2026-07-20, this update): Deploy runs `29792951440`
    and `29793550735` green end-to-end (OIDC → SHA-tagged ECR push →
    `update-service` → `RUNNING`); post-rollout
    `curl https://<app-runner-url>/api/health` → `200 {"status":"ok"}`;
    golden path executed against the public URL (see U4-FR3).

## 2) Coverage Matrix

### Functional Requirements — Unit 1 (backend cloud-readiness, strict TDD)

| Requirement | Status | Evidence |
| --- | --- | --- |
| U1-FR1 `awssdk:s3` under BOM 2.30.0 | Verified | `build.gradle` diff (commit `2327f5a`); `S3Client` resolves in tests |
| U1-FR2 `S3PhotoStorage` implements `PhotoStorage`, compression preserved, no S3 leak | Verified | `S3PhotoStorageTest` (mocked `S3Client`, asserts Put/Get/Delete requests + `ImageProcessor` compression); `09-proofs/09-task-01-proofs.md` |
| U1-FR3 Exactly-one-bean selector (`disk` default / `s3`) | Verified | `PhotoStorageSelectorTest` (ApplicationContextRunner, both branches, map size always 1); JaCoCo: `StorageConfig` 100% line, `PhotoProperties` 100% line + 100% branch |
| U1-FR4 `DynamoDbConfig` cloud switch (blank endpoint → no override + default creds; present → local behavior) | Verified | `DynamoDbConfigTest` both branches; JaCoCo: `DynamoDbConfig` **100% line (12/12), 100% branch (6/6)** — fresh run this validation |
| U1-FR5 Config keys + relaxed-binding env vars, local defaults intact | Verified | `application.yml` (`${ENSEMBLE_PHOTOS_BACKEND:disk}`, `${ENSEMBLE_DYNAMODB_ENDPOINT:http://localhost:8000}`); `.env.example` documents the vars; existing tests unchanged and green |
| U1-FR6 No change to Claude-key/passcode reads | Verified | No diff in key/passcode read paths; cloud values arrive as the same `ENSEMBLE_*` env vars via App Runner secret injection |

### Functional Requirements — Unit 2 (Terraform deploy module)

| Requirement | Status | Evidence |
| --- | --- | --- |
| U2-FR1 Self-contained `terraform/deploy/` root module, aws ~> 5.0, ≥1.11, prefix + region vars, no hard-coded account id | Verified | `versions.tf`/`providers.tf`/`variables.tf`; fresh grep: no 12-digit id in `terraform/deploy/*.tf` |
| U2-FR2 Remote S3 backend, S3-native locking (`use_lockfile`, no lock table), reproducible state-bucket bootstrap | Verified | `versions.tf` backend block; `create-state-bucket.sh` + `terraform/deploy/README.md`; live init/apply in `09-proofs/09-task-06-proofs.md` |
| U2-FR3 Photos bucket (public-access-block, SSE) + `abreiss-ensemble-items` table (`itemId` PK, on-demand) | Verified | `data_stores.tf`; operator plan/apply evidence in task-02/06 proofs |
| U2-FR4 ECR repo: scan-on-push, immutable tags, lifecycle policy | Verified | `ecr.tf`; task-03 proofs |
| U2-FR5 App Runner service: port 8080, `/api/health`, min 1/max 2, non-secret env literals, secrets by ARN | Verified | `apprunner.tf` (incl. `SPRING_PROFILES_ACTIVE=cloud`); live `describe-service` shows 3 secret refs by name, values never shown (task-06 proofs) |
| U2-FR6 Secret containers with no values in state | Verified | `secrets.tf` has no `secret_string`/version resource at all (strictest reading of the FR's either/or); task-03 proofs grep |
| U2-FR7 Instance role: least-privilege, prefix-scoped, boundary-attached (+ ECR access role) | Verified | `iam.tf`; rendered `policies/abreiss-ensemble-instance-runtime.json`; boundary on every role; task-04 proofs |
| U2-FR8 OIDC CI role: pre-existing provider, repo/ref-scoped trust, exactly ECR push + App Runner deploy + one scoped PassRole | Verified | `iam.tf` + `policies/abreiss-ensemble-ci{,-trust}.json`; PassRole-condition removal documented (`docs/AWS_ACCESS.md`) — resource scope + trust policies remain the guardrails, no widening |
| U2-FR9 Outputs (service URL, ECR URL, names, CI role ARN), no secrets/account id | Verified | `outputs.tf`; live apply outputs in task-06 proofs (identifiers only) |

### Functional Requirements — Unit 3 (CI pipeline)

| Requirement | Status | Evidence |
| --- | --- | --- |
| U3-FR1 `deploy.yml`: push-to-main, `id-token: write`, OIDC role assumption, build, ECR push under immutable git-SHA tag, rollout + verify | Verified | `.github/workflows/deploy.yml` (uses `update-service`, a documented deviation from `start-deployment` forced by the pinned-`:latest`/IMMUTABLE design — task 5.3 note + in-file comments) |
| U3-FR2 No `terraform apply` in CI; no long-lived AWS creds or app secrets in workflows | Verified | Fresh grep of `.github/workflows/`: no `AKIA`, no `aws_secret_access_key`, no `sk-ant`; no terraform apply step |
| U3-FR3 Standing checks: backend + frontend tests, `fmt -check`/`validate`, Access Analyzer policy lint | Verified | `.github/workflows/ci.yml`; lint steps `continue-on-error` due to the documented structural permission gap (see Issues) |
| U3-FR4 Traceable tags / rollback by earlier tag | Verified | `sha-<git-sha>` tagging in `deploy.yml`; rollback procedure in README runbook |

### Functional Requirements — Unit 4 (live deploy + runbook, operator-run)

| Requirement | Status | Evidence |
| --- | --- | --- |
| U4-FR1 One-time operator sequence (state bucket, init + `use_lockfile`, apply as scoped identity, secrets out-of-band) | Verified | `09-proofs/09-task-06-proofs.md`: caller-identity artifact, apply transcript, `AWSCURRENT` on all three secrets, converging plan (`No changes`) |
| U4-FR2 Push to `main` deploys via pipeline; public URL serves PWA + health 200 | Verified | Deploy runs `29792951440` + `29793550735` green end-to-end (OIDC assume → `sha-<git-sha>` ECR push → `update-service` → `RUNNING`); redacted log excerpt + post-rollout health `200` in task-06 proofs. Two surfaced defects fixed under task 6.3 with full linkage: unset repo variables (runbook step) and the enterprise **ID-stamped OIDC subject** the trust policy had to match (commits `e6de487`, `b39f537`, `00eff1f`) |
| U4-FR3 Golden path end-to-end in cloud (add → tag → vibe → grounded outfit → pushback), photos in S3, items in DynamoDB, secrets from Secrets Manager | Verified | Task-06 proofs "Golden path — API level": auth via Secrets-Manager passcode, 4 garments Haiku-tagged (incl. the editable-fallback `400` guardrail firing live), grounded outfit, pushback re-pick swapping exactly one piece, wear write (`wornCount` 0→1); `aws s3api list-objects-v2` shows the 4 photo JPEGs, `aws dynamodb get-item` shows the full item model incl. wear-history; operator browser screenshot of the deployed stylist flow embedded (`assets/09-task-06-stylist-datenight.png`) |
| U4-FR4 Deferred #16 gate: apply under only the scoped identity; gaps closed narrowly | Verified | Gate write-up in task-06 proofs: final apply + `No changes` plan under scoped user only, diagnostic policy deleted first; gaps closed as `abreiss-ensemble-terraform-ext` (second managed policy, resource-scoped) + PassRole-condition removal — never `*`, documented in `docs/AWS_ACCESS.md` |
| U4-FR5 Deploy runbook (README + DEVELOPMENT, ARCHITECTURE narrative) | Verified | README "Deploy to AWS (App Runner)" (all six required elements incl. rollback-by-earlier-SHA); `docs/DEVELOPMENT.md` "Deploying (operator-run)"; `docs/ARCHITECTURE.md` "Deployment (shipped — issue #9)"; commit `55a2a59` |

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Coverage split (`docs/TESTING.md`) | Verified | Unit 1 strict TDD (RED→GREEN sub-tasks 1.3/1.6/1.7, `CloudProfileConfigTest` RED→GREEN); Units 2–4 validated via fmt/validate/plan/lint/live smoke, not coverage-gated |
| Strict-TDD coverage gate | Verified | Fresh JaCoCo: `DynamoDbConfig` 100%/100%, `PhotoProperties` 100%/100%, `S3PhotoStorage` 100% line, `StorageConfig` 100% line, `LocalDiskPhotoStorage` 100%/100% — exceeds ≥90% line / 100% branch |
| Interface discipline | Verified | `S3PhotoStorage` depends only on `S3Client`/`PhotoProperties`/`ImageProcessor`; S3 types stay inside the class |
| Module isolation | Verified with note | `terraform/deploy/` never modified by-hand into bootstrap; the `terraform/bootstrap/` changes that did land are exactly the task-6.5-prescribed gap closures (admin-applied, documented) — see Gate D |
| Secrets never committed | Verified | Fresh scans: no tfstate/credential files tracked, workflows clean, proofs redacted; pre-commit secret scan ran on every commit |
| Conventional commits, one per unit | Verified | `2327f5a` feat(storage), `32ab2bb`/`6925535`/`e2f8618` feat(infra), `dd9b4dd` ci, `cd1ae5c` fix(infra), `55a2a59` docs — one per demoable unit |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| 1.0 | `09-proofs/09-task-01-proofs.md` (tests, JaCoCo, diffs) | Verified | Re-run fresh: 264 backend tests green; per-class coverage above |
| 2.0 | `09-proofs/09-task-02-proofs.md` (fmt/validate, backend files, bootstrap step) | Verified | Re-run fresh: `fmt -check` + `validate` clean |
| 3.0 | `09-proofs/09-task-03-proofs.md` (plan excerpt, HCL, no-secret grep) | Verified | File exists; HCL cross-checked; fresh no-account-id grep clean |
| 4.0 | `09-proofs/09-task-04-proofs.md` (policy JSON, boundary, trust policy) | Verified | Rendered policies exist under `terraform/deploy/policies/`; lint gap documented (see Issues) |
| 5.0 | `09-proofs/09-task-05-proofs.md` (workflow files, secret scan) | Verified | Both workflows exist as described; fresh workflow scan clean |
| 6.0 | `09-proofs/09-task-06-proofs.md` (apply transcript, health, secrets, 6.5 gate, 6.6 runbook, 6.3 pipeline run, 6.4 golden path + screenshot) | Verified (all of 6.1–6.6) | Live health re-checked post-rollout (`200 {"status":"ok"}`); 6.3/6.4 sections appended with the pipeline log excerpt, golden-path transcript, S3/DynamoDB reads, and the embedded screenshot (file confirmed present in `09-proofs/assets/`) |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| MEDIUM | Access Analyzer lint has never produced a clean run under the scoped identity — `access-analyzer:ValidatePolicy` is denied for both the scoped user and the boundary-capped CI role (structural: fixing it means widening the boundary itself, a `terraform/bootstrap/` change out of #9's scope). CI runs the lint `continue-on-error`; the exact operator commands for a broader session are in `terraform/deploy/policies/README.md`. | Success Metric 2's lint clause is satisfied via its documented-findings branch, not a green lint run | Operator: run the two `validate-policy` commands under an admin session once and attach the output; longer-term, consider a deliberate, narrow boundary addition in `terraform/bootstrap/` |
| LOW (resolved) | ~~Tasks 6.3/6.4 open until post-merge~~ — closed in this update: pipeline runs `29792951440`/`29793550735` green, golden-path evidence + screenshot appended, 6.3/6.4/6.0 all `[x]` | None | No action; kept for the audit trail |

No CRITICAL or HIGH issues. Gate D notes: changed files outside the Relevant
Files list are all linked — `application-cloud.yml` + `CloudProfileConfigTest`
(task 6.1's cloud-profile fix, TDD'd), `terraform/bootstrap/*` (task 6.5's
prescribed gap closures, admin-applied, fully narrated in
`docs/AWS_ACCESS.md`), small test adjustments (`PhotoPropertiesTest`,
`ImageProcessorTest`, `LocalDiskPhotoStorageTest`, `TaggingEvalRunner` — ripple
of task 1.2's `PhotoProperties` extension), `.gitignore` (state/creds ignore
rules), `.terraform.lock.hcl` (standard provider pinning).

Gate D notes for this update's post-merge commits (all core changes linked to
task 6.3 in their commit messages): `.github/workflows/ci.yml`
(`continue-on-error` on the policy-lint credentials step, `e6de487`);
`terraform/deploy/iam.tf` + `variables.tf` + the
`policies/abreiss-ensemble-ci-trust.json` review copy (ID-stamped OIDC subject
fix, `00eff1f`, applied via a one-resource in-place `terraform apply` under the
scoped identity with a converging plan afterwards); the temporary
`debug-oidc.yml` diagnostic was added (`b39f537`) and deleted (`00eff1f`) —
net-zero. Proof/doc commits `bf6ab04`/`0610d6a` are supporting files.

## 4) Evidence Appendix

- **Commits analyzed** (`origin/main..HEAD`, first pass): `2327f5a` (task
  1.0), `32ab2bb` (2.0), `6925535` (3.0), `e2f8618` (4.0), `dd9b4dd` (5.0),
  `cd1ae5c` (6.1 live-deploy fixes: amd64 seed, PassRole condition, cloud
  profile), `55a2a59` (6.5 write-up + 6.6 runbook).
- **Post-merge commits analyzed (this update):** `e6de487` (6.3: policy-lint
  PR-event guard), `b39f537` (6.3: temporary OIDC diagnostic), `00eff1f` (6.3:
  ID-stamped OIDC subject trust fix + diagnostic removal), `bf6ab04` (6.3/6.4
  proof evidence), `0610d6a` (6.4 screenshot embed, 6.0 complete).
- **Fresh commands run for this update (2026-07-20 evening):**
  - `gh run watch` on Deploy `29792951440` and `29793550735` → both green
    through Verify rollout (`RUNNING`); CI `29792951459`/`29793550720` green.
  - `curl https://<app-runner-url>/api/health` post-rollout →
    `200 {"status":"ok"}`.
  - Golden-path API transcript against the public URL (auth → tag ×4 →
    create ×4 → style → pushback re-pick → worn) — summarized in task-06
    proofs; session token and passcode never captured.
  - `aws s3api list-objects-v2` (photos bucket) → exactly 4 item JPEGs;
    `aws dynamodb get-item` (tee item) → full model incl.
    `wornCount 1`/`lastWorn`.
  - `ls 09-proofs/assets/` → `09-task-06-stylist-datenight.png` present and
    embedded in the proof doc.
- **Fresh commands run for this validation (2026-07-20):**
  - `./gradlew test jacocoTestReport -PskipFrontend --rerun-tasks` → BUILD
    SUCCESSFUL; 264 tests, 0 failures/errors/skips.
  - JaCoCo XML per-class extraction → coverage table above.
  - `cd frontend && npm test -- --run` → 18 files, 177 tests, all passed.
  - `terraform -chdir=terraform/deploy fmt -check` → clean;
    `terraform -chdir=terraform/deploy validate` → "Success! The configuration
    is valid" (the only warning is a local CLI dev_override for an unrelated
    provider on the operator machine, not a module finding).
  - `grep -rniE 'AKIA|aws_secret_access_key|sk-ant' .github/workflows/` → no
    matches; `git ls-files | grep -E '\.tfstate|^\.env$|\.pem$|credentials'` →
    no matches; `grep -rnE '[0-9]{12}' terraform/deploy/*.tf` → no matches.
  - `curl https://<app-runner-url>/api/health` → `{"status":"ok"}`, HTTP 200
    (live, public, right now — not a replayed artifact).
- **Proof files:** all six `09-proofs/09-task-0N-proofs.md` files exist,
  front-load context per the proof-artifact standard, and contain no
  credentials (account id redacted to `123456789012` throughout).

**Validation Completed:** 2026-07-20 17:55 local (testable core);
2026-07-20 ~18:50 local (full-spec update closing 6.3/6.4)
**Validation Performed By:** Claude (Fable 5)
