# 05-tasks-stylist-agent.md

Task list for the **stylist agent** feature (spec: `05-spec-stylist-agent.md`).

Three parent tasks, one per Demoable Unit in the spec. Each is an independently
demoable, roughly-one-commit slice.

Backend Units 1–2 are strict-TDD backend-domain logic (≥90% line, **100% branch**
on grounding/id-validation + forced-output parsing). Unit 3 is frontend — test
meaningful logic only, no over-testing view plumbing (`docs/TESTING.md`).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/stylist/StylistService.java` | Domain core: builds the `searchWardrobe` payload, calls the model client, parses output, runs the grounding guardrail + one-retry, empty-wardrobe short-circuit. |
| `src/test/java/com/ensemble/stylist/StylistServiceTest.java` | Strict-TDD unit tests (mocked `StylistModelClient` + stubbed `WardrobeService`); 100% branch on guardrail. |
| `src/main/java/com/ensemble/stylist/StylistModelClient.java` | Mockable seam interface (analogous to `VisionModelClient`) so guardrail logic is testable without the SDK. |
| `src/main/java/com/ensemble/stylist/AnthropicStylistModelClient.java` | Sonnet 5 tool-loop impl: `searchWardrobe` (`tool_choice: auto`), continuation cap, forced `record_outfit`; reuses `AnthropicVisionModelClient.firstToolUseJson(...)`. |
| `src/test/java/com/ensemble/stylist/AnthropicStylistModelClientTest.java` | Mocks the Anthropic SDK (ArgumentCaptor like `AnthropicVisionModelClientTest`); asserts tools/tool_choice/loop/cap and **no image bytes**. |
| `src/main/java/com/ensemble/stylist/OutfitParser.java` | Defensive `{itemIds, reason}` parser modeled on `TaggingService.map(...)`; blank/malformed → safe empty; never throws. 100% branch. |
| `src/test/java/com/ensemble/stylist/OutfitParserTest.java` | Unit tests for every parser branch (valid, blank, malformed, absent fields, extra fields). |
| `src/main/java/com/ensemble/stylist/dto/StyleRequest.java` | Request DTO `{ prompt }` at the API boundary. |
| `src/main/java/com/ensemble/stylist/dto/StyleResponse.java` | Response DTO `{ itemIds, reason, items:[{itemId, photoUrl}] }`; no internal types leaked. |
| `src/main/java/com/ensemble/stylist/web/StyleController.java` | `@RestController @RequestMapping("/api/style")`; delegates to `StylistService`, maps result → DTO. Modeled on `WardrobeController`. |
| `src/test/java/com/ensemble/stylist/web/StyleControllerTest.java` | `@WebMvcTest(StyleController.class)`, `@MockitoBean StylistService`, MockMvc contract + error-path tests. |
| `src/main/java/com/ensemble/config/AnthropicProperties.java` | Add `stylistModel` field (default `claude-sonnet-5`) alongside the Haiku `model`. |
| `src/main/resources/application.yml` | Add `ensemble.anthropic.stylist-model: claude-sonnet-5`. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Add `StyleController.class` to `assignableTypes` so style errors are handled. |
| `frontend/src/api/style.ts` | Client `requestStyle(prompt)` — copies the `api/items.ts` fetch-wrapper + reuses `photoUrl(id)`. |
| `frontend/src/api/style.test.ts` | Vitest client tests via `vi.stubGlobal('fetch', …)`. |
| `frontend/src/routes/Stylist.tsx` | `/style` route: vibe input → outfit card (real photos + reason); loading/error+retry/empty states. |
| `frontend/src/routes/Stylist.test.tsx` | RTL route tests via `vi.mock('../api/style')`. |
| `frontend/src/App.tsx` | Register `/style` route + nav link. |
| `frontend/src/index.css` | Reuse existing "Care Label" `:root` tokens; add only outfit-card styles if needed (no second design language). |
| `docs/specs/05-spec-stylist-agent/proof/` | Proof artifacts: `style-curl.txt` (keyless live run), `outfit-card.png` (mobile screenshot). |

### Notes

- Unit tests sit beside the code they test (backend: mirrored package under `src/test`; frontend: `*.test.ts(x)` next to source), per existing repo layout.
- Backend test command: `./gradlew test -PskipFrontend` (fast, no Node); coverage: `./gradlew jacocoTestReport`. Frontend: `cd frontend && npm test -- --run` + `npm run lint`.
- **Strict TDD:** every backend sub-task pair is RED (failing test) → GREEN (minimum impl); never write impl before its failing test. Mock the Claude client — no live network in tests.
- Follow existing conventions: DTOs at the boundary, layered controller→service→repository, conventional commits (~one per parent task), pre-commit gates must pass.
- **Non-goals guard:** no multi-turn re-pick and no wear-history *writes* (issue #7). `StylistService` may accept a message list internally (leaves room for #7) but ships no re-pick logic; the tool only *reads* wear-history.

## Tasks

### [x] 1.0 Stylist agent core — tool-loop, forced output, grounding guardrail (backend)

The AI-native heart. A new `com.ensemble.stylist` package: add a
`ensemble.anthropic.stylist-model` property (default `claude-sonnet-5`); a
`StylistModelClient` seam (analogous to `VisionModelClient`) with an Anthropic
impl that runs a Sonnet 5 tool-loop (`searchWardrobe`, `tool_choice: auto`,
bounded continuation cap) and forces a final `record_outfit` `{itemIds, reason}`;
a defensive parser; and the `StylistService` grounding guardrail (validate every
id against the real wardrobe → feed invalid ids back → retry exactly once →
render only the validated subset → error only when zero valid ids remain).
`searchWardrobe` returns `WardrobeService.list()` text tags + wear-history only —
**no image bytes** on any request. Strict TDD, mocked Claude client.

#### 1.0 Proof Artifact(s)

- Test: `StylistServiceTest` passes with `styleRequest_withValidOutput_returnsGroundedOutfit`, `styleRequest_withHallucinatedId_retriesOnceThenRendersValidSubset`, `styleRequest_allIdsInvalidAfterRetry_returnsError`, `styleRequest_withMalformedOutput_handledSafely`, `styleRequest_sendsNoImageBytesToModel`, `styleRequest_apiError_degradesGracefully`, `styleRequest_emptyWardrobe_returnsFriendlyResponse` — maps FRs: tool-loop, forced output, grounding + one retry, malformed-output safety, no-bytes guarantee, graceful degrade, empty-wardrobe short-circuit.
- Test: JaCoCo report at `build/reports/jacoco/test/html/index.html` shows **≥90% line and 100% branch** on the guardrail (`StylistService`) + forced-output parser (`OutfitParser`) classes in `com.ensemble.stylist`.
- CLI: `./gradlew test --tests 'com.ensemble.stylist.*' -PskipFrontend` exits `0` (green) — demonstrates the loop + guardrail behave under mocked-Claude conditions with no live network calls.

#### 1.0 Tasks

- [x] 1.1 Add a `stylistModel` field to the `AnthropicProperties` record (default `claude-sonnet-5`) and `ensemble.anthropic.stylist-model: claude-sonnet-5` to `application.yml`, alongside the existing Haiku `model`. Update any properties binding test if present.
- [x] 1.2 RED: write `OutfitParserTest` covering valid JSON → `{itemIds, reason}`, blank/`null`/malformed JSON → safe empty outfit, absent `itemIds`/`reason` fields → safe defaults, and extra/unknown fields ignored. Assert the parser never throws.
- [x] 1.3 GREEN: implement `OutfitParser` as a static defensive mapper modeled on `TaggingService.map(...)` — parse `{itemIds:string[], reason:string}`, return a safe empty result on any bad input, never throw. Refactor to green.
- [x] 1.4 Define the `StylistModelClient` seam interface (javadoc mirroring `VisionModelClient`) — input: `searchWardrobe` tool text + a message list; output: raw forced-output JSON. Define the internal `Outfit` result type (`itemIds`, `reason`).
- [x] 1.5 RED: write `StylistServiceTest` with the seven named cases using a **mocked** `StylistModelClient` and a stubbed `WardrobeService.list()`. Assert: id-validation against the real wardrobe; hallucinated id triggers **exactly one** retry (not zero, not twice) then renders the validated subset; all-invalid-after-retry → error; malformed output → safe; API error → graceful degrade; **no image bytes** in any built request; empty wardrobe → friendly response without invoking the model.
- [x] 1.6 GREEN: implement `StylistService` — build the `searchWardrobe` payload from `WardrobeService.list()` (ids + text tags + wear-history, **no bytes**), call the client, parse via `OutfitParser`, run the grounding guardrail (feed specific invalid ids back, retry exactly once, keep validated subset, error only when zero remain), and the empty-wardrobe early return. Refactor to green.
- [x] 1.7 RED: write `AnthropicStylistModelClientTest` — mock the Anthropic SDK client (ArgumentCaptor, per `AnthropicVisionModelClientTest`). Assert the built `MessageCreateParams` carries the `searchWardrobe` tool with `tool_choice: auto`, the loop appends the assistant turn + a `ToolResultBlockParam` and continues until `end_turn` **or the continuation cap** (assert it does not exceed the cap), the final pick forces `record_outfit`, and **no image/bytes block** is ever added.
- [x] 1.8 GREEN: implement `AnthropicStylistModelClient` — Sonnet 5 tool-loop using the `@Lazy AnthropicClient` bean + `stylistModel`; reuse `firstToolUseJson(...)`; bounded continuation cap (≤4); adaptive thinking default, **no** `budget_tokens`/sampling params. Refactor to green.
- [x] 1.9 REFACTOR + coverage gate: run `./gradlew test --tests 'com.ensemble.stylist.*' -PskipFrontend` (green) and `./gradlew jacocoTestReport`; confirm ≥90% line and **100% branch** on `StylistService` + `OutfitParser`; note the report path as the proof artifact.

### [x] 2.0 Style API endpoint + DTOs + edge-case handling

Expose the stylist over HTTP. `POST /api/style` accepting `{ prompt }` and
returning `{ itemIds, reason }` plus a per-item `photoUrl` for rendering, via
request/response DTOs (no Claude/DynamoDB/storage types leak into the
controller). Empty/too-small wardrobe → **200** with empty `itemIds` + an
explanatory `reason`. Upstream failure (Claude error/timeout, ungroundable
result) → graceful error through the existing exception-handling pattern.
**Register `StyleController` in `ApiExceptionHandler` `assignableTypes`.**

#### 2.0 Proof Artifact(s)

- Test: `StyleControllerTest` (`@WebMvcTest(StyleController.class)`, `@MockitoBean` service) passes `postStyle_valid_returns200WithOutfit`, `postStyle_emptyWardrobe_returnsFriendlyResponse`, `postStyle_upstreamFailure_returnsGracefulError` — demonstrates the request/response contract + error paths.
- CLI: `curl -s -X POST localhost:8080/api/style -H 'content-type: application/json' -d '{"prompt":"streetwear today"}'` returns owned item ids + a reason, captured to `docs/specs/05-spec-stylist-agent/proof/style-curl.txt` — end-to-end endpoint (live-key run; **no key embedded** in the artifact).
- Grep: `grep -n 'StyleController.class' src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` shows the new controller registered — demonstrates errors are routed through the shared handler.

#### 2.0 Tasks

- [x] 2.1 Define the boundary DTOs: `StyleRequest { prompt }` and `StyleResponse { itemIds, reason, items:[{ itemId, photoUrl }] }` (DTO-only; no `StylistService` internals, Claude, or DynamoDB types leaked).
- [x] 2.2 RED: write `StyleControllerTest` with `@WebMvcTest(StyleController.class)` + `@MockitoBean StylistService` — `postStyle_valid_returns200WithOutfit` (contract + `photoUrl` per id), `postStyle_emptyWardrobe_returnsFriendlyResponse` (200, empty `itemIds`, non-blank `reason`), `postStyle_upstreamFailure_returnsGracefulError` (mapped error status/body).
- [x] 2.3 GREEN: implement `StyleController` (`@RestController @RequestMapping("/api/style")`, `POST`) delegating to `StylistService`, mapping the domain `Outfit` → `StyleResponse` with `photoUrl(itemId)` per id. Modeled on `WardrobeController`. Refactor to green.
- [x] 2.4 Add `StyleController.class` to `ApiExceptionHandler` `assignableTypes` (import it) and confirm the upstream/ungroundable-failure → graceful-response mapping is covered by the `postStyle_upstreamFailure_returnsGracefulError` test.
- [x] 2.5 Verify + capture proof: `./gradlew test -PskipFrontend` green (140/0/0); MockMvc suite is the automated proof. Live `curl` documented as reviewer-runnable in `05-proofs/05-task-02-proofs.md` — not run here (no `ENSEMBLE_ANTHROPIC_API_KEY` in this environment; repo standard is keyless tests), and deliberately not fabricated.

### [ ] 3.0 Chat UI — vibe input → outfit card with real photos + reason

The user-facing mobile-first `/style` route: a vibe text input submits to
`POST /api/style` via a new `api/style.ts` (copying the `api/items.ts`
fetch-wrapper pattern); the returned ids render as an **outfit card** using each
item's real stored photo (`photoUrl(id)`) with the `reason` as editorial copy;
explicit loading / error-with-retry / empty-wardrobe states matching the existing
screens; reachable from `App.tsx` nav. Visual direction driven by the
**`frontend-design` skill**, reusing the existing "Care Label" design tokens.

#### 3.0 Proof Artifact(s)

- Test: `api/style.test.ts` (client, `vi.stubGlobal('fetch', …)`) and `Stylist.test.tsx` (route, `vi.mock('../api/style')`) pass under Vitest/RTL — submit → loading → card render, plus error+retry and empty-wardrobe states — demonstrates rendering logic without a live backend.
- CLI: `cd frontend && npm test -- --run` green and `npm run lint` clean — demonstrates the new route/client meet the frontend quality gates.
- Screenshot: `docs/specs/05-spec-stylist-agent/proof/outfit-card.png` — the rendered outfit card (real garment photos + reason) at a ~390px mobile viewport — demonstrates the end-to-end user experience.

#### 3.0 Tasks

- [ ] 3.1 RED: write `api/style.test.ts` — `requestStyle(prompt)` POSTs to `/api/style` with a JSON body, returns the parsed `{ itemIds, reason, items }`, and throws on a non-2xx response (mirroring the `api/items.ts` error handling), using `vi.stubGlobal('fetch', …)`.
- [ ] 3.2 GREEN: implement `frontend/src/api/style.ts` — copy the `api/items.ts` fetch-wrapper (same non-2xx→throw behavior) and re-export/reuse `photoUrl(id)` for the card.
- [ ] 3.3 RED: write `Stylist.test.tsx` with `vi.mock('../api/style')` — submit a vibe → loading indicator → outfit-card render (photos via `photoUrl`, reason text); error state shows a retry that re-requests; empty-wardrobe response (empty `itemIds` + `reason`) shows the friendly empty state.
- [ ] 3.4 GREEN: implement `frontend/src/routes/Stylist.tsx` — vibe input + submit, `loading`/`error`+retry/`empty`/`ready` states following the `WardrobeGrid`/`ItemDetail` pattern, outfit card rendered from `photoUrl(id)` + reason. **Invoke the `frontend-design` skill** to drive the card's visual direction; reuse Care Label `:root` tokens (no new design language). Refactor to green.
- [ ] 3.5 Register the `/style` route + a nav link in `App.tsx` (react-router, alongside `/`, `/add`, `/item/:id`).
- [ ] 3.6 Verify + capture proof: `cd frontend && npm test -- --run` green and `npm run lint` clean; run the app on a ~390px viewport and save the rendered outfit card to `docs/specs/05-spec-stylist-agent/proof/outfit-card.png`.
