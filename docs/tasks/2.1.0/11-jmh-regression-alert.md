---
title: "[2.1.0][D-4] JMH regression alert in the nightly: persist smoke scores, warn on drop"
labels: [2.1.0-train, P2, ci, performance, chore]
status: open
---

## Summary

The nightly already runs the JMH smoke with a row-count assertion (the silent-skip guard).
Extend it: persist per-scenario scores as dated artifacts and compare against the last week's
scores, **warning** on a >25% drop. Detection, not gating — false positives from shared
runners are expected; the alert is a signal to re-measure on a quiet machine, exactly as
BENCHMARKS.md prescribes.

## Why

BENCHMARKS.md is a manual discipline today; a regression (e.g., from a logging or commit-path
change) surfaces only when someone re-runs JMH by hand. The nightly already pays the smoke's
cost — this makes its numbers accumulate. Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md)
§ Track D-4.

## Scope

1. `.github/workflows/ci.yml` nightly job: after the existing smoke step, extract
   `Benchmark :: Score :: Error` rows from the JMH output into `jmh-scores-YYYY-MM-DD.json`
   (scenario → score/error/unit; keep the raw console log too).
2. Upload as a workflow artifact with a generous retention (90 days).
3. Comparison step: fetch the most recent previous artifact (actions/download-artifact with a
   name lookup); if found and comparable, emit the delta table into the job summary and
   `::warning::` on any scenario >25% slower. Missing artifact = skip silently (first runs,
   pruned history).
4. Document the semantics in BENCHMARKS.md: what the alert means, the quiet-machine
   re-measurement rule, and that the gate job is unaffected.

## Out of scope

- Failing the build on regression (deliberately — shared-runner noise).
- Full-mode benchmarks in CI (the 3-iteration quick smoke only); storing baselines outside
  Actions artifacts.

## Acceptance criteria

- [ ] A nightly run produces the dated scores artifact + a delta table in the job summary.
- [ ] Simulated regression (temporarily inflate one scenario's cost in a scratch branch, or
      replay a poisoned artifact) triggers the warning.
- [ ] Gate job untouched; nightly runtime growth bounded (~+minutes for the extra steps).
- [ ] BENCHMARKS.md documents the alert and where artifacts live.

## Verification

```bash
# locally, the extraction logic against a smoke run's output:
java -cp "$CP" org.flib.xdstorage.performance.XdStorageSearchEngineBenchmark quick > /tmp/jmh.txt
# ... plus the workflow run on your fork before opening the PR
```

## References

- [BENCHMARKS.md](../../BENCHMARKS.md) (gotcha #1 — silent skips; the existing row assertion)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track D-4

Estimate: ~0.5–1 dev-day.
