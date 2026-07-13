# Ensemble

An AI stylist that dresses you from photos of clothes you own: photograph your
wardrobe → AI tags each piece → give it a vibe → it builds an outfit from what
you own, explains why, and re-picks when you push back.

This repository currently contains the **app skeleton**: one Spring Boot process
that serves a JSON API under `/api` and the built React/Vite UI as static assets
— the runnable baseline every later feature plugs into.

> Contributors: read [AGENTS.md](AGENTS.md) first for the mandatory TDD workflow
> and coding standards, then the guides under [`docs/`](docs).

## Prerequisites

- **Java 21** (the backend targets the 21 LTS line)
- **Node 20+** and **npm** (frontend build/dev)
- **Docker** (container build; optional for local dev)
- **pre-commit** (optional but recommended local commit gates): `brew install pre-commit` or `pip install pre-commit`

No Claude API key is needed for this slice.

## Project Layout

```
ensemble/
  src/main/java/com/ensemble/   # Spring Boot backend (health endpoint, SPA serving)
  src/test/java/com/ensemble/   # Backend tests
  frontend/                     # React 19 + Vite 6 app (built assets served by Spring)
  docs/                         # AGENTS + DEVELOPMENT / TESTING / ARCHITECTURE / PRECOMMIT
  docs/specs/                   # SDD specs, one directory per issue
  Dockerfile                    # Multi-stage image (node build -> jar -> JRE)
```

## Local Development

Run the backend and the frontend dev server together. The Vite dev server
proxies `/api` to the backend, so the browser uses same-origin calls.

**1. Backend** (serves `/api/**` on port 8080):

```bash
./gradlew bootRun
```

Verify the API:

```bash
curl -s localhost:8080/api/health      # -> {"status":"ok"}
```

**2. Frontend** (Vite dev server on port 5173, hot reload, proxies `/api` → `:8080`):

```bash
cd frontend
npm install      # first time only
npm run dev
```

Open <http://localhost:5173> — the page shows **"Backend status: ok"** once it
reaches the backend.

## Build

A single command builds the frontend, embeds it into Spring's static resources,
and packages the runnable jar:

```bash
./gradlew build
```

The frontend build is wired into Gradle. For backend-only work or a Node-less
environment, skip it with:

```bash
./gradlew build -PskipFrontend      # or: ./gradlew test -PskipFrontend
```

Run the packaged jar (one process serves both API and UI on port 8080):

```bash
java -jar build/libs/app.jar
# then open http://localhost:8080
```

## Docker

One multi-stage image builds the frontend, packages the jar, and runs on a slim
JRE — serving both the API and the UI:

```bash
docker build -t ensemble:skeleton .
docker run --rm -p 8080:8080 ensemble:skeleton
# open http://localhost:8080 ; curl localhost:8080/api/health -> {"status":"ok"}
```

## Tests

**Backend** (JUnit 5, MockMvc):

```bash
./gradlew test -PskipFrontend           # fast, no Node needed
./gradlew jacocoTestReport               # coverage -> build/reports/jacoco/
```

**Frontend** (Vitest + React Testing Library):

```bash
cd frontend
npm test -- --run                        # run once (CI-style)
npm test                                 # watch mode
npm run lint                             # eslint
```

See [docs/TESTING.md](docs/TESTING.md) for the strict-TDD coverage split.

## Commit Gates (pre-commit)

Lightweight checks run on `git commit` (fast tests, lint, secret scan). Install
once:

```bash
pre-commit install
pre-commit run --all-files               # run all hooks manually
```

Configuration is in [`.pre-commit-config.yaml`](.pre-commit-config.yaml); details
in [docs/PRECOMMIT.md](docs/PRECOMMIT.md).
