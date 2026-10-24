---
title: "[2.1.0][CACHE2PC] Audit the cache commit protocol — phase-1/phase-2 body duplication and the legacy rollback order"
labels: [2.1.0-train, P2, engine, bug]
status: open
---

## Summary

Two findings of the 28-javadoc-coverage campaign (the doc-writing pass reads code with fresh
eyes) inside `XdStorageCacheCommitProtocol` — the two-phase heart of the resource-cache commit:

1. **`prepareCommit` and `performCommit` have near-identical bodies.** The phase-1 loop looks
   copy-pasted into phase 2; the only visible difference is the flag each phase sets. If the
   two phases were designed to diverge (they usually do in a 2PC), the duplication is a defect
   waiting to mislead the next editor; if they are genuinely identical, that itself needs a
   written reason in the class doc.
2. **The legacy no-journal rollback path rolls back in map-iteration order, not mutation
   order.** The documented guarantee (and the journaled path's behavior) is
   reverse-chronology rollback; the legacy `else if (map != null)` branch silently iterates
   whatever order the map holds — a weaker, order-dependent undo.

## Scope

- Investigate the two bodies: diff them, name what phase 2 should do differently, then either
  fix the divergence or delete the duplication (one loop parameterized by the phase flag) —
  with the decision recorded in the class doc.
- Make the legacy rollback path honor the same ordering contract as the journaled path (or
  prove map order is safe here and pin THAT with a test).
- Contract tests: a multi-resource rollback under the legacy path undoes in reverse mutation
  order; a phase-1-then-phase-2 sequence exercises both loops and asserts the flags/order.

## Out of scope

- The journaled path's behavior (already covered by the W-1/W-3 suites);
- `XdStorageCacheRecord`'s duplicate `getState()`/`getRecordTimestamp()` diagnostics
  (R-40-intentional; docs cross-linked in 28) and the mutable `XdStorageCacheObjectChange.object`
  field (caller-synchronized by design, documented in 28).

## Acceptance criteria

- [ ] The phase-1/phase-2 question is answered: divergence restored or duplication collapsed,
      decision in the class javadoc.
- [ ] Legacy-path rollback order test green (reverse mutation order) — or the documented
      contract corrected to match actual behavior, with the reason.
- [ ] Full suite green ×2.

Estimate: 0.5–1 d.

## References

- [28-javadoc-coverage](28-javadoc-coverage.md) — the campaign that surfaced both findings
- `src/main/java/io/github/xdstorage/resource/cache/XdStorageCacheCommitProtocol.java`
