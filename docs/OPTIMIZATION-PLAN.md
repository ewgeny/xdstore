# XdStorage — Optimization Plan

Baseline (2026-10-16, re-measured at HEAD `158a191`, full `mvn test` with retained surefire reports):
**469 tests / 7 red** — the Phase-3b stress family (`DeleteIsolation`, `GraphPhantom`,
`PhantomReadIsolation` (flaky under full-suite load), `XdStorageStressTest`,
`XmlDataStorageMultithreadsTest` ×2) plus the new `XdStorageCompositeObjectPersistentTest.step2`
boolean-field distortion (`isActive` expected true, was false on reload — under investigation by
the author).

**Status after the 2026-10-16/17 sessions (R-13..R-25 below): 470 tests — the only remaining
red is the flaky concurrent trio** (GraphPhantom / PhantomReadIsolation / Xml testMultithreads —
each green in isolated runs; the shared residual is a purge-vs-rolled-back-tx race, and the old
read-lock spin HANG has not reproduced since buffered IO landed). **`StressTest` is FIXED
(R-25: buffered persistence IO — the "deadlock" was unbuffered quadratic full-file rewrites):
5000/5000 commits, 0 rollbacks, ~29.5 s.** Stable green: DeleteIsolation, PhantomReadIsolation,
testOneThread, GraphPhantom (2/3+), boolean test (R-22).
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
- **Revert:** remove the added log statements (all additive, no control-flow changes).

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
