# 07-validation-repick-wear-history.md

Validation of the issue-#7 implementation (re-pick loop + wear-history) against
`07-spec-repick-wear-history.md` and the task-level Proof Artifacts.

## 1) Executive Summary

- **Overall:** **PASS** (no gates tripped; one MEDIUM evidence-quality issue found and
  **resolved during validation** — see §3).
- **Implementation Ready:** **Yes** — all three Demoable Units are covered by passing
  automated tests, the two critical rules hold **100% branch** coverage, and the loop was
  independently verified end-to-end (live `markWorn` + two-curl re-pick + mobile screenshots)
  against the correct #7 build.
- **Key metrics:**
  - Functional Requirements verified: **16 / 16 (100%)**, 0 Unknown.
  - Proof Artifacts working: **all** automated + live artifacts reproduced this session.
  - Files changed vs expected: **35 changed, all mapped** to the task "Relevant Files"
    (23 core src/test, 12 spec/proof/docs) — no out-of-scope core changes.
  - Backend coverage: `markWorn` **BRANCH 0-missed/2**; grounding guardrail
    (`style`/`pickWithOneRetry`/`renderWardrobe`/`invalidIds`) **BRANCH 0-missed** → 100%.
  - Frontend: **93 tests / 11 files pass**, `eslint` clean.

## 2) Coverage Matrix

### Functional Requirements

| Requirement | Status | Evidence |
| --- | --- | --- |
| **U1** `markWorn` reads item, sets `wornCount=prior+1` (null→0), `lastWorn=now`, persists, returns `ItemResponse` | Verified | `WardrobeServiceTest` (4 cases) pass; live `:8081` curl `0→1→2`; commit `f317283` |
| **U1** wear values computed in app code, never the model | Verified | `WardrobeService.markWorn` is a pure read-modify-write (no model call); `src/main/java/com/ensemble/wardrobe/WardrobeService.java` |
| **U1** unknown id → `ItemNotFoundException` → 404 | Verified | `markWorn_unknownId_throwsNotFound`; live curl unknown id → `{"error":"not_found"}` HTTP 404 |
| **U1** `POST /api/items/{id}/worn` → 200 + updated DTO | Verified | `WardrobeControllerTest.postWorn_valid_returns200WithUpdatedItem` / `postWorn_unknownId_returns404`; live curl HTTP 200 |
| **U1** other fields (tags/photo/`createdAt`) unchanged | Verified | `WardrobeRepositoryIT` round-trip asserts tags/`createdAt` untouched |
| **U2** `POST /api/style` accepts optional `history`; no-history == single-turn | Verified | `StyleControllerTest.postStyle_withHistory_*`; `style_emptyHistory_matchesSingleTurn`; commit `8e877f0` |
| **U2** seed conversation with history + prompt, run **unchanged** guardrail (validate/retry-once/subset/error-on-zero) | Verified | `style_repick_staysGroundedWithHallucinatedIdRetriedOnce`, `style_repick_rendersValidSubsetAfterRetry`; JaCoCo 100% branch on guardrail |
| **U2** instruct model to produce a **different** look from the previous when history present | Verified | `AnthropicStylistModelClientTest` (different-look instruction on prior-assistant turn); live two-curl → 3 different ids; look-2 note "a fresh, unrepeated outfit" |
| **U2** server **stateless**; client resends full thread | Verified | No server-side turn storage; `StylistService.style(vibe, history)` seeds per call; `style_repeatedPushback_eachPickGrounded` |
| **U2** text turns only (no image bytes) on every re-pick | Verified | `style_repick_sendsNoImageBytes`; `AnthropicStylistModelClientTest` text-only MessageParams |
| **U2** repeated pushback each grounded; empty-wardrobe friendly 200 on re-pick | Verified | `style_repeatedPushback_eachPickGrounded`, `style_repick_emptyWardrobe_returnsFriendly` |
| **U3** item detail displays `wornCount` (never-worn) + relative `lastWorn` (not-yet-worn), display-only | Verified | `ItemDetail.test.tsx` (3 cases) + `relativeTime.test.ts` (6); commit `2d00bb0` |
| **U3** "I wore this look" marks every piece worn, locks to "Logged ✓" one-shot; failed write → soft retry, keeps look | Verified | `Stylist.test.tsx` "logs a worn look…" / "keeps the look…"; `api/items.test.ts` `markWorn` |
| **U3** after a look, pushback field + "Show me another"; each POSTs accumulated `history` and renders the new look | Verified | `Stylist.test.tsx` re-pick suite (reveal/pushback/regenerate/thread-accumulates); screenshots look1→look2; commit `ab1373a` |
| **U3** preserve loading / error-with-retry / empty states across re-picks; Care-Label design | Verified | `Stylist.test.tsx` disabled-while-loading + error-retry + empty-on-repick; `index.css` `.repick`/`.btn-ghost` |
| **U3** client builds/forwards `history`; wardrobe client exposes `markWorn(id)` | Verified | `api/style.test.ts` (history in body + backward compat); `api/items.ts` `markWorn` |

### Repository Standards

| Standard Area | Status | Evidence & Notes |
| --- | --- | --- |
| Strict TDD (backend domain) | Verified | Task file shows RED→GREEN→REFACTOR per sub-task; per-unit commits; 100% branch on the two critical rules (JaCoCo) |
| Coverage split (frontend meaningful-logic only) | Verified | Frontend tests cover client contracts + route logic, not view plumbing (`docs/TESTING.md`) |
| Layered arch + DTO boundary | Verified | Controllers return `ItemResponse`/`StyleResponse`; no `Item`/Claude client/storage leak |
| Mock Claude in tests; DynamoDB Local for round-trip | Verified | `AnthropicStylistModelClientTest`/`StylistServiceTest` mock the seam; `WardrobeRepositoryIT` uses DynamoDB Local |
| Conventional commits; pre-commit gates | Verified | 4 `feat:` commits w/ task refs; pre-commit (tests+lint+secret scan) passed on `ab1373a` |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| T1.0 | `WardrobeServiceTest`/`WardrobeControllerTest`/`WardrobeRepositoryIT` | Verified | `./gradlew test` BUILD SUCCESSFUL |
| T1.0 | JaCoCo 100% branch on `markWorn` | Verified | `markWorn` BRANCH missed 0 / covered 2 |
| T1.0 | CLI `POST /worn` | Verified | live `:8081`: `wornCount 0→1→2`, unknown id sanitized 404 |
| T2.0 | `StylistServiceTest`/`StyleControllerTest`/`AnthropicStylistModelClientTest` | Verified | pass in full suite; guardrail 100% branch |
| T2.0 | CLI two-curl re-pick returns different ids | Verified | live `:8081`: pick1 vs pick2 = **3 different, all-owned** ids |
| T3.0 | `ItemDetail`/`relativeTime`/`items`/`Stylist` tests | Verified | in 93/11 frontend pass |
| T3.0 | Item-detail + "Logged ✓" screenshots | Verified | `assets/item-detail-wear.png`, `assets/stylist-logged-card.png` present, inline |
| T4.0 | `api/style.test.ts` + `Stylist.test.tsx` re-pick suite | Verified | in 93/11 frontend pass |
| T4.0 | Two-different-looks screenshots | Verified | `assets/stylist-repick-look1.png` / `-look2.png` re-captured against #7 (see §3) |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation / Resolution |
| --- | --- | --- | --- |
| MEDIUM (RESOLVED) | The T4.0 two-look screenshots were originally captured with the Vite dev proxy pointed at its default `:8080`, which was serving a **stale issue-#6 worktree** backend (`.worktrees/06-stylist/`) that lacks `/worn` and ignores `history`. The screenshots showed the correct **frontend** loop, but the "different look" was produced by #6's stateless re-style, not the #7 nudge — so the end-to-end backend claim was not actually exercised. | Evidence quality: proof overstated the end-to-end path. | **Resolved during validation:** stood up the #7 build on `:8081`, verified `markWorn` (`0→1→2`, sanitized 404) and the two-curl re-pick (**3 different, all-owned ids**), re-pointed the proxy at `:8081`, and re-captured both screenshots against #7. `07-task-04-proofs.md` result summaries updated to match; proxy config reverted. |
| LOW (env note) | The long-running dev backend on `:8080` is the **#6 worktree build**, not the #7 branch. | Manual testing against `:8080` would silently exercise old behavior. | Point local manual testing at a fresh `./gradlew bootRun` of the #7 branch (or restart the `:8080` server from this branch) before demoing. Not a code defect. |
| LOW (transient) | The live `:8081` stylist intermittently returned `503 stylist_unavailable` (upstream Claude overload) on first call. | None to correctness — the app degrades to the graceful error state (as specified) and succeeds on retry. | No action; the error-with-retry state is a tested requirement (U3). |

No CRITICAL/HIGH issues. No `Unknown` coverage entries. No secrets in any spec-07 artifact (scanned `sk-ant-…` / `AKIA…` → clean).

## 4) Evidence Appendix

**Commits analyzed (branch `feat/07-repick-wear-history`):**

```
ab1373a feat: pushback + 'Show me another' re-pick loop (frontend)     [T4.0]
2d00bb0 feat: wear-history display + 'I wore this look' (frontend)      [T3.0]
8e877f0 feat: stateless multi-turn re-pick / pushback (backend)        [T2.0]
f317283 feat: deterministic 'I wore this' wear-history write           [T1.0]
990837a docs: SDD spec, tasks, audit for issue #7                      [planning]
```

**Backend tests + coverage (`./gradlew test jacocoTestReport -PskipFrontend`):**

```
BUILD SUCCESSFUL
WardrobeService.markWorn        BRANCH=(missed 0, covered 2)  LINE=(0,5)
StylistService.style            BRANCH=(0, 6)   LINE=(0,16)
StylistService.pickWithOneRetry BRANCH=(0, 2)   LINE=(0,10)
StylistService.renderWardrobe   BRANCH=(0, 2)   LINE=(0,15)
StylistService.invalidIds λ     BRANCH=(0, 2)
```

**Frontend (`npm test -- --run`, `npm run lint`):**

```
Test Files  11 passed (11)
     Tests  93 passed (93)
eslint: clean (exit 0)
```

**Live end-to-end against #7 backend (`:8081`, DynamoDB Local, live Sonnet 5):**

```
# Unit 1 — markWorn
POST /api/items/<id>/worn  → wornCount 0 → 1 (lastWorn set) → 2      HTTP 200
POST /api/items/does-not-exist/worn → {"error":"not_found"}          HTTP 404

# Unit 2 — stateless re-pick
POST /api/style {prompt}                       → ids A = [ffbcdb71, 9d12e3e8, 72cd4a7b]
POST /api/style {prompt, history:[…prev…]}     → ids B = [14fbffbd, 392d1ed5, 9bfc4421]
identical set? False   |  B ⊆ owned (grounded)? True
```

**Screenshots (390px, re-captured against #7):**
`assets/stylist-repick-look1.png` (light-blue button-up + wide-leg jeans + retro sneakers)
and `assets/stylist-repick-look2.png` (black denim button-up + light jeans + oxfords; note:
"A completely different smart-casual combo… a fresh, unrepeated outfit").

## How to Continue the SDD Workflow

Likely next phase action: this feature's SDD workflow is complete; the next SDD action would be starting Phase 1 for a new feature.

To continue the workflow in this chat, reply with:

`Start SDD for a new feature.`

You can also continue in a new chat if you want to keep context lean; the SDD skill will reassess repository state from the persisted spec/task/audit/proof/validation artifacts.

Before merging, do a final code review of the completed implementation and this validation report.

**Validation Completed:** 2026-07-16
**Validation Performed By:** Claude Opus 4.8 (1M context)
