# Task 02 Proofs — Per-garment rationale from the stylist agent

## Task Summary

This task extends the stylist agent so every chosen garment carries its own
one-line rationale, sourced from the model's forced output and kept grounded like
the whole-look reason. The `record_outfit` tool now emits
`{reason, pieces:[{itemId, rationale}]}`; the parser derives ids + a
rationale-by-id map (keeping the legacy `itemIds` shape as a fallback); the
grounding guardrail carries rationale only for validated ids; and
`StyleResponse.OutfitItem` is enriched with the rationale plus the item's stored
tags (category, primaryColor, formality, warmth, descriptors) for the spec sheet.

## What This Task Proves

- The forced-output parser turns the `pieces` shape into ids + per-item
  rationale, and degrades safely on every malformed/partial variant (100% branch).
- The grounding guardrail rejects a hallucinated piece id, feeds it back, retries
  exactly once, and returns only owned ids with their rationale (100% branch).
- The `/api/style` response carries per-item rationale + stored tags, with the
  tags sourced deterministically from the wardrobe (not the LLM).
- The stylist still sends no image bytes; the empty-wardrobe 200 is unchanged.

## Evidence Summary

- `./gradlew test -PskipFrontend` → BUILD SUCCESSFUL, 203 tests, all green.
- JaCoCo branch coverage: `OutfitParser` 30/30, `StylistService` 18/18,
  `Outfit` 6/6 — 0 missed branches on the critical logic.
- The enriched `/api/style` response shape is asserted by `StyleControllerTest`.

## Artifact: Full backend suite green

**What it proves:** The extended output, parser, grounding, DTO, and controller
integrate without regressing any existing behavior.

**Why it matters:** The contract change touches the stylist's core path and its
existing fixtures; a green full suite shows back-compat held.

**Command:**

~~~bash
./gradlew test -PskipFrontend
~~~

**Result summary:** `BUILD SUCCESSFUL`; 203 tests total, including the full
`com.ensemble.stylist.*` package.

## Artifact: 100% branch coverage on the critical logic

**What it proves:** Every branch of forced-output parsing and id-grounding is
exercised (the AGENTS.md 100%-branch bar for these classes).

**Why it matters:** These sit between an untrusted model response and what the
user sees; an untested branch is exactly where a hallucinated id could leak.

**Command:**

~~~bash
./gradlew test -PskipFrontend jacocoTestReport
# parsed from build/reports/jacoco/test/jacocoTestReport.xml
~~~

**Result summary:**

~~~text
Outfit           BRANCH covered=6  missed=0
StylistService   BRANCH covered=18 missed=0
OutfitParser     BRANCH covered=30 missed=0
~~~

## Artifact: Enriched `/api/style` response shape (sanitized)

**What it proves:** Each returned item carries the stylist's per-item rationale
plus the stored tags the spec sheet renders; tags come from the wardrobe join,
not the model.

**Why it matters:** This is the contract the frontend spec sheet consumes in
tasks 3–5.

**Artifact path:** asserted by `StyleControllerTest.postStyle_enrichesItemsWithRationaleAndStoredTags`

**Result summary:** For a grounded look of items `a`, `b`, the endpoint returns:

~~~json
{
  "itemIds": ["a", "b"],
  "reason": "brunch-ready",
  "items": [
    {
      "itemId": "a",
      "photoUrl": "/api/items/a/photo",
      "rationale": "breathes on a warm morning",
      "category": "shirt",
      "primaryColor": "white",
      "formality": 3,
      "warmth": 2,
      "descriptors": ["linen"]
    },
    {
      "itemId": "b",
      "photoUrl": "/api/items/b/photo",
      "rationale": "earthy tone lifts the look",
      "category": "chinos",
      "primaryColor": "olive",
      "formality": 3,
      "warmth": 2,
      "descriptors": ["linen"]
    }
  ]
}
~~~

## Artifact: Tool schema + prompt request per-item rationale, byte-free

**What it proves:** The forced `record_outfit` schema requires a `pieces` array
carrying a per-item `rationale`, the prompt/description ask for it, and no image
bytes are ever sent.

**Why it matters:** The per-item rationale must be a first-class part of the
forced structured output, and the text-only guarantee must hold.

**Artifact path:** `AnthropicStylistModelClientTest.recordOutfitTool_requestsPerItemRationale_inSchemaPromptAndDescription` (+ the existing `assertNoImageBlocks` checks)

**Result summary:** The captured request's `record_outfit` schema contains
`pieces`/`rationale` and requires `pieces`; the tool description and system prompt
both mention the per-piece rationale; every forwarded turn is plain text.

## Reviewer Conclusion

The stylist now explains each garment, the per-item rationale is grounded and
retried exactly like the whole-look reason, and the deterministic tags remain
tool-sourced — all at 100% branch coverage on the critical parser/guardrail, with
the full backend suite green.
