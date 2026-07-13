# Task 03 Proofs — Single-process serving from Spring

## Task Summary

This task proves one Spring Boot process serves both the JSON API and the built
React UI. The Gradle build compiles the frontend into Spring's static resources,
the jar embeds those assets, and the running jar serves the SPA at `/` with a
fallback to `index.html` for client routes — without shadowing `/api/**`.

## What This Task Proves

- The frontend build is wired into Gradle: `./gradlew build` produces a jar that embeds the built UI (Unit 3 serving FR).
- One process serves both: `GET /` → the UI (200) and `GET /api/health` → `{"status":"ok"}` (200).
- SPA fallback works: an unknown client route (`/some/client/route`) returns the SPA shell (200).
- The fallback does not shadow the API: an unknown API path (`/api/does-not-exist`) returns **404**, not the SPA shell.
- Audit FLAG remediated: the frontend build is skippable via `-PskipFrontend`, so `./gradlew test -PskipFrontend` needs no Node toolchain.

## Evidence Summary

- `SpaForwardingTest` (MockMvc, written test-first) passes 4/4: root, nested client route, API-not-shadowed, real-asset-served.
- `./gradlew clean build` succeeds; the jar contains `BOOT-INF/classes/static/index.html` + hashed assets.
- Against the running jar: `/` = 200 (real index.html), `/api/health` = 200 `{"status":"ok"}`, `/some/client/route` = 200 (SPA), `/api/does-not-exist` = 404.
- A browser at `http://localhost:8080/` (no vite dev server) renders **"Backend status: ok"**.
- `-PskipFrontend` removes `:buildFrontend` from the task graph.

## Artifact: SpaForwardingTest (RED → GREEN)

**What it proves:** The SPA fallback serves `index.html` for client routes and real assets directly, while API routes are never rewritten to the SPA shell.

**Why it matters:** "SPA fallback must not shadow `/api/**`" is the core serving guardrail for this slice.

**RED** — before `SpaForwardingConfig` existed, compilation failed:

```
error: cannot find symbol
  @Import(SpaForwardingConfig.class)
          ^  symbol: class SpaForwardingConfig
```

**GREEN:**

**Command:**

```bash
./gradlew test -PskipFrontend --tests "com.ensemble.web.SpaForwardingTest"
```

**Result summary:** All 4 cases pass — `root_servesSpaIndex`, `clientRoute_fallsBackToSpaIndex`, `apiRoute_isNotShadowedBySpaFallback`, `staticAsset_isServedDirectly_notSpaIndex`.

```
BUILD SUCCESSFUL
```

## Artifact: Gradle build embeds the UI in the jar

**What it proves:** The frontend build is wired into Gradle and its output is packaged into the jar.

**Why it matters:** This is the single-deployable requirement — no manual copy step.

**Command:**

```bash
./gradlew clean build
unzip -l build/libs/ensemble-0.0.1-SNAPSHOT.jar | grep static/
```

**Result summary:** The build succeeds and the jar embeds the built SPA.

```
      413  BOOT-INF/classes/static/index.html
      288  BOOT-INF/classes/static/assets/index-CQPu3n-6.css
   194954  BOOT-INF/classes/static/assets/index-DQjY7ujV.js
```

## Artifact: One process serves API + UI + SPA fallback

**What it proves:** The running jar serves the UI, the API, and client-route fallback on a single port, and does not shadow unknown API paths.

**Why it matters:** This mirrors the eventual single-container deploy.

**Command:**

```bash
java -jar build/libs/ensemble-0.0.1-SNAPSHOT.jar &
curl -s -o /dev/null -w "%{http_code}" localhost:8080/                     # UI root
curl -s localhost:8080/api/health                                          # API
curl -s -o /dev/null -w "%{http_code}" localhost:8080/some/client/route    # SPA fallback
curl -s -o /dev/null -w "%{http_code}" localhost:8080/api/does-not-exist   # API not shadowed
```

**Result summary:** Root, API, and client route all return 200; the API body is `{"status":"ok"}`; an unknown API path correctly returns 404 (the fallback does not swallow it).

```
/                     -> HTTP 200   (serves <!doctype html> ... <title>Ensemble</title>)
/api/health           -> HTTP 200   {"status":"ok"}
/some/client/route    -> HTTP 200   (SPA shell, contains id="root")
/api/does-not-exist   -> HTTP 404
```

## Artifact: UI served by the single process (no vite)

**What it proves:** With only the jar running (vite dev server stopped), the browser at `:8080` renders the health status obtained from the same-origin API.

**Why it matters:** This is the Unit 3 serving proof — the deployed shape, not the dev proxy.

**Artifact path:** `docs/specs/01-spec-app-skeleton/01-proofs/artifacts/03-task-single-process-8080.png`

**Result summary:** The page shows `Ensemble` and **"Backend status: ok"**, served entirely from the packaged jar.

![Ensemble UI at localhost:8080 served by the jar, showing "Backend status: ok"](artifacts/03-task-single-process-8080.png)

## Artifact: `-PskipFrontend` escape hatch (audit FLAG remediation)

**What it proves:** The frontend build is bound to packaging but is skippable, so backend-only / Node-less workflows do not pull in `npm`.

**Why it matters:** The planning audit flagged that coupling the frontend build into the default lifecycle could break backend-only or Node-less CI loops.

**Command:**

```bash
./gradlew build --dry-run              # includes :buildFrontend
./gradlew test -PskipFrontend --dry-run # excludes :buildFrontend
```

**Result summary:** `:buildFrontend` appears in the default `build` graph but is absent when `-PskipFrontend` is set.

```
build            -> :buildFrontend SKIPPED  :bootJar SKIPPED  :test SKIPPED
test -PskipFrontend -> :test SKIPPED   (no :buildFrontend)
```

## Reviewer Conclusion

The single Spring process serves the API and the built UI from one jar, resolves
client-side routes to the SPA shell, and leaves `/api/**` untouched by the
fallback (unknown API paths 404 rather than returning HTML). The frontend build
is wired into Gradle yet cleanly skippable — satisfying the audit's regression
concern. This is the deployable shape the Docker image (Task 4) packages.
