# XdStorage — Optimization Plan

Baseline (2026-10-16, re-measured at HEAD `158a191`, full `mvn test` with retained surefire reports):
**469 tests / 7 red** — the Phase-3b stress family (`DeleteIsolation`, `GraphPhantom`,
`PhantomReadIsolation` (flaky under full-suite load), `XdStorageStressTest`,
`XmlDataStorageMultithreadsTest` ×2) plus the new `XdStorageCompositeObjectPersistentTest.step2`
boolean-field distortion (`isActive` expected true, was false on reload — under investigation by
the author).

**Status after the 2026-10-16 MVCC session (R-13..R-16 below): 469 tests / 4 stable red**
(CompositeObjectPersistent boolean + DeleteIsolation + GraphPhantom + StressTest),
2 flaky (PhantomReadIsolation 2/3, Xml `testMultithreads` 1/3, integration contention 1/3).
`testOneThread` — historically the most stubborn — is stable GREEN. The DeleteIsolation
materialization invariant (scan size == materialized count, no null links) holds across
~17k scans per run; the residual red is readers crashing on **dangling B+Tree child refs
during descent** (`cannot load by reference XdStorageBTreeNodeSimpleWrapper` from
`loadChildOfThisNode`) — per-tx wrapper-graph staleness, i.e. the Phase-3b core: canonical
per-id instance registry (single-flight init already done) + re-land of canonical sharing.
Every fix below is gated by the suites built in the previous sessions: YAML engine round-trip (11),
BTree split/join invariants (9) + mass-delete stability (6), ResourceCache MVCC/RC (12+11+10+1).

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

### R-6. REJECTED experiment: timestamp-equality clause in canBeChangedByTransaction — REMOVED, then RE-INTRODUCED (verified harmless)
- **What (undone):** `state == locked && this.transaction == transaction &&
  transaction.getTimestart() == timestamp` in both overloads.
- **Why reverted:** after `record.lock()` adopts an orphaned record (dead owner), the record's
  timestamp still holds the *dead* tx's timestart → adopter could never modify the record →
  `DeadTransactionOrphan` red. The identity check `this.transaction == transaction` is sufficient.
- **UPDATE (2026-10-16): the clause is BACK** at HEAD `158a191` — both `canBeChangedByTransaction`
  overloads (cache:1473/1492) and `isChangedByTransaction` (1507), framed by an «ИСПРАВЛЕНИЕ» comment.
  Verified **not** a live regression: `XdStorageResourceCacheSomeCasesTest` 6/6 (1 skipped). It works
  because of check ordering: the `!isOwnerTxAlive` escape fires *before* lock (while the dead tx still
  owns the record), and `markUpdate/markDelete` re-stamp `timestamp = adopter.getTimestart()` after
  `lock()` takes over — `lock()` itself does NOT re-stamp on adoption. Any new adoption path that
  checks `canBeChangedByTransaction` between lock and markUpdate will hit the original failure.

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

### R-9. watch() RC freshness — KEEP (closes Phase B item 3, 2026-10-16)
- **What:** in `watchInternal`, a materialized copy whose `recordVersion != record.getVersion()`
  is dropped and re-cloned under READ_COMMITTED (mirrors `readInternal(id)`); the fresh clone is
  put back into the per-tx read-set with the version stamped. SNAPSHOT behavior unchanged
  (stale copy still served = repeatable reads).
- **Verified:** new `RC-11 watchSeesFreshCommittedUpdate` (XStorageResourceCacheReadCommittedTest
  11/11; proven valid — goes red with the refresh condition disabled).
- **Revert:** delete the two `isolationLevel == XdStorageIsolationLevel.READ_COMMITTED` blocks
  in `watchInternal`.

### R-10. IsolationLevel extracted to top-level `XdStorageIsolationLevel` + volatile — KEEP (Phase A item 2, 2026-10-16)
- **What:** enum moved out of `XdStorageResourceCache` (same package, Russian javadoc preserved);
  the `isolationLevel` field made `volatile` (was a plain field mutated via setter); references
  updated in BTree/hash index resources and the RC test. Pure refactor + visibility fix.
- **Revert:** re-nest the enum inside `XdStorageResourceCache` and drop `volatile`.

### R-11. Crash-safe resource write-out (atomic rename) — KEEP (2026-10-16)
- **What:** all three `writeObjects` (`XdStorageResource`, `XdStorageBTreeIndexResource`,
  `XdStorageHashIndexResource`) now write to `<file>.tmp` via the new
  `XdStorageAtomicFile.writeReplacing` and swap with `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)`
  (plain-move fallback where atomic rename is unsupported). The old delete-then-recreate scheme
  lost the whole resource file if the process died mid-commit. Empty-resource semantics unchanged
  (empty cache = no file).
- **Verified:** full suite green on all persistence-heavy tests (XML/YAML storage, BTree/hash
  index writes, rollback-failed-commit rewrites).
- **Revert:** restore the delete-then-create bodies (re-adds the in-code TODO).

### R-12. Ops hygiene: log level INFO, log4j-core 2.23.1, .gitignore — KEEP (2026-10-16)
- **What:** `log4j2.xml` DEBUG→INFO (DEBUG logged every MVCC switch — GB-scale logs per stress
  run and heavy I/O slowdown; full suite runs 2:24 at INFO); `log4j-core` 2.11.1→2.23.1
  (Log4Shell CVE-2021-44228 family); new `.gitignore` (`target/`, `gencode/`, `teststorage/`,
  `xdstorage.log`, `.idea/`, `*.tmp`).
- **Verified:** full suite 469 tests / 7 red (same set as baseline minus the contention
  integration test) — no test asserts on log output.
- **Revert:** restore DEBUG levels / 2.11.1 / delete `.gitignore`.

### R-13. Snapshot-serial MVCC gate (commit-order visibility) — KEEP (2026-10-16)
- **What:** the old visibility gates compared reader `timestart` against `record.timestamp` —
  the OWNER'S BEGIN time. Any reader that began inside the writer's `[begin, commit)` window was
  treated as "after the version" and served the new value: committed deletes returned null to
  snapshot readers (DeleteIsolation N-1 losses), committed inserts leaked as phantoms
  (GraphPhantom leaks). Fix: a global commit counter (`XdStorageTransaction.COMMIT_SEQUENCE`,
  starts at 1) — `CacheRecord.commit()` stamps `commitSerial`; every real transaction stamps
  `snapshotSerial` at construction; `CacheRecord.servesCommittedVersion` gates committed
  records by `readerSerial < commitSerial` (exact commit-order snapshot), locked records and
  mock transactions (serial 0/null — Mockito returns 0L for boxed Long) fall back to the old
  timestart comparison. `getObjectForTransaction` now checks own-changes FIRST, then the gate.
- **Verified:** RC/snapshot/MVCC/real-cloner/BTree-entity/collection/consistency suites 90/90;
  DeleteIsolation materialization invariant holds (~17k scans/run, was 10+ violations/run);
  `XmlDataStorageMultithreadsTest` pair and `PhantomReadIsolationStressTest` flipped green
  (flaky 1-2/3 since); `testOneThread` stable green.
- **Revert:** drop `commitSerial`/`snapshotSerial` and restore the timestart-only comparison in
  `getObjectForTransaction`.

### R-14. Seen-keys snapshot fallback in id-index by-id — KEEP (2026-10-16)
- **What:** tree-key removal is not versioned — a foreign delete-commit removes the key from
  the B+Tree permanently, so a reader whose scan saw the key got a tree-miss on later by-id
  (`tree.find` short-circuits before the data cache can serve the materialized pre-image).
  `XdStorageBTreeIndexResource` now records `txId → (objectId → resourceId)` for every visible
  key it scans/reads (`rememberSeenKey`) and `getObjectResourceId` falls back to that map on a
  tree miss; cleared in `release`/`rollback`/`rollbackPerformingFirstPhaseCommit`.
- **Verified:** full suite green on all btree/resource/assertion suites; DeleteIsolation scan
  invariants hold.
- **Revert:** remove the map + the fallback in `getObjectResourceId`.

### R-15. Write-out integrity: readUnsafe serves latest committed — KEEP (2026-10-16)
- **What:** `readUnsafeInternal`'s steady-state branch routed through the snapshot switch — a
  committer that began BEFORE a neighbor's commit would write the neighbor's OLD value into the
  resource file (latent resurrection bug, amplified by R-13). The else-branch now serves
  `record.getObject()` (latest committed) for foreign records; own changes still go through
  the own-change branches.
- **Verified:** ConsistencyTest two-phase commit tests green; full suite green.
- **Revert:** restore `record.getObjectForTransaction(transaction)` in the else-branch.

### R-16. blockingTime waiter map leak — KEEP (Phase D-2, 2026-10-16)
- **What:** `CacheRecord.lock` removes the waiter's timing entry after acquisition (was: one
  leaked entry per (record, waiting tx) forever).
- **Revert:** delete the `blockingTime.remove(...)` after `locked.set(true)`.

### R-17. Structural diagnostics + separator/children invariants (testOneThread investigation) — KEEP (2026-10-16)
- **What:** flag-gated diagnostics `-Dxdstorage.chain.debug=true` in the B+Tree (chain rewrites
  at splits/joins with parent state, delete-miss dumps, update-miss stacks) + TWO new validator
  checks in `descendCollectingLeaves`: separator strict monotonicity and children == keys + 1
  per internal node. Both fire only on real corruption (btree suites 45/45, full suite unchanged).
- **Why — the testOneThread investigation (10 isolated runs: 5 failed, all in the afterEach
  single-threaded purge):** two visible symptoms, ONE defect. (a) validator divergence
  `листьев только из корня: [41]` — a leaf orphaned from the chain: `JOIN-LEFT left=40
  absorbing this=45` relinked `40.next=45.next` while leaf 41 still sat between them in the
  chain — the join follows PARENT-ARRAY adjacency, not chain adjacency. (b) `does not exists`
  (thrown by `XdStorageBTreeNode.deleteFromThisNode:1172`) — the descent delivered a delete to
  a leaf whose key range does not contain the key. The captured first domino: a parent with a
  DUPLICATE separator (647, 647) and an out-of-key-order child (44 between 36 and 37); the new
  invariant catches it earlier as `children=39 при keys=37` — a join/borrow adds children
  without the matching key updates. After the misalignment: descents misroute → wrong-leaf
  deletes → wrong-neighbor joins → chain orphans.
- **Flakiness mechanism:** galaxy UUID ids differ per JVM → save order of systems/planets
  differs → planet-tree shape differs → ~50% of shapes hit the corrupting cascade. In-suite
  runs pass because the Hi-Lo id counter is pre-advanced by earlier test classes (different
  shape — luck, not correctness).
- **Next:** find the exact join/borrow site that desyncs (keys, children) — instrument with the
  new invariant as the tripwire; same Phase-3b structural family.
- **Revert:** remove the CHAIN_DEBUG blocks + the two new checks in `descendCollectingLeaves`.

---

## 2. PROJECT ANALYSIS

- **Scale:** 168 main classes / 118 test classes. God classes: `XdStorageResourceCache` (~1730
  lines — the 6 MVCC read sites now delegate the core visibility switch to the consolidated
  `CacheRecord.getObjectForTransaction`, but each site keeps its own loop/materialize/RC-refresh
  scaffolding), `XdStorageBTreeNode`
  (1351 — navigation + structural mutations + locking in one), `XdStorage` (1265 — facade +
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

### Phase A — Consolidate the undo-log — **DONE (in a different shape than planned)**
1. ~~Extract the 6 inline MVCC switches~~ — **DONE**: the USER consolidated the core switch into
   the per-record `CacheRecord.getObjectForTransaction(tx)` (all 6 sites delegate to it); the
   per-site scaffolding (loop over records, materialization, RC refresh) remains duplicated —
   acceptable.
2. Move `IsolationLevel` to its own top-level file — **DONE 2026-10-16** (R-10, incl. `volatile`).
3. Kill the `==`-style dead experiments: R-6 clause re-introduced by the user — verified harmless
   (see R-6 UPDATE); no other remnants.
4. Acceptance: resource suite green, no behavior change — **met**.

### Phase B — Read-Committed completeness — **DONE**
1. Flat `read(id, tx)` RC freshness — **DONE earlier** (version/recordVersion invalidation).
2. `readByReference` RC freshness — **DONE earlier**.
3. `watch` RC freshness — **DONE 2026-10-16** (R-9, pinned by RC-11).
4. Acceptance: RC suite green (now 11 tests incl. RC-11), snapshot tests stay green — **met**.

### Phase C — Fix the remaining red (the delete-flow / Phase 3b core) — **SUBSTANTIAL PROGRESS, residual is the 3b core**
1. Single-flight tree-record initialization — **DONE** (`createTreeLock` double-check in
   `XdStorageBTreeIndexResource.insert`). The **canonical instance registry was NOT implemented** —
   the shipped approach is the R-4 live-instance guard instead; structural BTree changes are still
   global and unversioned.
2. `readByReference` infra wiring — **DONE earlier** (unwrap via `getWrappedObjectOrSameObject`).
3. Contract-A delete of insert-locked records — **DONE earlier** (R-5 stamping).
4. Snapshot visibility of the delete/insert window — **DONE 2026-10-16** (R-13/R-14/R-15):
   the record-level MVCC window is closed and the DeleteIsolation materialization invariant
   holds; `testOneThread` stable green.
5. **Residual (stable red 3/3):** readers descend a per-tx WRAPPER clone of the node graph that
   never re-syncs — after foreign join cascades the clone holds dangling child ids and
   `loadChildOfThisNode` throws `cannot load by reference XdStorageBTreeNodeSimpleWrapper`.
   Next step (Phase 3b proper, the plan's original item 1): **canonical per-id instance
   registry** for tree/node records so all transactions traverse ONE live graph (the RW-lock +
   retry-from-root budgets already handle transient states), then delete-isolation of STRUCTURE
   itself (defer structural deletes or version the topology) — record-level MVCC cannot fix
   torn topology. `StressTest` (cyclic locker deadlock) belongs to the same liveness family.

### Phase D — Performance (after correctness)
1. RC scan cost: reuse unchanged clones (the by-id read and `watch` compare
   `recordVersion`; the full/predicate scans still re-clone every row in RC — open).
2. `blockingTime` map cleanup in `CacheRecord.lock` — **DONE 2026-10-16** (R-16).
3. Counter/index records: consider batching `storage.update` calls in hot insert paths.

---

## 4. Regression net (do not skip per phase)

`YamlEngineEntitiesRoundTripTest` + `XdStorageBTreeSplitJoinInvariantTest` +
`XdStorageBTreeMassDeleteStabilityTest` + `XdStorageResourceCacheReadCommittedTest` +
`XdStorageResourceCacheRealClonerPhantomTest` + `XdStorageResourceCacheMethodCoverageTest` +
`XdStorageResourceCacheBTreeEntityTest` + resource package + btree package — all green at
2026-10-16 (full suite 469 tests, 7 red = the Phase-3b stress family + the new boolean-distortion
serialization test; assertion suites 100% green).
