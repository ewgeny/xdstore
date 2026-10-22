---
title: "[2.1.0][T-3a] Test gap-filling: suites for the under-covered packages"
labels: [2.1.0-train, P1, testing, tech-debt]
status: done
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

- [x] Every listed package has a dedicated suite; **the `operation`/`updaters` premise was
      stale** — no such packages exist in `src/main/java` (the updater SPI lives in
      `structure.update`; verified during the inventory, task text corrected here). Suites
      landed for `structure` (+`structure.update` contracts via the facade), `trigger`,
      `idgeneration`, `index`, `object`; `lock` (100%/84%br) and `observing` (96%/100%br)
      were already at or near full coverage at the task-04 baseline — no new suites needed there, per
      "prioritized by the map".
- [x] **+31 executing tests** (743 → 774), plus 5 acceptance-grade `@Disabled` tests pinned
      on discovered engine defects (below). The +40–80 band assumed `operation`/`updaters`
      existed; the real remaining dark surface is blocked by the discovered defects, and the
      task forbids padding — the count is every contract that could be pinned honestly.
      Justification per suite: trigger ×7, idgeneration ×7 (1 disabled), index ×7,
      structure ×7 (4 disabled), object ×4, plus the T-2 lint test already counted in 743.
- [x] **Coverage delta** (quick-gate verify, same HEAD before/after; task-04 numbers in
      parens where the map differs):

      | Package | before | after | note |
      |---|---|---|---|
      | `structure` | 8% lines / 5% br | **54% / 26%** | the schema-evolution suite |
      | `trigger` | 73% / 60% | **88% / 80%** | the facade contour suite |
      | `index` | 75% / 50% | **100% / 92%** | the cache contract suite |
      | `structure.update` | 10% / 0% | 13% / 0% | execution paths blocked by BUG A/B |
      | `object` | 13% / 7% | 14% / 8% | value contracts; the 8% converter is a god class (out of scope) |
      | `idgeneration` | 74% / 45% | 74% / 45% | facade contracts; generator internals partly BUG-C-blocked |
      | TOTAL | 7634/10951 lines | 7815/10995 | +~180 lines covered |

- [x] Full suite green ×2 consecutive (774 tests, 0 failures, BUILD SUCCESS — twice; one
      intervening run tripped the **documented** `XdStorageBTreeStressTest` flake
      (FLAKY.md, Track B3, untouched by this task) and was rerun).
- [x] No new FLAKY.md rows: nothing load-sensitive was added (all suites are deterministic;
      no sleeps; R-50-unique storages; the one hang risk found — unguarded reentrant triggers
      — is documented in the trigger suite as a required discipline, not a flake).

## Engine defects discovered by this gap-fill (follow-up issues)

- **BUG A — commit-journal replay NPE on the structure resource**: after any
  `executeStructureUpdate`, closing and reopening the folder throws
  `commit journal of .../structure/XdStorageClassStructure.yaml cannot be replayed (NPE)`
  at open time. Blocks: persisted-structure survival, the updater ledger end-to-end,
  real schema evolution. Repro: `XdStorageSchemaEvolutionTest` (the two `BUG A` tests).
- **BUG B — id-addressability after a structure update on a freshly-opened storage**: with a
  runtime-generated entity, save → reopen → `executeStructureUpdate` → load-by-id returns
  null; the same sequence with a plain compiled entity works (probed with `RangeOpsEntity`).
  Blocks: the V1→V2 evolution, migrated-data readability. Repro: the two `BUG B` tests.
- **BUG C — auto-generated ids are unreachable by id after reopen**: the id is assigned
  during the write-out phase, after the transaction's index-resource changes snapshot was
  taken (id still null at save time); the in-memory cache resolves it, the persisted
  id-index record does not exist. Explicit ids unaffected. Repro:
  `XdStorageIdGenerationContractTest.generatedIdsSurviveReopen` (`@Disabled`).
- **Contract surprises pinned as actual behavior** (not bugs, but read before relying on them):
  the writer auto-generates ids only for `DATABASE`/`CUSTOM` generator types —
  `UUID_GENERATOR` on a field never auto-generates (null id fails loudly at save);
  a trigger doing storage work must open its OWN transaction (writes through the committing
  tx land after the first phase and are lost — the `IXdStorageTrigger` javadoc says exactly
  this); unguarded reentrant triggers (an auditing trigger without an id-prefix guard
  recurses on its own commits and deadlocks the bounded commit executor — observed live);
  `XdStorageObject.getProperty` NPEs before the first `setProperty`.

## T-3 litter found during the source walk (input for task 07)

3 stray sources declaring another directory's FQCN (`index/XdStorageHashIndexRecordTest` →
`index.hash`, `structure/XdStorageUpdateStructureRecordTest` → `structure.update`,
`idgeneration/XdStorageSubGeneratorsTest` → `code`), non-test scratch classes matching
`*Test.java` (`temp/SpecificTest`, `code/SimpleCodeGeneratorTest`,
`code/IdObservableCodeGeneratorTest`, `code/UnmodifiableCodeGeneratorTest` — `main()`-only),
and the empty `idgeneration/XdStorageDefaultIdGeneratorTest`.

## Verification

```bash
mvn clean test && mvn clean test    # 774 tests, 0 failures — ×2 consecutive BUILD SUCCESS
mvn clean verify -Pquick-gate       # coverage delta: target/site/jacoco/
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-3
- Task 04 (the map scoping this), task 05 (tags for anything slow)

Estimate: ~2 dev-days.
