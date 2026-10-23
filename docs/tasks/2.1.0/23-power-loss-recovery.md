---
title: "[2.1.0][PWRLOSS] Power-loss recovery: the verified recovery matrix, the recipe, and the durableWrites default decision"
labels: [2.1.0-train, P0, engine, testing, docs]
status: open
---

## Summary

An embedder's server lost electricity ("electricity-lost") — and the engine's power-loss
story is only half-designed today: the default configuration is explicitly **process-crash
safe, not power-loss safe**, the recovery path after a power-loss-induced loud failure is
undocumented, and the `durableWrites` default has never been revisited against real
incidents. This task makes the power-loss behavior a **verified matrix**, documents the
**recovery recipe**, and settles the **default** — before 2.1.0 ships.

## Why (the incident, analyzed against the current contract)

Ground truth from the shipped guarantees: default writes are atomic-rename protected
(R-11) with EOF sentinels (S-1), `.tmp` sweeps at open (S-5), and the commit journal's
torn-tail policy discards everything after the last sentinel with a WARN (W-1) — that is
**process-crash safety**. What power loss adds is the loss of the **OS page cache**: a
"successfully renamed" file can come back with its tail missing, and then:

- a torn **journal** tail → recovered by design (discard + WARN) — good;
- a torn **base file** → the S-1 sentinel fires **loudly at open** → **the folder does not
  open**. That is the stranding class BUG-A already demonstrated once ("data stranded until
  the structure journal was cleared manually") — by design loud, but with **no documented
  recovery recipe**, and an unquantified blast radius (how much is lost = everything since
  the last fsync/checkpoint, which with `durableWrites=false` is unbounded);
- `durableWrites=true` (fsync per commit) prevents the class entirely — measured cost ~8×
  on writes (BENCHMARKS § Canonical, `writeDurable`: 47 vs 367 ops/s), which is exactly why
  it defaults off.

No issue exists for this on the tracker (checked 2026-10-23: the repo carries only the
sync-managed train tasks); this task file is the issue's home. If the original incident
report exists elsewhere (email, another tracker), link it in a comment.

## Scope

1. **The recovery matrix test** (reliability package — the power-loss proxy: power loss
   itself cannot be reproduced in CI, so simulate its observable effect — files truncated
   at arbitrary points — and assert the outcome for every cell):
   - truncate at N points, for each file class: data base file, per-resource journal
     (`<basefile>.journal.yaml`), structure files, references — × `durableWrites` on/off
     (with fsync, truncation implies disk-level damage; the matrix still pins the behavior);
   - assert per cell: **clean open with a lost-tail WARN** (journal case — the designed
     behavior), or **loud sentinel failure** (base case), and never a silently-partial open;
   - the interplay cells nobody has tested: **torn base + intact journal** and **torn
     journal + intact base** — does journal replay onto a truncated base behave, or must
     the recipe clear the journal first? (The BUG-A replay lesson says: do not assume.)
2. **The recovery recipe** (EMBEDDING.md, sibling of § Backup and restore — "After a power
   loss"): per failure mode, what the embedder does — expected loud failure semantics, the
   `acceptMissingEofSentinel(true)` reopen path (it exists — but its interplay with journal
   replay is exactly cell 1's untested territory), what is lost (everything since the last
   fsync/checkpoint), and when to restore from backup instead.
3. **Design decision (maintainer's call, presented with data): the `durableWrites` default.**
   - (a) **Keep off**, status quo documented loudly: the decision guide and EMBEDDING carry
     a prominent "power loss loses recent commits; flip durableWrites" warning.
   - (b) **Flip on for 2.1.0**: semver-legal (a config-default change is MINOR per
     RELEASE-PLAN); the cost is the measured ~8× on writes — against the target audience
     (≤10²–10³ write-tx/s, DECISION-GUIDE) that may simply be affordable, and "electricity
     lost" is precisely the incident class the flag exists for. A silent data-loss class
     being one flag away from off-by-default deserves this explicit re-decision.
4. **Cheap hardening, evaluated in the same round: checkpoint-on-clean-shutdown.** The W-1
   checkpoint machinery exists; triggering it at `shutdown()` (flag or default) means after
   any *clean* shutdown the base files are authoritative and the journals empty — a power
   loss *after* a clean close then loses nothing even with `durableWrites` off. Size it in
   the spike; if it is more than a trigger point + tests, defer to W-4's train.

## Out of scope

- Real power-loss reproduction hardware (the matrix simulates the observable effect —
  truncation — which is what the sentinels/journal actually see);
- auto-repair modes (silent truncation acceptance) — rejected: that is the data-loss class
  the S-1 sentinel exists to prevent;
- the FP-6 CLI `verify`/`recover` tooling (2.3.0 — this task only documents the manual
  recipe and keeps the surface ready for it).

## Acceptance criteria

- [ ] The matrix test is green ×2 and every cell asserts a defined outcome (no silently
      partial opens anywhere).
- [ ] EMBEDDING.md carries the recovery recipe; the decision guide's durability notes link it.
- [ ] The `durableWrites` default is DECIDED and implemented or documented (either way,
      CHANGELOG "Changed" carries it — a default flip is a user-facing change).
- [ ] Checkpoint-on-shutdown: shipped or explicitly deferred with a size note.
- [ ] Full suite green (BUILD SUCCESS verdict).

Estimate: 2–3 dev-days (the matrix is the bulk; the two decisions are maintainer calls).

## References

- [EMBEDDING.md](../../EMBEDDING.md) § Files and durability (the contract this extends)
- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) (the journal's torn-tail policy)
- BUG-A (docs/tasks/2.1.0/15) — the stranding precedent; S-1/S-2/S-5/W-1 history in
  [SRE-OPTIMIZATION-PLAN.md](../../SRE-OPTIMIZATION-PLAN.md)
- [BENCHMARKS.md](../../BENCHMARKS.md) § Canonical (`writeDurable` — the 8× number)
- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) § durability notes (the reader-facing warning)
