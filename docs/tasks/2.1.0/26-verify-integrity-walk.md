---
title: "[2.1.0][VERIFY] verify(): closed-folder integrity walk — the recovery recipe, mechanized"
labels: [2.1.0-train, P1, engine, testing]
status: open
---

## Summary

A read-only integrity API over a **closed** storage folder: `verify(folder)` walks the
engine-owned files and returns a report — per-resource sentinel validity, journal presence
and state, object counts, index-vs-data consistency — detecting every corruption class the
power-loss matrix taught us about, without mutating anything. The manual recipe from the
recovery documentation becomes one call.

## Why

Pulled from MASTER-PLAN Step 3 (FP-5's verification core), scoped down to what this train
already earned: task 19 proved copy-restore, task 23 built the truncation matrix and
documented the manual after-a-power-loss recipe — but the recipe's checks (is the sentinel
intact? is the journal torn? do the index counts match the data?) are grep-and-hope today.
`verify()` mechanizes them. It is the natural companion of both, it is read-only (no new
persistence semantics), and it prepares the FP-6 CLI (2.3.0) with a tested core instead of
script.

## Scope

1. **API**: a static/facade entry (`XdStorageProvider.verify(folder)` — exact shape in the
   PR design note) returning an `XdStorageVerifyReport`: per discovered resource —
   sentinel valid / missing (legacy v1) / **torn**; journal present? empty? size; object
   counts per class; id-index entries vs data objects; search-index key counts vs records;
   format marker version; overall verdict. **Read-only**: files opened, never written; the
   folder lock is *not* required (a stale/traveled `.lock` must not block a verify — the
   task-19 lock semantics).
2. **Detection classes** (from task 23's matrix — verify detects what the matrix asserts
   about open): truncated base file (sentinel failure), torn journal tail, missing format
   marker, count mismatches, orphaned journal files (no matching base), stale `.tmp` litter.
3. **Boundaries, loudly drawn**: `verify` never repairs (task 23's rejection — silent
   truncation acceptance stays impossible); it does not open a storage (no cache warm-up,
   no replay — it reads files the way the recovery recipe's manual checks would);
   verify-while-open is refused or reported as "in use — results unreliable" (design note
   picks one; recommend: report, not refuse — read-only file inspection is safe).
4. **Docs**: EMBEDDING.md § After a power loss — the recipe's manual steps gain "or run
   `verify`"; the report's fields documented; DECISION-GUIDE operability bullet updated.

## Out of scope

- Repair/compact (never in this API — FP-6 `compact` stays 2.3.0);
- online verify of an open, committing storage (MVCC-consistent verify = FP-5's full scope,
  2.3.0);
- backup (`backupTo` stays 2.3.0 — this task is FP-5's verification core only);
- repair *recommendations* beyond naming what is wrong.

## Acceptance criteria

- [ ] Green-folder report: a healthy storage from the task-19 walkthrough verifies clean
      (verdict OK, counts consistent).
- [ ] Detection suite: each corruption class from the task-23 matrix (truncated base,
      torn journal, missing marker, orphaned journal, `.tmp` litter, count mismatch via a
      hand-edited file) is DETECTED with a report entry naming the file — verified by
      corrupting copies, never originals.
- [ ] Verify never mutates the folder (byte-identical before/after in the test).
- [ ] Full suite green ×2; `CHANGELOG.md` "Added".

Estimate: ~1–2 dev-days (the count-consistency walk is the careful half).

## References

- Task 19 (the copy/lock semantics verify relies on) and task 23 (the corruption classes
  and the manual recipe this mechanizes)
- [EMBEDDING.md](../../EMBEDDING.md) § After a power loss (the recipe to upgrade)
- [MASTER-PLAN.md](../../MASTER-PLAN.md) Step 3 — FP-5's verification core pulled into this
  train (2026-10-23); `backupTo` + the FP-6 CLI stay 2.3.0
