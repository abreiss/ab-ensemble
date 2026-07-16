# Task 01 Proofs — Deterministic "I wore this" wear-history write

## Task Summary

This task adds the deterministic wear-history write: `WardrobeService.markWorn(itemId)`
increments `wornCount` (null/absent treated as 0) and sets `lastWorn = now`, computed
server-side (never by the model), exposed as `POST /api/items/{id}/worn` returning the
updated item. An unknown id reuses the existing `ItemNotFoundException` → 404 rule.

## What This Task Proves

- The wear write is **deterministic**: the count increments by exactly one and `lastWorn`
  is set to the server clock, with a null/absent count seeded to 0.
- The `POST /api/items/{id}/worn` endpoint returns `200` with the updated item and a
  sanitized `404` for an unknown id.
- The change **persists** to DynamoDB and leaves tags / `createdAt` untouched.
- The update rule has **100% branch coverage** (the null-vs-present count fork).

## Evidence Summary

- `WardrobeServiceTest` (4 new cases) + `WardrobeControllerTest` (2 new cases) +
  `WardrobeRepositoryIT` (1 new round-trip) all pass in the full backend suite
  (`BUILD SUCCESSFUL`).
- JaCoCo reports `markWorn` at **BRANCH 0 missed / 2 covered** and **LINE 0 missed / 5
  covered** → 100% branch + line on the update rule.
- A live end-to-end run shows create (wornCount 0) → `POST /worn` (1 + timestamp) →
  `POST /worn` again (2, deterministic re-wear) → unknown id `404 not_found`.

## Artifact: Backend unit + controller + integration tests

**What it proves:** the deterministic update rule, the HTTP contract, and real
persistence through DynamoDB Local.

**Why it matters:** this is the strict-TDD heart of the wear side — the rule must be exact
and provably covered.

**Command:**

~~~bash
./gradlew test -PskipFrontend
~~~

**Result summary:** the full suite passes, including
`WardrobeServiceTest.markWorn_firstTime_setsCountToOneAndLastWorn`,
`markWorn_existingCount_incrementsAndUpdatesLastWorn`, `markWorn_nullCount_treatedAsZero`,
`markWorn_unknownId_throwsNotFound`;
`WardrobeControllerTest.postWorn_valid_returns200WithUpdatedItem`,
`postWorn_unknownId_returns404`; and
`WardrobeRepositoryIT.markWorn_roundTrip_persistsIncrementAndLastWornLeavingTagsUntouched`.

~~~text
> Task :test
BUILD SUCCESSFUL in 17s
~~~

## Artifact: JaCoCo branch coverage on the update rule

**What it proves:** the null/zero seed branch of `markWorn` is exercised — 100% branch.

**Why it matters:** the spec requires 100% branch on the wear-history rule (critical logic).

**Command:**

~~~bash
./gradlew jacocoTestReport -PskipFrontend
# parsed build/reports/jacoco/test/jacocoTestReport.xml for method markWorn
~~~

**Result summary:** `markWorn` counters — `BRANCH: missed 0, covered 2`;
`LINE: missed 0, covered 5`; `METHOD: covered 1`.

~~~text
markWorn counters (missed,covered): {'INSTRUCTION': (0, 28), 'BRANCH': (0, 2), 'LINE': (0, 5), 'COMPLEXITY': (0, 2), 'METHOD': (0, 1)}
~~~

## Artifact: Live end-to-end HTTP run (DynamoDB Local)

**What it proves:** the endpoint works through the real HTTP + persistence stack, the
count increments deterministically across repeated wears, and an unknown id degrades to a
sanitized 404.

**Why it matters:** confirms the feature behaves end-to-end, not just under mocks. Run
against the current build on port 8081 (a stale dev server held 8080); the shared
DynamoDB Local backed both. Test items were deleted afterward.

**Commands:**

~~~bash
# valid image generated with PIL: Image.new('RGB',(120,120)).save('/tmp/ens_test.jpg')
curl -s -X POST localhost:8081/api/items -F photo=@/tmp/ens_test.jpg \
  -F category=top -F primaryColor=navy -F formality=3 -F warmth=2 -F descriptors=cotton
curl -s -X POST localhost:8081/api/items/<id>/worn
curl -s -X POST localhost:8081/api/items/<id>/worn          # again
curl -s -w "\nHTTP %{http_code}\n" -X POST localhost:8081/api/items/does-not-exist/worn
~~~

**Result summary:** create returned `wornCount=0, lastWorn=None`; the first `/worn`
returned `wornCount=1` with a fresh `lastWorn`; the second returned `wornCount=2` (realistic
re-wear); the unknown id returned `HTTP 404` with `{"error":"not_found"}`.

~~~json
// POST /worn (first)
{ "itemId": "977a2802-…", "wornCount": 1, "lastWorn": "2026-07-16T21:50:39.989995Z", "category": "top", "createdAt": "2026-07-16T21:50:39.784437Z" }
// POST /worn (second)
{ "wornCount": 2, "lastWorn": "2026-07-16T21:50:40.035090Z" }
// POST /worn unknown id
{ "error": "not_found", "message": "item not found: does-not-exist" }  // HTTP 404
~~~

## Reviewer Conclusion

The wear-history write is deterministic, server-computed, HTTP-exposed, persisted, and
100%-branch-covered — with both automated (unit/controller/IT) and live end-to-end
evidence. No secrets or real photos are present in this proof.
</content>
