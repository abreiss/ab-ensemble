# 06-spec-maroon-recolor.md

> GitHub issue: _to be filled in once created_

## Introduction/Overview

Ensemble's UI is driven by a small set of CSS custom properties ("design
tokens") defined once in `frontend/src/index.css` under `:root`. Today the
palette is cobalt-on-cream ("Care Label"). A design handoff
(`design_handoff_stylist_maroon/`) specifies a **maroon-and-beige** repalette of
that same token system. Because every button, chip, input, card, and grid
already reads from the tokens, swapping the token values recolors the whole app
without touching component markup.

**Primary goal:** replace the cobalt palette with the maroon/beige palette by
editing tokens only, so all existing screens (`/`, `/add`, `/item/:id`,
`/style`) render in the new colors with no behavioral change and all tests still
green.

This spec is deliberately narrow. It adds **only the palette tokens the current
app needs** — the 7 replaced tokens plus `--on-accent` (used to tokenize the one
hard-coded button color). The six other tokens from the handoff
(`--paper-sunk`, `--border`, `--ink-2`, `--placeholder`, `--pip-empty`,
`--accent-line`) are only consumed by the upcoming stylist-screen redesign and
are **deferred to issue #7**.

## Goals

- Replace the 7 existing color tokens in `:root` with the handoff's maroon/beige
  values, exactly as specified.
- Add `--on-accent` and use it to tokenize the single hard-coded color
  (`color: #fff` on filled buttons).
- Update the browser `theme-color` meta tag to the new paper color.
- Change zero component behavior: every existing frontend test still passes, and
  every screen keeps its current layout and interactions.
- Leave the six stylist-only tokens for issue #7 (explicit non-goal).

## User Stories

- **As a user of Ensemble**, I want the app to present a consistent, intentional
  maroon-and-beige look so that it feels like one finished product rather than a
  default template.
- **As the developer building the stylist-screen redesign (issue #7)**, I want
  the maroon palette already applied so that I build that screen directly against
  final base colors and only add the drawer/tray/pip tokens I actually use.
- **As a reviewer**, I want the recolor to be a self-contained token change so
  that I can verify it quickly and separately from any feature work.

## Demoable Units of Work

### Unit 1: Repalette the design system

**Purpose:** Repalette every existing screen from cobalt to maroon/beige by
editing the 7 base tokens and the `theme-color` meta, with no change to behavior
or layout.

**Functional Requirements:**
- The system shall replace these 7 `:root` tokens in `frontend/src/index.css`
  with the handoff values: `--paper` → `#f3ecdd`, `--paper-raised` → `#fcf8ef`,
  `--ink` → `#33271f`, `--muted` → `#8f8272`, `--hairline` → `#e3d8c4`,
  `--accent` → `#7c2833`, `--accent-soft` → `#ecd9d3`.
- The system shall leave `--danger`, `--danger-soft`, and all non-color tokens
  (fonts, spacing, radii) unchanged.
- The system shall update `frontend/index.html` `theme-color` meta from
  `#F7F5F0` to `#F3ECDD`.
- The system shall not modify any component markup, class names, or behavior.

**Proof Artifacts:**
- Screenshots: `/`, `/add`, `/item/:id`, and `/style` captured from the Vite dev
  server demonstrate the maroon/beige palette applied across all existing
  screens. (Without the backend running, data-driven screens show empty/error
  states — the palette is still visible on chrome, buttons, and banners.)
- CLI: `grep -nE "#2540ff|#e7ebff|#f7f5f0|#fffefb|#1c1b19|#8a857b|#e0dcd2"
  frontend/src/index.css` returning no matches demonstrates no cobalt values
  remain.

### Unit 2: Tokenize the on-accent color

**Purpose:** Remove the one hard-coded color so text on maroon fills follows a
palette token, and register `--on-accent` for reuse.

**Functional Requirements:**
- The system shall add `--on-accent: #f6ecd9;` to `:root` (with a short purpose
  comment matching the handoff's `theme-tokens.css`).
- The system shall change `frontend/src/index.css` (the `.btn-add, .btn-primary`
  rule, ~line 169) from `color: #fff` to `color: var(--on-accent)`.
- The system shall not add the other six handoff tokens (deferred to issue #7).

**Proof Artifacts:**
- CLI: `grep -n "#fff" frontend/src/index.css` returning no bare-white match, and
  `grep -n "\-\-on-accent" frontend/src/index.css` showing the definition + the
  button usage, demonstrates the color was tokenized.
- CLI: `cd frontend && npm test -- --run` passing demonstrates neither unit
  changed behavior (no test asserts a color, so all stay green).

## Non-Goals (Out of Scope)

1. **The six stylist-only tokens** (`--paper-sunk`, `--border`, `--ink-2`,
   `--placeholder`, `--pip-empty`, `--accent-line`): defined in issue #7 where
   they are first used, not here.
2. **The stylist-screen redesign**: chat UI, flat-lay spec-sheet, pushback chips,
   per-garment rationale, and routing changes are **issue #7**.
3. **Any backend change**: this is frontend CSS/HTML only.
4. **New components or markup changes**: no `.tsx` files change.
5. **Dark mode or theme switching**: single fixed palette only.
6. **Restyling the existing stylist card** (`.outfit-*` rules): it recolors for
   free via tokens; its structure is left for issue #7.

## Design Considerations

Base palette applied in this spec:

| Token | Value | Role | In scope |
| --- | --- | --- | --- |
| `--paper` | `#f3ecdd` | page background | ✅ Unit 1 |
| `--paper-raised` | `#fcf8ef` | cards / inputs | ✅ Unit 1 |
| `--ink` | `#33271f` | primary text | ✅ Unit 1 |
| `--muted` | `#8f8272` | muted labels | ✅ Unit 1 |
| `--hairline` | `#e3d8c4` | subtle borders | ✅ Unit 1 |
| `--accent` | `#7c2833` | maroon fills / large text / icons | ✅ Unit 1 |
| `--accent-soft` | `#ecd9d3` | soft accent backgrounds | ✅ Unit 1 |
| `--on-accent` | `#f6ecd9` | cream text/icons on maroon fill | ✅ Unit 2 |
| `--paper-sunk`, `--border`, `--ink-2`, `--placeholder`, `--pip-empty`, `--accent-line` | (handoff values) | drawer / tray / pips / rationale (stylist screen) | ⏳ issue #7 |

**Contrast rule (unchanged intent from the current system):** maroon `#7c2833`
is used for fills, large text, and icons — never for small body text. Small body
text keeps `--ink`. Cream `--on-accent` on a maroon fill is a high-contrast
light-on-dark pairing (comfortably meets WCAG AA for the button labels it is used
on). The existing `:focus-visible` outline uses `--accent`, which is fine.

A ready-to-paste `:root` block is in the handoff's `theme-tokens.css` (this spec
uses only the 7 replacements + `--on-accent` from it).

## Repository Standards

- Tokens live in a single `:root` block in `frontend/src/index.css`; there is no
  CSS-module split. Edit that block in place.
- The repo is mobile-first React 19 + Vite; per `docs/TESTING.md`, frontend view
  plumbing is lightly tested — a pure recolor needs no new tests, only that the
  existing suite stays green.
- Conventional commits (e.g. `feat: maroon/beige theme (token recolor)`).
- Work on the `feat/maroon-recolor` branch (already created off `main`); PR
  closes the recolor GitHub issue.

## Technical Considerations

- **Token-driven blast radius:** ~53 color-token references and several
  `color-mix()`-derived shades recolor automatically; no `.tsx`/`.ts` file
  contains a hard-coded hex. The only non-token color in CSS is the single
  `color: #fff` at line ~169, addressed in Unit 2.
- **Latest-standards research:** none required. The color values are fixed by the
  handoff, and WCAG contrast is a stable standard whose relevant rule (maroon for
  fills/large text only, ink for body) is captured above.
- The `theme-color` meta in `index.html` duplicates the paper color and must be
  edited separately from the CSS (it is not a CSS variable).

## Security Considerations

No specific security considerations identified. This change is cosmetic CSS/HTML
only — no secrets, credentials, network calls, data handling, or authorization
are involved. Screenshots used as proof artifacts show only UI chrome and empty
or sample states; no sensitive data.

## Success Metrics

1. **Palette applied:** all four existing screens (`/`, `/add`, `/item/:id`,
   `/style`) render in maroon/beige — verified by screenshots.
2. **No stale colors:** grep finds zero occurrences of the 7 old cobalt/cream hex
   values and no bare `#fff` in `frontend/src/index.css`.
3. **Correct tokens present:** the 7 replaced tokens + `--on-accent` exist in
   `:root`; the six stylist-only tokens are absent (they belong to #7).
4. **Behavior unchanged:** `npm test -- --run` passes with no test modifications.
5. **Contrast:** button cream-on-maroon and body ink-on-paper meet WCAG AA.

## Open Questions

No open questions at this time. The token-scope decision was resolved with the
user: this spec adds only the 7 replaced tokens + `--on-accent`, and defers the
six stylist-only tokens to issue #7.
