# 15 Questions Round 1 — Scope wardrobe data to the logged-in user

Please answer each question below (check one or more options, or add your own
notes under "Other"). Feel free to add context under any question. Once you've
saved your answers, tell me and I'll re-check sufficiency and write the spec.

Context: issue [#15](https://github.com/abreiss/ab-ensemble/issues/15) depends
on #14 (accounts). #14 already produced a resolvable `userId`
(`SessionAuthFilter` stashes it as the `ensemble.userId` request attribute;
controllers read it via `@CurrentUserId String userId`, e.g. `MeController`).
Today `Item` has **no owner field**, `WardrobeRepository.findAll()` does an
unscoped `table.scan()`, `photoKey` is just `<itemId>.jpg`, the stylist's
`searchWardrobe` returns every item in the table, and **no wardrobe endpoint
checks ownership at all** — any logged-in user can already read/delete any
other user's item by guessing its `itemId`. This round locks the handful of
decisions that materially change the implementation, migration, and acceptance
criteria.

---

## 1. `Item` key design — how do we make items queryable per user?

The issue names "composite key (`userId#itemId`) vs. keeping `itemId` as the
key with a `userId` GSI." My exploration found a wrinkle that matters: the
deployed `ensemble-items` table already exists with `itemId` as its **sole
HASH key** (Terraform-owned), and the daily-call-cap counter rows
(`usage#<date>`) live in that **same** table keyed only by `itemId`. DynamoDB
does **not** allow changing a table's key schema in place.

- [x ] (A) **`userId` GSI, keep `itemId` as the primary partition key.** Add a
      `userId` attribute to `Item`, annotate a Global Secondary Index on it.
      `WardrobeRepository` lists a user's items via `index.query(userId)` (no
      scan). `findById(itemId)` stays as-is, plus an **ownership check**
      (`item.userId == caller`) on every single-item op. Migration = an in-place
      `userId` set on existing rows (key unchanged). The GSI is **sparse**, so
      the keyless `usage#<date>` counter rows are naturally excluded. Terraform
      adds a GSI to the existing table (no table recreation).
- [ ] (B) **Composite primary key: partition = `userId`, sort = `itemId`.**
      Textbook tenant isolation; a single `query(userId)` returns exactly that
      user's items. But because you can't change a live table's key schema, this
      requires **destroying and recreating** the deployed `ensemble-items`
      table, a full data migration into the new key schema, and moving/reshaping
      the `usage#<date>` counter rows to fit the composite schema.
- [ ] (C) Other (describe)

**Current best-practice context:** For multi-tenant DynamoDB, using the tenant
id as the partition key (B) gives the strongest isolation, while a tenant-id
GSI (A) is a widely used, lower-friction alternative that AWS considers valid
at small/medium scale. This is stable, well-established DynamoDB modeling
guidance — no volatile external standard is in play. Notably, `ARCHITECTURE.md`
already names "a `userId` GSI" as the intended scale path, and the sibling
`UserRepository.findByUserId` already tolerates a full scan at demo scale.

**Recommended answer(s):** (A)

**Why these are recommended:**

- (A) is far lower-risk for *this* codebase: no destroy/recreate of a deployed,
  Terraform-owned table, an in-place migration (just set an attribute), and the
  sparse GSI cleanly sidesteps the `usage#<date>` counter rows that share the
  table — whereas (B) forces those counter rows to be reshaped or relocated.
- (A) matches the direction already written into `ARCHITECTURE.md` ("a `userId`
  GSI is the scale path"), so it's consistent with the documented architecture
  rather than a new departure.
- (B) is the "purer" isolation model and worth choosing if you specifically want
  partition-level tenant isolation for its own sake, but at demo scale (~20
  items) it buys little over (A) while costing a table recreation. Either option
  satisfies the issue's "no more full-table scans" requirement.
- Under (A), single-item endpoints (`GET /api/items/{id}`, `/photo`, `delete`,
  `updateTags`, `markWorn`) need an explicit ownership check; I'll spec those to
  return **404** (not 403) on a cross-user id, so existence isn't leaked —
  consistent with #14's non-enumerating auth posture. (Flag if you'd prefer 403.)

---

## 2. Data migration — how do pre-existing items get an owner?

#14 already locked the *target*: existing wardrobe data is reassigned to the
**seed account** (`ENSEMBLE_SEED_EMAIL` / `ENSEMBLE_SEED_PASSWORD`), and #15
owns doing it. Open part is the *mechanism* and the no-seed-account case. No
wardrobe seeder/migration exists yet; the closest pattern is
`SeedAccountRunner` (an idempotent startup `ApplicationRunner`).

- [ ] (A) **Idempotent startup `ApplicationRunner`**, mirroring
      `SeedAccountRunner` and ordered to run after it. On boot, every item (and
      photo/outfit, per Q4) with no `userId` is assigned the seed account's
      `userId`. Idempotent (skips already-owned rows); **no-op** when no seed
      account is configured — unowned rows simply stay invisible to everyone
      until an owner exists (data is never deleted). Gated by a config flag.
- [ ] (B) **Standalone one-time migration** (a CLI entry point / operator-run
      task), invoked deliberately once rather than on every startup.
- [x ] (C) Other (describe just delete the prexisting clothing items. they are not nessacary. ensure nothing outisde of the abreiss ensemble work is touched as this is a shared account. IAM should be already scoped for this

**Recommended answer(s):** (A)

**Why these are recommended:**

- (A) reuses an established, tested pattern in this repo (`SeedAccountRunner`) —
  same idempotency, same env-gating, same ordering discipline — so it's the
  least-invention path and easy to prove with a migration test.
- Because the migration under Q1-(A) is an in-place attribute set (not a key
  rewrite), running it idempotently at every startup is safe and cheap at demo
  scale; the no-seed-account no-op means it can't do anything surprising in an
  environment that hasn't opted in.
- (B) is the more "production-correct" posture for a heavy/destructive
  migration, but it adds an operator step and tooling this demo doesn't
  otherwise have; worth choosing only if you object to any data-touching logic
  running automatically at startup.

---

## 3. Daily call cap — per-user, global, or both?

Today it's **one global** `usage#<date>` counter (~100/day → 429), a choice
`07-questions-1-pwa-security-guards.md` made *explicitly for the single-user
case*. #14 flagged revisiting it here. Now that the app is multi-tenant, one
user can consume the whole day's budget for everyone.

- [ ] (A) **Per-user cap.** Counter key becomes `usage#<userId>#<date>`; each
      account gets its own ~100/day. Small change to `CallCapService.reserve()`
      (which now has the caller's `userId`) and `UsageRepository`.
- [x ] (B) **Keep the single global cap.** Simplest; preserves the original
      total-cost backstop intent, but one account can starve the others.
- [ ] (C) **Both** — a per-user cap *and* a global ceiling (belt-and-suspenders:
      fairness plus a hard total-spend limit).
- [ ] (D) Other (describe)

**Recommended answer(s):** (A), with (C) as the more robust alternative

**Why these are recommended:**

- (A) is the natural fit once data is per-user: it stops one account from
  exhausting the shared budget and is a minimal change now that `reserve()` can
  see the caller's `userId`. It's cleanly testable per user.
- (C) is the most correct if you care about *both* fairness and a hard cap on
  the total API bill (the original reason the global cap existed) — pick it if
  the shared-account cost ceiling still matters to you.
- (B) is fine only if you'd rather not touch the cap at all this round; given
  the issue explicitly asks to revisit it, leaving it global should be a
  deliberate choice, not a default.

---

## 4. Are saved outfits (`SavedOutfit` / `OutfitRepository`) in scope?

The issue enumerates "items, photos, stylist," but its Goal says "Every item,
photo, and stylist interaction is scoped to the authenticated user. **The app
becomes multi-tenant at the data layer**, not just at the login screen."
Exploration found `SavedOutfit`/`OutfitRepository` (from #21, manual outfit
assembly) has the **identical** leak: `outfitId`-only key, `findAll()` scan, no
owner field, no ownership check. Leaving it global would be a real cross-user
privacy leak that contradicts that Goal.

- [ x] (A) **Include saved outfits in #15.** Apply the same key/GSI + ownership +
      migration pattern to `SavedOutfit`/`OutfitRepository`/`OutfitController` so
      *all* user data is scoped, matching "multi-tenant at the data layer."
- [ ] (B) **Defer outfits to a separate follow-up issue.** Keep #15 to the
      literal enumerated scope (items/photos/stylist); track the outfit leak
      separately.
- [ ] (C) Other (describe)

**Recommended answer(s):** (A)

**Why these are recommended:**

- (A) honors the issue's own Goal statement ("multi-tenant at the data layer")
  and closes an equivalent privacy hole with the exact same pattern, so it's
  cheap incremental work rather than a new design. Shipping items-scoped but
  outfits-global would leave the app *not actually* multi-tenant.
- (B) is defensible only if you want to cap #15's size; the tradeoff is a known,
  unclosed cross-user leak living on `main` until the follow-up lands. If you
  pick (B), I'll add a Non-Goal and recommend filing the follow-up immediately.
- This is the main lever on spec size: (A) adds a demoable unit; (B) keeps #15
  to ~4 units.
