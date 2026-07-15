# 05-spec-stylist-agent.md

## Introduction/Overview

This feature is the heart of Ensemble: the **stylist agent**. Given a free-text vibe ("streetwear today"), Claude Sonnet 5 reasons over the wardrobe's **text tags + wear-history only** (never image bytes), picks a complete outfit from clothes the user owns, and explains why it works. The app then renders the chosen items' stored photos as an outfit card.

The primary goal is **grounded** recommendation: every returned item id must exist in the wardrobe. Hallucinated ids are caught, fed back to the model, retried exactly once, and never rendered. Delete the LLM and this feature cannot exist — a for-loop can pair items randomly but cannot reason over a vibe and justify the call.

## Goals

- Turn a free-text vibe into a **complete, owned-only outfit** (item ids + a human reason) via a Sonnet 5 tool-loop.
- Provide a `searchWardrobe` tool that returns **text tags + wear-history only** — the stylist never receives image bytes.
- Enforce a **grounding guardrail**: validate every returned id, reject hallucinated ids, feed the error back, retry exactly once, and never render an unvalidated id.
- Produce **forced structured output** `{itemIds, reason}` that is always parseable, with malformed output handled safely.
- Render the picked items' **real stored photos** as an outfit card with the reason, on a mobile-first chat screen.

## User Stories

- **As someone getting dressed**, I want to type a vibe and get an outfit built only from clothes I own, so that I get a real, wearable suggestion instead of a shopping list.
- **As a user**, I want the stylist to explain *why* the pieces work together, so that I trust the pick and learn something.
- **As a user with an empty or tiny wardrobe**, I want a clear, friendly message instead of a crash or a made-up item, so that I know to add clothes first.
- **As the app owner**, I want the stylist to be provably grounded (no hallucinated items ever rendered), so that the demo never shows clothes the user doesn't own.

## Demoable Units of Work

### Unit 1: Stylist agent — tool-loop, forced output, grounding guardrail (backend core)

**Purpose:** The AI-native core. A `StylistService` runs a Sonnet 5 tool-loop, calls `searchWardrobe`, forces `{itemIds, reason}`, and validates ids against the real wardrobe. This is the strict-TDD, 100%-branch-coverage heart of the feature.

**Functional Requirements:**
- The system shall expose a `searchWardrobe` tool (no params) that returns **all** items' `itemId` + text tags (`category`, `primaryColor`, `secondaryColor`, `formality`, `pattern`, `warmth`, `descriptors`) + wear-history (`lastWorn`, `wornCount`) — and **no image bytes or photo data**.
- The system shall run a tool-loop: send tools with `tool_choice: auto`, execute `searchWardrobe` when requested, append the assistant turn + tool result, and continue until the model stops requesting tools (`stop_reason == end_turn`), bounded by a small continuation cap.
- The system shall obtain **forced structured output** `{itemIds: string[], reason: string}` (via a forced `record_outfit` tool), and parse it defensively — malformed/blank/absent output must not crash the request.
- The system shall validate that **every** returned `itemId` exists in the wardrobe. If any id is invalid, it shall feed the specific invalid id(s) back to the model and retry the pick **exactly once**.
- After the retry, the system shall render only the **validated subset** of ids (dropping any still-invalid id) and keep the reason. It shall return a safe "couldn't build a grounded outfit" error **only when zero** valid ids remain. An unvalidated id is never rendered.
- The system shall let the model decide the outfit's composition (which/how many pieces) via the system prompt — **no hard slot rules** (works when the wardrobe lacks a full set at demo scale).
- The system shall send the stylist **no image bytes** on any request in the loop (assertable in tests).

**Proof Artifacts:**
- Test: `StylistServiceTest` passes with cases `styleRequest_withValidOutput_returnsGroundedOutfit`, `styleRequest_withHallucinatedId_retriesOnceThenRendersValidSubset`, `styleRequest_allIdsInvalidAfterRetry_returnsError`, `styleRequest_withMalformedOutput_handledSafely`, `styleRequest_sendsNoImageBytesToModel`, `styleRequest_apiError_degradesGracefully` — demonstrates grounded reasoning + 100% branch coverage on guardrail/parsing.
- Test: JaCoCo report shows ≥90% line and 100% branch on the guardrail + forced-output parsing classes.
- CLI: `./gradlew test` green for the stylist package — demonstrates the tool-loop and guardrail behave under mocked-Claude conditions with no live calls.

### Unit 2: Style API endpoint + DTOs + edge-case handling

**Purpose:** Expose the stylist over HTTP with clean DTOs and correct error paths, so the frontend has a stable contract.

**Functional Requirements:**
- The system shall expose `POST /api/style` accepting `{ prompt: string }` (the vibe) and returning `{ itemIds: string[], reason: string }` (plus per-item `photoUrl` for rendering) on success.
- The system shall return **200 with empty `itemIds` and an explanatory `reason`** for an empty (or too-small) wardrobe, rather than invoking the model against nothing.
- The system shall map upstream failures (Claude error/timeout, ungroundable result) to a graceful error response via the existing exception-handling pattern, and register the new controller with the API exception handler.
- The system shall leak no Claude client, DynamoDB item, or storage internals into the controller (DTOs only).

**Proof Artifacts:**
- Test: `StyleController` MockMvc tests pass for `postStyle_valid_returns200WithOutfit`, `postStyle_emptyWardrobe_returnsFriendlyResponse`, `postStyle_upstreamFailure_returnsGracefulError` — demonstrates the request/response contract and error paths.
- CLI: `curl -s -X POST localhost:8080/api/style -H 'content-type: application/json' -d '{"prompt":"streetwear today"}'` returns owned item ids + a reason — demonstrates the end-to-end endpoint (live-key run, captured as a proof artifact).

### Unit 3: Chat UI — vibe input → outfit card with real photos + reason

**Purpose:** The user-facing screen. A mobile-first chat where a typed vibe produces an outfit card built from the user's real photos plus the stylist's reason.

**Functional Requirements:**
- The user shall enter a vibe in a text input and submit it to `POST /api/style`.
- The system shall render the returned `itemIds` as an **outfit card** using each item's real stored photo (`/api/items/{id}/photo`) and display the `reason` text.
- The UI shall show explicit **loading**, **error** (with retry), and **empty-wardrobe** states, consistent with the existing wardrobe screens.
- The screen shall be reachable from the app nav and be mobile-first, following the existing "Care Label" design system — and be visually polished (see Design Considerations).

**Proof Artifacts:**
- Test: `Stylist` route + `api/style` client tests pass under Vitest/RTL (submit → loading → card render; error + empty states) — demonstrates the rendering logic without a live backend.
- Screenshot: the rendered outfit card (photos + reason) on a mobile viewport — demonstrates the end-to-end user experience.

## Non-Goals (Out of Scope)

1. **Multi-turn pushback / re-pick loop** ("too plain", avoid repeating the last look) — deferred to issue #7. The endpoint is stateless and its internals accept a message list, leaving room for #7, but no re-pick logic ships here.
2. **Wear-history writes** ("I wore this" increments) — deferred to issue #7. This feature *reads* wear-history via the tool but does not update it.
3. **Weather, color-as-code, occasion** — explicit stretch goals, not MVP.
4. **Passcode gate / daily call cap** — issue #8.
5. **Deploy / S3 / Secrets Manager** — issue #9; the key stays in `ENSEMBLE_ANTHROPIC_API_KEY` for local.
6. **Changing the tagging (Haiku) path** — untouched; the stylist is a separate Sonnet 5 path.

## Design Considerations

The user explicitly wants this screen to look **polished and real, not templated** — the **`frontend-design` skill must be used** during implementation (Unit 3) to drive the visual direction. Constraints:

- Reuse the existing **"Care Label" design system** (`frontend/src/index.css` `:root` tokens: `--paper`, `--ink`, `--accent`, display font Bricolage Grotesque, mono Space Mono, mobile-first `#root` max-width 30rem) so the stylist screen is cohesive with the wardrobe screens — do not introduce a second design language.
- The **outfit card** is the hero: the real garment photos laid out as a considered "look" (not a plain grid), with the stylist's reason presented as intentional editorial copy.
- Reuse existing state/loading/error/banner patterns (`WardrobeGrid.tsx`, `ItemDetail.tsx`) and the `photoUrl(id)` helper so behavior matches the rest of the app.
- Mobile-first; 44px touch targets; respect `:focus-visible` and `prefers-reduced-motion` already defined in the system.

## Repository Standards

- **Strict TDD** for the backend domain (Units 1–2 service/guardrail logic): RED → GREEN → REFACTOR; ≥90% line, **100% branch** on grounding/id-validation and forced-output parsing. Frontend (Unit 3) tests meaningful logic only — no over-testing view plumbing (`docs/TESTING.md`).
- **Layered architecture**: controller → `StylistService` → `WardrobeRepository`/`WardrobeService`; DTOs at the boundary; no Claude/DynamoDB/storage types in controllers.
- **Mock the Claude client** in tests (Mockito) — no live network calls; assert on the request built (tool defs, no image bytes) and on response handling. Follow the `AnthropicVisionModelClientTest` mocking template and the `try/catch → safe result` degrade pattern from `TaggingService`.
- Conventional commits, roughly one per demoable unit. Pre-commit hooks (fast tests + lint + secret scan) must pass.

## Technical Considerations

- **Model:** `claude-sonnet-5` (confirmed current id, no date suffix). Add a **second model property** (e.g. `ensemble.anthropic.stylist-model`, default `claude-sonnet-5`) to `AnthropicProperties` + `application.yml`, alongside the existing Haiku `model` for tagging. Reuse the existing `@Lazy AnthropicClient` bean (`AnthropicConfig`) and key handling (`ENSEMBLE_ANTHROPIC_API_KEY` → SDK env fallback).
- **Tool-loop (net-new — no existing scaffolding):** manual loop with the Java SDK — `MessageCreateParams` with `.addTool(searchWardrobe)`, `tool_choice: auto`; on each response, handle `tool_use` blocks, append the assistant `MessageParam` and a `ToolResultBlockParam` (via `MessageParam.contentOfBlockParams`), and re-call until `stop_reason == end_turn`. Bound with a small continuation cap (e.g. ≤4) to prevent infinite loops.
- **Forced structured output:** force a final `record_outfit` tool via `ToolChoiceTool` and extract JSON with the existing `AnthropicVisionModelClient.firstToolUseJson(...)` helper; parse defensively with a static mapper modeled on `TaggingService.map(...)` (blank/malformed → safe empty). This mirrors the text-only forced-tool pattern in `src/test/java/com/ensemble/eval/TagJudge.java`. (Sonnet 5 structured outputs via `output_config` are an accepted alternative, but the forced-tool path is preferred for consistency with the existing tagging code.)
- **Mockable seam:** introduce a `StylistModelClient` interface (analogous to `VisionModelClient`, whose javadoc already anticipates this) so `StylistService` guardrail logic is unit-testable without the SDK.
- **`searchWardrobe` data source:** reuse `WardrobeService.list()` → `WardrobeRepository.findAll()`; the existing `ItemResponse` DTO is already "tags + wear-history, no bytes" (`photoKey` → `photoUrl`), so the tool payload is byte-free by construction. The tool text must include `itemId` so the model can reference ids.
- **Adaptive thinking** may be left on (default) for stylist reasoning; do **not** set `budget_tokens` or sampling params (they 400 on Sonnet 5).
- **New controller** (`POST /api/style`) sits alongside `WardrobeController`; **must be added to `ApiExceptionHandler` `assignableTypes`** or its errors won't be handled.
- **Frontend:** new `api/style.ts` (copy the `api/items.ts` fetch-wrapper pattern), a `/style` route + nav link in `App.tsx`, render via `photoUrl(id)`; test with `vi.stubGlobal('fetch', …)` (client) and `vi.mock('../api/style')` (route).

## Security Considerations

- **No secrets committed.** The Claude key comes from `ENSEMBLE_ANTHROPIC_API_KEY` (local `.env`, git-ignored) / Secrets Manager (deploy, #9). Tests never need a key.
- **No image bytes to the stylist** — this is both a design guarantee and a privacy/cost property; it is explicitly asserted in tests.
- **Grounding is a safety property:** only validated, owned item ids are ever rendered, so the UI can never surface a hallucinated garment.
- Proof artifacts must not commit real photos or a live key — the live `curl`/screenshot proof is captured without embedding the key.

## Success Metrics

1. **Grounded**: 100% of rendered outfits contain only owned item ids across the test suite; hallucinated-id cases are caught, retried once, and never rendered.
2. **Coverage**: ≥90% line and 100% branch on grounding/id-validation and forced-output parsing (JaCoCo).
3. **End-to-end demo**: "streetwear today" returns a valid owned-item outfit + reason, rendered as a card with real photos, on a mobile viewport.
4. **No image bytes**: tests prove the stylist request carries text tags only.

## Resolved Decisions (locked in chat)

1. **Ungroundable pick** → render the **valid subset** after the one retry; error only when zero valid ids remain (never render an unvalidated id).
2. **Outfit completeness** → the **model decides** composition; no hard slot rules.
3. **Reasoning** → a **single overall `reason`** (no per-item why in this slice; can be added later without redesign).
4. **Empty wardrobe** → **`200`** with empty `itemIds` + explanatory `reason`.

## Open Questions

1. **Endpoint name** — assume `POST /api/style`; `/api/stylist` is an equally fine alias if preferred. Non-blocking.
