# Task 02 Proofs — Style API endpoint + DTOs + edge-case handling

## Task Summary

This task exposes the stylist over HTTP at `POST /api/style`. It accepts a
free-text vibe (`StyleRequest`) and returns a grounded outfit (`StyleResponse`:
`itemIds`, `reason`, and a render-ready `items[]` of `{itemId, photoUrl}`) —
DTOs only, no service/Claude/DynamoDB internals leak. An empty wardrobe returns a
friendly `200`; an upstream Claude failure or ungroundable pick maps through
`ApiExceptionHandler` to a graceful `503`.

## What This Task Proves

- `POST /api/style` returns `200` with the grounded ids, reason, and a `photoUrl`
  per id for rendering.
- An empty wardrobe returns `200` with empty `itemIds`/`items` and an explanatory
  `reason` (the model is never invoked against nothing — proven at the service
  layer in Task 1).
- An upstream/ungroundable failure returns a graceful `503` with a sanitized
  `{"error":"stylist_unavailable"}` body — no stack trace, no unvalidated id.
- `StyleController` is registered in `ApiExceptionHandler` `assignableTypes`, so
  its errors route through the shared handler.

## Evidence Summary

- `StyleControllerTest` (`@WebMvcTest`, mocked `StylistService`): **3 tests, 0
  failures**.
- Full backend suite green: **140 tests, 0 failures, 0 errors**.
- `ApiExceptionHandler` now lists `StyleController.class` (grep proof below).

## Artifact: Style endpoint contract + error-path tests

**What it proves:** The request/response DTO shape and both edge cases
(empty wardrobe, upstream failure) behave per the spec, under a mocked service so
no key/network is needed.

**Why it matters:** This is the stable contract the frontend (Task 3) codes
against; the error mapping guarantees the UI never sees a raw 500 or a
hallucinated id.

**Command:**

```bash
./gradlew test --tests 'com.ensemble.stylist.web.StyleControllerTest' -PskipFrontend
```

**Result summary:** All three cases pass.

```
StyleControllerTest tests=3 failures=0 errors=0
```

- `postStyle_valid_returns200WithOutfit`
- `postStyle_emptyWardrobe_returnsFriendlyResponse`
- `postStyle_upstreamFailure_returnsGracefulError`

## Artifact: Controller registered with the shared exception handler

**What it proves:** `StyleController` errors are handled by `ApiExceptionHandler`
(otherwise `StylistUnavailableException` would fall through to a raw 500).

**Command:**

```bash
grep -n "StyleController.class" src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java
```

**Result summary:** Present at line 32 — the handler's `assignableTypes` now
covers the stylist controller, and a `StylistUnavailableException` → `503`
handler was added.

```
32:	StyleController.class
```

## Artifact: End-to-end live curl (reviewer-runnable — requires a Claude key)

**What it proves:** The endpoint returns owned item ids + a reason end-to-end
against a live Sonnet 5 call.

**Why it matters:** Demonstrates the real integration beyond the mocked slice.

**Status:** NOT run in this environment — no `ENSEMBLE_ANTHROPIC_API_KEY` is set
here, and per repo standards tests never need one. This artifact is
reviewer-runnable: with a key in `.env`, DynamoDB Local up, and the backend
running:

```bash
docker compose up -d dynamodb
./gradlew bootRun &                       # backend on :8080 (key from .env)
# add at least one item first (see README "Wardrobe Storage"), then:
curl -s -X POST localhost:8080/api/style \
  -H 'content-type: application/json' \
  -d '{"prompt":"streetwear today"}'
# -> {"itemIds":["<owned-id>",...],"reason":"...","items":[{"itemId":"<owned-id>","photoUrl":"/api/items/<owned-id>/photo"}]}
```

A keyless end-to-end check is also possible: with the backend up and an **empty**
wardrobe, the same `curl` returns `200` with empty `itemIds` and a friendly
`reason` (the empty-wardrobe path short-circuits before any model call).

## Reviewer Conclusion

The HTTP contract and both edge cases are proven by the MockMvc suite and the
full green backend build; the controller is wired into the shared exception
handler. The live-key curl is documented and reviewer-runnable, deliberately not
fabricated here since no key is present.
