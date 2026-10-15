# XdStorage — Optimization Plan

Baseline: 449 tests / 4 red (`testOneThread` purge error, `DeleteIsolation`, `GraphPhantom`, `XdatStorageStressTest`).
Every fix below is gated by the three suites built in the previous sessions: YAML engine round-trip (11),
BTree split/join invariants (9) + mass-delete stability (6), ResourceCache MVCC/RC (12+11+10).

---

## 1. UNDO-CHANGE-LOG: XdStorageResourceCache

Baseline commit for the log: `f5fb65b` ("Timestamps from millis to nano", 1445 lines).
Current: `1ba4f17` + working tree (1685 lines). Each entry lists what changed, why, verification,
and the revert instruction. **Changes are independent unless noted; any entry can be reverted
without touching the others.**

### R-1. newObject visibility fallback (6 MVCC switches) — KEEP
- **What:** in the uncommitted-record branch of all 6 visibility switches:
  `object = getObject() != null ? getObject() : getNewObject()`.
- **Why:** `createInsertRecord` stores content in `newObject` with `object = null` until commit;
  readers of an uncommitted insert got `null`.
- **Verified:** phantom suite (RC-1..3, rollback test); real-cloner suite.
- **Revert:** restore `object = record.getObject();` in the 6 uncommitted branches.
- **Risk:** LOW. Note: `readUnsafe` intentionally unchanged (dirty-read semantics by design).

### R-2. IsolationLevel (SNAPSHOT default | READ_COMMITTED) — KEEP
- **What:** enum + `get/setIsolationLevel`; 12 version-selection points gated by
  `servesSnapshotVersion(tx, record)` (false in RC → latest committed `record.getObject()`).
- **Why:** user requirement — RC contract (dirty reads forbidden, non-repeatable reads and
  phantoms allowed).
- **Verified:** `XdatStorageResourceCacheReadCommittedTest` 9/9 (RC-1..9), snapshot suites green on
  default SNAPSHOT.
- **Revert:** `setIsolationLevel` → force SNAPSHOT; delete enum + helper + the 12 gates.
- **Risk:** LOW. Default unchanged.

### R-3. RC scan refresh (2 scan sites) — KEEP, but tighten
- **What:** full-scan and predicate-scan re-clone into the per-tx `readObjects` with `put()`
  (overwrite) when RC — removes the stale first-scan clone ("Stable_1" after foreign commit).
- **Verified:** RC-4 (non-repeatable read), RC-5 (phantom appears).
- **Revert:** restore `putIfAbsent` + drop the RC condition at both sites.
- **Risk:** MEDIUM — every scan re-clones every row in RC mode: O(N) clones per scan. Phase 2
  should reuse clones for unchanged records (compare record.version instead of identity).

### R-4. materializeForCache + isBTreeInfrastructureObject guard — KEEP
- **What:** `insert/update` store the **live instance** for XdStorageBTree/XdStorageBTreeNode
  (no unwrap-clone), so the node graph is never split between cache and memory.
- **Why:** YAML-era mass deletes corrupted trees whose root/firstLeaf pointed at clones.
- **Verified:** BTree-entity suite (live-graph identity tests), YAML storage tests.
- **Revert:** remove guard → restores clone semantics (breaks live-graph identity tests).
- **Risk:** committed infra records hold live instances: in-place mutations are NOT undone by
  record rollback (documented; `rollbackKeepsLiveGraph` asserts visibility only).

### R-5. Contract A: insert-record stamping (user) — KEEP
- **What:** `createInsertRecord` stamps `timestamp = tx.timestart`, `state = State.locked`;
  `markUpdate/markDelete` take the transaction.
- **Why:** insert-locked records are protected from foreign delete/modify at lock level.
- **Verified:** user's rewritten `uncommittedDeleteStaysVisible` (foreign delete of an
  uncommitted insert succeeds; reader sees null) + green resource suite.
- **Revert:** restore no-arg `markUpdate/markDelete` and `state = State.undefined`.

### R-6. REJECTED experiment: timestamp-equality clause in canBeChangedByTransaction — REMOVED
- **What (undone):** `state == locked && this.transaction == transaction &&
  transaction.getTimestart() == timestamp` in both overloads.
- **Why reverted:** after `record.lock()` adopts an orphaned record (dead owner), the record's
  timestamp still holds the *dead* tx's timestart → adopter could never modify the record →
  `DeadTransactionOrphan` red. The identity check `this.transaction == transaction` is sufficient.
- **Revert (if ever needed):** re-add the clause to both overloads; expect DeadTransactionOrphan
  to go red.

### R-7. Phase-2 fast-path refresh (update) — KEEP
- **What:** update fast-path refreshes the per-tx `readObjects` entry (put, not putIfAbsent)
  alongside `setNewObject(materializeForCache(...))`.
- **Verified:** own-mutation tests in BTree-entity and RC suites.

### R-8. Parent/tree pointer wiring in BTree navigation — KEEP (lives in XdStorageBTreeNode,
listed here because ResourceCache transit interacts with it)
- **What:** `child.setParent(current); child.setTree(current.getTree()); storage.update(child)`
  on every descent hop; `root/firstLeaf.setTree(this)` at BTree entry points.
- **Verified:** BTree invariants 9/9, mass-delete 6/6.
- **Revert:** unwrap `XdStorageBTreeNode.java` hunk; expect YAML-era graph-split anomalies to
  return.

---

## 2. PROJECT ANALYSIS

- **Scale:** 168 main classes / 118 test classes. God classes: `XdatStorageResourceCache` (1685
  lines, 51 public + 52 private members, 6 near-identical MVCC switches), `XdatStorageBTreeNode`
  (1351 — navigation + structural mutations + locking in one), `XdatStorage` (1265 — facade +
  dispatch + index/search orchestration).
- **Test health:** 449 tests, 4 red — all 4 in the delete-flow/multi-thread family
  (`testOneThread` purge error; `DeleteIsolation`, `GraphPhantom`, `XdatStorageStressTest`).
- **Architecture root cause (Phase 3b):** the id-index B+Tree is shared mutable in-memory state
  mutated in place, while the ResourceCache is per-transaction. Structural changes (split/merge)
  are immediately visible to all transactions → readers see torn/uncommitted topologies → the
  4 red stress tests. There is no structural versioning, and infrastructure back-references
  (`node.tree`) materialize as stubs/wrappers depending on path.
- **Serialization:** YAML engine fixed (reference dispatch + arrays); codegen caches wrappers in
  `gencode/`. Object model requires no-arg ctor + id accessors — documented.

---

## 3. OPTIMIZATION PLAN (executed in phases; each phase ends with all suites green)

### Phase A — Consolidate the undo-log (small, safe, do first)
1. Extract the 6 inline MVCC switches into one private
   `resolveVisible(tx, record, Object resolved)` — the branches are already semantically
   identical after R-1/R-2; one switch = one place to fix isolation bugs.
2. Move `IsolationLevel` to its own top-level file (`XdatStorageIsolationLevel.java`).
3. Kill the `==`-style dead experiments: confirm no timestamp-equality remnants (R-6 done).
4. Acceptance: resource suite 113/113, no behavior change (pure refactor).

### Phase B — Read-Committed completeness (medium)
1. Flat `read(id, tx)`: honor RC freshness (currently early-returns the first-scan clone →
   by-id reads are repeatable while scans are not — inconsistent). Keep identity within a
   single scan; RC scan-refresh already covers scans.
2. `readByReference`: same RC freshness (fill from `record.getObject()`, not stale transit).
3. `watch`: honor RC (watcher receives latest committed).
4. Acceptance: extend `XdatStorageResourceCacheReadCommittedTest` with by-id and
   readByReference non-repeatable-read cases; snapshot tests stay green.

### Phase C — Fix the remaining 4 red (the delete-flow / Phase 3b core)
1. Root cause: structural BTree changes are global; ResourceCache MVCC covers records, not
   topology. Implement a **canonical instance registry** for BTree infra (node id → live
   instance) so all transactions share one graph, plus **single-flight tree-record
   initialization** (the empty-tree race from the GraphPhantom diagnostics).
2. Make `readByReference` for infra records wire through the registry (removes
   observable-wrapper indirection for null-id nodes).
3. Make `delete` of an insert-locked record fail loudly per contract A (currently the lock
   timeout mask depends on mock vs real timeout).
4. Acceptance: `XdatStorageResourceCacheBTreeEntityTest` 8/8; `DeleteIsolation`,
   `GraphPhantom`, `testOneThread` green; BTree invariants + mass-delete stay green.

### Phase D — Performance (after correctness)
1. RC scan cost: reuse unchanged clones (compare record.version counter instead of re-cloning).
2. `blockingTime` map cleanup in `XdatStorageBTreeNode.lock` (leaks entries per waiting tx id).
3. Counter/index records: consider batching `storage.update` calls in hot insert paths.

---

## 4. Regression net (do not skip per phase)

`YamlEngineEntitiesRoundTripTest` + `XdatStorageBTreeSplitJoinInvariantTest` +
`XdatStorageBTreeMassDeleteStabilityTest` + `XdatStorageResourceCacheReadCommittedTest` +
`XdatStorageResourceCacheRealClonerPhantomTest` + `XdatStorageResourceCacheMethodCoverageTest` +
`XdatStorageResourceCacheBTreeEntityTest` + resource package + btree package — ~185 tests, all
green at plan start. Full suite target after Phase C: 449/449.
