# 14-audit-user-accounts.md

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2** (advisory; non-blocking)

All four REQUIRED gates pass. Every testable functional requirement maps to at
least one planned test artifact; proof artifacts are observable, reproducible,
scope-linked, and sanitized; repository standards are consistent across 6 read
sources; and every spec open question is resolved or carries an explicit
assumption. Two advisory FLAGs are recorded for the implementer.

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every testable FR → ≥1 planned test; docs/IaC FRs → verifiable Diff + `terraform validate` (exempt from unit tests per `docs/TESTING.md`) | `## Tasks` 1.0–4.0 |
| Proof artifact verifiability | PASS | Exact test names, `curl`/gradle/terraform commands, throwaway values; no secrets | all `#.0 Proof Artifact(s)` |
| Repository standards consistency | PASS | 6 sources read (incl. `AGENTS.md` + root `README.md`); no conflicts | Standards Evidence Table |
| Open question resolution | PASS | Q1 resolved (auto-login → task reorder); Q2/Q3 explicit assumptions | Spec §Open Questions |
| Regression-risk blind spots | FLAG | Token-format + `AuthRequest` change has a regression surface | FLAG 1 |
| Non-goal leakage | FLAG | `/api/me` `findByUserId` scan — justified, but note the scale path | FLAG 2 |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD (RED→GREEN→REFACTOR) for backend domain; ≥90% line + 100% branch on critical logic; layered arch; DynamoDB Enhanced Client (no JPA); DTOs at boundary; secrets from env only | none |
| `README.md` (root) | yes | Passcode gate: `POST /api/auth` + `GET /api/health` open; `X-Ensemble-Session` header / `?token=`; `sessionStorage`; 12h TTL; deploy secrets by ARN | none |
| `docs/TESTING.md` | yes | Coverage split — backend domain strict TDD; controllers test contracts/errors; frontend meaningful logic only; repo round-trips via DynamoDB Local/TestContainers; mock Claude | none |
| `.pre-commit-config.yaml` | yes | Fast backend + frontend tests, eslint, secret scans (Anthropic/AWS key patterns); commits must pass | none (no password/passcode pygrep rule exists — scan is key-pattern based) |
| `.github/workflows/ci.yml` | yes | CI: backend + frontend tests, `terraform fmt -check`/`validate`; no `apply`; OIDC only | none |
| `build.gradle` | yes | Spring Boot 4.1.0 / Java 21; `spring-boot-starter-validation` present; AWS SDK v2 enhanced; JaCoCo; `-PskipFrontend`; new `spring-security-crypto` (BOM-managed) | none |

## Findings

### REQUIRED Failures

None.

### FLAG Findings

1. **Regression surface of the token-format + `AuthRequest` change.**
   - Risk: changing `SessionTokenService.issue()/verify()` signatures and the
     `AuthRequest` body from `{passcode}` to `{email,password}` breaks every
     current caller and the existing full-context auth tests. Success Metric #7
     ("no regressions") requires the *pre-existing* suites (daily-cap, stylist,
     wardrobe) to stay green — not just the new tests.
   - Mitigation already planned: Tasks 2.9 / 3.8 / 4.8 run the full
     `./gradlew test -PskipFrontend` + frontend suite. The code map found the
     only `issue()`/`verify()` callers are `AuthController` + `SessionAuthFilter`,
     and the only `{passcode}` poster is frontend `auth.ts` — all rewritten here.
   - Suggested addition: an explicit "pre-existing suites unchanged + grep for
     stray `issue()`/`verify()`/`{passcode}` callers" check in Task 2.9 before merge.

2. **`/api/me` `findByUserId` full-table scan (Non-goal boundary).**
   - Risk: `/api/me`'s `{userId,email}` contract needs a `userId → row` lookup,
     but the table is `email`-keyed with no GSI (Q2-A). The plan uses a `scan`
     (documented in the tasks file) — correct and O(n)-cheap at demo scale
     (~1–20 users), but unindexed and a mild refinement of Q2-A's "no
     `userId → row` lookup needed" note. It does **not** leak #15's data-scoping
     scope (identity only).
   - Suggested addition: when Task 4.7 updates `docs/ARCHITECTURE.md`, note that a
     `userId` GSI is the scale path if a future issue needs frequent `userId`
     lookups. Non-blocking for the demo.

## Chain-of-Verification

- Do all REQUIRED gates pass with explicit evidence? **Yes** — traceability
  verified FR-by-FR against `14-spec-user-accounts.md` (Units 1–4); proof
  artifacts checked for concrete commands/test names; standards cross-checked
  against 6 source files; open questions checked against spec §Open Questions.
- Inconsistencies resolved: the sole non-testable FRs (docs + Terraform) are
  covered by Diff + `terraform validate` proofs, consistent with the
  `docs/TESTING.md` infra/docs exemption — not a traceability failure.
- Final synthesis: **PASS, 0 REQUIRED failures, 2 advisory FLAGs.** Planning is
  ready for the implementation phase. The FLAGs are recommendations, not gates.
