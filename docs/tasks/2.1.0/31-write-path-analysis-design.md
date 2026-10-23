---
title: "[2.1.0][WRTPATH] Write-path analysis and the W-4 design round: profiling, bottlenecks, the fix design"
labels: [2.1.0-train, P1, engine, performance]
status: done
---

## Summary

The write throughput is the project's acknowledged critical failure (field feedback,
2026-10-23: *"very low write numbers — surface them in the README with the plan: prepare
the analysis, find the bottlenecks, design the solution/refactoring"*). This task does
exactly that: **profile where write time actually goes, write the bottleneck ledger, and
produce the W-4 solution design** — the design round pulled from the 2.2.0 train into
2.1.0. **The implementation stays 2.2.0**; the deliverable here is the analysis, the
design, and the public fix-plan the README links.

## Why now

W-4 was pinned to 2.2.0 as *implementation* — correctly, it is the hardest class of change
in the engine (persisted-state semantics). But the field verdict re-orders priorities: an
evaluator reading ~10²–10³ write-tx/s with no visible plan walks away. The **design** can
and must land in this train: it is bounded, it has no runtime risk, and it converts the
biggest weakness into a published engineering plan. Task 29 surfaces the numbers + the link;
this task makes the link true.

## What is already known (the analysis starts from evidence, not zero)

- **The wall's shape** (WRITE-PATH-DESIGN §1 + BENCHMARKS): every commit rewrites the full
  file of every touched resource, serially, in phase-1 of the 2PC. Data classes are
  journaled since W-1/W-3 (O(delta)); **the id-index and search-index paths still full-file
  rewrite by design scope** — the honest W-3 verdict: the ≥10× write target requires
  journaling the index paths.
- **Measured anchors** (BENCHMARKS § Canonical): indexed save+delete ~731 ops/s;
  data-heavy write ~370 tx/s (journal ≈ neutral on that shape); commit-batch objects/s
  RISE with batch size (1.2k → 11k) — per-commit fixed cost dominates small batches;
  `durableWrites` ~8×.
- **Structural causes named but not yet profiled to shares**: (a) index-path write
  amplification (O(index file) per commit), (b) phase-1 serialization across resources,
  (c) commit-protocol CPU scaling with cache size (the B2 fix took the read side; the write
  side still iterates), (d) search-index key-tree maintenance per indexed field.

## Scope

1. **Profile first** (the feedback's "find the bottlenecks" — measured, not guessed):
   extend the canonical harness (task 13's protocol) with a write-path breakdown — per
   commit: phase-1 wall time per resource (data journal append vs id-index write-out vs
   search-index write-out vs structure), bytes written per resource class, phase-2 share.
   One canonical table: **where the ~13 ms commit actually goes** at a representative N.
2. **The bottleneck ledger**: the causes above, ranked by measured share, each with the
   evidence line and the fix family it belongs to.
3. **The W-4 design document** (WRITE-PATH-DESIGN v2 / W4-DESIGN.md) covering, per the
   standing rules (design round mandatory, kill-9 recovery tests, honest verdicts):
   - **index journaling** — extending the W-1 journal machinery to id-index/search-index
     resources (the ≥10× lever; the R-20 canonical-registry interplay is the risk area —
     the W-3 infrastructure-exclusion lesson is the design's opening chapter);
   - **disk-anchored scans + record-cache eviction** (the F-3 thaw — the heap≈DB ceiling);
   - **checkpoint reuse**: the deferred checkpoint-on-clean-shutdown (task 23's size note),
     checkpoint triggers, and the journal-growth bounds for index journals;
   - alternatives evaluated honestly (group commit; partial-index rewrite; per-delta index
     structures) with the rejection reasons;
   - acceptance targets restated against the canonical baseline, and the
     **implementation estimate for 2.2.0**.
4. **The public fix-plan block** (feeds task 29's limitations link): current numbers →
   root causes in one paragraph each → the fix plan with status. Published in the README
   and BENCHMARKS context (task 30 links it too).

## Out of scope

- The implementation itself (2.2.0 — this train's amendment admits fixes, not surgery);
- micro-optimizations outside the structural fix (they belong to the 2.2.0 work);
- benchmarking competitors (task 30's sourced-frame approach covers the comparison need).

## Acceptance criteria

- [ ] The write-path breakdown table exists in the canonical estate (result files +
      manifest, task-13 format) — the ~13 ms commit is accounted for to ≥80%.
- [ ] The bottleneck ledger is ranked by measured share, every entry with evidence.
- [ ] The W-4 design document covers index journaling, disk-anchored scans, checkpointing,
      and the evaluated alternatives — with the 2.2.0 implementation estimate.
- [ ] The README + BENCHMARKS fix-plan blocks land (with 29/30).
- [ ] No production code changes; the suite is untouched (BUILD SUCCESS verdict).

Estimate: ~2–3 sessions (profiling harness ~1, ledger+design ~1–2). Risk: LOW (analysis and
design only — the risk lives in the 2.2.0 implementation, gated by this document).

## References

- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) (the W-1..W-3 state this extends)
- [BENCHMARKS.md](../../BENCHMARKS.md) § Canonical (the anchors; task 13's protocol)
- Task 23 (checkpoint-on-shutdown size note — input to the checkpoint design)
- Task 29/30 (the pages that publish the plan)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) Step 2 — W-4's implementation home (unchanged)
