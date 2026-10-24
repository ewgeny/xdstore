---
title: "[2.1.0][SILENTFAIL] Silent-failure hazards in codegen/observer/cloner — fix the three swallow-and-continue paths"
labels: [2.1.0-train, P2, engine, bug]
status: open
---

## Summary

Three hazards found by the 28-javadoc-coverage campaign, all in the same family — a failure is
swallowed and execution continues with a degraded or `null` result, so the real defect surfaces
far from its cause (or never):

1. **`XdStorageClassGenerator.compileGeneratedClassesCode`** (~line 171): `result.put(cl, clazz)`
   sits INSIDE the generation loop and keys EVERY generated class by the entity class — a
   multi-class generation graph (entity + generated children/wrappers compiled together) keeps
   only the last loaded class per entity key. Today's callers pass single-class compilations, so
   nothing breaks — but any future multi-class use silently loses classes.
2. **`XdStorageObserverService.getObservableWrapper`**: unsynchronized check-then-act after the
   `computeIfAbsent` — two racing threads can both generate and register a wrapper for the same
   class (last put wins). It can also return `null`: the deprecated `wrapAsObservableObject`
   swallows ALL exceptions, so a codegen failure masquerades as "no wrapper".
3. **`XdStorageCollectionCloner.cloneCollection`/`cloneMap`** swallow every `Throwable` and
   return `null` — the MVCC snapshot materialization then silently drops the collection (the
   same data-loss family the R-44 comment in `XdStorageMvccGraphCloner` describes).

## Scope

- (1) key the result map by the GENERATED class (or the class that requested it — read the
  callers and pick the contract), and add a multi-class test;
- (2) close the race (the registration already goes through a concurrent map — use its atomic
  forms end-to-end) and make the failure path loud: propagate or throw instead of returning
  `null` (callers audited);
- (3) propagate clone failures as `XdStorageException`/runtime with the offending collection
  type in the message; the test asserts a poisoned element fails LOUDLY, never a `null`
  snapshot.

## Out of scope

- The deprecated `wrapAsObservableObject` API itself (removal is a MAJOR semver event);
- any change to when wrappers are generated (only the failure/race semantics).

## Acceptance criteria

- [ ] Each of the three has a failing-first test that passes after the fix (or an explicit
      documented decision why the swallow is load-bearing, in the class javadoc + here).
- [ ] No silent `null` returns remain on the three paths.
- [ ] Full suite green ×2.

Estimate: 1–1.5 d.

## References

- [28-javadoc-coverage](28-javadoc-coverage.md) — the campaign that surfaced all three
- `code/XdStorageClassGenerator.java`, `observing/XdStorageObserverService.java`,
  `utils/XdStorageCollectionCloner.java`, the R-44 note in `utils/XdStorageMvccGraphCloner.java`
