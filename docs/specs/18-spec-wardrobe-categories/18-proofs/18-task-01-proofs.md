# Task 01 Proofs - Backend taxonomy foundation

## Task Summary

This task establishes the wardrobe category taxonomy as a single backend
source of truth and makes it authoritative on every save path. It adds the
ordered eight-value taxonomy constant plus a pure, never-throwing `normalize`
function (`CategoryTaxonomy`), constrains the vision tool-use schema with an
`enum` derived from that constant, relaxes `TagRequest.formality`/`warmth` to
nullable so Jewelry/Accessory items can save, and wires `normalize` into the
single write choke point (`ItemMapper.applyTags`, covering both `create` and
`updateTags`) and into the vision-suggestion path (`TaggingService.map`). No
DynamoDB schema change and no batch migration were made.

## What This Task Proves

- A recognized/legacy/off-taxonomy `category` string always resolves to one of
  the eight taxonomy values, and an unrecognized/blank/`null` value always
  resolves to `"Other"` — `normalize` never throws, with **100% branch
  coverage**.
- A create request for a Jewelry item with `formality`/`warmth` omitted
  returns `201`, not `400` — the nullability relaxation is real, and existing
  supplied-out-of-range rejections still `400`.
- The vision tool-use schema's `category` property carries the taxonomy
  `enum` as a hint to the model.
- The vision-suggestion path (`TaggingService.map`) normalizes an
  off-taxonomy model category (e.g. `"sweatshirt"`) to its taxonomy bucket
  (`"Top"`) before it reaches the `TagSuggestion` pre-fill.
- The single write choke point (`ItemMapper.applyTags`) persists a normalized
  taxonomy value for both `create` and `updateTags`, for both legacy and
  canonical input.
- `Item.java` (the DynamoDB persistence model) is untouched — no schema
  change, no migration.

## Evidence Summary

- `CategoryTaxonomyTest` (15 tests) covers every canonical value, the starter
  legacy-synonym map, casing/whitespace variants, and unrecognized/blank/null
  input — all green, and JaCoCo reports **0 missed branches** on
  `CategoryTaxonomy` (6/6 covered).
- `WardrobeControllerTest` (22 tests, including the new Jewelry case) is
  green — a Jewelry create with no `formality`/`warmth` params returns `201`.
- `AnthropicVisionModelClientTest` (4 tests, including the new enum
  assertion) is green — the `category` tool-schema property's `enum` exactly
  matches `CategoryTaxonomy.values()`.
- `TaggingServiceTest` (21 tests, including two new normalization cases) is
  green — `"sweatshirt"` normalizes to `"Top"`; a canonical `"Jewelry"` value
  passes through unchanged; existing null/blank/malformed fallback tests are
  unaffected.
- `ItemMapperTest` (4 tests, including two new save-path cases) and
  `WardrobeServiceTest` (16 tests, including one new case) confirm
  `applyTags` persists a normalized value on both the `create` and
  `updateTags` paths.
- Full suite: **286 tests, 0 failures** (`./gradlew test -PskipFrontend`).
- `git diff --stat src/main/java/com/ensemble/wardrobe/Item.java` is empty.

## Artifact: `CategoryTaxonomy` — 100% branch coverage on `normalize`

**What it proves:** `normalize` is the critical fallback logic the whole
feature depends on (AGENTS.md / TESTING.md require 100% branch coverage
here). Every canonical value, a representative legacy-synonym set, casing/
whitespace variants, and unrecognized/blank/null input are exercised, and the
function never throws.

**Why it matters:** This is the single code-side guarantee behind "every
category that enters the system lands on a taxonomy value or `Other`" — the
vision `enum` is only an advisory hint, so this function is what actually
prevents bad data.

**Command:**

```bash
./gradlew test --tests "com.ensemble.wardrobe.CategoryTaxonomyTest" -PskipFrontend
./gradlew jacocoTestReport -PskipFrontend
```

**Result summary:** All 15 `CategoryTaxonomyTest` cases pass. The JaCoCo XML
report shows `CategoryTaxonomy` at 0 missed branches (6/6) and 0 missed lines
(23/23):

```
<testsuite name="com.ensemble.wardrobe.CategoryTaxonomyTest" tests="15" skipped="0" failures="0" errors="0" .../>
```

```xml
<class name="com/ensemble/wardrobe/CategoryTaxonomy" ...>
  ...
  <counter type="INSTRUCTION" missed="0" covered="312"/>
  <counter type="BRANCH" missed="0" covered="6"/>
  <counter type="LINE" missed="0" covered="23"/>
  <counter type="COMPLEXITY" missed="0" covered="8"/>
  <counter type="METHOD" missed="0" covered="5"/>
  <counter type="CLASS" missed="0" covered="1"/>
</class>
```

## Artifact: Jewelry saves with null formality/warmth (`201`, not `400`)

**What it proves:** `TagRequest.formality`/`warmth` are now optional (`@Min`/
`@Max` kept, `@NotNull` dropped); `category` stays required. A Jewelry create
request that omits both fields is accepted.

**Why it matters:** This is the concrete unblock for jewelry — without it,
every jewelry upload would `400` before ever reaching the domain.

**Command:**

```bash
./gradlew test --tests "com.ensemble.wardrobe.web.WardrobeControllerTest" -PskipFrontend
```

**Result summary:** All 22 `WardrobeControllerTest` cases pass, including the
new `createItem_jewelryWithoutFormalityOrWarmth_returns201` case, and the
pre-existing `createItem_formalityOutOfRange_returns400` /
`createItem_warmthOutOfRange_returns400` / `updateTags_*OutOfRange_returns400`
cases still `400` — a *supplied* out-of-range value is still rejected, only a
missing value is now accepted.

```java
@Test
void createItem_jewelryWithoutFormalityOrWarmth_returns201() throws Exception {
    when(service.create(any(), any())).thenReturn(response("new-id"));

    mockMvc.perform(multipart("/api/items")
            .file(photoPart())
            .param("category", "Jewelry")
            .param("primaryColor", "gold"))
        .andExpect(status().isCreated());

    ArgumentCaptor<TagRequest> captor = ArgumentCaptor.forClass(TagRequest.class);
    verify(service).create(captor.capture(), any());
    assertThat(captor.getValue().formality()).isNull();
    assertThat(captor.getValue().warmth()).isNull();
}
```

```
<testsuite name="com.ensemble.wardrobe.web.WardrobeControllerTest" tests="22" skipped="0" failures="0" errors="0" .../>
```

## Artifact: Vision tool-schema `category` carries the taxonomy `enum`

**What it proves:** `AnthropicVisionModelClient.tagTool()`'s `category`
input-schema property now declares `"enum": [Jacket, Top, Bottom, Dress,
Shoes, Jewelry, Accessory, Other]`, derived from `CategoryTaxonomy.values()`
(no independently-maintained duplicate list).

**Why it matters:** This is the model-facing half of the taxonomy guarantee
— a strong hint to Claude, backstopped in code by `normalize` (the `enum` is
advisory only per AGENTS.md's AI-agent standards; the actual guarantee is the
code-side fallback proven above).

**Command:**

```bash
./gradlew test --tests "com.ensemble.tagging.AnthropicVisionModelClientTest" -PskipFrontend
```

**Result summary:** All 4 tests pass, including the new
`tagTool_categoryProperty_carriesTaxonomyEnum`, which inspects the built
`Tool.InputSchema` directly (via a package-private `tagTool()`, an existing
test-seam pattern in this class) and asserts the `category` property's
`enum` list exactly matches `CategoryTaxonomy.values()`.

```
<testsuite name="com.ensemble.tagging.AnthropicVisionModelClientTest" tests="4" skipped="0" failures="0" errors="0" .../>
```

## Artifact: Vision-path normalization (`TaggingService.map`)

**What it proves:** An off-taxonomy model-emitted category (`"sweatshirt"`)
is normalized to its taxonomy bucket (`"Top"`) before it reaches the
`TagSuggestion` returned to the client; a canonical value (`"Jewelry"`) is
unaffected; a genuinely undetermined category (absent/non-textual field)
stays `null` so the form's unselected `—` placeholder still applies rather
than defaulting to `"Other"`.

**Why it matters:** Without this, an off-taxonomy vision guess would
pre-fill the edit form's `<select>` with a value outside its option list —
this is the fix that keeps the vision → suggestion → editable-form flow
consistent with the new fixed vocabulary.

**Command:**

```bash
./gradlew test --tests "com.ensemble.tagging.TaggingServiceTest" -PskipFrontend
```

**Result summary:** All 21 tests pass, including the two new cases below,
and every pre-existing null/blank/malformed/clamp/fallback case remains
green.

```java
@Test
void offTaxonomyCategory_isNormalizedToATaxonomyValue() {
    TagSuggestion out = suggestWithModelJson("{\"category\":\"sweatshirt\"}");
    assertThat(out.category()).isEqualTo("Top");
}

@Test
void canonicalTaxonomyCategory_isUnchanged() {
    TagSuggestion out = suggestWithModelJson("{\"category\":\"Jewelry\"}");
    assertThat(out.category()).isEqualTo("Jewelry");
}
```

```
<testsuite name="com.ensemble.tagging.TaggingServiceTest" tests="21" skipped="0" failures="0" errors="0" .../>
```

JaCoCo confirms `TaggingService` stays at 0 missed branches (26/26 covered)
after the change.

## Artifact: Save-path normalization at the single write choke point

**What it proves:** `ItemMapper.applyTags` — the one place both
`WardrobeService.create` and `WardrobeService.updateTags` call — normalizes
`category` via `CategoryTaxonomy.normalize` before it is set on the `Item`.
A legacy value (`"chinos"`) persists as `"Bottom"` on both paths; a Jewelry
item with null `formality`/`warmth` persists successfully.

**Why it matters:** This is the interdependency the spec calls out
explicitly — a single choke point guarantees every save path (vision or
manual, create or update) lands on a taxonomy value, so nothing downstream
has to re-derive or trust client input.

**Command:**

```bash
./gradlew test --tests "com.ensemble.wardrobe.dto.ItemMapperTest" -PskipFrontend
./gradlew test --tests "com.ensemble.wardrobe.WardrobeServiceTest" -PskipFrontend
```

**Result summary:** `ItemMapperTest` (4 tests) and `WardrobeServiceTest` (16
tests) are both green.

```java
// ItemMapperTest
@Test
void applyTags_legacyCategory_persistsNormalizedTaxonomyValue() {
    Item target = new Item();
    TagRequest tags = new TagRequest("chinos", null, null, null, null, null, null);
    ItemMapper.applyTags(target, tags);
    assertThat(target.getCategory()).isEqualTo("Bottom");
}
```

```java
// WardrobeServiceTest — confirms the choke point covers updateTags too
@Test
void updateTags_legacyCategory_persistsNormalizedTaxonomyValue() {
    when(repository.findById("x")).thenReturn(Optional.of(existing("x")));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    TagRequest legacy = new TagRequest("chinos", null, null, null, null, null, null);

    ItemResponse updated = service.updateTags("x", legacy);

    assertThat(updated.category()).isEqualTo("Bottom");
}
```

```
<testsuite name="com.ensemble.wardrobe.dto.ItemMapperTest" tests="4" skipped="0" failures="0" errors="0" .../>
<testsuite name="com.ensemble.wardrobe.WardrobeServiceTest" tests="16" skipped="0" failures="0" errors="0" .../>
```

## Artifact: Full suite green + no schema change

**What it proves:** The whole backend suite (287 tests across every package,
including the pre-existing `WardrobeRepositoryIT` DynamoDB-Local integration
tests) passes with the taxonomy foundation in place, and `Item.java` — the
`@DynamoDbBean` persistence model — has zero diff, confirming no table
migration was introduced.

**Command:**

```bash
./gradlew test -PskipFrontend
git diff --stat src/main/java/com/ensemble/wardrobe/Item.java
```

**Result summary:** `BUILD SUCCESSFUL`, 286 tests / 0 failures (100%
success per the Gradle HTML test report); the `git diff --stat` on
`Item.java` produced no output (empty diff).

```
BUILD SUCCESSFUL in 11s
5 actionable tasks: 2 executed, 3 up-to-date
```

```
$ git diff --stat src/main/java/com/ensemble/wardrobe/Item.java
$ (no output — file unchanged)
```

## Reviewer Conclusion

The backend now has one authoritative category taxonomy (`CategoryTaxonomy`)
that every save path runs through: the vision schema hints at it, the
choke-point mapper enforces it on both create and update, and the
vision-suggestion path pre-normalizes it for the eventual `<select>`-based
edit form. Jewelry/Accessory items save with null `formality`/`warmth`
without touching the tag schema's range constraints. `normalize` carries
100% branch coverage as required, the full backend suite (286 tests) is
green, and the DynamoDB item model is provably untouched — satisfying spec
Unit 1 and Success Metrics 2, 3, 5, and 6 for this parent task.
