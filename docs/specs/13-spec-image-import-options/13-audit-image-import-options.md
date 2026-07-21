# 13-audit-image-import-options.md

Planning audit for the **image-import-options** task list
([`13-tasks-image-import-options.md`](./13-tasks-image-import-options.md)) against
its spec ([`13-spec-image-import-options.md`](./13-spec-image-import-options.md)).

## Executive Summary

- Overall Status: **PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2**

## Gateboard

| Gate | Status | Notes | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every FR across Units 1–5 maps to ≥1 sub-task and ≥1 planned test artifact (see traceability note below). | `## Tasks 1.0–5.0` |
| Proof artifact verifiability | PASS | Each artifact is observable + reproducible (exact `npm test -- --run <path>` commands, pinned `proof/` screenshot paths) and sanitized (demo content, no secrets). | Proof Artifact blocks |
| Repository standards consistency | PASS | 6 guideline sources read (incl. `AGENTS.md` + root `README.md`); no conflicts; frontend-UI-wiring test policy from `docs/TESTING.md` applied. | Standards Evidence Table |
| Open question resolution | PASS | All 3 spec Open Questions carry explicit assumptions and are non-blocking (see below). | Spec §Open Questions |
| Regression-risk blind spots | FLAG | See FLAG-1 (real-device camera behaviour is manual-only). | Task 5.0 |
| Non-goal leakage | FLAG | See FLAG-2 (batch fan-out must stay a client-side loop; watch scope). | Task 2.0 / 3.0 |

## Standards Evidence Table (Required)

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` (+ `CLAUDE.md`) | yes | Frontend UI wiring exempt from strict-TDD 90%; test meaningful logic only; conventional commits ~1/unit; no backend changes here | none |
| `README.md` | yes | `/add` flow; `POST /api/items/tag` + `POST /api/items`; server-side `429` daily cap; `authedFetch` token flow | none |
| `docs/TESTING.md` | yes | Vitest + RTL on meaningful frontend logic; do not over-test `<video>`/render plumbing; AAA + behaviour names | none |
| `frontend/package.json` | yes | `npm run test -- --run` (Vitest 3); `npm run lint` (eslint); React 19 / Vite 6 / RR7 | none |
| `.pre-commit-config.yaml` | yes | Commit gates = frontend vitest + eslint; secret scans block Anthropic/AWS keys | none |
| `frontend/src/api/items.ts`, `http.ts`, `types/item.ts`, `components/TagForm.tsx`, `lib/tagValidation.ts` | yes | `ensureOk` throws generic status `Error`; `authedFetch` handles `401` only; `lib/` pure + co-located tests; `TagForm` seeds once + remounts via `key` | none — confirms spec's Technical Considerations |

## Traceability Note (FR → test artifact)

- **Unit 1:** state model / `enqueue` / N=1 → headline-flow test; per-item stale
  guard → stale-response test; degraded fallback → rejected/all-null test; URL
  revoke → per-item revoke test. (Tasks 1.1–1.6)
- **Unit 2:** `multiple`/`FileList` → multi-file enqueue test; independent save →
  mixed success/failure test; drained-queue → all-success nav test; remove +
  preserved `accept` → remove/cleanup test. (Tasks 2.1–2.6)
- **Unit 3:** throttle → `promisePool` test + AddItem "never >3" test; typed 429 →
  `items.test` `ApiError.status` test; stop+preserve+banner and non-429 isolation →
  AddItem cap test; save-after-429 → AddItem save test. (Tasks 3.1–3.7)
- **Unit 4:** paste event + Blob→File → `imageFile`/`clipboardImages` tests +
  AddItem paste test; multi-paste → clipboard-extractor "all images" test;
  capability gating → AddItem button-gating test. (Tasks 4.1–4.7)
- **Unit 5:** teardown → `CameraCapture` `stop()`-every-track test; permission/no-
  device fallback → getUserMedia-rejection test; multi-shot → AddItem Done→enqueue
  test; secure-context degrade → control-absent test. (Tasks 5.1–5.6)

## FLAG Findings

1. **Real-device camera behaviour is verified manually, not by unit tests.**
   - Risk: the mandated teardown/fallback tests run against mocked `getUserMedia`
     in jsdom; documented iOS-PWA quirks (permission persistence, orientation) are
     only caught by the on-device pass, which the spec's Open Question 3 already
     defers as a manual follow-up.
   - Suggested remediation: keep the mocked teardown/fallback tests as the automated
     gate (per `docs/TESTING.md`, do not over-test `<video>`), and record the
     on-device screenshot/observations as the Task 5.0 manual proof — no task change
     required.
2. **Batch/paste fan-out must remain a client-side loop (Non-Goal #1).**
   - Risk: the throttle + "Save all" work in Tasks 2.0/3.0 could tempt a `/batch`
     endpoint; the spec explicitly forbids new backend surface.
   - Suggested remediation: none needed — tasks already scope fan-out to looping the
     existing `POST /api/items/tag` + `POST /api/items`; flag is a watch-item for
     implementation review.

## Open Question Disposition

- Q1 (concurrency limit) → assumed **3**; encoded as the `promisePool` default
  (Task 3.2). Non-blocking, tunable.
- Q2 (compact tile vs always-expanded form) → assumed compact summary that expands
  to `TagForm` (Task 2.3). Presentation detail, non-blocking.
- Q3 (real installed-PWA iOS screenshots) → desktop/`localhost` secure-context
  capture accepted as substitute; on-device pass is a manual follow-up (see FLAG-1).
  Non-blocking.

## Chain-of-Verification

- Do all REQUIRED gates pass with explicit evidence? **Yes** — traceability,
  verifiability, standards, and open-question gates each cross-checked against the
  spec FRs, the task/proof blocks, and the read standards sources.
- Inconsistencies found: none unresolved. The two FLAGs are advisory and do not
  block the implementation phase.
- Final synthesis: **PASS — cleared for the implementation phase.** No remediation
  edits required unless the user chooses to act on a FLAG.
