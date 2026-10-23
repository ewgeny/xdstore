---
title: "[2.1.0][BUG-E] Search-index key types are not restored at reopen: numeric range/EQUALS seeks crash"
labels: [2.1.0-train, P0, engine, bug]
status: open
---

## Summary

After a storage `shutdown()` → reopen, **every B+tree seek with a typed bound on a SEARCH
index whose key field is numeric fails** with
`"Long cannot be cast to String"` inside `XdStorageSearchIndexKey.compareTo` — ranges crash
loudly (`load` and `count` alike), and `EQUALS` on a numeric key takes the same seek path
and crashes identically. String-keyed indexes are unaffected (Strings survive the YAML
round-trip as Strings), which is why every existing reopen regression (R-53's
`XdStorageW2ReopenBTreeTest`, String-keyed ships) and the FP-1 suites (single-open,
`@TempDir`) never saw it.

## Why — root cause chain

1. The search-index B+Tree's keys (`XdStorageSearchIndexKey.value`, **declared `Object`**)
   are persisted inside `XdStorageBTreeNode` YAML files — the search-index resource's own
   `prepare()` is a no-op and the keys ride the B+Tree infrastructure.
2. At open, the generic YAML reader binds scalars to the declared field type: `Object` →
   the keys come back as **raw Strings**. There is no restore-to-field-type hook anywhere
   on this path.
3. Before any reopen, the `XdStorageSearchManager` builds keys from live entity values
   (typed `Long`) — everything works. After a reopen, the first tree seek compares the
   raw-String tree keys against the typed query bound →
   `String.compareTo(Long)` → CCE → the mixed-type guard turns it into the loud
   `XdStorageRuntimeException`.

This is the **BUG-C family**: the id-index got exactly this fix in `3575ffb` (record keys
restored to the entity's id-field type at `prepare()`); the search-index keys — living one
layer deeper, inside the node files — were not covered.

**Discovered by** the FP-11 example app (docs/tasks/2.1.0/12): the quickstart's
range-after-reopen assertion failed deterministically 3/3. Acceptance-grade disabled test:
`XdStorageSearchRangeReopenTest` (search package; 3 tests — before-reopen sanity,
after-reopen range+count, after-reopen EQUALS). **Un-disabling it IS the acceptance.**

## Scope

1. **Type authority**: the indexed class's primary index field —
   `indexFieldNames[0]` resolved via `objectClInfo` (the `@XdStorageObjectSearchIndex`
   annotation is reachable from the tree identity `XdStorageBTreeId(clazz, indexName)`).
   The key's `objectId` needs the same treatment with the entity's **id-field type** —
   the BUG-C `restoreKeyValueType` logic is directly reusable.
2. **Fix site** (design options, evaluated):
   - (a) **recommended**: at the search manager's lazy tree-load site (insert/search/count
     converge there) — after `storage.load(XdStorageBTree.class, searchBTreeId, tx)`,
     walk the leaf chain **once** and restore each key's `value` + `objectId`; guard with a
     restore-once flag on the loaded tree instance (it is the canonical instance — the flag
     survives for the storage's lifetime). O(N) one-time per tree per open.
   - (b) node-parse-time restore — requires the target type to reach the generic node
     reader; deeper infra change, not needed for the fix.
   - (c) type-coercing inside `XdStorageSearchIndexKey.compareTo` — **rejected**: silent
     wrong-type comparisons on every node visit, and it hides the (equally broken) EQUALS
     hole instead of restoring the data.
3. **No migration** — on-disk keys were always written from typed live objects; this is
   purely a read-path restore (the BUG-C verdict verbatim).
4. Re-enable the FP-11 quickstart's range/count-after-reopen assertions (currently
   annotated as blocked by this task).

## Out of scope

- The multi-field-index secondary criteria (`fieldValues` on the index record) — they are
  evaluated on the fetched record, not via tree seeks; verify during the fix, extend the
  test if they misbehave, but the seek path is the defect.

## Acceptance criteria

- [ ] `XdStorageSearchRangeReopenTest` un-disabled and green (all 3 tests).
- [ ] The FP-11 quickstart asserts range + count AFTER its reopen again (green).
- [ ] Full suite green ×2 (BUILD SUCCESS verdict); no new FLAKY rows.
- [ ] `CHANGELOG.md` "Fixed" entry updated from "(planned)".

Estimate: 1–2 dev-days.

## References

- Discovered in docs/tasks/2.1.0/12 (the FP-11 example app)
- BUG-C fix `3575ffb` (the reusable `restoreKeyValueType` pattern and its rationale)
- R-53 (OPTIMIZATION-PLAN §6) — the reopen stub-materialization family this joins
