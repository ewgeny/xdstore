---
title: "[2.1.0][BUG-A] Commit-journal replay NPE on the structure resource after a structure update + reopen"
labels: [2.1.0-train, P0, engine, bug]
status: open
---

## Summary

After any `executeStructureUpdate(...)` on a storage, closing and reopening the same folder
throws at open time: `invalid state of database: commit journal of resource
.../structure/XdStorageClassStructure.yaml cannot be replayed`, caused by a
`NullPointerException` inside the journal replay. The storage becomes unopenable —
data on the folder is stranded until the structure journal is cleared manually.

## Why

Discovered by the T-3a gap-fill (docs/tasks/2.1.0/06): the schema-evolution suite's first
reopen scenario crashed deterministically. The structure manager performs its work in an
autonomous executor transaction (`XdStorageStructureManager.checkAndUpdateStructureIfNeed`)
while the caller's outer transaction is open; the journal entries written across that
interplay do not survive a close/open cycle. Beyond the crash itself this blocks every
schema-evolution guarantee (persisted-structure survival, the updater record ledger,
V1→V2 migration) — the structure.update package sits at 13% coverage because its execution
paths cannot be exercised across a reopen.

## Scope

1. Root-cause the replay NPE: capture the journal file written by the
   structure-update flow and identify the entry that replays as null (writer side:
   what the autonomous tx journals; replay side: what the parser dereferences).
2. Fix the journal write or the replay path — whichever holds the defect; the journal
   format is versioned on-disk state, so prefer a fix that keeps existing folders
   readable (a torn/unreplayable structure journal should degrade loudly but not strand
   the folder — decide and document the recovery semantics).
3. Acceptance: the two `BUG A` disabled tests in `XdStorageSchemaEvolutionTest`
   (`reopenSurvivesStructureAndData`, `updaterLedgerIdempotencyAndRetry`) pass un-disabled;
   full suite green ×2.

## Out of scope

- BUG B (id-addressability after a structure update — filed separately; partly masked by
  this crash, investigate independently).
- Redesigning the structure manager's autonomous-tx execution (only as far as the fix
  requires).

## Acceptance criteria

- [ ] The repro opens the same folder twice with a structure update in between, without
      error (the two disabled tests green).
- [ ] A folder hit by the old defect (journal already on disk) opens or fails with a
      documented recovery path — decided and written into the test.
- [ ] Full suite green ×2 (BUILD SUCCESS verdict).

## Verification

```bash
mvn -Dtest=XdStorageSchemaEvolutionTest test          # un-disable the BUG A tests first
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a) — repro pointers and probe history
- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) (commit/journal protocol context)

Estimate: 2–3 dev-days.
