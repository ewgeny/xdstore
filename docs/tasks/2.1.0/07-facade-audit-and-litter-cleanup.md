---
title: "[2.1.0][T-3b] Facade API audit (happy+failure matrix) and test-litter cleanup"
labels: [2.1.0-train, P1, testing, chore]
---

## Summary

Two halves: (a) prove every public `IXdFileStorage` method has at least one happy-path and one
failure-path test — the completeness audit the coverage map cannot give; (b) clean the test
litter accumulated across campaigns (`/temp` package, empty packages, the `/performance`
package's mixed content).

## Why

Coverage percentage says nothing about *which* methods are tested — an embedder's contract is
the facade, so the audit is per-method, not per-line. The litter is real: `temp/SpecificTest`
+ `Entity`/`EntityWrapper` sit in a package named for disposal; `entities`/`updaters` are
empty; `/performance` mixes stress families, genuine unit tests (BTreeGenerationStrategyTest,
ObjectGraphPolicyAndIndexTest, YamlRoundTripTest), a diagnostic (GraphFlowDiagnosticTest) and
JMH benchmarks. Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-3.

## Scope

1. **The facade matrix** — enumerate all `IXdFileStorage` (+ `XdStorageProvider`) public
   methods; for each: the test class/method that covers happy path and failure path. Gaps get
   filled (small focused tests; reuse entity helpers). The matrix goes into the PR — it is the
   deliverable, and future tasks update it.
2. **`/temp` dissolved** — `SpecificTest` is either a real missing case (relocate into the
   proper package, rename meaningfully) or dead (delete after confirming); the entity helpers
   move to wherever sibling tests keep theirs (or a shared test-infra location — do NOT
   rename `src/main/test` itself, F-6).
3. **Empty packages resolved** — `entities`, `updaters` (and `operation` if task 06 hasn't
   landed yet): fill per 06's plan or remove the package declaration.
4. **`/performance` content decision** — stress families get `@Tag("stress")` (task 05 did the
   tagging; this task confirms nothing load-bearing runs untaged in the default set);
   benchmarks (no `Test` suffix) stay surefire-invisible — document that in the package's
   package-info; `GraphFlowDiagnosticTest` gets a javadoc line saying what it diagnoses and
   when to run it.
5. All of it is test-side — **zero production-code changes** in this task.

## Out of scope

- The facade method's *semantics* changes (bugs found during the audit become separate issues
  with reproduction tests — file them, don't fix silently here).

## Acceptance criteria

- [ ] Facade matrix complete in the PR: no public method without named happy+failure tests
      (or an explicit linked issue explaining the gap).
- [ ] `/temp` gone; no empty test packages; `/performance` documented via package-info.
- [ ] Zero diffs under `src/main/java` (CI-enforceable by inspection of the PR file list).
- [ ] Full suite green ×2 (BUILD SUCCESS verdict); counts unchanged except added audit tests.

## Verification

```bash
git diff --stat main...HEAD -- src/main/java   # must be empty
mvn clean test && mvn clean test
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-3
- Tasks 04 (map), 05 (tags), 06 (package suites this builds on)

Estimate: ~1 dev-day. Depends on: 04, 05; partially 06.
