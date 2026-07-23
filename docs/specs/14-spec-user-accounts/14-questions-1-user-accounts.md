# 14 Questions Round 1 - User Accounts (signup/login)

Please answer each question below (select one or more options, or add your own notes). Feel free to add additional context under any question.

**How to answer:** put an `x` in the box(es) you want, e.g. `- [x] (A)`, and/or add a note under the question. When you're done, save the file and tell me to continue.

**Grounding (from the code map — so you can judge the recommendations):**

- The app has **no Spring Security and no password-hashing library** today; auth is a single shared passcode compared with hand-rolled SHA-256, and the session token is a **stateless HMAC over an expiry only — it carries no identity**. Adding accounts means (a) a new hashing dependency and (b) putting a `userId` into that token payload.
- The data layer is **two dedicated DynamoDB tables (items, outfits) with no GSIs anywhere**. A `User` record would copy the existing `SavedOutfit`/`OutfitRepository` pattern against a new `ensemble-users` table (auto-created for dev, Terraform-owned in cloud). The IAM policy wildcard already covers a future GSI, so no IAM change is needed either way.
- The frontend gate transport (token in `sessionStorage`, sent as `X-Ensemble-Session` header or `?token=` for `<img>`) can stay **unchanged**; only the passcode form itself would change.

**Assumptions I'll lock unless you override them here** (they follow the existing design closely, so I'm not asking them as full questions):

- The session token stays **stateless**: the `userId` is embedded in the existing signed HMAC payload (no server-side session table, so no mid-TTL revocation). Default TTL stays **12h**, no refresh/sliding — re-login on expiry. (This is issue #14's third open question; tunable later via config, so I'm treating it as non-blocking.)
- Sign-up does **not** send a verification email or do any email delivery (no mail infrastructure exists); an email is accepted as-is after format validation.

---

## 1. Fate of the shared passcode

Issue #14 is titled "replacing the shared demo passcode," and issue #8 originally added that passcode specifically so *"a stranger with the URL can't browse my wardrobe or trigger paid AI calls."* Once anyone can sign up, that cost/abuse barrier changes. What should happen to the passcode?

- [ ] (A) **Retire it entirely.** Login/signup is by account only. The **global daily call cap** (already built, ~100 Claude calls/day → `429`) becomes the sole spend backstop. Simplest; matches the issue title literally.
- [x] (B) **Keep it as a signup gate.** Anyone can *log in* with their account, but *creating* an account still requires the shared passcode (an extra field on the signup request). Preserves the "only people I gave the passcode to can spend my Claude budget" property from #8.
- [ ] (C) **Keep it as an outer gate on everything.** Enter the shared passcode to reach the app at all, *then* log into your account (two layers). Most protective, most friction, closest to double-login.
- [ ] (D) Other (describe)

**Current best-practice context:** For a single-owner demo that spends real API money behind a public URL, the meaningful risk isn't account takeover — it's an anonymous stranger running up the bill. A global daily cap bounds *total* spend but doesn't stop a stranger from consuming the whole day's budget, denying the owner. Gating *registration* is the cheapest way to keep spend to invited users.

**Recommended answer(s):** [(B)]

**Why these are recommended:**

- `(B)` keeps the exact protection #8 was created for (no anonymous paid calls) while still delivering real per-user identity — the passcode moves from "the whole auth system" to "one extra field on signup," which is a small, honest change rather than removing a safeguard.
- `(A)` is cleaner and most literal to the title, but it leaves the paid endpoints open to anyone who finds the URL; the global cap then protects *your wallet's ceiling* but lets one abuser exhaust the daily budget for you. Choose `(A)` if the demo URL is only ever shared privately and you accept that risk.
- `(C)` protects the most but forces two secrets/steps for every legitimate user and largely duplicates what per-account login already gives you — usually not worth the friction at demo scale.
- Whichever you pick, the acceptance criterion *"single-passcode flow is retired or explicitly documented as deprecated"* is satisfied: `(A)` retires it, `(B)`/`(C)` repurpose it to a narrower role that we'll document.

## 2. `User` record key design (email as key vs. `userId` + email GSI)

The issue says *"email as key/GSI."* Downstream (#15) needs a **durable `userId`** to scope wardrobe data by. Two clean shapes:

- [x] (A) **Email is the partition key; `userId` is a generated (UUID) attribute** on the same row. Login = `GetItem(email)`. Duplicate-email rejection = a conditional `PutItem(attribute_not_exists(email))` — atomic, no race. No GSI, no Terraform/auto-create GSI work. `userId` is what the token carries and what #15 scopes on.
- [ ] (B) **`userId` (UUID) is the partition key; email is a Global Secondary Index.** Login = `Query` the email GSI. Duplicate detection reads the GSI first (eventually consistent → a small race window unless you add a separate uniqueness item). Adds a GSI to Terraform + the local table initializer.
- [ ] (C) Other (describe)

**Current best-practice context:** DynamoDB single-item designs favor making the natural unique lookup key the partition key so uniqueness and reads are one strongly-consistent operation. A conditional write on the partition key is the standard atomic "unique email" guard; GSIs are eventually consistent and can't enforce uniqueness on their own.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` makes *"reject duplicate emails"* (an explicit acceptance criterion) a single atomic conditional write — `(B)` needs an extra guard to close the duplicate-signup race. Since a stateless token carries the `userId`, request auth never needs to load the user row, and #15 never needs a `userId → row` lookup — so `(A)`'s lack of a `userId` index costs nothing here.
- `(A)` also adds **zero** new infra (no GSI in Terraform or the dev table initializer), matching the repo's "no GSIs anywhere" reality.
- Pick `(B)` only if you expect users to **change their email** while keeping the same account, or to look accounts up by `userId` directly — neither is in scope for this demo.

## 3. Auth endpoint shape

Today `POST /api/auth` takes `{ passcode }` and returns `{ token }`; the filter exempts exactly `POST /api/auth` + `GET /api/health`. How should signup and login be exposed?

- [x] (A) **Repurpose `POST /api/auth`** to take `{ email, password }` (login), and add **`POST /api/accounts`** for signup. Both stay in the filter's open-route list. Minimal churn: the frontend `auth.ts`/filter keep the same endpoint name for login.
- [ ] (B) **New `POST /api/login`** (login) + **`POST /api/accounts`** (signup); remove/deprecate `/api/auth`. Cleaner naming, but touches the filter exemptions and the frontend endpoint constant.
- [ ] (C) **RESTful pair:** `POST /api/accounts` (create user) + `POST /api/sessions` (create session/login). Most convention-pure, most renaming.
- [ ] (D) Other (describe)

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` is the smallest diff that satisfies the issue: one endpoint changes its request body, one endpoint is added, and the filter's open-route list gains just `POST /api/accounts`. The frontend keeps calling `/api/auth` for login.
- `(B)`/`(C)` are more idiomatic names but rename an endpoint that already has tests and a frontend caller, for no functional gain at this scale.
- If you value REST purity over minimal churn, `(C)` is the "correct" long-term shape — say so and I'll spec it that way.

## 4. Password handling — hashing algorithm and strength policy

No hashing library exists today, so we must add one. Two sub-decisions (answer both):

**4a. Hashing algorithm / dependency**

- [x] (A) **bcrypt (work factor 12) via `spring-security-crypto`'s `BCryptPasswordEncoder`.** Pure-Java, no native library, added *without* the full Spring Security starter (so it won't touch the existing custom filter). Enforce the 72-byte input limit in validation.
- [ ] (B) **Argon2id (OWASP params) via `spring-security-crypto`'s `Argon2PasswordEncoder`.** OWASP's first choice for new systems, but pulls in **BouncyCastle**; slightly more config.
- [ ] (C) **Argon2id via `argon2-jvm`.** Also OWASP-preferred, but ships a **native library** — extra risk in the multi-stage amd64 App Runner image (the deploy target).
- [ ] (D) Other (describe)

**Current best-practice context:** The OWASP Password Storage Cheat Sheet (living document, 2025) recommends **Argon2id** first (min 19 MiB memory, 2 iterations, 1 parallelism), with **bcrypt at work factor ≥ 12** as an accepted alternative, noting bcrypt's 72-byte input truncation. All are strong; the practical differences here are dependency weight and native-lib deploy risk.

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` gets a strong, salted, slow hash (bcrypt/12 exceeds the OWASP minimum) with the **lightest, pure-Java footprint** — no native lib to break the container build, and no full Spring Security filter chain to collide with the hand-rolled `SessionAuthFilter`.
- `(B)` is technically the "best" algorithm but adds BouncyCastle for a marginal security gain the demo doesn't need; `(C)`'s native lib is the main thing that can silently break the amd64 deploy image — the worst trade for a demo.
- Pick `(B)` if you specifically want to say "Argon2id" in the security narrative and accept the extra dependency.

**4b. Password strength policy**

- [ x] (A) **NIST-aligned minimal:** length **≥ 8**, ≤ 72 bytes (bcrypt limit), **no composition rules** (no forced upper/lower/digit/symbol), validated server-side with Bean Validation; clear `400` on violation.
- [ ] (B) **Moderate:** length **≥ 12** plus at least one letter and one digit.
- [ ] (C) **Length only, higher floor:** length **≥ 10**, no other rules.
- [ ] (D) Other (describe)

**Current best-practice context:** NIST 800-63B and OWASP both now advise **length over composition** — mandatory character-class rules push users toward predictable patterns and add little entropy. Breached-password screening is recommended for production but needs an external list/service (out of scope for a demo).

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` matches current guidance (length-first, no composition theater), is trivially testable, and keeps the signup error path simple and clear — directly serving the *"malformed input returns clear errors"* acceptance criterion.
- `(B)`/`(C)` are defensible if you want a higher floor, but composition rules in `(B)` run against current NIST/OWASP advice for little real gain at demo scale.

## 5. Frontend scope — login/signup UI in this issue?

Retiring/changing the passcode necessarily breaks today's single-passcode entry screen (`AuthGate` + `auth.ts` post `{passcode}`). What's in scope for #14?

- [ x] (A) **Include a minimal login + signup UI.** Replace the passcode form with email/password login and a "create account" form, reusing the existing "Care Label" design system and the unchanged token transport (`sessionStorage` + `X-Ensemble-Session`/`?token=`). Makes the acceptance criteria (*"a new user can sign up"*, *"a user can log in"*) demoable end-to-end in the app.
- [ ] (B) **Backend-only this issue.** Ship the endpoints + filter principal; prove sign-up/login via `curl`/tests; leave the current passcode screen broken/stubbed and do the real UI in a follow-up.
- [ ] (C) Other (describe)

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` is the honest "just right" slice: because the passcode change *breaks the existing entry screen no matter what*, the frontend has to change anyway — so a minimal login/signup UI is the smallest state that leaves the app actually usable and makes the acceptance criteria demoable, not just curl-able.
- `(B)` would leave `main` with a broken login screen between issues; only choose it if you're deliberately sequencing the UI as a separate immediate follow-up.
- Frontend view plumbing stays light per `docs/TESTING.md` — meaningful state/API logic is tested, the forms themselves aren't over-tested.

## 6. Existing-user transition and the #14 / #15 boundary

The issue mentions a *"one-time transition: existing demo passcode users need a path forward"* and points to #15's note that *existing wardrobe data moves to one seed/default account*. But #15 explicitly **owns** the wardrobe-data reassignment. So what does **#14** own for the transition?

- [ x] (A) **#14 seeds a default account from env** (`ENSEMBLE_SEED_EMAIL` / `ENSEMBLE_SEED_PASSWORD`) at startup **if it doesn't already exist**, so the current single user has a real account to log into immediately. #14 stops there; **#15** later stamps the pre-existing wardrobe rows with that account's `userId`. Clean seam: #14 = identity, #15 = data.
- [ ] (B) **No seeding in #14.** The existing user just signs up fresh; *all* transition/reassignment of old data is deferred to #15. Simplest for #14, but there's a window where old wardrobe rows belong to no one.
- [ ] (C) **#14 includes a one-time migration script** that both creates the seed account *and* reassigns existing rows. Pulls #15's data work forward — likely over-scopes #14.
- [ ] (D) Other (describe)

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- `(A)` gives the issue's required "path forward" (the owner can log in on day one) while keeping a crisp boundary: #14 delivers *identity*, #15 delivers *data scoping/reassignment*. The seed account is exactly the `userId` #15 will key the old rows to.
- `(B)` is fine mechanically but leaves the "existing users have a path forward" requirement only half-met until #15 lands.
- `(C)` drags #15's data-migration scope into #14, blurring the two issues and enlarging this spec past "just right."
