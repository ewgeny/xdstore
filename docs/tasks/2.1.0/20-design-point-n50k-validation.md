---
title: "[2.1.0][N50K] Validate the published design point: headline scenarios at N=50k + heap-after-load"
labels: [2.1.0-train, P1, performance, chore]
status: done
---

## Summary

The decision guide now publicly claims a design point of **10⁴–10⁵ objects per class** —
but the canonical baseline measured N=2500–5000 only, and the N=50k probe was filed as
STRETCH in task 13. A published claim needs its measurement: run the headline scenarios at
N=50 000 and record heap-after-load, then either back the claim or re-state it honestly.

## Why

DECISION-GUIDE.md § positioning: *"The design point is 10⁴–10⁵ objects per class."* The
guide also says (§ losses, item 2) that heap ≈ working set until W-4 — which means the upper
end of the claimed range (10⁵) is exactly where the ceiling should show. Publishing a design
point without its boundary measurement is the kind of claim this project's transparency
discipline exists to prevent. Task 13 scoped this probe and explicitly deferred it; the
guide's publication is the demand signal that pulls it in.

## Scope

1. **The probe** (extends `scripts/run-benchmarks.sh` / the canonical harness — task 13's
   conditions protocol applies verbatim: quiet-machine gate, WARN-console logging, JSON
   results + run manifest):
   - headline scenarios at N=50 000 (single entity class, fragment size 128): point read by
     id, indexed EQUALS, selective range, save+delete, read-only tx;
   - **heap-after-load**: used-heap delta after open+load at N=5k vs N=50k (MXBean snapshot,
     the W-2 budget instrumentation already knows where records live) — the number that
     makes the heap≈DB ceiling concrete for readers;
   - runtime guard: the N=50k phases must complete in bounded time (state the budget in the
     script; if a scenario exceeds it, record the timeout as the finding — that IS the
     ceiling data).
2. **BENCHMARKS.md**: a "design point" subsection — the N=5k vs N=50k table + heap numbers,
   generated from the result files like the rest of the canonical estate.
3. **DECISION-GUIDE.md**: the design-point sentence gets its citation (or the honest
   correction — e.g., "10⁵ with heap ≈ data size; beyond that, W-4").

## Out of scope

- Any optimization the numbers suggest (file findings as issues); multi-class mixtures;
  N beyond 50k.

## Acceptance criteria

- [x] N=50k results + manifest archived under `docs/perf/design-point-2026-10-23/` (the
      canonical layout: result JSON + run manifest with git rev/JDK/CPU/loadavg/flags).
- [x] BENCHMARKS.md design-point subsection generated from the result files
      (`scripts/render-bench-baseline.py` gained the probe-document branch).
- [x] DECISION-GUIDE.md cites the measurement in both places — the positioning claim now
      scopes "hundreds of write-tx/s" to batched commits (with the unbatched 10⁵ number as
      the honest counter), and the heap≈working-set loss carries the measured
      ~1.4–2.0 KB/object.
- [x] The quick suite still fits its ≤15 min budget: the N=50k phases are a SEPARATE script
      mode (`scripts/run-benchmarks.sh n50k`) — never part of quick/full; the probe's own
      wall clock ≈ 3 min (both N phases, budget-capped scenarios inside).

## Findings (the honest verdict, 2026-10-23 run)

- **Reads scale to 5×10⁴**: point reads 41.5k→67.8k ops/s, read-only tx 80.2k→59.1k ops/s,
  indexed EQUALS 42.2k→34.5k ops/s — same 10⁴–10⁵ band as at 5×10³.
- **Heap ≈ working set, quantified**: ~1,988 B/object at 5×10³ and ~1,437 B/object at
  5×10⁴ (≈70–100 MB per 5×10⁴ for this entity shape).
- **The wall is unbatched WRITES**: save+delete collapses 67.5 → 7.0 pairs/s (≈10× scaling,
  120s budget timeout recorded at 5×10⁴). Batched commits remain the bulk-write path
  (canonical commitBatch: objects/s RISES with batch size).
- Probe-run gotchas fixed along the way (recorded for reruns): a BETWEEN primary criterion
  operates on the PRIMARY index field (indexFieldNames[0]) — the probe entity's indexed
  field IS its primary; and an empty range result at small N is a valid answer — gating the
  op counter on non-empty hits starved the 5k phase into a false TIMEOUT.

Estimate: ~1–2 dev-days.

## References

- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) § positioning / § losses (2) — the claim and
  its ceiling
- Task 13 (BENCH) — the harness, conditions protocol, and the STRETCH item being pulled in
- [BENCHMARKS.md](../../BENCHMARKS.md) § Canonical baseline (the format to extend)
