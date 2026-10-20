# XdStorage — Optimization Plan

> Whole-project SRE view (durability, lifecycle, observability, build/CI hygiene, write-path
> design): see [SRE-OPTIMIZATION-PLAN.md](SRE-OPTIMIZATION-PLAN.md). This file remains the
> engine-internal campaign journal (R-entries) and the phased engine plan.
> Consolidated forward-looking roadmap (priorities, frozen-items inventory, release train):
> [MASTER-PLAN.md](MASTER-PLAN.md).

JMH baselines (search engine via facade, resource cache, facade tx flow): see [BENCHMARKS.md](BENCHMARKS.md)
(recorded 2026-10-18, JDK 17).

Baseline (2026-10-16, re-measured at HEAD `158a191`, full `mvn test` with retained surefire reports):
**469 tests / 7 red** — the Phase-3b stress family (`DeleteIsolation`, `GraphPhantom`,
`PhantomReadIsolation` (flaky under full-suite load), `XdStorageStressTest`,
`XmlDataStorageMultithreadsTest` ×2) plus the new `XdStorageCompositeObjectPersistentTest.step2`
boolean-field distortion (`isActive` expected true, was false on reload — under investigation by
the author).

**Status after the 2026-10-16/17 sessions (R-13..R-27 below): 470 tests in 1:48 — the definitive
final measurement. Only 2 red, both flaky concurrent** (`GraphPhantom`, Xml `testMultithreads` —
each green in isolated runs; the shared residual is sustained-write read-lock starvation during
mass-delete cascades, see R-26/R-29). `StressTest`, `DeleteIsolation`, `PhantomReadIsolation`,
`testOneThread`, the boolean test (R-22), and every assertion suite are GREEN.
The campaign fixed 6 of the original 8 stable reds and halved the remaining flake set.
NEXT OPEN ITEM: R-29 (remove tree-lock contention: shallower deep-lock chains / per-node
striping / FIFO ticket latches — see R-26 for the rejected wait-rescheduling approaches).
Also open: frozen cloner pass-through (re-freeze decision), D-3 batching design.
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
- **Next:** ~~find the exact join/borrow site that desyncs (keys, children)~~ **RESOLVED — see R-18.**
- **Revert:** remove the CHAIN_DEBUG blocks + the two new checks in `descendCollectingLeaves`.

### R-18. Tripwire + two structural root-cause fixes (join separator, split double-remove) — KEEP (2026-10-16)
- **What:**
  1. **Tripwire** `XdStorageBTreeInvariants.checkStructuralAlignment(node, op)` — called at the
     end of EVERY structural mutation (joinWithRight/Left, both borrows, root-collapse):
     separators monotone (equal allowed for multiple-trees), children == keys + 1, no duplicate
     child ids. Fires immediately with the op name; btree suites stay green on healthy trees.
  2. **Join separator fix:** both joins demoted the separator via `findLeftKey` (a leftmost
     descent that returns null on transitively-empty nodes → merge WITHOUT the separator →
     children = keys + 2) and then subtracted a second key with a clamp-hack. Now: the
     separator is exactly `parent.keys[currentNodeIndex | i-1]` — removed from the parent
     always, demoted into the survivor for internal merges, dropped for leaf merges.
  3. **Split double-remove fix (deterministic, duplicate trees):** internal splits removed
     `midKey` TWICE — once by the list iterator in the last-child migration block, once by
     `this.getKeys().remove(midKey)` (first-equal-occurrence scan). For unique trees the second
     remove silently no-ops; for multiple trees it eats another equal separator → the split node
     keeps `keys = children - 1`. The redundant second removal is gone from both
     `splitAndInsertIntoParent` and `splitAndCreateNewRoot`.
  4. The new validator checks + tripwire are multiple-aware (equal separators legal when
     `tree.isMultiple()`).
- **Verified:** `duplicateKeysSurviveSplitAndJoin` (previously silent corruption, caught by the
  tripwire) green; btree suites 45/45; **`XmlDataStorageMultithreadsTest#testOneThread` 10/10
  isolated runs green (was 5/10 failing) + 3/3 in-suite**; full suite 471 tests — stable red
  unchanged (DeleteIsolation/GraphPhantom/StressTest = concurrent wrapper-staleness family,
  CompositeObject boolean), `PhantomReadIsolation`/`Xml.testMultithreads`/integration remain
  flaky 1-2/3.
- **Revert:** restore `findLeftKey` demote + clamp key removal in the joins, restore the
  `remove(midKey)` lines in both splits, drop the tripwire calls.

### R-19. Phase 3b canonical-graph re-land: IMPLEMENTED, then FROZEN behind comments — KEEP as groundwork (2026-10-16)
- **What shipped (all flag/comment-gated, zero behavior change at HEAD):**
  1. `XdStorageResourceCache.infrastructureCache` flag (class-name based) + live-instance read
     branches in `readInternal(id)`/`readByReferenceInternal` + the scoped bounded wait
     `waitForRecordResolution` (250ms cap, 5ms polls, `isInvisibleUncommittedMutation` predicate)
     re-enabled for infra caches — ALL COMMENTED OUT.
  2. `XdStorageSmartCloner.fillAndWrapForOneLevel`: canonical pass-through block re-landed and
     RE-COMMENTED (SimpleWrapper refs re-anchor inner to the live canonical via
     `XdStorageWrapperFieldLocator`; packFields of the live canonical stays forbidden).
  3. CHAIN_DEBUG find-state dump in `BTreeIndexResource.getObjectResourceId`.
- **Why frozen — the blocking finding:** with live-serving ON, all 96 resource suites passed and
  `DeleteIsolation` flipped GREEN, but the generation/giant/Xml family broke. Evidence
  (`[CHAIN] find id=200 tree@… root=2 rootKeys=[] firstLeaf=1 firstLeafKeys=0`): the reader's
  tree record holds the **prepare()-parsed COPY of the tree** (root/firstLeaf are unresolved
  id-stubs), while the writer mutated a different in-memory instance — the instance duality
  lives at the RESOURCE-PREPARE layer, not only in the cloner. Canonical serving without
  **re-wiring parsed graphs at prepare()** (node.tree/parent/next-chain + root/firstLeaf
  resolution to canonical instances) feeds readers bare stubs. The cloner-only swap additionally
  broke lazy-getter and observable-wrapper flows (mid-flight inner swap).
- **Phase 3b remaining design (next session):** (1) at resource `prepare()`/`fillCache`, re-wire
  parsed infra graphs to the canonical in-memory instances (registry keyed by (class, id)); (2)
  re-enable the live-serving branches + waits (they are written and safe per the 96/96 gate);
  (3) the cloner pass-through last, after wrappers no longer carry per-tx graphs.
- **Revert:** delete the commented blocks + the flag + the wait helper + the find dump.

### R-20. Phase 3b LANDED: canonical instance registry + live-serving + waits — KEEP (2026-10-16 night)
- **What:**
  1. **`XdStorageCanonicalInstanceRegistry`** (per-storage, held by `XdStorageServicesLocator`,
     cleared on shutdown): (class, id) → canonical live instance. Adoption hooks:
     `fillCache`/`fillCacheOnlyIfNotExists`/`fillAndCloneCacheOnlyIfNotExists` — a prepare()-parsed
     copy YIELDS to the registered live instance (this was the missing piece from R-19: readers
     got stub-wired parsed copies while the writer mutated a different instance);
     `materializeForCache` — the writer's live instance registers as canonical at first write.
     Mock locators (null registry) skip adoption — unit suites unaffected.
  2. **Live-serving un-frozen**: the infra branches in `readInternal(id)`/`readByReferenceInternal`
     (serve the canonical graph, no clones, own-uncommitted via newObject) and the scoped bounded
     wait `waitForRecordResolution` (250ms cap) are ACTIVE for infra caches.
  3. **Cloner pass-through: tried and RE-FROZEN** — with the registry in place it still deadlocks
     the concurrent wrapper flows (mid-flight inner swap; run hung in `testMultithreads`). The
     packFields-into-wrapper-snapshot it would replace is now a much smaller window; revisit only
     after wrappers stop carrying per-tx graphs.
- **Verified:** strategy/giant gate 7/7 (the first re-land's casualties), resource+btree gate
  148/148, full suite ×3: **469 tests / 4→3 red** — `DeleteIsolation` and
  `PhantomReadIsolation` are **STABLE GREEN for the first time in project history**;
  `testOneThread` green. Remaining stable red: `GraphPhantom` (writer-side), `StressTest`
  (cyclic locker deadlock), `CompositeObject` boolean (author's), `Xml.testMultithreads`
  flaky ~1/3 (concurrent purge vs rolled-back tx: "cannot load by reference" during afterEach).
- **Revert:** delete the registry class + the locator field, un-adopt fill paths, re-comment the
  infra branches/waits (all changes are localized and marked ФАЗА 3B).

### R-21. Phase D-1: RC scan version-reuse — KEEP (2026-10-16 night)
- **What:** the predicate and full scans materialized EVERY row on EVERY scan under READ_COMMITTED
  (O(N) clones per scan — the plan's Phase D item 1 and undo-log R-3's noted cost). Both sites now
  stamp `recordVersion` at materialization and re-clone only when the record's version changed
  (foreign mark/commit), the entry is absent, or it's the transaction's own change — mirroring the
  by-id read's invalidation. SNAPSHOT path unchanged (first materialization wins, now version-stamped).
- **Contract pinned:** new `RC-12 scanReusesUnchangedMaterializations` (ReadCommittedTest 12/12):
  two scans over unchanged data return the SAME instances; a foreign commit re-reads the row.
- **Verified:** resource+btree+strategy/giant gate 137/137; full suite 470 tests — stable red
  unchanged (GraphPhantom/StressTest/CompositeObject + flaky testMultithreads). A/B isolation check:
  `testMultithreads` fails identically with and without D-1 (its worker lock-timeouts are the
  pre-existing flake; a full-suite run during D-1 gating also hit the B+Tree read-lock spin
  (`tryLockRead` main-lock hot loop, 27 min CPU) — the StressTest liveness family, not D-1).
- **Phase D-3 (update batching in hot insert paths) stays OPEN** — it changes crash/commit
  semantics (which records ride a full-file write-out) and needs its own design round.
- **Revert:** restore the `rcRefresh || !contains || own` conditions in both scan sites.

### R-22. Boolean-field distortion FIXED: silent field-drop in the metadata scanner — KEEP (2026-10-17)
- **Symptom:** `XdStorageCompositeObjectPersistentTest.step2` — `isActive` came back `false`
  after reload while all other primitives survived.
- **Root cause:** the entity declared field `isActive` + getter `getIsActive()` + setter
  `setActive()` — the scanner derives the setter as `setIsActive` (get-branch: `set` + name
  minus `get`), the lookup failed, and the old `catch (Exception ignored)` **silently dropped
  the field from persistence** (write omitted it; reload saw the default).
- **Fix:** (1) entity setter renamed to `setIsActive` per the documented accessor contract;
  (2) `XdStorageReflectionFieldScanner` no longer drops silently: a field that HAS a getter but
  NO matching setter now produces a loud WARN ("the field is DROPPED from persistence — data
  loss on reload") instead of vanishing. Computed getters without a backing field remain
  silently skipped (they are not persistence properties). Behavior is otherwise identical.
- **Verified:** the test 2/2 green; serialization gate 19/19; full suite 470 — the boolean red
  is gone, remaining reds are only the concurrent/liveness family + flakes.
- **Revert:** rename the setter back and re-silence the scanner catch.

### R-23. GraphPhantom FIXED: search indexes were never built for DB-generated ids + wrapper class-name leaks in XML — KEEP (2026-10-17)
- **Root cause chain (three layers, each hidden behind the previous):**
  1. `SearchManager.insert` skipped indexing whenever the object's id was already set — but the
     facade assigns the DATABASE_GENERATOR id in the id-index resource BEFORE calling search-insert,
     so **search indexes were never built on first save**; any later `storage.update` crashed on the
     missing key tree (`SearchIndexResource.update:204/214 "does not exists"`).
  2. With indexing fixed, readbacks progressed to planet fragments and hit
     `ClassNotFoundException: XdSatelliteIdObservableWrapper` — the XML writer emitted
     **generated wrapper class names** for not-yet-materialized child instances (raw
     `object.getClass()` in the element/collection/map writers) instead of the entity class.
  3. Both index resources also lacked a fallback when a tree's cache record was lost to a
     creator rollback/eviction while the live graph remained.
- **Fix:** (1) removed the wrong skip; `SearchIndexResource.insert` is now IDEMPOTENT (reverse-key
  record present → no-op), so repeated saves don't double-index; (2) `XdStorageDefaultObjectsWriter`
  normalizes every emitted class name through `getEntityClass` (new `entityClassOf` helper, all
  element/collection/map/array sites); (3) both `BTreeIndexResource` and `SearchIndexResource`
  consult the canonical registry before creating a tree or throwing (adopt-instead-of-compete).
- **Verified:** GraphPhantom 2/3 green isolated (the 1 failure = the known concurrent
  purge-vs-rollback flake); suite minus the hang-prone concurrent classes: **460 tests, 0 red**.
  The concurrent family can still hang (`testMultithreads` read-lock spin — StressTest liveness
  family, documented in R-21's A/B note).
- **Revert:** restore the SearchManager skip, drop the idempotency check / registry consults /
  `entityClassOf` normalization.

### R-24. Observability pass: purposeful logging across the engine — KEEP (2026-10-17)
- **What (INFO = rare lifecycle, WARN = anomalies/recoveries, ERROR = acquisition failures; hot
  paths stay quiet — full suite logs ~244 KB total, vs 4.8 GB at the old DEBUG level):**
  - `XdStorageTransactionManager`: WARN when the timeout sweeper rolls a transaction back.
  - `XdStorageReadWriteLock`: ERROR on lock-acquisition timeout (with the holder thread —
    leak diagnosis), WARN when an acquisition waited ≥ 1 s, WARN on rejected `unlockWrite`
    (leaked/unbalanced lock).
  - `XdStorageBTree`: WARN before every retry-budget-exhausted throw (per op/key/tree), INFO
    on root creation and on tree-emptied purge.
  - `BTreeIndexResource` / `SearchIndexResource`: INFO on lazy tree creation; WARN when a tree
    is adopted from the canonical registry (its cache record was lost — anomaly).
  - `XdStorageResourceCache`: WARN when an infra record stays invisible past the wait budget;
    DEBUG on canonical adoption during fillCache.
  - `XdStorageAtomicFile`: WARN on non-atomic-move fallback and stale `.tmp` recovery.
  - `XdStorageProvider`: INFO on storage open/shutdown; WARN when a name returns an EXISTING
    instance (the test-isolation gotcha, previously invisible).
- **First catch:** the WARN set immediately surfaced a real `unlockWrite by main rejected:
  write lock held by null` leak signal and 35 retry-budget exhaustions in one concurrency test.
- **Verified:** no-flake suite 460/0 green with the logging in place.
- **Extension (full-coverage pass):** logging added to the remaining silent parts:
  - `XdStorageClassGenerator`: **ERROR** when no system JavaCompiler exists (JRE) and when
    wrapper compilation fails with diagnostics — both previously fell back to dummy wrappers
    SILENTLY (broken equals/hashCode contracts with zero trace).
  - `XdStorageTriggerManager`: **ERROR** when a trigger throws on insert/update/delete
    (rethrown — behavior unchanged, but the failing trigger class and operation are now named).
  - `XdStorageDefaultObjectsReader`: **WARN** on "premature end of file" partial reads
    (truncated resource file = data-loss signal, previously returned silently).
  - `XdStorageLongIdGenerator` / `XdStorageIntegerIdGenerator`: **WARN** when a generated id
    escapes its Hi-Lo batch (refetch race suspected).
  - `XdStorageBTreeInvariants`: **ERROR** at every invariant violation (single choke point in
    `validateLeafChain` — preserves the structural damage report in the log alongside rollback).
  - `XdStorageAbstractResourcesManager`: DEBUG on resource eviction.
- **Verified:** no-flake suite 460/0; suite log volume still ~240 KB.
- **Revert:** remove the added log statements (all additive, no control-flow changes).
- **Full-coverage extension (all classes):** loggers added to every remaining substantive
  class — `XdStorageBTreeNode` (DEBUG: split/join completion), `XdStorage` facade (DEBUG:
  delete dispatch), `XdStorageSearchManager` (DEBUG: per-record index delete), `XdStorageSmartCloner`
  (DEBUG: btree-infra live pass-through), `XdStorageResource` (DEBUG: prepare object counts),
  `XdStorageDefaultObjectsReader/Writer` (loggers for the existing WARN + future diagnostics),
  hash index resources. Discipline: per-operation traces are DEBUG (invisible at the default
  INFO config — 0 DEBUG lines in the suite log), anomalies WARN, rare lifecycle INFO.
  DEBUG traces activate with a one-line config change when diagnosing.
- **Resource-cache & transaction-management depth (final extension):** `XStorageResourceCache`
  logs INFO on every commit ("commit of tx X: N record(s) became permanent") and every rollback
  ("rollback of tx X: N record(s) reverted in reverse mutation order"); per-op INSERT/UPDATE/
  DELETE cleaned to parameterized DEBUG with class/id. `XStorageTransactionManager.beginTransaction`
  now logs the timeout and nesting ("Started transaction X (timeout N ms, nested in Y)").
  Suite volume: ~5700 INFO lines / ~900 KB — cache commits and transaction lifecycle fully
  visible at default config. Suite 460/0 green.
- **INFO operation flow (show-the-project log):** the facade now logs INFO on every
  successful `save` (object and collection, with class/object count) and every `delete`,
  plus `XStorageResource.prepare` logs the from-disk load with object counts. A full suite
  run produces a complete readable operation trace: ~3400 INFO lines / ~570 KB — saves,
  deletes, tree creations, prepares, transactions, cleanups in order.

### R-25. StressTest FIXED: buffered persistence IO — KEEP (2026-10-17)
- **Root cause:** the "cyclic locker deadlock" was never a deadlock — it was **unbuffered IO**.
  Every commit rewrites the whole fragment + id_index files (quadratic amplification: ~5000
  commits serialized ~6M objects over the run), and every read/write went through RAW
  `FileWriter`/`FileReader` — one syscall per append/parse-chunk. 50 threads × 100 ops could not
  fit the 60 s `awaitTermination` budget; workers looked "stuck".
- **Fix:** `XdStorageAtomicFile` wraps the temp-file writer in a 64 KB `BufferedWriter`; ALL
  `readAsObjects`/`readAsData` paths (data + btree-index + hash-index resources) wrap their
  `FileReader` in a 64 KB `BufferedReader`. 8 read sites + 1 write site.
- **Verified:** StressTest ×2 isolated and ×1 in-suite: **5000/5000 commits, 0 rollbacks,
  ~29.5 s against the 60 s budget** (was: timeout, 0 progress). Full suite 470 tests: only the
  flaky concurrent trio red in that particular run (each green isolated). No hangs.
- **Next candidates unlocked:** re-measure the flaky concurrent trio with the IO pressure gone;
  the remaining failures are pure concurrency-timing races (purge vs rolled-back tx), not IO.
- **Revert:** unwrap the buffered readers/writers (8+1 sites).

### R-26/R-27. RW-lock experiments REJECTED; infra latch-skip LANDED; the flake's layers mapped — KEEP latch-skip only (2026-10-17)
- **Diagnosed (testMultithreads isolated, evidence in /tmp logs of the session):** the flake is
  an onion. Layer 1: read-lock starvation during mass-delete cascades (lazy getters escalate the
  transient timeout into markRollbackOnly, killing whole worker txs). Layer 2 (after layer 1 was
  addressed): per-record latch timeouts on hot tree-node records — `CacheRecord.lock` holds the
  latch until the holder's tx ends, so hot nodes serialize 25 workers with 3 s expiry.
- **Rejected (both starved the other side):** reader-priority barrier (6 write-timeout runs);
  waiting-writers barrier starving new readers (read-timeouts persisted 3/3). The simple scheme
  (shared reads, no waiter gates) is RESTORED; the honest fix for starvation is a real
  java.util.concurrent RW-lock redesign — open, R-26 in the plan.
- **Landed (R-27):** infrastructure records (trees/nodes) NO LONGER take the per-record latch —
  `CacheRecord.lock/unlock` return immediately for infrastructure caches. Structural contention
  is already serialized by the tree's own write-lock; the latch only added a hot-node ticket
  queue with 3 s expiry. Own-change detection survives via `record.transaction` set in
  `createInsertRecord`/the no-op lock. Logs added earlier (R-24) cover the residual contention.
- **Verified:** no-flake suite 460/0 + StressTest green with the latch-skip and the reverted
  lock. testMultithreads in ISOLATION still fails ~2/3 (read-lock timeouts persist under this
  workload's sustained write pressure — the RW-lock redesign remains the open item); in-suite
  it stays flaky-green.
- **Revert:** remove the two infrastructureCache guards in CacheRecord lock/unlock.

### R-26. Fair-queue RW lock + the remaining flake characterized — PARTIAL, next layer documented (2026-10-17)
- **Diagnosed (testMultithreads isolated, caught in the act):** the flake's first layer was
  **read-lock starvation** — `XdStorageReadWriteLock.lockRead` timed out (3 s) during
  concurrent mass-delete cascades, and the generated lazy getter escalates that transient
  timeout into `markRollbackOnly`, killing the whole worker tx (and the purge tx hits the same).
- **Fixed:** true shared reads confirmed (the exclusivity lives only in `tryLockWrite`'s
  sole-reader upgrade guard) + **fair queue**: waiting WRITERS block only NEW readers
  (nested re-entries of a holder are exempt — self-deadlock guard); writers are NOT blocked
  by waiting readers. An intermediate reader-priority variant starved writers (6 write-timeout
  runs) and was replaced by the fair queue.
- **Result:** read-lock timeouts GONE (0 occurrences in failing runs); the no-flake suite is
  460/0 green with the new lock.
- **The remaining flake peeled one layer down:** the failure is now **per-record latch timeouts**
  (`CacheRecord.lock` "Object with id N is locked for update" — the `blockingTime` waiter
  budget) under 25-worker contention — a different, deeper mechanism than the RW lock. Next
  round: per-record latch fairness/timeout tuning (candidate: bounded wait + loud retry hint,
  or striping), then re-measure the flaky trio.
- **Also remaining:** the frozen cloner pass-through (still deadlocks concurrent wrapper flows),
  D-3 batching design (open; changes crash semantics), StressTest un-re-verified with the final
  lock (was green at R-25 state).
- **Revert:** remove `waitingWriters` + the two checks and the lockWrite/lockRead wrapping.

### R-28 (DESIGNED, NOT YET LANDED): condition-based RW-lock waiting — the next session's first item
- **Evidence closed the loop:** the R-24 ERROR logs show every read-acquisition timeout coincides
  with a ROTATING write-holder (different holder threads named across successive 3 s timeouts) —
  the reader's 15 ms sleep-quantum lands inside writer sections; with sustained writer arrival it
  never observes `writeLockThread == null`. Genuine starvation of the POLLING MECHANISM, not of
  the admission policy.
- **Design (contained, admission-preserving):** extract `tryWriteStateLocked(Thread)` /
  `tryReadStateLocked(Thread)` (the state checks under an already-held mainLock); `lockWrite`/
  `lockRead` hold `mainLock` and await a `Condition released` (15 ms timeout loop for the tx
  deadline) instead of `Thread.sleep`; `unlockWrite` (on the counter reaching zero) and
  `unlockRead` (on a thread's counter reaching zero) `signalAll()`. Admission policy, reentrancy,
  and the sole-reader upgrade stay byte-for-byte identical — only the waiting mechanism changes,
  eliminating the blind quantum.
- **Status:** **ATTEMPTED AND CLOSED (R-29).** Two implementations were built and gated:
  (1) condition-based waiting (the design above) — the flaky test still failed; woken readers
  still LOSE the mainLock handoff race against fresh arrivals (wakeup ≠ victory);
  (2) **fair `ReentrantReadWriteLock` backend** (full API-preserving wrapper) — WEDGED under
  25 workers: fair-mode queue handoff across the engine's deep-lock chains (leaf→ancestor
  multi-lock paths) does not drain; the 10-second stress window ran 25+ minutes without a
  verdict. Both were REVERTED; the simple spin-scheme is restored and verified (StressTest
  green; Xml back to its known flake level).
- **Open (R-29, needs its own focused session):** a real fix must remove the contention
  itself — per-node striping, shallower deep-lock chains, or moving tree-node latching to
  FIFO ticket latches — not re-schedule the waiters. The rejected variants + evidence are
  recorded here and in the lock source comment so they are not re-attempted blind.
- **Revert:** n/a (both variants reverted; source comment carries the history).

### R-31. testMultithreads: the wedge ROOT-CAUSED and FIXED (timeout-sweeper reschedule); the remaining wall is purge O(N²) validation — KEEP (2026-10-17)
- **The wedge root cause (proven):** jstack on a wedged run reports the JVM **unresponsive**
  (GC-thrash symptom, not a lock deadlock). Mechanism: the lazy getter marks the tx rollback-only
  → the timeout sweeper's deferred rollback saw `activeThreads > 0` and **returned silently**,
  relying on "re-call at final unlock" — which never comes when the triggering exception is
  swallowed (broadened operation catches). Result: the tx stays rollback-only forever with
  latched records, live maps, and locked tree records → other workers block on them → memory
  and lock pileup → the wedge. (The stack trace evidence: `XdatStorageStressTest` had logged
  the sweeper's "deferred" message before hangs; jstack un-responsive confirmed memory.)
- **Fix:** `registerRollbackOnlyTransaction` RESCHEDULES itself (200 ms delay via the executor)
  instead of returning — the rollback always completes.
- **Also landed:** the worker-side fixes from R-29a are RE-APPLIED and KEPT (9 operation
  catches broadened + best-effort per-galaxy purge, no rethrow).
- **Result:** the wedge is gone (3 runs, no freeze/dump needed); workers complete. The test now
  exceeds its time budget in the **afterEach purge**: ~1250 sequential tree deletes, each
  running the FULL structural validator (full leaf-chain walk + full descend + per-node
  checks) → O(N²) ≈ 780k node visits — the known purge-scaling wall.
- **Next step (precise):** validator sampling in `BTreeIndexResource.delete/insert` — always run
  the O(1)-per-node checks (children==keys+1, separator monotonicity), run the FULL chain walk
  every Nth op (N=25) and always on tree-emptied purge. Cuts purge validation to O(N) while
  keeping the loud-corruption guarantee (deep verifier suites still pin correctness).
- **Verified:** no-flake suite 460/0 with the reschedule fix.
- **Revert:** restore the silent `return` in `registerRollbackOnlyTransaction`; git-restore the
  operation/purge test files.
- **What:** a true ordered undo journal. `undoLogByTransaction` (per-tx `Deque<CacheRecord>`)
  records each record at its FIRST mutation (all four registration sites — insertInternal
  observable/plain, update, delete — push when `changes.map.put` returns null, i.e. the record
  enters the transactional contour). `rollback()` now replays the journal in **REVERSE order**
  (descendingIterator) instead of unordered map iteration — each mutation has exactly one undo
  point and the rollback strictly inverts the transaction's chronology. `commit` discards the
  journal (changes became permanent); `rollbackFailedCommit` and `clear` remove it.
- **Failure diagnostics:** a mid-journal rollback failure is no longer silent — ERROR with the
  record id and the count of unprocessed entries, then rethrow (previously a half-rolled-back
  state was unnoticeable).
- **Compatibility:** the unordered-map fallback remains for transactions without a journal
  (legacy/mocks); `hasChanges`/prepareCommit/performCommit/commit flows untouched (the `changes`
  map stays the write-set source of truth — the journal is the ordered view over it).
- **Verified:** no-flake suite 460 tests — 459 green + 1 concurrency flake
  (XdStorageBTreeStressTest parallel-find, green 2/2 on rerun; known family).
- **Revert:** remove `undoLogByTransaction`/`undoLogOf`, restore the map-iteration loop in
  `rollback`, drop the `if (map.put(...) == null)` wrappers at the 4 registration sites.

### R-29a. testMultithreads worker-side experiment REVERTED; a NEW WEDGE observed — next session MUST capture a live jstack (2026-10-17)
- **Attempted:** (1) broadening all 9 operation catches to include RuntimeException — the lazy
  getters wrap the SAME lock-contention into XdStorageRuntimeException, which escaped the
  operations' `catch (XdStorageException | XdStorageConnectionException)` rollback path and
  landed in exThread; (2) making the afterEach purge best-effort per-galaxy + no rethrow.
- **Result:** RUN 1 green, but RUN 2 WEDGED — 3 worker threads stuck indefinitely (main spinning
  in the countThreads join loop; log frozen mid-run). Pre-change isolated runs failed FAST with
  clear exceptions (no wedge). The wedge is a NEW phenomenon correlated with swallowing the
  runtime exception inside an operation: the operation's tx is rollback-only/damaged, the
  subsequent in-operation storage calls or `tx.rollback()` appear to block unboundedly somewhere
  (suspects: `skipOrWaitForInternalTransactions`, the sweeper's `synchronized(transaction)`
  interplay, or an unbounded resource-manager section). **Next session: script a run + jstack the
  live JVM at the wedge (prepare the dump script BEFORE the run — twice the dump window was missed).**
- **Reverted:** operation catches + purge tolerance restored to HEAD (git restore); verified
  no-flake suite 460/0 at the restored state. The known behavior returns: testMultithreads
  flaky/failing with loud exceptions (no wedge).

### R-33. Write-lock leak fixed in all three B+Tree retry loops + validator sampling — KEEP (2026-10-17)
- **The leak (proven by the purge logs):** `XdatStorageBTree.insert/delete/update` retry loops
  acquired the tree write lock with **no try/finally** — any throw from inside the write section
  (record-latch timeout, "does not exists", validator) leaked the lock for the JVM lifetime
  (holder = a FINISHED thread; every later tree op burned its 3 s timeout on it — the purge's
  crawl and the "leaked write-lock" ERRORs).
- **Fix:** try/finally around each post-lockWrite section in all three loops. The unlock is
  GUARDED (`if (isWriteLocked())`): the node-level `deepUnlock` inside the operations already
  releases the deep chain (which includes the tree when the cascade reaches the root), so the
  guard prevents double-release while still closing the residual loop-level hold.
- **Validator sampling (R-31 next step, landed):** full structural validation runs every 25th
  index operation (`XStorageBTreeInvariants.shouldRunFullValidation()`); between samples the
  O(1) `checkStructuralAlignment` tripwires (already in every node mutation) carry the
  loud-corruption guarantee. Cuts purge validation from O(N²) to O(N/25).
- **Verified:** btree suites 45/45; Xml class: RUN 1 green, RUN 3 green; RUN 2 still hit leaked
  locks (5 events) and timed out mid-purge — **a second, still-unidentified leak path exists**
  (not covered by the loop-level finally; possibly a node-level deep-lock path). The purge now
  SURVIVES leak events via per-galaxy skips instead of failing.
- **Revert:** remove the try/finally wrappers + the sampler; restore the bare validate calls.

### R-34. Record-arbitration bypass for infra REVERTED — contract A validated — KEEP arbitration (2026-10-17)
- **Attempted:** skipping `canBeChangedByTransaction` arbitration for infrastructure records
  (the R-27 no-op latch leaves stale transaction pointers that spuriously fail the check inside
  tree write cascades — "node 44 can not be updated"). All 8 sites were bypassed.
- **Result:** the BTreeEntity **contract-A test immediately failed** — foreign delete of an
  insert-locked record MUST throw loudly; the bypass silently proceeded. The "spurious"
  canBeChanged failures during cascades are CORRECT contention arbitration (another live tx
  owns the record) — the bug was never the arbitration, it was the LEAK on the throw path
  (fixed by R-33's try/finally).
- **Reverted:** all 8 sites back to exact arbitration; contract A preserved; suites green
  (68/68 on the combined gate).
- **Residual:** testMultithreads still times out on ~1/3 of isolated runs with 4–11
  "leaked write-lock" events — a leak path OUTSIDE the B+Tree retry loops (not covered by
  the R-33 finally). Next session: run with the in-test watchdog (unfiltered stacks) until
  it fires, and correlate each "leaked write-lock" holder-tx with its last logged operation
  (the correlation script pattern from this session: /tmp/leak-hunt.sh).

### R-35. The read-lock leak LOCATED but its outer fix REJECTED (lock-coupled design) — KEEP open with exact coordinates (2026-10-18)
- **Located:** `XdatStorageBTree.find`'s read section — `lockRead` → `root.find` descent with
  no try/finally. If the descent throws before the node-level coupling point releases the lock
  (`XdStorageBTreeNode.find:1282` calls `tree.unlockRead`), the tree read lock leaks — and a
  leaked READ lock blocks every subsequent tree WRITE (purge's "монопольно занят на запись" storm).
- **Outer fix REJECTED:** try/finally around the section double-releases — the node descent
  itself releases the read lock at the coupling point (9 failures: IllegalMonitorState
  "Блокировка чтения не удерживается"). Reverted; the section carries the R-35 warning comment.
- **The correct fix (next session):** make the NODE-level descent release-safe — in
  `XdStorageBTreeNode.find` (and `deleteFromThisNode` descent), wrap the descent so the tree
  read lock is released exactly once on any throw (track the coupling: a flag set when the
  node released it). Same audit for `XdatStorageBTree.read`'s `firstLeaf.read` chain walk.
- **Revert:** n/a (current state = the pre-R-35 behavior with the warning comment).

### R-36. FIND RETRY BUDGET — the wedge fixed; new nested-transaction test fails pre-existing — KEEP (2026-10-18)
- **The wedge mechanism (final):** `XdatStorageBTree.find`'s retryFind loop was the ONLY
  unbounded retry in the engine — under sustained write pressure node-level `tryLockRead`
  never converged, and each retry snapshot-copied collections (~2 GB/min allocation storm,
  healthy heap, 0.6 ms GC pauses — no memory issue). Workers spun forever; jstack couldn't
  attach (no safepoint in the counted allocation loop).
- **Fix:** `MAX_FIND_RETRY_BUDGET = 200` (mirrors REBALANCE_RETRY_BUDGET) — on exhaustion:
  WARN + loud `XdStorageException` (operations roll back cleanly; the test's best-effort
  purge skips).
- **Verified:** testMultithreads 3/3 COMPLETE (was 2/3 hung forever), 0–1 leaked-lock events
  (was 4–11); full suite green except the new nested-transaction test.
- **NEW: `XdStorageNestedTransactionIsolationStressTest` (committed by the author mid-session,
  8391ea1) fails 1/1 — A/B verified PRE-EXISTING (fails identically with the campaign changes
  stashed).** Nested transactions under parallel load — same contention family; diagnose with
  the same watchdog/correlation toolkit.
- **Note:** the author has been banking the campaign work as commits (64e2908 logging,
  190ca8b improvements, f5d45e8 update, 8391ea1 nested test) — the uncommitted remainder is
  only the final find-budget + watchdog + docs changes.
- **Revert:** remove the budget check + the field.

### R-37. XdStorageNestedTransactionIsolationStressTest: ZERO-progress failure characterized — OPEN (2026-10-18)
- **The signature (deterministic, 1/1 runs):** 8 workers × nested-tx storm → **0 parent
  commits, 0 nested commits, 0 nested rollbacks** — every worker fails its FIRST operation.
  16 anomalies: 15× tree write-lock timeouts ("монопольно занят на запись", 3000–5000 ms) +
  1× InterruptedException ("sleep interrupted" — the pool's shutdownNow).
- **Zero progress ≠ gradual contention:** the very first inserts of all 8 workers failed —
  something holds or corrupts the tree write-lock acquisition path systematically at the
  nested-tx storm start. The nested facade (`beginTransaction(parentTx, timeout)` →
  `transaction.suspend(nestedId)` → nested ops → commit/resume) is NEW code (the author's
  current feature) — the suspend/resume + `transactionsByThreadId` replacement interplay with
  8 parallel parent→nested chains is the prime suspect (not the B+Tree itself: the same tree
  handles the multithreaded non-nested load fine after R-36).
- **Diagnostics ready:** the operation catches now include RuntimeException (the worker
  failures carry full stacks); the sweep/reschedule (R-31) keeps records from deadlocking
  permanently; correlation recipe: grep the run log for the holder tx ids of each
  "Write-lock acquisition failed" event and trace "Started transaction <id>" / the holder's
  last ops — this identified the Thread-5 trail in the R-33 investigation.
- **Also verified this session:** the author's A/B — the test fails identically with the
  campaign changes stashed (pre-existing; not caused by R-9..R-36).
- **The starvation cycle CAPTURED in one log window (the definitive evidence):**
  `pool-3-thread-1` held the tree **read lock** during a slow chain walk — the walk does
  per-node `storage.load` (disk IO for reference-stub nodes) INSIDE the read lock — while
  `pool-3-thread-7`'s write spun its full 3 s nested budget against it, then threads
  2/3/4/5/6/8 timed out identically. Reader holds read-lock for disk durations; writers
  starve on the sleep-poll lock; their rollbacks trigger more scans. Mutual starvation.
- **The fix direction (R-38, next session):** (1) take the chain walk OUT of the read lock —
  snapshot the leaf chain under the lock, release, then load/visit (the viewer pattern
  already accepts this: the R-35 warning comment marks the coupling contract); (2) or make
  `storage.load` of tree nodes never hit disk mid-walk (the canonical registry + fillCache
  adoption should already ensure this — VERIFY why reference-stub nodes still resolve to
  disk during scans); (3) the RW lock itself stays as-is.
- **Revert:** n/a (diagnostic entry).


### R-38. Cold-cache disk-IO-under-lock: chain PRE-WARM + stub loads out of tree locks — KEEP (2026-10-18)
- **Mechanism (R-37 follow-up):** on a fresh storage the first leaf-chain walk executed
  `storage.load` of every reference-stub node (XML parse, ~0.1-1 s each) INSIDE held read
  locks (and the firstLeaf load even under the tree WRITE lock) — all writers spun into
  their 3-5 s timeouts: the captured R-37 window (thread-3 loading node-13/planet_idx/
  key_planet_idx/node-2 while 7 writers starved).
- **Fix:** `XdStorageBTree.read()` now pre-resolves the whole leaf chain BEFORE the locked
  walk (`prewarmLeafChain`): each stub loads under ONLY its own node write lock; the tree
  write lock is held only to read the `firstLeaf` pointer. `find()`'s root-stub load also
  no longer takes the tree write lock. Prewarm is NON-BLOCKING (R-42): a contended stub is
  SKIPPED (tryLockWrite) — the locked walk loads it later; a blocking prewarm crossed
  coupled readers in opposite order and produced mutual 3 s holds (PhantomRead).
- **`nextTreeNodeOnThisLevel` is volatile now** — prewarm reads it without the node lock.
- **Revert:** remove prewarmLeafChain + restore the two lockWrite-wrapped load blocks.

### R-40. THE WEDGE FAMILY (nested zero-progress): leaked child write-lock + MVCC chain + zombies — KEEP (2026-10-18)
- **Deterministic trigger (single-thread repro, fails=0 needed 5 stacked fixes):**
  1) **Child write-lock LEAK:** in `findChildAndInsert`/`findChildAndDelete` the child's
  write lock (taken before `storage.update(child)`) leaked on ANY throw (MVCC-cache
  conflict "can not be updated" at t=40 ms): next writer blocked on the child D holding
  the parent-node lock, third blocked on tree.lockWrite D holding a node lock on the
  descent — full lock-hostage cascade, all-timeout, zero progress. Fix: catch/throw
  releases the child (`child.unlockWrite()` before rethrow).
  2) **Nested-vs-parent self-conflict:** nested tx is a NEW transaction (new timestart),
  so the descent's parent-pointer updates locked by the SUSPENDED parent refused the
  nested descent — "can not be updated by transaction <own parent>". Fix: chain
  relaxation in `canBeChangedByTransaction` (both overloads): `isOwnedByTransactionChain`
  allows the writer chain (caller → globalTransaction) to update locked records.
  3) **Parent-poisoning nested rollback:** `rollbackTransaction(nested)` delegated
  `markRollbackOnly()` UPWARDS — poisoned the parent; scenario "nested rollback" failed
  `commitTransaction(parent)` with "marked as rollback only" EVEN SINGLE-THREADED
  (every 2nd iteration, 221/221). Fix: `markSelfAndChildrenRollbackOnly()` (downward-only
  cascade); `rollbackTransaction(nested)` uses it — the parent survives (the test's ACID
  contract).
  4) **Zombie abandoned nested tx:** a nested save that threw mid-way left the nested tx
  registered forever → parent's `skipOrWaitForInternalTransactions` waited forever →
  zombie parent kept its record locks → everyone refused. Fix: skipOrWait force-sweeps
  internal txs (a) abandoned ON THE CURRENT THREAD (immediate — nobody can finish them),
  (b) expired past timeout+500ms (markSelfAndChildrenRollbackOnly + standard sweeper).
  5) **Ghost nodes ("does not exists"):** rollback of a split leaves the new node in the
  in-memory structure (infra mutations are in-place on the live instance; record.rollback
  restores same-instance state), while insert-rollback `cache.remove`d its record; after
  resource eviction the node became unresolvable — every descent through it died. Fix:
  infra insert-rollback KEEPS the record serving (`rollbackInsertKeepServing`); update of
  an infra object with a lost record re-materializes it; delete of an infra object with
  a lost record is a no-op (already gone).
- **Diagnostics added (keep):** RW-lock timeout ERROR now prints full state
  (writeHolder/writeCount/readers map — caught the "no holder but waiters" leaks);
  UPDATE-refused ERROR prints record state; force-sweep WARNs.
- **Verified:** standalone 8-worker nested storm 4/4 runs fails=0 (parents 428-624);
  XdStorageNestedTransactionIsolationStressTest GREEN (was deterministic 1/1 red).
- **Revert:** per-item; each fix is independent and commented R-40/R-41/R-42.

### R-42. Infra records exit the refuse-until-commit MVCC policy; find retry backoff — KEEP (2026-10-18)
- **Mechanism:** after R-40, the remaining 8-worker failures were worker-vs-worker
  "can not be updated" on hot internal nodes: each descent locked the nodes it touched
  until COMMIT (whole parent-tx lifetime incl. the nested section) → mutual refusal
  storms (2614 fails, 18 commits per run). Structural correctness is ALREADY guaranteed
  by node write locks (hand-over-hand + deep-locks; R-27 said the same for latches).
- **Fix:** infrastructure records bypass the canBeChangedByTransaction gate in
  update/delete. FINAL SCOPE (after a contract regression): NODES
  (`XdStorageBTreeNode` — `structuralNodeCache`) relax fully; the TREE entity relaxes
  update but keeps the phantom contracts (uncommitted foreign INSERT cannot be
  deleted/updated by a foreign tx — BTreeEntityTest 8/8 green). Extra: unconditional parent-pointer `storage.update` per descent replaced by
  id-based change detection (`isSameParentId` — reference comparison always false due to
  instance duality); `find` retry loop sleeps 1 ms per 10 retries (mutation window +
  safepoint; the R-36 storm had none).
- **Tests:** the 4 isolation tests' `isLockBackpressure` now recognizes
  "retry budget exhausted" — same transient-backpressure class as lock timeouts
  (read-only, clean rollback, retried next iteration).
- **Verified:** storm fails 2681→147→0; full isolation family 6/6 GREEN.
- **Revert:** drop the `infrastructure` bypasses + backoff constants.

### R-41. XdStorageFacadeStressTest never ran its storm (latch bug) — FIXED, right-sized (2026-10-18)
- **The bug:** `startLatch.countDown()` / `finishLatch.await()` existed nowhere and the
  purge phase was INSIDE the worker-submission loop: workers parked forever on
  startLatch (100 non-daemon pool threads = JVM never exited = suite hung AFTER the test
  "passed" — caught live in this session's first full run: OOM storm 06:57-07:00, then
  100 threads parked at XdStorageFacadeStressTest:62, surefire fork immortal).
- **Fix:** submit-all → countDown → await → assert exceptions/inserts → executor
  shutdownNow → purge → empty-check (phases moved out of the loop). Storm right-sized
  100×2000 → 25×400 (the full 200k tx storm does not fit suite budgets; purge+ACID
  empty-check now actually execute).
- **Verified:** GREEN in 146 s (inserts storm + total purge + emptiness assert).
- **Revert:** n/a.

### R-43. CME при выгрузке живой ноды: serialization-guard (tryLock) + мониторные снимки писателя — KEEP (2026-10-18)
- **The crash:** `rollbackFailedCommit` → `writeObjects` → writer iterated the LIVE node's
  `LinkedList` (children/keys) while another worker's split mutated it — CME →
  "invalid state of database: error by writing resource XdStorageBTreeNode-3.xml" →
  failed commit cascades in the facade storm. Pre-existing on EVERY commit write-out
  (not just rollbacks): write-out serializes the live canonical instance unlocked.
- **Fix (three layers):**
  1) `IXdStorageSerializationGuard` (serialization pkg): begin/endConsistentSerialization.
     Node+Tree implement it with **tryLockWrite** on their own RW write lock — uncontended
     writes are fully consistent. BLOCKING lockWrite version REJECTED empirically: guard
     held node locks across file IO under 25 workers → hold-and-wait starvation
     (holder-X waited Y while holder-Y waited X, 15s timeouts).
  2) Node lists (keys/objects/children) are `Collections.synchronizedList` now
     (constructors + setters); writer's writeCollection/writeMap snapshot via
     `synchronized(collection){ toArray }` — same monitor → CME impossible even on the
     contended (no-lock) path. No added contention: mutators already serialize on the
     node write lock.
  3) Reader fallback: non-instantiable collection/map classes (wrapper class names in
     XML) fall back to LinkedList/HashMap.
- **Residual (documented):** torn keys/children parity if a snapshot interleaves the
  middle of a split's sub-steps (µs window) on the contended path — vs seconds of
  file-IO lock-holds of the rejected variant.
- **Verified:** facade storm (reproducer) 2/2 GREEN; full suite 471 tests / 1 failure =
  ONLY the historical GraphPhantom tearDown flake (~1/3 isolated, identical pre-R-43);
  0 CME / 0 write-resource errors in suite logs; nested storm repro fails=0; BTreeEntity
  8/8; serialization round-trips green.
- **Revert:** remove guard interface + implementations + synchronizedList + writer
  snapshots + reader fallbacks.


### R-44. ПОТЕРЯ ВЛОЖЕННЫХ ДЕТЕЙ (GraphPhantom tearDown "Разрушен вложенный спутник"): clonePojo падал на сгенерированных обертках, null молча писался в поле — FIXED (2026-10-18)
- **Детерминированный repro** (standalone MiniSatRepro2: writer save+update(system), 4 cascade-читателя): 145/150 планет читались БЕЗ спутника; диск при этом КОРРЕКТЕН (все спутники в XML) — резался только путь ЧТЕНИЯ.
- **Цепочка:** save до выдачи DB-id ребенка пакует в делегат `getObservableWrapper(satellite)` (XdSatelliteIdObservableWrapper). Чтение: cloneAndWrap(record.object) → packFields → ветка satellite → `internalCloneAsReferenceAndWrapObject` → `cloneObject(wrapper)` → `clonePojo` → `cl.newInstance()` на классе обертки → **InstantiationException** (нет no-arg конструктора) → `cloneObject` ГЛОТАЛ и возвращал null → **null записывался в поле satellite** → все последующие читатели видели планету без спутника.
- **Fix:** `XdStoragePojoCloner.clonePojo` разворачивает IXdStorageSimpleWrapper / IXdStorageIdObservableWrapper / IXdStorageUnmodifiableWrapper через `getWrappedObjectOrSameObject` ДО `newInstance`; `cloneObject` больше не молчит — ERROR-лог при отказе (раньше молчаливый null = тихая потеря данных). Плюс починен битый log-выражение в `XdStorageObjectField.set/get` (прецеденс `+ obj != null ?`).
- **Verified:** repro 2/2 lostAtEnd=0 nullSatAfterCommit=0 (было 145/150); tearDown GraphPhantom проходит (граф цел).

### R-45. ДЫРЫ SNAPSHOT ISOLATION на чтении record-ов: чужой читатель видел НЕЗАКОММИЧЕННОЕ состояние; коммит не атомарен между ресурсами — FIXED (2026-10-18)
- **Дыра 1:** `readInternal`, ветка update: чужой читатель при locked-обновлении получал `record.getObject()` — ЖИВОЕ незакоммиченное состояние писателя (ссылки на еще не существующие планеты) → "cannot load by reference" → reader-скан падал. Fix: `canBeChangedByTransaction` false → **prevObject** (последняя закоммиченная), как в `getObjectForTransaction`.
- **Дыра 2 (атомарность коммита):** каждая запись штамповала СВОЙ `nextCommitSerial()` в фазе-2 — читатель между фазами разных ресурсов видел смесь (новую систему + еще locked планеты → null). Fix: **один commit-serial на всю транзакцию** (`assignCommitSerial()` в `commitTransaction` ДО фаз; `record.commit` берет его же); читатель со снапшотом >= serial видит НОВОЕ состояние записи даже до ее фазы-2 (`committingSerialVisibleTo` gate: owner в коммите + firstPhaseCommit + снапшот >= serial).
- **Verified:** GraphPhantom изолированно 10/11 green (исторически ~2/3, местами 1/3); полный суит: fullsuite7 471/2 (обе редкие флаки), fullsuite8 **471/0 GREEN**.
- **Остаточное (open):** редкий (≈1/11 изолированно) residual того же семейства в GraphPhantom + единичный `рассинхрон структуры` в facade-штурме (валидатор R-32 увидел лист в цепочке, недостижимый из корня, под 25-воркерами — подозрение на транзитивное состояние между двумя обходами валидатора под конкуренцией). Требуют отдельной сессии с сохранением дампов.

### R-46. РАЗДЕЛЕНИЕ XdStorageResourceCache (god class 2119 строк → фасад + 5 коллабораторов) — KEEP (2026-10-18)
- **Что:** класс разделен по обязанностям без изменения публичного API (фасад сохраняет все
  публичные методы и порядок "лок записи ДО критической секции"; поведенческий контракт не тронут):
  - `XdStorageCacheRecord` — MVCC-запись: государственная машина read/insert/update/delete ×
    undefined/locked/committed, record-latch (R-27), 2PC-состояние, commit/rollback/
    rollbackInsertKeepServing (R-40), serial-логика (R-45) и **консолидированный вычислитель
    видимости** `resolveVisible(tx, View, committingSerial)` — шесть дублированных MVCC-переключателей
    (скан-предикат / watch / полный скан / unsafe-выписка / по id / по ссылке) сведены к одному
    методу с `View { SCAN, BY_ID, WRITE_OUT }`; различия сайтов сохранены ТОЧНО (BY_ID = барьер
    committing-serial + prevObject-фолбэк R-45; WRITE_OUT = последнее закоммиченное для чужих);
  - `XdStorageTransactionWorkspace` — 4 транзакционные карты (write-set `changes`, журнал отката
    `undoLogs`, неидентифицированные обертки, материализованное `readObjects`) + повторенный 4 раза
    паттерн "put в write-set; первая мутация — в журнал" сведен к `registerMutation`;
  - `XdStorageCacheMutator` — контур insert/update/delete (наблюдатель отложенного id, быстрый
    путь повторного апдейта, infra-релаксация R-42, самозарвка R-40);
  - `XdStorageCacheCommitProtocol` — prepareCommit/performCommit/commit/rollback (обратный порядок
    журнала + mid-journal диагностика)/rollbackFailedCommit;
  - `XdStorageCacheChange`, `XdStorageCacheObjectChange` — вынесенные enum/транзитный объект.
  - `XdStorageResourceCache` остался фасадом читающих операций + поддержки инфраструктуры
    (канонический реестр, materializeForCache, барьер waitForRecordResolution).
- **Почему:** план (раздел "PROJECT ANALYSIS") называл ResourceCache главным god class;
  6 копий MVCC-переключателя расходились при каждом семантическом фиксе (R-13/R-44/R-45 правили
  их по отдельности) — единый `resolveVisible` делает различия сайтов явными и проверяемыми.
- **Безопасность поведения:** все тела перенесены дословно; мониторы не тронуты (synchronized(record)
  остался на стороне вызывающих, record-монитор — тот же объект); CHM-карты те же инстансы;
  volatile isolationLevel читается в тех же точках. Публичный API и исключения — без изменений.
- **Verified:** resource-пакет 125/125 (1 skipped — штатно); все суиты кроме performance
  448/0/3; полный суит ×2: прогон 1 — 471/2 (facade-штурм: исторический флак "рассинхрон
  структуры" R-45-остатка — изолированно 2/2 green, в прогоне 2 green; GraphPhantom —
  разрешенный флак), **прогон 2 — 471/1: только разрешенный
  testMVCC_GraphRangeScans_UnderHeavyPhantomInsertions**. Критерий приема выполнен.
- **Итерация 2 (перенос в подпакет + инвариантные тесты):** коллабораторы переехали в
  `org.flib.xdstorage.resource.cache` (public, подпакет помечен internal в package-info);
  зависимость от фасада инвертирована через SPI-интерфейс `XdStorageCacheContext`
  (idField/records/workspace/флаги инфраструктуры/entityClass/isTransactionAlive/
  isolation/createReadRecord/createInsertRecord/materializeForCache/insert) — фасад его
  реализует, коллабораторы зависят только от контракта. Новые инвариантные сьюты
  (test: org.flib.xdstorage.resource.cache, стенд StubCacheContext без фасада):
  - `XdStorageCacheRecordInvariantTest` (23): фабрики записей, переходы mark/commit/rollback
    (включая таймстамп-барьер холодного старта и ОДНОУРОВНЕВЫЙ undo), контракт 2PC-флагов
    ("Illegal state of record to commit"), глобальный счетчик серий, матрица видимости
    resolveVisible SCAN/BY_ID/WRITE_OUT (барьер committing-serial R-45 и его SCAN-эксклюзивность,
    R-15 write-out поверх снапшотного роутирования, RC-выключение serial/timestart-гейтов),
    latch (таймаут, удочерение мертвого владельца, reentrancy, R-27 skip для инфраструктуры),
    матрица canBeChangedByTransaction (цепочка вложенности R-40, мертвый владелец, committed-гейт).
  - `XdStorageTransactionWorkspaceInvariantTest` (7): ровно одна точка отмены на запись,
    FIFO-хронология журнала, ленивые изолированные контуры, hasChanges, clear/finishTransaction
    (write-set изымает только протокол).
  - `XdStorageCacheMutatorInvariantTest` (18): insert (барьер null-id ДО CHM, double-insert,
    concurrent modification, живая инстанция инфраструктуры), быстрый путь апдейта (подмена
    контента + сохранение readTimestamp), полный путь, таймаут latch'а на update-записи,
    самозарвка R-40, контракты фантомов дерева (contract A — отказ на незакоммиченной чужой
    вставке; полная релаксация нод; foreign-update закоммиченного дерева), удаления
    (insert+delete изымает запись при сохранении точки отката; инфра-удаление держит запись).
  - `XdStorageCacheCommitProtocolInvariantTest` (9): перманентность 2PC + очистка контуров,
    коммит без фаз запрещен (и изымает write-set до итерации), обратный порядок журнала,
    rollback insert: домен изымает / инфраструктура держит живой инстанс, rollbackFailedCommit
    (update → восстановлен, insert → изъят, delete → воссоздан, пустые элементы пропускаются).
  - **Задокументированные семантики, вскрытые тестами** (все — поведение исходного кода,
    сохраненное дословно): владелец записи назначается lock()/фабрикой insert, но НЕ markUpdate;
    latch физически держат только update-записи (insert-записи защищены арбитражем — чужой
    lock() перехватывает владение, после чего арбитраж разрешает мутацию).
- **Verified (итерация 2):** инвариантные сьюты 57/57; все суиты кроме performance
  505/1 (единственный сбой — тот же исторический facade-флак "рассинхрон структуры",
  идентичная сигнатура R-45-остатка, к расщеплению отношения не имеет — BTree-внутреннее);
  полный суит — финальный прогон итерации 2.
- **Итерация 3 (логическое покрытие классов кэша, дисциплина R-24):** закрыты остававшиеся
  НЕМОЛЕ последовательности (INFO-объем не растет: новые линии — WARN/ERROR на редких
  аномалиях + DEBUG, невидимый на INFO-конфиге):
  - `XdStorageCacheRecord` (4→10): WARN перед таймаутом latch (с держателем/change/waiter),
    DEBUG удочерения latch у мертвого владельца (оба сайта), WARN отклоненного unlock
    (с владельцем), **ERROR отказа commit по фазам** (с first/secondPhaseCommit, state,
    change, owner — та самая диагностика "Illegal state of record to commit", ранее
    немая тирада в nested-шторме), DEBUG rollbackInsertKeepServing (R-40);
  - `XdStorageCacheMutator` (7→13): ERROR на всех отказах INSERT с состоянием записи
    (зеркало R-40 update-refused): deferred-id арбитраж, record-check после latch,
    удален-в-этой-tx, double insert, is exists, concurrent modification (с владельцем);
  - `XdStorageTransactionWorkspace` (0→2): DEBUG clear + DEBUG finishTransaction
    (закрытие транзитных контуров);
  - `XdStorageCacheCommitProtocol` (13→14): INFO-сводка rollbackFailedCommit (паритет
    с commit/rollback);
  - фасад (3→7): WARN восстановления инфра-записи из канонического реестра при ПОТЕРЕ
    записи (readInternal(id) + readByReferenceInternal — зеркальные "структурные ghosts"),
    DEBUG исчезновения записи во время ожидания развязки, DEBUG fillAndClone.
- **Verified (итерация 3):** resource+инварианты 182/0/1; полный суит — финальный прогон
  (объем лога на INFO-конфиге без изменений: новые линии не на INFO-путях).
- **Revert:** `git checkout` фасада + удалить 6 новых файлов пакета resource.


- **The test now self-diagnoses**: an in-JVM watchdog thread dumps **all** thread stacks
  (unfiltered — the first attempt's `Thread-` filter hid the holder) if the worker join loop
  exceeds 120 s. Works without external jstack (external attach fails under this workload:
  jstack/jcmd time out — the JVM doesn't reach the attach handshake).
- **Two distinct, alternating failure modes measured:**
  1. **The wedge (~1/3 of runs):** workers never finish; the R-29a dump showed 3 workers
     blocked on `mainLock.lock()` INSIDE the RW-lock class (two in `unlockRead`, one in
     `tryLockRead`) — a lost `mainLock` holder. The holder's stack was NOT in the first dump
     (the Thread- filter excluded non-Thread-Named threads — main was invisible). The
     unfiltered watchdog is now in place; the next wedged run names the holder directly.
  2. **The purge wall (the other mode):** workers finish, then the afterEach purge crawls —
     ~1250 sequential deletes × full-structural-validator-per-op = O(N²) ≈ 780k node visits,
     exceeding any reasonable budget. Fix designed (R-31 "next step"): validator sampling.
  GC-log evidence for mode 1: the wedged JVM shows a HEALTHY heap (342M→28M, 0.6 ms pauses)
  with an allocation storm (~2 GB/min) — a livelocked retry/allocation loop, NOT GC-thrash
  (the earlier "jstack doesn't respond" reading was safepoint starvation of the attach
  handshake, not memory).
- **Revert:** remove the watchdog block from the test (harmless diagnostic, KEEP recommended).

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
   **The single-threaded corruption is GONE (R-18): `testOneThread` is 10/10 green isolated.**

### Phase D — Performance — **D-1, D-2 DONE (2026-10-16)**
1. RC scan cost: reuse unchanged clones — **DONE (R-21)**: predicate + full scans are
   version-aware like the by-id read and `watch`; pinned by RC-12.
2. `blockingTime` map cleanup in `CacheRecord.lock` — **DONE 2026-10-16** (R-16).
3. Counter/index records: batching `storage.update` calls in hot insert paths — **OPEN**;
   changes crash/commit semantics (which records ride a full-file write-out) — needs a
   design round before implementation.

---

## 4. Regression net (do not skip per phase)

`YamlEngineEntitiesRoundTripTest` + `XdStorageBTreeSplitJoinInvariantTest` +
`XdStorageBTreeMassDeleteStabilityTest` + `XdStorageResourceCacheReadCommittedTest` +
`XdStorageResourceCacheRealClonerPhantomTest` + `XdStorageResourceCacheMethodCoverageTest` +
`XdStorageResourceCacheBTreeEntityTest` + resource package + btree package — all green at
2026-10-16 (full suite 469 tests, 7 red = the Phase-3b stress family + the new boolean-distortion
serialization test; assertion suites 100% green).

---

## 5. Track A session (2026-10-19): the facade-storm flake family — R-54..R-56

Session goal (MASTER-PLAN Track A1): resolve the residual flake family before calling 2.0 stable;
success gate = 10/10 green runs of all stress tests, then the full suite green.

### R-54. Find retry budget: attempts → time-based deadline with exponential backoff — KEEP
**What:** `XdStorageBTree.find`'s R-36 budget (200 attempts ≈ tens of ms under the 25-writer
storm) burned out on every burst: each retry re-acquires the tree read lock (waiting out a
mutator's whole op), descends, and loses the race to the next deep-lock chain — an honest reader
died loudly under legitimate load (the dominant isolated-failure signature in FLAKY.md A/B).
**Fix:** deadline = clamp(3/4 × tx timeout, 500ms..10s), exponential backoff 1→2→…→25ms; the loud
failure fires only when a retry actually failed AND the deadline expired. Also fixed: the attempt
budget could throw on a SUCCESSFUL attempt №201 (the check ignored `retryFind`).
**Verified:** find-budget exhaustions gone from all subsequent runs (was: 6-thread bursts).

### R-55. Resource retention: no eviction at refcount 0 — KEEP
**What:** `XdStorageAbstractResourcesManager.releaseResource` dropped the resource object when its
refcount hit 0. Each resource owns ITS OWN `XdStorageResourceCache` (records die with it) and its
`prepared` flag: the next toucher re-created the resource and re-parsed the file from disk. The
facade storm performed **88,699 disk parses per run** (hot node 3 parsed 891×), each parse being
IO under tree/node locks (fuel of R-54's find exhaustion); worse, until the canonical-registry
adoption kicks in, the parse introduces a stale-disk instance of a node whose live graph already
diverged from disk — the stale-parse channel. Recreation also reset fragment slot counters to 0
(race in `getFreeResourceId` fragment selection) and created orphaned-resource dualism (a dead
resource still registered in a live tx writing into its own cache while a fresh one re-parsed).
**Fix:** retain resources forever (bounded by files ever touched; the heap≈DB ceiling is the
documented pre-W-4 state); released on `storage.shutdown()`.
**Verified:** parse flood 88,699 → ~4.7k (one per node, first touch); facade run time 173s → ~62-80s;
runs 7-12 (retention era) ~2.5-3× faster than loop-1 era.

### R-56. The "рассинхрон структуры" family: validator made sample-consistent + the CME/tear hardening — KEEP
Evidence trail (runs 2-12, logs in /tmp/facade-loop on the session box):
1. First violation is ALWAYS the first WARN/ERROR of the run — the structure "tore" silently,
   with no throw, rollback, or tripwire preceding it (joins/borrows/rootCollapse tripwires silent).
2. Damage accumulates across transactions (orphan-leaf sets grow run-over-run) — consistent with
   the documented infra-rollback gap (in-place graph mutations survive record rollback).
3. Loop 5 (R-56 validator sampling): **4/4 green** at the previously-failing config, with 342-409
   "concurrent structure change" signals per run — all traced to the validator observing
   non-atomic state (entry-bumped counters flagged waiting mutators; artifacts).
4. Per-op validation (`-Dxdstorage.validation.interval=1`): 4/4 green, ZERO invariant violations —
   the tree never actually tore at the mutation level under full serialization.
**What changed:**
- `XdStorageBTree`: `structureVersion` (AtomicLong) + `activeMutations` (AtomicInteger), maintained
  STRICTLY inside the tree-write-lock section of insert/delete/update (entry-level counters flag
  waiting mutators — proven artifact, fixed to locked-section scope).
- `XdStorageBTreeInvariants`: `validateLeafChain` samples (version, active) before/between/after
  its two traversals; a change ⇒ `ConcurrentStructureChangeSignal` ⇒ retry (≤3) ⇒ skip with WARN.
  A divergence at a STABLE version still throws loudly (real corruption contract preserved).
  Validation interval configurable: `-Dxdstorage.validation.interval` (default 25 = R-32).
- Split paths: tripwire `checkStructuralAlignment` after `splitAndInsertIntoParent` and
  `splitAndCreateNewRoot` (joins/borrows/rootCollapse already had them); the silent
  separator-insert miss (split node not found in parent's children → `right` chain-linked but
  root-orphaned) is now a LOUD `XdStorageException` with an instance-level dump
  (`[CHAIN] SPLIT-ORPHAN`) instead of silent structural poisoning.
- Reader-side CME hardening (R-43 discipline extended): `find`/`read`/validator snapshot copies
  now taken under the collection monitor; **the YAML writer's collection/map iteration got the
  missing `synchronized` snapshots (R-43 fixed only the XML/default writer — YAML is the default
  format!) — the "CME on btree write-out" signature**.
**Also found (documented, NOT fixed — Track B/ACID backlog):** `XdStorageTransactionManager.commitTransaction`
SWALLOWS commit failures ("commit internal error" WARN → `rollbackFailedCommit` → returns
normally): the worker believes the commit succeeded while its data records were reverted (the
index/tree mutations survive in place). Green runs tolerate 14-28 such events (sweeper + registry
self-heal); the root cause of the 15s `lockForCommit` timeouts is commit-time resource-lock
queuing under the full-file write-out wall (Track B).

**Revert instructions:** each item is independent; R-56's validator sampling reverts by restoring
`validateLeafChain`/`validateUnderTreeLock` (the version counters in `XdStorageBTree` are inert
bookkeeping), R-55 by restoring the `resources.remove`/`locks.remove` in `releaseResource`,
R-54 by restoring the attempt-count budget in `find`.

### R-57. Compensating index-key deletion on rollback — KEEP
**Evidence:** GraphPhantom readers crashed with "cannot load by reference XdPlanet#N" on ids whose
DATA records were reverted by a rolled-back/failed-commit writer tx, while the id-index tree keys
survived (in-place mutation — the documented infra-MVCC compromise). Every such key is a permanent
reader landmine.
**What:** `IXdStorageTransaction.registerIndexKeyForRollback(tree, key)` / `rollbackIndexKeyInserts()`
(reverse-order compensating `tree.delete`, absent-key errors tolerated with DEBUG). Registration at
every successful index insert (id_index, search keyTree, search tree); compensation invoked from
`rollbackInternal()` (worker rollback + sweeper funnel) and from the commit-failure catch in
`commitTransaction`.
**Verified:** phantom ids no longer dangle in the index after writer failures (rebuilt index
consistent in tearDown verification).

### R-58. Zombie committed records: late/racing rollback suppressed — KEEP
**Evidence (REFDIAG probe, `-Dxdstorage.refdiag=true`):** readers failed on records with
`recordPresent=true, committed=true, change=read, object=null` — a LATE/racing rollback (e.g.
timeout-sweeper rollback catching up with an in-flight/finished commit) inverted an already
committed insert (`object = prevObject = null`), leaving a zombie that by-reference reads served
as visible-and-empty. One zombie poisoned every reader touching the id for the rest of the run.
**What:** `CacheRecord.rollback()` refuses records in the commit phase (`firstPhaseCommit ||
secondPhaseCommit`) with a WARN. `rollbackFailedCommit` is unaffected (it substitutes old record
instances with reset flags before calling rollback).
**Verified:** MVCC unit suites (RC 12 + phantom 12 + real-cloner 11) 35/35 green.

**Known engine anomalies documented, NOT fixed (Track B/2.1 backlog):**
- Commit-time `lockForCommit` queuing (full-file write-out wall) produces 15s commit failures
  under 25-writer storms; the engine self-heals (green runs tolerate dozens), but the
  `commitTransaction` failure path swallows the error — the worker believes the commit succeeded.
  ACID-honesty fix + the throughput wall = Track B.
- Hand-rolled RW-lock reentrancy anomaly (R-56 diagnostics): a mutator can be observed inside the
  tree-write-lock mutation section while another thread holds the same write lock
  (`activeMutations>=1, writeLocked=true`). The sample-consistent validator neutralizes the
  consequence; the lock redesign is R-29/Track B3 (the frozen-items list already forbids blind
  wait-rescheduling re-attempts).

### R-58 (final). Snapshot-consistent by-reference hydration + invisible-reference semantics — KEEP
**Evidence chain (REFDIAG serials):** a snapshot reader failed `cannot load by reference` on a
planet whose record showed `committed, commitSerial=1976` while `readerSnap=1969` — the reader's
snapshot legitimately excluded the phantom, yet its walked graph contained it: the system's
collection hydration used `View.SCAN`, which serves the LIVE record version regardless of the
reader's snapshot. The reader walked a not-its-version collection, hit the invisible phantom, and
the whole scan died ("marked as rollback only" cascade).
**What:**
- `readByReferenceInternal` hydrates with `View.BY_ID` + `committingSerialVisibleTo` (R-45
  atomicity) — a snapshot reader now sees exactly its own graph version.
- New `XdStorageReferenceInvisibleException` (extends `XdStorageException`): the record exists but
  is not visible in the reader's snapshot — the MVCC answer "this element is not in your visible
  graph version", NOT a structural error. The record-missing case remains a hard
  `XdStorageException` (real dangling reference).
- The generated wrapper templates (collection + map hydration) and
  `XdStorage.load(Collection, tx)` catch the invisible exception and **drop the element** from the
  reader's visible collection (iterator.remove / skip) — the phantom is not part of the visible
  graph version. A direct (single-object) reference load still propagates the exception.
- Diagnostics: `-Dxdstorage.refdiag=true` logs invisible-reference records with full MVCC state;
  `-Dxdstorage.dump.gencode=true` dumps failing generated wrapper sources.
- `getStructureVersion()`/`getActiveMutations()` renamed to `currentStructureVersion()` /
  `activeMutationCount()` — JavaBeans-shaped getters tripped the persistence field scanner
  (getter-without-setter WARN) on the persisted `XdStorageBTree`.
**Verified:** cache MVCC suites 59/59 + wrapper contract 37/37 + search engine integration green;
phantom-family trio (GraphPhantom / XmlMultithreads / DeleteIsolation / PhantomReadIsolation)
5/5 consecutive green passes; **the Track-A gate: 10/10 green consecutive runs of all 20 stress
tests**; the full suite 692 tests green (third run; runs 1-2 each carried one in-suite
`XdStorageBTreeStress` anomaly of the documented R-29 lock-contention family — different tests,
self-healing, gate-isolated 10/10).

### R-58 (дополнение, 2026-10-20). Regression coverage of the Track-A fixes — 13 pins
Each defect fixed by R-54..R-58 is pinned by a dedicated test (all red-by-construction on the
pre-fix code):
- `XdStorageFindTimeBudgetTest` (R-54): find survives a 600ms leaf-lock storm inside the time
  budget — the attempt-budget threw in ~20-40ms;
- `XdStorageBTreeRebalanceGuardTest` (R-56): real chain divergence at a stable version stays loud;
  mutation counters return to 0; **SPLIT-ORPHAN** (split whose parent does not contain it) is a
  loud `XdStorageException` instead of the silent chain-orphan;
- `XdStorageResourceRetentionTest` (R-55): after the last release the resource object, its record
  cache and the 3 records survive (old code: the resource vanished from the map);
- `XdStorageIndexRollbackCompensationTest` (R-57): save+rollback leaves the id-index clean — the
  full scan sees committed rows, neither rolled-back rows nor "cannot load by reference";
- `XdStorageCacheRollbackGuardTest` (R-58): rollback refuses committed records (no zombie),
  still cleans uncommitted inserts; the commit protocol skips an already-committed record
  (double-commit contour) instead of failing the whole commit; unprepared commit stays loud;
- `XdStorageInvisibleReferenceTest` (R-58): foreign-uncommitted insert →
  `XdStorageReferenceInvisibleException`; missing record → hard `XdStorageException`;
- `XdStorageYamlWriterCmeTest` (R-56): YAML write-out survives concurrent collection mutation
  without CME.
Full suite after the pins: **705 tests green**.
