# 15-audit-per-user-data-scoping.md

Planning audit for [15-tasks-per-user-data-scoping.md](./15-tasks-per-user-data-scoping.md)
against [15-spec-per-user-data-scoping.md](./15-spec-per-user-data-scoping.md). Runs 1–2.

## Executive Summary

- Overall Status: **PASS** (Run 2 — all REQUIRED gates pass after approved remediation)
- Required Gate Failures: **0** (was 1 in Run 1)
- Flagged Risks: **0 open** (both Run 1 flags remediated)

## Gateboard (Run 2 — current)

| Gate | Status | Note | Fix target (Run 1) |
| --- | --- | --- | --- |
| Requirement-to-test traceability | **PASS** | `create_stampsCallerUserId` added to 2.1/2.5, wired in 2.3/2.6 | `## Tasks > 2.0` |
| Proof artifact verifiability | PASS | — | — |
| Repository standards consistency | PASS | — | — |
| Open question resolution | PASS | — | — |
| Regression-risk blind spots | PASS | grep-guard in 4.5 + initializer WARN in 1.10; missing-photo case in 5.5 | `## Tasks > 4.0`,`1.0`,`5.0` |
| Non-goal leakage | PASS | — | — |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD backend domain; layered controllers→services→repos; DynamoDB Enhanced Client single-item; DTOs at boundary; grounding = reject→feedback→one retry; 100% branch on grounding/id-validation | none |
| `docs/TESTING.md` | yes | Coverage split (≥90% line domain, 100% branch critical); mock Claude; DynamoDB Local via TestContainers; IaC validated not unit-tested; AAA + behavior names | none |
| `docs/ARCHITECTURE.md` | yes | `userId` GSI is the documented scale path; `PhotoStorage` interface; stylist text-tags-only; cloud profile disables auto-create | none |
| `README.md` (root) | yes | `@CurrentUserId`/`X-Ensemble-Session` (+`?token=`); unknown id → 404; local `ensemble-items` auto-create; two-account CLI conventions | none |
| `.pre-commit-config.yaml` | yes | Fast gates: `./gradlew test -PskipFrontend`, vitest+eslint, Anthropic/AWS key scans | none |
| `.github/workflows/ci.yml` | yes | Backend+frontend tests; `terraform fmt`/`validate`; Access Analyzer lint on rendered `policies/*.json` | none |

## Findings

### REQUIRED Failures

1. **The FR "stamp `userId` on every write path (`save`/`create`) so no owned row is ever written without an owner" (Unit 1) has no test that directly asserts the persisted owner.**
   - Missing item: `WardrobeServiceTest` and `OutfitServiceTest` currently assert *scoped reads* (`list_returnsOnlyCallersItems`, `find_otherUsersItem_throwsNotFound`) with a **stubbed** `findByUserId`, so a `create` that forgot to set `userId` would still pass. No test captures the saved entity and asserts `getUserId()` equals the caller.
   - File section to edit: `## Tasks > 2.0` task 2.3 (wardrobe) and task 2.6 (outfit).
   - Acceptance condition: add an explicit assertion — capture the `Item`/`SavedOutfit` passed to `repository.save(...)` (e.g. `ArgumentCaptor`) in the `create` test and assert its `userId` equals the caller's id. Name it e.g. `create_stampsCallerUserId`.

## FLAG Findings

1. **Intermediate unscoped read path + local GSI skip.**
   - Risk: (a) the green-commit sequencing keeps a no-arg `WardrobeService.list()`/`findAll()` alive until Unit 4 removes it — if Unit 4 slips, an unscoped reader could merge. (b) `DynamoDbTableInitializer` skips existing tables, so a dev with a pre-GSI local table gets a **runtime** `findByUserId` failure, not a clear error.
   - Suggested remediation: (a) add a closing check in task 4.5 (e.g. `grep -rn "\.list()" src/main` returns no wardrobe callers) so the removal is verified, not assumed; (b) task 1.10 already documents the drop-and-recreate — optionally have the initializer log a warning when an existing table lacks `userId-index`. Non-blocking.

2. **Happy-path bias on the purge photo deletion.**
   - Risk: `UnownedDataPurgeRunnerIT` (5.5) asserts unowned photos are gone, but a purge that fails to delete a photo (e.g. `PhotoNotFoundException` on an already-missing key) should not abort the whole run. Regression risk if one bad row halts the migration.
   - Suggested remediation: add a case where an unowned item's photo is already absent and assert the runner still deletes the row and continues (idempotent, resilient). Non-blocking.

## User-Approved Remediation Plan

- **Approved (all three) and Completed** in the tasks file (no code yet):
  1. (REQUIRED) ✅ `create_stampsCallerUserId` (ArgumentCaptor on `save`) added to tasks 2.1 & 2.5 and wired into 2.3 & 2.6.
  2. (FLAG) ✅ No-unscoped-reader grep-guard added to 4.5; `DynamoDbTableInitializer` missing-index WARN added to 1.10.
  3. (FLAG) ✅ "Photo already missing → runner continues" resilience case added to 5.5.

## Re-Audit Delta (Run 2)

- Changed gate statuses since Run 1:
  - Requirement-to-test traceability: **FAIL → PASS** (stamping FR now has a direct captor assertion).
  - Regression-risk blind spots: **FLAG → PASS** (both flags remediated).
- Still-failing REQUIRED gates: **none**.
- Newly introduced findings: **none**.
- Handoff decision: **cleared for implementation (Phase 3).**

## Chain-of-Verification

- Do all REQUIRED gates pass with explicit evidence (Run 2)? **Yes** — traceability now has a direct `create_stampsCallerUserId` captor assertion (2.1/2.5→2.3/2.6); verifiability, standards, and open-question gates passed in Run 1 with the evidence cited in the Gateboard/Standards table.
- Fact-check: confirmed the `create` stamping FR is now directly covered (captor on `save`, not just stubbed reads); confirmed 6 standards sources read incl. `AGENTS.md` + root `README.md`; confirmed all 3 spec Open Questions carry explicit assumptions in the tasks (1.10 drop-and-recreate; 1.4/1.7 projection `ALL`; 5.6 default-off env var); confirmed no non-goal leakage (no per-user cap, no legacy reassignment, GSI not table recreation, users table untouched, no frontend/auth change).
- Final synthesis: **all REQUIRED gates pass; cleared for implementation (Phase 3).**
