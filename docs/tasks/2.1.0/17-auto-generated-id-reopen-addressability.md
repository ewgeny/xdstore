---
title: "[2.1.0][BUG-C] Auto-generated ids are unreachable by id after a reopen"
labels: [2.1.0-train, P0, engine, bug]
status: done
---

## Summary

An object saved with a numeric id — **auto-generated or explicit** — cannot be found by
`load(Class, id, tx)` after the storage is closed and reopened: the lookup returns null.
String-id objects are unaffected (which masked the defect for years).

**Root cause (corrected during the fix — the write-out-timing hypothesis below was wrong):**
the id is assigned at save time in the id-index resource, and the id-index records DO reach
disk correctly. The defect is a TYPE LOSS on reopen: the hash id-index resource's `clInfo`
describes the RECORD class (`XdStorageHashIndexRecord.objectId`, declared `Object`), so when
the record cache fills from the YAML file the keys stay raw strings/numbers — `Long(9000)`
never matches the stored `"9000"` and every numeric-id lookup silently misses. The same
storage instance works because its in-memory cache holds keys of the real type.

## Why

Discovered by the T-3a gap-fill (docs/tasks/2.1.0/06); root cause already localized. The
writer assigns the generated id during the **write-out phase**
(`XdStorageDefaultObjectsWriter` line ~133), i.e. *after* the transaction's
index-resource change snapshot was taken at `save()` time — when the id field was still
null. The commit therefore persists the object under its generated id but never the
id-index record that maps id → resource. Everything works until the in-memory cache goes
away; after a reopen the object is stranded (silent data-addressability loss, the worst
kind: no error, just a vanishing object).

## Scope

1. ~~Move generated-id assignment...~~ (superseded: the assignment point was never the defect)
2. **Fix (landed)**: `XdStorageHashIndexResource.prepare()` restores each record key to the
   ENTITY's id-field type (via `objectClInfo` — the resource's own `clInfo`/`idField` describe
   the record class) using the engine's simple-type conversion, before filling the cache.
3. A folder written by the defective version needs NO migration: the index records were
   always correct on disk — only the in-memory key typing was wrong, so the fix alone
   recovers previously-stranded objects.
4. Acceptance: `XdStorageIdGenerationContractTest.generatedIdsSurviveReopen` passes
   un-disabled (plus the new `explicitNumericIdsAddressableAfterReopen` regression — the
   defect was type-wide, not generation-specific); full suite green ×2.

## Out of scope

- The Hi-Lo batch/burn semantics (working as documented; T-3a pinned them).
- `UUID_GENERATOR` field-property semantics (filed separately as a 2.1.0 item —
  14-uuid-generator-field-property).

## Acceptance criteria

- [x] The disabled repro test green (save with generated id → reopen → load-by-id found).
- [x] The type-wide regression covered: explicit numeric ids get their own reopen test
      (child/collection saves share the same id-index resource and need no separate fix —
      the defect was in the reopen-time key typing, not in any save path).
- [x] Full suite green ×2 (BUILD SUCCESS verdict — final run of the bug-fix session).

## Verification

```bash
mvn -Dtest=XdStorageIdGenerationContractTest test    # un-disable generatedIdsSurviveReopen first
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a); root cause corrected during the fix (see Summary)
- Related: docs/tasks/2.1.0/14 (BUG-D — same test class, different defect)

Estimate: 2–3 dev-days.
