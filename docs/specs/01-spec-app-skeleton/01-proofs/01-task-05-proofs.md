# Task 05 Proofs — Dev tooling & README bootstrap

## Task Summary

This task proves a new developer can start Ensemble from the README alone, that
generated artifacts and secrets stay out of git, and that lightweight commit
gates (fast tests, lint, secret scan) run and pass.

## What This Task Proves

- `.gitignore` excludes build outputs and local env files (Security Considerations): `build/`, `.gradle`, `node_modules/`, `frontend/dist/`, generated `src/main/resources/static/`, `*.tsbuildinfo`, `*.env` / `.env*`, `.DS_Store`.
- `.pre-commit-config.yaml` provides fast, file-scoped gates and `pre-commit run --all-files` passes.
- `README.md` documents prerequisites, local run, build (+ `-PskipFrontend`), Docker, and test/coverage commands.
- Following the README, `./gradlew bootRun` boots the app and `curl /api/health` returns `200` (Success Metric 4).

## Evidence Summary

- `git check-ignore` confirms generated outputs and env files are ignored; a probe `.env` is untracked.
- `pre-commit run --all-files` runs 10 hooks — all pass (hygiene, private-key + Anthropic-key scan, backend tests, frontend tests, frontend lint).
- `./gradlew bootRun` + `curl localhost:8080/api/health` → `200` `{"status":"ok"}`.

## Artifact: Ignore rules keep artifacts and secrets out of git

**What it proves:** Build outputs, dependencies, and local env files are not committable.

**Why it matters:** Prevents bloated diffs and accidental secret leakage (no Claude key exists yet, but env files are pre-emptively ignored).

**Command:**

```bash
git check-ignore build .gradle frontend/node_modules \
  src/main/resources/static frontend/tsconfig.app.tsbuildinfo some.env .env
```

**Result summary:** Every path above is reported as ignored. (`frontend/dist/` is
also ignored via a directory pattern; git only reports it once the directory
exists — the Vite dev/build output lands in `src/main/resources/static`.)

```
build
.gradle
frontend/node_modules
src/main/resources/static
frontend/tsconfig.app.tsbuildinfo
some.env
.env
```

## Artifact: Commit gates pass

**What it proves:** The lightweight pre-commit suite runs and passes on the whole repo.

**Why it matters:** These gates catch broken tests, lint regressions, and committed secrets before they land.

**Command:**

```bash
pre-commit run --all-files
```

**Result summary:** All 10 hooks pass — including the Anthropic-key secret scan and the fast backend/frontend test + lint hooks.

```
trim trailing whitespace.................................................Passed
fix end of files.........................................................Passed
check yaml...............................................................Passed
check for added large files..............................................Passed
check for merge conflicts................................................Passed
detect private key.......................................................Passed
Block committed Anthropic API keys.......................................Passed
Backend tests (gradle, -PskipFrontend)...................................Passed
Frontend tests (vitest)..................................................Passed
Frontend lint (eslint)...................................................Passed
```

## Artifact: README-only bootstrap reaches the API

**What it proves:** Running the documented local-run command boots the backend and serves the health endpoint.

**Why it matters:** Success Metric 4 — a new developer can start the app following only the README.

**Command:**

```bash
./gradlew bootRun          # documented local-run command
curl -s localhost:8080/api/health
```

**Result summary:** The backend boots and the health endpoint returns `200` with `{"status":"ok"}`.

```
bootRun health 200
health body: {"status":"ok"}
http code: 200
```

## Reviewer Conclusion

The repository ignores generated artifacts and env files, enforces fast commit
gates that pass (tests, lint, secret scan), and ships a README that a new
developer can follow to a running `/api/health`. The skeleton is complete and
reproducible from a clean checkout.
