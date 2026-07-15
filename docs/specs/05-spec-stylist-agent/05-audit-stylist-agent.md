# 05-audit-stylist-agent.md

Planning audit for `05-tasks-stylist-agent.md` against `05-spec-stylist-agent.md`
and repository standards. Exception-only reporting.

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2** (non-blocking; already folded into sub-tasks)

## Gateboard

| Gate | Status | Note | Target |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every FR maps to a named test artifact (matrix below) | — |
| Proof artifact verifiability | PASS | All artifacts observable + reproducible (exact test names, gradle/npm cmds, JaCoCo path, keyless curl, screenshot path) | — |
| Repository standards consistency | PASS | 4 guideline sources read; no conflicts | — |
| Open question resolution | PASS | Sole open question (endpoint name) resolved by explicit assumption `POST /api/style` | — |
| Regression-risk blind spots | FLAG | See F1 | `1.5`, `1.7` |
| Non-goal leakage | FLAG | See F2 | Notes / `1.6` |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD (RED→GREEN→REFACTOR) on backend domain; layered controller→service→repo, DTOs at boundary; mock Claude, never live; Sonnet 5 for stylist; forced `{itemIds,reason}` + grounding + one retry | none |
| `README.md` | yes | Test cmds (`./gradlew test -PskipFrontend`, `jacocoTestReport`, `npm test -- --run`, `npm run lint`); `.env`/`ENSEMBLE_ANTHROPIC_API_KEY` key handling; conventional commits + pre-commit gates | none |
| `docs/TESTING.md` | yes | Coverage split: backend domain ≥90% line / 100% branch on grounding + forced-output parsing; frontend meaningful-logic only; descriptive AAA test names | none |
| `05-spec-stylist-agent.md` (Technical/Repository Standards) | yes | `StylistModelClient` seam; reuse `firstToolUseJson` + `TaggingService.map` parser pattern; add `stylist-model` property; register controller in `ApiExceptionHandler`; no `budget_tokens`/sampling on Sonnet 5 | none |

## Requirement-to-Test Traceability Matrix

| Spec FR | Planned test artifact |
| --- | --- |
| U1 `searchWardrobe` returns tags+wear-history, no bytes | `StylistServiceTest.styleRequest_sendsNoImageBytesToModel` + `AnthropicStylistModelClientTest` (no-bytes assert); payload from `WardrobeService.list()` |
| U1 tool-loop (auto, execute, append, end_turn, cap) | `AnthropicStylistModelClientTest` (loop + cap assertions) |
| U1 forced output + defensive parse | `OutfitParserTest` (all branches) + `StylistServiceTest.styleRequest_withMalformedOutput_handledSafely` |
| U1 validate ids, feed back, retry exactly once | `StylistServiceTest.styleRequest_withHallucinatedId_retriesOnceThenRendersValidSubset` |
| U1 validated subset; error only when zero | same + `...allIdsInvalidAfterRetry_returnsError` |
| U1 model decides composition (no slot rules) | `...withValidOutput_returnsGroundedOutfit` (accepts any composition; no slot assertion imposed) |
| U1 no image bytes on any request | `...sendsNoImageBytesToModel` + client test |
| U1 API error degrades gracefully | `...apiError_degradesGracefully` |
| U2 `POST /api/style` contract | `StyleControllerTest.postStyle_valid_returns200WithOutfit` |
| U2 empty/too-small wardrobe → 200 friendly | `...postStyle_emptyWardrobe_returnsFriendlyResponse` + `StylistServiceTest.styleRequest_emptyWardrobe_returnsFriendlyResponse` |
| U2 upstream failure → graceful | `...postStyle_upstreamFailure_returnsGracefulError` |
| U2 no internals leaked (DTOs only) | DTO shape asserted in `StyleControllerTest` |
| U3 vibe input submit | `Stylist.test.tsx` (submit) |
| U3 render itemIds as card w/ real photo + reason | `Stylist.test.tsx` (card render via `photoUrl`) |
| U3 loading/error+retry/empty states | `Stylist.test.tsx` (three states) |
| U3 reachable from nav, mobile-first, polished | Screenshot proof + `frontend-design` skill (view plumbing — light-tested per `docs/TESTING.md`) |

## FLAG Findings

1. **Retry/loop boundary regression risk.**
   - Risk: "retry exactly once" and the continuation cap are off-by-one-prone — a regression could retry twice or loop unbounded, both invisible to a happy-path test.
   - Remediation (folded in): sub-task `1.5` asserts retry fires **exactly once** (not zero, not twice); sub-task `1.7` asserts the loop **does not exceed the cap**.
2. **Non-goal proximity (#7 re-pick / wear-writes).**
   - Risk: `StylistService` accepting an internal message list (spec leaves room for #7) invites accidentally shipping re-pick or wear-history-write logic, which are out of scope.
   - Remediation (folded in): Notes section explicitly forbids re-pick + wear-writes this slice; the tool only *reads* wear-history. No sub-task adds either.

## User-Approved Remediation Plan

- None required — all REQUIRED gates pass. The two FLAGs are advisory and already
  reflected in sub-tasks `1.5` / `1.7` and the Notes non-goal guard; no edits pending approval.

## Chain-of-Verification

- All REQUIRED gates pass with explicit evidence (matrix + standards table above).
- Each finding checked against spec FRs, the task file, and the four standards sources; no unsupported claim.
- Final status: **PASS — ready for implementation (Phase 3).**
