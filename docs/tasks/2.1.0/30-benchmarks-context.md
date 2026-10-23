---
title: "[2.1.0][BMCONTEXT] Benchmarks in context: the field comparison the numbers need"
labels: [2.1.0-train, P0, docs, performance]
status: open
---

## Summary

The feedback: *"comparisons against competitors are nowhere, while the benchmarks are
measured in a vacuum."* BENCHMARKS.md measures honestly but reads absolutely — numbers
with no frame of reference. Add the frame: a context section that places the canonical
baseline against the field (order-of-magnitude public figures, sourced), per scenario
group, with the honest disclaimer of what such comparisons can and cannot claim.

## Why

Runs second in the P0 docs track: task 29 fixes the entry surface, this fixes the
measurement page's meaning. The decision guide already carries a comparison table — but it
lives in a document the evaluators didn't reach (the 29 lesson); the numbers page itself
must carry the context, because that is where a skeptical reader looks for it.

## Scope

1. **The context header** on BENCHMARKS.md: what these numbers measure (embedded,
   transactional object persistence with ACID + MVCC + triggers — not raw KV ops), and the
   order-of-magnitude field frame from public materials (RocksDB/MapDB/Chronicle/LMDB
   ballparks with links, as already compiled in DECISION-GUIDE § comparison) — restated
   briefly, linked for depth, and framed honestly: **workload-dependent, not
   like-for-like**, the useful signal is the gaps within XdStorage's own scenarios plus the
   honest statement of where the engine does not compete (sustained >10³ writes/s).
2. **Per-family "in real terms" lines** already partially exist in the reading notes —
   complete them: every canonical scenario group gets one sentence of what it means for an
   embedder (e.g. `readOnlyTx` 63.6k ops/s = "request-scoped reads are effectively free
   next to the write cost"; `writeDurable` 8× = "the price of surviving a power loss").
3. **The write-throughput context**: the write numbers carry the same limitation +
   fix-plan link as the README (task 29/31) — measured gap, target, status.
4. **A sources note**: the public figures cited with their origin (project docs/benchmarks
   pages), dated; no invented numbers.

## Out of scope

- Running competitor benchmarks ourselves (a credible cross-product harness is a project
  of its own; order-of-magnitude sourced figures are the honest v1);
- changing any measurement or the canonical protocol (task 13's estate is untouched).

## Acceptance criteria

- [ ] The context header + field frame live on BENCHMARKS.md, linked from DECISION-GUIDE
      (which links back — one story, two entry points).
- [ ] Every canonical scenario family has its "in real terms" line.
- [ ] All cited external figures carry sources and dates; nothing invented.
- [ ] DOCSLINT (21) stays green.

Estimate: ~0.5 dev-day (framing and citations; the measurements exist).

## References

- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) § comparison (the sourced frame)
- [BENCHMARKS.md](../../BENCHMARKS.md) § Canonical baseline (the page gaining context)
- Task 29 (the entry surface), task 31 (the write-gap plan both pages link)
