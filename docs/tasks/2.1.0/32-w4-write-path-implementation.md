---
title: "[2.1.0][W4] W-4 implementation: index journaling per W4-DESIGN — hash id-index first, checkpoint-on-shutdown with it"
labels: [2.1.0-train, P0, engine, performance, feature]
status: open
---

## Summary

Implement the write-path fix per [W4-DESIGN.md](../../../W4-DESIGN.md) — the design
delivered by task 31 from the field-feedback demand ("very low write numbers — surface them
with the plan"). Phased by the design's risk gates:

- **W-4a (this task's core, in-train)**: journal the **hash id-index** — the default id
  index and the measured O(N)-per-commit wall. Flat record file → the W-1 journal machinery
  reused almost verbatim; replay is idempotent by id; the BUG-C `restoreKeyValueType`
  pattern applies on replay. Flag `indexJournalWrites` (default off → soak → flip).
- **W-4c (lands with W-4a)**: **checkpoint on clean shutdown** (task 23's deferred item,
  now designed) + per-resource journal size bounds — after any clean close, journals are
  empty and a later power loss loses nothing even with `durableWrites=false`.
- **W-4b (next after W-4a's gates)**: the search key-tree **logical-op journal**
  (ADDKEY/DELKEY replayed into the live canonical tree — divergence-safe by design, unlike
  the W-3 node-snapshot approach the soak rejected). Starts only after W-4a's soak is green.
- **W-4d (decision-gated, may complete in 2.2.0)**: disk-anchored scans + record-cache
  eviction — the heap≈DB ceiling. Gate: W-4a/b landed cleanly AND task 20's N50K heap data
  demands it; carries its own design note before code.

## Why (the one-paragraph version)

The write wall is a **scaling wall**, not an absolute one: sub-millisecond commits at N≈10³
(canonical ~731 ops/s save+delete), but the hash id-index rewrites its **entire file** —
O(N) IO plus O(N) `readUnsafe` CPU — on every touched write commit, exactly across the
published design point of 10⁴–10⁵ objects per class. Journaling it converts O(N) → O(delta)
with the machinery, recovery semantics and naming already proven by W-1..W-3 and tasks
23/24. Full analysis and the rejected alternatives: [W4-DESIGN.md](../../../W4-DESIGN.md) §1–§2.

## Scope (W-4a + W-4c, in-train)

1. `indexJournalWrites` config flag (default off); the hash id-index resource joins the
   W-1 journal path — append `UPSERT/DELETE` redo in phase-1, replay at open, checkpoint =
   full rewrite + reset (JRNLNAME/task-24 naming for the journal file).
2. **Replay correctness**: BUG-C key-type restoration on replayed records (numeric ids are
   the point of this fix — they must not come back as raw Strings); idempotent re-replay;
   torn-tail discard + WARN (W-1 policy verbatim).
3. **Checkpoint on clean shutdown** for every journal-bearing resource (data + index), plus
   the open-time self-heal checkpoint (oversized journal after replay → checkpoint).
4. **Before/after measurement duty** (W4-DESIGN § acceptance): add the canonical scenarios
   `saveAndDeleteIndexed@N` and `updateRename@N` (N=1k/5k/50k) FIRST, record the honest
   before-numbers, then re-measure with the flag on. W-4a's gate: the N=50k write cost
   within a **constant factor ≤3× of the N=1k cost** (the O(N) term eliminated) — or the
   honest re-scoping with the measured residual.
5. **The soak**: full suite ×N with the flag ON (the W-3 soak protocol, including its
   humility — any divergence found ends in a documented exclusion, not a forced flip);
   kill-9 mid-journal recovery tests; the 2.0.0-upgrade path (folders without index
   journals) pinned.
6. **Docs**: WRITE-PATH-DESIGN/W4-DESIGN status updates, EMBEDDING config recap +
   compatibility note, CHANGELOG Added/Changed, README limitation block gets its
   "fixed-in-2.1.0" status update (task 29's link).

## Out of scope

W-4b search-tree journal (starts after W-4a's soak — same task file, new phase comment);
W-4d eviction (decision-gated); BTree id-index journaling (measured near-delta already —
re-evaluate with W-4a's numbers); group commit (the fallback valve, only if journaling
stalls).

## Acceptance criteria

- [ ] Before-numbers recorded (N=1k/5k/50k) in the canonical estate BEFORE the flag
      exists (the honest baseline).
- [ ] W-4a green behind the flag: journal round-trip, idempotent replay, BUG-C typing on
      replay, torn tail, kill-9 recovery, 2.0.0-upgrade (no index journal) — dedicated
      suites.
- [ ] W-4c green: clean shutdown empties every journal; post-shutdown power-loss
      simulation (task 23's matrix) loses nothing; oversized-journal self-heal at open.
- [ ] Soak green (full suite ×N with flag ON, the W-3 protocol) → default flip decided
      with data (a flip without a green soak is forbidden).
- [ ] After-numbers reported honestly against the ≤3× constant-factor gate.
- [ ] Full suite green ×2; no new FLAKY rows; CHANGELOG + docs updated.

Estimate: W-4a ~2–3 sessions, W-4c ~1 session (in-train); W-4b ~3–4 (next); W-4d gated.
Risk: MEDIUM — mitigated by the flat-record design choice, the landed patterns (W-1
machinery, BUG-C restore), and the flag lifecycle.

## References

- [W4-DESIGN.md](../../../W4-DESIGN.md) — the governing design (analysis §1, design §2,
  targets, risks)
- Task 31 (this task's parent — the analysis/design deliverable), task 29/30 (the pages
  that publish the fix plan)
- W-1..W-3 history in [WRITE-PATH-DESIGN.md](../../../WRITE-PATH-DESIGN.md); the W-3 soak
  lesson (documented exclusions over forced flips)
- BUG-C fix `3575ffb` (the replay key-type pattern); task 24 (journal naming); task 23
  (checkpoint-on-shutdown size note)
