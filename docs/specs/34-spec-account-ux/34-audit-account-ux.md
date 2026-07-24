# 34-audit-account-ux.md

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2**

All four REQUIRED planning-audit gates pass on the first run. Two advisory FLAGs are recorded for a decision; neither blocks the implementation phase.

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every functional requirement maps to ≥1 planned test, or (for docs/migration) a Diff/Doc-Log proof per the project's testing split | `## Tasks 1.0–4.0` proof artifacts |
| Proof artifact verifiability | PASS | Artifacts name exact test classes/methods, curl commands, and JaCoCo report; observable + reproducible + sanitized (synthetic creds) | all `#### N.0 Proof Artifact(s)` |
| Repository standards consistency | PASS | 8 guideline sources read incl. `AGENTS.md` + root `README.md`; the one gap (no enforced JaCoCo threshold) has documented precedence | Standards Evidence Table |
| Open question resolution | PASS | Spec's 3 open questions are non-blocking, each with an explicit stated default | `34-spec` Open Questions |
| Regression-risk blind spots | FLAG | Cloud seed env var and destructive-migration safety rest on non-test evidence | FLAG 1, FLAG 2 |
| Non-goal leakage | PASS | Tasks stay within goals; cloud/Terraform change kept out-of-band as the spec directs | — |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD (RED→GREEN→REFACTOR) on backend domain; ≥90% line + 100% branch on critical logic; DTOs at boundary; mock Claude/no live network; conventional commits + `Co-Authored-By` | none |
| `README.md` | yes | `./gradlew test -PskipFrontend` + `jacocoTestReport`; `npm test -- --run` / `npm run lint`; accounts via `/api/auth` + `/api/accounts`; seed needs `ENSEMBLE_SEED_EMAIL`+`ENSEMBLE_SEED_PASSWORD` | none |
| `docs/TESTING.md` | yes | Coverage split: strict TDD on domain, contracts on controllers, meaningful-logic-only on frontend, infra/docs not unit-tested; DynamoDB Local via TestContainers | none |
| `.pre-commit-config.yaml` | yes | `backend-tests`, `frontend-tests`, `frontend-lint`, secret scans (`block-anthropic-keys`, `block-aws-keys`, `detect-private-key`) | none |
| `.github/workflows/ci.yml` | yes | Java 21 backend tests, Node 20 frontend tests, terraform fmt/validate, policy-lint; no JaCoCo % gate in CI | none |
| `build.gradle` | yes | Spring Boot 4.1.0, Java 21, `jacocoTestReport` report-only (no `jacocoTestCoverageVerification`); no Checkstyle/SpotBugs | Gap: coverage bar is a report, not an enforced gate |
| `frontend/package.json` | yes | Vitest ^3, RTL ^16, React 19, Vite 6; scripts `test`/`lint`/`build` | none |
| `.env.example` | yes | Documents `ENSEMBLE_SEED_EMAIL`/`ENSEMBLE_SEED_PASSWORD`, `ENSEMBLE_PASSCODE`, `ENSEMBLE_SESSION_SECRET` | none |
| `CONTRIBUTING.md` | not found | Contributor guidance lives in `AGENTS.md` instead | absence noted |
| `.github/pull_request_template.md` | not found | No PR template configured | absence noted |

**Standards precedence for the JaCoCo gap:** `AGENTS.md`/`docs/TESTING.md` mandate ≥90% line + 100% branch on critical logic, but neither `build.gradle` nor CI enforces a threshold. Precedence: treat the documented bar as authoritative and verify it by reading the generated `jacocoTestReport`, since no automated gate exists. This is reflected in the 1.0 coverage proof artifact and the tasks Notes.

## FLAG Findings

1. **Cloud seed env var goes stale (regression-risk).**
   - Risk: `terraform/deploy/apprunner.tf` injects `ENSEMBLE_SEED_EMAIL` (from a `seed_email` secret) into App Runner. After the app is changed to read `ENSEMBLE_SEED_USERNAME`, the deployed cloud seed silently no-ops until the operator updates Terraform. The spec's docs-update list (Unit 2) omits Terraform, and cloud migration is explicitly out-of-band operator work.
   - Suggested remediation: keep the Terraform change out-of-band (do not expand code scope), but explicitly document the `ENSEMBLE_SEED_EMAIL` → `ENSEMBLE_SEED_USERNAME` Terraform/secret rename as a required operator follow-up in the migration note. Task 2.6 already carries this conditionally — confirm to promote it to unconditional.

2. **Destructive-migration safety rests on inspection, not a test (regression-risk).**
   - Risk: the "no automatic table drop / no enumerate-and-delete against a shared store" safety property (Resolved Decision D2, and the user's standing cleanup rule) is validated by code inspection + a documented procedure, not an automated regression guard.
   - Suggested remediation: accepted per the project's testing split (migration/infra is not unit-tested) — the Doc/Log proof + a grep confirming no drop/delete code was added is the appropriate evidence. No task change required unless you want a belt-and-suspenders assertion.

## Chain-of-Verification

- Self-question — do all REQUIRED gates pass with explicit evidence? Yes; each is tied to a named proof artifact or standards source above.
- Fact-check — traceability re-checked FR-by-FR against `34-tasks` proof artifacts; standards re-checked against the read sources; open questions re-checked against the spec (all carry explicit defaults). No unsupported findings.
- Result — audit is publishable as PASS; the 2 FLAGs are advisory and do not gate handoff.
