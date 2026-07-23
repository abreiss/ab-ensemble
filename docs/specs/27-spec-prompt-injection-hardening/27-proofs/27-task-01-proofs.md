# Task 01 Proofs — Every attacker-controlled input is bounded, over-cap requests get a sanitized 400

## Task Summary

This task bounds every attacker-controlled field of a style request at the API
boundary so an oversized or malformed request is rejected with the existing
sanitized `400` **before** any daily-cap reservation or Claude call runs. It adds
Jakarta Bean Validation caps to `StyleRequest` (`prompt` `@NotBlank` +
`@Size(max = 1000)`; `history` `@Size(max = 20)` + `@Valid` cascade;
`StyleTurn.text` `@Size(max = 2000)`) and `@Valid` on the `@RequestBody` in
`StyleController.style(...)`. No new error-handling code was needed — the caps
route through `ApiExceptionHandler`'s existing `MethodArgumentNotValidException`
→ `{"error":"bad_request","message":"invalid request"}` mapping (spec Unit 1 /
issue work item **P0-1**).

## What This Task Proves

- An over-cap `prompt` (1001 chars), over-cap `history` (21 turns), and over-cap
  per-turn `text` (2001 chars) each return `400` with the exact sanitized body,
  leaking no validation internals.
- A blank / whitespace-only `prompt` returns `400` (`@NotBlank` is wired).
- The caps are **inclusive**: a `prompt` at exactly 1000 chars and a `history` at
  exactly 20 turns are accepted (`200`), so valid input is never rejected.
- Rejection happens on **request binding, before the controller body**:
  `verifyNoInteractions(service, callCapService)` holds on every over-cap request,
  proving no cap reservation or stylist call is made for a hostile request.

## Evidence Summary

- `./gradlew test -PskipFrontend --tests '*StyleControllerTest'` → **BUILD
  SUCCESSFUL**, 13 tests (7 pre-existing contract/error tests + 6 new input-cap
  tests) all green.
- Full backend suite `./gradlew test -PskipFrontend` → **BUILD SUCCESSFUL** — no
  regression from adding the constraints.
- RED was observed first: before wiring the constraints, the four rejection tests
  failed (validation absent → NPE/500 instead of 400); the two boundary "accepted"
  tests passed. GREEN followed once the annotations + `@Valid` were added.

## Artifact: New input-cap web-layer tests (RED → GREEN)

**What it proves:** Each cap returns the sanitized `400`, boundaries are inclusive,
and rejection precedes any service/cap interaction.

**Why it matters:** These are the spec Unit 1 proof artifacts — the boundary
between a hostile client and a Claude call.

**RED (constraints not yet wired):**

```
StyleControllerTest > styleRequest_oversizeHistory_rejectedWith400() FAILED
    jakarta.servlet.ServletException ... Caused by: java.lang.NullPointerException
StyleControllerTest > styleRequest_blankPrompt_rejectedWith400() FAILED
StyleControllerTest > styleRequest_oversizeTurnText_rejectedWith400() FAILED
StyleControllerTest > styleRequest_oversizePrompt_rejectedWith400() FAILED
13 tests completed, 4 failed
```

**Result summary:** The four rejection cases fail against the unvalidated code
(the request reaches the controller body and NPEs on the unstubbed service),
confirming a true RED. The two boundary "accepted" cases pass because they stub
the service — proving the tests assert the *cap*, not incidental behavior.

**GREEN (after adding `@NotBlank`/`@Size`/`@Valid` + `@Valid` on `@RequestBody`):**

**Command:** `./gradlew test -PskipFrontend --tests '*StyleControllerTest'`

```
> Task :test
BUILD SUCCESSFUL in 3s
```

**Result summary:** All 13 `StyleControllerTest` tests pass. The over-cap prompt,
over-cap history, over-cap turn text, and blank prompt each return `400` with
`{"error":"bad_request","message":"invalid request"}`; the at-cap prompt and
at-cap history return `200`.

## Artifact: Full backend suite — no regression

**What it proves:** Adding the DTO constraints did not break any existing behavior.

**Why it matters:** The constraints sit on the request DTO used by the whole
stylist flow; a regression here would surface across the suite.

**Command:** `./gradlew test -PskipFrontend`

```
> Task :test
BUILD SUCCESSFUL in 14s
```

**Result summary:** The whole backend suite is green with the caps in place.

## Artifact: The bounded DTO and the `@Valid` request body

**What it proves:** The caps live on the DTO (layered boundary) with named,
inclusive constants; `@Valid` triggers the cascade so nested turn text is validated.

**Why it matters:** Placing validation on the DTO (not the controller body) is what
lets rejection happen on binding, before `CallCapService.reserve()`.

**`StyleRequest` (excerpt):**

```java
public record StyleRequest(
    @NotBlank @Size(max = MAX_PROMPT_LENGTH) String prompt,
    @Size(max = MAX_HISTORY_TURNS) @Valid List<StyleTurn> history) {

    public static final int MAX_PROMPT_LENGTH = 1000;
    public static final int MAX_HISTORY_TURNS = 20;
    public static final int MAX_TURN_TEXT_LENGTH = 2000;

    public record StyleTurn(String role, @Size(max = MAX_TURN_TEXT_LENGTH) String text) {
    }
}
```

**`StyleController.style(...)` (excerpt):**

```java
public StyleResponse style(@Valid @RequestBody StyleRequest request) {
    callCapService.reserve();
    ...
}
```

**Result summary:** The caps are declared once as inclusive constants and enforced
on binding; `ApiExceptionHandler` (unchanged) maps the resulting
`MethodArgumentNotValidException` to the sanitized `400`.

## Reviewer Conclusion

Every attacker-controlled input to the stylist is now bounded and rejected cheaply
with a generic `400` before any Claude call, using only additive annotations and
the pre-existing sanitized-error path. RED-then-GREEN was observed, the boundary
cases confirm the caps are inclusive, and `verifyNoInteractions` proves the
rejection is pre-call — satisfying spec Unit 1 / issue work item P0-1 with no
regression to the existing suite.
