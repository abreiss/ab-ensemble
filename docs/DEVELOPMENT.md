# Development Guide

How to set up, run, and develop Ensemble. Read [AGENTS.md](../AGENTS.md) first for the mandatory TDD workflow and standards.

## Prerequisites

- Java 21 (the LTS line the backend targets)
- Node 20+ and npm
- Docker (for DynamoDB Local and the container build)
- A Claude API key (set as an environment variable — never committed)

## Project Layout

```
ensemble/
  src/main/java/com/ensemble/    # Spring Boot backend
  src/test/java/com/ensemble/    # Backend tests
  frontend/                      # React + Vite app (built assets served by Spring)
  docs/                          # This guide + TESTING / ARCHITECTURE / PRECOMMIT
  docs/specs/                    # SDD specs, one directory per issue
```

## Local Run

Backend + frontend run together in dev; a single container serves both in prod.

- **Backend:** `./gradlew bootRun` → serves `/api/**` on port 8080.
- **Frontend:** `cd frontend && npm run dev` → Vite dev server on 5173, proxying `/api` → 8080.
- **DynamoDB Local:** `docker compose up -d dynamodb`; the table (`ensemble-items`) is auto-created on dev startup. Photos are written to `ensemble.photos.dir` (default `./data/photos`, git-ignored). See the "Wardrobe Storage" section in `README.md` for the `/api/items` CRUD flow.
- **Claude key:** copy `.env.example` to `.env` and set `ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...` before running anything that tags or styles. `.env` is git-ignored and never committed; if it is unset the client falls back to the SDK's standard `ANTHROPIC_API_KEY` environment variable. Tests never need a key. See the "Vision tagging" section in `README.md`.
- **Passcode gate:** set `ENSEMBLE_PASSCODE=<your-demo-passcode>` in the same `.env` before calling anything under `/api/**` other than `POST /api/auth` / `GET /api/health` — a blank passcode leaves the gate closed (`401` on every protected route). Exchange it for a session token via `POST /api/auth` and send the token back as the `X-Ensemble-Session` header (or `?token=` for `<img>` GETs). See "Passcode gate & daily call cap" in `README.md`.

Verify the skeleton: `curl -s localhost:8080/api/health` returns `200` with `{"status":"ok"}`.

## TDD Workflow

Every feature follows Red-Green-Refactor (see [AGENTS.md](../AGENTS.md) and [TESTING.md](TESTING.md)):

1. Write a failing test that names the behavior.
2. Write the minimum code to pass it.
3. Refactor with tests green.

Do not write production code before a failing test.

## Issue-by-Issue SDD Process

Work is tracked as GitHub issues under Epic #1, built local-first (#2–#7) → deploy (#9) → deliverables (#10).

For each issue:

1. Generate its spec (`docs/specs/NN-spec-<name>/`) — "Continue SDD with spec generation" / start a new feature.
2. Generate the task list + pass the planning audit — "Continue SDD with task planning."
3. Implement task-by-task with TDD + proof artifacts — "Continue SDD with implementation."
4. Validate against the spec — "Continue SDD with validation."

Keep commits small (roughly one per demoable unit) with conventional-commit messages.
