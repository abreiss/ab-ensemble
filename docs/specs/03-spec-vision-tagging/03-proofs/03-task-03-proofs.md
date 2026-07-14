# Task 03 Proofs — TaggingService: vision JSON → tags + graceful fallback

## Task Summary

This task proves the domain core of the vision slice: `TaggingService` turns a
garment photo into the wardrobe's structured tag shape by downsizing the image
through the shared `ImageProcessor`, calling the mockable `VisionModelClient`
seam, and mapping/clamping the model's JSON onto an all-nullable `TagSuggestion`.
Every failure mode degrades to a partial/empty suggestion instead of throwing —
except a non-decodable image, which is deliberately allowed to propagate so the
endpoint can answer `400`.

## What This Task Proves

- A valid vision JSON maps to the 6 scalar tags (`category`, `primaryColor`,
  `secondaryColor`, `formality`, `pattern`, `warmth`) plus `descriptors` (FR1.4).
- Numeric tags are clamped/validated: `formality` outside 1–5, `warmth` outside
  1–3, and non-numeric values are left empty rather than passed through or thrown
  (FR1.5, critical branch).
- Every failure — `null`/blank model output, malformed JSON, an API error, and a
  timeout — returns a partial/empty `TagSuggestion` and never throws (FR1.6,
  critical branch).
- The image-guard failure (`InvalidImageException`) is **not** swallowed: it
  propagates out of `suggest`, locking the 400-vs-200 split the endpoint relies on.
- The bytes sent to the model are the resized JPEG, not the raw upload.

## Evidence Summary

- `TaggingServiceTest` (19 cases, mocked seam + mocked `ImageProcessor`) passes
  with **no `ANTHROPIC_API_KEY` and no network call**.
- JaCoCo shows **100% branch (24/24)** and **100% line (33/33)** on
  `TaggingService` — meeting the 100%-branch bar for the critical mapping +
  fallback logic, and exceeding the ≥90% line bar for the tagging package.

## Artifact: TaggingServiceTest passes with a mocked seam (no live call)

**What it proves:** The mapping, numeric clamping, and every fallback path behave
as specified, verified without any Claude API key or network access.

**Why it matters:** This is the headline TDD evidence for the slice's critical
logic — the two paths the spec singles out for 100% branch coverage.

**Command:**

~~~bash
./gradlew test --tests 'com.ensemble.tagging.TaggingServiceTest' -PskipFrontend
~~~

**Result summary:** `BUILD SUCCESSFUL`; all 19 test cases green.

~~~text
> Task :test
BUILD SUCCESSFUL in 3s
5 actionable tasks: 3 executed, 2 up-to-date
~~~

Cases exercised (each names the behavior it locks):

~~~text
validResponse_mapsSixScalarsPlusDescriptors
missingScalarField_isLeftNull_othersMapped
nonTextualScalar_isLeftNull
missingDescriptors_isNull_and_nonArrayDescriptors_isNull
descriptorsArray_keepsStrings_dropsNonStrings
formalityBelowRange_isLeftNull / formalityAboveRange_isLeftNull
warmthBelowRange_isLeftNull / warmthAboveRange_isLeftNull
nonNumericFormality_isLeftNull
seamReturnsNull_returnsEmptySuggestion / seamReturnsBlank_returnsEmptySuggestion
malformedJson_returnsEmptySuggestion_noThrow
apiError_seamThrows_returnsEmptySuggestion_noThrow
timeout_seamThrows_returnsEmptySuggestion_noThrow
invalidImage_propagates_andSeamIsNeverCalled
suggest_downsizesBeforeCallingTheModel
seamReturnsEmptyObject_returnsEmptySuggestion
descriptors_ignoredList_doesNotAffectScalars
~~~

## Artifact: 100% branch + line coverage on the critical logic

**What it proves:** The vision-JSON→tag mapping and the fallback path are fully
branch-covered — no untested clamp bound, absent-field case, or failure mode.

**Why it matters:** The spec requires **100% branch** on exactly these methods;
this is the measured gate, not an assertion.

**Command:**

~~~bash
./gradlew test jacocoTestReport -PskipFrontend
# parsed from build/reports/jacoco/test/jacocoTestReport.xml
~~~

**Result summary:** `TaggingService` — BRANCH missed=0 covered=24 (**100%**),
LINE missed=0 covered=33 (**100%**).

~~~text
== package com/ensemble/tagging ==
  TaggingService              BRANCH miss=0 cov=24   LINE miss=0 cov=33
  TagSuggestion (dto)         BRANCH miss=0 cov=0    LINE miss=0 cov=3
~~~

## Reviewer Conclusion

`TaggingService` implements the vision-JSON→tag mapping and the graceful fallback
with 100% branch coverage, the seam is mocked (no key, no network), and the
non-image path propagates so the endpoint can enforce the 400-vs-200 split. The
domain core of the slice is complete and fully covered.
