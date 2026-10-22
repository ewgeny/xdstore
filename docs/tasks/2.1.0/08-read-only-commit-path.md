---
title: "[2.1.0][B2] Read-only transaction commit path: skip 2PC record iteration for no-change transactions"
labels: [2.1.0-train, P0, engine, performance, feature]
status: done
---

## Summary

A `begin → load → commit` cycle with an empty write-set still walks the full 2PC record
iteration. Make the commit of a read-only transaction a cheap release path — measured at
~25 ms/op on the Hash path at N=5000 today, with **zero file write-outs** (the cost is pure
commit-protocol CPU scaling with resource-cache size).

## Why

[PERFORMANCE-RECOMMENDATIONS.md](../../PERFORMANCE-RECOMMENDATIONS.md) § 5.3 names this the
**top optimization candidate for read-heavy workloads**: request-scoped read transactions hit
a ~40 ops/s ceiling per storage at N≈5000 regardless of fragment size. The fix is confined to
the commit protocol (post-R-46: `io.github.xdstorage.resource.cache.XdStorageCacheCommitProtocol`)
— no persistence-semantics change, which is why it is safe for this train while W-4 is not.

## Scope

1. Detect the empty write-set (`hasChanges`-equivalent across the transaction workspace) and
   route commit to a short path that performs only the mandatory lifecycle work:
   record/lock release, workspace cleanup, active-thread accounting, commit-serial policy
   unchanged for writers.
2. Preserve observable contracts exactly:
   - rollback-only detection still throws on `commit` of a marked transaction;
   - timeout-sweeper semantics untouched (the R-31 reschedule path);
   - nested transactions: `skipOrWaitForInternalTransactions` still applies (a nested
     read-only tx must still be waited for by the parent);
   - metrics: `transactionsCommitted` still counts; `resourceWriteOuts` must NOT change.
3. Multi-thread transactions (activeThreads > 0) take the same short path on the last exit.

## Out of scope

- Any change for transactions with a non-empty write-set (the 2PC path is untouched).
- Cache eviction / disk-anchored anything (W-4 territory — frozen until its design round).

## Acceptance criteria

- [x] Dedicated tests: read-only tx commit releases locks/records identically to today —
      concurrent writer visibility unchanged; rollback-only still throws; nested read-only tx
      still waited for; metrics counters verified (committed +1, writeOuts +0).
      (`XdStorageReadOnlyCommitPathTest`, 5 tests green.)
- [x] Benchmark evidence: the `SweepFindTxVerify`-style harness (test sources,
      `performance/sweep/`) shows the Hash-path read tx at N=5000 dropping from ~25 ms/op to
      ≤1 ms/op; numbers recorded as a BENCHMARKS.md addendum **honestly** (the W-3 precedent —
      if it's less than predicted, say so).
      (Measured **0.69 ms/op at N=5000, 0.63–0.74 flat to N=40000, writeOuts=0 — the ~25 ms
      figure no longer reproduces post-R-59: the short path already shipped with the R-45/R-46
      rework; B2's deliverable = contract tests + the honest addendum, no code change.)
- [x] JMH facade flow (`XdStoragePerformanceBenchmark`) unchanged or better; no regression in
      the write scenarios. (quick: 132.9 ± 27.6 ops/s vs documented 127.3 ± 24.6 — same band.)
- [x] Full suite green ×2 (BUILD SUCCESS verdict); no new FLAKY rows. (719 tests, 0 failures — twice.)
- [x] `CHANGELOG.md` "Added" B2 entry matches what shipped.

## Verification

```bash
mvn test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test)"
java -cp "$CP" org.flib.xdstorage.performance.sweep.SweepFindTxVerify   # before/after numbers
mvn clean test
```

## References

- [PERFORMANCE-RECOMMENDATIONS.md](../../PERFORMANCE-RECOMMENDATIONS.md) § 5.3 (the evidence)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (B2) — FP-4's quick win
- R-46's collaborator split (where the change lives)

Estimate: ~1 dev-day.
