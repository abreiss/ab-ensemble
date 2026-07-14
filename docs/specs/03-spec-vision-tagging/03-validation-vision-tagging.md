# 03-validation-vision-tagging.md

Validation of `03-spec-vision-tagging` against its implementation, task list, and
proof artifacts on branch `feat/03-vision-tagging`.

> **Update (2026-07-14):** The one deferred item — the live, key-gated end-to-end
> run (Success Metric 1 / tasks 5.2–5.4) — has since been **executed and captured**
> in `03-proofs/03-task-05-proofs.md`: a real garment photo → live Haiku 4.5 tags →
> item created (`201` + `GET`), key sourced only from a git-ignored `.env`. The
> MEDIUM issue below is therefore **RESOLVED**; overall result is now an
> unconditional **PASS**. (Note: the key is now read via `ENSEMBLE_ANTHROPIC_API_KEY`
> from `.env`, superseding the spec's original env-only-`ANTHROPIC_API_KEY` wording,
> per user request.)

## 1) Executive Summary

- **Overall:** **PASS** (conditional) — no gate tripped by a blocker; one
  non-blocking MEDIUM (the live, key-gated demo proof is deferred).
- **Implementation Ready:** **Yes** — all 13 Functional Requirements are
  independently verified by automated tests + inspection with the Claude client
  mocked; the only outstanding item is the live end-to-end demo (Success Metric 1),
  which requires an `ANTHROPIC_API_KEY` not available in this environment and does
  not indicate a code defect.
- **Key metrics:**
  - Requirements Verified: **13/13 (100%)**
  - Proof Artifacts working: **automated 100%**; 1 planned **live** artifact deferred (key-gated)
  - Files changed vs expected: all changed core files are in the task's Relevant
    Files list or explicitly linked; **no unmapped out-of-scope core change**
  - Coverage: tagging package **96.6% line**; `TaggingService` (critical mapping +
    fallback) **100% branch (24/24)**, 100% line
  - Secret scan: **green** (no Anthropic key in code, config, tests, or proofs)

## 2) Coverage Matrix

### Functional Requirements

| FR | Requirement (abbrev.) | Status | Evidence |
| --- | --- | --- | --- |
| FR1.1 | SDK dep + client bean `claude-haiku-4-5`, key from env | Verified | `AnthropicPropertiesTest` (defaults), `AnthropicConfig` lazy `fromEnv()`, `build.gradle` dep; commit `de8d267` |
| FR1.2 | Narrow mockable seam (no SDK leak) | Verified | `VisionModelClient` interface; `AnthropicVisionModelClientTest` mocks the SDK; `grep import com.anthropic` → only `AnthropicConfig` + `AnthropicVisionModelClient` |
| FR1.3 | Single Haiku vision request, image + forced JSON | Verified | `AnthropicVisionModelClientTest#request_targetsHaiku_carriesImage_andForcesTagTool` (request captor) |
| FR1.4 | Map JSON → 6 scalars + `descriptors` | Verified | `TaggingServiceTest#validResponse_mapsSixScalarsPlusDescriptors`; commit `c8b132c` |
| FR1.5 | Clamp/validate numeric → unknown if bad | Verified | `TaggingServiceTest` formality/warmth below+above range + `nonNumericFormality_isLeftNull`; 100% branch on `intInRange` |
| FR1.6 | Any failure → partial/empty, never throw | Verified | `TaggingServiceTest` null / blank / malformed / apiError / timeout cases → `TagSuggestion.empty()` |
| FR1.7 | Never send image bytes to stylist path | Verified (inspection) | No stylist code exists (issue #6); SDK confined to seam+config; image asserted only in the vision request. Matches audit FLAG-2 |
| FR2.1 | `POST /api/items/tag` multipart → `TagSuggestion` JSON | Verified | `TaggingControllerTest#tag_goodSuggestion_returns200Json`; commit `e67ce2d` |
| FR2.2 | `TagSuggestion` all-nullable, no constraints | Verified | `TagSuggestionTest#allNullSuggestion_serializesToValidJson`; DTO record carries no `jakarta.validation` annotations (vs `TagRequest`) |
| FR2.3 | Persist nothing | Verified | `grep -rE "WardrobeRepository|PhotoStorage|DynamoDb|software.amazon" src/main/java/com/ensemble/tagging/` → no matches; mocked-service `@WebMvcTest` |
| FR2.4 | Validate image; missing/invalid → 400, reuse guard | Verified | `TaggingControllerTest` missing-part + non-image → 400; `TaggingServiceTest#invalidImage_propagates_andSeamIsNeverCalled` |
| FR2.5 | Degraded → 200, never 500 | Verified | `TaggingControllerTest#tag_degradedEmptySuggestion_stillReturns200` |
| FR2.6 | Error handling via reused `@RestControllerAdvice` | Verified | `ApiExceptionHandler` `assignableTypes = {WardrobeController, TaggingController}`; sanitized `bad_request` body asserted |

No `Unknown` entries → **GATE B satisfied**.

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Coding Standards (layered, DTO boundary, package-by-feature) | Verified | controller → service → seam under `com.ensemble.tagging`; config under `com.ensemble.config`; DTO-only boundary (`TagSuggestion`); no SDK type in controller/service |
| Testing Patterns (TDD, Mockito, `@WebMvcTest`, mock Claude) | Verified | RED→GREEN per task; seam + `ImageProcessor` mocked; `@WebMvcTest` for the endpoint; **no live call in suite** |
| Quality Gates (JaCoCo, pre-commit) | Verified | ≥90% line on tagging (96.6%), 100% branch on critical logic; all 3 commits passed pre-commit (backend tests + secret scan) |
| Secret Hygiene | Verified | `block-anthropic-keys` green; key env-only (`ANTHROPIC_API_KEY`), never a config property |
| Documentation | Verified | `README.md` "Vision tagging (tag preview)" section added (env var, sample curl, non-blocking 200 note) |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| Unit 1 / T3 | `TaggingServiceTest` (19 cases: mapping, clamp, fallback) | Verified | `./gradlew test --tests 'com.ensemble.tagging.*'` → BUILD SUCCESSFUL |
| Unit 1 / T3 | Coverage: 100% branch on mapping + fallback | Verified | JaCoCo: `TaggingService` BRANCH 24/24, LINE 33/33 |
| Unit 1 / T1 | `AnthropicVisionModelClientTest` (request shape, mocked SDK) | Verified | 3 tests green; request targets `claude-haiku-4-5`, carries image, forces tag tool |
| Unit 2 / T4 | `@WebMvcTest`: good→200, degraded→200, missing/non-image→400 | Verified | `TaggingControllerTest` 4 tests green; sanitized `bad_request` body |
| Unit 2 / T4 | All-null `TagSuggestion` serialization | Verified | `TagSuggestionTest` 2 tests green |
| Unit 2 / T5 | Clean-build coverage summary under `03-proofs/` | Verified | `03-task-05-proofs.md`; `clean test jacocoTestReport` green |
| Unit 2 / T5 | **Live** `curl` real photo → tags → item, saved transcript | **Deferred** | Not run — requires `ANTHROPIC_API_KEY` + running server; reproduction steps recorded in `03-task-05-proofs.md` (MEDIUM, see Issues) |

All five proof files exist under `docs/specs/03-spec-vision-tagging/03-proofs/`
(`03-task-01`…`03-task-05-proofs.md`) → **GATE C met for all produced artifacts**.

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| MEDIUM | Live end-to-end demo proof deferred. Task `5.2–5.4` (`03-tasks-vision-tagging.md`) and Success Metric 1 require a real `ANTHROPIC_API_KEY` + running server, unavailable here. Evidence: `03-task-05-proofs.md` "Pending" section; env has no key. | Headline user-facing acceptance is not demonstrated by a live artifact; **all 13 FRs remain independently verified** by automated tests, so requirement verification is not blocked. | Run the recorded commands with a real key, append the sanitized (key-redacted) transcript to `03-task-05-proofs.md`, then mark `5.2–5.4` and `5.0` `[x]`. |
| LOW | `AnthropicVisionModelClient.serialize()` has one uncovered branch (BRANCH 3/4). Evidence: JaCoCo XML. | Not part of the spec's 100%-branch critical logic (mapping + fallback), which is fully covered. | Optional: add a test for the JSON-serialization fallback, or leave as-is (task 1.0 seam code, non-critical). |

No CRITICAL or HIGH issues → **GATE A not tripped**.

## 4) Evidence Appendix

### Commits analyzed (all map to spec-03 tasks)

```
b968ffd docs: document tag-preview flow + record coverage/secret gate   (T5.1, T5.5)
e67ce2d feat: add POST /api/items/tag tag-preview endpoint              (T4)
c8b132c feat: TaggingService maps vision JSON to tags with fallback     (T3)
4d67339 refactor: extract shared ImageProcessor from LocalDiskPhotoStorage (T2)
de8d267 feat: Claude client seam + Anthropic SDK + typed config         (T1)
```

Every changed core file appears in the task's Relevant Files list (or is an
explicitly-listed extension, e.g. `ApiExceptionHandler`); supporting test/proof/doc
files are linked through the commits above → **GATE D satisfied** (no unmapped
out-of-scope core change).

### Test execution

```
$ ./gradlew test --tests 'com.ensemble.tagging.*' -PskipFrontend
> Task :test
BUILD SUCCESSFUL
# TaggingServiceTest 19, TaggingControllerTest 4, TagSuggestionTest 2, AnthropicVisionModelClientTest 3
```

### Coverage (JaCoCo, client mocked)

```
com/ensemble/tagging/TaggingService              BRANCH 24/24   LINE 33/33
com/ensemble/tagging/web/TaggingController       BRANCH  0/0    LINE  4/4
com/ensemble/tagging/dto/TagSuggestion           BRANCH  0/0    LINE  3/3
com/ensemble/tagging/AnthropicVisionModelClient  BRANCH  3/4    LINE 46/49
AGGREGATE tagging: LINE 86/89 = 96.6%   BRANCH 27/28 = 96.4%
```

### Security + boundary checks

```
$ grep -rIn 'sk-ant-[A-Za-z0-9_-]{20,}' docs/specs/03-spec-vision-tagging src
  -> no match (block-anthropic-keys green)

$ grep -rl "import com.anthropic" src/main/java
  src/main/java/com/ensemble/config/AnthropicConfig.java
  src/main/java/com/ensemble/tagging/AnthropicVisionModelClient.java   (seam boundary held)

$ grep -rE "WardrobeRepository|PhotoStorage|DynamoDb|software.amazon" src/main/java/com/ensemble/tagging/
  -> no match (tag path persists nothing)
```

## Gate Summary

| Gate | Result |
| --- | --- |
| A — no CRITICAL/HIGH | PASS |
| B — no Unknown FRs | PASS (13/13 Verified) |
| C — proof artifacts accessible/functional | PASS for all produced; 1 live artifact deferred (MEDIUM) |
| D — file integrity / no unmapped core change | PASS |
| E — repository standards | PASS |
| F — no secrets in proofs | PASS |

**Recommendation:** The mocked slice is complete and merge-ready. Before final
demo/merge sign-off, capture the live end-to-end transcript (task 5.2–5.4) with a
real key to close Success Metric 1, and do a final human code review of the
implementation + this report.

**Validation Completed:** 2026-07-14
**Validation Performed By:** Claude Opus 4.8 (1M context)
