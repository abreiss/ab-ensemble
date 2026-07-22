# Assumptions Log — 18-spec-wardrobe-categories (GitHub issue #18)

This log records every non-obvious decision made on the user's behalf while
generating this spec autonomously (delegated SDD worker — no live user contact).
The user reviews this to catch anything they would have decided differently.

Feature: Wardrobe categories (grouped browsing) + Jewelry support. GitHub issue
#18. Branch: `wardrobe-grouping`.

---

## Phase 1 — Spec

### Setup / scope

- **A1.1 — Spec sequence + name.** Used `18-spec-wardrobe-categories/` with spec
  file `18-spec-wardrobe-categories.md`, per the manager's naming instruction
  (this repo numbers spec dirs by GitHub issue number). Feature slug
  `wardrobe-categories`.
- **A1.2 — Clarification: sufficient, no questions file.** The issue is unusually
  complete (fixed taxonomy given, rejected alternatives, non-goals, per-file
  targets, interdependency risks, suggested build order). All material questions
  were resolvable from the issue + codebase; the residual items are cosmetic and
  recorded as non-blocking Open Questions. No user contact (delegated worker).
- **A1.3 — Scope: "just right," kept as one spec.** Four small, interdependent
  changes across backend + frontend that must ship together (the issue's own
  "interdependent three fixes" warning). Splitting would reintroduce the exact
  regressions the issue calls out. Kept as a single spec with 4 demoable units.

### Design decisions resolved inline

- **A1.4 — Taxonomy "single source of truth" is per-stack.** There is no build
  step that shares a constant across Java and TypeScript at this scale, so the
  taxonomy is defined **once per stack**: one backend Java constant and one
  frontend TS constant, each the sole source its own stack derives from (vision
  schema + `applyTags` normalization on the backend; `<select>` options + grid
  section list/order + slot map + display-label map on the frontend). The
  cross-stack invariant (the two lists agree) is guarded by review + tests, not
  codegen. Rationale: a shared-codegen pipeline is overengineering for an 8-value
  list on a ~20-item demo app; the issue's intent ("derive three lists from one")
  is satisfied within each stack.

- **A1.5 — Where normalization runs (reconciling the issue's internal tension).**
  The issue says both "normalize-or-`Other` on every save path" *and*
  "normalization is read-time/display-only, not a data rewrite." Resolved as two
  layers that satisfy both:
  1. **Backend save-path normalization** — a pure `normalize(category) →
     taxonomy value | "Other"` function applied at the single write choke point
     `ItemMapper.applyTags` (covers create *and* update, vision-populated *and*
     manual). This is the code-enforced fallback the issue demands ("enforced in
     code on every save path, not assumed from the schema"). New writes store a
     canonical value.
  2. **Frontend read-time normalization** — the wardrobe grid normalizes each
     item's *stored* `category` to a taxonomy bucket purely for grouping/display.
  The non-goal ("no backend migration/rewrite of existing items' stored
  values") is honored: there is **no batch backfill script**; pre-existing rows
  that are never re-saved keep their raw stored value and are normalized only at
  read time for grouping. Normalizing a value *as it passes through a single
  user-triggered save* is not the forbidden retroactive batch rewrite.

- **A1.6 — `TagRequest` relaxation, no strict enum rejection.** Drop `@NotNull`
  from `formality`/`warmth` (keep `@Min`/`@Max`, which pass on `null` under
  Jakarta Bean Validation), so jewelry/accessory items save with null
  warmth/formality. `category` stays `@NotBlank`. Deliberately **do not** add a
  taxonomy `enum`/`@Pattern` that returns `400` — off-taxonomy strings are
  bucketed to `"Other"` in code, never rejected (the issue's "unrecognized falls
  into Other," never a hard reject).

- **A1.7 — Vision tool-use schema `enum`.** Add `"enum": [taxonomy]` to the
  `category` property in `AnthropicVisionModelClient.tagTool()`. Treated as a
  strong hint only (per the issue + stable Anthropic tool-use guidance); the
  backend `normalize` at A1.5(1) is the actual guarantee. `TaggingService.map`
  also normalizes the model-emitted category so the pre-filled edit form
  pre-selects a valid `<select>` option (UX consistency). Both paths call the
  same pure function; 100% branch coverage on it.

- **A1.8 — Jewelry shares the `CARRY` slot (resolves the issue's flagged minor
  risk).** In `slotForCategory`, `Jewelry` and `Accessory` both map to `CARRY`.
  No new value is added to the `Slot` union, so the outfit-result card and the
  manual-assembly/placement code (issue #21, which reuses `slotForCategory`) are
  untouched — staying within this issue's "browsing/tagging only" boundary. A
  dedicated `JEWELRY` slot can be added cheaply later if wanted.

- **A1.9 — `Dress` slot label.** `Dress → TOP` in `slotForCategory` (a dress
  occupies the torso/upper region; `TOP` is a reasonable card label and avoids
  regressing to the generic `PIECE`). Cosmetic and trivially changeable; also
  logged as a non-blocking Open Question.

- **A1.10 — Grouped grid target screen = `/wardrobe` (`WardrobeGrid.tsx`).**
  Since spec-20, `/` is the Stylist screen and `/wardrobe` is the flat photo grid
  the issue describes ("when I open my wardrobe"). Grouping is added to
  `WardrobeGrid.tsx`.

- **A1.11 — Section order + display labels.** Section order follows the taxonomy
  order — Jacket, Top, Bottom, Dress, Shoes, Jewelry, Accessory, Other — with
  `Other` always last and empty sections hidden. Singular→plural header map:
  Jacket→Jackets, Top→Tops, Bottom→Bottoms, Dress→Dresses, Shoes→Shoes,
  Jewelry→Jewelry, Accessory→Accessories, Other→Other.

- **A1.12 — Starter legacy-keyword normalization map (case/whitespace
  insensitive).** Recognized synonyms → bucket:
  - **Top**: top, shirt, t-shirt, tshirt, tee, sweater, sweatshirt, hoodie,
    blouse, polo, knit
  - **Jacket**: jacket, coat, blazer, parka, overcoat
  - **Bottom**: bottom, pants, chinos, jeans, trousers, shorts, skirt, leggings
  - **Dress**: dress, gown
  - **Shoes**: shoes, shoe, sneakers, boots, loafers, sandals, heels, footwear
  - **Jewelry**: jewelry, jewellery, necklace, ring, bracelet, earrings, watch
  - **Accessory**: accessory, bag, tote, hat, cap, belt, scarf, sunglasses,
    gloves, tie
  - **Other**: anything unrecognized, blank, or null.
  This is a *starter* to confirm against the actual wardrobe during
  implementation (per the issue). Note the deliberate split: "jacket" buckets to
  the **Jacket** *category* for grouping, while `slotForCategory("Jacket")`
  returns the **TOP** card *slot* — grouping bucket and card slot are distinct
  mappings and are allowed to differ.

- **A1.13 — Jewelry/Accessory use the same 6-field schema.** No lighter jewelry
  type; formality/warmth/pattern are simply left null where inapplicable — same
  "leave null if undeterminable" behavior the vision model already has (issue
  non-goal + existing `TagSuggestion` nullability).

## Phase 2 — Task planning

Decisions made while breaking the spec into tasks (delegated worker, no live user
contact; manager-authorized to resolve inline).

- **A2.1 — `tagTool()` made package-private for the enum-assertion test.** The
  spec's Unit 1 proof artifact requires a "vision-client test asserting the
  `category` tool-schema property carries the taxonomy `enum`." `tagTool()` is
  currently `private static` in `AnthropicVisionModelClient`, so its `InputSchema`
  cannot be inspected from a test. Chose to **widen `tagTool()` to package-private**
  (the test lives in the same `com.ensemble.tagging` package) rather than assert
  the enum indirectly through a live/mocked request round-trip. Rationale:
  package-private test seams are already used in this codebase (`TAG_TOOL`,
  `firstToolUseJson` are package-private/`public static` for exactly this reason),
  and inspecting the built schema directly is a stronger, faster assertion than
  reconstructing it from a mocked `Message`. Non-blocking, trivially reversible.

- **A2.2 — Backend taxonomy constant location + name.** Placed the taxonomy
  constant + `normalize` in a new `com.ensemble.wardrobe.CategoryTaxonomy` (domain
  package, alongside `Item`/`WardrobeService`), per the spec's Repository Standards
  ("pure domain logic and belongs with the taxonomy constant, not in a
  controller"). `normalize` is a pure `static` function so it is unit-testable in
  isolation and reused by both `ItemMapper.applyTags` and `TaggingService.map`
  without a Spring bean.

- **A2.3 — Frontend module split: taxonomy vs. grouping algorithm.** Split the
  frontend logic into two modules mirroring the `specSheet.ts` + `placement.ts`
  precedent: `categoryTaxonomy.ts` holds the source-of-truth constant,
  `normalizeCategory`, the display-label map, and section order; a separate pure
  `wardrobeSections.ts` holds the `groupByCategory` algorithm (the headline
  meaningful logic, unit-tested independent of React). Rationale: keeps the
  data/labels separate from the grouping algorithm, matches the repo's
  "meaningful logic in a pure module" pattern, and lets the grid stay thin view
  plumbing. Non-blocking; could equally be one module.

- **A2.4 — `slotForCategory` keeps its own keyword map (not derived from the
  taxonomy constant).** `specSheet.ts`'s `CATEGORY_SLOTS` is **extended** with the
  taxonomy values rather than rewired to import `categoryTaxonomy.ts`, because
  grouping bucket and card slot are deliberately distinct mappings (spec Technical
  Considerations: `Jacket` buckets to the Jacket section but `slotForCategory`
  returns `TOP`). The two lists are bound only as a **test invariant** (Task 4.1
  imports `CATEGORIES` to assert every taxonomy value resolves), not by shared
  code — consistent with the per-stack "two derived lists, one intent" decision
  (A1.4). `Other` is intentionally left unkeyed so it falls through to the existing
  `PIECE` fallback.

## Manager decisions (SDD fleet orchestrator)

- **M1 — Feature branch:** Phase-3 commits land on the pre-existing `wardrobe-grouping` branch (created by the user, currently at `main`'s tip 756f1f3) instead of a new `feature/18-wardrobe-categories` branch. The branch name and its position make the intent unambiguous.
