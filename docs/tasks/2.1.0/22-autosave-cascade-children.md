---
title: "[2.1.0][AUTOSAVE] @XdStorageAutoSave: cascade-save independent children on parent save/update"
labels: [2.1.0-train, P1, engine, feature]
status: done
---

## Summary

A new field-level annotation, `@XdStorageAutoSave`, on a parent's child-object field: when
the parent is saved or updated, the engine **cascade-upserts the annotated children** that
live under independent policies (`StoreAsClassObjects`, `StoreAsSingleObject`) — in the same
transaction, with triggers, observers, id generation and rollback semantics identical to
explicit saves.

```java
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class Order {
    @XdStorageObjectId private String id;
    @XdStorageAutoSave private List<OrderLine> lines;   // OrderLine: StoreAsClassObjects too
    @XdStorageAutoSave private OrderDelivery delivery;  // single reference works as well
}
```

Today the user must save every independent child by hand — `storage.save(order, tx)` persists
**only the order** (verified: `XdStorage.save(object, tx)` dispatches to the object's own
index/search resources and nothing else, `XdStorage.java:252-283`). The annotation makes the
relationship declarative.

## Why

The project's pitch — *"the annotated class is the schema"* (DECISION-GUIDE.md) — currently
stops at relationships: parents of independent-policy children force manual save loops, and a
forgotten child save is silent partial persistence. Cascade-save is the ORM-grade ergonomics
the domain-model promise implies, it is JPA-familiar (`@OneToMany(cascade)` on the parent's
field — the same placement), and it is **safe for this train**: no persisted-format change,
no new crash semantics — it is orchestration over the existing per-object save path inside
one 2PC transaction.

## Ground truth (verified in code, 2026-10-23)

- The facade `save`/`update` handles only the object itself — no cascade anywhere; no
  annotation name collision (`annotations/` has no save/cascade annotation).
- The parent's file already stores child-object fields **inline** (full nested serialization
  in `XdStorageDefaultObjectsWriter.writeObject`) unless the field is
  `@XdStorageParentObject` (written as a reference). The child class's policy decides where
  the child's authoritative resource lives; AutoSave upserts that resource — the inline copy
  rides along exactly as it does today.
- Children with a **generated id** are inserted through the existing deferred-id path; the
  same instance in the parent's collection gets its id backfilled (BUG-C's lesson — id
  assignment timing — is the spike question below).

## Design

### V1 semantics (decided)

| Aspect | Decision |
|---|---|
| Placement | **Field level on the parent** (relationship-specific, mirrors JPA cascade) |
| Trigger | Parent `save` (insert) **and** `update` — both cascade |
| Field types v1 | `Collection<Child>` and a single child reference. Arrays/Maps: later |
| Child operation | **Upsert**: child id `null` → insert (generated id backfills the same instance); id set → `has(id)` ? update : insert |
| Transitivity | A child's own `@XdStorageAutoSave` fields cascade further; a per-operation **identity visited-set** makes cycles safe (A→B→A: the second visit is a skip) |
| Transaction | The caller's transaction — 2PC atomicity, child triggers/observers/id-generation and rollback are inherited for free (the cascade IS a sequence of ordinary saves) |
| Rollback | Automatic (same tx): a failed parent commit leaves no half-cascaded children — pinned by a test |
| Dedup | The same child instance reached via two annotated fields is saved once per operation (the visited-set) |
| Re-save cost | v1 re-upserts annotated children on **every** parent save — idempotent, but write-amplifying for wide collections; documented; dirty-checking (version/timestamp) is a future optimization |
| Removed children | **NOT deleted** — a child removed from the collection keeps its independent resource. Pinned loudly by a test; cascade-delete/orphan-removal is deliberately out (data-loss surface, needs its own design round) |
| Embedded children | An annotated field whose element class is `StoreWithParentObject` is a **no-op + one-time metadata WARN** (the parent's write already embeds it). A WARN, not an exception — uniform annotation styles over mixed models must not break (the R-22 loud-scanner precedent) |
| Stale-copy semantics | AutoSave persists what the field **holds** — the transaction's view. No merging of foreign changes; MVCC governs conflicts exactly as for explicit saves. Documented, not hidden |

### Rejected (recorded so they are not re-proposed)

- Cascade-delete / orphan-removal — the most dangerous cascade form; later, with its own
  design round and loud defaults.
- Annotation on the child class ("auto-save me when any parent saves") — relationships
  belong to the parent's field; class-level placement makes cascade direction ambiguous
  with multiple parents.
- `Map` fields and arrays in v1 — collection/reference covers the 90% case.

### Mandatory mini-spike before code (time-boxed, the two open questions)

1. **Hook point**: the facade dispatch (`XdStorage.save/update`, after the parent's own
   mutation is registered, before commit). The cache-mutator layer is per-record — the wrong
   layer. The cascade must re-enter the **public** save/update internals (the
   `incrementActiveThreads`/alive-check plumbing), not bypass the guards. Confirm recursion
   shape and that no re-entrancy guard in the facade fights the cascade.
2. **Deferred-id interplay**: parent with a DB-generated id + a child holding a parent
   back-reference (`@XdStorageParentObject` — written as a reference). Verify in a spike
   whether the child, cascaded in the same tx, captures the parent's id (the observer
   registry may already solve it) or whether the id is only assigned at write-out (the
   BUG-C history). If the latter: **v1 scope = parent-holds-children direction only**
   (children without parent back-references), loudly documented — do not half-support the
   back-reference case.

### Implementation sketch

- `@XdStorageAutoSave` (RUNTIME retention, `FIELD` target, no attributes in v1 — keep the
  door closed until orphan-removal is actually designed).
- Metadata: the reflection field scanner collects annotated fields **including inherited
  ones** (the `getIdFieldName` hierarchy precedent), element class from the generic
  declaration (collection) or the field type (reference).
- Cascade engine in the facade layer (~100–150 lines): resolve fields → per child:
  visited-set → policy check → upsert → recurse.

## Acceptance criteria

- [x] Contract suite `XdStorageAutoSaveCascadeTest` (10 tests): insert-cascade with
      generated-id backfill into the SAME instance; update-cascade (modify child → parent
      update → child persisted); add-child via parent update; **removed child stays
      persisted** (the loud pin); cycle A↔B terminates; embedded-element field → save
      succeeds with the metadata WARN (one-time per class); child triggers fire on cascaded
      children; rollback atomicity (a failing cascade child aborts the save — nothing
      persists after rollback); reopen → children loadable by id; MAP values cascade (keys
      never candidates; id-field-less values skipped); ARRAY fields cascade their elements;
      transitive grandchildren persist.
- [x] README § Cascade save + EMBEDDING.md (§ Cascade save and transactions) carry the
      annotation's contract — including the removed-children and stale-copy semantics (the
      two surprises).
- [x] Full suite green ×2 (BUILD SUCCESS verdict); no new FLAKY rows.
- [x] `CHANGELOG.md` "Added" entry.

## Implementation notes (what landed, 2026-10-23)

- **Metadata in `XdStorageClassInfo`** (review point 1): `List<XdStorageAutoSaveFieldInfo>`
  (accessor + shape COLLECTION/MAP/ARRAY/REFERENCE) built by the registry from the scanned
  fields — inherited included (the scanner walks superclasses).
- **Engine out of the facade** (review point 2): `utils/XdStorageAutoSaveCascadeEngine` owns
  the visited/depth ThreadLocals and the walk; `XdStorage` keeps only the tail hook
  (`cascadeAfterPersist(object, tx, services, this)`) — its inline save/update dispatches
  restored verbatim; the transient private `persistObject`/`updateObject` copies removed
  (dead once the dispatch returned inline).
- **Walk-semantics fix** (found by the transitive test): the visited mark must happen AT the
  walk — pre-marking children before their cascade made the child's own fields never walk
  (transitivity silently broken; caught because `XdStorageAutoSaveCascadeTest`
  `transitiveGrandchildCascades` failed on first run).
- **Shapes**: COLLECTION, single REFERENCE, **ARRAY** (elements are the candidates), and
  **MAP** (VALUES whose class carries an id-field; keys are never candidates; id-field-less
  values WARN-skipped once per class). Maps/arrays were "out of scope" in the original
  design — pulled in by review.
- v1 stays per the design: no cascade-delete, no dirty checking, no annotation options.
  Deferred-id spike outcome: the back-reference case is NOT v1-supported — children with
  `@XdStorageParentObject` back-references to independent parents should use the embedded
  shape; documented in README § Cascade save.

## Out of scope

Cascade-delete/orphan-removal; dirty-checking before re-save; Maps/arrays; annotation
options of any kind in v1; a JMH scenario (the cascade is N ordinary saves — the per-save
numbers already exist; add one only if it is trivial).

Estimate: 2–4 dev-days (spike included). Risk: LOW-MEDIUM — no persisted-format change.

## References

- `XdStorage.save/update` dispatch (the hook point), `XdStorageDefaultObjectsWriter.writeObject`
  (the inline-child fact), `annotations/` (naming)
- [README.md](../../README.md) § Defining the data model (to extend)
- [EMBEDDING.md](../../EMBEDDING.md) § Transactions and threads (the contract being extended)
- BUG-C history (docs/tasks/2.1.0/17) — why the deferred-id spike is mandatory
