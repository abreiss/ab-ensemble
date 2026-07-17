# Task 02 Proofs — Stateless multi-turn re-pick / pushback

## Task Summary

This task extends the stylist for pushback re-picks. `POST /api/style` now accepts an
optional ordered `history` of `{role, text}` turns, `StylistService` gains
`style(vibe, history)` (the old `style(vibe)` delegates with an empty list), the
conversation is seeded with the prior turns before the current vibe, and — when the
resent thread already carries a prior assistant turn — the SDK seam nudges the model to
produce a **different** look. The server stays **stateless** (the client resends the whole
thread each turn) and every turn is **text-only**. The grounding guardrail runs
**unchanged** on each pick.

## What This Task Proves

- A re-pick **forwards the full accumulated thread** (prior turns, then the newest vibe) to
  the model, in order.
- The **grounding guardrail is intact through the history path**: hallucinated ids are fed
  back and retried exactly once; only the valid subset is rendered; an ungroundable pick
  degrades gracefully.
- `style(vibe)` and `style(vibe, [])` are **identical** (backward compatible).
- An **empty wardrobe short-circuits** even with re-pick history, with no model call.
- **Repeated pushback** keeps every pick grounded and forwards the growing thread.
- The SDK seam adds a **"different look" instruction** only on a re-pick, and forwards
  **no image bytes**.
- The extended `POST /api/style` maps `history` → typed turns and preserves the graceful
  error mapping.
- The guardrail (`StylistService`) retains **100% branch coverage** through the new path.
- **End-to-end**, a second call with the prior pick + "too plain" returns a **different**
  set of owned item ids.

## Evidence Summary

- `StylistServiceTest` (+7 cases), `StyleControllerTest` (+2 cases, 3 stubs retargeted), and
  `AnthropicStylistModelClientTest` (+3 cases) all pass in the full backend suite
  (`BUILD SUCCESSFUL`).
- JaCoCo reports `StylistService` at **BRANCH 0 missed / 14 covered** and
  **LINE 0 missed / 48 covered**, and `StyleController` at **BRANCH 0 missed / 4 covered** →
  100% branch on the guardrail + controller mapping.
- A live two-call run against the running stack (DynamoDB Local, 14-item wardrobe) shows the
  first vibe and the follow-up pushback returning **disjoint** owned item sets.

## Artifact: Backend unit + controller + SDK-seam tests

**What it proves:** the history overload, the unchanged guardrail, the controller mapping,
and the byte-free re-pick nudge on the SDK seam.

**Why it matters:** this is the strict-TDD heart of the re-pick — grounded multi-turn
reasoning must be provably correct against a mocked client, with no key or network.

**Command:**

~~~bash
./gradlew test -PskipFrontend
~~~

**Result summary:** the full suite passes, including
`StylistServiceTest.style_withHistory_forwardsPriorTurnsToModel`,
`style_emptyHistory_matchesSingleTurn`,
`style_repick_staysGroundedWithHallucinatedIdRetriedOnce`,
`style_repick_rendersValidSubsetAfterRetry`, `style_repick_sendsNoImageBytes`,
`style_repick_emptyWardrobe_returnsFriendly`, `style_repeatedPushback_eachPickGrounded`;
`StyleControllerTest.postStyle_withHistory_returns200WithOutfit`,
`postStyle_withHistory_upstreamFailure_returnsGracefulError`; and
`AnthropicStylistModelClientTest.repickConversation_carriesDifferentLookInstruction`,
`firstTurnConversation_hasNoDifferentLookInstruction`,
`repickConversation_forwardsTextOnly_noImageBytes`.

~~~text
> Task :test
BUILD SUCCESSFUL in 16s
~~~

## Artifact: JaCoCo branch coverage on the guardrail through the history path

**What it proves:** the id-validation / one-retry / ungroundable branches remain fully
exercised after routing through `style(vibe, history)`.

**Why it matters:** the spec requires 100% branch on the grounding guardrail (critical
logic); the re-pick must not open an uncovered path.

**Command:**

~~~bash
./gradlew jacocoTestReport -PskipFrontend
# parsed build/reports/jacoco/test/jacocoTestReport.xml
~~~

**Result summary:** class-level counters —
`StylistService`: `BRANCH missed 0 / covered 14`, `LINE missed 0 / covered 48`;
`StyleController`: `BRANCH missed 0 / covered 4`, `LINE missed 0 / covered 15`.

~~~text
StylistService              BRANCH missed=0 covered=14  LINE missed=0 covered=48
StyleController             BRANCH missed=0 covered=4   LINE missed=0 covered=15
AnthropicStylistModelClient BRANCH missed=1 covered=25  LINE missed=2 covered=80
~~~

> The seam (`AnthropicStylistModelClient`) is SDK glue, not the critical guardrail; its one
> remaining branch (a defensive `serialize` failure path) predates this task and is outside
> the 100%-branch requirement, which targets the guardrail in `StylistService`.

## Artifact: Live end-to-end re-pick (two chained `/api/style` calls)

**What it proves:** the stateless re-pick works through the real HTTP + Claude + persistence
stack — resending the prior thread with a "too plain" pushback yields a **different** look
built only from owned items.

**Why it matters:** confirms the loop behaves end-to-end, not just under mocks. Run against a
fresh build on port 8081 (a stale dev server held 8080), sharing the same DynamoDB Local
14-item wardrobe. No test data was created or deleted. Reasons are trimmed for brevity; the
live key was supplied from the git-ignored `.env` and is not shown.

**Commands:**

~~~bash
# Turn 1 — initial vibe
curl -s -X POST localhost:8081/api/style \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"streetwear today"}'

# Turn 2 — resend prior thread as history + pushback (expect a different look)
curl -s -X POST localhost:8081/api/style \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"too plain, show me something bolder",
       "history":[{"role":"user","text":"streetwear today"},
                  {"role":"assistant","text":"Previously chose: <ids> — <reason>"}]}'
~~~

**Result summary:** turn 1 returned a 4-item look; turn 2 returned a **disjoint** 3-item
look, and its reason explicitly narrates the swap ("Swapped in the graphic Megadeth band tee
for a much bolder, high-contrast statement piece…"). Both picks are grounded (all ids exist
in the wardrobe).

~~~text
TURN 1  prompt='streetwear today'
  itemIds: [f52af804…, 392d1ed5…, 9bfc4421…, 2b1d8845…]
  reason : Vintage WWE graphic tee brings authentic streetwear energy, paired with
           relaxed-fit black cargo denim … chunky pl…

TURN 2  prompt='too plain, show me something bolder' (+history)
  itemIds: [cb061347…, 5df0620a…, 72cd4a7b…]
  reason : Swapped in the graphic Megadeth band tee for a much bolder, high-contrast
           statement piece — the black/orange print pops way more than a plain tee …

=== COMPARISON ===
  set(turn1): [2b1d8845…, 392d1ed5…, 9bfc4421…, f52af804…]
  set(turn2): [5df0620a…, 72cd4a7b…, cb061347…]
  identical set?  False
  DIFFERENT look? True
~~~

## Reviewer Conclusion

Stateless multi-turn re-pick is implemented end-to-end: the full thread is forwarded, the
grounding guardrail is preserved (100% branch on `StylistService`), the model is nudged to
vary the look on a pushback, no image bytes leave the seam, and a live run confirms a
"too plain" follow-up returns a different, fully-grounded outfit. No secrets or real photos
appear in this proof.
