# Task 05 Proofs — End-to-end proof + docs + coverage gate

## Task Summary

This task closes the slice: it documents the tag-preview flow for a new developer,
re-verifies the full coverage gate on a clean build, and confirms secret hygiene.
The one remaining step — a **live** real-photo → tags → item run — requires an
`ANTHROPIC_API_KEY` and a running server and is recorded below as pending with
exact reproduction commands.

## What This Task Proves

- `README.md` documents the tag-preview flow: the env var, a sample `curl` to
  `POST /api/items/tag`, and the note that a failed/degraded call still returns an
  editable `200` (5.1).
- A clean `./gradlew clean test jacocoTestReport -PskipFrontend` is green with the
  Claude client **mocked** — no key, no network call in the suite (5.5).
- The tagging package meets the coverage bar: **≥90% line** overall and **100%
  branch** on the critical mapping + fallback logic (5.5).
- The `block-anthropic-keys` secret scan is green — no key in code, config, tests,
  or proofs (5.5).

## Evidence Summary

- Clean build: `BUILD SUCCESSFUL` for `clean test jacocoTestReport -PskipFrontend`.
- Coverage: tagging package LINE 86/89 = **96.6%**; `TaggingService` (mapping +
  fallback) BRANCH 24/24 = **100%**, LINE 33/33 = 100%.
- Secret scan: `Block committed Anthropic API keys ... Passed`.

## Artifact: Clean build + coverage gate (client mocked, no key)

**What it proves:** The whole backend suite passes from a clean state without a key
and without any network call, and the coverage gate is met.

**Why it matters:** This is the slice's TDD acceptance gate (Success Metrics 3–4):
≥90% line on the tagging package and 100% branch on the critical logic, all with
the Claude client mocked.

**Command:**

~~~bash
./gradlew clean test jacocoTestReport -PskipFrontend
# coverage parsed from build/reports/jacoco/test/jacocoTestReport.xml
~~~

**Result summary:** Green build; the tagging package is 96.6% line and the critical
`TaggingService` is 100% branch.

~~~text
BUILD SUCCESSFUL

com/ensemble/tagging/web/TaggingController       BRANCH 0/0     LINE 4/4
com/ensemble/tagging/dto/TagSuggestion           BRANCH 0/0     LINE 3/3
com/ensemble/tagging/VisionModelClient           BRANCH 0/0     LINE 0/0
com/ensemble/tagging/AnthropicVisionModelClient  BRANCH 3/4     LINE 46/49
com/ensemble/tagging/TaggingService              BRANCH 24/24   LINE 33/33
AGGREGATE tagging: LINE 86/89 = 96.6%   BRANCH 27/28 = 96.4%
~~~

The one uncovered branch is the `serialize()` JSON-error fallback in
`AnthropicVisionModelClient` (the SDK seam from task 1.0), not the mapping/fallback
logic the spec pins at 100% branch — `TaggingService` is fully branch-covered.

## Artifact: Secret scan green

**What it proves:** No Anthropic key is present anywhere in the tree.

**Why it matters:** Security Consideration — the key is env-only; the pre-commit
scan must stay green.

**Command:**

~~~bash
pre-commit run block-anthropic-keys --all-files
~~~

**Result summary:**

~~~text
Block committed Anthropic API keys.......................................Passed
~~~

## Artifact: README tag-preview section (5.1)

**What it proves:** A new developer can run the tag-preview slice.

**Why it matters:** Demoability — the endpoint, env var, and non-blocking fallback
are documented next to the existing wardrobe CRUD flow.

**Artifact path:** `README.md` → "Vision tagging (tag preview)".

**Result summary:** Documents `export ANTHROPIC_API_KEY=...`, a sample
`curl -F photo=@... /api/items/tag` with an example response, the manual-create
follow-up, and the note that a degraded call still returns an editable `200`.

## Pending: live end-to-end run (5.2–5.4) — requires the key

**Status:** NOT YET RUN. The live headline criterion (Success Metric 1) needs a
real `ANTHROPIC_API_KEY` and a running server + DynamoDB Local, which are not
available in this environment. Everything else in the slice is built and verified
without the key (per the spec's own note). To produce the sanitized transcript,
run:

~~~bash
export ANTHROPIC_API_KEY=...            # real key; redact it in the saved transcript
docker compose up -d dynamodb
./gradlew bootRun -PskipFrontend        # serves /api on :8080

# 5.2 — real photo -> suggested tags (200), nothing persisted
curl -s -X POST localhost:8080/api/items/tag -F photo=@garment.jpg | tee tag.json

# 5.3 — create the item from the (edited) suggested tags -> 201, then GET it
curl -s -X POST localhost:8080/api/items \
  -F photo=@garment.jpg -F category=... -F primaryColor=... \
  -F formality=... -F warmth=... | tee create.json
curl -s localhost:8080/api/items/<itemId>

# 5.4 — paste the sanitized (key-redacted) request/response transcript below
~~~

When run, append the captured transcript to this file with the key redacted, then
mark 5.2–5.4 `[x]` and 5.0 `[x]` in the task list.

## Reviewer Conclusion

The slice is code-complete and fully tested with the Claude client mocked: the
tag-preview flow is documented, the clean-build coverage gate is met (≥90% line;
100% branch on the critical mapping + fallback), and the secret scan is green. The
only outstanding item is the live, key-gated end-to-end run, which is documented
above with exact reproduction steps.
