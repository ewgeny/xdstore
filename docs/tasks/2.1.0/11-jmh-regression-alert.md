---
title: "[2.1.0][D-4] JMH regression alert in the nightly: persist smoke scores, warn on drop"
labels: [2.1.0-train, P2, ci, performance, chore]
status: done
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

1. **Rebased onto task 13 (as that task's coordination note prescribed):** the nightly smoke
   now produces `jmh-quick.json` (machine-readable), so the extraction reads the JSON instead
   of scraping the console: `scripts/jmh-scores.py extract` → `jmh-scores-<date>.json`
   (scenario keys INCLUDE the @Param dimensions — journalWrites=true vs false compare
   like-for-like). The raw console log stays archived in the bench-results directory.
2. Upload: a rolling artifact `jmh-scores-latest` (last nightly wins, `overwrite: true`) +
   dated copies, both 90-day retention.
3. Comparison: the previous successful nightly run is located via `gh api`, its rolling
   artifact fetched with `actions/download-artifact@v4` (`run-id` input); if found,
   `scripts/jmh-scores.py compare` emits the delta table into the job summary and
   `::warning::` on any scenario >25% slower — plus a MISSING-scenario warning (the
   silent-skip gotcha #1 made loud). Missing artifact = skip silently.
4. Documented in BENCHMARKS.md § Canonical baseline (the Nightly regression watch block).

## Out of scope

- Failing the build on regression (deliberately — shared-runner noise).
- Full-mode benchmarks in CI (the 3-iteration quick smoke only); storing baselines outside
  Actions artifacts.

## Acceptance criteria

- [x] A nightly run produces the dated scores artifact + a delta table in the job summary
      (the steps are wired; the first scheduled run exercises them end-to-end — the workflow
      itself can only be verified on GitHub, per the task's own verification note).
- [x] Simulated regression triggers the warning: verified locally with a poisoned-previous
      replay — a −50% `readOnlyTx` warns, a −10% drop stays silent (below the threshold), a
      vanished scenario warns (the silent-skip gotcha made loud), and identical sets produce
      zero alerts.
- [x] Gate job untouched (only the nightly job changed); the added steps are seconds of
      work on an existing artifact.
- [x] BENCHMARKS.md documents the alert and where artifacts live.

## Verification

```bash
python3 scripts/jmh-scores.py extract docs/perf/canonical-baseline-2026-10-22/jmh-full.json /tmp/scores.json
python3 scripts/jmh-scores.py compare /tmp/scores.json /tmp/scores.json          # identical -> no alerts
# poisoned-previous replay (a -50% scenario in the 'new' file) -> one ::warning::  (verified)
bash scripts/run-benchmarks.sh quick                                             # the real smoke
```

## References

- [BENCHMARKS.md](../../BENCHMARKS.md) (gotcha #1 — silent skips; the existing row assertion)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track D-4

Estimate: ~0.5–1 dev-day. Depends on: 13 (the unified scenario list the assertion must match).
