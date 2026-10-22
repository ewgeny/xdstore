# 2.2.0 train — work packages (deep engine surgery)

One markdown file = one GitHub issue. The frontmatter (`title`, `labels`) is read by
[`scripts/sync-issues.sh`](../../../scripts/sync-issues.sh) (dir name = milestone); everything
below it becomes the issue body. Per the version-pinning decision (MASTER-PLAN § trains):
2.1.0 carries no deep engine surgery — the commit/write-path defects below land here, alongside
W-4 and B3. Every defect was discovered by the T-3a gap-fill (docs/tasks/2.1.0/06) and ships
with an acceptance-grade disabled test — un-disabling it IS the fix's acceptance test.

## Index

| # | File | Defect | Labels | Est. | Depends on |
|---|---|---|---|---|---|
| 01 | [commit-journal-replay-npe](01-commit-journal-replay-npe.md) | BUG A | P0, engine, bug | 2–3 d | — |
| 02 | [id-addressability-after-structure-update](02-id-addressability-after-structure-update.md) | BUG B | P1, engine, bug | 1–2 d | 01 (where masked) |
| 03 | [auto-generated-id-reopen-addressability](03-auto-generated-id-reopen-addressability.md) | BUG C | P0, engine, bug | 2–3 d | — |

## Execution lanes

- 01 ∥ 03 (independent surfaces: structure journal vs write-out id assignment);
  02 after 01 (the journal crash masks part of its repro).

## Defect snapshot (evidence in docs/tasks/2.1.0/06)

| Defect | Signature | Strands data? | Acceptance test |
|---|---|---|---|
| BUG A | journal replay NPE on `structure/XdStorageClassStructure.yaml` after structure update + reopen | folder unopenable | `XdStorageSchemaEvolutionTest` BUG-A tests |
| BUG B | load-by-id null after structure update on a freshly-opened storage (non-system-loader classes) | object unreachable | `XdStorageSchemaEvolutionTest` BUG-B tests |
| BUG C | auto-generated ids unreachable by id after reopen (id assigned at write-out, after the tx index snapshot) | object unreachable | `XdStorageIdGenerationContractTest.generatedIdsSurviveReopen` |
