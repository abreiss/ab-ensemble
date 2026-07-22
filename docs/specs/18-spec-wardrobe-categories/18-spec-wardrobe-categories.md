# 18-spec-wardrobe-categories.md

## Introduction/Overview

Today the wardrobe grid renders every item as one flat, undifferentiated list,
and the underlying `category` tag is unvalidated free text — so casing/spelling
drift (`"shirt"`, `"T-Shirt"`, `"jacket"`, `"chinos"`) makes reliable browsing
impossible, and jewelry cannot be represented at all. This feature introduces a
**fixed category taxonomy** (`Jacket, Top, Bottom, Dress, Shoes, Jewelry,
Accessory, Other`) used as a single source of truth by the vision tagger, the
manual edit form, and the wardrobe grid; **groups the grid into category
sections**; and **adds Jewelry/Accessory** as first-class categories using the
existing 6-field tag model. The goal is reliable grouped browsing plus jewelry
support **without** changing the item's underlying tag schema, adding a table
migration, or touching the AI stylist's outfit-composition logic.

## Goals

- Define the category taxonomy **once per stack** (one backend constant, one
  frontend constant) and derive every consumer from it: the vision tool-use
  schema, the edit-form `<select>`, the grid's section list/order, the
  slot-label map, and the display-label map.
- Group the `/wardrobe` grid into category **sections** in a fixed display
  order, `Other` last, empty sections hidden.
- Make **Jewelry** and **Accessory** representable and saveable using the same
  6-field tag model, with `formality`/`warmth`/`pattern` left null where they do
  not apply.
- Guarantee that **every category that enters the system lands on a taxonomy
  value or `"Other"`** — the vision path is enum-hinted *and* code-normalized;
  the manual path is a constrained `<select>`; unrecognized values bucket to
  `"Other"` and are **never rejected**.
- Keep existing items usable with **no data migration**: legacy free-text
  categories are normalized to a bucket at read/display time for grouping.
- Prevent the outfit-result card from regressing to the generic `PIECE` label
  once vision stops emitting the old free-text vocabulary, by updating
  `slotForCategory` with the new taxonomy values.

## User Stories

- **As a wardrobe owner**, I want my items grouped into category sections
  (Jackets, Tops, Shoes, Jewelry…) when I open my wardrobe, so I can find
  something specific without scrolling one flat grid.
- **As a wardrobe owner**, I want to pick a category from a fixed list when
  adding/editing an item, so my wardrobe doesn't accumulate typos and casing
  variants.
- **As a wardrobe owner**, I want to photograph a jewelry item and have it
  tagged and grouped correctly, instead of forced into an ill-fitting clothing
  category.
- **As a wardrobe owner**, I want my existing items to land in a sensible
  category automatically where possible, so I'm not stuck manually re-tagging my
  whole wardrobe on day one.

## Demoable Units of Work

### Unit 1: Backend taxonomy + vision enum + nullable warmth/formality + defensive normalization

**Purpose:** The interdependent backend foundation. Establish the taxonomy as a
single backend source of truth, constrain the vision schema, relax the tag
constraints so jewelry can be saved, and enforce normalize-or-`"Other"` on the
save path — so nothing downstream depends on the model or the client honoring
the taxonomy.

**Functional Requirements:**
- The system shall define the category taxonomy as a single backend constant
  (source of truth) with the starter values `Jacket, Top, Bottom, Dress, Shoes,
  Jewelry, Accessory, Other`, and derive the vision schema `enum` and the
  normalization logic from it (no independently-maintained duplicate list).
- The system shall add an `enum` of the taxonomy values to the `category`
  property of the vision tool-use input schema in
  `AnthropicVisionModelClient.tagTool()`.
- The system shall relax `TagRequest.formality` and `TagRequest.warmth` from
  required (`@NotNull`) to nullable — keeping the existing range constraints
  (`formality` 1–5, `warmth` 1–3) so a supplied value is still range-checked but
  a null is accepted — so items where those fields do not apply (e.g. jewelry)
  save successfully. `category` remains required (`@NotBlank`).
- The system shall provide a pure `normalize(category) → taxonomy value` function
  that maps recognized/legacy values to a canonical taxonomy value
  (case- and whitespace-insensitive; e.g. `"chinos"`/`"jeans"` → `Bottom`,
  `"shirt"`/`"T-Shirt"` → `Top`) and maps any unrecognized, blank, or null value
  to `"Other"` — **never** raising an error or `400`.
- The system shall apply `normalize` at the single write choke point
  (`ItemMapper.applyTags`), so **both** create and update, and **both**
  vision-populated and manual, save paths persist a canonical taxonomy value;
  and shall apply the same function to the model-emitted category in
  `TaggingService.map` so the suggested pre-fill is already a taxonomy value.
- The system shall **not** perform a batch migration/rewrite of existing stored
  `category` values, and shall keep `category` a plain schemaless DynamoDB
  `String` attribute (no table change).

**Proof Artifacts:**
- Test: unit tests for `normalize` covering each taxonomy value, representative
  legacy synonyms (`chinos`→`Bottom`, `T-Shirt`→`Top`, `necklace`→`Jewelry`),
  casing/whitespace variants, and unrecognized/blank/null → `"Other"` —
  demonstrates the fallback with **100% branch coverage**.
- Test: `TagRequest`/controller test — a create request with `category=Jewelry`
  and `formality=null`, `warmth=null` returns `201/200` (not `400`) —
  demonstrates the nullability relaxation lets jewelry save.
- Test: vision-client test asserting the `category` tool-schema property carries
  the taxonomy `enum` — demonstrates the schema constraint is emitted.
- Test: `TaggingService.map` test — an off-taxonomy model category (e.g.
  `"sweatshirt"`) is normalized to a taxonomy value before it reaches the
  suggestion — demonstrates the vision-path fallback.
- CLI: `./gradlew test jacocoTestReport` passes with branch coverage on the
  normalization logic — demonstrates the coverage gate.

### Unit 2: `TagForm` category `<select>` + relaxed client validation

**Purpose:** Make the manual add/edit path produce only taxonomy values and let
jewelry save from the form (null warmth/formality), mirroring the way
`formality`/`warmth` already render as `<select>` controls.

**Functional Requirements:**
- The system shall define the taxonomy **once** on the frontend (source of truth)
  and derive the form's category options from it.
- The system shall replace `TagForm`'s free-text category `<input>` with a
  `<select>` whose options are the taxonomy values (matching the existing
  `formality`/`warmth` `<select>` pattern), with an unselected `—` placeholder.
- When editing an existing item whose stored `category` is a legacy/off-taxonomy
  value, the system shall pre-select the **normalized** bucket (via the same
  frontend `normalize` used by the grid), so the control never renders a blank
  or an option outside the list.
- The system shall relax client-side tag validation (`tagValidation.ts`) so that
  `formality`/`warmth` are **optional** (a null is valid; a supplied value is
  still range-checked), while `category` remains required; and shall update the
  `TagInput` type so `formality`/`warmth` are `number | null`.
- The system shall keep the vision → suggestion → editable-form → save flow
  working end-to-end, including the multi-item review-queue "Save all" path.

**Proof Artifacts:**
- Test: `TagForm` test — the category control is a `<select>` listing exactly the
  taxonomy values — demonstrates the fixed vocabulary.
- Test: `TagForm` test — selecting `Jewelry` and leaving warmth/formality unset
  yields a submittable form and a `TagInput` with null warmth/formality —
  demonstrates jewelry saves from the form.
- Test: `TagForm` test — an item seeded with a legacy category (`"chinos"`)
  pre-selects `Bottom` — demonstrates edit-time normalization of the control.
- Test: `tagValidation` test — null `formality`/`warmth` are valid; a
  non-blank/taxonomy `category` is required — demonstrates the relaxed rules.
- Screenshot: the add/edit form showing the category dropdown open with the
  taxonomy values — demonstrates the UI.

### Unit 3: Wardrobe grid grouped into category sections

**Purpose:** The headline browsing improvement — the flat `/wardrobe` grid
becomes category sections, with legacy values normalized at read time so
existing items land sensibly with no migration.

**Functional Requirements:**
- The system shall group the `/wardrobe` grid (`WardrobeGrid.tsx`) into sections,
  one per taxonomy value, each rendered under a section header.
- The system shall determine an item's section by applying the frontend
  `normalize` to its **stored** `category` at render time (no stored value is
  mutated), so legacy free-text values (`"shirt"`, `"chinos"`) group correctly
  and unrecognized values fall into `Other`.
- The system shall render sections in a fixed display order matching the taxonomy
  order (Jacket, Top, Bottom, Dress, Shoes, Jewelry, Accessory), with `Other`
  **always last**.
- The system shall **hide empty sections** (a taxonomy value with no items
  renders no header).
- The system shall render section headers from an explicit singular→plural
  display-label map (e.g. `Jacket`→"Jackets", `Accessory`→"Accessories",
  `Shoes`→"Shoes", `Jewelry`→"Jewelry"), not by string concatenation.
- The system shall preserve the existing grid's loading, empty-wardrobe, and
  list-failure/retry states and the per-item thumbnail/detail-link behavior.

**Proof Artifacts:**
- Test: grid/grouping unit test — given a mixed item list (including legacy
  free-text and an unrecognized value), sections render in the fixed order with
  `Other` last, empty sections omitted, and each item under the expected header —
  demonstrates grouping + ordering + read-time normalization.
- Test: grid test — a `Jewelry` item appears under a "Jewelry" section —
  demonstrates jewelry grouping.
- Screenshot: `/wardrobe` showing multiple category sections with headers —
  demonstrates grouped browsing.

### Unit 4: `slotForCategory` map updated for the new taxonomy

**Purpose:** Keep the outfit-result card's TOP/BOTTOM/SHOES/CARRY/PIECE labels
correct once vision emits taxonomy values instead of the old free-text
vocabulary — the interdependent fix that prevents a silent regression to the
generic `PIECE` label.

**Functional Requirements:**
- The system shall extend the `slotForCategory` keyword map in
  `frontend/src/lib/specSheet.ts` so every taxonomy value resolves to a slot:
  `Jacket`→`TOP`, `Top`→`TOP`, `Bottom`→`BOTTOM`, `Dress`→`TOP`, `Shoes`→`SHOES`,
  `Jewelry`→`CARRY`, `Accessory`→`CARRY`, `Other`→`PIECE`.
- The system shall keep the existing case-insensitive/whitespace-trimmed lookup
  and the `PIECE` fallback for null/blank/unrecognized categories (no behavior
  regression for values still in the legacy map).
- The system shall introduce **no new value** to the `Slot` union (Jewelry shares
  `CARRY`), so the outfit-result card and the manual-assembly placement code
  (issue #21) that reuse `slotForCategory`/`Slot` need no change.

**Proof Artifacts:**
- Test: `specSheet` test — each taxonomy value maps to its expected slot (and a
  `Jewelry` item resolves to `CARRY`, not `PIECE`) — demonstrates the map covers
  the new vocabulary.
- Test: existing `slotForCategory` tests remain green — demonstrates no
  regression for legacy values or the `PIECE` fallback.

## Non-Goals (Out of Scope)

1. **No change to the stylist's outfit-composition logic** or the existing "no
   hard slot rules" decision (spec 05). This is browsing/tagging only.
2. **No separate/lighter tag schema for jewelry.** Jewelry/Accessory use the same
   6 fields as clothing; inapplicable fields are simply null.
3. **No backend migration/rewrite of existing items' stored `category` values.**
   Normalization for grouping is read/display-time; save-path normalization
   applies only to values written after this ships, via a single user-triggered
   save (not a batch backfill).
4. **No tabs or filter chips** in the wardrobe grid — grouped sections only.
5. **No change to the free-text search** in `WardrobeDrawer.tsx`.
6. **No new `Slot` value** and no dedicated Jewelry outfit-card slot in this issue
   (Jewelry shares `CARRY`); a dedicated slot is deferred as a cheap follow-up.

## Design Considerations

- **Section headers** use the existing screen/grid visual language
  (`WardrobeGrid.tsx` / `index.css` — `.screen`, `.grid`, `.grid-cell`,
  `.screen-title` and existing tokens). Add a lightweight section-header element
  reusing existing type/spacing tokens; no new design system.
- **Category `<select>`** matches the existing `formality`/`warmth` `<select>`
  markup in `TagForm.tsx` exactly (same `.input`/`.field` classes, `—`
  placeholder option), so the form reads consistently.
- **Mobile-first** grid unchanged within each section (existing responsive
  `.grid` columns); sections stack in one continuous scroll (the issue's chosen
  model over tabs/chips).
- **Empty and error states** of the grid are preserved as-is.

## Repository Standards

- **Backend:** Layered (controller → service → repository/mapper); DTOs at the
  boundary; DynamoDB Enhanced Client single-item model; no new relational
  modeling. The normalization function is pure domain logic and belongs with the
  taxonomy constant (not in a controller).
- **Strict TDD (backend domain):** RED→GREEN→REFACTOR. `normalize` and the
  vision-tag mapping are **critical logic → 100% branch coverage**; ≥90% line on
  new backend domain code (AGENTS.md, TESTING.md).
- **Frontend:** React 19 + Vite, mobile-first; Vitest + React Testing Library on
  meaningful logic (the grouping/ordering/normalization and the form behavior),
  not view plumbing. Taxonomy + normalization live in `frontend/src/lib/`.
- **Claude usage:** vision tool-use schema built with the Anthropic SDK; the
  stylist still receives **text tags only** (no change here). `enum` on tool-use
  is an advisory hint — the code-side fallback is mandatory (AGENTS.md AI-agent
  standards).
- **Commits:** conventional commits, roughly one per demoable unit.

## Technical Considerations

- **Single write choke point:** both `WardrobeService.create` and
  `WardrobeService.updateTags` call `ItemMapper.applyTags`, making it the one
  place to enforce save-path normalization for every path.
- **Bean Validation nullability:** dropping `@NotNull` while keeping
  `@Min`/`@Max` is safe — Jakarta `@Min`/`@Max` treat `null` as valid, so a
  supplied value is still range-checked and a null is accepted.
- **`enum` is advisory, not a guarantee:** Claude tool-use treats an
  input-schema `enum` as a strong hint; the model can still emit an off-taxonomy
  string. The `normalize`-to-`Other` fallback must run in code on every save
  path regardless of source — do not assume schema membership. (No external
  standards research changed this; the issue's guidance matches current stable
  Anthropic tool-use behavior.)
- **Two derived lists, one intent:** because Java and TS cannot literally share a
  constant here, each stack defines the taxonomy once and derives its own
  consumers; the cross-stack agreement is a review/test invariant, not codegen
  (see assumptions A1.4).
- **Grouping bucket vs card slot are distinct mappings:** e.g. `"jacket"` buckets
  to the **Jacket** grouping category, while `slotForCategory("Jacket")` returns
  the **TOP** card slot. Both are intentional and may differ.
- **`slotForCategory` is reused by issue #21's placement code** (`lib/placement.ts`)
  and the outfit-result card; because no new `Slot` value is introduced, both
  consumers benefit from the richer map with zero change on their side.
- **`Item` model and DTO shapes are otherwise unchanged** — no new fields;
  `category` stays a nullable `String`.

## Security Considerations

- **No new secrets, endpoints, or credentials.** No new backend surface beyond
  the existing authenticated `/api/items` create/update; the taxonomy/vision
  changes reuse the existing Claude key path.
- **No new user input reaches the stylist LLM.** Category values are tags already
  in the wardrobe; the stylist payload shape is unchanged and remains text-only.
- **Input handling:** `category` remains validated (`@NotBlank`) and normalized
  server-side; a malicious/garbage value cannot crash grouping (it buckets to
  `Other`) and is escaped by React on render.
- **Proof artifacts:** screenshots must not embed the demo passcode or a session
  token.

## Success Metrics

1. **Grouped browsing:** `/wardrobe` renders category sections in the fixed
   order, `Other` last, empty sections hidden (grid tests + screenshot pass).
2. **Jewelry supported end-to-end:** a photographed jewelry item is tagged
   `Jewelry` with null warmth/pattern, saves successfully, and appears in the
   Jewelry section (backend + form + grid tests pass).
3. **Fixed vocabulary enforced:** the manual form offers only taxonomy values;
   the vision schema carries the `enum`; every save path persists a taxonomy
   value or `"Other"` — verified by full-branch tests on `normalize`.
4. **No PIECE regression:** the outfit-result card labels each taxonomy value with
   its correct slot (Jewelry→CARRY, not PIECE) — `specSheet` tests pass.
5. **No migration / no schema change:** existing items group correctly via
   read-time normalization; no table change, no backfill script; all existing
   backend + frontend tests stay green.
6. **Coverage gate:** ≥90% line on new backend domain code and 100% branch on
   `normalize` + vision-tag mapping.

## Open Questions

1. **`Dress` slot label** — mapped to `TOP` (occupies the torso; avoids the
   generic `PIECE`). Cosmetic; could instead be `PIECE`. Non-blocking assumption
   (A1.9); trivially changeable.
2. **Jewelry outfit-card slot** — shares `CARRY` with `Accessory` (no new `Slot`
   value). A dedicated `JEWELRY` slot is a cheap future follow-up. Non-blocking
   (A1.8), matching the issue's "minor/cosmetic" note.
3. **Exact legacy-keyword map contents** — a starter synonym→bucket map is
   provided (A1.12); confirm/extend it against the actual wardrobe composition
   during implementation. Adding synonyms later is cheap. Non-blocking.
4. **Starter taxonomy (8 values)** — a first cut per the issue; adding/removing a
   value later is cheap since nothing hardcodes an assumed count. Non-blocking.
