# 27-validation-prompt-injection-hardening.md

> Spec: [`27-spec-prompt-injection-hardening.md`](./27-spec-prompt-injection-hardening.md)
> Task list: [`27-tasks-prompt-injection-hardening.md`](./27-tasks-prompt-injection-hardening.md)
> Planning audit: [`27-audit-prompt-injection-hardening.md`](./27-audit-prompt-injection-hardening.md)
> GitHub issue: [#21 — prevent prompt injection](https://github.com/abreiss/ab-ensemble/issues/21)

## 1) Executive Summary

- **Overall: PASS** — no gate tripped (Gates A–F all satisfied).
- **Implementation Ready: Yes** — all four demoable units are implemented under strict TDD, every named red-team test is present and green, and critical-logic branch coverage is preserved at 100%.
- **Key metrics:**
  - **Requirements verified:** 17 / 17 functional requirements (100%).
  - **Proof artifacts working:** 100% — backend **335 tests, 0 failures, 0 errors**; frontend **364 tests, 32 files, all pass**; JaCoCo critical-logic branch coverage 100%.
  - **Files changed vs expected:** 18 changed files, all mapped to the task list's "Relevant Files" (5 core source, 4 backend tests + 1 frontend test, docs + proofs). No out-of-scope core changes.

## 2) Coverage Matrix

### Functional Requirements

| Requirement (spec unit) | Status | Evidence |
| --- | --- | --- |
| U1 — Bean Validation caps on `StyleRequest` (`prompt` `@NotBlank`+`@Size(1000)`, `history` `@Size(20)`, `StyleTurn.text` `@Size(2000)`, `@Valid` cascade) | Verified | `StyleRequest.java:32-55`; commit `d6e571c` |
| U1 — `@Valid` on `@RequestBody` in `StyleController.style(...)` | Verified | `StyleController.java:59`; commit `d6e571c` |
| U1 — sanitized `400` (`bad_request`/`invalid request`), no leaked internals | Verified | `StyleControllerTest` cap tests green; reuses unchanged `ApiExceptionHandler` |
| U1 — reject **before** `CallCapService.reserve()` / any Claude call | Verified | `verifyNoInteractions(service, callCapService)` in oversize tests; binding-time validation |
| U2 — model-side output constraint + ignore-vibe-format clause | Verified | `AnthropicStylistModelClient.java:71-89` (system prompt), `:270-274` (`record_outfit` desc); test `recordOutfit_constrainsReasonAndRationaleToStylingOnly` |
| U2 — deterministic cap `reason` ≤300 / `rationale` ≤200 before DTO | Verified | `TextBounds.java:29-37`; `StylistService.java:123-130`; tests `..._isTruncatedBeforeDto` |
| U2 — truncation is a backstop (both layers present) | Verified | model-side clauses + `TextBounds` backstop, both tested |
| U2 — grounding / parser / forced-output unchanged | Verified | `OutfitParser` 30/30 branch; existing stylist tests green |
| U2 — hostile value renders escaped (frontend) | Verified | `OutfitResult.test.tsx` "renders a hostile per-piece rationale as escaped" |
| U3 — wrap vibe as data + system clause (itemIds authoritative) | Verified | `AnthropicStylistModelClient.java:167-170` (`wrapAsData`), `:84-89`; tests `vibe_isWrappedAsData_inConversationContent`, `systemPrompt_framesTaggedTextAsData` |
| U3 — neutralize delimiter break-out | Verified | `WRAPPER_DELIMITER` strip at `:65-66,168`; test `userText_closingDelimiter_isNeutralized` |
| U3 — assistant history untrusted; role preserved; retry uses `user` turn | Verified | `:113-122` (content wrapped, role untouched); `StylistService.java:148-155`; tests `forgedAssistantHistory_isWrappedAsUntrustedData`, `styleRequest_forgedAssistantHistory_cannotChangeOutputShape` |
| U3 — byte-free + stateless unchanged | Verified | existing `repickConversation_forwardsTextOnly_noImageBytes` green |
| U4 — data-framing on `searchWardrobe` / `renderWardrobe` | Verified | `AnthropicStylistModelClient.java:239-243`; `StylistService.java:177-180`; test `searchWardrobeTool_framesWardrobeTextAsData` |
| U4 — indirect-injection stays grounded | Verified | test `styleRequest_indirectInjectionInDescriptor_staysGrounded` |
| U4 — `docs/ARCHITECTURE.md` updated | Verified | `docs/ARCHITECTURE.md:48-90` "Prompt-injection posture (issue #21)" + Security bullet; commit `21bf83d` |
| Success Metrics — 100% branch preserved + no regression | Verified | JaCoCo table below; full backend + frontend suites green |

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Strict TDD (RED→GREEN→REFACTOR) | Verified | Each proof records a true RED (compile/assertion failure) before GREEN; client-level tests carry the genuine RED for Units 3–4 (guards documented per audit FLAG 1). |
| Mock the Claude seam (no live API) | Verified | Tests exercise the `StylistModelClient` seam / mocked SDK; no live key required. |
| Layered / DTO boundary | Verified | Validation on `StyleRequest` DTO; truncation in `StylistService` + pure `TextBounds`; client/DynamoDB never cross into the controller. |
| 100% branch on critical logic | Verified | `StylistService` 18/18, `OutfitParser` 30/30, `TextBounds` 4/4, `StyleController` 8/8. |
| Conventional commits, ~one per unit | Verified | `d6e571c`, `32b67de`, `cfb0121`, `21bf83d` — one per demoable unit. |
| Frontend testing (light, meaningful) | Verified | One RTL escaping guard added; no over-testing of view plumbing. |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| Unit 1 | `StyleControllerTest` cap/boundary/blank/pre-call tests | Verified | Green; 335-test backend suite, 0 failures. |
| Unit 2 | `TextBoundsTest` (5 branches) + `StylistServiceTest` truncation-before-DTO | Verified | Green; `TextBounds` 4/4 branch. |
| Unit 2 | `AnthropicStylistModelClientTest#recordOutfit_constrainsReasonAndRationaleToStylingOnly` | Verified | Green; clauses present in prompt + tool desc. |
| Unit 2 | `OutfitResult.test.tsx` escaping guard | Verified | Green; literal `onerror` renders as text, no live element. |
| Unit 3 | client-seam wrap/delimiter/system-clause/forged-history tests | Verified | Green; 4 RED-first tests + service handling guards. |
| Unit 4 | `searchWardrobeTool_framesWardrobeTextAsData` + `styleRequest_indirectInjectionInDescriptor_staysGrounded` | Verified | Green; wardrobe text framed as data at tool + render boundary. |
| Unit 4 | `docs/ARCHITECTURE.md` diff | Verified | Injection posture section present (lines 48-90). |
| Success Metrics | JaCoCo critical-logic branch report | Verified | 100% on all critical classes (table below). |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| LOW | Misplaced Javadoc block. `AnthropicStylistModelClient.java:151-160` is a Javadoc comment describing `systemPromptFor(...)` but sits immediately above the `wrapAsData(...)` Javadoc/method; `systemPromptFor` is defined below at `:172` without its own doc. Cosmetic only — no behavioral effect and all tests pass. | Traceability/readability (none functional) | Optionally move the `systemPromptFor` Javadoc to directly precede its method (or delete the stray block). Non-blocking. |

No CRITICAL, HIGH, or MEDIUM issues found. The two planning-audit FLAGs (guard tests without a RED; wrapping disturbing text assertions) were both realized exactly as anticipated and are non-blocking: the genuine RED for Units 3–4 lives in the client-level tests, and no test asserts raw vibe/history equality (full stylist suite green).

## 4) Evidence Appendix

### Git commits analyzed (`main..HEAD`)

- `d6e571c feat(stylist): bound style-request inputs with a sanitized 400` — Unit 1: `StyleRequest` caps, `@Valid`, `StyleControllerTest` (+spec/tasks/audit/proof).
- `32b67de feat(stylist): bound and constrain reason/rationale output` — Unit 2: `TextBounds`, `StylistService` caps, model-side clauses, frontend escaping guard.
- `cfb0121 feat(stylist): frame client vibe/history as data and neutralize break-out` — Unit 3: `wrapAsData` + delimiter strip, system-prompt data-framing, forged-history handling.
- `21bf83d feat(stylist): frame wardrobe text as data and document injection posture` — Unit 4: `searchWardrobe`/`renderWardrobe` framing, indirect-injection guard, `docs/ARCHITECTURE.md`.

Commit story is coherent and maps one-to-one to the four demoable units. No unrelated changes.

### Test suite results

```
Backend:  ./gradlew test -PskipFrontend jacocoTestReport  → BUILD SUCCESSFUL
          44 result files, 335 tests, 0 failures, 0 errors
Frontend: cd frontend && npm test -- --run                → Test Files 32 passed (32), Tests 364 passed (364)
```

### JaCoCo critical-logic branch coverage (`build/reports/jacoco/test/jacocoTestReport.xml`)

| Class | Role | Branch (covered/total) |
| --- | --- | --- |
| `StylistService` | grounding / id-validation / truncation | 18/18 — 100% |
| `OutfitParser` | forced-output parsing | 30/30 — 100% |
| `TextBounds` | output truncation | 4/4 — 100% |
| `StyleController` | input-validation (`@Valid`) path | 8/8 — 100% |
| `AnthropicStylistModelClient` | model seam (not on critical-logic list) | 30/32 — 94% |

The two uncovered seam branches (a defensive `null`-guard in `wrapAsData` and a tool-filter short-circuit in `searchResults`) are not on the spec's critical-logic 100%-branch list; matches the Task 04 proof.

### File integrity (GATE D)

18 files changed vs `main`; all map to the task list's "Relevant Files": 5 core source files (`StyleRequest`, `StyleController`, `TextBounds`, `StylistService`, `AnthropicStylistModelClient` — all listed), 4 backend test files + 1 frontend test (`OutfitResult.test.tsx`), and `docs/ARCHITECTURE.md` + spec/tasks/audit/proof docs. `frontend/src/routes/Stylist.test.tsx` was listed as optional and was intentionally not changed — acceptable per the task list. No unmapped out-of-scope core file changes.

### Security (GATE F)

Secret scan of `docs/specs/27-spec-prompt-injection-hardening/` (keys, AWS access-key ids, PEM headers, inline passwords) → none found. Red-team fixtures use only synthetic payload strings; no test requires or embeds an API key.

---

**Validation Result:** PASS
**Validation Completed:** 2026-07-23 10:55 PDT
**Validation Performed By:** Claude Opus 4.8 (1M context)
