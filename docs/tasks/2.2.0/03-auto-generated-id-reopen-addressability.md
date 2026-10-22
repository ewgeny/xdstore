---
title: "[2.2.0][BUG-C] Auto-generated ids are unreachable by id after a reopen"
labels: [2.2.0-train, P0, engine, bug]
status: open
---

## Summary

An object saved with an **auto-generated id** (`DATABASE_GENERATOR` default, id field left
null) cannot be found by `load(Class, id, tx)` after the storage is closed and reopened —
the lookup returns null. The same lookup in the *same* storage instance works: the
in-memory id-index cache resolves the pointer, but the persisted id-index record does not
exist. Explicitly assigned ids are not affected.

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

1. Move generated-id assignment to before the transaction's resource-change snapshot
   (facade `save`, where the FP-1a-era "facade assigns ids in the index resource before
   search-insert" note already points) — or persist the index record for write-out-time
   generated ids during commit; decide per [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md).
2. Sweep the sibling write paths (the writer has ~4 copies of the generation condition —
   lines ~133/485/535/582) so collection/child saves are fixed by the same change.
3. A folder written by the defective version contains stranded objects: document (and
   ideally provide) a recovery/migration note — the objects exist, only the id-index
   records are missing.
4. Acceptance: `XdStorageIdGenerationContractTest.generatedIdsSurviveReopen` passes
   un-disabled; full suite green ×2.

## Out of scope

- The Hi-Lo batch/burn semantics (working as documented; T-3a pinned them).
- `UUID_GENERATOR` field-property semantics (filed separately as a 2.1.0 item —
  14-uuid-generator-field-property).

## Acceptance criteria

- [ ] The disabled repro test green (save with generated id → reopen → load-by-id found).
- [ ] Child/collection save paths covered by the same fix (a test each or an explicit
      statement why not applicable).
- [ ] Full suite green ×2 (BUILD SUCCESS verdict).

## Verification

```bash
mvn -Dtest=XdStorageIdGenerationContractTest test    # un-disable generatedIdsSurviveReopen first
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a); the disabled test documents the full repro
- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) (commit/write-out protocol context)

Estimate: 2–3 dev-days.
