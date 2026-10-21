---
title: "[2.1.0][FP-11] Decision-guide page + runnable example app"
labels: [2.1.0-train, P1, docs, chore]
status: open
---

## Summary

Publish the architect evaluation's §3/§4 (competitive comparison + when-to-choose guidance) as
a docs page, ship a small runnable example application, and fix the README's test-count
staleness — the train's closing task, landing **after** the query pack so the guide describes
the capabilities that actually ship in 2.1.0.

## Why

The evaluation ([ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) § 2.3, FP-11) found
the project's niche invisible in the vocabulary users search for: no comparison page, no
"when to use / when not to use", no runnable end-to-end sample. The claims became publishable
the day 2.0.0 shipped with a license; they must reflect 2.1.0's range queries, or the page is
stale on arrival.

## Scope

1. **docs/DECISION-GUIDE.md** — adapted from the evaluation:
   - the comparison table (XdStorage vs MapDB, Xodus, RocksDB, LMDB, Chronicle Map, H2 MVStore,
     SQL engines, EclipseStore) with the honest order-of-magnitude framing,
   - "choose XdStorage when… / do not choose when…" checklists,
   - the positioning statement and the design point (10⁴–10⁵ objects/class; write-rate
     ceiling — **restate from the post-B2 measurements**, not from the evaluation's guess),
   - cross-links: BENCHMARKS.md, PERFORMANCE-RECOMMENDATIONS.md, EMBEDDING.md, FLAKY.md
     (the transparency pitch).
2. **Runnable example app** — `examples/quickstart/` (standalone Maven project, NOT in the
   reactor build): annotated entities, open → tx → save → indexed query incl. a range query →
   commit → reopen → load → shutdown; asserts its own behavior via tests; README with the five
   commands from clone to green. Follow EMBEDDING.md's lifecycle contract precisely — the
   example is the first code many embedders copy.
3. **README** — link the decision guide from the TOC; fix the "~630 tests" staleness (either
   auto-generate the number at build time or drop it — a hardcoded count rots again).

## Out of scope

- Benchmarks in the example (link the real ones); Spring/Quarkus flavors (FP-7 is demand-driven).

## Acceptance criteria

- [ ] `examples/quickstart` builds and its tests pass on a fresh clone (`mvn test` inside the
      directory) with only the published `io.github.xdstorage:xdstorage` dependency — the
      2.0.0 Central artifact or the 2.1.0 RC once available.
- [ ] The decision guide's performance statements cite BENCHMARKS.md sections, and every
      number matches a doc that exists.
- [ ] README links the guide; the stale test count is fixed or gone.
- [ ] Full main build untouched (`examples/` outside the reactor; `mvn clean test` at repo
      root unchanged).

## Verification

```bash
cd examples/quickstart && mvn test    # fresh-clone walkthrough
mvn clean test                        # repo root unchanged
```

## References

- [ARCHITECT-EVALUATION.md](../../ARCHITECT-EVALUATION.md) § 2.3, § 3, § 4, FP-11
- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (FP-11 — lands last in the train)

Estimate: ~1 dev-day. Depends on: 09, 10 (query pack capabilities).
