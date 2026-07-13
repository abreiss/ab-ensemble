# 01-tasks-app-skeleton.md

Task list for `01-spec-app-skeleton`.

Traceability: Unit 1 (backend health) → 1.0; Unit 2 (frontend calls API) → 2.0; Unit 3 (single-process serving + container) → 3.0 + 4.0; Repository Standards / "new dev can start from README" → 5.0.

Assumptions locked from the spec's Open Questions: Gradle build tool; Java 21 / Spring Boot 3.4.x / React 19 / Vite 6; frontend directory `frontend/`; npm package manager.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `settings.gradle`, `build.gradle` | Gradle project + deps (Spring Boot web, test), JaCoCo, and the task that wires the frontend build into static resources. |
| `gradlew`, `gradle/wrapper/*` | Gradle wrapper for reproducible builds. |
| `src/main/java/com/ensemble/EnsembleApplication.java` | Spring Boot application entry point. |
| `src/main/java/com/ensemble/health/HealthController.java` | Implements `GET /api/health`. |
| `src/test/java/com/ensemble/health/HealthControllerTest.java` | Test for the health endpoint (written first). |
| `src/main/java/com/ensemble/web/SpaForwardingController.java` | Forwards non-API, non-asset routes to `index.html` (SPA fallback). |
| `src/test/java/com/ensemble/web/SpaForwardingTest.java` | Tests SPA fallback serves `index.html` and does not shadow `/api/**`. |
| `src/main/resources/application.yml` | Server port (8080) and static-resource config. |
| `src/main/resources/static/` | Destination for the built frontend (populated by the Gradle build). |
| `frontend/package.json`, `frontend/tsconfig.json` | Frontend deps, scripts, TypeScript config. |
| `frontend/vite.config.ts` | Vite config: dev proxy `/api` → `:8080`, build `outDir` → backend static, Vitest config. |
| `frontend/index.html`, `frontend/src/main.tsx` | Frontend HTML entry + React bootstrap. |
| `frontend/src/App.tsx` | Mobile-first shell that calls the health API and renders status. |
| `frontend/src/api/health.ts` | Fetch wrapper for `GET /api/health`. |
| `frontend/src/App.test.tsx` | Vitest + RTL test: renders status (ok) and failure (unreachable). |
| `frontend/src/setupTests.ts` | RTL/Vitest test setup. |
| `Dockerfile`, `.dockerignore` | Multi-stage image (node build → jar → JRE) serving API + UI. |
| `.gitignore` | Excludes build outputs, `node_modules/`, env files. |
| `.pre-commit-config.yaml` | Lightweight commit gates (fast tests, format, secret scan). |
| `README.md` | Local-run, build, docker, and test/coverage instructions. |

### Notes

- Backend tests live under `src/test/java/...` mirroring the main package; run with `./gradlew test`.
- Frontend tests live beside their components (e.g. `App.test.tsx`); run with `npm test -- --run`.
- Follow strict TDD for the backend health endpoint and SPA fallback (RED → GREEN → REFACTOR).
- Follow `AGENTS.md`, `docs/DEVELOPMENT.md`, and `docs/TESTING.md` for conventions and the coverage split.

## Tasks

### [x] 1.0 Backend scaffold + health endpoint

Establish the Gradle Spring Boot project (`com.ensemble`, Java 21, Spring Boot 3.4.x) with test infra (JUnit 5, JaCoCo), and implement `GET /api/health` test-first.

#### 1.0 Proof Artifact(s)

- Test: `HealthControllerTest` passes — demonstrates the endpoint returns 200 with `{"status":"ok"}`, written test-first (Unit 1 FRs).
- CLI: `./gradlew bootRun` then `curl -s -o /dev/null -w "%{http_code}" localhost:8080/api/health` returns `200` — backend boots + serves API.
- CLI: `curl -s localhost:8080/api/health` returns `{"status":"ok"}` — endpoint contract.
- CLI: `./gradlew test jacocoTestReport` completes — test + coverage infra wired.

#### 1.0 Tasks

- [x] 1.1 Initialize the Gradle project (via Spring Initializr): `settings.gradle`, `build.gradle` (Java 21 toolchain, Spring Boot **4.1.0** + dependency-management plugins, `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, JaCoCo plugin), and the Gradle wrapper (Gradle 9.5.1). _Version bumped from the spec's 3.4.x placeholder to the current default._
- [x] 1.2 Add `EnsembleApplication.java` (`@SpringBootApplication`) and `application.yml` (`server.port: 8080`).
- [x] 1.3 (RED) Wrote `HealthControllerTest` (`@WebMvcTest` + MockMvc) asserting `GET /api/health` → 200 and body `{"status":"ok"}`; confirmed it failed (no controller).
- [x] 1.4 (GREEN) Implemented `HealthController` (`@GetMapping("/api/health")`) returning `{"status":"ok"}`; test passes.
- [x] 1.5 (REFACTOR) Confirmed `./gradlew test jacocoTestReport` generates the coverage report.
- [x] 1.6 Verified: `./gradlew bootRun` + `curl` → `200` and `{"status":"ok"}`.

### [x] 2.0 Frontend scaffold + health call

Scaffold React 19 + Vite 6 + TypeScript in `frontend/` with a mobile-first shell that calls `GET /api/health` and renders the status; configure the dev proxy and Vitest + RTL.

#### 2.0 Proof Artifact(s)

- Test: `App.test.tsx` (Vitest + RTL) passes — renders status from a mocked API for both success ("ok") and failure ("unreachable").
- Screenshot: browser at `localhost:5173` shows the rendered health status — frontend↔backend wiring via dev proxy (Unit 2 FRs).
- CLI: `cd frontend && npm run build` succeeds — frontend builds cleanly.

#### 2.0 Tasks

- [x] 2.1 Scaffold the Vite React-TS app in `frontend/` (`package.json`, `tsconfig.json`, `index.html`, `src/main.tsx`); add `react` 19 + `vite` 6 + Vitest + `@testing-library/react`.
- [x] 2.2 Configure `vite.config.ts`: dev-server proxy `/api` → `http://localhost:8080`; `build.outDir` → `../src/main/resources/static`; Vitest (`jsdom`, `setupTests.ts`).
- [x] 2.3 Add mobile-first shell in `App.tsx` (viewport meta in `index.html`, single-column layout).
- [x] 2.4 Add `src/api/health.ts` fetching `/api/health` and returning the parsed status.
- [x] 2.5 (RED) Write `App.test.tsx` mocking the health fetch, asserting the status renders for success and failure; confirm it fails.
- [x] 2.6 (GREEN) Implement `App` to call health on load and render "ok" / "unreachable"; confirm the test passes.
- [x] 2.7 Verify: `npm run dev` (with backend running) shows the status in the browser (screenshot); `npm run build` succeeds.

### [x] 3.0 Single-process serving from Spring

Wire the frontend build into Gradle so its output lands in Spring static resources; serve the SPA at `/` with fallback to `index.html`, without shadowing `/api/**`.

#### 3.0 Proof Artifact(s)

- CLI: `./gradlew build` produces a jar that embeds the built frontend — frontend build wired into backend build.
- CLI: run the jar, then `curl -s -o /dev/null -w "%{http_code}" localhost:8080/` → `200` and `.../api/health` → `200` — one process serves both.
- CLI: `curl -s -o /dev/null -w "%{http_code}" localhost:8080/some/client/route` → `200` — SPA fallback.
- Screenshot: browser at `localhost:8080/` (vite not running) renders the UI — single-process serving (Unit 3 serving FRs).

#### 3.0 Tasks

- [x] 3.1 Add a Gradle task that runs `npm ci && npm run build` in `frontend/` and outputs to `src/main/resources/static`; make `processResources`/`build` depend on it.
- [x] 3.2 (RED) Write `SpaForwardingTest` (MockMvc): `GET /` and `GET /some/client/route` return `index.html`; `GET /api/health` still returns 200 (fallback must not shadow the API); confirm it fails.
- [x] 3.3 (GREEN) Implement `SpaForwardingController` (or a resource resolver) forwarding non-API, non-asset routes to `index.html`; confirm the test passes.
- [x] 3.4 Verify: `./gradlew build`, run the jar, `curl` `/`, `/api/health`, and a client route for the 200 proofs; screenshot the UI at `:8080`.

### [ ] 4.0 Multi-stage Docker image

Author a multi-stage Dockerfile (node build → Gradle jar → slim JRE) producing one image serving API + UI.

#### 4.0 Proof Artifact(s)

- CLI: `docker build -t ensemble .` succeeds — multi-stage build produces one image.
- CLI: `docker run -p 8080:8080 ensemble`, then `curl -s -o /dev/null -w "%{http_code}" localhost:8080/api/health` → `200` — image serves the API.
- Screenshot: browser at `localhost:8080/` (from the container) renders the UI — image serves the UI (Unit 3 container FRs).

#### 4.0 Tasks

- [ ] 4.1 Write the multi-stage `Dockerfile`: stage 1 (`node:20`) builds the frontend; stage 2 (Gradle) builds the jar with the built assets; final stage (`eclipse-temurin:21-jre`) runs the jar. Add `.dockerignore`.
- [ ] 4.2 Order layers for cache efficiency (copy wrapper/manifests before sources); expose 8080.
- [ ] 4.3 Verify: `docker build`, `docker run`, `curl /api/health` → 200; screenshot the UI from the container.

### [ ] 5.0 Dev tooling & README bootstrap

Add `.gitignore`, a lightweight `.pre-commit-config.yaml`, and README run/build instructions so a new developer can start from the README alone.

#### 5.0 Proof Artifact(s)

- Diff: `.gitignore` excludes `build/`, `.gradle/`, `node_modules/`, `frontend/dist`, and env files — no artifacts/secrets committed (Security Considerations).
- CLI: from a clean state, following the README, `./gradlew bootRun` + `npm run dev` start the app and `curl .../api/health` → `200` — README-only bootstrap (Success Metric 4).
- CLI: `pre-commit run --all-files` completes — commit gates present and pass.

#### 5.0 Tasks

- [ ] 5.1 Add `.gitignore` (`build/`, `.gradle/`, `node_modules/`, `frontend/dist/`, `*.env`, `.env*`, `.DS_Store`).
- [ ] 5.2 Add `.pre-commit-config.yaml` per `docs/PRECOMMIT.md`: fast backend tests, `npm test -- --run`, format/lint, secret scan.
- [ ] 5.3 Update `README.md`: prerequisites, local run (`bootRun` + `npm run dev`), build, docker, and test/coverage commands.
- [ ] 5.4 Verify: clean-state README bootstrap reaches `/api/health` → 200; `pre-commit run --all-files` passes.
