# 07-spec-repick-wear-history.md

## Introduction/Overview

This feature closes Ensemble's styling **loop**. Today the stylist makes one grounded
pick and stops, and the wear-history fields (`lastWorn`, `wornCount`) round-trip in
storage but are never displayed or updated. Issue #7 adds the two moves that make the
app feel alive: **pushback / re-pick** — the user can say "too plain" (or tap "Show me
another") and get a *different* grounded outfit that responds to the feedback — and
**"I wore this"** — one tap on a styled look records that the pieces were worn
(`wornCount++`, `lastWorn = now`), which the stylist already reads and prefers to
avoid next time.

The primary goal is to make styling **conversational and self-adjusting** while keeping
every existing safety property: each re-pick is still grounded (only owned item ids are
ever rendered, hallucinations retried once), and the wear-history write is
**deterministic** (the server computes the count and timestamp — never the LLM).
Together they demonstrate the loop: *style → wear it → re-pick avoids what you just wore*.

## Goals

- Let the user **push back** on a look — free-text ("more color") or a plain
  regenerate — and receive a *different*, still-grounded outfit that reflects the note.
- Keep the server **stateless**: the client holds the conversation and resends it, so
  re-pick is a normal `POST /api/style` call carrying prior turns.
- Add a **deterministic "I wore this" write** that increments `wornCount` and sets
  `lastWorn = now`, exposed as a clean endpoint and never delegated to the model.
- **Display wear-history** on the item-detail screen (worn count + last-worn), the
  display deferred from issue #4.
- Preserve every guardrail: grounding + one retry apply to **every** pick, and the
  stylist still receives **no image bytes** (the conversation is text-only).

## User Stories

- **As someone getting dressed**, I want to tell the stylist "too plain" and get a
  different outfit, so that I can steer the look instead of accepting the first guess.
- **As an impatient user**, I want a one-tap "Show me another" that just re-picks, so
  that I can browse looks without typing.
- **As a user who actually wore an outfit**, I want to tap "I wore this look" once, so
  that the app remembers it and doesn't keep suggesting the same pieces.
- **As a user maintaining my wardrobe**, I want each item's detail screen to show how
  often and how recently I've worn it, so that I know what's in rotation.
- **As the app owner**, I want re-picks to stay provably grounded and the wear write to
  be deterministic, so that the loop never renders a hallucinated item or trusts the LLM
  with bookkeeping.

## Demoable Units of Work

### Unit 1: "I wore this" — deterministic wear-history write (backend domain)

**Purpose:** The strict-TDD, 100%-branch heart of the wear side. A `WardrobeService`
operation records that an item was worn, computed entirely server-side, exposed over
HTTP so a look can be logged.

**Functional Requirements:**
- The system shall provide a `WardrobeService.markWorn(itemId)` operation that reads the
  item, sets `wornCount` to its current value + 1 (treating an absent/null count as 0),
  sets `lastWorn` to the current server instant, persists the item, and returns the
  updated `ItemResponse`.
- The system shall **not** ask the model to compute any wear-history value — the count
  and timestamp are produced deterministically in application code.
- The system shall raise `ItemNotFoundException` (→ 404) when `markWorn` targets an id
  that does not exist, reusing the existing not-found rule.
- The system shall expose `POST /api/items/{id}/worn` returning `200` with the updated
  item DTO on success and the existing sanitized `404` for an unknown id.
- The system shall leave all other item fields (tags, photo, `createdAt`) unchanged.

**Proof Artifacts:**
- Test: `WardrobeServiceTest` passes with `markWorn_firstTime_setsCountToOneAndLastWorn`,
  `markWorn_existingCount_incrementsAndUpdatesLastWorn`, `markWorn_nullCount_treatedAsZero`,
  `markWorn_unknownId_throwsNotFound` — demonstrates the deterministic update rule with
  100% branch coverage on the null/zero seed path.
- Test: `WardrobeController` MockMvc tests `postWorn_valid_returns200WithUpdatedItem` and
  `postWorn_unknownId_returns404` pass — demonstrates the endpoint contract + error path.
- Test: a DynamoDB-Local round-trip (create → markWorn → get) shows the persisted
  `wornCount`/`lastWorn` change — demonstrates a real read-modify-write.
- CLI: `curl -s -X POST localhost:8080/api/items/<id>/worn` returns the item with an
  incremented `wornCount` and a fresh `lastWorn` — demonstrates the end-to-end write.

### Unit 2: Pushback / re-pick — stateless multi-turn stylist (backend domain)

**Purpose:** The AI-native loop. The stylist accepts prior conversation turns and
re-picks a *different* grounded outfit that answers the user's feedback, with the
grounding guardrail applied to the new pick exactly as before.

**Functional Requirements:**
- The system shall extend `POST /api/style` to accept an optional ordered `history` of
  prior turns (`{ role: "user" | "assistant", text }`) alongside `prompt`; a request
  with no `history` behaves exactly as the current single-turn call (backward
  compatible).
- The system shall seed the stylist conversation with the supplied `history` followed by
  the current `prompt`, then run the **same** tool-loop + grounding guardrail (validate
  every id, feed invalid ids back, retry exactly once, render only the validated subset,
  error only when zero remain).
- The system shall instruct the model, when prior turns are present, to produce a
  **different** outfit from the **immediately previous** look and to address the user's
  feedback, so "too plain" / "show me another" does not return the same pieces. (Avoiding
  only the last look — not every look shown — keeps a small wardrobe from exhausting its
  options.)
- The system shall keep the server **stateless** — no conversation is stored; the client
  resends the **full conversation thread** (every prior pick + pushback) each turn, so
  feedback compounds across re-picks.
- The system shall carry **text turns only** into the model client, preserving the
  no-image-bytes guarantee across every re-pick.
- The system shall handle **repeated pushback** (several re-pick turns) with each pick
  independently grounded, and shall keep the empty-wardrobe friendly `200` response on a
  re-pick against an emptied wardrobe.

**Proof Artifacts:**
- Test: `StylistServiceTest` passes with `style_withHistory_forwardsPriorTurnsToModel`,
  `style_repick_staysGroundedWithHallucinatedIdRetriedOnce`,
  `style_repick_rendersValidSubsetAfterRetry`, `style_emptyHistory_matchesSingleTurn`,
  and `style_repick_sendsNoImageBytes` — demonstrates grounded multi-turn re-pick with
  100% branch coverage preserved on the guardrail.
- Test: `StyleController` MockMvc `postStyle_withHistory_returns200WithOutfit` and
  `postStyle_withHistory_upstreamFailure_returnsGracefulError` pass — demonstrates the
  extended request contract and unchanged error mapping.
- CLI: two chained `curl` calls to `/api/style` (first a vibe, then the same vibe with
  the prior pick + "too plain" in `history`) return **different** owned item ids — a
  captured artifact demonstrating the re-pick loop end-to-end (live-key run).

### Unit 3: The loop on screen — wear-history display, "I wore this look", pushback + regenerate (frontend)

**Purpose:** The user-facing loop. Item detail shows wear-history; the styled outfit
card can be logged as worn in one tap; the stylist screen lets the user push back with a
note or a button and see a new look.

**Functional Requirements:**
- The item-detail screen shall **display** wear-history — `wornCount` (with a "never
  worn" state when 0/null) and `lastWorn` as a short **relative label** ("2 days ago",
  with a "not yet worn" state when absent) — without a per-item write action.
- The outfit card shall show an **"I wore this look"** action that marks every rendered
  piece worn (one `POST /api/items/{id}/worn` per piece) and, on success, **locks into a
  "Logged ✓" state** — one log per rendered look, so an accidental double-tap cannot
  inflate `wornCount`; a fresh re-pick yields a new, tappable card. A failed write
  degrades to a soft, retryable message without losing the rendered look.
- The stylist screen shall, after a look renders, offer both a **free-text pushback
  field** and a **"Show me another" regenerate button**; each submits a new
  `POST /api/style` carrying the accumulated conversation `history` (prior pick(s) +
  feedback) and renders the resulting new look.
- The UI shall preserve the existing loading / error-with-retry / empty-wardrobe states
  across re-picks and follow the existing "Care Label" design system.
- The stylist client shall build and forward the `history` (turn list) so the server
  stays stateless; the wardrobe client shall expose a `markWorn(id)` call.

**Proof Artifacts:**
- Test: `ItemDetail.test.tsx` passes cases rendering worn count + last-worn and the
  never-worn / not-yet-worn fallbacks — demonstrates the deferred wear-history display.
- Test: `Stylist.test.tsx` passes cases: "I wore this look" calls `markWorn` for each
  piece and shows the logged state; pushback and regenerate each POST with accumulated
  `history` and render the new look; regenerate is disabled while loading — demonstrates
  the loop UI without a live backend.
- Test: `api/items.test.ts` (`markWorn` hits `POST /api/items/{id}/worn`) and
  `api/style.test.ts` (`requestStyle` includes `history` in the body) pass —
  demonstrates the client contracts.
- Screenshot: the outfit card after "I wore this look" (logged state) and the
  item-detail screen showing wear-history, on a mobile viewport — demonstrates the
  end-to-end loop.

## Non-Goals (Out of Scope)

1. **Weather, color-as-code, occasion** — explicit stretch goals, not MVP.
2. **Passcode gate / daily call cap** — issue #8; the wear write and re-pick add no auth.
3. **Deploy / S3 / Secrets Manager** — issue #9; the key stays in
   `ENSEMBLE_ANTHROPIC_API_KEY` for local.
4. **Server-side conversation storage / sessions** — the server stays stateless; the
   client holds and resends history. No persistence of chat turns.
5. **Undo / un-wear** — "I wore this" only increments; there is no decrement action in
   this slice (each tap records a wear, mirroring real repeat wears).
6. **A deterministic recency filter** — "worn recently" remains the model's judgment over
   the wear-history text the tool already returns; the app supplies the data, not the rule.
7. **Per-item "I wore this" button** — the wear *action* lives on the outfit card
   (whole look); item detail is display-only this slice.
8. **Changing the tagging (Haiku) path** — untouched.

## Design Considerations

Reuse the existing **"Care Label" design system** (`frontend/src/index.css` tokens and
the loading/error/banner/empty patterns in `WardrobeGrid.tsx`, `ItemDetail.tsx`,
`Stylist.tsx`) — do not introduce a second design language. Specifics:

- **Wear-history on item detail** should read as a small, quiet metadata line (e.g. an
  eyebrow + value pair like "Worn 3× · 2 days ago" using a relative `lastWorn` label),
  not a heavy widget — it is informational, secondary to the photo and tag form.
- **"I wore this look"** is a clear affordance on the outfit card (44px touch target),
  distinct from a re-pick action, that visibly settles into a locked "Logged ✓" state
  after the tap.
- **Pushback + regenerate** should sit with the rendered look so the loop feels
  continuous: a short text field ("too plain, more color…") with submit, plus a
  secondary "Show me another" button. Both share the existing loading state; disable
  them while a re-pick is in flight and respect `:focus-visible` /
  `prefers-reduced-motion`.
- The **`frontend-design` skill should be used** for Unit 3 so the loop additions stay
  polished and cohesive with the stylist screen shipped in #5.

## Repository Standards

- **Strict TDD** for backend domain (Units 1–2): RED → GREEN → REFACTOR; ≥90% line and
  **100% branch** on the wear-history update rule and on the (unchanged) grounding
  guardrail as exercised through the new multi-turn path. Frontend (Unit 3) tests
  meaningful logic only — no over-testing view plumbing (`docs/TESTING.md`).
- **Layered architecture**: controller → service → repository; DTOs at the boundary; no
  DynamoDB `Item`, Claude client, or storage internals in controllers.
- **Mock the Claude client** in tests (Mockito) — no live calls; assert the conversation
  forwarded (prior turns present, no image bytes) and response handling. Wear-history
  writes use **DynamoDB Local** for the real round-trip.
- Conventional commits, roughly one per demoable unit; pre-commit hooks (fast tests +
  lint + secret scan) must pass.

## Technical Considerations

- **Wear write (Unit 1):** add `WardrobeService.markWorn(String itemId)` — a
  read-modify-write via `WardrobeRepository.findById` → mutate → `save`, reusing the
  private `find(...)`/not-found rule. Increment defensively:
  `count = (item.getWornCount() == null ? 0 : item.getWornCount()) + 1`. Timestamp with
  `Instant.now()`. Return `ItemMapper.toResponse(...)`. Add `POST /api/items/{id}/worn`
  to `WardrobeController` returning the updated `ItemResponse`; no new exception mapping
  is needed (reuses `ItemNotFoundException` → 404 already in `ApiExceptionHandler`).
- **Re-pick (Unit 2):** extend `StyleRequest` to `{ String prompt, List<StyleTurn> history }`
  with a nested `StyleTurn { String role, String text }` (nullable/empty `history` for the
  first turn). Map each `StyleTurn` to the existing `StylistMessage` (`user`/`assistant`).
  Add an overload `StylistService.style(String vibe, List<StylistMessage> history)`; the
  existing `style(vibe)` delegates with an empty list. `pickWithOneRetry` seeds the
  conversation with `history` then the current vibe before the existing grounding logic —
  **the guardrail is unchanged**. Signal "produce a different look" via the system prompt
  (or a synthesized instruction when history is non-empty) in
  `AnthropicStylistModelClient`; the `StylistModelClient.proposeOutfit(text, conversation)`
  seam already accepts the full turn list, so no interface change is required. Each request
  runs a fresh tool-loop, so the model re-reads the wardrobe (picking up any wear-history
  updated by "I wore this").
- **No image bytes:** `StylistMessage`/`StyleTurn` carry text only — the no-bytes
  guarantee holds by construction and is re-asserted in the multi-turn test.
- **Frontend:** add `markWorn(id)` to `api/items.ts` (`POST /api/items/{id}/worn`);
  extend `requestStyle` in `api/style.ts` to accept and send `history`. `Stylist.tsx`
  accumulates the **full thread** (each prior pick summarized as an assistant turn;
  pushback/regenerate as a user turn) and resends it every re-pick. The "I wore this
  look" button is a per-card one-shot: after a successful fan-out it locks to "Logged ✓"
  (guard re-entry with local state). `ItemDetail.tsx` renders the already-present
  `lastWorn`/`wornCount` from the `Item` type. Test with `vi.stubGlobal('fetch', …)`
  (clients) and `vi.mock` (routes), following the existing test files.
- **Formatting:** `lastWorn` is an ISO instant string in the DTO; render it as a short
  **relative** label ("2 days ago") on the client via a small helper, keeping the raw
  value in the API.

## Security Considerations

- **No secrets committed.** Claude key stays in `ENSEMBLE_ANTHROPIC_API_KEY` (local
  `.env`, git-ignored) / Secrets Manager (deploy, #9). Tests need no key.
- **No image bytes to the stylist** on any turn — a privacy/cost property re-asserted in
  the multi-turn test.
- **Grounding is a safety property** preserved per re-pick: only validated owned ids are
  ever rendered, so pushback can never surface a hallucinated garment.
- **Wear write is unauthenticated in this slice** by design (single-user demo); the
  passcode gate + daily cap are issue #8. No sensitive data is exposed by the endpoint
  (it returns only the item's own tags/wear-history).
- Proof artifacts must not commit real photos or a live key — the live `curl`/screenshot
  proofs are captured without embedding the key or personal images.

## Success Metrics

1. **Grounded across turns:** 100% of re-picked outfits contain only owned ids across the
   test suite; hallucinated-id cases on a re-pick are caught, retried once, and never
   rendered.
2. **Deterministic wear write:** `markWorn` sets `wornCount = prior + 1` and a fresh
   `lastWorn` with 100% branch coverage on the null/zero seed path; the model computes no
   wear-history value.
3. **Coverage:** ≥90% line and 100% branch maintained on the wear-history update rule and
   the grounding guardrail (JaCoCo).
4. **Loop demo:** style a look → "I wore this look" → item detail shows the incremented
   count → re-pick with "too plain" returns a **different**, still-owned outfit — on a
   mobile viewport.
5. **No image bytes:** tests prove every stylist request (including re-picks) carries
   text turns only.

## Resolved Decisions (locked in chat)

1. **Wear action surface** → the **outfit card** carries "I wore this look" (marks the
   whole rendered look, one write per piece). Item detail is **display-only** this slice.
2. **Re-pick UX** → **both** a free-text pushback field **and** a "Show me another"
   regenerate button on the stylist screen.
3. **Re-pick transport** → reuse `POST /api/style`, extended with an optional `history`;
   the server stays **stateless** and the client resends the conversation each turn.
4. **Wear write shape** → per-item `POST /api/items/{id}/worn` (the domain primitive);
   the card's "whole look" action fans out to it per piece on the client.
5. **No undo / no deterministic recency filter** → wearing only increments; "worn
   recently" stays the model's judgment over the tool's wear-history text.
6. **History depth** → the client resends the **full conversation thread** each re-pick
   (not a trimmed last-look), so feedback compounds across turns. Cheap at demo scale.
7. **Re-pick avoid scope** → the model avoids only the **immediately previous** look, not
   every look shown this session — a small wardrobe would otherwise run out of pairings.
8. **"I wore this look" behavior** → one log per rendered look; the button **locks to
   "Logged ✓"** after a successful tap so a double-tap can't inflate `wornCount`.
9. **`lastWorn` display** → a short **relative** label ("2 days ago"); the raw ISO
   instant stays in the API.

## Open Questions

1. **Endpoint naming** — assume `POST /api/items/{id}/worn`; `/api/items/{id}/wear` is an
   equally fine alias if preferred. Non-blocking.
2. **Consecutive-repick cap** — none is imposed; each pushback is one stateless request.
   A soft client-side cap could be added later without redesign. Non-blocking.
</content>
</invoke>
