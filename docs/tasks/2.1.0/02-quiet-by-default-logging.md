---
title: "[2.1.0][L-2] Quiet-by-default logging: demote per-operation INFO to DEBUG"
labels: [2.1.0-train, P0, logging, refactor]
status: done
---

## Summary

A library's default log level must be quiet. The engine currently logs INFO on **every**
operation (the R-24 "show-the-project log" campaign decision); demote the per-operation flow
to DEBUG and keep INFO for rare lifecycle events only.

## Why

Historical context: the INFO-per-operation flow was an ops/debugging tool built during the
R-24 campaign (full readable operation trace at default config). As a shipped library default
it is the log-spam complaint vector. The verbose trace must remain one config away — the
diagnostic example config from task 01 (`docs/examples/logging/log4j2-diagnostic.xml`) is that
config. Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track L-2.

## Scope

Demote to **DEBUG** (site-by-site, message text unchanged):

- `XdStorage` facade: INFO on every successful `save` (object + collection) and `delete`.
- `XdStorageResourceCache`: INFO commit/rollback summaries ("commit of tx X: N record(s)..."),
  `XdStorageResource.prepare` from-disk load lines (object counts).
- `XdStorageBTree`: INFO root creation and tree-emptied purge lines.
- Everything else INFO that fires per-operation rather than per-lifecycle — sweep the INFO
  sites with: "would a long-running production app emit this thousands of times per minute?"

Keep at **INFO**: storage open and shutdown (`XdStorageProvider`), journal checkpoint events,
and anything a 24/7 service should see about its own lifecycle. WARN/ERROR sites are untouched.

## Out of scope

- Message rewording, exception handling, metrics — levels only.
- The test-side log config semantics (covered by task 01).

## Acceptance criteria

- [x] Default-config consumer run: console shows only lifecycle INFO (open/shutdown/
      checkpoint) during a save/load/commit workload.
- [x] The diagnostic example config restores the full operation trace (verify by diffing
      against the pre-change trace of the same workload).
- [x] Suite log volume measured **before and after** at the same config (one full suite run
      each; the R-24 campaign measured ~3400 INFO lines / ~570 KB — record both numbers in
      the PR description).
- [x] `mvn clean test` green (BUILD SUCCESS verdict); no assertion anywhere depends on log
      output (check: no test reads the log file).
- [x] `CHANGELOG.md` "Changed" entry (Quiet-by-default bullet) matches what shipped.

## Verification

```bash
mvn clean test
# INFO-line count comparison, same workload, before vs after:
grep -c " INFO " xdstorage.log   # run before the change, then after; both numbers in the PR
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track L-2 (incl. the R-24 revision rationale)
- Task 01 (config layout this builds on)

Estimate: ~0.5 dev-day. Depends on: 01 (same files, landed first).
