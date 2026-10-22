---
title: "[2.1.0][BUG-B] Objects unreachable by id after a structure update on a freshly-opened storage"
labels: [2.1.0-train, P1, engine, bug]
status: open
---

## Summary

With an entity class loaded through a non-system classloader (the schema-evolution
suite's runtime-compiled V2 generation), the sequence save → reopen →
`executeStructureUpdate(V2)` → load-by-id returns **null**: the object written by the
previous storage instance is no longer addressable. The same sequence with a plain
compiled entity (`RangeOpsEntity`) works — verified by probes during T-3a — so the
defect lives in the interplay of the structure-update pass with the id-index/data
resolution for non-system-loader classes.

## Why

Discovered by the T-3a gap-fill (docs/tasks/2.1.0/06). Schema evolution is the feature
this engine's structure-update machinery exists for; an entity that vanishes from
id-addressability after its structure is migrated is silent data unavailability. The
dual-classloader scenario is not exotic: it is exactly what an application upgrade
(older entity class version on disk, newer class in the running JVM) looks like.

## Scope

1. Root-cause: walk the load-by-id path (`lockIndexResource` → resource id → data read)
   for a non-system-loader class after a structure update; compare with the working
   plain-class probe. Suspects: class-name vs classloader-qualified keying in the id
   index or the metadata registry, and the structure pass re-registering the class
   under a second identity.
2. Fix the identity model (one class, one id-index identity, loader-independent).
3. Acceptance: the two `BUG B` disabled tests in `XdStorageSchemaEvolutionTest`
   (`evolutionDetectsChangeAndReportsUpdaterOutcomes`, `migratedDataReadableAsV2`)
   pass un-disabled — change detection fires, the migrated object is readable through
   the V2 class with its persisted fields intact.

## Out of scope

- BUG A (the journal replay NPE — filed separately; where it masks this defect, split
  the repro along the probe evidence).
- Implementing full schema-evolution UX beyond restoring the invariants.

## Acceptance criteria

- [ ] The two disabled tests green.
- [ ] The plain-class probe sequence still green (no regression in the working path).
- [ ] Full suite green ×2 (BUILD SUCCESS verdict).

## Verification

```bash
mvn -Dtest=XdStorageSchemaEvolutionTest test          # un-disable the BUG B tests first
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a) — probe history isolates the defect from BUG A
- Depends conceptually on 01-commit-journal-replay-npe (where the journal crash masks it)

Estimate: 1–2 dev-days.
