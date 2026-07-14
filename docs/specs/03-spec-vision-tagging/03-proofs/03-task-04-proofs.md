# Task 04 Proofs — Tag-preview endpoint `POST /api/items/tag`

## Task Summary

This task proves the HTTP surface of the vision slice: `POST /api/items/tag`
accepts a garment photo as `multipart/form-data` and returns a `TagSuggestion` as
JSON without persisting anything. A good suggestion returns `200`; a degraded or
empty suggestion still returns `200` (never `500`) so the client can fall back to
manual entry; a missing or non-decodable photo returns a sanitized `400` through
the existing `@RestControllerAdvice`.

## What This Task Proves

- `POST /api/items/tag` returns the suggested tags as JSON at `200` (FR2.1).
- A degraded/empty `TagSuggestion` is still returned at `200`, never `500` (FR2.5).
- A missing `photo` part and a service `InvalidImageException` both return `400`
  with the sanitized `{error:"bad_request", message:"invalid request"}` body,
  reusing the shared advice (FR2.4, FR2.6).
- `TagSuggestion` is all-nullable and serializes to valid JSON even when every
  field is null; it carries no bean-validation constraints, unlike `TagRequest`
  (FR2.2).
- The tag path touches no persistence — no `WardrobeRepository`, `PhotoStorage`,
  or DynamoDB reference exists in the tagging package (FR2.3).

## Evidence Summary

- `TaggingControllerTest` (`@WebMvcTest`, mocked service) covers good→200,
  degraded→200, missing-part→400, and non-image→400 sanitized body.
- `TagSuggestionTest` confirms an all-null suggestion serializes to valid JSON.
- A source grep confirms the tagging package imports no persistence type.
- JaCoCo: `TaggingController` LINE 4/4 (100%), `TagSuggestion` LINE 3/3 — the
  tagging web layer exceeds the ≥90% line bar.

## Artifact: Web-layer contract tests pass (mocked service)

**What it proves:** The endpoint's status/JSON contract and its non-blocking
fallback behave as specified, verified without a service or network dependency.

**Why it matters:** This is the API-contract evidence: it locks the 200-vs-400
split and the reuse of the sanitized error advice for the new controller.

**Command:**

~~~bash
./gradlew test --tests 'com.ensemble.tagging.web.TaggingControllerTest' \
                --tests 'com.ensemble.tagging.dto.TagSuggestionTest' -PskipFrontend
~~~

**Result summary:** `BUILD SUCCESSFUL`; all cases green.

~~~text
tag_goodSuggestion_returns200Json
tag_degradedEmptySuggestion_stillReturns200
tag_missingPhotoPart_returns400
tag_nonImage_serviceThrowsInvalidImage_returns400_sanitizedBody
allNullSuggestion_serializesToValidJson
empty_isTheAllNullSuggestion
~~~

## Artifact: Sanitized 400 body reuses the shared advice

**What it proves:** `ApiExceptionHandler` now lists `TaggingController` in its
`assignableTypes`, so a missing/invalid photo returns the same generic body the
wardrobe API uses — no binding or exception internals leak.

**Why it matters:** FR2.6 requires the new endpoint's errors to be covered by the
existing advice rather than a bespoke handler that could leak internals.

**Result summary:** The `400` response body asserted in the test is exactly
`{"error":"bad_request","message":"invalid request"}`.

~~~java
@RestControllerAdvice(assignableTypes = {WardrobeController.class, TaggingController.class})
~~~

## Artifact: Tag path persists nothing (inspection)

**What it proves:** No persistence type is referenced anywhere in the tagging
package, so tagging cannot create an item record or store a photo.

**Why it matters:** FR2.3 / Non-Goal #4 — tagging is a standalone suggestion step;
nothing is written until the user saves through the existing create endpoint.

**Command:**

~~~bash
grep -rnE "WardrobeRepository|PhotoStorage|DynamoDb|software\.amazon" src/main/java/com/ensemble/tagging/
~~~

**Result summary:** No matches — the tagging package imports only its own service,
the seam, the shared `ImageProcessor`, and the `TagSuggestion` DTO.

~~~text
NONE — tag path touches no persistence
~~~

## Artifact: Tagging web-layer coverage

**What it proves:** The new endpoint and DTO are fully line-covered.

**Why it matters:** The slice's ≥90% line bar applies to the new web layer.

**Result summary:** `TaggingController` LINE 4/4, `TagSuggestion` LINE 3/3.

~~~text
[com/ensemble/tagging/web] TaggingController  LINE 4/4
[com/ensemble/tagging/dto] TagSuggestion      LINE 3/3
~~~

## Reviewer Conclusion

The tag-preview endpoint exposes the tagging service over HTTP with a DTO-only
boundary, returns `200` for both good and degraded suggestions, maps missing/invalid
photos to a sanitized `400` via the shared advice, and persists nothing — all
verified by mocked web-layer tests and source inspection.
