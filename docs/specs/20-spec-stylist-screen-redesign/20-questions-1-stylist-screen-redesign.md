# 20 Questions Round 1 - Stylist-Screen Redesign

Please answer each question below (select one or more options, or add your own
notes). Feel free to add additional context under any question.

These questions resolve **material** ambiguity — each answer changes the scope,
the implementation path, or which TDD tier applies. Please answer all five
before I write the spec.

---

## 1. Per-garment rationale — where does the "reason per item" come from?

Today the backend emits **one `reason` string for the whole outfit**
(`Outfit` / `StyleResponse`); `OutfitItem` carries only `itemId` + `photoUrl` —
no per-item text. "Per-garment rationale" in the issue could mean two very
different things:

- [ ] (A) **Frontend-only presentation.** No new AI output. Re-present the
      single existing outfit `reason` visually near the flat-lay (e.g., one
      rationale block per look), no per-item text. Smallest slice; **no
      backend/LLM change**; stays a pure frontend spec.
- [ ] (B) **Real per-item reasons from the LLM.** Extend the forced-output
      schema to a short note per item, update `OutfitParser`, the grounding
      guardrail, `StyleResponse`/`OutfitItem` DTOs, and the Sonnet prompt. This
      is **backend domain logic under strict TDD** (100% branch on
      forced-output parsing + id-grounding) and materially enlarges the issue.
- [ ] (C) **Split.** Ship the visual redesign now as a **frontend-only** spec
      (option A behavior), and file per-item AI rationale (option B) as its own
      backend issue/spec.
- [ ] (D) Other (describe)

**Recommended answer(s):** [(C), or (A) if you don't want a follow-up issue]

**Why these are recommended:**

- Option (B) crosses into the AI guardrails (forced output + grounding), which
  AGENTS.md holds to strict TDD and 100% branch coverage — bundling it with a
  visual redesign makes one oversized, mixed-tier spec that's hard to validate.
- (C) keeps this spec a clean, demoable frontend slice against final maroon
  colors while preserving the per-item-AI idea as a properly-scoped backend
  spec. (A) is the same frontend scope if you'd rather not open a second issue.
- Pick (B) only if per-item AI text is a hard requirement for this deliverable
  and you accept the backend scope in this one spec.

## 2. Design source — token values and the drawer/tray/pip/flat-lay layout

The `design_handoff_stylist_maroon/` folder referenced by spec 06 is **not in
the repo** and was never committed (spec 06 audit FLAG-2). So the six deferred
token **values** (`--paper-sunk`, `--border`, `--ink-2`, `--placeholder`,
`--pip-empty`, `--accent-line`) and the exact visual layout (what "drawer",
"tray", "pips", "flat-lay spec-sheet" look like) are unknown. How should I
source them?

- [ ] (A) **You provide the handoff.** Drop `design_handoff_stylist_maroon/`
      into the repo (or paste `theme-tokens.css` + the layout mockups) and I
      spec exactly to it.
- [ ] (B) **I derive them.** I propose concrete token values consistent with the
      existing maroon palette and a described layout for each element; you
      review in the spec before implementation.
- [ ] (C) **Tokens only, layout at implementer discretion** within WCAG contrast
      and existing patterns.
- [ ] (D) Other (describe)

**Current best-practice context:** New CSS custom properties should be defined
once in `:root` where first used, and any text/background pairing must meet WCAG
2.2 AA contrast (4.5:1 body, 3:1 large) — this constrains derived values in (B).

**Recommended answer(s):** [(A) if the handoff exists; otherwise (B)]

**Why these are recommended:**

- (A) gives pixel/hex fidelity to the intended design and removes all guessing —
  best if the handoff still exists anywhere.
- (B) keeps the project moving with reviewable, WCAG-checked values when the
  handoff is truly gone; you still approve every value in the spec.
- (C) risks an inconsistent result and weak proof artifacts, since "looks right"
  can't be validated against a fixed target.

## 3. Routing changes — what actually changes about routes?

Today `/style` is a **single route** holding the vibe form and the result inline
(`App.tsx`). What does "routing changes" mean?

- [ ] (A) **No route change.** Redesign is layout-only inside the existing
      `/style` route.
- [ ] (B) **Split entry vs. result** (e.g., `/style` for vibe entry →
      `/style/result` for the look), with browser back/deep-link behavior
      defined.
- [ ] (C) **Change the app entry** so the stylist is the landing screen instead
      of the wardrobe grid (`/`).
- [ ] (D) Other (describe)

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- (A) is the least disruptive, keeps the existing multi-turn state model intact
  (history lives in one component), and avoids new deep-link/back-button edge
  cases that (B) introduces and would need their own tests.
- Choose (B) or (C) only if the redesign genuinely requires a navigation change;
  if so, describe the exact routes and the back/deep-link behavior you expect.

## 4. Chat UI — full transcript or conversational single-card?

The app already accumulates a conversation `history` but renders only the
**latest** look. "Chat UI" could mean:

- [ ] (A) **Full transcript.** Render prior turns (vibe, each pick, each
      pushback) as a scrollable chat log of bubbles/cards.
- [ ] (B) **Conversational single-card.** Keep showing the current look, style
      the vibe/pushback exchange to feel conversational (chips + rationale), but
      no full scrollback history.
- [ ] (C) Other (describe)

**Recommended answer(s):** [(B)]

**Why these are recommended:**

- (B) matches the current data flow (one active look; history is resent to the
  server, not displayed) and keeps the redesign focused and demoable.
- (A) adds transcript rendering, keying, and scroll management — real extra
  scope and tests — worth it only if you specifically want to see the whole
  conversation on screen.

## 5. Pushback chips — which canned chips, and what does each do?

"Pushback chips" = one-tap canned feedback that fires a re-pick turn (vs. today's
free-text pushback box). What set and behavior?

- [ ] (A) **Proposed default set**, each firing a re-pick with its text, keeping
      the free-text box too: `Too plain`, `Too formal`, `Too casual`, `Warmer`,
      `Show me another`.
- [ ] (B) A **different set** (list them).
- [ ] (C) **Chips replace** the free-text pushback box entirely.
- [ ] (D) Other (describe)

**Recommended answer(s):** [(A)]

**Why these are recommended:**

- (A) reuses the existing re-pick turn mechanism (each chip = a canned
  `newestUserText`), so it's low-risk and testable, while free-text stays for
  anything the chips don't cover.
- (C) removes an existing capability (arbitrary pushback) — only choose it if
  you deliberately want to constrain feedback to the canned set.
- Edit the (A) list freely; the exact chip labels become spec requirements and
  proof artifacts.

---

**How to proceed:** answer inline above, save the file, and tell me it's ready.
I'll re-run the clarification check and write the spec.
