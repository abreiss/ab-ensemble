# Task 01 Proofs — Claude client seam + Anthropic SDK + typed config

## Task Summary

This task adds Ensemble's first AI integration: the Anthropic Java SDK, a
Claude client bean built from the environment, a typed non-secret config record,
and a **narrow, mockable seam** (`VisionModelClient`) that hides all SDK types
behind a single `byte[] → JSON` method. It is the seam the vision `TaggingService`
(task 3.0) and, later, the stylist (issue #6) build on.

## What This Task Proves

- The `com.anthropic:anthropic-java` dependency resolves on the backend classpath.
- Vision tagging is **pinned to Haiku 4.5** via typed config, with the API key
  read from the environment (never a config property, never committed).
- The seam builds a **forced-structured-output** vision request targeting
  `claude-haiku-4-5`, carrying the image, and extracts the tool-use JSON — all
  verified against a **mocked** SDK client (no key, no network call).
- The SDK is reachable only through the seam boundary (two files), so no
  controller/service depends on SDK types directly.
- The Spring context still starts with **no `ANTHROPIC_API_KEY`** (lazy client bean).

## Evidence Summary

- The dependency tree shows `anthropic-java:2.48.0` resolved.
- 9 tests pass (6 config + 3 seam), all with the client mocked.
- SDK imports appear in exactly two files; the `sk-ant-` secret scan is clean.

## Artifact: Anthropic SDK dependency resolves

**What it proves:** The SDK is on the runtime classpath at a pinned version.

**Why it matters:** Every later tagging behavior depends on this dependency
being present and reproducible.

**Command:** `./gradlew dependencies --configuration runtimeClasspath -PskipFrontend`

**Result summary:** `com.anthropic:anthropic-java:2.48.0` resolves with its
okhttp + core modules.

```text
\--- com.anthropic:anthropic-java:2.48.0
     +--- com.anthropic:anthropic-java-client-okhttp:2.48.0
     |    +--- com.anthropic:anthropic-java-core:2.48.0
```

## Artifact: Config + seam tests pass with the client mocked

**What it proves:** The model is pinned to `claude-haiku-4-5`, the timeout
defaults are bounded, and the seam builds the correct forced-JSON vision request
and extracts tool-use output — without any live call.

**Why it matters:** This is the mockable testing pattern `docs/TESTING.md`
requires and that the stylist will reuse; it must work with no key.

**Command:** `./gradlew test -PskipFrontend --tests 'com.ensemble.config.AnthropicPropertiesTest' --tests 'com.ensemble.tagging.AnthropicVisionModelClientTest'`

**Result summary:** 9 tests, 0 failures.

```text
AnthropicVisionModelClientTest -> 3 tests, 0 failed
AnthropicPropertiesTest        -> 6 tests, 0 failed
TOTAL: 9
```

Key seam assertions (`AnthropicVisionModelClientTest`): the captured request has
`params.model().asString() == "claude-haiku-4-5"`, a forced `tool_choice`
naming `extract_garment_tags`, and an image content block in the user message.

## Artifact: SDK import boundary + secret hygiene

**What it proves:** The Anthropic SDK is imported only inside the config bean and
the seam impl; no key string is present anywhere.

**Why it matters:** Enforces the "reach Claude only through the mockable seam"
standard (spec Success Metric 5) and the no-secrets rule.

**Command:** `grep -rl "com.anthropic" src/main/java` and `grep -rn "sk-ant-" src build.gradle`

**Result summary:** SDK imports in exactly two files; secret scan clean.

```text
src/main/java/com/ensemble/config/AnthropicConfig.java
src/main/java/com/ensemble/tagging/AnthropicVisionModelClient.java

sk-ant- scan: clean (no matches)
```

## Artifact: Context starts with no API key (lazy client)

**What it proves:** `@SpringBootTest` (`EnsembleApplicationTests.contextLoads`)
starts the full context with `ANTHROPIC_API_KEY` unset.

**Why it matters:** CI and the test suite must run with no key and make no
external call; the `@Lazy` client bean guarantees construction is deferred to a
real tag request.

**Command:** `unset ANTHROPIC_API_KEY; ./gradlew test -PskipFrontend --tests 'com.ensemble.EnsembleApplicationTests'`

**Result summary:** Context loads successfully with no key set.

## Reviewer Conclusion

The Claude client seam, SDK dependency, and typed Haiku-4.5 config are in place
and fully exercised with the client mocked. The seam boundary holds, no secret is
present, and the context runs keyless — the foundation the tagging service builds
on is proven.
