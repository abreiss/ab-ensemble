# 21-validation-manual-outfit-assembly.md

Validation report for **issue #20 — Manual outfit-assembly page (`/assemble`)**,
against `21-spec-manual-outfit-assembly.md`, `21-tasks-manual-outfit-assembly.md`,
and the four proof files under `21-proofs/`. Phase-4 (SDD validate) — final quality
gate. Branch `feature/21-manual-outfit-assembly`.

## 1) Executive Summary

- **Overall: PASS** — no gates tripped (GATE A–F all pass).
- **Implementation Ready: Yes.** All 21 functional requirements across the four
  demoable units are implemented and backed by machine-verifiable evidence
  (re-run test suite, lint, typecheck, coverage, and direct code inspection); the
  only outstanding items are the intentionally-deferred screenshot / on-device
  touch artifacts, disclosed in every proof file and assumption A1.5, and none of
  them is the *sole* evidence for any requirement.
- **Key metrics:**
  - Requirements Verified: **21 / 21 (100%)** — 0 Failed, 0 Unknown.
  - Proof artifacts working: **all automated artifacts reproduced green**;
    6 screenshots + 1 on-device recording explicitly deferred (documented).
  - Files changed vs. expected: **28 changed, all mapped** to the task list's
    "Relevant Files" or disclosed as supporting files. **0 backend Java/Gradle
    changes** (verified independently).

### Independently re-run evidence

| Check | Command | Result |
| --- | --- | --- |
| Full frontend suite | `cd frontend && npm test -- --run` | **259 passed / 259**, 27 files (matches task-04 proof) |
| Lint | `npm run lint` | Clean, exit 0 |
| Typecheck / build | `npx tsc -b` | Clean, exit 0 |
| Placement branch coverage | `npx vitest run src/lib/placement.test.ts --coverage … --coverage.thresholds.branches=100 …` | **100 / 100 / 100 / 100** (stmts/branch/funcs/lines), thresholds enforced |
| Dependency added | `grep -n "@dnd-kit/core" frontend/package.json` | `"@dnd-kit/core": "^6.3.1"` |
| No backend changes | `git diff --name-only main...HEAD \| grep -E '\.java\|\.gradle\|src/main/java'` | No matches |

## 2) Coverage Matrix

### Functional Requirements

| Requirement (unit) | Status | Evidence (file:line, test, commit) |
| --- | --- | --- |
| FR-1 Route `/assemble` inside `AuthGate`, sibling of `/` (U1) | Verified | `App.tsx:43` inside `<AuthGate>` (`:21`); gated + mount tests in `App.test.tsx` pass; commit `45356a3` |
| FR-2 Static SVG mannequin, 4 labeled `Slot`-keyed zones (U1) | Verified | `Mannequin.tsx` — SVG line-art + `MannequinZone` with `data-slot` TOP/BOTTOM/SHOES/CARRY; `Assemble.test.tsx` four-zone-labels test passes |
| FR-3 Entry-point links from `/` and `/wardrobe` (U1) | Verified | `Stylist.tsx:166` + `WardrobeGrid.tsx:77` `Link to="/assemble"`; link tests in both `*.test.tsx` assert `href="/assemble"` |
| FR-4 Empty-wardrobe state → `/add` (U1) | Verified | `Assemble.tsx:209-221` reuses `state-block empty-state`/`empty-title`/`btn btn-primary`; empty-state test passes |
| FR-5 Reuse loading + list-failure handling (U1) | Verified | `Assemble.tsx:103-119` `settle()` + retry; list-failure test passes (non-crashing error note) |
| FR-6 Add `@dnd-kit/core` (U2) | Verified | `package.json:14` `^6.3.1`; installed in `node_modules`; imported by `Assemble.tsx`/`dndConfig.ts` |
| FR-7 Placement as pure, unit-tested module (U2) | Verified | `placement.ts` — React/DOM-free; 18 tests; **100% branch** re-run and enforced |
| FR-8 Default zone via `slotForCategory`, override on different zone (U2) | Verified | `placement.ts:68` `targetSlot ?? slotForCategory(category)`; `slotForCategory` reused unchanged (`specSheet.ts` not modified on branch); default+override tests |
| FR-9 Single-occupancy replace TOP/BOTTOM/SHOES; replaced returns to source (U2) | Verified | `placement.ts:32,70-72` `SINGLE_OCCUPANCY_SLOTS`; `Assemble.tsx:228` excludes `placedIds` so displaced item reappears; replace tests + wiring test |
| FR-10 Multi-occupancy CARRY/PIECE tray (U2) | Verified | `placement.ts:73` accumulate branch; tray-accumulation unit + wiring tests |
| FR-11 Pointer + touch sensors; respects `prefers-reduced-motion` (U2) | Verified | `dndConfig.ts` `PointerSensor`+`TouchSensor` w/ activation constraints (4 tests); `index.css:1408` global `*` reduced-motion rule neutralizes drag transitions. On-device touch *feel* deferred (A1.5) — config itself machine-verified |
| FR-12 Session-only in-memory state, no backend call (U2) | Verified | `Assemble.tsx:100` `useState`; `placement.ts` imports no API; no new endpoint |
| FR-13 Remove "×" ≥44px, tap-remove no drag (U3) | Verified | `Assemble.tsx:64-76` `PlacedTile` button `minWidth/minHeight:44`, `stopPropagation` on pointerdown; tap-remove test asserts removal + ≥44px contract |
| FR-14 Drag-back-to-source removal (U3) | Verified | `Assemble.tsx:156-159` `over.id === SOURCE_DROPPABLE_ID` → `removeItem`; drag-back wiring test |
| FR-15 Source reuses drawer markup + search (U3) | Verified | `AssembleSource.tsx` reuses `drawer-body/title/search*/grid/tile` + `searchText`; `WardrobeDrawer.tsx` untouched (A1.3); markup/search test |
| FR-16 Exclude already-placed items (U3) | Verified | `AssembleSource.tsx:80-81` filters `placedIds`; exclusion test |
| FR-17 Removed/re-added keep rules consistent (U3) | Verified | `placement.ts:43-49` `withoutItem` across all slots; removal cases at 100% branch |
| FR-18 Wear-today fans out to `markWorn` once per placed id (U4) | Verified | `Assemble.tsx:182` `Promise.allSettled(currentlyPlacedIds.map(markWorn))`; fan-out-count test (exactly N calls) |
| FR-19 Lifecycle affordances logging/logged/error (U4) | Verified | `Assemble.tsx:15,181-184,231-251` `LogStatus` + `btn btn-logged`/`banner banner-error`; success-lock + error-banner tests |
| FR-20 Disable when nothing placed (U4) | Verified | `Assemble.tsx:178-180,241` guard + `disabled`; disabled-when-empty test |
| FR-21 Deterministic, server-owned write (U4) | Verified | Screen only calls `markWorn`; no client-side `wornCount`/`lastWorn` computation anywhere in `Assemble.tsx` |

No `Unknown` entries — **GATE B satisfied**.

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Coding standards / architecture | Verified | New screen under `routes/`, shared logic under `lib/`, reusable UI under `components/` (spec Repo Standards). Reuse over reinvention: `slotForCategory` unchanged, `markWorn`/`photoUrl`/`listItems` reused, drawer CSS classes reused, no divergent tile fork (A1.3) |
| Testing patterns / coverage split | Verified | `docs/TESTING.md` split honored: placement rules are a pure, **100%-branch** module; view plumbing tested for meaningful wiring (synthetic `onDragEnd`, tap-remove, fan-out) without over-testing jsdom drag fidelity. RED→GREEN documented in tasks 3.4/4.1 proofs |
| Quality gates | Verified | Vitest (259/259), ESLint (clean), `tsc -b` (clean) all re-run green — matches pre-commit/CI gates |
| Workflow conventions | Verified | Conventional commits, ~one per demoable unit (`feat(frontend): …` ×4 + `docs(spec): …`); coherent progression |
| No backend changes | Verified | `git diff` main…HEAD shows 0 `.java`/`.gradle`/`src/main/java` files; no new API contract |
| Documentation | Verified | Spec, tasks, audit (PASS), assumptions log, 4 proof files, 1 task-1 review — all present and consistent |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| U1 (1.0) | Gated-route + empty/error + 4-zone + entry-link tests | Verified | Reproduced in full suite (259/259); `App`/`Assemble`/`Stylist`/`WardrobeGrid` cases pass |
| U1 (1.0) | 2 scaffold screenshots (populated + empty) | Deferred | Headless run — disclosed in `21-task-01-proofs.md`; visual behavior covered by passing tests |
| U2 (2.0) | `placement.test.ts` 100% branch + `dndConfig.test.ts` + wiring test + dnd-kit grep | Verified | Coverage re-run 100/100/100/100 (enforced); grep returns `^6.3.1`; suites green |
| U2 (2.0) | Interaction screen recording (doubles as on-device touch proof) | Deferred | Headless/no device — disclosed in `21-task-02-proofs.md` + A1.5; sensor config machine-verified as substitute |
| U3 (3.0) | `AssembleSource.test.tsx` (exclusion/search/draggable) + tap-remove + drag-back tests | Verified | Suites green; RED-then-GREEN documented (task 3.4) |
| U3 (3.0) | Placed-tile "×" + source-exclusion screenshot | Deferred | Headless — disclosed in `21-task-03-proofs.md` |
| U4 (4.0) | Fan-out-count + logged-lock + error-banner + disabled-empty tests; markWorn grep; no-Java-diff | Verified | 11/11 `Assemble.test.tsx`; grep shows reused `../api/items` import; no backend diff |
| U4 (4.0) | "Logged ✓" screenshot | Deferred | Headless — disclosed in `21-task-04-proofs.md` |

## 3) Validation Issues

No CRITICAL, HIGH, or MEDIUM issues. One LOW / informational item recorded for
traceability:

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| LOW (informational) | Visual proof artifacts deferred: 6 screenshots (U1 populated+empty, U3 placed-tile, U4 logged) and the U2 interaction recording that doubles as the iOS on-device touch-drag proof were not captured — this was a headless run with no browser/device. Disclosed in each proof file and assumption A1.5. | No functional requirement is left unverified: every FR (including pointer/touch sensor config, ≥44px targets, and `prefers-reduced-motion`) has independent machine-verifiable evidence. The only genuinely un-machine-verifiable acceptance is real on-device touch *feel/threshold tuning* (spec Success Metric 5 / Open Q4), already logged as a deferred manual step. | Before demo/close-out, a human should run `npm run dev`, open `/assemble` at a ~390px viewport, capture the deferred screenshots, and validate/tune the `dndConfig.ts` pointer+touch thresholds on a real touchscreen. Not a merge blocker per A1.5. |

Note (non-issue): `.gitignore` gained one line (`frontend/coverage/`) — a supporting
change with clear linkage, disclosed in `21-task-02-proofs.md` (first task to run the
Vitest coverage reporter). GATE D2 satisfied; not a defect.

## 4) Gate Results

| Gate | Result | Basis |
| --- | --- | --- |
| **A** — No CRITICAL/HIGH | **PASS** | None found |
| **B** — No `Unknown` FRs | **PASS** | 21/21 Verified via code + re-run tests |
| **C** — Proof artifacts accessible & functional | **PASS** | All automated artifacts reproduced green; deferrals disclosed, none is a sole evidence source |
| **D** — File integrity (tiered) | **PASS** | All 28 changed files map to Relevant Files or disclosed supporting files; 0 unmapped out-of-scope core changes; 0 backend changes |
| **E** — Repository standards | **PASS** | TDD split, conventional commits, reuse-over-reinvention, quality gates all met |
| **F** — No secrets in proofs | **PASS** | Scan of spec dir found only the literal phrase "secret scan" (pre-commit hook description); no real keys/tokens/passwords |

## 5) Evidence Appendix

### Commits analyzed (`git log main..HEAD`)

- `ea3850c` docs(spec): plan issue #20 (spec + tasks + audit)
- `45356a3` feat(frontend): /assemble route + mannequin scaffold — Unit 1
- `7476664` feat(frontend): drag-and-drop placement onto the mannequin — Unit 2
- `dc06a36` docs(spec): fleet assumptions log + task 1.0 backup review
- `d4fee46` feat(frontend): remove/undo affordance + wardrobe drag source — Unit 3
- `2b3cdfc` feat(frontend): wear-today fan-out on assembled look — Unit 4

Working tree clean; all work committed on `feature/21-manual-outfit-assembly`.

### Files changed (28) — classification

- **Core (production):** `frontend/src/routes/Assemble.tsx`, `.../Stylist.tsx`,
  `.../WardrobeGrid.tsx`, `frontend/src/App.tsx`,
  `frontend/src/components/{Mannequin,AssembleSource}.tsx`,
  `frontend/src/lib/{placement,dndConfig}.ts`, `frontend/src/index.css`,
  `frontend/package.json`, `frontend/package-lock.json` — all in Relevant Files.
- **Supporting (tests/docs/config):** the matching `*.test.tsx`/`*.test.ts`,
  the `docs/specs/21-*` artifacts, `.gitignore` (disclosed). All linked.
- **Backend:** none.

### Command outputs

- `npm test -- --run` → `Test Files 27 passed (27) / Tests 259 passed (259)`.
- `npm run lint` → exit 0, no output. `npx tsc -b` → exit 0, no output.
- `placement.ts` coverage → `100 | 100 | 100 | 100` with `--coverage.thresholds.*=100`
  enforced (run would fail non-zero on any gap).
- `git diff --name-only main...HEAD | grep -E '\.(java|gradle)…'` → no matches.
- Secret scan of `docs/specs/21-*` → no real credentials.

## How to Continue

This feature's SDD workflow is complete and passes validation. Before merging,
the operator should (a) capture the deferred screenshots and run the on-device
touch validation per assumption A1.5, and (b) do a final human code review of the
implementation + this report. The next SDD action is starting Phase 1 for a new
feature.

**Validation Completed:** 2026-07-21
**Validation Performed By:** Claude Opus 4.8 (1M context) — SDD Phase-4 validation worker
