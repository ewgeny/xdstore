---
title: "[2.1.0][BENCH] Unify the benchmark estate: one canonical suite, one baseline doc, run manifests"
labels: [2.1.0-train, P0, performance, ci, chore]
status: done
---

## Summary

The benchmark documentation is not unified: numbers live in two docs, produced by **two
different harnesses under two different JDKs, machine loads, and logging configurations**,
with quick-mode rows mixed into full-mode tables and no single source of truth. Compile the
estate into one canonical suite with machine-readable results and one baseline document; add
the scenarios missing for a full performance picture. **ASAP in the train:** the numbers
tasks 08–10 already recorded (one-off addenda and per-feature sections) must be absorbed
into the unified shape, and task 11's nightly assertion depends on the final scenario list.

## Why — the audit of what is not unified today (2026-10-21)

| # | Inconsistency | Evidence |
|---|---|---|
| 1 | **Two harnesses, two methodologies** | JMH classes (`performance/XdStorage{SearchEngine,ResourceCache,Performance}Benchmark`, concurrent, 3 iterations) vs the plain-Java **serial** sweep (`performance/sweep/XdStorageConfigSweep`, per-config phases) — their numbers are not comparable and were never meant to be, yet both feed the docs' "performance" story without a stated relationship |
| 2 | **Two JDKs, two machine conditions** | JMH tables: JDK **17.0.15**, "24-core box **under normal developer load**"; sweep: JDK **21.0.7**, "quiet load (loadavg < 0.5)" |
| 3 | **Logging config pollutes the numbers** | PERF study §9 admits it outright: the JMH numbers were measured with **TRACE console logging enabled** — "expect somewhat better absolute numbers from a quiet-config re-measure"; the sweep used a WARN console config |
| 4 | **Quick-mode rows mixed into full-mode tables** | BENCHMARKS.md's W-3 addendum is a single-iteration quick run ("treat as ballpark") printed in the same document as 3-iteration full tables, and reads as comparable |
| 5 | **No single baseline table, no source of truth** | Numbers are scattered: BENCHMARKS.md §1–3 + addendum; PERFORMANCE-RECOMMENDATIONS.md §4/§5/§7/§7.1 — duplicated prose tables with no machine-readable artifact binding |
| 6 | **No run manifests** | Conditions (JDK, load, logging, config flags, git rev) are prose per doc, not structured metadata attached to each result set |
| 7 | **Coverage gaps for the full picture** | No read-only-tx scenario (the B2 headline), no journal on/off A/B on a data-heavy pattern, no durableWrites cost, no commit-batch curve, no cold-start/first-save, no bounded concurrent-writer number, no N-scaling probe beyond 5000 (the heap≈DB ceiling is invisible in the docs) |

## Scope

1. **Canonical conditions protocol** (both harnesses adopt it):
   - one pinned JDK (recommend the 21 line — it is what the sweep used; state the choice),
   - a quiet-machine gate (loadavg check before the run, recorded in the manifest),
   - **one explicit logging config for benchmarking** (WARN console, no file I/O on the hot
     path) — passed explicitly on the command line, immune to task 01/02's config reshuffle,
   - YAML format, disclosed config flags (journalWrites, durableWrites, t, fragment size).
2. **One entry point + machine-readable results**: `scripts/run-benchmarks.sh [quick|full|sweep]`
   — JMH with `-rf json`, sweep as today (JSON/CSV), everything under a results directory
   accompanied by a **run manifest** (git rev, JDK, CPU model, loadavg, date, mode, config
   flags). Doc tables are generated from these files, never hand-typed.
3. **One baseline document**: BENCHMARKS.md becomes the single canonical baseline (one table
   per harness family: scenario, threads, N, config, ops/s ± error, conditions, date, mode).
   Every table labeled mode/conditions/date; the 2026-10-18 TRACE-era numbers **re-measured
   once under the canonical conditions** and marked superseded (keep the old tables as
   labeled history). PERFORMANCE-RECOMMENDATIONS.md keeps the *studies* but gets a banner:
   edition-specific numbers, canonical baseline lives in BENCHMARKS.md.
4. **New scenarios — the full picture** (proposals; CORE = this train, P1 = this train if
   time allows, STRETCH = file as follow-up):
   - **CORE: read-only transaction throughput** — `begin → load(id) → commit` loop vs plain
     batched reads; the B2 headline — task 08 shipped its evidence as a one-off sweep run and
     a BENCHMARKS addendum; this scenario makes it a permanent, re-runnable part of the suite.
   - **CORE: journal on/off A/B on a DATA-heavy write pattern** — the W-3 addendum honestly
     showed no win on the index-heavy benchmark; a fragment-heavy scenario (large fragments,
     many fields) shows where `journalWrites` actually pays. This is the missing half of the
     W-3 verdict.
   - **CORE: durableWrites on/off** — the fsync cost, promised by S-2's acceptance, never
     canonically recorded.
   - **CORE: commit-batch scaling** — 10/100/500/1000 objects per transaction; the commit
     cost curve every embedder asks for.
   - **CORE: FP-1 shapes** — range (`GT`/`BETWEEN`) vs scan on the ordered index; `ORDER BY +
     LIMIT` paged reads. Tasks 09/10 already recorded their rows as per-feature BENCHMARKS
     sections; this task absorbs them into the canonical set under a unified naming/shape.
   - **P1: bounded concurrent-writer throughput** (4 writers, `@Tag("stress")` discipline,
     nightly/RC only — never the gate).
   - **P1: cold-start profile** — open time + first-save (makes codegen warm-up visible; the
     FP-3 AOT baseline for later).
   - **P1: reopen + read after shutdown** — canonical restart story (the sweep has the
     phases; port the numbers into the baseline set).
   - **STRETCH: N-scaling probe** — headline scenarios at N=50k + heap-after-load (makes the
     heap≈DB ceiling and the W-4 motivation visible in the docs).
5. **CI/nightly alignment** — the nightly smoke's row assertion (task 11) must match the final
   scenario list; this task delivers the list and updates the assertion together with task 11
   (or lands first and task 11 rebases onto it — one of the two, explicitly decided in the PR).

## Out of scope

- Any performance *work* (optimization itself — this task measures, it does not tune).
- Replacing the sweep with JMH (structural knob sweeps stay plain-Java — PERF §2's rationale
  stands: compile-time annotation values and per-config directories).
- Interpreting/fixing what the new numbers reveal — findings become separate issues.

## Acceptance criteria

- [x] One documented command (`scripts/run-benchmarks.sh [quick|full|sweep]`) runs the
      canonical suite; results + run manifests are machine-readable and archived
      (`docs/perf/canonical-baseline-2026-10-22/` = the referenced baseline result set;
      per-run archives under `target/bench-results/<utc>-<mode>/`).
- [x] BENCHMARKS.md: one canonical baseline table set (generated by
      `scripts/render-bench-baseline.py` from the archived full-run JSON); every number
      traceable to the result file + manifest; tables labeled mode/conditions/date; the
      2026-10-18 TRACE-era tables kept as **labeled history, marked superseded**.
- [x] All CORE scenarios exist in `XdStorageCanonicalBenchmark` (readOnlyTx; writeDataHeavy
      journal A/B on a fragment-heavy pattern; writeDurable A/B; commitBatch 10/100/500/1000;
      FP-1 shapes absorbed from the search-engine family) and are documented in
      BENCHMARKS.md with an honest first reading. **Stated budget:** ≤ 15 min quick (the
      task's cap); the FULL run measured **2m21s** — quick is strictly cheaper. P1 scenarios
      (bounded concurrent writers, cold-start, reopen) and the STRETCH N-scaling probe are
      NOT in this pass — they stay listed in the task as the follow-up set, deliberately
      (the four CORE A/B dimensions landed; the P1s add harness surface, not missing
      headline numbers).
- [x] The nightly assertion matches the final scenario list: the nightly smoke now runs
      `scripts/run-benchmarks.sh quick` (the unified entry point, `BENCH_MAX_LOADAVG=8`
      for dedicated CI runners) and asserts **9 canonical + 14 search-engine rows**.
      Coordination decision (as the task allowed): THIS task lands first; task 11 rebases
      onto the final list — the old `< 9` search-only assertion is superseded.
- [x] The suite stays surefire-invisible (`XdStorageCanonicalBenchmark` — no `Test` suffix);
      `mvn clean test` unchanged and green.
- [x] Full suite green (BUILD SUCCESS verdict — 777 tests, ×2 runs).

## Verification

```bash
bash scripts/run-benchmarks.sh quick        # the entry point (quick ≈ minutes)
bash scripts/run-benchmarks.sh full         # canonical baseline runs (~2.5 min)
python3 scripts/render-bench-baseline.py target/bench-results/<run>/jmh-full.json
mvn clean test                              # suite untouched
```

## Implementation notes (what landed, 2026-10-22)

- `XdStorageCanonicalBenchmark` — the CORE scenarios; `@Param`-driven A/B (journalWrites,
  durableWrites, batchSize) so JMH's JSON carries the dimensions machine-readably. The
  fragment-heavy entity (19 fields, long strings) + fragment size 25 = the DATA-heavy
  pattern the W-3 addendum lacked.
- The FP-1 shapes stay in `XdStorageSearchEngineBenchmark` (moving them = churn without
  information); the baseline doc absorbs their rows into the canonical table set — the
  "unified shape" is the doc + manifests, not a single class.
- First canonical reading (full run, archived): `readOnlyTx` 63.6k ops/s (B2's short path,
  permanent scenario); **`writeDurable` on/off = 47 vs 367 ops/s — the S-2 fsync cost is
  ~8×, canonically recorded at last**; `writeDataHeavy` journal on/off ≈ neutral at quick
  noise (the W-3 index-heavy verdict stands); `commitBatch` per-tx cost amortizes: objects/s
  RISES 1.2k → 11k from batch 10 → 1000.
- Quick-run discipline note: the archived full run used 1s measurement iterations (the
  classes' standing annotations) — honest ± errors are wide (±100% on slow scenarios);
  quote canonical numbers as ballpark until the iterations are widened (a one-line change
  in the classes' annotations when the first real regression needs it).

## References

- [BENCHMARKS.md](../../BENCHMARKS.md) (current scattered baseline + gotchas #1/#3)
- [PERFORMANCE-RECOMMENDATIONS.md](../../PERFORMANCE-RECOMMENDATIONS.md) §2 (methodology), §5.3
  (read-tx cost — the B2 scenario seed), §9 (the TRACE-logging admission)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (position in the train: first engine-lane item)
- Tasks 08/09/10 (already-done number producers — their addenda/sections get absorbed here)
- Task 11 (nightly assertion — depends on this task's scenario list)

Estimate: ~2 dev-days. Priority: **P0, ASAP** — lands before tasks 08–11 record or assert numbers.
