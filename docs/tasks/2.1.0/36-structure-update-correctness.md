---
title: "[2.1.0][STRUCTFIX] Structure-update correctness — the FAILURE-mislabel, equals/hashCode, the ignored tx parameter, and the dead transform override"
labels: [2.1.0-train, P2, engine, bug]
status: open
---

## Summary

Four findings of the 28-javadoc-coverage campaign, all in the stored-schema layer
(`structure/`, `structure/update/`):

1. **`XdStorageStructureManager.updateStructure` mislabels a prior success as FAILURE.** In the
   "script has been executed earlier" branch the code calls `setResult(res, ...)` where `res`
   still holds the initial `FAILURE` — `getResult()` answers FAILURE with the message
   "has been executed earlier" for a script that actually succeeded. Any caller branching on
   the result takes the failure path.
2. **`XdStorageClassStructure` and `XdStorageClassIndexStructure` override `equals` without
   `hashCode`.** Both are persisted `@XdStorageObjectId` records that end up in hash-based
   collections — an equals/hashCode contract violation with nondeterministic lookup behavior.
3. **`checkAndUpdateStructureIfNeed(Class<?>, XdStorageTransaction)` ignores its `tx`
   parameter** — the verification/migration actually runs in an internally created autonomous
   transaction on the engine executor, while `IXdFileStorage.executeStructureUpdate` requires
   the caller to be inside a transaction. Decide: honor the caller's transaction (2PC-atomic
   with the caller's work) or make the autonomous-transaction contract explicit — either way,
   today's signature suggests a guarantee the code does not give.
4. **`XdStorageSearchIndexStructureUpdater.transform(...)` is a dead override** — always
   returns `null` and is never invoked (`execute` is overridden and re-indexes via watchers),
   so the inherited record-rewrite machinery is unused here. Remove the override or wire it,
   per what the updater really needs.

## Scope

- (1) fix the result propagation + a regression test asserting SUCCESS with the
  "executed earlier" note;
- (2) add consistent `hashCode` to both records (equals semantics unchanged);
- (3) the decision first (one paragraph here or in the class doc), then the code/doc change —
  honoring the caller's transaction is the preferred outcome if the executor allows it;
- (4) delete the dead override (git keeps the history) or wire it, with the updater's
  re-index flow covered by an existing test (16's id-addressability suite is the closest).

## Acceptance criteria

- [ ] All four resolved with a test or an explicit recorded decision.
- [ ] `equals`/`hashCode` contract test on both structure records.
- [ ] Full suite green ×2.

Estimate: 1 d.

## References

- [28-javadoc-coverage](28-javadoc-coverage.md) — the campaign that surfaced all four
- [16-id-addressability-after-structure-update](16-id-addressability-after-structure-update.md)
  — the structure-update defect family this extends
