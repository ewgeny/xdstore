---
title: "[2.1.0][BACKUP] Backup/restore walkthrough: prove 'cp -r while closed' as a tested guarantee"
labels: [2.1.0-train, P1, testing, docs]
status: done
---

## Summary

The decision guide sells operability with one sentence: *"a folder you can back up with
`cp -r` while closed."* Today that is prose, not a guarantee — nothing in the 780-test suite
proves a copied folder reopens with its content and indexes intact. Make it a tested,
documented contract.

## Why

DECISION-GUIDE.md § "Where XdStorage wins outright" (item 2) and § "Concretely good fits"
name backup-by-copy as a differentiator for the adoption audience (auditors, fixtures,
edge gateways). The first embedder who tries it should not be the test. The engine already
guarantees the pieces (atomic rename write-outs, EOF sentinels, the format marker); this
task pins their composition under the one operation every ops engineer will perform.

## Scope

1. **Reliability test** (`reliability` package, plain JUnit — not stress):
   - build a storage with known content across all three storage policies (embedded child,
     single-object, class-fragments) + a numeric-keyed search index and a String-keyed one;
   - `shutdown()` → deep-copy the folder (`Files.walk` + copy) to a new location;
   - open the COPY under a new storage name; assert: object counts per class, load-by-id of
     sampled entities, EQUALS on the String-keyed index, and (once BUG-E/task-18 lands) a
     range query on the numeric index; assert the `.xdstorage.lock` is NOT part of a valid
     backup (the copy must open cleanly — the original's lock file must not travel);
   - negative documentation: copying an OPEN folder is unsupported (the OS lock blocks
     same-machine copies — assert the lock file exists while open, and state the contract
     in the test's javadoc).
2. **EMBEDDING.md § Files and durability**: a short "Backup and restore" subsection —
   the procedure (close → copy → open the copy), what travels, what must not (`.lock`),
   and the pointer to the test as the executable proof.

## Out of scope

- Hot/online backup (`backupTo(folder)` — FP-5, pinned 2.3.0); `verify()` integrity walks
  (same train later); backups across format generations.

## Acceptance criteria

- [x] The walkthrough test is green ×2 (BUILD SUCCESS verdict).
- [x] EMBEDDING.md documents the procedure and links the test (§ Backup and restore).
- [x] The decision guide's operability sentence links the new subsection.
- [x] No engine code changes (test + docs only).

## Implementation notes

- **One assumption corrected by the run**: the task said "the original's lock file must not
  travel" — factually wrong for this engine. The lock FILE remains after a clean shutdown
  (what shutdown releases is the exclusive OS file lock), so a copied folder travels with a
  harmless lock file and opens cleanly. The test pins the REAL contract: lock file present
  while open; release proven by re-opening the original; the copy carries the file and opens;
  a stale lock file (unclean shutdown) is equally harmless (second test).
- The copied storage is verified across all three policies: class fragments (ships + crew
  child, load-by-id + scan + String-keyed EQUALS), single-object (map payload), parent +
  `StoreWithParentObject` child (count + load-by-id), plus the numeric-keyed index range +
  count (BUG-E semantics travel with the copy).
- Fixture gotchas: the engine's cloner needs no-arg-constructible collections (a fixture
  using `Arrays.asList` broke the save — wrap in `ArrayList`); each entity's index name is
  its own contract (the BUG-E acceptance entity's index is `range_idx`, not `by_score`).

Estimate: ~0.5–1 dev-day.

## References

- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) § wins (2) / § good fits — the sentence this
  task makes true
- [EMBEDDING.md](../../EMBEDDING.md) § Files and durability (to extend)
- docs/tasks/2.1.0/18 — the numeric-index-after-reopen dependency for one assertion
