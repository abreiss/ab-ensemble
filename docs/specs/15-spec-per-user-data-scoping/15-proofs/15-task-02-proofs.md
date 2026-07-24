# Task 02 Proofs — Ownership-enforced wardrobe & outfit APIs

## Task Summary

This task closes the live cross-user authorization gap: every `/api/items` and
`/api/outfits` operation is now scoped to the authenticated caller's `userId`
(resolved from the session token via `@CurrentUserId`). Lists return only the
caller's rows (through the `userId-index` GSI), `create` stamps the caller as
owner, and every single-resource operation routes through an ownership choke
point that throws a **non-enumerating 404** for any id the caller does not own —
so a cross-user id is indistinguishable from a missing one and never returns
another user's data.

## What This Task Proves

- **Scoped reads.** `WardrobeService.list(userId)` / `OutfitService.list(userId)`
  delegate to the `findByUserId` GSI query — two accounts see disjoint sets.
- **Owner stamping.** `create(userId, …)` persists the caller's `userId` on the
  new `Item`/`SavedOutfit` (captured and asserted directly, not inferred).
- **Ownership choke point.** `get` / `updateTags` / `markWorn` / `delete` (and
  outfit `delete`) on another user's id throw `ItemNotFoundException` /
  `OutfitNotFoundException` → **404**, never that resource's contents.
- **Controllers thread the caller.** All seven wardrobe handlers and all three
  outfit handlers take `@CurrentUserId` and forward it to the service.
- **No regression.** The full backend suite (incl. DynamoDB Local ITs and the
  stylist, which still uses the retained unscoped `list()` until Unit 4) stays green.

## Evidence Summary

- Unit tests for scoped `list`, cross-user 404, and owner-stamping pass at the
  service and controller layers for both wardrobe and outfit.
- A **live two-account run** (DynamoDB Local, isolated `proof15-*` tables) shows
  disjoint item/outfit lists and every cross-user op returning `404` while the
  owner's data survives.
- Full backend suite: **BUILD SUCCESSFUL**, 0 failures.

---

## Artifact: Scoped service + controller unit tests (both aggregates)

**What it proves:** Our handling is correct regardless of transport — scoped reads,
owner stamping, and the cross-user → 404 rule are enforced in the domain layer and
threaded by the controllers.

**Why it matters:** These are the fast, deterministic guardrails that keep the
isolation from regressing; the `create_stampsCallerUserId` captor test specifically
guards against writing an owned row without an owner.

**Command:**

```bash
./gradlew test -PskipFrontend --tests '*WardrobeServiceTest' --tests '*WardrobeControllerTest' \
  --tests '*OutfitServiceTest' --tests '*OutfitControllerTest' --tests '*SessionAuthFilterTest'
```

**Result summary:** All pass. Per-suite counts (from `build/test-results/test`):
`WardrobeServiceTest` 19, `WardrobeControllerTest` 27, `OutfitServiceTest` 13,
`OutfitControllerTest` 10, `SessionAuthFilterTest` 9 — all `failures=0 errors=0`.
New behavior tests include `list_returnsOnlyCallersItems`,
`find_otherUsersItem_throwsNotFound`, `create_stampsCallerUserId`,
`get/updateTags/postWorn/deleteItem_otherUsersItem_returns404`,
`list_returnsOnlyCallersOutfits`, `find_otherUsersOutfit_throwsNotFound`,
`deleteOutfit_otherUsersOutfit_returns404`.

```
<testsuite name="com.ensemble.wardrobe.WardrobeServiceTest"    tests="19" skipped="0" failures="0" errors="0"/>
<testsuite name="com.ensemble.wardrobe.web.WardrobeControllerTest" tests="27" skipped="0" failures="0" errors="0"/>
<testsuite name="com.ensemble.outfit.OutfitServiceTest"        tests="13" skipped="0" failures="0" errors="0"/>
<testsuite name="com.ensemble.outfit.web.OutfitControllerTest" tests="10" skipped="0" failures="0" errors="0"/>
<testsuite name="com.ensemble.security.web.SessionAuthFilterTest" tests="9" skipped="0" failures="0" errors="0"/>
```

`SessionAuthFilterTest` is a full-context `@SpringBootTest` that exercises the real
`SessionAuthFilter` → `@CurrentUserId` resolution end-to-end (not just the slice
`.requestAttr(...)` shortcut).

## Artifact: Live two-account isolation run (success metric #1)

**What it proves:** With two real logged-in accounts against DynamoDB Local, each
sees only its own data and no cross-user operation can read, mutate, or delete the
other's resources.

**Why it matters:** This is the end-to-end demoable outcome — the isolation holds
over HTTP with real tokens and a real GSI query, not only against mocks.

**Setup (sanitized):** the app ran against **isolated** `proof15-*` tables (created
fresh, so they carry the `userId-index` GSI) with a **distinct**
`ENSEMBLE_SESSION_SECRET` and a temporary photo dir; both accounts were created via
invite-code signup. Tokens are referenced as `$TOKEN_A` / `$TOKEN_B`, never printed.
The `proof15-*` tables were deleted afterward; the developer's real
`ensemble-*` tables were never touched.

**Result summary:** Disjoint item **and** outfit lists per account; every cross-user
op on A's item called by B (`GET`, photo `GET`, `PUT` tags, `POST` worn, `DELETE`)
returned `404`; A's item and outfit survived; B could not delete A's outfit.

```
===== 3. GET /api/items is disjoint per account =====
-- A sees: ['930cbf47-4f77-4493-808f-cfe92b8dcb07']
-- B sees: ['ab0be689-e692-403b-baa3-3df559d94fe5']

===== 4. Cross-user single-resource ops on A's item, called by B =====
-- B GET    A's item      -> HTTP 404
-- B GET    A's photo     -> HTTP 404
-- B PUT    A's tags      -> HTTP 404
-- B POST   A's worn      -> HTTP 404
-- B DELETE A's item      -> HTTP 404

===== 5. A's item survived B's attempts (still 200 for A) =====
-- A GET    A's item      -> HTTP 200

===== 6. Outfits: B cannot delete A's saved outfit =====
-- B DELETE A's outfit    -> HTTP 404
-- B outfit list: []
-- A outfit list: ['af16c2cd-088c-47ca-86e0-c506df9058b8']
```

## Artifact: Full backend regression suite

**What it proves:** Re-signing the service methods and threading `@CurrentUserId`
broke nothing elsewhere — including the DynamoDB Local integration tests and the
stylist, which still reads the retained unscoped `list()` until Unit 4.

**Why it matters:** Confirms the green-commit sequencing held: the scoped API is in
place while every remaining unscoped reader still compiles and passes.

**Command:**

```bash
./gradlew test -PskipFrontend
```

**Result summary:** `BUILD SUCCESSFUL`; 52 test classes executed, 0 failures/errors.
DynamoDB Local ITs ran (`WardrobeRepositoryIT` 10, `OutfitRepositoryIT` 7).

## Reviewer Conclusion

Per-user authorization is enforced end-to-end: reads are GSI-scoped, writes are
owner-stamped, and every cross-user single-resource operation degrades to a
non-enumerating 404. The mocked unit/slice tests lock the behavior in, and the live
two-account run demonstrates the demoable outcome with no data leaking across users.
This isolation is only as strong as the session token — the docs now state a distinct
`ENSEMBLE_SESSION_SECRET` is required for it to be trustworthy (the invite-code
fallback would let any invited user forge a `userId`).
