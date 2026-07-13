# Task 02 Proofs — Frontend scaffold + health call

## Task Summary

This task proves the React 19 + Vite 6 frontend in `frontend/` renders a
mobile-first shell, calls `GET /api/health` on load, and displays the result —
showing `ok` on success and `unreachable` on failure. It also proves the Vitest
+ RTL suite covers both paths and that the production build succeeds, emitting
assets into Spring's static resources (`../src/main/resources/static`).

## What This Task Proves

- The frontend renders the backend health status obtained from the API (Unit 2 FRs).
- The rendering decision is covered test-first: `ok` on success, `unreachable` on failure.
- The Vite dev server proxies `/api` → `http://localhost:8080`, so the browser uses same-origin calls in dev.
- `npm run build` type-checks (`tsc -b`) and produces static assets wired into the backend's static resources.

## Evidence Summary

- `App.test.tsx` (Vitest + RTL) passes 2/2 with the health API mocked for success and failure.
- A live browser at `http://localhost:5173` renders **"Backend status: ok"**, with the value fetched through the dev proxy from the running backend.
- `curl localhost:5173/api/health` returns `{"status":"ok"}`, confirming the proxy forwards to the Spring backend.
- `npm run build` completes and emits `index.html` + hashed JS/CSS into `src/main/resources/static`.

## Artifact: Vitest + RTL suite (RED → GREEN)

**What it proves:** The status-rendering logic is implemented test-first and both the success and failure branches are covered.

**Why it matters:** The "display the result (ok / unreachable)" requirement is the one piece of real frontend logic in this slice; strict-enough TDD guards it.

**RED** — before `App.tsx` existed, the suite failed to resolve the import:

```
Error: Failed to resolve import "./App" from "src/App.test.tsx". Does the file exist?
 Test Files  1 failed (1)
      Tests  no tests
```

**GREEN** — after implementing `App.tsx`:

**Command:**

```bash
cd frontend && npm test -- --run
```

**Result summary:** Both tests pass — success renders `ok`, failure renders `unreachable`.

```
 ✓ src/App.test.tsx (2 tests) 27ms

 Test Files  1 passed (1)
      Tests  2 passed (2)
```

## Artifact: Dev proxy forwards /api to the backend

**What it proves:** During `vite dev`, requests to `/api/**` reach the Spring backend on `:8080`.

**Why it matters:** The frontend uses same-origin `/api` calls; without the proxy the browser call would fail in dev.

**Command:**

```bash
curl -s localhost:5173/api/health   # served by vite dev, proxied to :8080
curl -s localhost:8080/api/health   # direct backend
```

**Result summary:** Both return the same body, confirming the proxy path.

```
proxied /api/health via vite:5173:
{"status":"ok"}
direct backend:
{"status":"ok"}
```

## Artifact: Rendered health status in the browser

**What it proves:** The mobile-first shell renders the live health status fetched from the backend through the dev proxy.

**Why it matters:** This is the Unit 2 proof artifact — frontend↔backend wiring visible in a real browser.

**Artifact path:** `docs/specs/01-spec-app-skeleton/01-proofs/artifacts/02-task-frontend-health.png`

**Result summary:** The page shows the `Ensemble` heading and **"Backend status: ok"**, confirming the app fetched and rendered the status.

![Ensemble frontend at localhost:5173 showing "Backend status: ok"](artifacts/02-task-frontend-health.png)

## Artifact: Production build succeeds

**What it proves:** `npm run build` type-checks and emits static assets into the backend's static resources directory.

**Why it matters:** This is the wiring the single-process serving (Task 3) and Docker image (Task 4) depend on.

**Command:**

```bash
cd frontend && npm run build   # tsc -b && vite build
```

**Result summary:** Build succeeds; assets land under `src/main/resources/static/`.

```
../src/main/resources/static/index.html                   0.41 kB │ gzip:  0.28 kB
../src/main/resources/static/assets/index-CQPu3n-6.css    0.29 kB │ gzip:  0.22 kB
../src/main/resources/static/assets/index-DQjY7ujV.js   194.95 kB │ gzip: 61.05 kB
✓ built in 463ms
```

## Reviewer Conclusion

The frontend runs, calls the API, and renders the status in a real browser; the
rendering logic is test-covered for both success and failure; and the
production build emits assets into Spring's static resources — the exact
handoff the single-process serving and container tasks build on. Generated
build output (`src/main/resources/static/`) is git-ignored, not committed.
