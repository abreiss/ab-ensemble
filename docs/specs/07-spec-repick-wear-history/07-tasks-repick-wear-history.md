# 07-tasks-repick-wear-history.md

Task list for spec `07-spec-repick-wear-history`. Order is a dependency chain: backend
primitives (1.0, 2.0) land before the screens that consume them (3.0, 4.0). Backend units
follow **strict TDD** (RED → GREEN → REFACTOR); frontend units test meaningful logic only
(`docs/TESTING.md`).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `src/main/java/com/ensemble/wardrobe/WardrobeService.java` | Add `markWorn(itemId)` — the deterministic wear-history write (read-modify-write). |
| `src/test/java/com/ensemble/wardrobe/WardrobeServiceTest.java` | Unit tests for `markWorn` (100% branch on null/zero seed). |
| `src/main/java/com/ensemble/wardrobe/web/WardrobeController.java` | Add `POST /api/items/{id}/worn` returning the updated `ItemResponse`. |
| `src/test/java/com/ensemble/wardrobe/web/WardrobeControllerTest.java` | MockMvc tests for the worn endpoint (200 + 404). |
| `src/test/java/com/ensemble/wardrobe/WardrobeRepositoryIT.java` | DynamoDB-Local round-trip: create → markWorn → get shows persisted change. |
| `src/main/java/com/ensemble/wardrobe/Item.java` | Update the class javadoc (wear-history is now mutated in #7); fields already exist. |
| `src/main/java/com/ensemble/stylist/dto/StyleRequest.java` | Add optional `history` (list of `StyleTurn{role,text}`) for stateless re-pick. |
| `src/main/java/com/ensemble/stylist/web/StyleController.java` | Map `history` → `StylistMessage`; call the new `style(vibe, history)`. |
| `src/test/java/com/ensemble/stylist/web/StyleControllerTest.java` | Update stubs to the new signature; add `history` request cases. |
| `src/main/java/com/ensemble/stylist/StylistService.java` | Add `style(vibe, history)`; existing `style(vibe)` delegates with empty history. |
| `src/test/java/com/ensemble/stylist/StylistServiceTest.java` | Re-pick guardrail tests (grounding preserved through the history path). |
| `src/main/java/com/ensemble/stylist/AnthropicStylistModelClient.java` | Inject a "produce a different look" instruction when history is present; stays byte-free. |
| `src/test/java/com/ensemble/stylist/AnthropicStylistModelClientTest.java` | Assert the different-look instruction + no image bytes on the built request. |
| `frontend/src/lib/relativeTime.ts` | New helper: ISO instant → short relative label ("2 days ago"). |
| `frontend/src/lib/relativeTime.test.ts` | Unit tests for the relative-label helper. |
| `frontend/src/api/items.ts` | Add `markWorn(id)` → `POST /api/items/{id}/worn`. |
| `frontend/src/api/items.test.ts` | Client test for `markWorn`. |
| `frontend/src/api/style.ts` | Extend `requestStyle(prompt, history?)`; add `StyleTurn` type; re-export `markWorn`. |
| `frontend/src/api/style.test.ts` | Client test: `requestStyle` sends accumulated `history`. |
| `frontend/src/routes/ItemDetail.tsx` | Display wear-history (worn count + relative last-worn); update the #7-deferral javadoc. |
| `frontend/src/routes/ItemDetail.test.tsx` | Invert the "does not render wear-history" test → now asserts the display + fallbacks. |
| `frontend/src/routes/Stylist.tsx` | "I wore this look" (locks to "Logged ✓"); pushback field + "Show me another"; thread accumulation. |
| `frontend/src/routes/Stylist.test.tsx` | Update call shape; add wore-this + re-pick loop tests. |
| `frontend/src/index.css` | Care-Label styles for the wear line, logged state, pushback field, regenerate button. |

### Notes

- Backend tests: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)` or plain `mock(...)`), MockMvc via `@WebMvcTest`, DynamoDB Local via TestContainers. Run `./gradlew test -PskipFrontend`; coverage `./gradlew jacocoTestReport` (`build/reports/jacoco/`).
- Frontend tests: Vitest + RTL alongside the component; `vi.mock` the API module (routes), `vi.stubGlobal('fetch', …)` (clients). Run `cd frontend && npm test -- --run` and `npm run lint`.
- Never call the live Claude API in tests; mock the client seam. No secrets in any artifact; screenshots < 2048kb, ~390px viewport.
- Conventional commits, ~one per parent task; pre-commit hooks must pass.

## Tasks

### [x] 1.0 Deterministic "I wore this" wear-history write (backend domain)

Adds `WardrobeService.markWorn(itemId)` — a read-modify-write that sets
`wornCount = prior + 1` (null/absent treated as 0) and `lastWorn = Instant.now()`,
computed server-side (never by the model) — exposed as `POST /api/items/{id}/worn`
returning the updated `ItemResponse`, reusing the existing `ItemNotFoundException` → 404.
Strict TDD; 100% branch on the null/zero seed path. (Spec Unit 1.)

#### 1.0 Proof Artifact(s)

- Test: `WardrobeServiceTest` passes `markWorn_firstTime_setsCountToOneAndLastWorn`,
  `markWorn_existingCount_incrementsAndUpdatesLastWorn`,
  `markWorn_nullCount_treatedAsZero`, `markWorn_unknownId_throwsNotFound` via
  `./gradlew test -PskipFrontend` — demonstrates the deterministic update rule (Unit 1 FR1–FR3).
- Test: `WardrobeControllerTest` passes `postWorn_valid_returns200WithUpdatedItem` and
  `postWorn_unknownId_returns404` — demonstrates the `POST /api/items/{id}/worn` contract
  + sanitized 404 (Unit 1 FR4).
- Test: `WardrobeRepositoryIT` (DynamoDB Local) create → markWorn → get shows the
  persisted `wornCount`/`lastWorn` change with tags/`createdAt` unchanged — real round-trip
  (Unit 1 FR1, FR5).
- CLI: `curl -s -X POST localhost:8080/api/items/<id>/worn` returns the item with an
  incremented `wornCount` and a fresh `lastWorn` (captured, no key/photo) — end-to-end write.
- Coverage: `./gradlew jacocoTestReport` shows 100% branch on the `markWorn` update rule.

#### 1.0 Tasks

- [x] 1.1 RED: In `WardrobeServiceTest`, add `markWorn_firstTime_setsCountToOneAndLastWorn`
  (stub `findById` → item with `wornCount=0`/null, `save` echoes; assert returned
  `wornCount==1` and `lastWorn` non-null; capture the saved `Item` to confirm both were
  set), `markWorn_existingCount_incrementsAndUpdatesLastWorn` (start `wornCount=7` → `8`),
  `markWorn_nullCount_treatedAsZero` (start null → `1`), and
  `markWorn_unknownId_throwsNotFound` (`findById` empty → `ItemNotFoundException`, `save`
  never called). Reuse the `existing(id)` helper + `ArgumentCaptor<Item>` pattern. Confirm
  it fails to compile/run (no method).
- [x] 1.2 GREEN: Implement `WardrobeService.markWorn(String itemId)` — `find(itemId)`,
  `count = (item.getWornCount() == null ? 0 : item.getWornCount()) + 1`,
  `item.setWornCount(count)`, `item.setLastWorn(Instant.now())`,
  `return ItemMapper.toResponse(repository.save(item))`. Make the tests pass.
- [x] 1.3 RED: In `WardrobeControllerTest`, add `postWorn_valid_returns200WithUpdatedItem`
  (mock `service.markWorn("abc")` → an `ItemResponse` with `wornCount=8`; `POST
  /api/items/abc/worn`; expect 200 + `jsonPath("$.wornCount").value(8)` +
  non-blank `lastWorn`) and `postWorn_unknownId_returns404` (mock throws
  `ItemNotFoundException`; expect 404, `error=not_found`). Confirm fails (no mapping).
- [x] 1.4 GREEN: Add `@PostMapping("/{id}/worn")` to `WardrobeController` returning
  `service.markWorn(id)` (`ItemResponse`, 200). No new exception mapping (existing
  `ItemNotFoundException` handler covers 404). Make the tests pass.
- [x] 1.5 RED→GREEN: In `WardrobeRepositoryIT` (DynamoDB Local), add a round-trip that
  saves an item (`wornCount=0`), invokes the wear write, then reloads and asserts
  `wornCount==1`, `lastWorn` set, and tags/`createdAt` unchanged. Use the existing
  container/table harness in that IT.
- [x] 1.6 REFACTOR: Update the `Item.java` class javadoc (wear-history is now mutated by
  #7); add concise javadoc to `markWorn` (deterministic, server-computed). Run
  `./gradlew test -PskipFrontend` and `./gradlew jacocoTestReport`; confirm green and 100%
  branch on the update rule. Commit.

### [x] 2.0 Stateless multi-turn re-pick / pushback (backend domain)

Extends `POST /api/style` with an optional ordered `history` of `{role,text}` turns
(mapped to `StylistMessage`), adds `StylistService.style(vibe, history)` (current
`style(vibe)` delegates with an empty list), seeds the conversation with history + the
current prompt, and runs the **unchanged** grounding guardrail per pick. The model is
instructed — when history is present — to produce a **different** outfit from the
immediately previous look. Server stays stateless; turns are text-only. Strict TDD;
guardrail branch coverage preserved. (Spec Unit 2.)

#### 2.0 Proof Artifact(s)

- Test: `StylistServiceTest` passes `style_withHistory_forwardsPriorTurnsToModel`,
  `style_repick_staysGroundedWithHallucinatedIdRetriedOnce`,
  `style_repick_rendersValidSubsetAfterRetry`, `style_emptyHistory_matchesSingleTurn`,
  `style_repick_sendsNoImageBytes`, `style_repick_emptyWardrobe_returnsFriendly`,
  `style_repeatedPushback_eachPickGrounded` via `./gradlew test -PskipFrontend` — grounded
  multi-turn re-pick with the guardrail intact, incl. repeated pushback + empty-on-repick
  (Unit 2 FR2, FR4, FR5, FR6).
- Test: `StyleControllerTest` passes `postStyle_withHistory_returns200WithOutfit` and
  `postStyle_withHistory_upstreamFailure_returnsGracefulError` — extended contract +
  unchanged error mapping (Unit 2 FR1).
- Test: `AnthropicStylistModelClientTest` asserts the "different look" instruction is sent
  when history is non-empty and that no image bytes are forwarded (mocked SDK) — re-pick
  prompt + byte-free guarantee (Unit 2 FR3, FR5).
- CLI: two chained `curl` calls to `/api/style` (a vibe, then the same vibe with the prior
  pick + "too plain" in `history`) return **different** owned item ids (captured) —
  re-pick loop end-to-end.
- Coverage: `./gradlew jacocoTestReport` shows 100% branch retained on the guardrail
  through the history path.

#### 2.0 Tasks

- [x] 2.1 RED: In `StylistServiceTest`, add `style_withHistory_forwardsPriorTurnsToModel`
  (call `service.style("too plain", List.of(user("streetwear"), assistant("chose a,b")))`;
  capture the conversation; assert the prior turns appear before an appended current-vibe
  user turn), `style_repick_staysGroundedWithHallucinatedIdRetriedOnce` and
  `style_repick_rendersValidSubsetAfterRetry` (same guardrail assertions as the single-turn
  cases but through the history overload), `style_emptyHistory_matchesSingleTurn`
  (`style(vibe)` and `style(vibe, List.of())` behave identically),
  `style_repick_sendsNoImageBytes` (all captured turns have non-null text; tool text has no
  photo path), `style_repick_emptyWardrobe_returnsFriendly` (non-empty history + empty
  `wardrobe.list()` → empty outfit + friendly reason with `verifyNoInteractions(model)`),
  and `style_repeatedPushback_eachPickGrounded` (two consecutive pushback turns — stub the
  model across turns; assert each pick is grounded and the later call forwards the full
  accumulated thread). Confirm fails (no overload).
- [x] 2.2 GREEN: Add `StylistService.style(String vibe, List<StylistMessage> history)`;
  refactor `style(vibe)` to delegate with `List.of()`. In `pickWithOneRetry`, seed
  `conversation` with all `history` turns, then `StylistMessage.user(vibe)`, before the
  existing grounding/retry logic (unchanged). Make the tests pass.
- [x] 2.3 RED: Extend `StyleRequest` to `{ String prompt, List<StyleTurn> history }` with a
  nested `record StyleTurn(String role, String text)`. Update the three existing
  `StyleControllerTest` stubs from `service.style(anyString())` to
  `service.style(anyString(), anyList())`, then add
  `postStyle_withHistory_returns200WithOutfit` (JSON body with a `history` array → 200
  outfit; verify the mapped turn count/roles via an `ArgumentCaptor`) and
  `postStyle_withHistory_upstreamFailure_returnsGracefulError` (503 `stylist_unavailable`).
  Confirm fails.
- [x] 2.4 GREEN: In `StyleController`, map `request.history()` (null → empty) to
  `List<StylistMessage>` (`"assistant"` → `assistant`, else `user`) and call
  `service.style(request.prompt(), history)`. Backward compatible (no history → empty).
  Make the tests pass.
- [x] 2.5 RED: In `AnthropicStylistModelClientTest`, following its existing SDK-mock
  pattern, add a case asserting that when the conversation contains a prior assistant turn
  the built request carries a "different"/"another" look instruction (system prompt or a
  synthesized turn), and a case asserting every forwarded `MessageParam` is text-only (no
  image content block). Confirm fails.
- [x] 2.6 GREEN: In `AnthropicStylistModelClient`, when the incoming conversation contains
  an assistant turn (i.e. a re-pick), append a concise "produce a different outfit from the
  previous look, addressing the user's feedback" instruction to the system prompt (or as a
  leading note). Keep it byte-free. Make the tests pass.
- [x] 2.7 REFACTOR: Extract a small `StyleTurn` → `StylistMessage` mapper if it clarifies
  the controller; add javadoc noting stateless resend + the different-look nudge. Run
  `./gradlew test -PskipFrontend` and `jacocoTestReport`; confirm 100% branch on the
  guardrail. Capture the two-`curl` re-pick CLI proof (live key, sanitized). Commit.

### [x] 3.0 Wear-history display + "I wore this look" on the card (frontend)

Shows wear-history on item detail (`wornCount` with a never-worn state; `lastWorn` as a
short **relative** label via a new helper, with a not-yet-worn state — display only), adds
`markWorn(id)` to the API client, and wires the outfit card's **"I wore this look"** button
to mark every rendered piece worn and **lock to "Logged ✓"** (one log per look; a failed
write → soft retryable message without losing the look). Meaningful-logic tests only.
Depends on 1.0. (Spec Unit 3 — wear side.)

#### 3.0 Proof Artifact(s)

- Test: `ItemDetail.test.tsx` passes cases rendering `wornCount` + relative `lastWorn` and
  the never-worn / not-yet-worn fallbacks via `npm test -- --run` — deferred display (Unit 3 FR1).
- Test: `frontend/src/lib/relativeTime.test.ts` passes ("today", "2 days ago",
  not-yet-worn) — the relative-label helper (Unit 3 FR1, Decision #9).
- Test: `api/items.test.ts` passes `markWorn` → `POST /api/items/{id}/worn` — client
  contract (Unit 3 FR5).
- Test: `Stylist.test.tsx` passes "I wore this look" calls `markWorn` per rendered piece
  and locks to "Logged ✓"; a failed write shows a retryable soft error without losing the
  look — whole-look write + lock (Unit 3 FR2, Decision #8).
- Screenshot: item detail showing "Worn 3× · 2 days ago" and the outfit card in the
  "Logged ✓" state at ~390px (sanitized, < 2048kb) — the wear UX.

#### 3.0 Tasks

- [x] 3.1 RED: Create `frontend/src/lib/relativeTime.test.ts` for a
  `relativeTime(iso: string, now?: Date): string` helper — assert "today" (same day),
  "1 day ago"/"2 days ago" (pass a fixed `now` for determinism), and a longer span.
  Confirm fails (no file).
- [x] 3.2 GREEN: Create `frontend/src/lib/relativeTime.ts` with the minimal implementation.
- [x] 3.3 RED: In `api/items.test.ts`, add a `markWorn` case using the existing
  `vi.stubGlobal('fetch', …)` pattern — asserts `POST /api/items/abc/worn` and returns the
  parsed `Item`; throws on a non-2xx. Confirm fails.
- [x] 3.4 GREEN: Add `markWorn(id)` to `api/items.ts`
  (`ensureOk(await fetch(\`${BASE}/${id}/worn\`, { method: 'POST' }), 'Mark worn')` →
  parsed `Item`). Re-export it from `api/style.ts` (mirroring `photoUrl`) so the card imports
  one stylist-facing module. Make the test pass.
- [x] 3.5 RED: In `ItemDetail.test.tsx`, **replace** the existing
  `does not render wear-history fields (deferred to #7)` test with cases asserting the
  display: a worn item shows "Worn 7×" and a relative last-worn label; `wornCount` 0/null →
  a "Never worn" state; `lastWorn` null → a "not yet worn" state. Confirm fails.
- [x] 3.6 GREEN: In `ItemDetail.tsx`, render a quiet wear-history metadata line (eyebrow +
  value) using `relativeTime`, with the never-worn / not-yet-worn fallbacks; update the
  component doc-comment (no longer "deferred to #7"). Make the tests pass.
- [x] 3.7 RED: In `Stylist.test.tsx`, extend the `../api/style` mock with
  `markWorn: vi.fn()`; add a case where clicking "I wore this look" calls `markWorn` once
  per rendered piece and the control locks to "Logged ✓"; add a case where one `markWorn`
  rejects → a retryable soft message shows and the look stays rendered. Confirm fails.
- [x] 3.8 GREEN: In `Stylist.tsx`, add an "I wore this look" button on the outfit card;
  on click run `Promise.allSettled(outfit.items.map((p) => markWorn(p.itemId)))`; on all
  success lock to a "Logged ✓" state (local state, one-shot, disabled); on any failure show
  a soft retryable message and keep the look. Make the tests pass.
- [x] 3.9 REFACTOR: Add Care-Label styles in `index.css` for the wear-history line, the
  "Logged ✓" state, and a 44px "I wore this look" target. Run `npm test -- --run` and
  `npm run lint`; capture the item-detail + logged-card screenshot (390px, sanitized).
  Commit.

### [x] 4.0 Pushback + "Show me another" re-pick UI (frontend)

After a look renders, adds a free-text pushback field and a "Show me another" regenerate
button; both build the **full conversation thread** (prior pick(s) summarized as assistant
turns + feedback as user turns) and re-run `POST /api/style` via an extended
`requestStyle(prompt, history)`, rendering the new look. Preserves loading /
error-with-retry / empty-wardrobe states across re-picks; controls disable while in flight.
Meaningful-logic tests only. Depends on 2.0. (Spec Unit 3 — re-pick side.)

#### 4.0 Proof Artifact(s)

- Test: `api/style.test.ts` passes `requestStyle` includes the accumulated `history` in the
  POST body via `npm test -- --run` — the stateless client contract (Unit 3 FR5).
- Test: `Stylist.test.tsx` passes: pushback submit and the regenerate button each POST with
  full-thread history and render the new look; controls are disabled while loading; the
  empty-wardrobe and error-with-retry states hold across a re-pick — the loop UI logic
  (Unit 3 FR3, FR4, Decisions #6, #7).
- Screenshot: the stylist screen showing a rendered look with the pushback field +
  "Show me another", and a second, **different** look after re-pick, ~390px (sanitized,
  < 2048kb) — the re-pick loop on screen.

#### 4.0 Tasks

- [x] 4.1 RED: In `api/style.test.ts`, add cases: `requestStyle(prompt, history)` sends a
  body containing both `prompt` and the `history` array; `requestStyle(prompt)` (no history)
  sends a body without a populated `history` (backward-compatible). Use the existing fetch
  stub + body-capture pattern. Confirm fails (signature).
- [x] 4.2 GREEN: In `api/style.ts`, add `export interface StyleTurn { role: 'user' |
  'assistant'; text: string }`; extend `requestStyle(prompt: string, history: StyleTurn[] =
  [])` to send `{ prompt, ...(history.length ? { history } : {}) }`. Update any existing
  body assertion for the no-history call. Make the tests pass.
- [x] 4.3 RED: In `Stylist.test.tsx`, update existing `requestStyle` call-shape assertions
  to the new signature; add: after a look renders, a pushback textbox + "Show me another"
  button appear; submitting pushback calls `requestStyle` with the newest user text and a
  `history` containing the prior vibe + an assistant turn summarizing the prior pick, then
  renders the new look; the regenerate button does the same with a "show me another" user
  turn; both controls are disabled while loading; the error-with-retry and empty states
  still hold on a re-pick. Confirm fails.
- [x] 4.4 GREEN: In `Stylist.tsx`, keep a `history: StyleTurn[]`; after a successful pick
  append `assistant` turn summarizing it (e.g. `Previously chose: a, b — <reason>`); render
  a pushback field + "Show me another" button below the card; on submit/regenerate call
  `requestStyle(newestUserText, history)` (history = all turns before the newest), render
  the result, then append the newest user turn + the new assistant summary; disable controls
  while `status==='loading'`; preserve the existing states. Make the tests pass.
- [x] 4.5 REFACTOR: Add Care-Label styles for the pushback field, the regenerate button,
  and disabled states (respect `:focus-visible` / `prefers-reduced-motion`). Run
  `npm test -- --run` and `npm run lint`; capture the two-different-looks screenshot
  (390px, sanitized). Commit.
</content>
