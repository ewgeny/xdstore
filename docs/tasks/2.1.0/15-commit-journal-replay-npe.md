---
title: "[2.1.0][BUG-A] Commit-journal replay NPE on the structure resource after a structure update + reopen"
labels: [2.1.0-train, P0, engine, bug]
status: done
---

## Summary

After any `executeStructureUpdate(...)` on a storage, closing and reopening the same folder
throws at open time: `invalid state of database: commit journal of resource
.../structure/XdStorageClassStructure.yaml cannot be replayed`, caused by a
`NullPointerException` inside the journal replay. The storage becomes unopenable —
data on the folder is stranded until the structure journal is cleared manually.

## Why

Discovered by the T-3a gap-fill (docs/tasks/2.1.0/06): the schema-evolution suite's first
reopen scenario crashed deterministically. The structure manager performs its work in an
autonomous executor transaction (`XdStorageStructureManager.checkAndUpdateStructureIfNeed`)
while the caller's outer transaction is open; the journal entries written across that
interplay do not survive a close/open cycle. Beyond the crash itself this blocks every
schema-evolution guarantee (persisted-structure survival, the updater record ledger,
V1→V2 migration) — the structure.update package sits at 13% coverage because its execution
paths cannot be exercised across a reopen.

## Scope

1. **Root cause (found — two layers deep):** the journal records of the structure resource
   embed `java.lang.Class` values (the entity `type`, index field types). On replay the
   YAML object readers resolved them via raw `Class.forName` (system/context loaders only) —
   a class the application loaded through its OWN classloader (an upgraded entity
   generation) was invisible: the helper returned null → the record's identity field
   (`XdStorageClassStructure.type`) became null → `ConcurrentHashMap.put(null, …)` NPE at
   replay → the folder stranded. The stack was invisible in CI logs because hot-path
   NPEs are fast-throw (stackless) — reproduce with `-XX:-OmitStackTraceInFastThrow`.
2. **Fixes (landed):**
   - `XdStorageClassMetadataRegistry.resolveOrThrow/findLoadedClassByName` — TCCL + registry
     (every class any storage of this JVM registered, whatever loader) + caller-loader
     fallback with the original CNFE semantics;
   - the YAML block readers resolve every class token through it (journal replay, structure
     records, data files — one central seam);
   - the journal replay SKIPS an upsert whose identity is still unresolvable, loudly
     (documented recovery: the record drops from the read contour; the next structure pass
     re-establishes it) — a journal can never again strand the folder with a null-key NPE;
   - the codegen shared loader gains a code-source bridge (`BridgingURLClassLoader.addURL`)
     so an application-loaded entity can even materialize.
3. Acceptance: the strand is gone — `structureResourceReplaysAfterStructureUpdateAndReopen`
   (enabled) proves reopen + re-validation after a structure update. The full end-to-end
   evolution scenarios (`reopenSurvivesStructureAndData` etc.) remain `@Disabled` under
   **BUG-B** (docs/tasks/2.1.0/16): entity MATERIALIZATION for non-system-loader
   generations is blocked by the name-based codegen wrapper identity — an engine seam,
   not a journal defect. Full suite green ×2 (final run of the session).

## Out of scope

- BUG B (id-addressability after a structure update — filed separately; partly masked by
  this crash, investigate independently).
- Redesigning the structure manager's autonomous-tx execution (only as far as the fix
  requires).

## Acceptance criteria

- [x] The repro opens the same folder twice with a structure update in between, without
      error (the new enabled acceptance test; the two full-evolution tests are re-annotated
      under BUG-B — materialization, not the journal).
- [x] A journal record with an unresolvable identity is skipped LOUDLY (log.error + the
      documented recovery: the next structure pass re-establishes it) — written into the
      replay path as code + comment.
- [x] Full suite green ×2 (BUILD SUCCESS verdict — final run of the session).

## Verification

```bash
mvn -Dtest=XdStorageSchemaEvolutionTest test          # un-disable the BUG A tests first
mvn clean test && mvn clean test
```

## References

- Discovered in docs/tasks/2.1.0/06 (T-3a) — repro pointers and probe history
- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) (commit/journal protocol context)

Estimate: 2–3 dev-days.
