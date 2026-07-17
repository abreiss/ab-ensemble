# 20-spec-stylist-screen-redesign.md

## Introduction/Overview

Redesign the stylist into the maroon-and-beige "spec sheet" experience from the
vendored design handoff (`claude-design/design_handoff_stylist_maroon/`, option
**2a**) and **promote it to the app's landing screen (`/`)**: a conversational
chat stream, a wardrobe drawer, and a result rendered as a numbered flat-lay tray
beside a per-garment spec list (color swatch, formality/warmth pips, and a
**one-line rationale per piece**). To feed that per-piece rationale, the stylist
agent is extended to emit a short reason **for each chosen item** (grounded and
retried like today's single reason). The redesign builds against the final maroon
base palette (spec 06) and defines the six deferred design tokens it consumes.

This delivers the outfit-explanation experience the README already promises
("builds an outfit from what you own, explains why, and re-picks when you push
back"), at higher fidelity and with per-item reasoning.

## Goals

- Extend the stylist agent's forced output so each chosen item carries its own
  short **rationale**, grounded and retried exactly like the existing whole-look
  reason (strict TDD, 100% branch on parsing + grounding).
- Surface, per outfit piece, the render data the spec sheet needs (rationale +
  the already-stored tags) through the `/api/style` response DTO.
- Promote the stylist to the landing route (`/`), move the wardrobe grid to
  `/wardrobe`, and rebuild the screen to the handoff's spec-sheet layout:
  wardrobe drawer, conversational chat stream, flat-lay tray, and per-garment
  spec list.
- Define and apply the six deferred design tokens (`--paper-sunk`, `--border`,
  `--ink-2`, `--placeholder`, `--pip-empty`, `--accent-line`) with the exact
  handoff values.
- Keep every existing capability working: multi-turn re-pick, "I wore this"
  wear-logging, empty/too-small wardrobe, loading, and error/retry.

## User Stories

- **As a user**, I want the stylist to explain *each* garment (not just the look
  as a whole) so that I understand why every piece is in the outfit and can push
  back precisely.
- **As a user**, I want a clear flat-lay of the look with the rationale beside
  each piece so that the recommendation reads like a considered outfit, not a
  list of ids.
- **As a user**, I want quick-start and adjust chips so that I can start or
  re-pick with one tap instead of typing every time.
- **As the developer**, I want the six drawer/tray/pip tokens defined once in
  `:root` so that every new component reads them and the screen matches the
  handoff exactly.

## Demoable Units of Work

### Unit 1: Define the six deferred design tokens

**Purpose:** Register the drawer/tray/pip/rationale tokens the new components
read, matching the handoff values exactly. Pure token addition; no behavior
change.

**Functional Requirements:**
- The system shall add these six tokens to `:root` in `frontend/src/index.css`
  with exactly these values: `--paper-sunk: #e9dfca`, `--border: #d7cab2`,
  `--ink-2: #574a3d`, `--placeholder: #a89a86`, `--pip-empty: #d8cbb2`,
  `--accent-line: rgba(124, 40, 51, .22)`.
- The system shall not re-add `--on-accent` (already present from spec 06) and
  shall not modify any existing token value.
- The system shall keep all existing frontend tests green (no behavior change).

**Proof Artifacts:**
- CLI: `grep -nE -- "--paper-sunk|--border|--ink-2|--placeholder|--pip-empty|--accent-line" frontend/src/index.css` returns 6 matches with the exact values — demonstrates the tokens exist as specified.
- Test: `cd frontend && npm test -- --run` passes — demonstrates no regression.

### Unit 2: Per-garment rationale from the stylist agent (backend)

**Purpose:** Give the stylist the ability to explain each piece. Extend the
forced structured output to include a short rationale per chosen item, keep it
grounded (every piece id owned; hallucinated ids rejected with exactly one
retry), and surface the per-piece data the spec sheet renders.

**Functional Requirements:**
- The system shall change the stylist's forced output so it returns, in addition
  to the whole-look `reason`, a **rationale string per chosen item**, associated
  with that item's id (e.g. `pieces: [{ itemId, rationale }]`).
- The system shall parse the extended output such that: a well-formed response
  yields one rationale per item id; a missing/blank per-item rationale degrades
  to an empty rationale for that piece (never a crash); malformed/absent output
  yields the existing empty outfit.
- The system shall ground per-piece output with the existing rule: every
  returned item id must exist in the wardrobe; any hallucinated id is rejected,
  the error is fed back, and exactly one retry is allowed. Only validated ids
  (with their rationale) are returned.
- The system shall enrich `StyleResponse.OutfitItem` with the fields the spec
  sheet needs — the per-item `rationale` plus the already-stored tag fields
  (`category`, `primaryColor`, `formality`, `warmth`, `descriptors`) — sourced
  deterministically from the wardrobe item, **not** from the LLM.
- The system shall keep the empty-wardrobe / too-small response a normal `200`
  with empty `itemIds`/`items` and an explanatory `reason`.
- The stylist shall still never receive image bytes (text tags only).

**Proof Artifacts:**
- Test: `OutfitParser` tests pass for valid per-item output, missing per-item rationale, and malformed/absent output — demonstrates forced-output parsing with 100% branch coverage.
- Test: `StylistService` grounding test — a hallucinated piece id is rejected, the error is fed back, exactly one retry runs, and only owned ids (with rationale) are returned — demonstrates the grounding guardrail on the extended output.
- CLI/JSON: a sample `POST /api/style` response body showing `items[]` each carrying `rationale` + the tag fields — demonstrates the enriched contract.
- Test: `cd backend && ./gradlew test` (stylist package) passes — demonstrates no regression.

### Unit 3: Spec-sheet stylist screen (frontend)

**Purpose:** Promote the stylist to the landing route and rebuild it to the
handoff's option-2a layout, wired to the enriched API, preserving all existing
stylist behavior.

**Functional Requirements:**
- The system shall make the stylist the landing screen: route `/` → the new
  stylist screen, `/wardrobe` → the existing `WardrobeGrid`, with `/add` and
  `/item/:id` unchanged. The persistent header keeps the `Ensemble` wordmark and
  `+ Add`, and gains a `/wardrobe` link. All existing internal links/redirects
  that assumed the grid lived at `/` shall be updated to `/wardrobe` so no
  navigation lands on the wrong screen.
- The system shall render the screen as the handoff layout: a wardrobe drawer
  (search input + 2-column thumbnail grid from `listItems()`, tiles in the
  current look outlined with `--accent`) and a main column (chat stream + result
  + composer). On narrow (<900px) viewports it shall stack mobile-first, the
  drawer collapsing to a toggle/bottom sheet; touch targets stay ≥44px.
- The system shall render the result as: (a) a **flat-lay tray** of numbered
  piece tiles (real photos via `photoUrl`, top-left index badge 1–N) with a
  `Wear today` primary action wired to the existing wear-log (`markWorn`) and a
  save/heart control rendered but non-persisting (see Non-Goals); and (b) a
  **spec list** with one card per piece showing: index, a **derived item name**,
  a **slot label** (`TOP`/`BOTTOM`/`SHOES`/`CARRY`) mapped from `category`, a
  **color swatch** from `primaryColor`, **FORM** pips (from `formality`, max 5)
  and **WARM** pips (from `warmth`, max 3), and the piece's **rationale**.
- The system shall derive the item name, the slot label, and the swatch color
  **deterministically** from the item's stored tags via pure, unit-tested
  helpers (no LLM call); each helper degrades gracefully when a tag is null.
- The system shall provide a `RatingPips` component (props `value`, `max`) that
  renders `max` dots, filling `value` with `--ink` and the rest with
  `--pip-empty`, preceded by a Space Mono label.
- The system shall render the exchange as a **chat message stream**: each user
  turn (typed, quick-start chip, or adjust chip) appends a right-aligned user
  bubble; each stylist reply appends a left-aligned assistant bubble (with the
  maroon `E` avatar) plus, when a look is produced, the `OutfitResult`. Prior
  turns remain visible as scrollback.
- The system shall provide **quick-start chips** under the first assistant
  message — `Brunch`, `Interview`, `Date night`, `What goes with these
  loafers?` — and **adjust/pushback chips** above the composer — `Dressier ↑`,
  `Warmer`, `Swap #N`, `More color` (handoff copy); each chip fires a styling
  turn with its text, and the free-text composer is retained.
- The system shall keep all existing stylist behavior: multi-turn re-pick over
  the accumulated history, loading ("thinking") state, error state with a
  same-turn retry, and the empty/too-small-wardrobe state.

**Proof Artifacts:**
- Screenshot: `/` (stylist landing) on a wide viewport showing drawer + chat stream + flat-lay tray + spec list with per-piece rationale — demonstrates the redesign matches the handoff.
- Screenshot: `/` on a narrow (mobile) viewport showing the stacked layout — demonstrates mobile-first responsiveness.
- Test: routing test — `/` renders the stylist and `/wardrobe` renders the grid — demonstrates the landing-route change.
- Test: helper unit tests (name derivation, slot mapping, swatch color) pass, including null-tag degradation — demonstrates deterministic rendering logic.
- Test: component tests pass for a rendered look (pips reflect formality/warmth, chips fire a turn, `Wear today` logs, chat scrollback grows, re-pick/error/empty states) — demonstrates behavior preserved.

## Non-Goals (Out of Scope)

1. **Save-look / favorites persistence** (the heart control). No backend exists;
   the control renders but does not persist (no-op / omit if cleaner).
2. **Wardrobe-drawer drag-to-"swap this item into the look" interaction.** The
   `Swap #N` adjust chip sends text like any other turn (it does not
   drag-and-drop); a drawer tile may link to `/item/:id` or be display-only.
3. **Session persistence of the chat stream.** The stream is in-memory for the
   session (a page reload starts fresh); it is not saved server-side.
4. **Weather, color-as-code, occasion** — project stretch, not this issue.
5. **Re-tagging or changing the vision pipeline.** Spec-sheet attributes are
   read from existing stored tags only.

## Design Considerations

Source of truth: `claude-design/design_handoff_stylist_maroon/` (open
`Ensemble Stylist.dc.html`, option **2a**; `theme-tokens.css` for values;
`README.md` for component specs). Fidelity is **high** — colors, type, spacing,
and radii are final; match them.

Key specs (from the handoff README):
- **Palette:** the six tokens in Unit 1 plus the spec-06 maroon base.
- **Type:** display Bricolage Grotesque 700/800; body system-ui; labels/eyebrows
  Space Mono uppercase (all already loaded).
- **Radius:** input 8px, tile 9–11px, card 12px, tray 14px, pill 999px.
- **Elevation:** flat — borders only, no in-page shadows.
- **Icons:** Lucide (`search`, `arrow-up`, `heart`) substituting the mock's
  unicode glyphs.
- **Animation:** honor existing `prefers-reduced-motion`; ~200–320ms ease-out
  rises, no bounce.

**Alignment with the handoff:** the screen follows the handoff on all points the
Q1–Q5 round raised — the stylist is the landing route (`/`) with the grid at
`/wardrobe`, the exchange is a full chat message stream with scrollback, and the
chip copy is the handoff's (`Brunch / Interview / Date night / What goes with
these loafers?` quick-start; `Dressier ↑ / Warmer / Swap #N / More color`
adjust). Q1 (real per-item LLM rationale) and Q2 (handoff vendored as the design
source) hold.

## Repository Standards

- **Backend:** layered (controller → service → repo); AWS SDK v2 DynamoDB
  Enhanced Client; DTOs at the boundary; the Claude tool-loop pattern; forced
  structured output; grounding guardrail with one retry; Sonnet 5 for styling.
  Strict TDD, ≥90% line and **100% branch** on forced-output parsing and
  id-grounding.
- **Frontend:** React 19 + Vite, mobile-first, CSS variables in
  `frontend/src/index.css`, `react-router-dom`, `Item` type + `/api/items` and
  `/api/style` clients. Vitest + RTL on meaningful logic (helpers, behavior); do
  not over-test view plumbing.
- Conventional commits; keep the token change, the backend change, and the
  frontend screen as separate demoable commits.

## Technical Considerations

- **LLM vs deterministic split (critical):** the LLM supplies **only** the
  per-item rationale (and the whole-look reason). Name, slot label, color swatch,
  and FORM/WARM pips are derived from stored tags in code — never asked of the
  model — per the AGENTS.md deterministic-data rule.
- **Contract shape:** extend the stylist forced output to associate a rationale
  with each item id (e.g. `pieces: [{ itemId, rationale }]`), and enrich
  `StyleResponse.OutfitItem` with `rationale` + `category`, `primaryColor`,
  `formality`, `warmth`, `descriptors`. Enriching the DTO server-side (over
  joining `listItems()` on the client) keeps the render contract explicit and the
  frontend simple; the drawer still uses `listItems()` for the full wardrobe.
- **Backward compatibility:** the whole-look `reason` and the empty-wardrobe
  `200` behavior are preserved; the multi-turn history/re-pick mechanism is
  unchanged (each chip/pushback is just another `newestUserText`).
- **`Item` has no name field:** derive a display name deterministically from
  `primaryColor` + lead `descriptor` + `category` (title-cased), degrading when
  fields are null. Map `category` → slot and `primaryColor` → swatch hex via
  small, tested tables with sensible fallbacks (some color names are valid CSS
  keywords; others need a hex fallback).
- **Contrast:** never use maroon `--accent` for small body text — use
  `--ink`/`--ink-2`; maroon is for fills and large text only (WCAG AA).

## Security Considerations

- No new secrets. The Claude key stays in env/Secrets Manager; the stylist call
  is server-side. The vendored `claude-design/` handoff contains only CSS/HTML/JS
  design reference — no credentials — and is safe to commit.
- The stylist continues to receive text tags only (no image bytes).
- No new user input reaches the model unescaped beyond the existing prompt/vibe
  path; per-item rationale is model output rendered as text (no HTML injection —
  React escapes by default).

## Success Metrics

1. **Per-item rationale:** a styled look returns one grounded rationale per
   piece; 100% branch coverage on the extended parser and grounding.
2. **Fidelity:** the stylist landing screen (`/`) matches handoff option 2a
   (tokens, layout, pips, chips) on desktop and stacks mobile-first <900px.
3. **No regression:** all existing backend and frontend tests stay green;
   re-pick, wear-log, empty, loading, and error/retry all still work.

## Open Questions

1. **Save-look/heart:** rendered but non-persisting this issue (Non-Goal 3);
   assumed acceptable to wire as a no-op until a save endpoint exists.
2. **Color-swatch fallback:** exact color-name→hex table entries are an
   implementation detail; assumed a small curated map with a neutral fallback is
   acceptable (non-blocking).
