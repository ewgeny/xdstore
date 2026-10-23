---
title: "[2.1.0][DOCSLINT] Adoption-surface docs contract: links, cited benchmarks, and versions cannot rot"
labels: [2.1.0-train, P2, docs, ci, chore]
status: open
---

## Summary

The decision guide's own warning — *"a hardcoded count rots"* — applies to every claim the
adoption surface makes. Mechanize it: a lint test that keeps DECISION-GUIDE.md, the README,
and the quickstart example mechanically honest — every relative link resolves, every cited
benchmark scenario exists in the generated BENCHMARKS.md baseline, and the example's
dependency version matches the root pom.

## Why

DECISION-GUIDE.md is now the front door for evaluators: its comparison table, its
"where to go next" links, and its numbers table are promises. Docs rot silently (the README
test count rotted for months — task 12 found it). The T-2 precedent already established
source-walking lint tests in the default run; this applies the same discipline to the docs
the adoption depends on. Cheap, boring, and it deletes a whole class of stale-claim issues.

## Scope

1. **Lint test** (`XdStorageAdoptionDocsContractTest`, plain JUnit in the default run —
   the T-2 source-walking pattern, `Files.walk` from the repo root which exists locally and
   in CI checkouts):
   - **links**: every relative markdown link in `docs/DECISION-GUIDE.md`, `README.md`,
     `examples/quickstart/README.md` resolves to an existing file (skip http(s) anchors);
   - **cited scenarios**: every scenario name in the decision guide's numbers table exists
     as a row in BENCHMARKS.md's canonical table (parse the guide's table, grep BENCHMARKS.md);
   - **versions**: `examples/quickstart/pom.xml` `<xdstorage.version>` equals the root
     `pom.xml` `<version>`;
   - failure messages name the file, the broken link/claim, and the fix.
2. **CI**: nothing new — the test rides the default suite (and therefore the gate).

## Out of scope

- Prose/grammar checking; external URL liveness (network in tests is a flake vector);
  javadoc lint (the release profile's doclint tightening is a separate future item).

## Acceptance criteria

- [ ] The lint test is green on the current tree (all three contract dimensions).
- [ ] Deliberate breakage (rename a link target / cite a bogus scenario / bump the example
      version) fails with a message naming file and fix — verified locally, then reverted.
- [ ] `mvn clean test` impact: +1 fast test; BUILD SUCCESS verdict unchanged.

Estimate: ~0.5 dev-day.

## References

- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) — the surface this keeps honest
- T-2's `XdStorageTestGroupingContractTest` (the source-walking lint precedent)
- Task 12 (the guide + example whose promises this pins)
