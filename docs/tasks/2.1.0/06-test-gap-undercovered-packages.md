---
title: "[2.1.0][T-3a] Test gap-filling: suites for the under-covered packages"
labels: [2.1.0-train, P1, testing, tech-debt]
status: open
---

## Summary

Write the missing test suites for the packages the coverage map (task 04) shows as dark or
thin, prioritized by **public-surface relevance**, not by percentage.

## Why

Ground truth before the map: `operation` (0 test classes), `updaters` (0), `structure` (2),
`trigger` (1), `observing` (2), `lock` (1), `object` (3), `index` (2) — while `btree`,
`resource`, `utils` are well covered (the R-campaign built their suites). The asymmetry means
the least-tested code is exactly the code an embedder touches first (triggers, observers,
structure updates) or that guards correctness (lock, index). Decision record:
[MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-3.

## Scope

Per package — a dedicated suite class with happy-path AND failure-path tests:

- `operation` and `updaters` (currently zero — start here; these are public-surface).
- `structure` (schema-update hooks): add/rename/remove field scenarios, persisted-data
  survival, mixed-version open.
- `trigger` (insert/update/delete triggers inside the committing transaction): trigger order,
  rollback-on-throw semantics (the R-24-era ERROR path), nested-tx interplay.
- `observing` (deferred-id observers): observer invocation on id assignment, exception in
  observer, JVM-global registry behavior (document it in the test).
- `lock` (the RW-lock): reentrancy, sole-reader upgrade, fair-queue writer preference — the
  invariants R-26 landed, pinned directly.
- `object` + `index`: storage-policy and index-resource contracts not covered elsewhere.
- `idgeneration` (6 existing classes): edge cases — Hi-Lo batch boundary (the ≤99 burned ids
  crash contract), UUID/custom generator SPI.

Conventions (binding): unique storage name+dir per test; no sleeps/time-gating; R-52 verdict
rule; anything load-sensitive gets `@Tag("stress")` and lands in [FLAKY.md](../../FLAKY.md).

## Out of scope

- God-class internal paths (`XdStorageBTreeNode` etc.) — covered by existing btree suites;
  deepen only where the map shows a dark public method (coordinate with task 07).
- Refactoring production code to make it testable — test what is; propose a follow-up issue
  if a seam is genuinely missing.

## Acceptance criteria

- [ ] Every listed package has a dedicated suite; `operation`/`updaters` are no longer zero.
- [ ] Suite total grows by the map-informed target (order of +40–80 tests; justify the number
      in the PR — do not pad).
- [ ] Coverage map delta vs task 04's baseline in the PR description.
- [ ] Full suite green ×2 consecutive runs (BUILD SUCCESS verdict).
- [ ] No new FLAKY.md rows without the evidence entry (rate + signature).

## Verification

```bash
mvn clean test && mvn clean test    # ×2 consecutive green
# coverage delta:
mvn verify && open target/site/jacoco/index.html
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-3
- Task 04 (the map scoping this), task 05 (tags for anything slow)

Estimate: ~2 dev-days. Depends on: 04, 05.
