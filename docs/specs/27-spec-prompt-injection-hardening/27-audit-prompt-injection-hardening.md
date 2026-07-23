# 27-audit-prompt-injection-hardening.md

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2** (advisory, non-blocking)

All four REQUIRED gates pass on the first audit run. Two FLAG findings are recorded
for transparency; neither blocks handoff to implementation.

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every FR maps to ≥1 task + ≥1 planned test (docs FR → doc proof) | see traceability matrix below |
| Proof artifact verifiability | PASS | All artifacts name exact tests, run commands, and assertions | `## Tasks > *.0 Proof Artifact(s)` |
| Repository standards consistency | PASS | 6 sources read incl. `AGENTS.md` + `README.md`; no conflicts | Standards Evidence Table below |
| Open question resolution | PASS | All 3 spec open questions non-blocking, explicit assumptions adopted in tasks | `1.4`, `2.4`, `3.2`, `3.5` |
| Regression-risk blind spots | FLAG | Guard tests (`3.6`, `4.4`) may pass without a RED | see FLAG 1 |
| Non-goal leakage | PASS | No classifier, rate-limit, sanitizer lib, new tool, or guardrail/parser change | spec Non-Goals 1–6 |

## Standards Evidence Table (Required)

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` (imported via `CLAUDE.md`) | yes | Strict TDD RED→GREEN→REFACTOR; ≥90% line + 100% branch on critical logic; mock the Claude seam (no live API); DTO boundary | none |
| `README.md` (root) | yes | `./gradlew test -PskipFrontend`; `./gradlew jacocoTestReport`; frontend `npm test -- --run` + `npm run lint`; pre-commit gates | none |
| `docs/TESTING.md` | yes | 100%-branch list (grounding/parsing/validation); mock client + assert on request; light frontend testing; AAA naming | none |
| `.pre-commit-config.yaml` | yes | fast backend + frontend tests + eslint + secret scan on commit | none |
| `build.gradle` | yes | `spring-boot-starter-validation` already present (Task 1 additive); `taggingEval` = opt-in live-runner precedent; jacoco report (no hard gate) | none |
| `docs/ARCHITECTURE.md` | yes | "Stylist Agent + Guardrails" + "Security" = Task 4.5 doc targets | none |
| `CONTRIBUTING.md`, `.github/pull_request_template.md` | not found | fell back to `AGENTS.md`/`README`/pre-commit for contribution standards | none |

## Requirement-to-Test Traceability Matrix

| Functional Requirement (spec) | Task(s) | Planned Test / Proof |
| --- | --- | --- |
| U1 — Bean Validation caps on `StyleRequest` (prompt/history/turn text, `@Valid` cascade) | 1.4 | `styleRequest_oversizePrompt/History/TurnText_rejectedWith400` (1.1), boundary (1.2) |
| U1 — `@Valid` on `@RequestBody` | 1.5 | oversize tests route through handler (1.1) |
| U1 — sanitized 400, no leaked internals | 1.4–1.6 | body `bad_request`/`invalid request` assertions (1.1) |
| U1 — reject before `CallCapService.reserve()` | 1.3 | `verifyNoInteractions(service, callCapService)` (1.3) |
| U2 — model-side output constraint + ignore-format clause | 2.5–2.6 | `recordOutfit_constrainsReasonAndRationaleToStylingOnly` (2.5) |
| U2 — deterministic cap reason ≤300 / rationale ≤200 before DTO | 2.1–2.4 | `TextBoundsTest` (2.1); `..._isTruncatedBeforeDto` (2.3) |
| U2 — truncation as backstop (both layers) | 2.3–2.6 | backstop tests (2.3) + model-side tests (2.5) |
| U2 — grounding/parser/forced-output unchanged | 2.4, 2.8 | existing stylist tests stay green |
| U2 — hostile value renders escaped | 2.7 | `outfitResult_maliciousReason_isRenderedEscaped` |
| U3 — wrap vibe as data + system clause (itemIds authoritative) | 3.1,3.3,3.5 | `vibe_isWrappedAsData...`, `systemPrompt_framesTaggedTextAsData` |
| U3 — neutralize delimiter break-out | 3.2,3.5 | `userText_closingDelimiter_isNeutralized` |
| U3 — assistant history untrusted; re-pick decoupled; retry uses user turn | 3.4–3.6 | `forgedAssistantHistory_isWrappedAsUntrustedData` + existing `repickConversation_*` green |
| U3 — byte-free + stateless unchanged | 3.5 | `repickConversation_forwardsTextOnly_noImageBytes` stays green |
| U4 — data-framing note on `searchWardrobe` / `renderWardrobe` | 4.1–4.2 | `searchWardrobeTool_framesWardrobeTextAsData` |
| U4 — indirect-injection stays grounded | 4.3–4.4 | `styleRequest_indirectInjectionInDescriptor_staysGrounded` |
| U4 — update `docs/ARCHITECTURE.md` | 4.5 | doc diff (proof artifact) |
| Success Metrics — 100% branch preserved + no regression | 4.6 | `jacocoTestReport` + full suite green |

## FLAG Findings (advisory, non-blocking)

1. **Guard tests without a RED (strict-TDD deviation).**
   - Risk: Tasks `3.6` (`roleSwitchVibe`, `forgedAssistantHistory` at the service
     level) and `4.4` (`indirectInjectionInDescriptor`) are handling/regression
     guards that may pass on first write, so they don't produce a true RED.
   - Why acceptable: the spec's testing philosophy explicitly frames these as
     proving *our handling* (grounded/bounded/escaped regardless of model output);
     the genuine RED behavior for Units 3–4 lives in the client-level tests
     (`3.1`–`3.4`, `4.1`), which do fail first. No remediation required.
   - Suggested remediation (optional): none — documented split is intentional.

2. **Wrapping could disturb existing text assertions.**
   - Risk: Wrapping the vibe/history as data (Task 3.5) changes the exact text
     handed to the seam; a future test asserting raw vibe equality could break.
   - Why acceptable: no current test asserts raw vibe/history text equality
     (verified against `AnthropicStylistModelClientTest` and `StylistServiceTest`);
     Task 3.7 re-runs the full stylist suite as the backstop.
   - Suggested remediation (optional): none — covered by the 3.7 verify step.

## Chain-of-Verification

- All REQUIRED gates pass with explicit evidence (matrix + evidence table above).
- Each finding fact-checked against the spec, the task file, and the read source
  files; no unsupported or ambiguous finding remains.
- Final status: **PASS** — ready for the implementation phase.
