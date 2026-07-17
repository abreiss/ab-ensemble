# 07-audit-pwa-security-guards.md

Planning audit for **Spec 07 — PWA install + security guards**
(`07-spec-pwa-security-guards.md` → `07-tasks-pwa-security-guards.md`). Audit run 1.

## Executive Summary

- **Overall Status: PASS**
- Required Gate Failures: **0**
- Flagged Risks: **2** (both non-blocking)

All REQUIRED gates pass on the first run; the task plan is cleared for the implementation phase. Two FLAG findings are recorded for visibility — neither blocks handoff.

## Gateboard

| Gate | Status | Note | Reference |
| --- | --- | --- | --- |
| Requirement-to-test traceability | PASS | Every functional requirement maps to ≥1 planned test/proof artifact (matrix below) | `## Tasks` 1.0–5.0 |
| Proof artifact verifiability | PASS | All artifacts observable + reproducible (exact `gradlew`/`npm`/`curl`/`grep` commands, sanitized) | Proof Artifact blocks |
| Repository standards consistency | PASS | 8 guideline sources read (incl. `AGENTS.md` + `README.md`); no conflicts | Standards Evidence Table |
| Open question resolution | PASS | Spec's 3 open questions are non-blocking with explicit defaults (limit 100, TTL 12h, icon art) | Spec §Open Questions |
| Regression-risk blind spots | FLAG | Counter-integrity on non-Claude paths + frontend fetch refactor (see Findings) | 4.5, 3.2/5.3 |
| Non-goal leakage | PASS | No task exceeds goals/non-goals (no multi-user auth, per-IP limiting, deploy/Terraform) | Spec §Non-Goals |

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` | yes | Strict TDD for backend domain; layered architecture (controller/filter → service → repo); DTOs at boundary, no client/DynamoDB leaks | none |
| `CLAUDE.md` | yes | Imports AGENTS.md; AWS SDK v2 DynamoDB single-item; keys via env/Secrets Manager, never committed | none |
| `docs/TESTING.md` | yes | ≥90% line + 100% branch on critical logic; frontend meaningful-logic only; mock Claude; TestContainers for DynamoDB | none |
| `docs/ARCHITECTURE.md` | yes | Stateless server; single-table DynamoDB; server-side passcode gate; daily cap → 429; vite-plugin-pwa | none |
| `README.md` | yes | `/api` CRUD + tag flows; local-run + env-var config conventions | none |
| `.pre-commit-config.yaml` | yes | Fast gates: backend/frontend tests + eslint + secret scan (must not commit passcode) | none |
| `build.gradle` / `frontend/package.json` | yes | JUnit5/Mockito/TestContainers/JaCoCo; Vite 6/Vitest/RTL; build outputs to `static/` | none |
| `docs/PRECOMMIT.md` | yes | Lightweight local gates; JaCoCo + container build in CI | none |

## Requirement-to-Test Traceability Matrix

| Spec FR (abbrev.) | Task | Planned test / proof artifact |
| --- | --- | --- |
| U1: vite-plugin-pwa emits manifest + SW to `static/` | 1.3, 1.6 | Build-output listing |
| U1: manifest minimum fields | 1.3, 1.6 | `manifest.webmanifest` cat/JSON check |
| U1: apple-touch icon + iOS meta | 1.4, 1.6 | Generated `index.html` file check |
| U1: SW registered; `/api` never cached | 1.5, 1.6 | `sw.js`/denylist config check |
| U1: real-iPhone standalone install | 1.7 | Home-screen + standalone screenshot |
| U2: `POST /api/auth` 200/401, constant-time | 2.3 | `AuthControllerTest` |
| U2: stateless signed token, TTL, reject tamper/expiry/malformed | 2.2 | `SessionTokenServiceTest` (100% branch) |
| U2: gate `/api/**` except auth+health; blocked never reaches controller | 2.4 | `SessionAuthFilterTest` |
| U2: token via header **or** `?token=` query | 2.4 | filter header/query tests |
| U2: static assets not gated | 2.4 | filter static-path test |
| U2: passcode never in client bundle | 5.2 | built-`static/` grep proof |
| U2: frontend passcode screen, `sessionStorage`, 401→gate | 3.1, 3.2, 3.4 | `auth`/`http`/`AuthGate` tests |
| U3: global atomic `ADD` counter keyed `usage#<UTC-date>` | 4.3 | `UsageRepositoryIT` |
| U3: both endpoints increment+check before Claude; 429; configurable | 4.4, 4.5 | `CallCapServiceTest` + controller 429 tests |
| U3: UTC-day reset via `Clock` | 4.4 | `newUtcDay_resetsCount` |
| U3: `findAll()` excludes `usage#` rows | 4.2 | `findAll_excludesUsageRows` (100% branch) |
| U3: non-Claude paths don't corrupt counter | 4.5 | controller tests (see FLAG-1) |

## FLAG Findings (non-blocking)

1. **Counter-integrity on non-Claude paths is asserted only implicitly.**
   - Risk: Spec FR "requests that do not reach the Claude call (auth `401`, cap `429`, bad input `400`) shall not corrupt the counter." The plan enforces this by calling `reserve()` only inside the two Claude controllers after the gate, but no sub-task adds an *explicit* test that a `401`/`400` leaves the counter untouched.
   - Suggested remediation: add one assertion to task 4.5 (or 2.4) — e.g. a bad-input `400` / auth `401` request does not invoke `CallCapService.reserve()` (verify-no-interactions) — to lock the FR.

2. **Frontend authed-fetch refactor touches specs 04/05 API tests.**
   - Risk: routing `items.ts`/`style.ts` through the new authed fetch (task 3.2) can regress the existing `api/items.test.ts` / `api/style.test.ts` and route tests from specs 04/05. Task 5.3 re-runs the suites, but no sub-task explicitly updates those pre-existing tests for the injected header.
   - Suggested remediation: note in task 3.2 that the specs 04/05 api/route tests must be updated to expect the `X-Ensemble-Session` header (already covered by the "update their tests first" instruction; surfaced here for tracking).

## Chain-of-Verification

- Do all REQUIRED gates pass with explicit evidence? **Yes** — traceability matrix, proof-artifact commands, standards table, and the spec's non-blocking open questions each carry evidence.
- Fact-check: each matrix row was verified against the spec FR text and the corresponding task/proof artifact in `07-tasks-pwa-security-guards.md`. No unsupported claims.
- Inconsistencies resolved: the one partially-implicit FR (counter integrity on non-Claude paths) is recorded as FLAG-1 rather than asserted as fully covered.
- Final synthesis: **PASS — cleared for implementation.** The two FLAGs are optional hardening; fold them in now or during implementation.
