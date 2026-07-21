# Assumptions Log — 21-spec-manual-outfit-assembly (issue #20)

This log records every non-obvious decision the SDD-fleet **manager** made on the
user's behalf while running the workflow autonomously. The user reviews this at the
push gate to catch anything they would have decided differently.

Feature: Manual outfit-assembly page (`/assemble`) — drag wardrobe items onto a
mannequin. GitHub issue #20. Branch: `feature/21-manual-outfit-assembly`.

---

## Phase 0 — Setup

- **A0.1 — Spec sequence number.** Used `21-spec-manual-outfit-assembly` (not `20-*`).
  Rationale: spec dirs are numbered by creation order, not issue number; the existing
  `20-spec-stylist-screen-redesign` is issue #12's spec. The issue text itself asked
  for the next free number (21). Non-controversial.
- **A0.2 — Feature branch.** All work (spec artifacts included) is committed on the new
  branch `feature/21-manual-outfit-assembly`, created from a clean `main`, per the
  user's explicit "all work on a new branch" instruction. Nothing lands on `main`
  until the push gate.
- **A0.3 — Other mid-flight specs ignored.** The assessor showed `07-pwa-security-guards`
  (S4_START) and `13-image-import-options` (S3_MIDFLIGHT) as active. These are unrelated
  to issue #20 and were left untouched; the user's request is unambiguously this new feature.

## Phase 1 — Spec (open questions resolved by the manager)

- **A1.1 — Clarification: none needed.** The issue is unusually complete (Decided design,
  rejected alternatives, non-goals, acceptance criteria). The manager accepted the spec
  agent's judgment that no clarification questions file was warranted. No user contact.
- **A1.2 — Mannequin SVG art (Open Q1).** Resolved: a simple, hand-authored in-repo
  line-art SVG silhouette is acceptable — no external/licensed asset. The drop *zones*
  matter more than the artwork's fidelity. Non-blocking.
- **A1.3 — Drag source component (Open Q2).** Resolved: **prefer a sibling source
  component that reuses `WardrobeDrawer`'s CSS classes/markup** (`drawer-grid` /
  `drawer-tile` / `drawer-search`) rather than mutating the display-only `WardrobeDrawer`
  itself, so the existing drawer's behavior (used elsewhere) stays untouched. Extending
  the drawer is also acceptable if cleaner in practice; the hard rule is **do not fork a
  divergent tile style**. Left to task planning.
- **A1.4 — Placed-tile detail (Open Q3).** Resolved: a placed tile shows **photo + remove
  "×" only** — no slot label or rating pips required for this issue. Keeps it minimal.
- **A1.5 — iOS touch activation thresholds (Open Q4).** Resolved: because this autonomous
  run has **no real iOS device**, the implementation will set sensible default dnd-kit
  activation constraints (a small PointerSensor distance threshold + a TouchSensor
  delay/tolerance to avoid scroll-vs-drag conflict) and the on-device tuning/validation is
  accepted as a **documented deferred/manual item**, not a hard blocker. The spec captures
  it as a watch-item + on-device proof artifact. This is the one acceptance criterion
  ("works with touch on a real mobile device") that cannot be machine-verified in this run —
  flagged explicitly for the user at the push gate.
- **A1.6 — Spec approved without revision.** The manager reviewed the full spec against the
  issue and approved it as-is; the 4 open questions above are carried forward as logged,
  non-blocking assumptions (none change scope, requirements, demoable units, or acceptance
  criteria).

## Phase 2 — Task list + planning audit

- **A2.1 — Parent tasks = demoable units.** Approved the 4 parent tasks as scoped (they map
  1:1 to the spec's demoable units). No merge/split.
- **A2.2 — Audit PASS, optional hardening accepted.** The planning audit passed all 4
  REQUIRED gates with one advisory FLAG (drag/touch fidelity can't run in jsdom — by design).
  The manager **accepted the optional FLAG-1 hardening**: type the synthetic `onDragEnd` test
  payload with dnd-kit's `DragEndEvent` type so a future dnd-kit upgrade that changes the
  payload shape fails at compile time instead of silently passing a stale synthetic test.
  Rationale: low-cost, strengthens the one flagged blind spot; sensor config is also extracted
  to a testable `dndConfig.ts` so the pointer+touch requirement has a machine-verifiable test.
  Folded into Task 2.6 and re-audited.
- **A2.3 — Stale README label noted, not fixed.** Root README still calls `/` "the wardrobe
  grid" (stale since spec-20; `/` is now the Stylist screen). Out of scope for this issue;
  code + spec win. No task touches it.

## Phase 3 — Implementation (mid-run adjustments)

- **A3.1 — Per-task backup reviewers dropped (user instruction).** After task 1.0 (which did
  get a background backup review — PASS), the user directed: "stop the extra validation for the
  next few phases. just leave validation to the final stage." Accordingly, tasks 2.0–4.0 are
  implemented WITHOUT the per-task background review agents; correctness is gated by (a) each
  worker's own TDD + test/lint/typecheck gates, (b) the manager's lightweight blocking check
  (commit + proof file + `[x]` present), and (c) the final Phase-4 validation. Task 1.0's
  review verdict (PASS) still stands and is carried to the push gate.
