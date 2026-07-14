# 03-tasks-vision-tagging.md

Task plan for `03-spec-vision-tagging`. Strict TDD applies (backend domain):
Red-Green-Refactor, tests before code, ≥90% line coverage on the tagging
package, and **100% branch** on the two critical paths — the vision-JSON→tag
mapping (present/absent fields, numeric clamp/validate) and the tagging fallback
(API error, timeout, malformed/incomplete JSON, low-confidence field). The
Claude client is mocked in every test; **no live API calls in the suite**.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `build.gradle` | Add the `com.anthropic:anthropic-java` SDK dependency (first AI integration). |
| `src/main/resources/application.yml` | Add non-secret `ensemble.anthropic.model` + `ensemble.anthropic.timeout` keys. |
| `src/main/java/com/ensemble/config/AnthropicProperties.java` | New — typed `@ConfigurationProperties("ensemble.anthropic")` record; `model` defaults to `claude-haiku-4-5`, bounded `timeout` default. |
| `src/test/java/com/ensemble/config/AnthropicPropertiesTest.java` | New — asserts model/timeout defaults and binding (mirrors `PhotoPropertiesTest`). |
| `src/main/java/com/ensemble/config/AnthropicConfig.java` | New — enables `AnthropicProperties`; builds the SDK `AnthropicClient` bean from the env (`AnthropicOkHttpClient.fromEnv()`), lazily, with the configured timeout. |
| `src/main/java/com/ensemble/tagging/VisionModelClient.java` | New — narrow, mockable seam interface (jpeg bytes → model JSON text); hides all SDK types. |
| `src/main/java/com/ensemble/tagging/AnthropicVisionModelClient.java` | New — seam impl wrapping `AnthropicClient`; builds the one Haiku 4.5 vision request (image block + forced structured JSON). |
| `src/test/java/com/ensemble/tagging/AnthropicVisionModelClientTest.java` | New — mocked SDK client; captures and asserts the built request (model `claude-haiku-4-5`, image present, forced JSON). |
| `src/main/java/com/ensemble/storage/ImageProcessor.java` | New — decode + decompression-bomb pixel-cap + resize-to-≤800px-JPEG, extracted from `LocalDiskPhotoStorage` so storage and tagging share one guard. |
| `src/test/java/com/ensemble/storage/ImageProcessorTest.java` | New — direct tests: resize >800px, no-upscale, non-image → `InvalidImageException`, over pixel-cap → reject. |
| `src/main/java/com/ensemble/storage/LocalDiskPhotoStorage.java` | Refactor: inject `ImageProcessor` and delegate; drop the moved private decode/resize methods. |
| `src/test/java/com/ensemble/storage/LocalDiskPhotoStorageTest.java` | Update construction to pass an `ImageProcessor`; must stay green (behavior-preserving). |
| `src/main/java/com/ensemble/tagging/TaggingService.java` | New — downsize via `ImageProcessor`, call the seam, parse JSON defensively, map/clamp to `TagSuggestion`, fall back on any failure. Critical-logic unit. |
| `src/test/java/com/ensemble/tagging/TaggingServiceTest.java` | New — mapping, numeric clamp, and every fallback path with a **mocked** seam. 100% branch target. |
| `src/main/java/com/ensemble/tagging/dto/TagSuggestion.java` | New — all-nullable suggestion DTO, **no** bean-validation constraints (distinct from `TagRequest`). |
| `src/main/java/com/ensemble/tagging/web/TaggingController.java` | New — `POST /api/items/tag` multipart → `TagSuggestion` JSON; persists nothing. |
| `src/test/java/com/ensemble/tagging/web/TaggingControllerTest.java` | New — `@WebMvcTest` (mocked service): 200 good, 200 degraded/empty, 400 non-image/missing part. |
| `src/main/java/com/ensemble/wardrobe/web/ApiExceptionHandler.java` | Extend `assignableTypes` to also cover `TaggingController` so its 400s reuse the sanitized advice. |
| `README.md` | Document the tag-preview flow (env var, sample `curl`, note failure still returns editable `200`). |
| `docs/specs/03-spec-vision-tagging/03-proofs/` | Proof artifacts: sanitized live tag→create transcript + JaCoCo coverage summary. |

### Notes

- Backend tests run with `./gradlew test -PskipFrontend`; coverage via `./gradlew jacocoTestReport -PskipFrontend` (report under `build/reports/jacoco/`).
- **No live Claude call anywhere in the suite.** Two mock layers: mock the SDK `AnthropicClient` when testing the seam impl (assert the request built); mock the `VisionModelClient` seam when testing `TaggingService` (assert mapping/fallback). Neither needs a key.
- Follow the package-by-feature layout: new tagging code under `com.ensemble.tagging` (+ `.dto`, `.web`); the SDK client bean + properties under `com.ensemble.config`; the shared image guard under `com.ensemble.storage`.
- The **API key is env-only** (`ANTHROPIC_API_KEY`), never a config property, never committed. Keep the `block-anthropic-keys` pre-commit scan green; redact the key in every proof artifact.
- Forced-output mechanism (SDK structured-output vs. a single forced tool call) is an implementation detail (spec Open Question 3) — only the observable "valid JSON, parsed defensively" behavior is tested.
- The **400-vs-200 split** is deliberate: image decode/validate failure (`InvalidImageException`) propagates → `400`; API/timeout/parse failure is swallowed → `200` with a partial/empty suggestion. `TaggingService` must not swallow `InvalidImageException`.

## Tasks

### [x] 1.0 Claude client seam + Anthropic SDK + typed config

#### 1.0 Proof Artifact(s)

- Test: `AnthropicPropertiesTest` asserts `ensemble.anthropic.model` defaults to `claude-haiku-4-5` and a bounded timeout default is applied when unset — demonstrates the typed, model-pinned config binding.
- Diff: `build.gradle` shows `com.anthropic:anthropic-java` added; the client bean is constructed from the environment and the seam interface hides the SDK types — demonstrates the mockable seam exists and no controller/service imports the SDK directly.
- CLI: `./gradlew test -PskipFrontend --tests '*Anthropic*'` passes with **no `ANTHROPIC_API_KEY` set and no network call** — demonstrates the seam is test-reachable without a live key.
- CLI: `grep -R "sk-ant-" src build.gradle` returns nothing and `pre-commit run block-anthropic-keys --all-files` passes — demonstrates secret hygiene (sanitized).

#### 1.0 Tasks

- [x] 1.1 Add `com.anthropic:anthropic-java` (pinned current stable version) to `build.gradle` `implementation`; run `./gradlew dependencies --configuration runtimeClasspath` to confirm it resolves.
- [x] 1.2 RED: write `AnthropicPropertiesTest` — `model` defaults to `claude-haiku-4-5` when unset, an explicit value is kept, and a bounded `timeout` default applies when unset; confirm it fails (class absent).
- [x] 1.3 GREEN: implement `AnthropicProperties` (`@ConfigurationProperties("ensemble.anthropic")` record; defaults in the compact constructor) and add `ensemble.anthropic.model` + `.timeout` keys to `application.yml`; make the test pass.
- [x] 1.4 GREEN: implement `AnthropicConfig` — `@EnableConfigurationProperties(AnthropicProperties.class)` + a lazy `AnthropicClient` bean built via `AnthropicOkHttpClient.fromEnv()` with the configured timeout; confirm the context test still starts with **no key set** (lazy construction, mirrors `DynamoDbConfig`).
- [x] 1.5 GREEN: define the `VisionModelClient` seam interface in `com.ensemble.tagging` (input: jpeg bytes; output: model JSON text; throws on API failure) and implement `AnthropicVisionModelClient` wrapping `AnthropicClient` + `AnthropicProperties`, building one Haiku 4.5 vision request (base64 image block + forced structured JSON). No SDK type appears on the interface.
- [x] 1.6 RED→GREEN: write `AnthropicVisionModelClientTest` with a **mocked** `AnthropicClient` (Mockito captor on the request params); assert the request targets `claude-haiku-4-5`, carries an image content block, and requests structured/forced JSON — no live call. Implement until green. *(Covers FR1.1 model, FR1.3 request shape, FR1.7 image only in this vision request.)*
- [x] 1.7 REFACTOR: `grep -R "com.anthropic" src/main/java` shows the SDK imported **only** in `AnthropicConfig` + `AnthropicVisionModelClient` (seam boundary held); confirm `block-anthropic-keys` scan is green.

### [ ] 2.0 Shared image decode/resize guard (extract from storage, reuse)

#### 2.0 Proof Artifact(s)

- Test: existing `LocalDiskPhotoStorageTest` (resize >800px, no-upscale, non-image → `InvalidImageException`, pixel-cap over limit) stays **green** after the extraction — demonstrates behavior-preserving refactor.
- Test: a new `ImageProcessorTest` exercises the shared component directly for decode + pixel-cap rejection + ≤800px JPEG output — demonstrates the guard is reusable in isolation.
- Coverage: `./gradlew jacocoTestReport -PskipFrontend` shows **100% branch** retained on the decode/pixel-cap/resize logic in its new home — demonstrates critical-logic coverage survived the move.

#### 2.0 Tasks

- [ ] 2.1 RED: write `ImageProcessorTest` — `resizesOver800EdgeToJpeg`, `leavesSmallImageUnchanged` (no upscale), `nonImage_throwsInvalidImageException`, `overPixelCap_throwsInvalidImageException`; confirm it fails (class absent).
- [ ] 2.2 GREEN: create `com.ensemble.storage.ImageProcessor` (`@Component`, constructor takes `PhotoProperties`); move `decode` + pixel-cap check + `toResizedJpeg` + the `readRaster` test-seam + `MAX_EDGE`=800 from `LocalDiskPhotoStorage` into it; make `ImageProcessorTest` green.
- [ ] 2.3 GREEN: refactor `LocalDiskPhotoStorage` to inject `ImageProcessor` and delegate the resize on `save`; delete the now-moved private methods.
- [ ] 2.4 REFACTOR: update `LocalDiskPhotoStorageTest` construction to pass an `ImageProcessor` and confirm it stays green; run `jacocoTestReport -PskipFrontend` and confirm 100% branch on the decode/pixel-cap/resize logic is retained in `ImageProcessor`.

### [ ] 3.0 TaggingService: vision JSON → tags + graceful fallback (mocked)

#### 3.0 Proof Artifact(s)

- Test: a mocked valid structured response maps to the correct 6 scalar tags + `descriptors` — demonstrates the vision-JSON→model mapping.
- Test: mocked malformed JSON, incomplete JSON, and a missing/undetermined field each yield a partial/empty suggestion with **no exception** — demonstrates the low-confidence fallback (critical branch).
- Test: a mocked API error and a mocked timeout each yield an empty suggestion with no exception — demonstrates the failure fallback (critical branch).
- Test: an out-of-range `formality`/`warmth` or non-numeric value is left empty rather than passed through or thrown — demonstrates numeric clamp/validate (critical branch).
- Coverage: JaCoCo shows ≥90% line on `com.ensemble.tagging` and **100% branch** on the mapping + fallback methods — demonstrates the TDD standard.

#### 3.0 Tasks

- [ ] 3.1 RED: write `TaggingServiceTest#validResponse_mapsSixScalarsPlusDescriptors` with a mocked `VisionModelClient` returning a valid tag JSON string and a stubbed `ImageProcessor`; confirm it fails (class absent).
- [ ] 3.2 GREEN: create `com.ensemble.tagging.dto.TagSuggestion` (record, all-nullable, no constraints) and `TaggingService` — downsize bytes via `ImageProcessor`, call the seam, parse the JSON (Jackson `ObjectMapper`), map to `TagSuggestion`; make the mapping test pass.
- [ ] 3.3 RED→GREEN: add clamp/validate tests — `formality` outside 1–5, `warmth` outside 1–3, and a non-numeric value each leave that field empty (not thrown, not passed through); implement the clamp. *(FR1.5, critical branch.)*
- [ ] 3.4 RED→GREEN: add fallback tests — malformed JSON, incomplete JSON (missing fields), seam throws (API error), and seam throws a timeout — each returns a partial/empty `TagSuggestion` with no exception; implement the catch-all plus per-field absence handling. *(FR1.6, critical branch.)*
- [ ] 3.5 RED→GREEN: add a test that `InvalidImageException` raised by the image guard is **not** swallowed (propagates out of `suggest`), while API/parse failures are swallowed — locks the 400-vs-200 split the endpoint depends on.
- [ ] 3.6 REFACTOR: tidy naming/structure; run `jacocoTestReport -PskipFrontend` and confirm ≥90% line on `com.ensemble.tagging` and 100% branch on the mapping + fallback methods.

### [ ] 4.0 Tag-preview endpoint `POST /api/items/tag` + DTO + error handling

#### 4.0 Proof Artifact(s)

- Test: `@WebMvcTest` with a mocked `TaggingService` covers (a) a good suggestion returned as JSON, (b) a degraded/empty suggestion still returned **200**, and (c) a non-image / missing part returning **400** — demonstrates the API contract and non-blocking fallback.
- Test: a `TagSuggestion` with every field null serializes to valid JSON and carries no validation annotations — demonstrates the all-nullable suggestion DTO is distinct from `TagRequest`.
- Test: MockMvc asserts the 400 body is the generic sanitized `bad_request` shape and no internals leak — demonstrates the reused advice covers the new controller.
- Coverage: JaCoCo ≥90% line on the tagging web layer — demonstrates the standard on the new endpoint.

#### 4.0 Tasks

- [ ] 4.1 RED: write `TaggingControllerTest` (`@WebMvcTest(TaggingController.class)`, mocked `TaggingService`) — a good suggestion → `200` JSON with the expected tag fields; confirm it fails (class absent).
- [ ] 4.2 GREEN: implement `com.ensemble.tagging.web.TaggingController` — `POST /api/items/tag` consuming `multipart/form-data` (`photo` part), calling `service.suggest(photo.getBytes())`, returning the `TagSuggestion` at `200`; persists nothing.
- [ ] 4.3 RED→GREEN: add controller tests — (a) a degraded/empty suggestion still returns `200`, (b) a missing `photo` part returns `400`, (c) the service throwing `InvalidImageException` (non-image) returns `400`; implement/adjust until green. *(FR2.4, FR2.5.)*
- [ ] 4.4 GREEN: extend `ApiExceptionHandler` `assignableTypes` to include `TaggingController`; add a MockMvc assertion that the `400` body is the sanitized `{error:"bad_request", message:"invalid request"}` shape with no internals. *(FR2.6.)*
- [ ] 4.5 RED→GREEN: add a serialization test — an all-null `TagSuggestion` produces valid JSON — and confirm by inspection the DTO carries no bean-validation annotations (unlike `TagRequest`). *(FR2.2.)*
- [ ] 4.6 REFACTOR: confirm the tag path touches **no** persistence (no `WardrobeRepository` / `PhotoStorage` / DynamoDB call — mocked-service test + inspection) and the DTO-only boundary holds; `jacocoTestReport -PskipFrontend` ≥90% line on the tagging web layer. *(FR2.3.)*

### [ ] 5.0 End-to-end live proof (real photo → tags → item) + docs

#### 5.0 Proof Artifact(s)

- CLI (**live — requires `ANTHROPIC_API_KEY`**): `curl -F photo=@<garment>.jpg http://localhost:8080/api/items/tag` returns valid tag JSON for a real photo; the returned tags then create an item via `POST /api/items` — demonstrates the headline criterion end to end.
- Artifact: the captured request/response of that live run saved under `03-proofs/` **with the key redacted** — demonstrates real photo → structured tags → item created (sanitized).
- Diff: `README.md` gains a tag-preview section (env var, sample `curl`, note that failure still returns editable `200`) — demonstrates a new developer can run the slice.
- Artifact: `./gradlew clean test jacocoTestReport -PskipFrontend` coverage summary (tagging package ≥90% line, 100% branch on mapping + fallback) saved under `03-proofs/` — demonstrates the coverage gate met with the client mocked.

#### 5.0 Tasks

- [ ] 5.1 Add a "Vision tagging (tag preview)" section to `README.md`: `export ANTHROPIC_API_KEY=...`, a sample `curl -F photo=@... /api/items/tag`, and a note that a failed/degraded call still returns an editable `200`.
- [ ] 5.2 With the key set and the app running, `curl` a real garment photo to `POST /api/items/tag`; capture the returned tag JSON.
- [ ] 5.3 Feed the returned (optionally edited) tags into `POST /api/items`; confirm the item is created (`201` + a follow-up `GET`); capture the transcript.
- [ ] 5.4 Save the sanitized transcript (**API key redacted**) to `03-proofs/03-task-05-proofs.md`.
- [ ] 5.5 Run `./gradlew clean test jacocoTestReport -PskipFrontend`; save the coverage summary (tagging ≥90% line, 100% branch on mapping + fallback) to `03-proofs/`; confirm the `block-anthropic-keys` scan is green.
