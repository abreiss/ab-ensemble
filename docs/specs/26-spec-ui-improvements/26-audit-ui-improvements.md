# 26-audit-ui-improvements.md

Planning audit for `26-tasks-ui-improvements.md` (against
`26-spec-ui-improvements.md`). Compact, exception-only reporting.

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **1**

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every FR maps to ≥1 planned test/proof artifact (see traceability table below). | `## Tasks 1.0–5.0` |
| Proof artifact verifiability | PASS | All artifacts are observable + reproducible (exact `gradlew`/`curl`/`terraform`/Vitest refs) + sanitized (`<token>` placeholder, no passcode in screenshots, redacted ARNs). | `#### N.0 Proof Artifact(s)` |
| Repository standards consistency | PASS | 4 guideline sources read (AGENTS.md, README.md, TESTING.md, ARCHITECTURE.md); the one conflict (dedicated table vs "single-table") has a documented decision (spec §Technical Considerations + Q1-A). | `## Standards Evidence` |
| Open question resolution | PASS | Spec's 3 open questions each carry an explicit assumption; the 3 forking decisions (Q1-A, Q2-B, Q3-C) are resolved in the questions file and reflected in the spec/tasks. | spec `## Open Questions`, `26-questions-1` |
| Regression-risk blind spots | FLAG | Part B (Task 5.0) is verified by manual screenshots only — no automated guard. | `#### 5.0` |
| Non-goal leakage | PASS | Tasks stay within scope; no LLM call in save/browse, no edit/reorder/name, no per-user scoping, no dedup, no `CallCapService.reserve()` on outfit routes. | spec `## Non-Goals` |

## Standards Evidence Table (Required)

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD backend domain; ≥90% line / 100% branch on grounding; layered slice; DTOs at boundary; Enhanced Client single-item model. | none |
| `README.md` | yes | `/api/**` session-gated; CRUD `201`/`204`/`404`; local table auto-create; operator-run deploy; fast pre-commit + secret scan. | none |
| `docs/TESTING.md` | yes | 100% branch on id-validation; controllers test contracts + error paths; frontend RTL on meaningful logic only; IaC validated by plan, not unit-tested. | none |
| `docs/ARCHITECTURE.md` | yes | Grounding guardrail = reject hallucinated ids; instance role scoped `table/${prefix}-*`; secrets by ARN. | "single-table" note vs Q1-A dedicated outfits table — **decision documented** (spec §Technical Considerations, accepted trade-off). |

## Requirement → Test/Proof Traceability

| Functional Requirement (spec) | Planned Artifact | Task |
| --- | --- | --- |
| `SavedOutfit` entity + `OutfitRepository` (dedicated table, no prefix filter) | `OutfitRepositoryIT` round-trip | 1.1, 1.2 |
| `OutfitService` create/list/delete + not-found choke point | `OutfitServiceTest` | 1.4 |
| Save-time grounding guard (unknown id / empty / bad source → 400) — 100% branch | `OutfitServiceTest` (branch report) | 1.4 |
| DTO records + `OutfitMapper`; bean off boundary | `OutfitControllerTest` (JSON contract) | 1.5, 1.6 |
| `OutfitController` `POST`/`GET`/`DELETE` + handler registration (400/404) | `OutfitControllerTest` | 1.6, 1.7 |
| Config prop + local dual-table auto-create | `DynamoDbTableInitializerTest`; `curl` E2E | 1.8, 1.9 |
| Cloud Terraform table + env var, no IAM diff | `terraform validate` + `plan` excerpt | 2.1–2.4 |
| `api/outfits.ts` typed client | `outfits.test.ts` | 3.1 |
| Stylist real save lifecycle (`source: ai`) | `OutfitResult.test.tsx`/`Stylist.test.tsx` | 3.2 |
| Build "Save outfit" (`source: manual`) + empty guard | `Assemble.test.tsx` | 3.3 |
| `/saved` route inside `AuthGate` + `Saved` nav link left of `Build` | routing/nav tests | 4.1 |
| Page states (load/empty/error) + remove | `SavedOutfits.test.tsx` | 4.2 |
| Deleted-piece skip + note (Q3-C), never crash | `SavedOutfits.test.tsx` | 4.3 |
| Responsive layout: fill desktop / center narrow / Stylist unchanged / mobile single-col | Screenshot proof artifacts (manual — per TESTING.md CSS is not unit-tested) | 5.1–5.3 |

## FLAG Findings

1. **Part B verified by manual screenshots only (regression-risk blind spot).**
   - Risk: a future CSS edit could re-cap width, break the Stylist two-pane, or
     introduce horizontal scroll on mobile with no failing test to catch it.
   - Context: this is **standards-consistent** — `docs/TESTING.md` and the spec's
     own assumption exclude CSS/view plumbing from unit tests, and screenshots are
     an accepted proof mode. Non-blocking.
   - Optional remediation (not required to proceed): add one lightweight assertion
     that `#root` no longer carries the 30rem cap / that non-Stylist screens mount
     without the old exception, or explicitly accept manual verification.

## Chain-of-Verification

- All REQUIRED gates pass with explicit evidence (Gateboard + traceability table).
- Each finding fact-checked against the spec, `26-tasks-ui-improvements.md`, and
  the standards sources.
- The Part B manual-verification point was re-examined and correctly classified as
  a FLAG (not a REQUIRED traceability failure), because repository standards
  explicitly exclude CSS from unit testing and accept screenshot proof.
- Final synthesis: **PASS — ready for implementation.** The single FLAG is
  non-blocking and needs no remediation edit.
