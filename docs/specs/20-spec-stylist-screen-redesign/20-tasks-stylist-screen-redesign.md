# 20-tasks-stylist-screen-redesign.md

Tasks for the stylist-screen redesign (spec `20-spec-stylist-screen-redesign.md`).

Design source of truth: `claude-design/design_handoff_stylist_maroon/` (option
**2a**). Decisions follow the handoff: stylist = landing `/`, full chat message
stream, handoff chip copy. Per-item rationale is real LLM output (Q1=B).

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/src/index.css` | Add the six `:root` tokens (task 1) and the new component styles (drawer, chat bubbles, tray, spec cards, pips, chips) (task 5). |
| `src/main/java/com/ensemble/stylist/Outfit.java` | Domain record; extend with per-item rationale (`rationaleById`), canonical-empty safe. |
| `src/test/java/com/ensemble/stylist/OutfitTest.java` | Tests the extended record (null/empty rationale map defaults). |
| `src/main/java/com/ensemble/stylist/OutfitParser.java` | Parse `pieces:[{itemId, rationale}]` (+ legacy `itemIds`), never throws. |
| `src/test/java/com/ensemble/stylist/OutfitParserTest.java` | 100% branch: well-formed, missing/blank rationale, legacy, malformed/absent. |
| `src/main/java/com/ensemble/stylist/StylistService.java` | Ground per-piece output; carry rationale only for grounded ids; one-retry unchanged. |
| `src/test/java/com/ensemble/stylist/StylistServiceTest.java` | Grounding + one-retry with rationale; empty/ungroundable paths. |
| `src/main/java/com/ensemble/stylist/AnthropicStylistModelClient.java` | Change the `record_outfit` tool inputSchema + prompt to request a one-line rationale per piece. |
| `src/test/java/com/ensemble/stylist/AnthropicStylistModelClientTest.java` | Assert the tool schema/prompt include per-item rationale; still text-only (no image bytes). |
| `src/main/java/com/ensemble/stylist/dto/StyleResponse.java` | `OutfitItem` gains `rationale` + `category`/`primaryColor`/`formality`/`warmth`/`descriptors`. |
| `src/main/java/com/ensemble/stylist/web/StyleController.java` | Enrich each `OutfitItem` by joining a grounded id → `ItemResponse` (via `WardrobeService`) + its rationale. |
| `src/test/java/com/ensemble/stylist/web/StyleControllerTest.java` | Assert the enriched response contract; empty-wardrobe still a normal 200. |
| `frontend/src/api/style.ts` | Extend `Outfit`/`OutfitItem` types to the enriched contract (rationale + tags). |
| `frontend/src/api/style.test.ts` | Client parses the enriched response. |
| `frontend/src/lib/specSheet.ts` (new) | Pure helpers: `deriveName`, `slotForCategory`, `swatchColor` (deterministic, null-safe). |
| `frontend/src/lib/specSheet.test.ts` (new) | Unit tests for the helpers incl. null-tag degradation. |
| `frontend/src/App.tsx` | Routing: `/` → Stylist, `/wardrobe` → WardrobeGrid; header `/wardrobe` link; optional `/style`→`/` redirect. |
| `frontend/src/App.test.tsx` | Routing tests: `/` = stylist, `/wardrobe` = grid. |
| `frontend/src/routes/AddItem.tsx` | `navigate('/')` → `navigate('/wardrobe')` after create. |
| `frontend/src/routes/AddItem.test.tsx` | Update the post-create redirect probe route to `/wardrobe`. |
| `frontend/src/routes/ItemDetail.tsx` | `navigate('/')` (after delete) + `<Link to="/">` → `/wardrobe`. |
| `frontend/src/routes/ItemDetail.test.tsx` | Update the redirect/back probe route to `/wardrobe`. |
| `frontend/src/routes/Stylist.tsx` | Rebuild: chat stream, wardrobe drawer, composer, chips, `OutfitResult`. |
| `frontend/src/routes/Stylist.test.tsx` | Behavior: chips fire a turn, stream grows, wear-log, re-pick/empty/error. |
| `frontend/src/components/RatingPips.tsx` (new) | `value`/`max` pip renderer (FORM=5, WARM=3). |
| `frontend/src/components/RatingPips.test.tsx` (new) | Filled/empty pip counts per value/max. |
| `frontend/src/components/WardrobeDrawer.tsx` (new) | Search + 2-col thumbnail grid from `listItems()`; in-look tiles outlined. |
| `frontend/src/components/WardrobeDrawer.test.tsx` (new) | Renders items; highlights in-look ids; search filters. |
| `frontend/src/components/OutfitResult.tsx` (new) | Flat-lay tray (numbered, `Wear today`/heart) + per-piece spec cards. |
| `frontend/src/components/OutfitResult.test.tsx` (new) | Renders pieces with name/slot/swatch/pips/rationale; `Wear today` fires. |
| `frontend/package.json` | `lucide-react` added (icons: search, arrow-up, heart). |

### Notes

- Backend tests: `./gradlew test -PskipFrontend`. Frontend: `cd frontend && npm run test -- --run` and `npm run lint`. All gated by pre-commit.
- Strict TDD (RED→GREEN→REFACTOR) on tasks 1–3 backend/logic; tasks 4–5 test meaningful logic + behavior, not view plumbing (per TESTING.md).
- Mock the Claude client (never live) in backend tests. Stylist stays text-only — assert no image bytes in the model request.
- `react-router-dom` is **v7**; use its API. Icons via `lucide-react` (`Search`, `ArrowUp`, `Heart`).
- Deterministic-vs-LLM: LLM supplies only rationale; name/slot/swatch/pips derive from stored tags in code.

## Tasks

### [x] 1.0 Define the six deferred design tokens

Add the drawer/tray/pip/rationale tokens to `:root`. Pure token addition. (Spec Unit 1.)

#### 1.0 Proof Artifact(s)

- CLI: `grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css` returns 6 matches with values `#e9dfca` / `#d7cab2` / `#574a3d` / `#a89a86` / `#d8cbb2` / `rgba(124, 40, 51, .22)` — demonstrates the tokens exist exactly as the handoff specifies (FR U1-1).
- CLI: `git diff frontend/src/index.css` shows only additions in `:root`, no existing token value changed — demonstrates no recolor regression (FR U1-2).
- Test: `cd frontend && npm run test -- --run` passes — demonstrates no behavior change (FR U1-3).

#### 1.0 Tasks

- [x] 1.1 Add the six tokens to `:root` in `frontend/src/index.css` after the existing color tokens (near `--accent-soft`), with the exact handoff values and their purpose comments; do not touch existing tokens or re-add `--on-accent`.
- [x] 1.2 Run `npm run test -- --run` and `npm run lint`; confirm green (no snapshot/behavior change).
- [x] 1.3 Capture the two grep proofs (tokens present; diff additions-only) into `20-proofs/`.

### [x] 2.0 Per-garment rationale from the stylist agent (backend)

Extend the forced output to a rationale per chosen item; parse + ground it; enrich the response DTO. Strict TDD, 100% branch on parse + grounding. (Spec Unit 2.)

#### 2.0 Proof Artifact(s)

- Test: `OutfitParserTest` passes for well-formed per-item output, missing/blank rationale (→ empty for that id), legacy `itemIds`-only, and malformed/absent (→ empty outfit) — demonstrates forced-output parsing at 100% branch (FR U2-1, U2-2).
- Test: `StylistServiceTest` — a hallucinated piece id is rejected, the invalid id is fed back, exactly one retry runs, and only owned ids with their rationale are returned — demonstrates grounding on the extended output (FR U2-3).
- Test: `StyleControllerTest` — `OutfitItem` carries `rationale` + the tag fields sourced from the wardrobe item (not the LLM); empty-wardrobe stays a normal 200 — demonstrates the enriched contract + deterministic split (FR U2-4, U2-5).
- CLI: `./gradlew test -PskipFrontend` passes; a captured sample `POST /api/style` JSON body (ids sanitized) shows `items[]` each with `rationale` + tag fields — demonstrates the contract end-to-end.

#### 2.0 Tasks

- [x] 2.1 RED: extend `OutfitTest` for a rationale-per-id (`rationaleById`) accessor — null/absent map defaults to empty, lookups for unknown ids return `""`. Then add the field to `Outfit` (canonical-empty safe) to GREEN.
- [x] 2.2 RED: add `OutfitParserTest` cases — `pieces:[{itemId,rationale}]` well-formed (ids in order + rationale map); a piece missing/blank `rationale` (→ `""`); `pieces` absent but legacy `itemIds` present (→ ids, empty rationale); malformed/absent JSON (→ `Outfit.empty()`). Then extend `OutfitParser.parse` to read `pieces` (fallback to `itemIds`) to GREEN. Keep it never-throwing.
- [x] 2.3 RED: extend `StylistServiceTest` — a first pick naming a hallucinated piece id is corrected via one user retry; the grounded result keeps rationale only for owned ids; ungroundable → `StylistUnavailableException`; empty wardrobe unchanged. Then update `StylistService` grounding to carry `rationaleById` for the grounded subset to GREEN.
- [x] 2.4 RED: extend `AnthropicStylistModelClientTest` — the `record_outfit` tool inputSchema requires a `pieces` array of `{itemId, rationale}` and a top-level `reason`; the prompt asks for a one-line rationale per piece; the wardrobe payload still carries no image bytes. Then update `AnthropicStylistModelClient` schema + prompt to GREEN.
- [x] 2.5 RED: extend `StyleControllerTest` — the response `items[]` each carry `rationale` + `category`/`primaryColor`/`formality`/`warmth`/`descriptors` joined from the owned item; empty-wardrobe is a normal 200 with empty items. Then add `rationale` + tag fields to `StyleResponse.OutfitItem` and enrich in `StyleController` (join grounded id → `ItemResponse` via `WardrobeService`, attach rationale) to GREEN.
- [x] 2.6 REFACTOR: de-duplicate the id→tags join; run `./gradlew test -PskipFrontend` + `jacocoTestReport`; confirm 100% branch on `OutfitParser` + grounding. Capture the sample JSON + coverage proof into `20-proofs/`.

### [x] 3.0 Frontend data layer: enriched contract + deterministic render helpers

Update the TS types/client and add pure spec-sheet helpers. No view code. (Spec Unit 3 — deterministic logic.)

#### 3.0 Proof Artifact(s)

- Test: `specSheet.test.ts` passes — `deriveName` (primaryColor + lead descriptor + category, title-cased; null degradation), `slotForCategory` (category → `TOP`/`BOTTOM`/`SHOES`/`CARRY` + fallback), `swatchColor` (color name → hex + neutral fallback) — demonstrates deterministic rendering logic incl. edge cases (FR U3 name/slot/swatch).
- Test: `style.test.ts` passes with the enriched `OutfitItem` (rationale + tags) — demonstrates the client matches the backend contract (FR U2-4).
- CLI: `cd frontend && npm run test -- --run` and `npm run lint` pass — demonstrates tested + lint-clean.

#### 3.0 Tasks

- [x] 3.1 RED: extend `style.test.ts` expecting `OutfitItem` with `rationale` + `category`/`primaryColor`/`formality`/`warmth`/`descriptors`. Then extend the `Outfit`/`OutfitItem` interfaces in `api/style.ts` to GREEN (mirror the backend DTO).
- [x] 3.2 RED: write `specSheet.test.ts` for `slotForCategory` — map known categories (shirt/tee/sweater/jacket → TOP; pants/chinos/jeans/shorts/skirt → BOTTOM; shoes/loafers/sneakers/boots → SHOES; bag/tote/accessory → CARRY) and an unknown → fallback. Then implement `slotForCategory` in `specSheet.ts` to GREEN.
- [x] 3.3 RED: `specSheet.test.ts` for `deriveName` — full tags → "White linen shirt"; missing color/descriptor degrade sensibly; all-null → a safe label. Then implement `deriveName` to GREEN.
- [x] 3.4 RED: `specSheet.test.ts` for `swatchColor` — CSS-keyword colors (white/olive/tan) map through; non-keyword (e.g. "natural") → curated hex; unknown → neutral fallback. Then implement `swatchColor` (small curated map) to GREEN.
- [x] 3.5 REFACTOR + run `npm run test -- --run` and `npm run lint`; capture helper test output into `20-proofs/`.

### [x] 4.0 Landing-route change + spec-sheet screen shell

Stylist → landing `/`; grid → `/wardrobe`; fix internal links; lay out the two-pane shell. (Spec Unit 3 — routing + layout.)

#### 4.0 Proof Artifact(s)

- Test: `App.test.tsx` — `/` renders the stylist, `/wardrobe` renders the grid; `AddItem`/`ItemDetail` redirect/back tests land on `/wardrobe` — demonstrates the landing-route migration with no link left pointing at the grid via `/` (FR U3 routing).
- Screenshot: wide-viewport shell — `--paper-sunk` drawer (250px) beside the main column; header wordmark + `Wardrobe` link + `+ Add` — demonstrates the two-pane layout (FR U3 layout).
- Screenshot: narrow (mobile) viewport — stacked, drawer collapsed to a toggle/bottom sheet, touch targets ≥44px — demonstrates mobile-first responsiveness (FR U3 responsive).
- Test: `cd frontend && npm run test -- --run` passes — demonstrates existing screens still route/render.

#### 4.0 Tasks

- [x] 4.1 RED: update `App.test.tsx` to assert `/` = stylist and `/wardrobe` = grid. Then in `App.tsx` set `/` → `Stylist`, `/wardrobe` → `WardrobeGrid` (keep `/add`, `/item/:id`); add a `Wardrobe` header link; optionally add `/style` → `<Navigate to="/" replace/>`. GREEN.
- [x] 4.2 RED: update `AddItem.test.tsx` + `ItemDetail.test.tsx` redirect/back probes to `/wardrobe`. Then change `navigate('/')` → `navigate('/wardrobe')` in `AddItem.tsx` and `ItemDetail.tsx`, and `<Link to="/">` → `/wardrobe` in `ItemDetail.tsx`. GREEN.
- [x] 4.3 Lay out the two-pane shell in `Stylist.tsx` + `index.css`: `--paper-sunk` drawer (250px, `border-right`) + flex main column; mobile-first stack <900px with the drawer as a toggle/bottom sheet. (WardrobeDrawer contents land in task 5; a placeholder is fine here.)
- [x] 4.4 Run `npm run test -- --run`; capture desktop + mobile shell screenshots into `20-proofs/assets/`.

### [x] 5.0 Chat stream, flat-lay result, spec list & chips

Build the conversational stream, composer, chips, and `OutfitResult`, preserving all existing behavior. (Spec Unit 3 — chat + result.)

#### 5.0 Proof Artifact(s)

- Screenshot: `/` wide — chat stream + flat-lay tray (numbered) + spec list, each piece with color swatch, FORM/WARM pips, and a one-line rationale — demonstrates handoff 2a fidelity (FR U3 stream/result/spec-list).
- Screenshot: `/` narrow — same, stacked — demonstrates mobile fidelity.
- Test: `RatingPips.test.tsx` — filled/empty counts match `value`/`max` (FORM=5, WARM=3).
- Test: `OutfitResult.test.tsx` — renders name/slot/swatch/pips/rationale per piece; `Wear today` calls the wear-log.
- Test: `WardrobeDrawer.test.tsx` — renders `listItems()`, highlights in-look ids, search filters.
- Test: `Stylist.test.tsx` — a quick-start chip and an adjust chip each fire a styling turn; the stream appends user+assistant turns (scrollback grows); empty/too-small, loading ("thinking"), and error→retry states render — demonstrates behavior preserved (FR U3 chips/stream/behavior).

#### 5.0 Tasks

- [x] 5.1 RED→GREEN: `RatingPips.tsx` (+ test) — render `max` dots, fill `value` with `--ink`, rest `--pip-empty`, Space Mono label; drive from `formality`/`warmth`.
- [x] 5.2 RED→GREEN: `WardrobeDrawer.tsx` (+ test) — `Search` (lucide) input, 2-col thumbnail grid from `listItems()`, tiles in the current look outlined `--accent`; loading/error like existing screens.
- [x] 5.3 RED→GREEN: `OutfitResult.tsx` (+ test) — flat-lay tray (numbered tiles via `photoUrl`, `Wear today` → existing `markWorn` fan-out, non-persisting `Heart`) + spec cards (index, `deriveName`, `slotForCategory` label, `swatchColor` swatch, `RatingPips`, rationale). Reuse the existing wear-log lock/error behavior.
- [x] 5.4 RED→GREEN: chat stream + composer in `Stylist.tsx` — render accumulated turns as user/assistant bubbles with scrollback + a "thinking" state; composer (lucide `ArrowUp`); quick-start chips (`Brunch`, `Interview`, `Date night`, `What goes with these loafers?`) and adjust chips (`Dressier ↑`, `Warmer`, `Swap #N`, `More color`), each firing a turn with its text; keep the free-text path. Preserve multi-turn history/re-pick, empty/too-small, and error→retry.
- [x] 5.5 Style everything in `index.css` to the handoff (bubbles, avatar, tray, cards, chips, composer, radii); honor `prefers-reduced-motion`; maroon never on small body text.
- [x] 5.6 Run `npm run test -- --run` + `npm run lint`; capture desktop + mobile screenshots into `20-proofs/assets/`.
