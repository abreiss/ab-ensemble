# 26 Questions Round 1 - UI Improvements (Saved Outfits + Responsive Layout)

Please answer each question below (check one or more options, or add your own
notes under any question). Issue #26 is already very detailed and prescriptive —
these are the few decisions that genuinely fork the implementation and that I
should not guess on. Everything else in the issue is taken as decided.

---

## 1. Where do saved outfits get stored? (data-model topology)

Issue #26 says the outfit entity gets "its own id (`outfitId` partition key)"
and uses "the same enhanced-client pattern as the existing item repository."
But the codebase has **always used a single shared DynamoDB table**
(`ensemble-items`, partition key `itemId`): even the daily-call-cap counters
live in that same table as reserved-prefix `usage#<date>` rows that are filtered
out of item scans (`WardrobeRepository.findAll`). The issue's wording and the
repository's actual precedent point in different directions, so I want your call
before baking it into the tasks.

Note: `com.ensemble.stylist.Outfit` already exists as an *ephemeral* stylist-pick
value record, so the persisted entity will be named **`SavedOutfit`** (new
`com.ensemble.outfit` package) regardless of which option you pick.

- [x] (A) **Separate table** `abreiss-ensemble-outfits`, partition key `outfitId`
      — a parallel `SavedOutfit` bean + `OutfitRepository` that mirror
      `Item` / `WardrobeRepository` one-to-one. Local dev auto-creates the second
      table exactly like the items table; cloud needs **one** new Terraform
      `aws_dynamodb_table` resource + a table-name env var (the instance-role IAM
      is already `table/abreiss-ensemble-*`, so **no IAM change**).
- [ ] (B) **Shared existing table**, storing outfits as reserved-prefix rows
      (`outfit#<uuid>` under the `itemId` partition key) exactly like the
      `usage#<date>` counter rows already do — a `SavedOutfit` bean mapped to the
      same table, with scan-filtering discipline (item scans exclude `outfit#`,
      outfit listing selects `outfit#`). **Zero** Terraform / IAM / table change;
      strictly one physical table, matching `ARCHITECTURE.md`'s "single-table".
- [ ] (C) Other (describe)

**Current best-practice context:** DynamoDB single-table design (one physical
table, multiple entity types distinguished by key prefix) is a well-established
pattern and is what this repo already does for the `usage#` rows — it minimizes
provisioned tables and IAM surface. Separate-table-per-entity is simpler to reason
about and is the more common default when entities are independent and there are
no cross-entity access patterns (which is the case here — outfits and items are
never queried together).

**Recommended answer(s):** [(A)]

**Why this is recommended:**

- `(A)` matches the issue's explicit "`outfitId` partition key" wording and
  mirrors the existing `WardrobeRepository` slice most directly (its own
  `DynamoDbTable<SavedOutfit>`), which keeps the grounding-guard and CRUD code a
  clean copy of a pattern already proven in this codebase.
- Saved outfits are a **first-class, browsable entity**, not an internal counter;
  overloading the items table with them (option B) couples the two scans forever
  and means every future `findAll` has to remember to exclude another prefix.
- The deploy cost of `(A)` is genuinely small and fully understood: one Terraform
  table resource + one env var, with IAM already covered by the `-*` wildcard.
- `(B)` is the lightest-deploy option and the most literal reading of
  "single-table," so it is a legitimate choice if you'd rather add **zero** new
  infrastructure — pick it if avoiding a second table matters more than keeping
  the entity's storage cleanly separate.

---

## 2. Is the cloud/Terraform wiring in scope for this issue, or a deploy follow-up?

(Mostly relevant if you pick **1(A)**; if you pick **1(B)** there is no
infrastructure change and this question is largely moot.)

The project was built **local-first** (#2–#7) with deploy handled separately
(#9), and deploys are operator-run. A new separate outfits table needs a small
cloud change to actually work in production.

- [ ] (A) **Local-first only now** — implement and validate against DynamoDB
      Local / TestContainers; capture the cloud table + env-var wiring as a
      documented deploy-time follow-up (matches how the project separated build
      from deploy).
- [ x] (B) **Include the cloud wiring in this issue** — add the Terraform
      `outfits` table resource + the `ENSEMBLE_OUTFITS_TABLE_NAME` env var
      (apprunner.tf + cloud profile) as part of this work, so a deploy after
      merge "just works" and never writes to a non-existent table.
- [ ] (C) Other (describe)

**Recommended answer(s):** [(B)]

**Why this is recommended:**

- Shipping a feature that writes to a table that doesn't exist in production is a
  latent 5xx landmine; the delta here is tiny and well-scoped, so folding it in
  now removes that risk.
- It stays fully within the established blast-radius box (the `-*` prefix IAM
  already permits it), so it doesn't expand the security surface.
- `(A)` is still reasonable if you prefer to keep this issue strictly local-first
  and batch all Terraform changes into a deliberate deploy pass — the follow-up
  is small either way.

---

## 3. How should the Saved Outfits page render an outfit whose piece was later deleted?

The issue requires that a saved outfit referencing an item that has since been
**deleted from the wardrobe** must never crash — "skip / placeholder … Define
behavior." I need the exact behavior for the acceptance criteria.

- [ ] (A) **Silently skip** the missing piece(s); render the outfit with the
      pieces that still exist. If *every* piece is gone, still show the card with
      a short "pieces no longer in your wardrobe" note (never a blank/broken
      card).
- [ ] (B) **Placeholder tile** — render a neutral "removed" tile in place of each
      missing piece so the original piece count/positions are preserved.
- [ x] (C) **Skip + note** — skip missing pieces (as in A) **and** show a subtle
      caption like "1 piece is no longer in your wardrobe," so the user
      understands why the look looks smaller than when they saved it.
- [ ] (D) Other (describe)

**Recommended answer(s):** [(C)]

**Why this is recommended:**

- `(C)` is honest without being ugly: the user sees the real, still-ownable
  pieces plus a quiet explanation, rather than a mysteriously shrunk look (A) or
  a grid of dead placeholder tiles (B).
- All three satisfy the hard "never crash / tolerate missing piece" requirement;
  the choice is purely UX polish, so any is safe — `(A)` is the simplest if you'd
  rather not add the note.
- Whichever you pick, the missing piece is resolved at **render time** by
  matching saved `itemIds` against the current wardrobe list (the saved record
  itself is never rewritten when an item is deleted).

---

### Assumptions I'll take as decided unless you say otherwise

These come straight from the issue's own recommendations, so I'm **not** asking
about them — flag any you want changed:

- **Duplicate saves allowed** (saving the same set twice creates two records) —
  simplest, harmless at demo scale, per the issue's own recommendation.
- **Persisted entity named `SavedOutfit`** in a new `com.ensemble.outfit`
  package (avoids the existing `stylist.Outfit` collision).
- **Photos are not duplicated** into the outfit record; the page renders each
  piece via the existing `photoUrl(itemId)` builder.
- **Save-time grounding guard** rejects the whole save with `400` if any
  `itemId` is unknown (synchronous analog of the stylist's drop-and-retry
  grounding).
- **Header nav order** becomes: **Saved · Build · Wardrobe · + Add** (Saved link
  placed left of Build).
- **Part B (responsive layout)** generalizes the current
  `#root:has(.stylist-layout) { max-width: 72rem }` exception into the default
  shell behavior (desktop fills up to ~72rem, mobile stays single-column), and is
  verified manually on mobile + desktop rather than unit-tested.
