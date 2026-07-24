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
`/api/**` route except `POST /api/auth`, `POST /api/accounts`, and
`GET /api/health` requires a logged-in session — see
[Passcode gate & daily call cap](#passcode-gate--daily-call-cap) before
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

Per-user scoping (spec #15) added a `userId-index` GSI to the `ensemble-items`
and `ensemble-outfits` tables. The startup initializer skips tables that
already exist, so a local dev whose tables predate the GSI will log a WARN
that the index is missing rather than add it — drop the tables to pick it up
(`docker compose down -v` then restart, or delete just those two tables) so
they're recreated with the index. Deployed tables get the index in place via
Terraform (an in-place `UpdateTable`, no table recreation).

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

**Accounts.** Ensemble uses **username/password accounts** (issue #14) instead of a
single shared login passcode. Every `/api/**` route except `POST /api/auth`,
`POST /api/accounts`, and `GET /api/health` requires a valid session token.

- `POST /api/auth` — **log in** to an existing account with `{"username", "password"}`.
  An unknown username and a wrong password both return the same generic `401`
  (non-enumerating); malformed input returns `400`.
- `POST /api/accounts` — **invite-only sign-up** with
  `{"username", "password", "passcode"}` and auto-login on success. The `passcode`
  here is the shared **signup/invite code** — set it in your git-ignored `.env`
  (see [Vision tagging](#vision-tagging-tag-preview) for the `cp .env.example .env`
  step):

  ```bash
  # .env
  ENSEMBLE_PASSCODE=<signup-code>
  # optional: ENSEMBLE_SESSION_SECRET=<separate-hmac-key>   (defaults to the passcode)
  ```

  A blank/unset `ENSEMBLE_PASSCODE` leaves **signup** closed — `POST /api/accounts`
  returns `401` and no account is created — but existing accounts can still log
  in. Usernames are 3–30 chars, charset `[A-Za-z0-9._-]` with no leading/trailing
  separator, normalized case-insensitively (lowercased). A duplicate username
  returns `409`; an invalid username, a password under 8 characters, or a
  password over 72 bytes returns `400`; a wrong signup passcode returns `401`
  (no user created).
- **Seed account.** If both `ENSEMBLE_SEED_USERNAME` and `ENSEMBLE_SEED_PASSWORD`
  are set in `.env`, a default account is created idempotently on startup
  (skipped if it already exists), bypassing the signup passcode — a way to get
  a usable login before you have (or want to share) an invite code. Leave both
  blank to skip seeding.

**Migrating a local `ensemble-users` table (email → username).** The users
table's partition key changed from `email` to `username`. DynamoDB cannot alter
an existing table's key schema in place, so a local `ensemble-users` table
created before this change must be **recreated**, not migrated — the same
"drop the table so it's recreated" approach already used above for the
`userId-index` GSI. This is a deliberate, operator-run step: the app performs
**no automatic table drop** and **never enumerates-and-deletes rows**.

1. Stop the app (stop the `./gradlew bootRun` process).
2. Drop the old table against DynamoDB Local (the `docker-compose.yml` in this
   repo publishes DynamoDB Local on `:8000`; adjust the port if yours differs):

   ```bash
   aws dynamodb delete-table --table-name ensemble-users --endpoint-url http://localhost:8000
   ```
3. Restart the app (`./gradlew bootRun`) — `DynamoDbTableInitializer` runs on
   startup and auto-recreates `ensemble-users` with `username` as the partition
   key.
4. Set `ENSEMBLE_SEED_USERNAME` and `ENSEMBLE_SEED_PASSWORD` in `.env` and
   restart once more to reseed the default account.

**Migrating the cloud `ensemble-users` table (operator-run Terraform).** The
cloud users table is Terraform-owned, so its key schema does **not** change when
CI deploys the new app image — the username app would then fail against the old
`email`-keyed table. `terraform/deploy/data_stores.tf` now declares the
`username` partition key, and the seed secret/env var have been renamed
`ENSEMBLE_SEED_EMAIL` → `ENSEMBLE_SEED_USERNAME` (`apprunner.tf`, `secrets.tf`,
`variables.tf`). Because DynamoDB cannot alter a key schema in place, applying
this **destroys and recreates** `<prefix>-users`, wiping any existing cloud
accounts (recreate, not migrate; acceptable at demo scale per Resolved Decision
D2). Run as the scoped `abreiss-ensemble-terraform` identity:

1. `terraform plan` — expect the `aws_dynamodb_table.users` **replacement**, plus
   (with `seed_account_enabled` left `false`) no seed-secret resources and no
   seed env vars on the service.
2. `terraform apply` — do this promptly: until it runs, the CI-deployed username
   app is broken against the still-`email`-keyed table (account calls 500).
3. Optional: to seed a cloud account, set `seed_account_enabled = true`, populate
   the `<prefix>-seed-username` / `<prefix>-seed-password` secret values
   out-of-band, then apply — otherwise use invite-only signup (`ENSEMBLE_PASSCODE`).

Either `/api/auth` or `/api/accounts` returns a signed, expiring (default 12h)
session token carrying an opaque `userId` (no username/PII). Send it as the
`X-Ensemble-Session` header on every subsequent call:

```bash
# log in with an existing (e.g. seeded) account
TOKEN=$(curl -s -X POST localhost:8080/api/auth \
  -H 'Content-Type: application/json' \
  -d '{"username":"<demo-username>","password":"<demo-password>"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')

curl -s localhost:8080/api/items -H "X-Ensemble-Session: $TOKEN"
```

```bash
# or sign up a new account with the invite passcode (auto-login)
TOKEN=$(curl -s -X POST localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"username":"<demo-username>","password":"<demo-password>","passcode":"<signup-code>"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
```

`<img src>` requests can't set headers, so gated photo GETs also accept the
token as a `?token=` query param (the frontend's `photoUrl(id)` does this
automatically). The frontend itself renders a login/sign-up screen, stores the
token in `sessionStorage`, and returns to that screen on any `401`, so day-to-day
use just means logging in once per tab. `GET /api/me` returns
`{"userId", "username"}` for the authenticated caller.

**Per-user isolation (spec #15).** Every `/api/items` and `/api/outfits` route is
scoped to the caller's `userId` from the token: two accounts see disjoint
wardrobes, and one account cannot read, mutate, or delete another's items — a
cross-user id returns the same `404` as a missing one, never the resource. Quick
check with two accounts' tokens (`$TOKEN_A` / `$TOKEN_B`, never literal secrets):

```bash
# each account sees only its own items (disjoint id sets)
curl -s localhost:8080/api/items -H "X-Ensemble-Session: $TOKEN_A"
curl -s localhost:8080/api/items -H "X-Ensemble-Session: $TOKEN_B"

# B cannot delete one of A's items -> 404 (not 204), and A's item is untouched
curl -s -o /dev/null -w '%{http_code}\n' \
  -X DELETE localhost:8080/api/items/<an-A-item-id> -H "X-Ensemble-Session: $TOKEN_B"
# -> 404
```

This isolation is only trustworthy with a distinct `ENSEMBLE_SESSION_SECRET` set;
otherwise the token-signing key falls back to the shared invite code and an
invited user could forge another user's `userId` (a startup warning fires until
you set one).

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

## Deploy to AWS (App Runner)

The same container deploys to AWS: Terraform provisions the cloud footprint
(S3 photos bucket, DynamoDB table, ECR, App Runner, Secrets Manager, IAM
roles) and GitHub Actions turns every push to `main` into a deployment.
Terraform runs locally as the scoped `abreiss-ensemble-terraform` identity —
never as admin, never in CI. Identity/credential setup is in
[docs/AWS_ACCESS.md](docs/AWS_ACCESS.md); module internals in
[`terraform/deploy/README.md`](terraform/deploy/README.md).

**Prerequisites:** the scoped identity's AWS profile configured locally (see
[docs/AWS_ACCESS.md](docs/AWS_ACCESS.md)), Terraform ≥ 1.11, Docker.

**1. One-time — create the Terraform state bucket.** The module keeps its own
remote state in S3 (`abreiss-ensemble-tfstate`, S3-native locking), and
Terraform can't create the bucket its backend needs, so it's created once out
of band:

```bash
cd terraform/deploy
AWS_PROFILE=abreiss-ensemble-terraform ./create-state-bucket.sh
```

**2. Provision the footprint** (review the plan before applying):

```bash
AWS_PROFILE=abreiss-ensemble-terraform terraform init
AWS_PROFILE=abreiss-ensemble-terraform terraform plan
AWS_PROFILE=abreiss-ensemble-terraform terraform apply
```

**3. Populate the secret values out-of-band.** Terraform declares only
empty Secrets Manager containers, so no plaintext ever enters state or git.
Three are required — `abreiss-ensemble-anthropic-key`, `-passcode` (the
signup/invite code), and `-session-secret`. Paste each value in the AWS console
(Secrets Manager → secret → *Set secret value*), or use the CLI with a
git-ignored file so the value stays out of shell history:

```bash
# required: abreiss-ensemble-anthropic-key, -passcode, -session-secret
AWS_PROFILE=abreiss-ensemble-terraform aws secretsmanager put-secret-value \
  --secret-id abreiss-ensemble-anthropic-key --secret-string file://secret.txt
rm secret.txt
```

Startup seeding of a default account is **opt-in and off by default**: with
`seed_account_enabled = false` the `-seed-email` / `-seed-password` secrets are
not created and are not referenced by the service, so the deploy never depends
on them and invite-only signup (`POST /api/accounts`, gated by the passcode) is
the account-creation path. To seed a default account instead, apply with
`-var seed_account_enabled=true` **and** populate both `-seed-email` /
`-seed-password` values before/at apply — an enabled-but-empty seed secret is
unresolvable and fails the App Runner revision (it rolls back to the prior one).

**4. One-time — push the `linux/amd64` seed image.** The service is pinned to
`:latest` exactly once before CI takes over; App Runner only runs amd64
images. Follow the "Seed image" section in
[`terraform/deploy/README.md`](terraform/deploy/README.md) (an arm64 seed
built on Apple Silicon fails the deploy with no application logs).

**5. Wire CI to the provisioned resources.** Set three GitHub **repository
variables** (not secrets — they're resource identifiers, not credentials)
from the module outputs: `AWS_CI_ROLE_ARN`, `AWS_ECR_REPOSITORY_URL`,
`AWS_APPRUNNER_SERVICE_ARN`. The mapping table is in
[`terraform/deploy/README.md`](terraform/deploy/README.md).

**6. Push to deploy.** Every push to `main` runs
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml): assume the CI
role via OIDC (no stored AWS keys) → build the image → push to ECR under an
immutable `sha-<git-sha>` tag → `aws apprunner update-service` repoints the
service at the new tag → poll `describe-service` until `RUNNING`.

**Read the public URL** (and verify health):

```bash
URL=$(terraform -chdir=terraform/deploy output -raw app_runner_service_url)
curl -s "https://$URL/api/health"        # -> {"status":"ok"}
```

Open `https://$URL` in Safari on an iPhone and **Share → Add to Home Screen**
for the standalone PWA.

**Rollback.** Every deploy is an immutable git-SHA tag, so rolling back is
repointing the service at an earlier tag — the same call CI makes:

```bash
AWS_PROFILE=abreiss-ensemble-terraform aws apprunner update-service \
  --service-arn "$(terraform -chdir=terraform/deploy output -raw app_runner_service_arn)" \
  --source-configuration '{"ImageRepository":{"ImageIdentifier":"<ecr-repo-url>:sha-<earlier-git-sha>","ImageRepositoryType":"ECR"}}'
```

Then poll `aws apprunner describe-service` until the status returns to
`RUNNING`. (Re-running the old commit's Deploy workflow won't work — ECR's
immutable tags reject re-pushing an existing tag.)

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
