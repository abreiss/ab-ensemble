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

## Phase 3 — Task 2.0 implementation

Decisions made while implementing Task 2.0 (delegated worker, no live user contact;
manager-authorized to resolve inline).

- **A3.1 — Edit-time normalization applies only when a category value is
  present; a blank/absent suggestion still renders the `—` placeholder.** The
  spec's Unit 2 wording ("when editing an existing item whose stored `category`
  is a legacy/off-taxonomy value, pre-select the normalized bucket") is scoped to
  a *present* stored value. A brand-new item with no vision suggestion
  (`initial?.category` null/undefined/blank) must still show the unselected `—`
  placeholder — exactly like the existing `formality`/`warmth` null-is-blank
  behavior — rather than defaulting to `"Other"`. Implemented as
  `toDraftCategory`: `rawCategory ? normalizeCategory(rawCategory) : ''`. This
  keeps the existing "renders a null suggestion as empty, editable fields" test
  meaningful and consistent with the rest of the form.

- **A3.2 — Updated pre-existing consumer tests that assumed free-text category
  pass-through (`TagForm.test.tsx`, `AddItem.test.tsx`, `ItemDetail.test.tsx`).**
  Replacing the category `<input>` with a taxonomy `<select>` is a real behavior
  change: a select can only hold one of `CATEGORIES` (or the blank placeholder),
  never arbitrary free text, and any *present* seed value is now normalized. This
  broke several pre-existing tests that typed or asserted arbitrary strings
  (`"denim jacket"`, `"scarf"`, `"trucker jacket"`, `"shirt"` round-tripped
  verbatim, etc.) — not because a step was skipped, but because the tests
  encoded the very free-text behavior Unit 2 removes. Updated them in place
  (same files, no new scope) to use `selectOptions` on a taxonomy value and
  assert the normalized/selected bucket instead of raw text; where a test used
  two distinct free-text strings as a race-condition marker (`"first jacket"` /
  `"second jacket"`), swapped in two synonyms that normalize to two distinct,
  distinguishable buckets (`"jacket"`→`Jacket`, `"shirt"`→`Top`) so the test's
  original intent (proving no cross-tile bleed) still holds. `AddItem.test.tsx`
  and `ItemDetail.test.tsx` are not listed in the Task 2.0 Relevant Files table,
  but leaving them red would violate task 2.7's own "`AddItem.test.tsx` stays
  green" requirement and the phase's "run the full test suite" gate — this is
  necessary collateral of implementing 2.0 correctly, not scope creep into
  Tasks 3.0/4.0.

- **A3.3 — Dropdown screenshot deferred to manual verification.** This
  implementation run is headless (no paired browser/`claude-in-chrome` session
  available), matching the precedent already established in this repo for the
  same situation (`docs/specs/21-spec-manual-outfit-assembly/21-proofs/*.md`,
  Tasks 1.0–3.0). Rather than fabricate a screenshot, the "add/edit form with
  the category dropdown open" artifact is explicitly deferred to manual/
  on-device verification in `18-proofs/18-task-02-proofs.md`; the same behavior
  is covered by the machine-verified `TagForm.test.tsx` assertion that the
  category control is a `<select>` listing exactly `CATEGORIES`.

## Phase 3 — Task 3.0 implementation

Decisions made while implementing Task 3.0 (delegated worker, no live user contact;
manager-authorized to resolve inline).

- **A3.4 — `sectionOrder()` returns `CATEGORIES` directly rather than a
  separately-maintained list.** `CATEGORIES` already places `Other` last
  (established in Task 2.0), so a distinct `sectionOrder` array would be a
  second copy of the same invariant that could drift. `sectionOrder()` is kept
  as a named function (not a re-export) so `wardrobeSections.ts` and its tests
  express intent — "the fixed section order," not "the taxonomy list" — even
  though the values are identical today. Non-blocking; trivially changeable if
  section order and select-option order are ever meant to diverge.

- **A3.5 — Section header element is `<h2>`, not a generic `<div>`.** The
  spec calls for a "section header" without specifying markup. Used a real
  heading element (`<h2 className="section-header">`) so the header is
  accessible (`getByRole('heading', ...)` in tests, screen-reader landmark
  navigation in the app) rather than a `<div>`/`<p>` styled to look like one.
  Each section is wrapped in its own `<section>` for a semantic
  heading→content grouping; the existing `data-testid="wardrobe-grid"` outer
  `<section>` and its loading/empty/error branches are unchanged.

- **A3.6 — Grouped-`/wardrobe` screenshot deferred to manual verification,**
  for the same reason as A3.3 (this run is headless; no paired browser session
  to capture a real screenshot). Documented in
  `18-proofs/18-task-03-proofs.md`; the grouping/ordering behavior it would
  show is instead covered by the machine-verified `WardrobeGrid.test.tsx`
  assertions (fixed section order, `Other` last, a "Jewelry" header, empty
  sections omitted) plus the pure `wardrobeSections.test.ts` unit tests.

## Phase 3 — Task 5.0 remediation (post-review)

Decisions made while remediating the Task 2.0 backup-reviewer finding
(`18-reviews/18-task-02-review.md`) as Task 5.0 (delegated worker, no live user
contact; manager-authorized to resolve inline).

- **A5.1 — New `appendOptionalNumber` helper instead of widening
  `appendOptional`.** The existing `appendOptional(form, key, value: string |
  null | undefined)` in `frontend/src/api/items.ts` is typed for string tag
  fields (`primaryColor`/`secondaryColor`/`pattern`). `formality`/`warmth` are
  `number | null` on `TagInput`. Rather than widen `appendOptional`'s signature
  to a union (`string | number | null | undefined`) — which would let a caller
  accidentally pass a number where a string field is expected, or vice versa,
  with no compiler help — added a small sibling `appendOptionalNumber(form,
  key, value: number | null | undefined)` that does the null/undefined check
  then `String(value)`-converts only on append. Same omit-when-null behavior,
  same call-site shape, stronger typing at each call site. Non-blocking,
  trivially squashable into one generic helper later if the pattern grows a
  third type.
- **A5.2 — `updateTags` audited, confirmed unaffected, left unchanged.** Per
  the remediation scope ("audit the tag-update path in the same file for the
  same null-handling bug — fix it too if affected"), re-verified the
  reviewer's claim: `updateTags` builds its body via
  `JSON.stringify(tags)`, so `formality: null`/`warmth: null` already
  serialize as JSON `null` (not the string `"null"`) with no code change
  needed. No fix applied to `updateTags`; this is stated explicitly in the
  Task 5.0 proof file rather than left implicit, so a reviewer doesn't have to
  re-derive it.
- **A5.3 — No backend change.** `TagRequest.formality`/`warmth` were already
  relaxed to nullable in Task 1.0 (`@Min`/`@Max` only, no `@NotNull`); the
  existing `WardrobeControllerTest` Jewelry-create case already asserts
  omitted `formality`/`warmth` params bind to `null` and return `201`. The
  fixed frontend request shape (field omitted entirely) is exactly what that
  test already exercises, so no backend test or code change was needed —
  confirmed by reading the existing test rather than starting the backend.

## Explicitly accepted trade-offs (post-review, user-directed)

- **A6.1 — Hand-duplicated taxonomy across stacks can silently diverge
  (accepted).** `CategoryTaxonomy.java` and `categoryTaxonomy.ts` are maintained
  independently (per A1.4). Their agreement is exact today but **nothing
  automated enforces it**: the "test invariant" from Task 4.1 binds
  `specSheet.ts` to the *frontend* taxonomy only — no test compares frontend to
  backend. Adding a value or synonym (e.g. `romper → Dress`) to one side only
  would silently diverge backend save-path normalization from frontend
  edit/display-time normalization, and no test would fail. At demo scale
  (8 values, ~20 items, both files' docstrings cross-referencing each other and
  this entry) this is an **explicitly accepted** maintainability trade-off, not
  a silent one. **Revisit trigger:** if the taxonomy is expected to grow, add a
  cheap guard first — either a shared JSON fixture both stacks load their
  values/synonyms from, or a CI check that extracts and diffs the two lists.
  Recorded at the user's direction after the push-gate review (2026-07-22).
