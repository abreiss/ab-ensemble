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

## Wardrobe Storage (DynamoDB Local + photos)

The wardrobe API (`/api/items`) persists item records to **DynamoDB Local** and
photos to local disk. Start the local database before the backend:

```bash
docker compose up -d dynamodb      # DynamoDB Local on :8000
```

The table (`ensemble-items`) is auto-created on startup. Photos are written to
`./data/photos`, compressed to ≤800px JPEG on save; both `data/` and the DB
volume are git-ignored. Override via `application.yml` or the environment:

- `ensemble.dynamodb.endpoint` (default `http://localhost:8000`)
- `ensemble.dynamodb.table-name` (default `ensemble-items`)
- `ensemble.photos.dir` (default `./data/photos`)

With the backend running and the database up, exercise the CRUD flow:

```bash
# create (multipart: photo + tag fields) -> 201 with a server-generated itemId
curl -s -X POST localhost:8080/api/items \
  -F photo=@your-photo.jpg -F category=top -F primaryColor=navy \
  -F formality=3 -F warmth=2 -F descriptors=cotton

curl -s localhost:8080/api/items                          # list all
curl -s localhost:8080/api/items/{id}                     # get one
curl -s localhost:8080/api/items/{id}/photo -o out.jpg    # photo (image/jpeg, <=800px)
curl -s -X PUT localhost:8080/api/items/{id}/tags \
  -H 'Content-Type: application/json' \
  -d '{"category":"top","primaryColor":"black","formality":5,"warmth":2}'
curl -s -X DELETE localhost:8080/api/items/{id}           # delete -> 204
```

`formality` must be 1–5 and `warmth` 1–3 (else `400`); an unknown id returns
`404`. Stop the database with `docker compose down`.

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
