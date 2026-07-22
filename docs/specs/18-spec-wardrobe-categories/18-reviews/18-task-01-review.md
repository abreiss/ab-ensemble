# Review: Task 1.0 — Backend taxonomy foundation

**Verdict: PASS**

**Commit inspected:** `47de36a7628ec839cc0d8114f4c443288f0aa5bb`
**Scope reviewed:** Spec 18 Unit 1 / parent task 1.0 (sub-tasks 1.1–1.7)

## What was checked

- Read spec Unit 1 (FRs + proof artifacts), the parent-task 1.0 description/
  sub-tasks, and the proof file end-to-end.
- Read the full commit diff (13 files) against each sub-task's stated intent.
- Independently re-ran the backend suite: `./gradlew test jacocoTestReport
  -PskipFrontend --rerun-tasks` → `BUILD SUCCESSFUL`, 286 tests / 0 failures
  (matches the proof file's headline number).
- Parsed the JaCoCo XML directly for `CategoryTaxonomy`: `BRANCH missed="0"
  covered="6"`, `LINE missed="0" covered="23"`, `INSTRUCTION covered="312"` —
  matches the proof file's cited counters exactly (not fabricated/stale).
- Confirmed `git diff --stat src/main/java/com/ensemble/wardrobe/Item.java`
  is empty (no schema change), matching FR 1.6 / the proof claim.
- Grepped the proof file for secret patterns (API keys, passcodes) — none
  found.

## Findings

**Implementation matches every sub-task:**
- 1.1/1.2 — `CategoryTaxonomy` (new) has the exact 8-value ordered list, a
  case/whitespace-insensitive synonym map, and a `normalize` that never
  throws (null → check, blank → check, unrecognized → `"Other"` via
  `getOrDefault`). `CategoryTaxonomyTest` exercises every canonical value,
  representative legacy synonyms per bucket, casing/whitespace, and
  null/blank/unrecognized — matches 1.1's spec exactly.
- 1.3 — `TagRequest.formality`/`warmth` lost `@NotNull`, kept `@Min`/`@Max`;
  `category` still `@NotBlank`. New `WardrobeControllerTest` case
  (`createItem_jewelryWithoutFormalityOrWarmth_returns201`) posts a
  multipart create with only `category`/`primaryColor` and asserts `201`
  plus null formality/warmth reaching the captured `TagRequest`. Existing
  out-of-range-rejection tests are untouched (still present, so still
  enforced).
- 1.4 — `tagTool()` made package-private; `category` schema property now
  carries `"enum", CategoryTaxonomy.values()`. New test reads the built
  `Tool.InputSchema` and asserts the enum list exactly matches
  `CategoryTaxonomy.values()`.
- 1.5 — `TaggingService.map` routes the model's raw category through a new
  `normalizedCategory` helper that calls `CategoryTaxonomy.normalize` only
  when the raw value is non-null (preserves the "absent/non-textual → null"
  placeholder behavior instead of defaulting to `"Other"`, which is the
  correct call for the downstream `<select>` UX). Two new tests cover
  off-taxonomy (`"sweatshirt"` → `"Top"`) and already-canonical
  (`"Jewelry"` passthrough); three pre-existing category assertions were
  updated in place to their now-normalized values (`"top"`→`"Top"`,
  `"shoes"`→`"Shoes"`, `"jacket"`→`"Jacket"`) rather than silently
  weakened — this is a correct, expected consequence of 1.5, not scope
  creep.
- 1.6 — `ItemMapper.applyTags` now sets `item.setCategory(CategoryTaxonomy
  .normalize(tags.category()))` — the single choke point, as specced. New
  `ItemMapperTest` case proves `"chinos"` → `"Bottom"`; a second new case
  proves a Jewelry item with null formality/warmth persists cleanly
  (belt-and-suspenders coverage beyond what 1.6 strictly asked for, in the
  right direction).
- 1.7 — `WardrobeServiceTest` updates the pre-existing `create`/`updateTags`
  assertions from `"top"` to `"Top"` (proving the choke point fires on
  both paths through the real service, not just the mapper unit) and adds
  an explicit `updateTags_legacyCategory_persistsNormalizedTaxonomyValue`
  case for extra choke-point coverage on the update path specifically.

**Tests are real, not trivial:** every new/changed assertion binds to a
concrete taxonomy value derived from actual normalization logic (not
tautological `assertThat(x).isEqualTo(x)` patterns), and pre-existing
green tests were updated rather than deleted/weakened to accommodate the
behavior change.

**Proof file:** Evidence in `18-task-01-proofs.md` reproduces cleanly —
the cited JaCoCo counters (`BRANCH missed="0" covered="6"` etc.) match a
fresh local re-run byte-for-byte, and the cited code snippets match the
diff verbatim. No secrets or leaked tokens. No fabrication found.

## Non-blocking nit

- The proof file is internally inconsistent on the full-suite test count:
  the "Full suite" bullet (line 54) and the closing "Reviewer Conclusion"
  (line 304) say **286 tests**, matching the actual re-run; but the
  "Artifact: Full suite green" section header text (line 269) says
  "**287 tests** across every package" before its own Result Summary
  three lines later correctly says 286. Likely a copy-paste slip while
  drafting — doesn't affect correctness of the work, just a one-word
  proof-doc typo worth fixing on a future touch of that file.

## Conclusion

Task 1.0 is genuinely done: every sub-task (1.1–1.7) is implemented as
described, the taxonomy/normalize logic is correctly the single guarded
choke point on both save paths and the vision pre-fill path, jewelry
nullability is real and verified end-to-end via the controller test, the
vision schema enum is derived (not duplicated) from the same constant,
`Item.java`/the DynamoDB schema is untouched, and 100% branch / line
coverage on `CategoryTaxonomy` is real and independently reproduced. The
only issue found is a cosmetic test-count typo in the proof file's prose,
not a functional or process defect.
