---
title: "[2.1.0][FP-1b] Query power pack II: ORDER BY, LIMIT/OFFSET, COUNT"
labels: [2.1.0-train, P1, search, engine, feature]
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

- [ ] Composition suite: range + ORDER BY + LIMIT/OFFSET on both id-index types; boundary
      cases (offset beyond end, LIMIT 0, negative arguments → loud validation errors);
      DESC correctness; empty results.
- [ ] Ordering-stability tests under concurrent commits (a second tx committing mid-read
      changes nothing in the already-open snapshot result).
- [ ] JMH: paged range query (LIMIT 50) on the B+tree path — the walk-termination win visible
      vs unbounded range; recorded in BENCHMARKS.md.
- [ ] README query section updated together with task 09's operator table; `CHANGELOG.md`
      "Added" entry.
- [ ] Full suite green ×2 (BUILD SUCCESS verdict).

## Verification

```bash
mvn clean test
java -cp "$CP" org.flib.xdstorage.performance.XdStorageSearchEngineBenchmark quick
```

## References

- Task 09 (the ordered fast path this composes with)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1; [ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) FP-1

Estimate: ~1–2 dev-days. Depends on: 09.
