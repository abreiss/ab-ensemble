# 03-spec-vision-tagging.md

## Introduction/Overview

The first AI slice of the Ensemble MVP: **perception**. At upload time, one
Claude **Haiku 4.5** vision call turns a garment photo into the structured text
tags the wardrobe already models — so a user photographs a piece of clothing and
gets it auto-tagged instead of typing every field by hand. The tags come back as
a **suggestion for review**: the app shows them, the user edits anything wrong,
then saves through the existing wardrobe CRUD (issue #3 / spec 02). Tagging is a
**separate, non-blocking step in front of create** — if the vision call fails,
times out, or returns junk, the user still gets an editable (partial/empty) tag
set and can create the item by hand. This slice adds the Claude client seam the
codebase does not yet have; the stylist tool-loop (issue #6) is out of scope.

## Goals

- Add a **mockable Claude client seam** and Anthropic SDK dependency to the
  backend (the first AI integration), with the API key read from the
  environment and never committed.
- Implement a **`TaggingService`** that makes one **Haiku 4.5** vision call and
  returns **forced structured JSON** mapping onto the existing item tag fields:
  the 6 scalar tags (`category`, `primaryColor`, `secondaryColor`, `formality`,
  `pattern`, `warmth`) plus the `descriptors` list.
- Guarantee a **graceful fallback**: any failure, timeout, malformed output, or
  low-confidence field yields a partial/empty suggestion — **never a crash and
  never a blocked item creation**.
- Expose a **`POST /api/items/tag` preview endpoint** that accepts a photo and
  returns the suggested tags **without persisting** anything, so the user can
  review and edit before saving via the existing `POST /api/items`.
- Meet backend-domain TDD standards (≥90% line; **100% branch** on the
  vision-JSON→tag mapping and the fallback path) with the Claude client mocked —
  **no live API calls in tests**.

## User Stories

- **As the eventual user (served by the wardrobe UI, issue #5)**, I want my
  garment photo auto-tagged so that I can add a piece in seconds by confirming
  suggestions instead of filling in every field by hand.
- **As the eventual user**, I want to review and correct the suggested tags
  before saving so that a wrong guess never ends up in my wardrobe.
- **As the eventual user**, I want to still add a garment when auto-tagging
  fails so that a flaky vision call never blocks me from building my wardrobe.
- **As the developer (building later features)**, I want a tested, mockable
  Claude client seam so that the stylist agent (issue #6) can reuse the same
  integration and testing pattern instead of starting from scratch.

## Demoable Units of Work

### Unit 1: Vision tagging service (Haiku 4.5 → structured tags, mocked)

**Purpose:** Prove a garment image becomes the wardrobe's structured tag fields
via one forced-JSON Haiku 4.5 call, with every failure mode degrading to an
editable fallback rather than crashing.

**Functional Requirements:**
- The system shall add the Anthropic Java SDK dependency and a Claude client
  bean configured with the **`claude-haiku-4-5`** model, reading the API key
  from the `ANTHROPIC_API_KEY` environment variable.
- The system shall expose the Claude client behind a **narrow, mockable seam**
  (an interface or thin wrapper) so tests inject a mock and no live call is
  made.
- The system shall provide a `TaggingService` that, given image bytes, sends a
  **single Haiku 4.5 vision request** carrying the image and requesting
  **forced structured JSON** for the tag fields.
- The system shall **map the vision JSON onto the existing tag shape**: the 6
  scalar tags (`category`, `primaryColor`, `secondaryColor`, `formality`,
  `pattern`, `warmth`) plus the `descriptors` list.
- The system shall **clamp/validate** numeric tags to their allowed ranges
  (`formality` 1–5, `warmth` 1–3); an out-of-range or unparseable value is
  treated as **unknown** (left empty) rather than passed through or thrown.
- On **any** failure — API error, timeout, malformed/incomplete JSON, or a
  field the model could not determine — the service shall return a
  **partial or empty** tag suggestion and **never throw** out of the tagging
  path.
- The system shall **never send image bytes to the stylist path**; images are
  used only here, at tagging time (consistent with `docs/ARCHITECTURE.md`).

**Proof Artifacts:**
- Test: a mocked-client unit test where a valid structured response maps to the
  correct 6 scalar tags + `descriptors`, demonstrates the vision-JSON→model
  mapping.
- Test: mocked malformed/incomplete JSON and a missing field yield a
  partial/empty suggestion (no exception), demonstrates the low-confidence
  fallback.
- Test: a mocked API error and a mocked timeout each yield an empty suggestion
  (no exception), demonstrates the failure fallback.
- Test: the built request is asserted to target `claude-haiku-4-5`, carry the
  image, and request structured JSON — demonstrates the model + forced-output
  contract without a live call.
- Coverage: JaCoCo shows ≥90% line on the tagging package and **100% branch** on
  the JSON→tag mapping and the fallback path.

### Unit 2: Tag-preview API endpoint (`POST /api/items/tag`) + live proof

**Purpose:** Prove the wardrobe can auto-tag a real photo end to end over HTTP,
returning editable suggestions that flow into the existing create endpoint.

**Functional Requirements:**
- The system shall expose **`POST /api/items/tag`** accepting a photo as
  **`multipart/form-data`** (same `photo` part shape as `POST /api/items`) and
  returning a **`TagSuggestion` DTO** as JSON.
- The `TagSuggestion` DTO shall carry the tag fields as **all-nullable** values
  (no bean-validation constraints), because a suggestion may be partial or
  empty — distinct from `TagRequest`, whose constraints apply only when the
  user finally saves.
- The endpoint shall **not persist** anything: no item record and no stored
  photo are created by tagging.
- The endpoint shall **validate the uploaded part is a decodable image** and
  reject a missing/invalid photo with **`400`**, reusing the same image-decode
  guard as storage (including the decompression-bomb pixel cap).
- When tagging degrades (vision failure/timeout/malformed), the endpoint shall
  return **`200` with a partial/empty `TagSuggestion`** — never `500` — so the
  client can fall back to manual entry and still create the item.
- Error handling for the new endpoint shall be covered by the existing
  `@RestControllerAdvice` pattern (extend its coverage or reuse it), so
  internals are never leaked.

**Proof Artifacts:**
- Test: a `@WebMvcTest` with a mocked `TaggingService` covering (a) a good
  suggestion returned as JSON, (b) a degraded/empty suggestion still returned
  `200`, and (c) a non-image / missing part returning `400`, demonstrates the
  API contract and non-blocking fallback.
- CLI (**live — requires `ANTHROPIC_API_KEY`**): `curl -F photo=@<garment>.jpg
  http://localhost:8080/api/items/tag` returns valid tag JSON for a real garment
  photo, then the returned tags (edited as needed) create the item via
  `POST /api/items` — demonstrates the headline acceptance criterion end to end.
- Artifact: the captured request/response of that live run saved under
  `03-proofs/`, demonstrating a real photo → valid structured tags → item
  created. (This is the one step that needs the key; everything else is built
  and tested without it.)

## Non-Goals (Out of Scope)

1. **No stylist reasoning / `searchWardrobe` / tool-loop** — the judgment half of
   the app is issue #6. This slice reuses no stylist code because none exists
   yet; it establishes the Claude client seam the stylist will later reuse.
2. **No wardrobe UI** — no camera-add or tag-edit screens; this slice is the
   tagging API only. The review-and-edit UX is issue #5.
3. **No change to the wardrobe CRUD surface** — `POST /api/items`,
   `PUT /api/items/{id}/tags`, and the item model are unchanged. Tagging adds a
   new preview endpoint in front of them.
4. **No persistence in the tag step** — tagging returns suggestions only;
   nothing is written until the user saves through the existing create endpoint.
5. **No auth / passcode gate / daily call cap** — issue #8. This endpoint will
   later sit behind that gate and cap; it runs unauthenticated pre-deploy.
6. **No batch/re-tagging of existing items** — tagging is single-photo,
   upload-time only. Re-tagging a saved item is not in scope.
7. **No weather/color-as-code/occasion tags** — the tag set is exactly the
   existing model fields; stretch tags are out of MVP.

## Design Considerations

No UI in this slice. The surface is the `POST /api/items/tag` REST endpoint. The
key design choice UI issue #5 depends on: tagging is a **standalone suggestion
step**, returning an all-nullable `TagSuggestion` that the client renders as
editable form defaults, then submits (corrected, and now valid) to the existing
`POST /api/items`. Because the suggestion is not persisted and carries no
validation constraints, a partial/empty result is a normal, renderable state —
the UI can show "we couldn't auto-tag everything, please fill in the rest"
without any error path. The photo is sent once for tagging and again on save;
statelessness is preserved and no server-side draft is held (consistent with the
stateless-server decision).

## Repository Standards

Follow the patterns established in issues #2–#3 and codified in `AGENTS.md` /
`docs/`:

- **Layered architecture:** controller → service → Claude client seam, under the
  `com.ensemble` root. A new tagging-focused package (e.g. `com.ensemble.tagging`)
  mirrors the existing feature-package layout (`wardrobe`, `storage`, `health`);
  the Anthropic client bean and its `@ConfigurationProperties` live in
  `com.ensemble.config` alongside the DynamoDB/photo config.
- **DTOs at the boundary:** never leak the Claude client, its request/response
  types, or raw model output into controllers — map to a `TagSuggestion` DTO,
  the same way `ItemMapper` isolates the DynamoDB bean.
- **Strict TDD** for this backend-domain slice: Red-Green-Refactor, tests before
  code, ≥90% line coverage, **100% branch** on the critical paths named below.
- **Mock the Claude client** (Mockito) exactly as `docs/TESTING.md` requires:
  assert on the request built (model, image present, forced-JSON) and on how
  responses are handled (valid / malformed / error / timeout). **No live
  network calls in tests.**
- **Config prefix:** non-secret Anthropic settings under the existing
  `ensemble.*` namespace (e.g. `ensemble.anthropic.model`,
  `ensemble.anthropic.timeout`), bound by a typed `@ConfigurationProperties`
  record like `DynamoDbProperties` / `PhotoProperties`. The **API key is not a
  config property** — it comes from `ANTHROPIC_API_KEY` in the environment.
- **Tooling:** JUnit 5, Mockito, `@WebMvcTest`, JaCoCo — already wired.
- **Commits:** small, conventional-commit messages, roughly one per demoable
  unit.
- **Secret hygiene:** the pre-commit `block-anthropic-keys` scan already blocks
  `sk-ant-*`; keep it green (no key in code, config, tests, or proof artifacts).

### Critical Logic Requiring 100% Branch Coverage (this slice)

- **Vision-tag mapping:** a valid vision JSON maps to the 6 scalar tags +
  `descriptors`; each field present/absent is handled; numeric tags are
  clamped/validated to range or treated as unknown.
- **Tagging fallback:** API error, timeout, malformed/incomplete JSON, and a
  low-confidence/missing field each produce a partial/empty suggestion and never
  throw — the tagging path always returns.

## Technical Considerations

- **Stack:** Java 21, Spring Boot 4.1.x, Gradle — as established in issues #2–#3.
- **Anthropic SDK:** add `com.anthropic:anthropic-java` to `build.gradle`.
  Construct the client from the environment
  (`AnthropicOkHttpClient.fromEnv()` reads `ANTHROPIC_API_KEY`); wrap it behind
  a narrow interface so tests mock the seam, not the SDK internals.
- **Model + forced output:** `claude-haiku-4-5`, one vision request with the
  garment image (base64 image content block) and **forced structured output**
  for the tag schema. Structured outputs are supported on Haiku 4.5; whether via
  the SDK's structured-output feature or a single forced tool call is an
  implementation detail — the **observable requirement** is valid JSON matching
  the tag schema, **parsed defensively** (a bad/partial body degrades to
  fallback, never an exception).
- **Image handling:** validate and downsize the uploaded photo before the call
  using the same decode/resize path as storage (≤800px JPEG) so we send a small,
  known-good image and reuse the decompression-bomb guard. This keeps token
  cost down and rejects non-image input at `400` before any API call.
- **Timeout + resilience:** set a bounded request timeout on the client; on
  timeout or API error, return the empty fallback. Do not add retries in this
  slice beyond the SDK default (the stylist's retry-on-hallucination is a
  separate, later concern).
- **Where images go:** this endpoint is the **only** place image bytes leave the
  app to Claude. The stylist (issue #6) will receive text tags only — this slice
  must not create any image-to-stylist path.
- **No new persistence:** tagging touches neither DynamoDB nor `PhotoStorage`.
- **Statelessness:** no server-side draft/session; the client holds the
  suggestion and resends the photo on save.

## Security Considerations

- **API key from the environment only** (`ANTHROPIC_API_KEY`); never in config,
  code, tests, or committed proof artifacts. The `ensemble.anthropic.*` config
  holds only non-secret settings (model, timeout).
- **Secret scan stays green:** the existing `block-anthropic-keys` pre-commit
  hook must continue to pass; verify no `sk-ant-*` string is introduced.
- **No live key in CI/tests:** all tests mock the client, so the pipeline needs
  no key and makes no external call.
- **Untrusted image input:** the uploaded photo is untrusted — enforce the image
  decode + pixel-cap guard (reject non-image/oversized at `400`) before sending
  to the API, and only ever **extract structured tags** from the response
  (no tool execution, no code paths driven by model output in this slice), so a
  malicious image can at worst produce junk tags the user edits away.
- **Data leaving the app:** garment photos are sent to Anthropic for tagging;
  this is the intended perception step. No other user data is included in the
  request. (Retention/privacy posture is a deploy-time concern, issue #9.)
- **Later gating:** the passcode gate and daily call cap (issue #8) will front
  this endpoint; note it as a future control, not part of this slice.

## Success Metrics

1. A real garment photo posted to `POST /api/items/tag` (with `ANTHROPIC_API_KEY`
   set) returns valid structured JSON for the tag fields, and those tags create
   an item via `POST /api/items` — the headline acceptance criterion, captured as
   a proof artifact.
2. Tagging failure (API error / timeout / malformed output) returns a
   partial/empty suggestion with `200`, and item creation still succeeds with
   manually-entered tags — verified by mocked tests.
3. Backend-domain coverage: ≥90% line on the tagging package; **100% branch** on
   the vision-JSON→tag mapping and the fallback path.
4. No live Claude call in the test suite (client mocked); the secret scan passes
   with no key committed.
5. The Claude client is reached only through the mockable seam — no controller or
   service depends on the SDK types directly (verified by inspection).

## Open Questions

1. **Endpoint path:** assumed `POST /api/items/tag` (kept under the existing
   `/api/items` mapping). Non-blocking; the exact path/segment can change without
   affecting the model, service, or fallback behavior.
2. **Suggestion status flag:** whether `TagSuggestion` should carry an explicit
   "auto-tagging degraded" boolean (so the UI can message the fallback) vs. the
   UI inferring it from empty fields. Assumed **not** required for this API slice;
   can be added for issue #5 without changing the tagging logic. Non-blocking.
3. **Forced-output mechanism:** assumed the SDK's structured-output / forced-JSON
   feature for Haiku 4.5, with defensive parsing as the guarantee. If a beta flag
   or a single forced tool call proves more robust in practice, that is an
   implementation choice — only the observable "valid JSON, parsed defensively"
   behavior is specified. Non-blocking.
4. **"6 tag fields" reconciliation:** issue #4 says "6 tag fields"; this spec
   reads that as the **6 scalar tags plus the `descriptors` list**, matching the
   existing `Item` / `TagRequest` shape and spec 02's "6 vision-tag fields"
   wording. Assumed correct; flagged for confirmation. Non-blocking if confirmed.
