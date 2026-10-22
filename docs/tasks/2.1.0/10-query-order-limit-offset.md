---
title: "[2.1.0][FP-1b] Query power pack II: ORDER BY, LIMIT/OFFSET, COUNT"
labels: [2.1.0-train, P1, search, engine, feature]
status: done
---

## Summary

Add result-shaping to the query model: `ORDER BY` (primary-key order first), `LIMIT`/`OFFSET`
pagination, and a `COUNT` convenience. Rides on task 09's ordered fast path.

## Why

The evaluation's FP-1 pack: range operators without ordering/pagination answer "which rows
match" but not "give me page 3" — together they are the SQL-shaped query surface embedders
expect. Scope honesty: the primary index is ordered natively; secondary-index ordering is NOT
free and must not be silently sorted. Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md)
§ Step 1 (FP-1 phase 1).

## Scope

1. **ORDER BY on the primary key** — natural leaf-chain order on the B+tree id index
   (ASC/DESC); on the Hash id index or when the query's primary is scan-path: explicit,
   documented fallback = collect + sort within the transaction snapshot (state the cost).
   Secondary-field ORDER BY is explicitly out of scope (document why: the search index orders
   by key trees, not arbitrary fields).
2. **LIMIT/OFFSET** — applied after ordering; composes with range operators (task 09) and with
   child/secondary criteria (filter-then-shape); early termination on the ordered walk where
   the plan allows (LIMIT on a seek path stops the walk — that is the point of combining them).
3. **COUNT(query)** — convenience returning the matched count without materializing objects
   (record-level, same snapshot semantics).
4. **Snapshot semantics pinned by tests**: ORDER BY reflects the transaction's snapshot;
   pagination across two transactions is NOT stable across commits (document; the repeatable
   read story stays what MVCC gives).

## Out of scope

- Secondary-field ordering; query-aware `watch`; streaming/lazy result APIs; keyset/cursor
  pagination (file a follow-up issue if the OFFSET cost matters at scale — with LIMIT +
  ordered walks, OFFSET re-walks; document that honestly).

## Acceptance criteria

- [x] **Composition suite** — `XdStorageQueryShapingTest` (12 tests): range + ORDER BY +
      LIMIT/OFFSET on both id-index types (Hash = `RangeOpsEntity`, B+Tree =
      `RangeOpsBTreeIdEntity`); boundary cases (offset past the end → empty page, LIMIT 0 →
      legal empty page, negative LIMIT/OFFSET → loud `XdStorageRuntimeException` at query
      construction); DESC correctness (exact mirror of ASC, DESC pagination from the tail);
      empty results under shaping; child-criterion filter-then-shape; union of two ranges
      ordered and paged; scan-path fallback ordering with the null contract (nulls are the
      greatest value).
- [x] **Ordering-stability under concurrent commits**: the open tx's ordered page does not
      see concurrently inserted objects (FP-1a's snapshot semantic, pinned for pages); a
      concurrent deletion IS reflected through the live index — the documented live-index
      exposure, asserted deterministically in the suite.
- [x] **JMH**: `searchByPrimaryBetweenPagedLimit50` — LIMIT 50 over the wide 100-key window
      runs ~25% ahead of the unbounded same-window range (quick mode: 15 719 vs 12 588 ops/s);
      recorded in [BENCHMARKS.md](../../BENCHMARKS.md) § FP-1b (quick rows 13 → 14; the
      nightly row assertion is a lower bound and stays valid).
- [x] README § Search engine updated (shaping contract + example next to the task 09 operator
      table); `CHANGELOG.md` "Added" FP-1 phase 2 entry; MASTER-PLAN § Step 1 phase-2 note.
- [x] Full suite green ×2 (BUILD SUCCESS verdict).

## Implementation notes

- `XdStorageSearchQuery.orderByPrimary(boolean)` / `limit(long)` / `offset(long)` — fluent,
  validated at construction; `hasShaping()` routes to the shaping-aware evaluation, the legacy
  path stays byte-compatible.
- LIMIT pushdown rides a public `XdStorageBTree.WalkTerminated` signal caught by `readRange`
  as normal completion (same lock-release protocol as the FP-1a upper-bound abort). ASC
  single-criterion walks count only snapshot-visible matches toward the page, so the page is
  exact; DESC must collect the full match set (singly linked leaf chain) and fetches only the
  page after sorting — documented, not silently optimized.
- `count()` ignores ORDER BY/LIMIT/OFFSET (SQL COUNT semantics) and counts through the same
  visibility read as `load`, so `count(q) == load(q).size()` on one snapshot.

## Verification

```bash
mvn clean test
mvn test-compile && java -cp target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test) \
     io.github.xdstorage.performance.XdStorageSearchEngineBenchmark quick
```

(The task's original snippet referenced the pre-2.0 `org.flib.xdstorage` package — the
namespace is `io.github.xdstorage` since 2.0.0.)

## References

- Task 09 (the ordered fast path this composes with)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1; [ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) FP-1

Estimate: ~1–2 dev-days. Depends on: 09.
