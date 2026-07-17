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

No Claude API key is needed to build or test. Live vision tagging needs a key in a
git-ignored `.env` — see [Vision tagging](#vision-tagging-tag-preview). Every
`/api/**` route except `POST /api/auth` and `GET /api/health` is passcode-gated —
see [Passcode gate & daily call cap](#passcode-gate--daily-call-cap) before
calling `/api/items`, `/api/items/tag`, or `/api/style` locally.

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

## Vision tagging (tag preview)

`POST /api/items/tag` auto-tags a garment photo with one Claude **Haiku 4.5**
vision call and returns the suggested tags **without persisting anything** — the
client reviews/edits them, then saves through `POST /api/items` above.

The API key is read at startup from a git-ignored **`.env`** file (or the process
environment) — no need to re-`export` it each session. Copy the template and fill
in your key:

```bash
cp .env.example .env
# edit .env:  ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...
```

`.env` is git-ignored and never committed; tests never need a key. If
`ENSEMBLE_ANTHROPIC_API_KEY` is unset, the client falls back to the SDK's standard
`ANTHROPIC_API_KEY` environment variable. Then:

```bash
# preview: multipart photo -> suggested tags (200), nothing is saved
curl -s -X POST localhost:8080/api/items/tag -F photo=@your-photo.jpg
# -> {"category":"top","primaryColor":"navy","secondaryColor":null,
#     "formality":3,"pattern":"striped","warmth":2,"descriptors":["cotton"]}

# then create the item from the (optionally edited) suggested tags
curl -s -X POST localhost:8080/api/items \
  -F photo=@your-photo.jpg -F category=top -F primaryColor=navy \
  -F formality=3 -F warmth=2 -F descriptors=cotton
```

Tagging is **non-blocking**: if the vision call fails, times out, or returns
junk, the endpoint still returns `200` with a partial/empty suggestion (any field
may be `null`) so you can fill in the rest by hand — it never blocks item
creation. A missing or non-decodable photo (or one over the pixel cap) returns
`400`. The uploaded photo is downsized to ≤800px JPEG before the call, reusing the
same image guard as storage.

## Wardrobe UI

A mobile-first React (Vite) front end that drives the wardrobe CRUD + tag-preview
flow above. Three client-side routes:

- **`/`** — the wardrobe grid: every owned item as a lazy-loaded photo thumbnail
  (empty and retryable-error states included).
- **`/add`** — add an item: take/choose a photo → tags are **auto-suggested** →
  edit the tag form (descriptors are add/remove chips) → save → back to the grid.
- **`/item/:id`** — item detail: edit tags or delete the item behind an explicit
  confirmation.

Run it in dev alongside the backend:

```bash
docker compose up -d dynamodb        # DynamoDB Local on :8000
./gradlew bootRun                    # backend/API on :8080
cd frontend && npm run dev           # UI on :5173, proxies /api -> :8080
# open http://localhost:5173  (use a ~390px viewport / device toolbar)
```

**Browsing and editing need no Claude key** — the grid, item detail, tag editing,
and delete all work against DynamoDB alone. Only **live auto-tagging** on `/add`
calls Claude; without a key the tag-preview degrades to an empty-but-editable form
(you fill the tags in by hand) and everything else is unaffected. In the packaged
build the same screens are served by Spring at `http://localhost:8080/`.

## PWA Install (iPhone home screen)

The production build ([`vite-plugin-pwa`](https://vite-pwa-org.netlify.app/)) emits
a web app manifest and a service worker alongside the built assets, so the
packaged app installs to an iPhone home screen and opens **standalone** (no
Safari chrome):

```bash
./gradlew build                 # or: cd frontend && npm run build
ls src/main/resources/static/   # manifest.webmanifest, sw.js, the icon set
```

To install on an iPhone: open the app in Safari (`http://<host>:8080`, or the
deployed URL), tap **Share → Add to Home Screen**, then launch it from the
home-screen icon. The service worker precaches the app shell only — `/api/**`
responses are never cached (`navigateFallbackDenylist`), so authed/priced calls
always hit the server.

## Passcode gate & daily call cap

Because this app spends real Claude money and can show a private wardrobe, two
guards wrap the API — both are transparent once you're logged in and under the
limit.

**Passcode gate.** Every `/api/**` route except `POST /api/auth` and
`GET /api/health` requires a valid session token. Set a passcode in your
git-ignored `.env` (see [Vision tagging](#vision-tagging-tag-preview) for the
`cp .env.example .env` step):

```bash
# .env
ENSEMBLE_PASSCODE=<your-demo-passcode>
# optional: ENSEMBLE_SESSION_SECRET=<separate-hmac-key>   (defaults to the passcode)
```

A blank/unset `ENSEMBLE_PASSCODE` leaves the gate **effectively closed** — every
protected route returns `401` until it's set. Exchange the passcode for a
signed, expiring (default 12h) session token, then send it as the
`X-Ensemble-Session` header on every subsequent call:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth \
  -H 'Content-Type: application/json' -d '{"passcode":"<your-demo-passcode>"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

curl -s localhost:8080/api/items -H "X-Ensemble-Session: $TOKEN"
```

`<img src>` requests can't set headers, so gated photo GETs also accept the
token as a `?token=` query param (the frontend's `photoUrl(id)` does this
automatically). The frontend itself renders a passcode entry screen, stores the
token in `sessionStorage`, and returns to that screen on any `401`, so day-to-day
use just means logging in once per tab.

**Daily call cap.** `POST /api/style` and `POST /api/items/tag` (the two
Claude-backed endpoints) share one global counter, keyed by UTC calendar day.
Once a call would push the day's count past `ensemble.usage.daily-limit`
(default **100**), the endpoint returns `429` instead of calling Claude — the
counter increments before the call, so a failed/timed-out call still counts.
Tune it locally or at deploy via:

```bash
# application.yml
ensemble:
  usage:
    daily-limit: 100
```

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
