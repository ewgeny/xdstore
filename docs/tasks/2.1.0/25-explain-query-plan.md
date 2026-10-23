---
title: "[2.1.0][EXPLAIN] explain(): query plan visibility — index used, seek vs scan, records visited"
labels: [2.1.0-train, P1, search, engine, feature]
status: open
---

## Summary

A read-only query-plan API: `explain(Class, indexName, query)` returns the plan the engine
would take — which index, **seek vs ordered range walk vs scan**, the bound shape, and which
criteria ride as post-filters — without executing the query. The docs *tell* users scans are
O(dataset); the engine should *show* them when they wrote one.

## Why

Pulled from MASTER-PLAN Step 4 into this train (architect decision 2026-10-23): the
evaluation rated FP-2 "small, high-leverage", and FP-1a/FP-1b made it *timely* — the engine
now has three genuinely different query paths (index seek, ordered range walk with pushdown,
full scan) whose cost model DECISION-GUIDE.md advertises. `explain()` is the runtime face of
that cost model, it is purely additive (a read-only surface over the routing decision
`XdStorageSearchManager.handleQuery` already makes), and it protects users from the
accidental-scan class of performance bugs before they ship.

## Scope

1. **API** — recommend a plan-only facade method returning an `XdStorageQueryPlan`
   (value object: matched index name + id-index type, path enum
   `INDEX_SEEK / INDEX_RANGE_WALK / SCAN`, bound shape for ranges, the criteria tree summary
   marking which child/field criteria evaluate as post-filters on the fetched record, and
   the fallback reason when the plan degrades — e.g. "range on a Hash id index → scan",
   the FP-1a fallback contract made visible). A design note in the PR settles the exact
   shape; alternatives (plan attached to results, executed-variant with visited/elapsed
   metrics) evaluated but not built in v1.
2. **Single source of routing truth** — the plan builder must be *extracted from* the
   existing seek/scan branching in `XdStorageSearchManager`, not forked from it: `explain`
   and `load`/`count` must consult one decision function, or the plan will lie.
3. **Never executes**: no record materialization, no side effects; the static plan needs no
   transaction (index metadata is class-level) — an overload inside a tx is optional.
4. **Docs**: README § Search engine gains the plan section; DECISION-GUIDE's cost-model
   paragraph links it ("the engine can show you").

## Out of scope

- Executed-plan metrics (records visited, elapsed) — v2 if asked; the canonical benchmarks
  remain the measurement surface;
- `watch`/`count` plan variants beyond what falls out of the shared decision function;
- cost *estimates* (cardinality statistics) — plan shape only, no optimizer.

## Acceptance criteria

- [ ] Plan suite: `EQUALS` on a search index → INDEX_SEEK; `GT`/`BETWEEN`/`IN` on the
      ordered path → INDEX_RANGE_WALK (bound shape reported); no selective primary /
      `NOT_EQUALS` → SCAN; range with a **Hash id index** → SCAN with the fallback reason
      (the FP-1a matrix, made visible); child/field criteria listed as post-filters;
      BUG-E-fixed numeric seeks plan as seeks.
- [ ] Explain and execute agree: for a sampled query set, the plan's path matches the path
      the execution actually took (assert via the internal decision point — one test at the
      plumbing seam).
- [ ] No hot-path change: the JMH quick suite is unchanged (the plan builder runs only when
      called); full suite green ×2.
- [ ] README + DECISION-GUIDE updated; `CHANGELOG.md` "Added".

Estimate: ~1–2 dev-days (the routing extraction is the careful half).

## References

- [ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) FP-2 ("small, high-leverage")
- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) § the numbers table (the cost model explained
  makes visible)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) Step 4 — pulled into this train, 2026-10-23
