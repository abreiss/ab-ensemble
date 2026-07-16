# Tagging model eval — Haiku 4.5 vs Sonnet 5

An offline, opt-in experiment that answers one question: **does Sonnet 5 tag garment photos
better than Haiku 4.5, and is it worth the extra cost?** It runs both models over your own
labeled photos through the *exact* production request path and reports three axes side by side:

1. **Accuracy** — parsed tags vs your gold labels, per field.
2. **Quality** — Opus 4.8 judges the two tag sets blind, per image (+ a human spot-check section).
3. **Cost & latency** — token usage × price, wall-clock per call.

The harness lives under `src/test/java/com/ensemble/eval/` and is driven by the `taggingEval`
Gradle task. It is **never** part of `./gradlew test` and is **never** packaged in the jar, so it
can only make (paid) API calls when you run it by hand.

## 1. Add your photos

Drop ~12–20 garment photos into `eval/tagging/images/` (git-ignored). One garment per photo.

## 2. Write the gold labels

Copy `gold.example.json` to `gold.json` (git-ignored) and fill one entry per photo. Fields mirror
`TagSuggestion`:

| field | type | notes |
|---|---|---|
| `image` | string | filename under `images/` |
| `category` | string | e.g. top / bottom / outerwear |
| `primaryColor`, `secondaryColor` | string / null | `null` when there isn't one |
| `formality` | 1–5 | |
| `pattern` | string | e.g. solid / striped / graphic |
| `warmth` | 1–3 | |
| `descriptors` | string[] | free-form tags |

Scoring is normalized (case- and whitespace-insensitive); a `null` in gold means "undetermined"
and matches a `null` prediction. Formality/warmth are scored both exact and within ±1. Descriptors
are scored by set F1.

## 3. Run it

```bash
export ENSEMBLE_ANTHROPIC_API_KEY=sk-ant-...   # or ANTHROPIC_API_KEY
./gradlew taggingEval -PskipFrontend --args="--judge"
```

- `-PskipFrontend` skips the unrelated Vite build.
- Drop `--judge` to skip the Opus judge (accuracy + cost only, cheaper).
- Flags: `--images DIR` `--gold FILE` `--out DIR` `--judge` (all optional; defaults shown below).

Defaults: `--images eval/tagging/images`, `--gold eval/tagging/gold.json`, `--out eval/tagging/out`.

A ~15-image run (both models + judge) costs **well under $1**.

## 4. Read the report

Written to `eval/tagging/out/` (git-ignored): `report-<date>.md` (summary table + per-image
side-by-side + a human spot-check table to fill in) and `results-<date>.json` (raw data).

## Fairness controls (why the comparison is valid)

- Both models get the **same ≤800px JPEG** (production `ImageProcessor`) — identical input.
- Both use the **same forced-tool request** (`AnthropicVisionModelClient.tagRequestBuilder`) and
  the **same** parse/clamp (`TaggingService.map`). Only the model id (and Sonnet's disabled
  thinking) differ.
- Sonnet 5 runs with **thinking disabled** so it's a single-shot perception call like Haiku.
- The judge sees the two sets **blind**, with A/B order randomized per image.

## Later arms (if the result is close)

- Sonnet 5 **with** thinking on.
- **Full-res** images (up to Sonnet's 2576px) instead of the 800px production cap.
- **Repeat-N** per image for self-consistency / variance bars.
