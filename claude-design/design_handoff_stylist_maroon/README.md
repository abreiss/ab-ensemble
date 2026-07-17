# Handoff: Ensemble Stylist screen — "Spec sheet" style, maroon & beige

## Overview
This package specifies a new **AI-stylist home screen** for Ensemble and a **maroon-and-beige recolor** of the existing "Care Label" design system. The screen is where the user asks for an outfit in a chat, and the assistant returns a look built from their own wardrobe — shown as a flat-lay with a numbered "spec sheet" beside it (color swatch, formality/warmth pips, and a one-line rationale per piece).

This is the feature the repo README already promises ("give it a vibe → it builds an outfit from what you own, explains why, and re-picks when you push back") but that isn't built yet. The current app only has the wardrobe grid (`/`), add (`/add`), and item detail (`/item/:id`).

## About the design files
`Ensemble Stylist.dc.html` (open it in a browser) is a **design reference**, not production code. It's a static HTML prototype showing the intended look. Option **2a** in that file is the target. Your job is to **recreate 2a inside the existing `frontend/` React app** using its patterns (React 19 + Vite, CSS variables in `frontend/src/index.css`, `react-router-dom`, the `Item` type and `/api/items` client) — not to copy the HTML in.

Two things in the prototype are placeholders and should be replaced by real app data:
- **Garment silhouettes** (the little SVG shirts/pants) stand in for **real photos**. In the app, each tile is an `<img src={photoUrl(item.itemId)}>` with `object-fit: cover`.
- **The wardrobe contents and the outfit** are hard-coded sample data. In the app they come from `/api/items` and a new outfit endpoint (see "Backend gap").

## Fidelity
**High-fidelity.** Colors, typography, spacing, and radii below are final — match them exactly. The recolor is a pure token swap; the new screen is new UI built to the spec here.

## Git: work on a branch
```bash
git checkout -b feat/stylist-screen-maroon
# ...implement...
git add -A && git commit -m "feat: AI stylist screen + maroon/beige theme"
git push -u origin feat/stylist-screen-maroon
```
Keep the recolor and the new screen in the same branch, or split into two commits (theme first, screen second) so the token change is easy to review on its own.

---

## Part 1 — Recolor the design system (fast, low-risk)

The app is already token-driven in `frontend/src/index.css` under `:root`. Swap these values. Everything that already uses the tokens (buttons, chips, inputs, cards, grid) recolors for free.

### Replace these existing tokens
| Token | Old (cobalt) | New (maroon/beige) |
|---|---|---|
| `--paper` | `#f7f5f0` | `#f3ecdd` |
| `--paper-raised` | `#fffefb` | `#fcf8ef` |
| `--ink` | `#1c1b19` | `#33271f` |
| `--muted` | `#8a857b` | `#8f8272` |
| `--hairline` | `#e0dcd2` | `#e3d8c4` |
| `--accent` | `#2540ff` | `#7c2833` |
| `--accent-soft` | `#e7ebff` | `#ecd9d3` |

### Add these new tokens (the screen needs them)
```css
--paper-sunk: #e9dfca;   /* drawer / flat-lay tray background (a step darker than paper) */
--border:     #d7cab2;   /* stronger border than --hairline: inputs, tiles, buttons */
--ink-2:      #574a3d;   /* secondary body text / rationale copy */
--placeholder:#a89a86;   /* input placeholder + search hint */
--on-accent:  #f6ecd9;   /* cream text/icons on a maroon fill */
--pip-empty:  #d8cbb2;   /* unfilled rating pip */
--accent-line: rgba(124,40,51,.22); /* border on the user chat bubble */
```

Also update the theme-color meta in `frontend/index.html`: `content="#F7F5F0"` → `content="#F3ECDD"`.

A ready-to-paste `:root` block is in `theme-tokens.css` in this folder.

> Note on the accent: maroon `#7c2833` on white/beige passes contrast for large text and UI fills, but for small body text keep using `--ink`/`--ink-2`, never maroon — same rule the current system has for green-on-white.

---

## Part 2 — The Stylist screen

### Routing
Make the stylist the landing screen. Suggested routes in `App.tsx`:
- `/` → **StylistHome** (new)
- `/wardrobe` → the existing `WardrobeGrid`
- `/add`, `/item/:id` → unchanged

The persistent header (`Ensemble` wordmark left, `+ Add` pill right) stays; add a link to `/wardrobe`.

### Layout (desktop, ≥900px)
A single full-height row, no outer card/shadow (the shadow in the prototype is only to float the mock — the real app is full-viewport):
```
┌───────────── header (wordmark · + Add) ─────────────┐
├──────────────┬──────────────────────────────────────┤
│ Wardrobe     │  Main (chat context + result + input) │
│ drawer       │                                       │
│ 250px, fixed │  flex: 1                              │
│ bg --paper-  │  padding 24px 28px                    │
│ sunk         │                                       │
└──────────────┴──────────────────────────────────────┘
```
- Drawer: `width:250px; flex:none; background:var(--paper-sunk); border-right:1px solid var(--hairline); padding:20px`.
- Main: `flex:1; display:flex; flex-direction:column; gap:16px; padding:24px 28px`.

### Responsive (<900px)
Stack: chat/result becomes the full-width column; the wardrobe drawer collapses to a bottom sheet or a toggle ("Wardrobe" button in the header). The existing app is mobile-first at 30rem — preserve that. Touch targets stay ≥44px (the existing `--space`/button rules already enforce this).

### Components to build

**1. `WardrobeDrawer`**
- Heading `Wardrobe` (Bricolage 700, 15px, `-0.02em`).
- Search input: `background:var(--paper-raised); border:1px solid var(--border); border-radius:8px; padding:8px 11px`; placeholder color `--placeholder`; a `⌕` glyph before it (or a Lucide `search` icon at 13px).
- 2-column grid of thumbnails, `gap:8px`. Each tile: `aspect-ratio:3/4; background:var(--paper-raised); border:1px solid var(--hairline); border-radius:9px; overflow:hidden`, containing `<img src={photoUrl(id)} style="width:100%;height:100%;object-fit:cover">`.
- Tiles that are **in the current outfit** get `border:1.5px solid var(--accent)`.
- Data: `listItems()` from `frontend/src/api/items.ts`.

**2. `StylistChat`** (message stream + composer)
- **Assistant bubble:** `background:var(--paper-raised); border:1px solid var(--hairline); border-radius:14px; border-top-left-radius:4px; padding:13px 16px; font-size:14.5px; line-height:1.55`. Left of it, a 28px maroon avatar square (`background:var(--accent); color:var(--on-accent); border-radius:8px`) with an `E`.
- **User bubble:** right-aligned, `background:var(--accent-soft); border:1px solid var(--accent-line); border-radius:14px; border-top-right-radius:4px; padding:13px 16px; color:var(--ink)`.
- **Quick-start chips** under the first assistant message: pills, `font:Space Mono 12px; padding:6px 12px; border:1px solid var(--border); border-radius:999px; background:var(--paper-raised); color:var(--ink-2)`. Copy: `Brunch`, `Interview`, `Date night`, `What goes with these loafers?`.
- **Composer** (sticky bottom): `display:flex; align-items:center; gap:10px; background:var(--paper-raised); border:1px solid var(--border); border-radius:999px; padding:7px 8px 7px 18px`; placeholder "Ask for a change, or a whole new look…"; send button 38px circle `background:var(--accent); color:var(--on-accent)` with `↑` (or Lucide `arrow-up`).

**3. `OutfitResult`** — two columns, `gap:22px`.

*Left — flat-lay tray* (`flex:none; width:340px`):
- Eyebrow `THE LOOK · FLAT-LAY` (Space Mono 10px, uppercase, `letter-spacing:.14em`, `color:var(--muted)`).
- Tray: `background:var(--paper-sunk); border:1px solid var(--hairline); border-radius:14px; padding:16px; display:grid; grid-template-columns:1fr 1fr; gap:12px`.
- Each piece tile: `position:relative; aspect-ratio:1; background:var(--paper-raised); border:1px solid var(--hairline); border-radius:11px`, holding the item photo (cover). Top-left number badge: 20px circle `background:var(--accent); color:var(--on-accent); font:Space Mono 11px/700`, value = its index in the look (1–N).
- Actions row (`margin-top:14px; gap:8px`): primary `Wear today` button (`flex:1; height:40px; background:var(--accent); color:var(--on-accent); border-radius:999px`) + a 44×40 outline heart button (`border:1px solid var(--border); background:var(--paper-raised); color:var(--accent)`).

*Right — spec list* (`flex:1; display:flex; flex-direction:column; gap:11px`). One card per piece:
- Card: `border:1px solid var(--hairline); background:var(--paper-raised); border-radius:12px; padding:13px 15px`.
- Header row: 19px maroon number circle (`background:var(--accent); color:var(--on-accent)`), item name (Bricolage 700, 15px, `-0.01em`, `color:var(--ink)`), then the category slot label right-aligned (Space Mono 11px, `color:var(--muted)`) — `TOP` / `BOTTOM` / `SHOES` / `CARRY`.
- Attribute row (below, separated by a `1px dashed var(--hairline)`): a 14px color swatch (`border-radius:4px; border:1px solid var(--border)`, fill = the item's `primaryColor`) + its color name; then `FORM` pips and `WARM` pips.
- Rationale: a maroon 5px bullet (`background:var(--accent)`) + one sentence, `font-size:12.5px; line-height:1.45; color:var(--ink-2)`.

**4. `RatingPips`** (used twice)
- Props: `value`, `max` (`FORM` = 5, `WARM` = 3). Render `max` dots, 7px, `border-radius:50%`, `gap:3px`; filled = `var(--ink)`, empty = `var(--pip-empty)`. Precede with a Space Mono 9px uppercase label (`FORM`/`WARM`) in `--muted`. Map straight from `item.formality` (1–5) and `item.warmth` (1–3) in `frontend/src/types/item.ts`.

**5. Pushback chips** (above the composer)
- Label `Adjust` (Space Mono 10px uppercase) then chips `Dressier ↑`, `Warmer`, `Swap #N`, `More color` (`font:600 12px; padding:6px 12px; border:1px solid var(--border); border-radius:999px; background:var(--paper-raised)`). Clicking a chip sends that as the next user turn.

### Exact copy used in the mock (for reference)
- Context bubble: `"Brunch Saturday — warm, put-together but relaxed"` → `→ 4 pieces matched`
- Piece names / slots / rationale:
  1. **White linen shirt** · TOP · white · form ●●●○○ · warm ●●○ — "Breathes on a warm morning; the neutral base keeps the whole look relaxed rather than stiff."
  2. **Olive chinos** · BOTTOM · olive · form ●●●○○ · warm ●●○ — "Softer than denim but still put-together; the earthy tone plays off the tan loafers."
  3. **Tan suede loafers** · SHOES · tan · form ●●●○○ · warm ●○○ — "No-socks ease that suits brunch, and the warm tan lifts the cool whites."
  4. **Canvas tote** · CARRY · natural · form ●●○○○ · warm ●○○ — "Room for sunglasses and a book, with a casual finish so nothing reads formal."

---

## Interactions & behavior
- **Send a message** → append user bubble → show a thinking state on the assistant → render `OutfitResult`.
- **Quick-start chip / pushback chip** → same as typing that text and sending.
- **Wardrobe tile click** → could open `/item/:id`, or (nicer) offer "swap into look".
- **Wear today** → record wear (bump `wornCount`, set `lastWorn`) — backend support is a follow-up; wire the button now, no-op or optimistic if the endpoint isn't ready.
- **Heart / Save look** → save the outfit; also a follow-up (see gap).
- **Animation** (match existing system, `prefers-reduced-motion` already handled in index.css): fades rise from 8–16px below, ~200–320ms, ease-out. No bounces.
- **Hover:** chips/buttons lighten ~6% or border → `--accent`; press `scale(0.98)`. Focus ring: the existing `:focus-visible` 2px accent outline (now maroon) is fine.

## State management
- `messages: {role:'user'|'assistant', text, outfit?}[]`
- `wardrobe: Item[]` (from `listItems()`), `loading`/`error` like the existing screens.
- `pending: boolean` while an outfit is being generated.
- `currentOutfit: Item[]` (or item ids) to drive drawer highlighting + the spec list.

## Backend gap (important)
There is **no outfit-generation endpoint yet** — `/api/items` only does CRUD + `/api/items/tag`. The screen needs something like:
```
POST /api/outfit   { prompt: string, context?: {...} }
  -> { items: string[] /* itemId order */, pieces: [{ itemId, slot, reason }], note?: string }
```
Until it exists, build the UI against a **mock** that picks a few items from `listItems()` and returns canned `slot`/`reason` strings, so the front end is done and swapping in the real call later is a one-line change. Flag this to whoever owns the backend.

## Design tokens (summary)
- **Colors:** paper `#f3ecdd`, paper-raised `#fcf8ef`, paper-sunk `#e9dfca`, ink `#33271f`, ink-2 `#574a3d`, muted `#8f8272`, placeholder `#a89a86`, hairline `#e3d8c4`, border `#d7cab2`, accent `#7c2833`, on-accent `#f6ecd9`, accent-soft `#ecd9d3`, accent-line `rgba(124,40,51,.22)`, pip-empty `#d8cbb2`.
- **Type:** display **Bricolage Grotesque** 700/800 (tracking `-0.02em`/`-0.03em`); body **system-ui**; labels/eyebrows **Space Mono** (uppercase, `+0.12–0.14em`). Sizes: name 15px, title 20px, body 14.5px, small 12.5px, eyebrow 10px, pip label 9px. All already loaded via `frontend/index.html`.
- **Radius:** input 8px, tile 9–11px, card 12px, tray 14px, pill 999px.
- **Spacing:** 4px base (matches existing `--space-*`); component padding 13–24px, gaps 8–22px.
- **Elevation:** flat — borders only, no shadow on in-page cards (unchanged from current system).

## Assets
- Garment photos come from the user's wardrobe via `photoUrl(itemId)` — no image assets ship in this bundle.
- Icons: the design system standard is **Lucide**; the mock uses two unicode glyphs (`⌕` search, `↑` send, `♡` save) — substitute Lucide `search` / `arrow-up` / `heart` in the app.

## Files in this bundle
- `Ensemble Stylist.dc.html` — the visual reference. Option **2a** is the target.
- `support.js` — runtime needed to open the reference in a browser.
- `theme-tokens.css` — drop-in `:root` block with the recolored + new tokens.
- `README.md` — this document.
