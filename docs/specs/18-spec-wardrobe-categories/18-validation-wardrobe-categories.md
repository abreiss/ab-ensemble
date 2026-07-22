# 18-validation-wardrobe-categories.md

Validation report for **issue #18 — Wardrobe categories (grouped browsing) +
Jewelry support**, validating the implementation on branch `wardrobe-grouping`
against `18-spec-wardrobe-categories.md`, `18-tasks-wardrobe-categories.md`, and
the `18-proofs/` artifacts.

## 1) Executive Summary

- **Overall: PASS.** No gates tripped. All six mandatory gates (A–F) pass.
- **Implementation Ready: Yes.** All 20 functional requirements are verified
  against re-run tests and coverage; the one HIGH-severity finding a backup
  reviewer raised against Task 2.0 (a null-serialization bug in
  `frontend/src/api/items.ts`) was remediated as Task 5.0 and independently
  confirmed fixed.
- **Key metrics:**
  - **Requirements Verified: 20/20 (100%)** — 0 Failed, 0 Unknown.
  - **Proof Artifacts working: all machine-verifiable artifacts pass.** Two of
    the spec's screenshot artifacts (Unit 2 dropdown, Unit 3 grouped grid) are
    explicitly deferred to manual/on-device capture (headless run) — recorded
    below as a non-blocking MEDIUM note, with automated test equivalents present.
  - **Files changed vs expected: 38 changed; all mapped.** Core source files all
    trace to a Relevant-Files entry + task/FR; two changed test files outside the
    table (`AddItem.test.tsx`, `ItemDetail.test.tsx`) are linked via assumption
    A3.2. No out-of-scope core changes.
  - **Independent re-run:** backend `./gradlew test jacocoTestReport
    -PskipFrontend --rerun-tasks` → BUILD SUCCESSFUL, **286 tests / 0 failures /
    0 errors**; frontend `npm test -- --run` → **319 tests / 0 failures**;
    `npm run lint` exit 0; `npx tsc -b` exit 0.
  - **Critical-logic branch coverage (re-run JaCoCo XML):** `CategoryTaxonomy`
    **100% branch (6/6)** and 100% line (23/23); `TaggingService` (vision-tag
    mapping) **100% branch (26/26)**; `ItemMapper` 100% (no branches).

## 2) Coverage Matrix

### Functional Requirements

FR IDs are assigned per demoable unit (U#-FR#) in spec order.

| Requirement | Status | Evidence |
| --- | --- | --- |
| **U1-FR1** Taxonomy as single backend constant; derive enum + normalize | Verified | `CategoryTaxonomy.java:30-31` (ordered 8-value `VALUES`), `normalize` L59-68; `CategoryTaxonomyTest` 15 tests green; JaCoCo 100% branch. Commit `47de36a` |
| **U1-FR2** `enum` added to `category` in `tagTool()` input schema | Verified | `AnthropicVisionModelClient.java:108` `"enum", CategoryTaxonomy.values()`; `AnthropicVisionModelClientTest` enum-assertion test green (4/4). Commit `47de36a` |
| **U1-FR3** Relax `formality`/`warmth` to nullable; keep range; `category` `@NotBlank` | Verified | `TagRequest.java` `@NotNull` removed, `@Min`/`@Max` kept; `WardrobeControllerTest.createItem_jewelryWithoutFormalityOrWarmth_returns201` green; out-of-range-→400 cases still green (22/22) |
| **U1-FR4** Pure `normalize` → taxonomy; unrecognized/blank/null → `Other`, never throws | Verified | `CategoryTaxonomy.normalize` L59-68 (null→check, blank→check, `getOrDefault(...,OTHER)`); `CategoryTaxonomyTest` fallback cases; **100% branch coverage** re-confirmed |
| **U1-FR5** Apply `normalize` at `applyTags` (create+update) and `TaggingService.map` | Verified | `ItemMapper.java:39` `setCategory(CategoryTaxonomy.normalize(...))`; `TaggingService.java:88,101-111` `normalizedCategory`; `ItemMapperTest`/`WardrobeServiceTest`/`TaggingServiceTest` green |
| **U1-FR6** No batch migration; `category` stays plain DynamoDB String | Verified | `git diff 756f1f3..HEAD -- Item.java` is empty (re-run). No table change |
| **U2-FR1** Define taxonomy once on frontend; derive form options | Verified | `categoryTaxonomy.ts` `CATEGORIES` L11-20; `TagForm.tsx:149` maps `CATEGORIES`; `categoryTaxonomy.test.ts` 33 tests green, 100% coverage. Commit `515f66d` |
| **U2-FR2** Replace category `<input>` with `<select>` + `—` placeholder | Verified | `TagForm.tsx:141-154` `<select>` with `<option value="">—</option>`; `TagForm.test.tsx` asserts option list `['', ...CATEGORIES]` |
| **U2-FR3** Edit legacy value pre-selects normalized bucket | Verified | `TagForm.tsx:49-50` `toDraftCategory` → `normalizeCategory`; `TagForm.test.tsx` `"chinos"`→`Bottom` test green |
| **U2-FR4** Relax client validation; `TagInput` warmth/formality `number\|null` | Verified | `tagValidation.ts:29,32` (null-guarded range check); `types/item.ts:46,48`; `tagValidation.test.ts` 15 tests, 100% coverage |
| **U2-FR5** Keep vision→suggestion→form→save incl "Save all" | Verified | `AddItem.test.tsx` (24) + `ItemDetail.test.tsx` (10) green after in-place conversion (A3.2) |
| **U3-FR1** Group grid into sections, one per taxonomy value, under header | Verified | `WardrobeGrid.tsx:84-102` one `<section>`/`<h2>` per group; `WardrobeGrid.test.tsx` Jewelry-header test green. Commit `2fc74b7` |
| **U3-FR2** Section by read-time `normalizeCategory` of stored value, no mutation | Verified | `wardrobeSections.ts:23-43` `groupByCategory` (Map/push, no writes); `wardrobeSections.test.ts` no-mutation test green |
| **U3-FR3** Fixed display order, `Other` last | Verified | `sectionOrder()` → `CATEGORIES` (`Other` last); `wardrobeSections.test.ts` + `WardrobeGrid.test.tsx` order assertions green |
| **U3-FR4** Hide empty sections | Verified | `wardrobeSections.ts:38` skips zero-item buckets; empty-omission tests green |
| **U3-FR5** Section headers from explicit singular→plural label map | Verified | `categoryTaxonomy.ts:99-108` `CATEGORY_LABELS` (`Dress`→"Dresses", `Accessory`→"Accessories"); `categoryTaxonomy.test.ts` label tests green |
| **U3-FR6** Preserve loading/empty/list-failure-retry + thumbnail/detail | Verified | `WardrobeGrid.tsx:44-77` all three branches intact; 5 pre-existing grid tests unmodified + green |
| **U4-FR1** Extend `slotForCategory` so every taxonomy value resolves | Verified | `specSheet.ts:19-45` adds `dress`/`bottom`/`jewelry`; `specSheet.test.ts` `it.each` over `CATEGORIES` green (49/49). Commit `c272b2e` |
| **U4-FR2** Keep case-insensitive lookup + `PIECE` fallback; no legacy regression | Verified | `specSheet.ts:52-55` trimmed/lower + `?? 'PIECE'`; `other` unkeyed → PIECE; pre-existing legacy/null/blank tests green |
| **U4-FR3** No new `Slot` value (Jewelry shares `CARRY`) | Verified | `specSheet.ts:7` `Slot` union byte-identical; `placement.ts` zero diff (re-confirmed) |

Remediation Task 5.0 (issue at the `TagForm`→`items.ts` boundary) is verified
separately below under Proof Artifacts; it restores U2-FR5's end-to-end behavior
for the create path.

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Strict TDD (backend domain) | Verified | RED→GREEN evidenced in task 3.0/4.0/5.0 proofs (documented failing runs before GREEN); commits ordered backend-foundation-first per spec interdependency |
| Coverage split (TESTING.md) | Verified | 100% branch on `normalize` (`CategoryTaxonomy` 6/6) and vision-tag mapping (`TaggingService` 26/26) re-confirmed via JaCoCo XML; frontend logic modules `categoryTaxonomy.ts`/`wardrobeSections.ts`/`tagValidation.ts`/`specSheet.ts`/`items.ts` all 100%; view plumbing (`WardrobeGrid.tsx` 93% branch, `TagForm.tsx` 84% branch) not over-tested, as allowed |
| Layered architecture / DTO boundary | Verified | `normalize` is pure domain logic in `com.ensemble.wardrobe.CategoryTaxonomy` (not a controller, A2.2); applied at `ItemMapper.applyTags` choke point; `TagRequest` DTO at boundary |
| No schema change / single-item DynamoDB | Verified | `Item.java` diff empty; `category` stays schemaless `String` |
| Claude usage (enum advisory, text-only stylist) | Verified | `tagTool()` enum is a hint; code-side `normalize` is the guarantee; no stylist/image-byte change (Non-Goal 1) |
| Conventional commits, ~1 per unit | Verified | `feat(backend):`, `feat(frontend):` ×2, `fix(frontend):` ×2, `docs(spec):` — one per demoable unit + remediation |
| Quality gates (lint/typecheck/tests) | Verified | Backend 286/0; frontend 319/0; ESLint 0; `tsc -b` 0 — all re-run this validation |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| Task 1.0 | `CategoryTaxonomyTest` + JaCoCo 100% branch | Verified | 15 tests green; JaCoCo XML `BRANCH missed=0 covered=6`, `LINE missed=0 covered=23` (re-run) |
| Task 1.0 | `WardrobeControllerTest` jewelry-null → 201 | Verified | 22/22 green incl. new case; captured `TagRequest.formality()`/`warmth()` null |
| Task 1.0 | `AnthropicVisionModelClientTest` enum assertion | Verified | 4/4 green |
| Task 1.0 | `TaggingServiceTest` `"sweatshirt"`→`Top` | Verified | 21/21 green; `TaggingService` 100% branch |
| Task 1.0 | `ItemMapperTest`/`WardrobeServiceTest` save-path | Verified | 4 + 16 green |
| Task 1.0 | CLI `git diff --stat Item.java` empty | Verified | Empty diff re-confirmed |
| Task 2.0 | `categoryTaxonomy.test.ts` | Verified | 33 tests green (label + order additions from 3.0 folded in); 100% coverage |
| Task 2.0 | `TagForm.test.tsx` select / jewelry-null / legacy-preselect | Verified | 9/9 green |
| Task 2.0 | `tagValidation.test.ts` | Verified | 15/15 green; 100% coverage |
| Task 2.0 | Screenshot: dropdown open | **Deferred** | Headless run (A3.3); behavior covered by `TagForm.test.tsx` option-list assertion. Manual capture outstanding — MEDIUM note below |
| Task 3.0 | `wardrobeSections.test.ts` grouping/ordering | Verified | 7/7 green; 100% coverage |
| Task 3.0 | `WardrobeGrid.test.tsx` sections/Jewelry/edge-states | Verified | 8/8 green (RED→GREEN documented) |
| Task 3.0 | Screenshot: grouped `/wardrobe` | **Deferred** | Headless run (A3.6); behavior covered by `WardrobeGrid.test.tsx` header/order assertions. Manual capture outstanding — MEDIUM note below |
| Task 4.0 | `specSheet.test.ts` taxonomy→slot | Verified | 49/49 green (RED on `Bottom`/`Dress`/`Jewelry` documented); 100% coverage |
| Task 4.0 | CLI `placement.ts` zero-diff; `Slot` unchanged | Verified | Confirmed |
| Task 5.0 | `items.test.ts` null formality/warmth omitted | Verified | 22/22 green (RED→GREEN documented); `items.ts` 100% coverage; `appendOptionalNumber` at `items.ts:51-54,65,67` |
| Task 5.0 | Backend accepts fixed shape (existing controller test) | Verified | `WardrobeControllerTest` jewelry case exercises the omitted-params shape |

## 3) Validation Issues

No CRITICAL or HIGH issues remain. GATE A is clear because the sole HIGH finding
(the backup reviewer's Task 2.0 FAIL) was remediated and re-verified.

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| MEDIUM (resolved) | Backup reviewer `18-reviews/18-task-02-review.md` FAILED Task 2.0: `frontend/src/api/items.ts` `tagFormData` sent `String(null)` = `"null"` for a Jewelry item's null `formality`/`warmth`, likely a real-endpoint 400 despite green tests (mock boundary in `AddItem.test.tsx` hid it). | Broke U2-FR5 end-to-end on the create path. | **Done.** Task 5.0 (commit `9050b4b`) added `appendOptionalNumber` mirroring `appendOptional`; regression test in `items.test.ts` re-run green; `updateTags` audited (JSON path unaffected, A5.2); backend already accepts the shape (A5.3). Verified this validation. |
| MEDIUM | Two screenshot proof artifacts (Unit 2 dropdown; Unit 3 grouped `/wardrobe`) are DEFERRED, not captured — headless run (A3.3/A3.6), matching prior repo precedent (spec 21). | Visual/UX artifacts not machine-verified; functional behavior is. | Non-blocking for PASS. Capture both on-device (`npm run dev` + `./gradlew bootRun`) before the final demo/merge sign-off so Success Metric 1's screenshot half is closed. |
| LOW | `18-proofs/18-task-01-proofs.md` prose is internally inconsistent on the full-suite count: the "Full suite green" section header says "287 tests" while its own result line and the summary say 286 (actual: 286). Already flagged by the Task 1.0 reviewer. | Cosmetic proof-doc typo; no functional effect. | Fix the "287" to "286" on a future touch of that file. |
| LOW (out of scope) | `npm test` emits `act(...)` warnings from `WardrobeDrawer` tests. `WardrobeDrawer.tsx` is a spec Non-Goal (#5) and was not touched by this branch; the warning is pre-existing. | None on this spec. | No action for #18; address separately if desired. |

## 4) Evidence Appendix

### Git commits analyzed (branch `wardrobe-grouping`, base `main`@756f1f3)

- `bd2b2c3` docs(spec): spec, task list, planning audit — 4 files (docs only)
- `47de36a` feat(backend): category taxonomy + normalize on every save path — 13 files (Unit 1)
- `515f66d` feat(frontend): taxonomy select + relaxed tag validation — 12 files (Unit 2)
- `2fc74b7` feat(frontend): group wardrobe grid into category sections — 10 files (Unit 3)
- `c272b2e` fix(frontend): map new taxonomy values to outfit-card slots — 4 files (Unit 4)
- `9050b4b` fix(frontend): omit null formality/warmth from item API requests — 5 files (Task 5.0 remediation)

38 files changed vs `main`, +3581/-102. All within `docs/specs/18-*`,
`frontend/src/`, `src/main/java/`, `src/test/java/` (verified: no changes
elsewhere). Working tree clean except the untracked, advisory `18-reviews/` dir.

### File integrity (GATE D)

- **Core files** (production source): all 14 map to a Relevant-Files row + a
  task/FR (see Coverage Matrix). No unmapped out-of-scope core change → **D1 pass**.
- **Supporting files** outside the Relevant-Files table: `AddItem.test.tsx`,
  `ItemDetail.test.tsx` — changed to convert free-text `type()` to
  `selectOptions()` after the `<input>`→`<select>` change; linkage documented in
  assumption **A3.2** and Task 2.0 proof → **D2 pass**, no D3 gap.

### Commands executed (this validation)

```
./gradlew test jacocoTestReport -PskipFrontend --rerun-tasks   # BUILD SUCCESSFUL
  backend: 286 tests / 0 failures / 0 errors (parsed from build/test-results)
  JaCoCo CategoryTaxonomy: BRANCH 6/6 (100%), LINE 23/23 (100%)
  JaCoCo TaggingService:   BRANCH 26/26 (100%)
  JaCoCo ItemMapper:       INSTRUCTION 65/65, no branches
git diff 756f1f3..HEAD -- src/main/java/com/ensemble/wardrobe/Item.java   # empty

cd frontend && npm test -- --run          # 29 files / 319 tests passed
cd frontend && npm run lint               # exit 0, 0 problems
cd frontend && npx tsc -b                 # exit 0, no output
cd frontend && npx vitest run --coverage  # categoryTaxonomy/wardrobeSections/
   tagValidation/specSheet/items = 100%; WardrobeGrid 93% br, TagForm 84% br (view)
```

### Security (GATE F)

Grepped all `18-*` spec artifacts (spec, tasks, proofs, reviews, assumptions,
audit) for `sk-ant-`/`api_key`/`passcode`/`secret`/`token`/`password`/`bearer`/
`AKIA`. Every hit is either a CSS design-token reference, the "secret scan"
process description, or an explicit "no secrets/tokens present" statement. **No
real credentials in any proof artifact.**

---

**Validation Completed:** 2026-07-22
**Validation Performed By:** Claude Opus 4.8 (SDD Phase 4, delegated worker)
