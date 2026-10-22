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

## Root cause (precisely characterized by the BUG-A session, 2026-10-23)

The journal replay and identity resolution are FIXED (docs/tasks/2.1.0/15): after the
fixes, the reopened storage OPENS, the id-index resolves, the data is found — and the
object still cannot be MATERIALIZED. The remaining seam is the codegen wrapper identity:

- every stored entity is wrapped (`XdStorageSimpleWrapperClassCodeGenerator`) under a
  NAME-based wrapper class (`<Entity>SimpleWrapper`) in one shared code directory, loaded
  through one shared classloader (`XdStorageClassGenerator.sharedClassLoader`);
- two LIVE generations of one FQCN (V1 saved by storage-1, V2 loaded by the app through
  its own loader) collide on that one wrapper name: the first generation's wrapper class
  wins `Class.forName`, and the second generation's object fails instantiation with
  `NoSuchMethodException: <Wrapper>.<init>(Object, evolution.EvoEntity, IXdStorage)` —
  exactly reproducible with `XdStorageSchemaEvolutionTest`'s dual-classloader scenarios
  (their `@Disabled` annotations carry the signature).

The fix is a DESIGN decision, not a patch: generation-aware wrapper identity (loader-
or-content-qualified wrapper names with per-generation loading), or wrapper-less
materialization for non-system-loader classes. Both touch the codegen contract that the
whole read path depends on — hence the task stays open and scoped to the 2.2.0-class
surgery tier alongside W-4.

## Scope

1. Decide the wrapper identity model (generation-qualified names vs wrapper-less
   materialization fallback) — record it in [MASTER-PLAN.md](../../MASTER-PLAN.md) § codegen.
2. Implement + make the four `@Disabled` dual-classloader scenarios in
   `XdStorageSchemaEvolutionTest` pass un-disabled (change detection fires; the migrated
   object is readable through the V2 class with its persisted fields intact).
3. A regression: the plain-class (system-loader) probe sequence stays green.

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
