# Task 01 Proofs — Stylist agent core: tool-loop, forced output, grounding guardrail

## Task Summary

This task builds the AI-native heart of Ensemble: a `com.ensemble.stylist`
package that turns a free-text vibe into a **grounded** outfit built only from
owned items. A Sonnet 5 tool-loop (`searchWardrobe` → forced `record_outfit`)
proposes a pick; the `StylistService` guardrail validates every returned id
against the real wardrobe, feeds hallucinated ids back for **exactly one** retry,
renders only the validated subset, and never surfaces an unvalidated id. All
logic is exercised under a **mocked** Claude client — no key, no network.

## What This Task Proves

- The stylist produces a grounded outfit from valid model output.
- A hallucinated id triggers exactly one retry, after which only the validated
  subset is rendered; a still-invalid id is dropped.
- Zero valid ids after the retry (and malformed/empty output) degrade to a safe
  `StylistUnavailableException`, never a crash and never an unvalidated id.
- The stylist request carries **text tags + wear-history only — no image bytes**.
- An empty wardrobe returns a friendly empty outfit without invoking the model.
- The critical logic (grounding/id-validation + forced-output parsing) has
  **100% line and 100% branch** coverage.

## Evidence Summary

- `./gradlew test -PskipFrontend` is green: **137 tests, 0 failures, 0 errors**
  (26 new stylist/config tests).
- JaCoCo shows **100% branch** on `StylistService` and `OutfitParser` — the two
  critical classes — and ≥90% line across the new package.
- The `searchWardrobe` payload is built from `WardrobeService.list()` and
  excludes `photoUrl`, so it is byte-free by construction.

## Artifact: Stylist package test suite (mocked Claude)

**What it proves:** Every grounding/parsing/loop behavior in the spec's Unit 1
functional requirements works under mocked-Claude conditions.

**Why it matters:** These are the strict-TDD tests that define grounded
recommendation — the guarantee the demo never shows clothes the user doesn't own.

**Command:**

```bash
./gradlew test --tests 'com.ensemble.stylist.*' --tests 'com.ensemble.config.AnthropicPropertiesTest' -PskipFrontend
```

**Result summary:** All suites pass — the guardrail, parser, SDK loop, and the
new stylist-model property are green.

```
AnthropicStylistModelClientTest    tests=4  failures=0 errors=0
OutfitParserTest                   tests=10 failures=0 errors=0
OutfitTest                         tests=3  failures=0 errors=0
StylistServiceTest                 tests=9  failures=0 errors=0
AnthropicPropertiesTest            tests=14 failures=0 errors=0
```

Named cases mapping to spec Unit 1 (from `StylistServiceTest`):

- `styleRequest_withValidOutput_returnsGroundedOutfit`
- `styleRequest_withHallucinatedId_retriesOnceThenRendersValidSubset`
- `styleRequest_retryStillPartiallyInvalid_rendersOnlyValidSubset`
- `styleRequest_allIdsInvalidAfterRetry_returnsError`
- `styleRequest_withMalformedOutput_handledSafely`
- `styleRequest_sendsNoImageBytesToModel`
- `styleRequest_apiError_degradesGracefully`
- `styleRequest_emptyWardrobe_returnsFriendlyResponse`
- `styleRequest_retryFeedbackContainsInvalidIds`

## Artifact: Full backend suite

**What it proves:** The new package integrates without breaking any existing
behavior (the `AnthropicProperties` record gained a 4th component).

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `BUILD SUCCESSFUL` — 137 tests, 0 failures, 0 errors.

## Artifact: JaCoCo coverage on critical logic

**What it proves:** Grounding/id-validation (`StylistService`) and forced-output
parsing (`OutfitParser`) meet the mandated **100% branch** bar; the whole package
clears ≥90% line.

**Why it matters:** These two classes are where a bug would surface a hallucinated
garment; 100% branch is the project's contract for them (`docs/TESTING.md`).

**Command:**

```bash
./gradlew jacocoTestReport -PskipFrontend
# report: build/reports/jacoco/test/html/index.html
```

**Result summary:** Both critical classes are fully covered; the SDK adapter
exceeds the ≥90% line target.

| Class | Line | Branch |
| --- | --- | --- |
| `StylistService` (grounding guardrail) | 47/47 (100%) | 14/14 (**100%**) |
| `OutfitParser` (forced-output parsing) | 17/17 (100%) | 16/16 (**100%**) |
| `Outfit` (value object) | 6/6 (100%) | 4/4 (100%) |
| `AnthropicStylistModelClient` (SDK loop) | 75/77 (97%) | 20/22 (91%) |

## Reviewer Conclusion

The grounded-reasoning core is implemented and proven under a mocked Claude
client: valid picks are grounded, hallucinated ids are caught and retried exactly
once, ungroundable/malformed/upstream failures degrade safely, no image bytes
reach the stylist, and the critical guardrail + parser carry 100% branch
coverage. The HTTP endpoint (Task 2) and the chat UI (Task 3) build on this core.
