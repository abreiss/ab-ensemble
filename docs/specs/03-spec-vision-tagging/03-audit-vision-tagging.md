# 03-audit-vision-tagging.md

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: 0
- Flagged Risks: 2

All four REQUIRED gates pass on the first audit run. Two non-blocking FLAG
findings are recorded below for implementation awareness; neither blocks the
handoff to the implementation phase.

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | All 13 FRs map to a task + a planned test artifact | see traceability matrix |
| Proof artifact verifiability | PASS | Every artifact is a named test / exact command / redacted transcript | `## Tasks` proof blocks |
| Repository standards consistency | PASS | 7 sources read incl. `AGENTS.md` + root `README.md`; no conflicts | `## Standards Evidence Table` |
| Open question resolution | PASS | All 4 spec open questions carry explicit non-blocking assumptions honored by tasks | spec §Open Questions |
| Regression-risk blind spots | FLAG | 2.0 refactors shipped critical-branch storage logic | FLAG-1 |
| Non-goal leakage | FLAG | FR1.7 negative guarantee is inspection-verified (no stylist code exists) | FLAG-2 |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD backend domain (≥90% line, 100% branch on vision-tag mapping + fallback); layered controller→service→seam; DTOs at boundary; Haiku 4.5 tagging; keys env-only | none |
| `CLAUDE.md` | yes | Imports AGENTS.md; stylist never sees images (images only at tag time) | none |
| `docs/TESTING.md` | yes | Mock Claude client; assert request built (model/image/forced-JSON) + response handling (valid/malformed/error/timeout); no live calls | none |
| `README.md` | yes | `./gradlew bootRun`; `-PskipFrontend`; package-by-feature layout | none |
| `.pre-commit-config.yaml` | yes | `block-anthropic-keys` pygrep (`sk-ant-*`); backend-tests gate | none |
| `build.gradle` | yes | Spring Boot 4.1 / Java 21 / JaCoCo; BOM pattern for versioned deps | none |
| `docs/specs/02-.../02-tasks-*.md` | yes | Task-file format (Relevant Files → Notes → parent proof blocks → RED/GREEN/REFACTOR sub-tasks); proofs under `NN-proofs/` | none |

`CONTRIBUTING.md` and `.github/pull_request_template.md`: **not found** (searched);
fallback to `AGENTS.md` + `.pre-commit-config.yaml`, both read. Standards
confidence: **high**.

## Requirement-to-Test Traceability Matrix

| FR | Requirement (abbrev.) | Task(s) | Planned test artifact |
| --- | --- | --- | --- |
| FR1.1 | SDK dep + client bean `claude-haiku-4-5`, key from env | 1.1, 1.3, 1.4, 1.6 | `AnthropicPropertiesTest`; `AnthropicVisionModelClientTest` (model asserted) |
| FR1.2 | Narrow mockable seam | 1.5, 1.7 | `AnthropicVisionModelClientTest` (mocked SDK); seam mocked in `TaggingServiceTest` |
| FR1.3 | Single Haiku vision request, image + forced JSON | 1.5, 1.6 | `AnthropicVisionModelClientTest` (request captor) |
| FR1.4 | Map JSON → 6 scalars + descriptors | 3.1, 3.2 | `TaggingServiceTest#validResponse_mapsSixScalarsPlusDescriptors` |
| FR1.5 | Clamp/validate numeric → unknown if bad | 3.3 | `TaggingServiceTest` clamp cases |
| FR1.6 | Any failure → partial/empty, never throw | 3.4 | `TaggingServiceTest` fallback cases (malformed/incomplete/error/timeout) |
| FR1.7 | Never send image to stylist path | 1.6, 1.7, 3.5 | request-built assertion (image only in vision call) + `grep` inspection — see FLAG-2 |
| FR2.1 | `POST /api/items/tag` multipart → `TagSuggestion` JSON | 4.1, 4.2 | `TaggingControllerTest` good→200 |
| FR2.2 | `TagSuggestion` all-nullable, no constraints | 4.5 | `TaggingControllerTest` all-null serialization + inspection |
| FR2.3 | Persist nothing | 4.6 | mocked-service `@WebMvcTest` (no repo/storage wired) + inspection |
| FR2.4 | Validate image; missing/invalid → 400, reuse guard | 2.x, 3.5, 4.3 | `TaggingControllerTest` 400 cases; `TaggingServiceTest` propagation |
| FR2.5 | Degraded → 200, never 500 | 4.3 | `TaggingControllerTest` degraded→200 |
| FR2.6 | Error handling via reused `@RestControllerAdvice` | 4.4 | `TaggingControllerTest` sanitized 400 body |

Coverage: 13/13 FRs traced. No unmapped requirement.

## FLAG Findings

1. **FLAG-1 — Refactor of shipped critical-branch logic (regression risk).**
   - Risk: task 2.0 extracts the decode / decompression-bomb pixel-cap / resize
     logic out of the already-shipped, 100%-branch-covered `LocalDiskPhotoStorage`.
     A careless move could silently drop branch coverage or change behavior.
   - Suggested remediation: none required — task 2.4 already mandates the existing
     `LocalDiskPhotoStorageTest` stays green and that 100% branch coverage is
     re-verified in the new home. Recorded for implementer attention.

2. **FLAG-2 — FR1.7 is a negative guarantee, inspection-verified.**
   - Risk: "never send image bytes to the stylist path" cannot be enforced by an
     end-to-end test because no stylist code exists yet (issue #6). It is covered
     by the request-built assertion (image present only in the Haiku vision call)
     plus the `grep` seam-boundary check in 1.7.
   - Suggested remediation: none required this slice; issue #6 should add an
     assertion that the stylist path receives text tags only. Recorded as a
     forward note.

## Chain-of-Verification

- Self-question — do all REQUIRED gates pass with explicit evidence? **Yes**;
  each is backed by a named source or the traceability matrix above.
- Fact-check — every FR row was checked against `03-spec-vision-tagging.md`
  (§Functional Requirements) and the task file's sub-tasks/proof blocks.
- Inconsistency resolution — none found; the two residual risks are recorded as
  FLAGs, not failures.
- Final synthesis: **PASS** — cleared to proceed to the implementation phase.
