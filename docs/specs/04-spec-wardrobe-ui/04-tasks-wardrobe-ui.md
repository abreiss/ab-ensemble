# 04-tasks-wardrobe-ui.md

Task plan for `04-spec-wardrobe-ui`.

## Standards Evidence Table

| Source File | Read | Standards Extracted | Conflicts |
| --- | --- | --- | --- |
| `AGENTS.md` (root) | yes | (1) TDD: strict on backend domain, but frontend tests cover *meaningful* logic only — do not chase 90% on view plumbing; (2) React 19 + Vite, mobile-first, Vitest + RTL; (3) DTO shapes are the API contract; conventional commits | none |
| `README.md` (root) | yes | (1) Frontend lives in `frontend/`, Vite dev proxies `/api` → `:8080`, build embeds into Spring `static/`; (2) documented `/api/items` surface (create multipart, list, get, get photo, PUT tags, DELETE); (3) `POST /api/items/tag` returns editable suggestion, no key needed to build/test | none |
| `docs/TESTING.md` | yes | (1) Frontend logic (state, API calls, rendering decisions) → Vitest + RTL on meaningful logic; view plumbing light; (2) never call live backend/network in tests — mock `fetch`/API client | none |
| `.pre-commit-config.yaml` | yes | (1) `frontend-tests` = `cd frontend && npm run test -- --run`; (2) `frontend-lint` = `npm run lint` (eslint); (3) large-file cap 2048kb (screenshots must stay under) | none |
| `frontend/eslint.config.js` | yes | typescript-eslint recommended + react-hooks + react-refresh; `.test.{ts,tsx}` allowed Vitest globals | none |
| `frontend/package.json` | yes | React 19, Vite 6, Vitest 3, RTL, TS; scripts `dev` / `build` (`tsc -b && vite build`) / `test` / `lint` | none |
| `CONTRIBUTING.md` | not found | — | n/a |
| `.github/pull_request_template.md` | not found | — | n/a |
| `.github/workflows/*` | not found | No CI workflow files present; local gate is pre-commit only (fallback evidence used) | none |

Two-plus guideline sources read (incl. `AGENTS.md` + root `README.md`); no
conflicts across sources.

## Relevant Files

| File | Why It Is Relevant |
| --- | --- |
| `frontend/package.json` | Add `react-router-dom` (current v7 line) as a dependency; scripts unchanged. |
| `frontend/src/main.tsx` | Mount the router (`<BrowserRouter>` / `createBrowserRouter`) at app root. |
| `frontend/src/App.tsx` | App shell/layout: persistent "add" + back navigation and the routed `<Outlet>`. |
| `frontend/src/App.test.tsx` | Shell + routing render test (`/`, `/add`, `/item/:id` mount their screens). |
| `frontend/src/index.css` | Branded mobile-first design tokens (type scale, color, spacing) + base styles. |
| `frontend/src/types/item.ts` | TypeScript types mirroring `ItemResponse` / `TagRequest` / `TagSuggestion` DTOs. |
| `frontend/src/api/items.ts` | Typed API client for `/api/items` (list, get, photoUrl, tagPreview, create, updateTags, delete). |
| `frontend/src/api/items.test.ts` | Unit tests for the API client (request shape + response/error mapping). |
| `frontend/src/lib/tagValidation.ts` | Pure required-field validator (`category` non-blank, `formality` 1–5, `warmth` 1–3). |
| `frontend/src/lib/tagValidation.test.ts` | Unit tests for the validator (critical guardrail logic). |
| `frontend/src/components/TagForm.tsx` | Shared editable tag form reused by Add + Detail (null field → empty editable input). |
| `frontend/src/components/TagForm.test.tsx` | Tests: renders from suggestion, edits emit, submit gated by validation. |
| `frontend/src/components/DescriptorChips.tsx` | `descriptors` add/remove chip editor. |
| `frontend/src/components/DescriptorChips.test.tsx` | Tests: add chip, remove chip, emit list. |
| `frontend/src/routes/WardrobeGrid.tsx` | Grid screen (`/`): thumbnails, empty + error states. |
| `frontend/src/routes/WardrobeGrid.test.tsx` | Tests: N items → N thumbs, tap→detail, empty, list-failure. |
| `frontend/src/routes/AddItem.tsx` | Add screen (`/add`): capture/upload, auto tag-preview, TagForm, save. |
| `frontend/src/routes/AddItem.test.tsx` | Tests: full add flow, degraded suggestion, validation gate, create-failure. |
| `frontend/src/routes/ItemDetail.tsx` | Detail screen (`/item/:id`): view photo + edit tags + guarded delete. |
| `frontend/src/routes/ItemDetail.test.tsx` | Tests: edit→updateTags, confirm→delete→nav, not-found, failure states. |
| `README.md` | Add a "Wardrobe UI" dev section (routes, how to run, no key needed to browse/edit). |
| `docs/specs/04-spec-wardrobe-ui/04-proofs/` | Screenshots + green test/lint/build run summaries (sanitized, < 2048kb). |

### Notes

- Tests sit alongside the code they cover (`Foo.tsx` + `Foo.test.tsx`), per the repo's existing `App.test.tsx` / `api/health.ts` layout.
- Test command: `cd frontend && npm run test -- --run [path]` (Vitest). Lint: `npm run lint`. Build: `npm run build`.
- **TDD:** each meaningful-logic sub-task is RED (failing test) → GREEN (minimum code). Pure view/layout/CSS plumbing is styled in REFACTOR steps and only lightly tested, per `docs/TESTING.md`.
- No live network in tests — mock `fetch` (client tests) or the `items` API module (screen tests). No Claude key needed.
- Use the **frontend-design skill** for the visual system in the REFACTOR/design sub-tasks (1.5, 2.4, 3.7, 4.5).

## Tasks

### [x] 1.0 App shell: routing, typed API client & branded mobile-first design foundation

Foundation slice (spec Unit 1). Adds `react-router-dom`, the `/api/items` client
layer, and a small branded design system every screen reuses. Depends on nothing;
Tasks 2–4 depend on it.

#### 1.0 Proof Artifact(s)

- Test: `cd frontend && npm run test -- --run src/api/items.test.ts` passes — asserts each client function (`listItems`, `getItem`, `photoUrl`, `tagPreview`, `createItem`, `updateTags`, `deleteItem`) builds the correct method/path and multipart-vs-JSON body, and maps 2xx / non-2xx / network-failure outcomes — demonstrates the API contract (spec Unit 1 FRs 2–3).
- Test: `cd frontend && npm run test -- --run` includes a routing test showing `/`, `/add`, and `/item/:id` mount their respective screens — demonstrates client-side routing (FR1).
- CLI: `cd frontend && npm run lint` exits clean and `npm run build` (`tsc -b && vite build`) succeeds, emitting assets to `src/main/resources/static/` — demonstrates the shell compiles and integrates with Spring's static serving.
- Screenshot: `docs/specs/04-spec-wardrobe-ui/04-proofs/` app shell at ~390px viewport showing the branded style + persistent "add"/back navigation — demonstrates the design foundation (FRs 4–5). (Sanitized; < 2048kb.)

#### 1.0 Tasks

- [x] 1.1 Install `react-router-dom` (current v7 stable) in `frontend/`; wire the router in `main.tsx` with routes `/` → `WardrobeGrid`, `/add` → `AddItem`, `/item/:id` → `ItemDetail` (temporary stub screens are fine here).
- [x] 1.2 RED: write `src/types/item.ts` types (mirroring `ItemResponse` / `TagRequest` / `TagSuggestion`) and `src/api/items.test.ts` mocking `fetch` — assert `listItems`/`getItem`/`photoUrl`/`tagPreview`/`createItem`/`updateTags`/`deleteItem` build the right method+path+body (multipart for tagPreview/create, JSON for updateTags) and map 2xx→parsed / non-2xx→throw / network→throw; confirm it fails (module absent).
- [x] 1.3 GREEN: implement `src/api/items.ts` to satisfy the client tests, following the `src/api/health.ts` pattern (typed return, throw on non-2xx/network).
- [x] 1.4 RED→GREEN: write `src/App.test.tsx` rendering the app under a router (MemoryRouter) at `/`, `/add`, `/item/:id` and asserting each screen stub mounts; implement `App.tsx` as the shell/layout (persistent add + back nav, `<Outlet>`) to pass.
- [x] 1.5 Design foundation: add branded mobile-first tokens + base styles to `index.css` (type scale, color, spacing) and style the shell/nav; use the **frontend-design skill**. Light render test that the shell exposes an "add" navigation control.
- [x] 1.6 REFACTOR: run `npm run lint` (clean) and `npm run build` (succeeds → `src/main/resources/static/`); capture the app-shell mobile screenshot to `04-proofs/`.

### [x] 2.0 Wardrobe grid (browse owned items)

Home screen (spec Unit 2): a mobile photo grid of owned items with empty and
error states. Depends on 1.0.

#### 2.0 Proof Artifact(s)

- Test: `cd frontend && npm run test -- --run` includes a grid test rendering N mocked items as N thumbnails and asserting a tap navigates to `/item/:id` — demonstrates browse + navigate (FRs 1–3).
- Test: an empty-wardrobe test shows the empty state with an add link to `/add`, and a list-failure test shows a non-crashing error + retry — demonstrates the empty/error edge cases (FRs 4–5).
- Screenshot: `04-proofs/` populated grid at ~390px viewport with lazy-loaded thumbnails — demonstrates thumbnails render. (Sanitized; < 2048kb.)

#### 2.0 Tasks

- [x] 2.1 RED: write `src/routes/WardrobeGrid.test.tsx` mocking the `items` API — N items → N thumbnails using `photoUrl`, and a tap on a thumbnail navigates to `/item/:id`; confirm it fails (component absent).
- [x] 2.2 GREEN: implement `WardrobeGrid.tsx` — fetch `listItems` on mount, render thumbnails (native lazy-loading) linking to `/item/:id`; pass the test.
- [x] 2.3 RED→GREEN: add an empty-wardrobe test (empty list → empty state + link to `/add`) and a list-failure test (error state + retry control); implement both states.
- [x] 2.4 REFACTOR: style the grid mobile-first via the **frontend-design skill**; `npm run lint` clean; capture the populated-grid mobile screenshot to `04-proofs/`.

### [x] 3.0 Add item: camera/upload → auto-tag → edit → save (headline flow)

The headline slice (spec Unit 3): photo → automatic tag preview → editable form
(chips for descriptors) → validated save → back to grid. Depends on 1.0.

#### 3.0 Proof Artifact(s)

- Test: `cd frontend && npm run test -- --run` includes an add-flow test simulating photo select → auto-fired mocked `tagPreview` → pre-filled form → edit → save, asserting `createItem` is called with the correct multipart payload (photo + tags) and the app then routes to the grid — demonstrates the end-to-end headline flow (FRs 1–3, 5).
- Test: a degraded-suggestion test (all-null `TagSuggestion`) shows the form still renders editable and produces a valid save — demonstrates the failed-vision fallback (FR3).
- Test: a validation test shows save is disabled until `category` is non-blank and `formality` (1–5) / `warmth` (1–3) are valid; and a create-failure test preserves the photo + entered tags — demonstrates client guardrails + no data loss (FRs 4, 6).
- Screenshot(s): `04-proofs/` mobile capture of photo → auto-tags → edit → save with the item then visible in the grid — demonstrates spec acceptance criterion 1. (Sanitized; non-sensitive garment photo; < 2048kb.)

#### 3.0 Tasks

- [x] 3.1 RED→GREEN: write `src/lib/tagValidation.test.ts` covering `category` non-blank, `formality` in/out of 1–5, `warmth` in/out of 1–3 (valid + each invalid case); implement the pure `tagValidation.ts` validator to pass. *(Critical guardrail — mirrors backend `TagRequest` constraints.)*
- [x] 3.2 RED: write `src/components/DescriptorChips.test.tsx` (add a chip, remove a chip, emits the updated list) and `src/components/TagForm.test.tsx` (renders every field from a suggestion; a null field renders empty + editable; edits emit; submit disabled until `tagValidation` passes); confirm both fail.
- [x] 3.3 GREEN: implement `DescriptorChips.tsx` and the shared `TagForm.tsx` (using `tagValidation`) to pass 3.2.
- [x] 3.4 RED: write `src/routes/AddItem.test.tsx` — photo select → auto-fired mocked `tagPreview` → pre-filled `TagForm` → edit → save calls `createItem` with the correct multipart payload → routes to `/`; plus a degraded (all-null) suggestion still saving, and a `createItem` failure that preserves photo + entered tags; confirm it fails.
- [x] 3.5 GREEN: implement `AddItem.tsx` — file input with `accept="image/*" capture`, photo preview, auto-call `tagPreview` with a loading state, render `TagForm`, save via `createItem`, navigate to the grid, and handle errors without data loss; pass 3.4.
- [x] 3.6 REFACTOR: style the add flow mobile-first via the **frontend-design skill** (photo preview, loading, form, chips); `npm run lint` clean; capture the add-flow mobile screenshots to `04-proofs/`.

### [x] 4.0 Item detail: edit tags + guarded delete

Maintenance screen (spec Unit 4): view one item, edit its tags, or delete with
confirmation. No wear-history shown (deferred to #7). Depends on 1.0 (and reuses
`TagForm` from 3.0).

#### 4.0 Proof Artifact(s)

- Test: `cd frontend && npm run test -- --run` includes a detail-edit test loading a mocked item, editing a field, saving, and asserting the `updateTags` JSON payload (with the same required-field rules as create) — demonstrates edit (FRs 1–2).
- Test: a delete test shows an explicit confirm step → `deleteItem` called → navigation back to the grid with the item gone — demonstrates guarded delete (FRs 3–4).
- Test: a not-found test (unknown id) and a failed save/delete test each render a non-crashing state without losing context — demonstrates the error edge cases (FR5).
- Screenshot: `04-proofs/` item detail at ~390px viewport showing editable tags + the guarded delete affordance, with no wear-history fields present — demonstrates the maintenance surface and the #7 deferral. (Sanitized; < 2048kb.)

#### 4.0 Tasks

- [x] 4.1 RED: write `src/routes/ItemDetail.test.tsx` — mock `getItem`, render the item photo + `TagForm` pre-filled, edit a field, save, and assert the `updateTags` JSON payload; assert **no** wear-history (`lastWorn`/`wornCount`) is rendered; confirm it fails (component absent).
- [x] 4.2 GREEN: implement `ItemDetail.tsx` — fetch `getItem` on mount, show the photo + reused `TagForm`, save edits via `updateTags`; pass 4.1.
- [x] 4.3 RED→GREEN: add a delete test (explicit confirm step → `deleteItem` called → navigate back to `/` ) and implement the guarded delete.
- [x] 4.4 RED→GREEN: add a not-found test (unknown id → non-crashing "not found") and a failed save/delete test (error shown, context preserved); implement both states.
- [x] 4.5 REFACTOR: style the detail screen mobile-first via the **frontend-design skill**; `npm run lint` clean; capture the detail mobile screenshot to `04-proofs/`.

### [x] 5.0 End-to-end proof against the live backend + docs

Ties the UI to the running API and records the acceptance evidence (spec Success
Metrics 1–3). Depends on 1.0–4.0.

#### 5.0 Proof Artifact(s)

- CLI/manual: with DynamoDB Local + the backend running (`./gradlew bootRun`) and the Vite dev server (`cd frontend && npm run dev`), exercise all three acceptance criteria (add on a phone-width browser → auto-tags → edit → save → visible in grid; grid shows items; edit + delete work); save a sanitized transcript/screenshots to `04-proofs/` — demonstrates the spec's headline acceptance criteria end to end.
- CLI: full `cd frontend && npm run test -- --run` + `npm run lint` + `npm run build` output saved to `04-proofs/` (all green) — demonstrates the quality gate (Success Metric 4).
- Diff: `README.md` gains a "Wardrobe UI" section (how to run the UI in dev, the routes, and that no Claude key is needed to browse/edit) — demonstrates a new developer can run the slice.

#### 5.0 Tasks

- [x] 5.1 Add a "Wardrobe UI" section to `README.md`: how to run the UI in dev (`npm run dev` + backend + DynamoDB Local), the three routes, and that browsing/editing needs no Claude key (only live tagging does).
- [x] 5.2 Run `cd frontend && npm run test -- --run`, `npm run lint`, and `npm run build`; save the green summary to `04-proofs/`.
- [x] 5.3 Live run against the backend (DynamoDB Local + `bootRun` + Vite dev): exercise the three acceptance criteria at phone width; save sanitized screenshots/transcript (no secrets) to `04-proofs/`.
- [x] 5.4 Verify no secrets in `04-proofs/` and that pre-commit (frontend tests + lint + secret scan) is green before the final commit.
