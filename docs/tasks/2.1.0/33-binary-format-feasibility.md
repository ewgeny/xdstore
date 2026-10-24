---
title: "[2.1.0][BINFEAS] Binary-format feasibility: measure the serialization share on cold paths (FP-9's gate)"
labels: [2.1.0-train, P2, docs, performance]
status: open
---

## Summary

An external proposal (docs/proposals/OPTIMIZATION-PROPSAL-01.md) asks for a binary
serialization format (Protobuf/Kryo/CBOR/MessagePack) and claims **5–10× faster IO**. The
serialization SPI already exists and FP-9 (binary compact format, non-default) is planned
behind exactly one gate: *"if profiling justifies."* This task runs that gate: measure the
serialization share on the paths where the text format costs, and produce a go/no-go
recommendation with numbers.

## Why

The known measured serialization cost is the **cold path**: after a reopen, reads parse
YAML per tree level (~35 µs per level, PERF study §4 — the 80–87 µs plateau at t≥32 is
mostly parse). Warm paths never re-parse (the record cache holds live instances). So the
binary-format upside is bounded by how much of real cold-path latency is parsing versus
IO — a number nobody has measured. The proposal's 5–10× is unsourced; the honest response
is data, not enthusiasm (and not rejection either — the proposal is a genuine demand
signal).

## Scope

1. **Instrument the cold path**: reopen a storage at N=5k (and the N=50k probe set from
   task 20 if available), measure per-phase wall time of the first-touch reads: base-file
   parse vs tree-node parse vs index parse vs IO — attribution via existing log/metrics
   hooks or micro-instrumentation (no production code changes; a measurement harness in
   the sweep family).
2. **The bound**: compute the theoretical binary-format ceiling — if parsing is X% of
   cold-path time, a zero-parse format can win at most X% (plus smaller files → fewer page
   reads). State the number per scenario.
3. **The go/no-go recommendation** for FP-9: GO (binary SPI is worth a design round, with
   the measured upside) or NO-GO/DEFER (the share is too small to justify a second
   serializer surface — the WRITE-PATH-DESIGN §3 rejection rationale stands).
4. The recommendation lands in MASTER-PLAN Step 4 (FP-9's gate resolved) + a summary in
   BENCHMARKS or the proposal's analysis file.

## Out of scope

- Implementing any binary format (FP-9 stays SPI-shaped, non-default, and gated);
- benchmarking third-party serializers (Kryo/protobuf) — that is the implementation
  phase's work IF the gate says go;
- warm-path optimizations (the record cache means warm reads never parse — nothing to win
  there by format alone).

## Acceptance criteria

- [ ] The measurement harness runs under the canonical conditions protocol (task 13) and
      its results are archived with a manifest.
- [ ] The cold-path serialization share is stated per scenario (parse vs IO vs other), at
      N=5k and one larger N.
- [ ] The go/no-go recommendation for FP-9 is written with the numbers, and MASTER-PLAN
      Step 4's FP-9 entry is updated (gate resolved either way).
- [ ] No production code changes; full suite green (BUILD SUCCESS verdict).

Estimate: ~0.5–1 dev-day.

## References

- [OPTIMIZATION-PROPSAL-01.md](../OPTIMIZATION-PROPSAL-01.md) + its
  [analysis](../OPTIMIZATION-PROPSAL-01-ANALYSIS.md) (the mapping and verdicts)
- [PERFORMANCE-RECOMMENDATIONS.md](../../PERFORMANCE-RECOMMENDATIONS.md) §4 (the cold-path
  parse cost data)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) Step 4, FP-9 (the gate this resolves)
