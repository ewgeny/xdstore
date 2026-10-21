---
title: "[2.1.0][FP-1a] Query power pack I: GT/GTE/LT/LTE/BETWEEN/IN primaries on the ordered index"
labels: [2.1.0-train, P0, search, engine, feature]
---

## Summary

Add range operators to the criteria model and — when the primary criterion targets the
B+tree-backed id index — evaluate them as **ordered range seeks** instead of today's O(N)
scan. The tree is already ordered; this is the largest query-expressiveness gap vs SQL engines
identified in the architect's evaluation, and it is cheap to close.

## Why

Today every non-EQUALS primary criterion falls back to a full leaf-chain scan
([BENCHMARKS.md](../../BENCHMARKS.md): scans are ~3.5–4× slower than indexed lookups, and the
gap grows linearly with the dataset). The evaluation ([ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md)
FP-1) calls this pack "P0: cheap differentiator". Decision record:
[MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (FP-1, phase 1).

## Scope

1. **Operator surface** (`io.github.xdstorage.search.query`, `IXdStorageCriterionOperator` /
   `XdStoragePrimaryCriterion` / `XdStorageSearchQuery`): `GT`, `GTE`, `LT`, `LTE`,
   `BETWEEN` (inclusive bounds — pin the choice), `IN`. Composition with the existing
   AND/OR/NOT and child criteria keeps current semantics (secondary/child criteria are
   evaluated on the fetched record, unchanged).
2. **The ordered fast path** — primary-criterion range seeks on the B+tree id index
   (leaf-chain navigation from the seek point); GT/GTE/LT/LTE/BETWEEN map to bounded range
   walks, IN to a union of point seeks (sorted, deduplicated).
3. **Honest fallbacks, documented**: Hash id index ⇒ range operators evaluate as a scan with
   identical result semantics (the operator is API-wide; the index type decides the plan).
   A scan-path result must be indistinguishable from a seek-path result — same ordering
   contract as today (unordered/insertion unless task 10's ORDER BY is applied).
4. **Null and mixed-type contract, stated loudly**: nulls never satisfy range operators
   (document; `IS_NULL` remains the null test); comparisons are per-field-type (numbers
   numerically, strings lexicographically, Comparable contract otherwise); a comparable-type
   mismatch fails loudly, not silently-empty.
5. **Semantics note in the PR** — a short design section (operator table, comparison contract,
   fallback matrix). This is additive query surface, not W-4-class persisted-state change — a
   design *note*, not a design *round*.

## Out of scope

- `ORDER BY` / `LIMIT` / `OFFSET` / `COUNT` (task 10), query-aware `watch` (later), `explain()`
  (FP-2, Step 4), STARTS_WITH (evaluate during implementation — if it lands scan-path only,
  defer it to task 10's follow-up with a note).

## Acceptance criteria

- [ ] **Boundary-semantics suite** (the T-3c deliverable): empty ranges, degenerate BETWEEN
      (lo > hi), open/closed bound combinations, IN with duplicates/empty list, null handling
      for every operator, mixed-type loud failure, composition with AND/OR/NOT and child
      criteria, snapshot-isolation interplay (a range query inside a tx sees the snapshot).
- [ ] **JMH evidence**: a new scenario in `XdStorageSearchEngineBenchmark` (range on the
      B+tree id index) scales **sub-linearly vs the scan it replaces**; recorded in
      BENCHMARKS.md with the Hash-fallback numbers beside it.
- [ ] README § Search engine updated (operator table + fallback matrix).
- [ ] Full suite green ×2; no new FLAKY rows; `CHANGELOG.md` "Added" FP-1 entry updated.

## Verification

```bash
mvn clean test
mvn test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test)"
java -cp "$CP" org.flib.xdstorage.performance.XdStorageSearchEngineBenchmark quick
```

## References

- [ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) § 5 FP-1 (the why)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (FP-1 phase 1 + acceptance)
- [BENCHMARKS.md](../../BENCHMARKS.md) (scan-vs-index cost model to cite)

Estimate: ~2 dev-days.
