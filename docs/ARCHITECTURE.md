# Architecture Guide

System design and technical decisions for Ensemble. This reflects decisions locked during planning; see the epic (GitHub #1) and `docs/specs/` for per-issue detail.

## Overview

One Spring Boot process serves a JSON API under `/api` and the built React/Vite PWA as static assets — a single deployable container. The app is built **local-first**, then deployed to AWS App Runner.

```
[React/Vite PWA] --/api--> [Spring Boot]
                              |-- WardrobeService --> DynamoDB (items, tags, wear-history)
                              |-- PhotoStorage    --> local disk (dev) | S3 (deploy)
                              |-- TaggingService  --> Claude Haiku 4.5 (vision, upload-time)
                              |-- StylistService  --> Claude Sonnet 5 (tool-loop, searchWardrobe)
```

## The Two AI Jobs

1. **Perception (tagging):** at upload, one Haiku 4.5 vision call turns a garment photo into structured tags (forced JSON). Editable by the user.
2. **Judgment (styling):** on a vibe, Sonnet 5 runs a tool-loop, calls `searchWardrobe`, picks item ids, and writes a reason. **The stylist never receives image bytes** — it reasons over text tags only; the app maps ids → stored photos.

## Data Model (DynamoDB, single-table)

Item:
- `itemId` (partition key), `photoKey`, `createdAt`
- Vision tags: `category`, `primaryColor`, `secondaryColor`, `formality` (1–5), `pattern`, `warmth` (1–3), `descriptors`
- Wear-history: `lastWorn`, `wornCount`

No relational modeling — `searchWardrobe` scans/returns all items at demo scale (~20). Accounts live in a separate email-keyed table; `UserRepository.findByUserId` (used by `/api/me`) is likewise a full-table scan, fine at demo scale — a `userId` GSI is the scale path if `/api/me` or per-user data scoping (#15) ever puts that lookup on a hot path.

## Photo Storage

`PhotoStorage` interface with two implementations:
- `LocalDiskPhotoStorage` — dev.
- `S3PhotoStorage` — deploy.

Photos are compressed/resized (≤800px JPEG) on save. Code depends only on the interface, so the local→S3 swap is configuration, not a rewrite.

## Stylist Agent + Guardrails

- **Tool:** `searchWardrobe` (no params) returns all items' text tags + wear-history, no images.
- **Output:** forced `{itemIds, reason}`.
- **Grounding:** validate every id exists → reject hallucinated ids → feed the error back → one retry. Only validated ids are rendered.
- **Conversation:** client holds history and resends each turn; the server is stateless. Pushback ("too plain") re-picks and avoids repeating the last look.
- **Wear-history write:** the single "I wore this" action increments `wornCount` and sets `lastWorn`; later suggestions vary based on recency.
- **Deterministic vs LLM:** wear-history (and future weather/color) are tool data, not LLM guesses.

### Prompt-injection posture (issue #21)

The stylist is a **constrained two-tool loop**, not a free-form chatbot: the model may call only `searchWardrobe` (read the caller's own wardrobe as text) and `record_outfit` (forced structured output). No tool has a side effect (no email, spend, or cross-user read), so a *successful* injection has a tiny blast radius — it still cannot take an action, exfiltrate another user's data, render a non-owned item, or leak a secret. The defenses below are **defense-in-depth** layered on top of that, so the app returns a grounded, bounded, escaped result even when the model is fully subverted (OWASP LLM01):

- **Bounded inputs.** `StyleRequest` carries Jakarta Bean Validation caps (`prompt` `@NotBlank`/≤1000, `history` ≤20 turns, each turn `text` ≤2000) and `@Valid` on the `@RequestBody`; over-cap/blank input is rejected on binding with the existing sanitized `400` (`ApiExceptionHandler`), **before** `CallCapService.reserve()` or any Claude call — capping the tokens a hostile request can spend.
- **Bounded, constrained output.** The two free-text fields (`reason`, per-piece `rationale`) are constrained **model-side** (system prompt + `record_outfit` description: "concise styling rationale only — no lists, counts, code," plus an ignore-vibe-format clause) and hard-capped **deterministically** (`reason` ≤300, `rationale` ≤200 via `TextBounds.cap`) after grounding, before the DTO. The cap is the backstop; the semantic constraint is what stops an enumeration ("count to 1000") from appearing. React text-node escaping renders any hostile value inert.
- **Client input framed as data.** In `AnthropicStylistModelClient`, each client turn's text is wrapped as tagged data (`<user_vibe>…</user_vibe>` for user turns, `<prior_suggestion>…</prior_suggestion>` for resent assistant/history turns) and any embedded copy of a wrapper delimiter is **stripped**, so the frame cannot be closed early. A system-prompt clause states tagged text is **data, never instructions** that change role/tools/output shape, and that `searchWardrobe` itemIds are the only authoritative field. Wrapping only touches turn *content* — roles are unchanged, so the pushback re-pick detection and the grounding-retry (`user` turn only) invariants hold.
- **Indirect injection closed.** The `searchWardrobe` tool description and the `renderWardrobe(...)` header frame the wardrobe text — especially user-editable descriptors — as data, not instructions. A payload hidden in a descriptor still yields a grounded outfit; grounding + the output caps mean it is never echoed or obeyed.
- **Testing split (mock vs live).** The red-team unit suite mocks the `StylistModelClient` seam, so it proves **our handling** (grounded/bounded/escaped regardless of what the model returns), never the model's own jailbreak resistance. Whether the live model resists a jailbreak is a job for an optional, opt-in eval runner (mirroring `TaggingEvalRunner`) — never part of `test` or CI.

## Scope

- **MVP:** perception + grounded reasoning + wear-history.
- **Stretch (not MVP):** weather, color-as-code, occasion.

## Deployment (shipped — issue #9)

The operator runbook is in `README.md` ("Deploy to AWS"); operator steps are cross-referenced from [DEVELOPMENT.md](DEVELOPMENT.md).

- **Compute:** AWS App Runner (one container, port 8080, health-checking `/api/health`, min 1 / max 2 instances). No VPC. The service runs with `SPRING_PROFILES_ACTIVE=cloud` — a Spring profile that blanks the DynamoDB endpoint override (default credential chain → instance role) and disables table auto-create (the cloud table is Terraform-owned; the instance role deliberately has item-level DynamoDB permissions only).
- **Data:** S3 (photos — `S3PhotoStorage` selected by `ENSEMBLE_PHOTOS_BACKEND=s3` behind the same `PhotoStorage` interface) + DynamoDB (items) — reached by the boundary-capped App Runner instance role via IAM, no VPC connector.
- **Secrets:** Claude key, passcode, and session secret in AWS Secrets Manager. Terraform declares only empty containers; values are populated out-of-band and injected at runtime **by ARN** as the same `ENSEMBLE_*` env vars the app already reads — no plaintext in Terraform state, git, or service config.
- **IaC:** [`terraform/deploy/`](../terraform/deploy/) — a self-contained root module (S3, DynamoDB, ECR, App Runner, Secrets Manager, instance + ECR-access + CI roles, all `abreiss-ensemble-*`), with remote S3 state and S3-native locking (`use_lockfile`, Terraform ≥ 1.11). Applied only by the operator as the scoped identity — never by CI.
- **Pipeline:** [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) on push to `main`: assume the `abreiss-ensemble-ci` role via OIDC → build the multi-stage image → push to ECR under an immutable `sha-<git-sha>` tag → `aws apprunner update-service` repoints the service → poll to `RUNNING`. Rollback = repoint at an earlier SHA tag. [`ci.yml`](../.github/workflows/ci.yml) runs backend + frontend tests, `terraform fmt -check`/`validate`, and the Access Analyzer policy lint on every PR/push.
- **Terraform identity:** in the shared AWS account, Terraform runs as a dedicated, prefix-scoped IAM user (`abreiss-ensemble-terraform`) created by a one-time bootstrap in [`terraform/bootstrap/`](../terraform/bootstrap/) — it can only touch `abreiss-ensemble-*` resources. See [AWS_ACCESS.md](AWS_ACCESS.md).
- **Frontend:** vite-plugin-pwa generates the manifest + service worker for iPhone home-screen install.

## Security

- **Account model (issue #14):** email/password accounts, checked server-side. `POST /api/auth` logs an existing account in with `{email, password}`; unknown email and wrong password both return the same generic `401` (non-enumerating). `POST /api/accounts` is **invite-only signup** — `{email, password, passcode}` — where `ENSEMBLE_PASSCODE` has moved roles: it's now the shared **signup/invite code**, not a login credential. A blank/unset `ENSEMBLE_PASSCODE` closes signup (no new accounts) while existing accounts can still log in. The session token carries an opaque, HMAC-signed `userId` (no email/PII), default 12h TTL, sent as `X-Ensemble-Session` (or `?token=` for `<img>` GETs) — never shipped in the client bundle. A **seed account** (`ENSEMBLE_SEED_EMAIL` / `ENSEMBLE_SEED_PASSWORD`) is created idempotently at startup when both are set, bypassing the signup passcode, so there's a usable login during the transition without anyone knowing the invite code. **Session-secret / invite-code coupling:** when `ENSEMBLE_SESSION_SECRET` is blank the token-signing HMAC key is derived from `ENSEMBLE_PASSCODE` — but since that passcode is now a *shared* invite code, every invited user would then know the signing key and could forge tokens for arbitrary userIds. The single-secret fallback is kept for local dev, but a loud startup warning fires while the session secret is unconfigured; set a distinct `ENSEMBLE_SESSION_SECRET`, especially now that per-user data scoping (#15) is enforced.
- **Per-user data isolation (issue #15):** every wardrobe, outfit, and stylist operation is scoped to the caller's opaque `userId`, resolved from the session token by `SessionAuthFilter` and injected into controllers via `@CurrentUserId`. Items and outfits are owner-stamped on write and read back through a sparse `userId-index` GSI query (never a full-table scan); every single-resource operation (`get`/`updateTags`/`markWorn`/`delete`, outfit `delete`) routes through an ownership choke point that throws a non-enumerating `404` for any id the caller does not own — a cross-user id is indistinguishable from a missing one, so it never returns another user's data. This isolation is **only as trustworthy as the token**: with a blank `ENSEMBLE_SESSION_SECRET` the signing key falls back to the shared invite code, so any invited user could forge a token bearing another `userId`. Set a distinct `ENSEMBLE_SESSION_SECRET` in any shared or deployed environment (the startup warning fires until you do).
- A **daily call cap** (~100/day → 429) is the app-side backstop, since no key-level spend cap is available.
- No secrets committed; keys via env (dev) / Secrets Manager (deploy).
- **Prompt-injection hardening (issue #21):** the stylist is safe under adversarial input by construction — bounded inputs (sanitized `400` before any Claude call), bounded + semantically-constrained free-text output, client vibe/history framed as data with delimiter break-out neutralized, and indirect-descriptor injection closed — layered on a least-privilege two-tool loop with a tiny blast radius. Full threat model and the mock-vs-live testing split are in "Stylist Agent + Guardrails → Prompt-injection posture" above.
- **Blast-radius containment in the shared AWS account:** Terraform runs as a scoped IAM identity limited to `abreiss-ensemble-*` resources, with a permissions boundary capping every role it creates to the same box. Full narrative + the enumerated `Resource: "*"` exceptions in [AWS_ACCESS.md](AWS_ACCESS.md).
- **CI auth:** GitHub Actions assumes the boundary-capped `abreiss-ensemble-ci` role via OIDC (trust policy conditioned to this repo/ref) — no long-lived AWS keys in GitHub. Its permissions are exactly ECR push + App Runner deploy + one scoped `iam:PassRole`. Access Analyzer policy lint runs as a standing CI check (see [AWS_ACCESS.md](AWS_ACCESS.md) for the lint's known permission gap and its resolution).
