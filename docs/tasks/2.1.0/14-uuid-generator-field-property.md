---
title: "[2.1.0][BUG-D] The UUID_GENERATOR field property never auto-generates — decide the contract and make it true"
labels: [2.1.0-train, P2, engine, bug]
status: open
---

## Summary

`@XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.UUID_GENERATOR)`
on an id field does **not** activate auto-generation: the writer's generation condition
covers only `DATABASE_GENERATOR` and `CUSTOM_GENERATOR`
(`XdStorageDefaultObjectsWriter`, ~lines 133/485/535/582), so a null id under
`UUID_GENERATOR` fails loudly at save (`NullPointerException` from the index write) and
the application must assign the id itself. The property name promises generation; the
engine does not deliver it.

## Why

Discovered by the T-3a gap-fill (docs/tasks/2.1.0/06); the actual behavior is pinned by
`XdStorageIdGenerationContractTest.stringIdGenerationContract`. This is a
decision-plus-small-fix item, safe for the 2.1.0 train (no commit-protocol surgery):
either the property gains generation semantics, or it is redefined as explicit and the
contract is documented where users will find it.

## Scope

1. **Decide the contract** (decision recorded in MASTER-PLAN § Track T or here):
   - **(a) make it generate** — extend the writer's condition to include
     `UUID_GENERATOR` (the String generator already emits UUIDs, so the semantics are
     unambiguous for String ids; non-String ids under this property are a loud
     validation error); or
   - **(b) make it honest** — keep the behavior, rename/document the property as
     "app-assigned" and point users at the `DATABASE` default for auto-UUIDs.
   Recommendation: (a) — the annotation name already promises it, and the String
   generator makes (a) a two-line change.
2. Update `XdStorageIdGenerationContractTest.stringIdGenerationContract` to assert the
   decided contract (it currently pins the pre-fix behavior).
3. Document the final id-generation matrix (type × generator type → who assigns) in
   README § Search engine or EMBEDDING — one table, next to where T-3a's contracts live.

## Out of scope

- The write-out-time assignment re-order (BUG C, docs/tasks/2.1.0/17) — this item only
  touches the generator-type condition, not the assignment point.

## Acceptance criteria

- [ ] The decided contract implemented and pinned by the (updated) contract test.
- [ ] The id-generation matrix documented (one table).
- [ ] Full suite green ×2 (BUILD SUCCESS verdict).

## Verification

```bash
mvn -Dtest=XdStorageIdGenerationContractTest test
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a); the pinned actual behavior is in the contract test
- docs/tasks/2.1.0/17 (the related write-out-time assignment defect — different fix, same test class)

Estimate: 0.5 dev-day.
