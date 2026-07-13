# 01-validation-app-skeleton.md

Validation of `01-spec-app-skeleton` against its spec, task list, and proof artifacts. All evidence below was independently re-executed during this validation (test suites re-run, jar rebuilt and curled, Docker container re-run), not merely read from the proof docs.

## 1) Executive Summary

- **Overall:** **PASS** (no gates tripped)
- **Implementation Ready:** **Yes** — every Functional Requirement is demonstrated by an independently re-verified proof artifact, all tests pass, and one runnable jar/image serves both the API and the UI.
- **Key metrics:**
  - Requirements Verified: **8/8 (100%)**
  - Proof Artifacts Working: **5/5 proof docs, 3/3 screenshots (100%)**
  - Files Changed vs Expected: all changed files map to a task/Relevant-File or are linked supporting files (tests/proofs/config); **0 unmapped core changes**
  - Tests: **6 backend (0 fail) + 2 frontend (0 fail)**

Gate results: **A** (no CRITICAL/HIGH) PASS · **B** (no Unknown FRs) PASS · **C** (proofs accessible/functional) PASS · **D** (file integrity) PASS · **E** (repo standards) PASS · **F** (no secrets in proofs) PASS.

## 2) Coverage Matrix

### Functional Requirements

| Requirement | Status | Evidence (independently re-verified) |
| --- | --- | --- |
| U1-FR1: start Spring Boot locally via documented command | Verified | README `./gradlew bootRun`; re-run this session → `/api/health` = 200. Proof `01-task-05-proofs.md`, commit `478f7da`/`93f68f1` |
| U1-FR2: `GET /api/health` → 200 + small JSON `{"status":"ok"}` | Verified | `HealthControllerTest` (re-run, 1/1 pass); `curl localhost:8080/api/health` → `{"status":"ok"}`. `01-task-01-proofs.md`, commit `478f7da` |
| U2-FR1: React+Vite mobile-first shell | Verified | `frontend/index.html` viewport meta, `App.tsx`, `index.css` single-column; screenshot `02-task-frontend-health.png`. Commit `204e3a7` |
| U2-FR2: frontend calls `/api/health` on load, displays result | Verified | `App.tsx` (`useEffect`+`fetchHealth`); `App.test.tsx` (re-run, 2/2: ok + unreachable); screenshot shows "Backend status: ok". `01-task-02-proofs.md` |
| U2-FR3: dev proxy `/api` → backend during `vite dev` | Verified | `vite.config.ts` proxy; proof shows `curl :5173/api/health` = `{"status":"ok"}`. `01-task-02-proofs.md`, commit `204e3a7` |
| U3-FR1: serve built UI at `/` w/ SPA fallback; `/api/**` not shadowed | Verified | `SpaForwardingConfig`; `SpaForwardingTest` (re-run, 4/4); jar curl matrix re-run: `/`=200, `/some/client/route`=200, `/api/does-not-exist`=**404**. `01-task-03-proofs.md`, commit `55884b5` |
| U3-FR2: multi-stage Dockerfile (node build → Java runtime) → one image | Verified | `Dockerfile` 3 stages (`node:20-alpine`→`temurin:21-jdk`→`temurin:21-jre`); `ensemble:skeleton` image present (512MB). `01-task-04-proofs.md`, commit `77abc3e` |
| U3-FR3: image runs locally serving `/` and `/api/health` | Verified | Container re-run this session: `/api/health`=200 `{"status":"ok"}`, `/`=200 (`<title>Ensemble</title>`); screenshot `04-task-docker-container-8080.png`. `01-task-04-proofs.md` |

### Repository Standards

| Standard Area | Status | Evidence & Compliance Notes |
| --- | --- | --- |
| Strict TDD (backend domain) | Verified | RED→GREEN documented and reproduced for `HealthController` and `SpaForwardingConfig` (import-fail / config-missing RED captured in proofs). JaCoCo wired (`jacocoTestReport`). |
| Layered architecture | Verified | Presentation/config only (`health/HealthController`, `web/SpaForwardingConfig`); no business logic — correct for a skeleton (spec Non-Goal 2). |
| No Spring Data JPA / no DB | Verified | No JPA/DB dependencies added (`build.gradle` has only webmvc + test); persistence deferred to issue #3. |
| Frontend (React 19 + Vite, mobile-first, Vitest+RTL) | Verified | `package.json` react 19 / vite 6; `App.test.tsx` via Vitest+RTL; mobile-first viewport + single column. |
| Coverage split honored | Verified | Backend logic unit-tested; frontend meaningful logic tested; no over-testing of view plumbing / IaC. |
| Conventional commits, small per-unit | Verified | 5 commits, each `feat:`/`chore:` with `Related to T#.0 in Spec 01`. |
| No secrets committed | Verified | Secret scan of proofs + staged diffs clean; env files git-ignored; pre-commit Anthropic-key + private-key hooks pass. |

### Proof Artifacts

| Unit/Task | Proof Artifact | Status | Verification Result |
| --- | --- | --- | --- |
| T1.0 | `01-task-01-proofs.md` + `HealthControllerTest` | Verified | Test re-run 1/1; curl 200 `{"status":"ok"}` |
| T2.0 | `01-task-02-proofs.md` + `02-task-frontend-health.png` | Verified | 2/2 tests re-run; PNG valid (1280×577); proxy curl matches |
| T3.0 | `01-task-03-proofs.md` + `03-task-single-process-8080.png` | Verified | 4/4 tests re-run; jar curl matrix incl. 404 for unknown API |
| T4.0 | `01-task-04-proofs.md` + `04-task-docker-container-8080.png` | Verified | Image present; container re-run serves API+UI |
| T5.0 | `01-task-05-proofs.md` | Verified | `pre-commit run --all-files` (10 hooks) pass; README bootstrap → 200; ignore rules confirmed |

## 3) Validation Issues

| Severity | Issue | Impact | Recommendation |
| --- | --- | --- | --- |
| MEDIUM | The three UI screenshots (`02-`, `03-`, `04-…png`) are **byte-identical** (md5 `2209aa7…`). The built UI renders the same page in all three serving contexts (dev-proxy `:5173`, jar `:8080`, container `:8080`) and no address bar is captured, so the image alone cannot distinguish which serving path produced it. | Evidence quality / traceability — the *context* of each screenshot is not self-evident from the image. **Non-blocking:** each context is independently proven by the curl evidence in the same proof doc (and re-verified in this report). | Optional: capture with a visible URL/address bar, or add an on-page origin/build indicator, so each screenshot self-identifies its serving context. |
| LOW | `HealthController` returns `Map<String,String>` rather than a dedicated DTO. | None for this slice (trivial fixed body, no domain). AGENTS.md DTO-at-boundary rule matters once real payloads exist. | Introduce a `HealthResponse` record when the API surface grows; acceptable as-is for the skeleton. |

No CRITICAL or HIGH issues. No `Unknown` coverage entries. No unmapped out-of-scope core file changes.

Planning-audit FLAG (Gradle↔frontend coupling) was **remediated** in implementation: `-PskipFrontend` removes `:buildFrontend` from the `test` graph (verified via `--dry-run`), and is documented in the README.

## 4) Evidence Appendix

**Commits analyzed (spec `5695d3e` → HEAD):**
- `478f7da` backend scaffold + health (T1.0)
- `204e3a7` frontend scaffold + health call (T2.0)
- `55884b5` single-process serving (T3.0)
- `77abc3e` multi-stage Docker (T4.0)
- `93f68f1` dev tooling + README (T5.0)

**Commands executed (this validation):**
```
./gradlew clean test -PskipFrontend      → 3 suites: EnsembleApplicationTests 1, HealthControllerTest 1, SpaForwardingTest 4 (0 fail/err)
cd frontend && npm test -- --run          → App.test.tsx 2/2 pass
./gradlew build                           → BUILD SUCCESSFUL; jar embeds BOOT-INF/classes/static/{index.html,assets/*}
java -jar build/libs/ensemble-0.0.1-SNAPSHOT.jar
  GET /                   → 200
  GET /api/health         → 200  {"status":"ok"}
  GET /some/client/route  → 200
  GET /api/does-not-exist → 404
docker run ensemble:skeleton
  GET /api/health         → 200  {"status":"ok"}
  GET /                   → 200  (<title>Ensemble</title>)
```

**File integrity:** all 47 changed files (`5695d3e..HEAD`) classified — core (backend `src/main`, `build.gradle`, `Dockerfile`, frontend source, config) mapped to tasks/Relevant-Files; supporting (tests, `src/test/resources/static/*` fixtures, proof docs, `.pre-commit-config.yaml`, `eslint.config.js`, `README.md`) linked via commit messages/task notes. Working tree clean after build (generated outputs git-ignored).

**Security (Gate F):** grep for `sk-ant-…`, `ANTHROPIC_API_KEY=…`, private keys, and passwords across `01-proofs/` → none. Screenshots are dark-mode UI captures with no credentials.

---

**Validation Completed:** 2026-07-13
**Validation Performed By:** Claude Opus 4.8 (1M context)
