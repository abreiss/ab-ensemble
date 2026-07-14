# Task 05 Proofs — End-to-end proof + docs + coverage gate

## Task Summary

This task closes the slice: it documents the tag-preview flow for a new developer,
re-verifies the full coverage gate on a clean build, confirms secret hygiene, and
captures the **live** end-to-end run — a real garment photo auto-tagged by Claude
Haiku 4.5, whose tags create a persisted wardrobe item.

## What This Task Proves

- `README.md` documents the tag-preview flow: the `.env` key setup, a sample `curl`
  to `POST /api/items/tag`, and the note that a failed/degraded call still returns
  an editable `200` (5.1).
- A clean `./gradlew clean test jacocoTestReport -PskipFrontend` is green with the
  Claude client **mocked** — no key, no network call in the suite (5.5).
- The tagging package meets the coverage bar: **≥90% line** overall and **100%
  branch** on the critical mapping + fallback logic (5.5).
- The `block-anthropic-keys` secret scan is green — no key in code, config, tests,
  or proofs (5.5).
- **Live:** a real photo posted to `POST /api/items/tag` returns valid structured
  tags, which then create an item via `POST /api/items` (`201` + `GET`) — the
  headline acceptance criterion, against the real model (5.2–5.4).

## Evidence Summary

- Clean build: `BUILD SUCCESSFUL` for `clean test jacocoTestReport -PskipFrontend`.
- Coverage: tagging package LINE 86/89 = **96.6%**; `TaggingService` (mapping +
  fallback) BRANCH 24/24 = **100%**, LINE 33/33 = 100%.
- Secret scan: `Block committed Anthropic API keys ... Passed`.
- Live run: real Megadeth-tee photo → `T-Shirt/Black/Orange, formality 1, warmth 3,
  Graphic Print` + descriptors → item `cb061347-…` created (`201`), photo stored as
  640×800 JPEG. Key sourced only from git-ignored `.env`, never in the transcript.

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

## Artifact: Live end-to-end run — real photo → tags → item (5.2–5.4)

**What it proves:** The headline acceptance criterion (Success Metric 1) works
against the **real** Claude Haiku 4.5 model: a genuine garment photo is auto-tagged
over HTTP and those tags create a persisted wardrobe item.

**Why it matters:** This is the one step the mocked suite cannot cover — it
exercises the real vision call, forced-JSON parsing, and the create→persist→read
round-trip together.

**Setup (key supplied via git-ignored `.env`, never printed):**

~~~bash
# .env holds ENSEMBLE_ANTHROPIC_API_KEY=<redacted>   (git-ignored, loaded on startup)
docker compose up -d dynamodb
./gradlew bootRun -PskipFrontend        # serves /api on :8080; reads .env; table auto-created
~~~

Input photo: a Megadeth band tee, `~/Downloads/megadeth.jpg`, JPEG 4061×5076
(≈5.4 MB) — downsized to ≤800px before the vision call.

**5.2 — tag preview (live Claude Haiku 4.5, nothing persisted):**

~~~bash
curl -s -X POST localhost:8080/api/items/tag -F photo=@~/Downloads/megadeth.jpg
~~~

~~~json
{"category":"T-Shirt","primaryColor":"Black","secondaryColor":"Orange",
 "formality":1,"pattern":"Graphic Print","warmth":3,
 "descriptors":["Band Tee","Megadeth","Metal","Vintage","Oversized Fit",
 "Short Sleeve","Crew Neck"]}
~~~

The model returned well-formed structured JSON mapping onto all six scalar tags +
`descriptors`, with `formality` (1) and `warmth` (3) already in range.

**5.3 — create the item from the (lightly-edited) tags → `201`, then `GET`:**

~~~bash
curl -s -D- -X POST localhost:8080/api/items \
  -F photo=@~/Downloads/megadeth.jpg \
  -F category=T-Shirt -F primaryColor=Black -F secondaryColor=Orange \
  -F formality=1 -F warmth=3 -F pattern="Graphic Print" \
  -F descriptors=Band-Tee -F descriptors=Megadeth -F descriptors=Metal
~~~

~~~text
HTTP/1.1 201
Location: /api/items/cb061347-40e2-416b-9c99-960c1b3e863d
~~~

~~~json
{"itemId":"cb061347-40e2-416b-9c99-960c1b3e863d","category":"T-Shirt",
 "primaryColor":"Black","secondaryColor":"Orange","formality":1,
 "pattern":"Graphic Print","warmth":3,"descriptors":["Band-Tee","Megadeth","Metal"],
 "photoUrl":"/api/items/cb061347-40e2-416b-9c99-960c1b3e863d/photo",
 "createdAt":"2026-07-14T22:39:58.593960Z","lastWorn":null,"wornCount":0}
~~~

A follow-up `GET /api/items/cb061347-…` returned the same persisted record, and
`GET /api/items/cb061347-…/photo` returned `200 image/jpeg`, **640×800** (the
original 4061×5076 downsized to the ≤800px longest edge), 30,979 bytes.

**Result summary:** Real photo → valid structured tags → persisted item, verified
end to end. **API key redacted** — the key lives only in the git-ignored `.env`
and never appears in any request, response, or this transcript.

## Reviewer Conclusion

The slice is complete and proven both ways: the mocked suite covers every FR with
100% branch on the critical mapping + fallback, and the live run above demonstrates
the headline criterion against the real Haiku 4.5 model — a garment photo becomes
structured tags that create a persisted wardrobe item, with the key sourced only
from a git-ignored `.env`.
