# XdStorage 2.0

**XdStorage** is an embedded, file-backed **object storage for Java**. Plain JavaBeans/POJOs annotated with a
small set of annotations are persisted into a folder of files with **ACID transactions, MVCC snapshot reads,
B+tree/hash indexes, a criteria-based search engine, triggers, and pluggable on-disk formats** (YAML, XML).
There is no server, no native code and no external dependency to operate — the whole database lives next to
your application.

```
Your JVM ──► IXdFileStorage facade ──► transactions (MVCC, 2PC commit)
                                   ├─► primary id index (Hash / B+tree)
                                   ├─► search engine (criteria queries, child indexes)
                                   ├─► resource cache + triggers + observers
                                   └─► YAML / XML file fragments on disk
```

## Table of contents

1. [Features at a glance](#features-at-a-glance)
2. [Requirements and installation](#requirements-and-installation)
3. [Quick start](#quick-start)
4. [Defining the data model](#defining-the-data-model)
5. [Opening and closing a storage](#opening-and-closing-a-storage)
6. [Transactions](#transactions)
7. [CRUD operations](#crud-operations)
8. [Search engine](#search-engine)
9. [Triggers](#triggers)
10. [Identifier generation and observers](#identifier-generation-and-observers)
11. [Streaming reads and predicate filtering](#streaming-reads-and-predicate-filtering)
12. [Serialization formats](#serialization-formats)
13. [Structure updates (schema migration)](#structure-updates-schema-migration)
14. [Concurrency model and internals](#concurrency-model-and-internals)
15. [Performance](#performance)
16. [Current limitations](#current-limitations)
17. [Building and testing](#building-and-testing)
18. [Documentation and project layout](#documentation-and-project-layout)

## Features at a glance

- **Embedded file storage** — one storage is one folder; the provider keeps a named singleton per storage.
- **POJO data model** — ordinary JavaBeans with getters/setters; collections, arrays and maps are supported
  as field types; nested object graphs are stored according to a per-class policy.
- **ACID transactions** — explicit `beginTransaction / commit / rollback`, atomic multi-resource 2PC commit,
  automatic rollback of a failed commit (the database returns to a valid state), transaction timeouts,
  `markRollbackOnly`; read-only transactions commit through a cheap release path (no 2PC record
  iteration, zero file write-outs).
- **MVCC** — writers produce new versions; readers work against a consistent snapshot
  (`SNAPSHOT` / `READ_COMMITTED` read isolation).
- **Three storage policies** — embed an object inside its parent file, one file per object, or a shared
  per-class file with fragmentation.
- **Indexes** — a primary id index (hash or B+tree, configurable) plus declarative secondary search indexes,
  including indexes over child objects.
- **Search engine** — criteria queries (`EQUALS`, `NOT_EQUALS`, `CONTAINS`, `IS_NULL`, `IS_NOT_NULL`),
  AND/OR combinations, negation, child-object criteria; indexed lookups cost about the same as an id load.
- **Triggers** — `Insert` / `Update` / `Delete` callbacks executed within the committing transaction.
- **Identifier generation** — persistent per-class counters for `Long`/`Integer`, UUID for `String`,
  fully pluggable custom generators; deferred-id observers get notified when an id is assigned.
- **Pluggable serialization** — YAML (default), XML; the reader/writer pair is an SPI
  (`IXdStorageIOFactory`) so you can implement your own format. (The 1.x `FilesFormat.JSON`
  constant was removed in 2.0.0 — it wrote XML content; see
  [docs/EMBEDDING.md](docs/EMBEDDING.md) § Compatibility.)
- **Streaming reads** — iterate a class without materializing the whole collection (`watch`), or filter with
  a predicate (`load(Class, IXdStoragePredicate)`).
- **Structure updates** — registered updaters migrate persisted structures (e.g. rebuild a search index after
  its annotation changed) via `executeStructureUpdate`.
- **Pure Java** — bytecode stays Java 8 compatible; the development/test runtime is JDK 17; logging via
  SLF4J — the API jar is the only logging artifact (no backend, no configuration ships in the library).

## Requirements and installation

- Java 8 or newer to run the library (the bytecode target is 1.8; wrapper codegen needs a JDK at
  runtime — see [docs/EMBEDDING.md](docs/EMBEDDING.md)); JDK 17 is used for development and tests.
- Runtime dependencies: `org.slf4j:slf4j-api` (2.0.20) and `org.apache.commons:commons-lang3`
  (3.8.1). The library ships **no logging backend and no log configuration** — bind the backend of
  your choice (log4j2, Logback, ...) to see log output; without one you get a single SLF4J no-op
  notice.

The released 2.0.0 artifact is on Maven Central; add it to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.xdstorage</groupId>
    <artifactId>xdstorage</artifactId>
    <version>2.0.0</version>
</dependency>
```

To run against the current 2.1.0 train, install from source:

```bash
mvn install
```

## Quick start

```java
import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.IXdStorageTransaction;
import io.github.xdstorage.XdStorageProvider;
import io.github.xdstorage.serialization.FilesFormat;

// 1. Open (or get) a named storage in a folder. YAML is the default format.
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage("mydb", "./data/mydb", 250, FilesFormat.YAML);

// 2. All operations run inside a transaction.
Ship ship = new Ship();
ship.setName("Alpha");
IXdStorageTransaction tx = storage.beginTransaction(15000L);   // 15 s timeout
try {
    ship.setTonnage(100);
    ship.setTonnage(100);
    ship.addCrewMember(new CrewMember("Pilot"));

    storage.save(ship, tx);            // insert; the id is generated and set on the object
    tx.commit();
} catch (Throwable t) {
    tx.rollback();
    throw t;
}

// 3. Read it back by id, or find it with the search engine.
IXdStorageTransaction readTx = storage.beginTransaction();
try {
    Ship loaded = storage.load(Ship.class, ship.getId(), readTx);

    Collection<Ship> found = storage.load(Ship.class, "ship_idx",
            new XdStorageSearchQuery(new XdStoragePrimaryCriterion(
                    IXdStorageCriterionOperator.EQUALS, "Alpha")), readTx);

    readTx.commit();
} catch (Throwable t) {
    readTx.rollback();
    throw t;
}

// 4. Release the storage (unregisters it from the provider and stops its threads).
storage.shutdown();
```

## Defining the data model

Entities are ordinary JavaBeans. XdStorage reads the class structure once, caches the metadata, and generates
runtime wrapper classes per entity (see [Concurrency model](#concurrency-model-and-internals)).

### Rules for every entity

- The class **must be a top-level class** (not a nested/inner/anonymous class) — the runtime code generator
  cannot compile wrappers for nested classes on JDK 9+.
- The identifier field is marked with `@XdStorageObjectId` and **must have a getter and a setter**.
- The id type must implement `equals()`/`hashCode()`. For custom id classes it must also provide a
  constructor accepting a single `java.lang.String`. Built-in generators support `Long`, `Integer` and
  `String` ids (see [Identifier generation](#identifier-generation-and-observers)).
- Every persisted field needs a getter and a setter.
- Child objects referenced by fields follow their **own** class policy.

### Storage policies — `@XdStorageObjectPolicy`

| Policy | Meaning |
|---|---|
| `StoreWithParentObject` (default) | The object is written inside the file of its parent object (embedded type) — it lives and dies with the parent. |
| `StoreAsSingleObject` | One object — one file on disk. |
| `StoreAsClassObjects` | All objects of the class are packed into shared class-file **fragments** (the `fragmentSize` parameter caps how many objects go into one fragment before a new fragment file is created). |

```java
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class Ship { ... }

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class CrewMember { ... }
```

Only `StoreAsClassObjects` and `StoreAsSingleObject` classes are top-level (independently addressable)
stored objects; `StoreWithParentObject` children live and die with their parent.

### Primary id index — `@XdStorageObjectIdIndexType`

Chooses the physical index type for the id index of the class:

```java
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 64)  // t = B+tree minimum degree
public class Ship { ... }
```

`XdStorageIndexType.Hash` (default) gives O(1) point lookups and the best cold reads after restart;
`BTree` adds ordered keys, native range walks over the id index, and ~2× bulk-insert throughput.
`t` is the B+tree minimum degree — default 64 (the configuration-study optimum; stay within 32–128,
never below 8 — see [Performance](#performance)).

### Search indexes — `@XdStorageObjectSearchIndex`

Declares a named secondary index over one or more fields. The **first** field of `indexFieldNames` is the
*primary* index value (backed by a B+tree — the fast path for `EQUALS` queries); the remaining fields are
secondary values filterable after the lookup. `childrenIndexes` attaches indexes of child objects
(`childFieldName` is the field on this class, `childClassIndexName` is the index name declared on the child
class).

```java
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(
        indexName = "ship_idx",
        indexFieldNames = {"name", "tonnage"},          // "name" is the primary index value
        t = 8,
        childrenIndexes = {
                @XdStorageObjectChildSearchIndex(childFieldName = "captain", childClassIndexName = "crew_idx"),
                @XdStorageObjectChildSearchIndex(childFieldName = "crew",    childClassIndexName = "crew_idx")
        })
public class Ship {

    @XdStorageObjectId
    private Long id;

    private String name;
    private int tonnage;

    private CrewMember captain;             // single child object  — indexed
    private List<CrewMember> crew;          // collection of children — indexed
    // arrays and Map values work the same way

    public void addCrewMember(CrewMember member) {
        if (crew == null) crew = new ArrayList<>();
        crew.add(member);
    }

    // getters and setters for every field ...
}

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
@XdStorageObjectSearchIndex(indexName = "crew_idx", indexFieldNames = {"rank"}, t = 8)
public class CrewMember {

    @XdStorageObjectId
    private Long id;

    private String rank;

    @XdStorageParentObject
    private Ship parent;      // back-reference, filled automatically on load

    public CrewMember() {}
    public CrewMember(String rank) { this.rank = rank; }

    // getters and setters ...
}
```

### Field properties — `@XdStorageObjectFieldProperties`

Configures per-field storage parameters and id generation:

```java
@XdStorageObjectId
@XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.DATABASE_GENERATOR)
private Long id;

@XdStorageObjectFieldProperties(length = 1000)   // declared field length
private String name;
```

- `length` — declared length of the field (default 255).
- `idGeneratorType` — who assigns the id when it is still null at save time:

  | `idGeneratorType` | Long / Integer id | String id |
  |---|---|---|
  | `DATABASE_GENERATOR` *(default)* | engine Hi-Lo batches (per class, persisted counter; a reopen burns the unused batch remainder — at most batch-size−1 ids) | engine UUID |
  | `UUID_GENERATOR` | **loud validation error** (a numeric id cannot carry a UUID) | engine UUID |
  | `CUSTOM_GENERATOR` + `idGeneratorClass` | the SPI class generates (constructed with the services locator when such a constructor exists, else no-arg) | same |

  An app-assigned (non-null) id is **never overwritten** in any mode. Generated Long/Integer
  ids are addressable by id after a reopen; UUID ids likewise (BUG-D contract,
  docs/tasks/2.1.0/14 — before the fix `UUID_GENERATOR` never generated and the client had
  to assign). A custom generator SPI implements `IXdStorageIdGenerator` — its `generate`
  receives the entity class and the running transaction.

### Parent references and lazy children

- `@XdStorageParentObject` on a field marks the back-reference to the owning object; it is populated when
  the child is loaded.
- `@XdStorageLoadByGetMethod` on a child-collection field makes the generated getter **lazy**: the first
  call loads the collection from the storage (`storage.load(getter result, tx)`) and caches it. The first
  access must happen inside a transaction; if that load fails, the transaction is marked rollback-only and
  a `XdStorageRuntimeException` is thrown.

## Opening and closing a storage

```java
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder);          // default fragment size (128), YAML
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder, config);  // default fragment size (128)
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder, fragmentSize);
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder, fragmentSize, FilesFormat.YAML);
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder, fragmentSize, XdStorageConfig.defaults());
IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder, fragmentSize, FilesFormat.YAML, config);
```

- `name` — logical storage name. The provider keeps a **registry**: calling `newOrGetFileStorage` again with
  the same name returns the *existing* instance (until `shutdown()`), even if the folder was deleted and
  recreated.
- `folder` — the directory holding all storage files (created on demand). The folder is **exclusively
  locked** for the storage's lifetime (`.xdstorage.lock`): a second process — or a second instance in the
  same JVM — on the same folder fails loudly with `XdStorageFolderLock.XdStorageInUseException` instead of
  corrupting data. The lock is released by `shutdown()`.
- `fragmentSize` — number of objects per fragment file for the `StoreAsClassObjects` policy. The
  overloads without this parameter use the engine default `128` (the configuration-study optimum —
  see [Performance](#performance)).
- `FilesFormat` — `YAML` (default) or `XML`. (The 1.x `JSON` constant was removed in 2.0.0; a
  1.x folder written through it holds XML content in `.json`-named files — migration recipe in
  [docs/EMBEDDING.md](docs/EMBEDDING.md) § Compatibility.)
- `XdStorageConfig` — open-time knobs (all defaults reproduce the pre-config behavior):
  `durableWrites` (fsync file+directory per commit — power-loss safe at fsync cost),
  `acceptMissingEofSentinel` (legacy migration: open pre-sentinel storages instead of failing loudly on
  truncated files), `journalWrites` (ON by default since W-3 — data-class commits are journaled;
  turn off for the classic full-file rewrite), `txMaterializedObjectsCap` (per-transaction read
  budget, 0 = unlimited), `daemonWorkerThreads` (worker pool cannot block JVM exit), `registerShutdownHook`
  (best-effort `shutdown()` on JVM exit), `workerPoolSize`, `defaultTransactionTimeoutMillis`,
  `isolationLevel` (`SNAPSHOT` default, `READ_COMMITTED`).

`shutdown()` **must be called** when you are done: it stops the storage worker threads, releases the folder
lock and removes the instance from the provider registry, allowing the name/folder to be reopened cleanly
afterwards. It is idempotent; after it, every storage call throws a loud `XdStorageRuntimeException`
("storage ... is shut down") instead of failing deep inside the engine.

The `IXdFileStorage` interface extends `IXdStorage`, so every storage also exposes `getName()`,
`getTransactionManager()`, and trigger registration.

## Transactions

XdStorage is transactional by design: **every operation runs inside a transaction**. Each facade method has
two forms — with an explicit transaction, and without one (the latter resolves the transaction bound to the
current thread and throws `XdStorageException("method must be executed in transaction")` if there is none).

```java
IXdStorageTransaction tx = storage.beginTransaction();        // default timeout: 3 s
IXdStorageTransaction tx = storage.beginTransaction(15000L);  // explicit timeout in ms
try {
    // ... storage.save / load / update / delete / search with tx ...
    tx.commit();
} catch (Throwable t) {
    tx.rollback();      // or tx.markRollbackOnly() to poison it for the caller
    throw t;
}
```

Semantics:

- A transaction **times out** after the given number of milliseconds; using a dead transaction throws.
- `commit()` runs a two-phase commit across every resource the transaction touched (object files, id
  indexes, search indexes). If the commit fails at any phase, the changes are rolled back and the database
  returns to a valid state.
- `markRollbackOnly()` flags the transaction so a later `commit()` is refused.
- `beginTransaction(IXdStorageTransaction parent)` derives a transaction from a parent one — this is the
  correct way for **triggers** to run their own storage work while remaining inside the committing
  transaction.
- `storage.getTransactionManager()` exposes the underlying
  `IXdStorageTransactionManager` (`getCurrentTransaction()`, `getTransaction(id)`, `isTransactionAlive(tx)`,
  ...).

## CRUD operations

All operations are available in a `(...)` and a `(..., IXdStorageTransaction)` form, and in single-object and
`Collection` forms. Examples below use the explicit-transaction form.

### save — insert a new object

```java
Ship ship = new Ship();
ship.setName("Alpha");
storage.save(ship, tx);
// ship.getId() is now set (DATABASE_GENERATOR ids are assigned during save)
```

`save` is an **insert**: saving an object whose id already exists in the storage (in a later transaction)
throws `XdStorageException`. Use `update` to modify a saved object. Bulk form: `save(Collection<?>, tx)`.

### has — existence check by id

```java
boolean exists = storage.has(Ship.class, ship.getId(), tx);
```

### load — read objects

```java
Ship ship = storage.load(Ship.class, id, tx);            // by id, or null if absent
Collection<Ship> all = storage.load(Ship.class, tx);     // every object of the class

// load into already-existing instances (their @XdStorageObjectId values are used as keys):
storage.load(existingShip, tx);
storage.load(collectionOfShips, tx);

// filter with a predicate (full scan over the class):
Collection<Ship> big = storage.load(Ship.class, s -> s.getTonnage() > 100, tx);
```

Loaded graphs are reconstructed according to the policies: embedded children and `@XdStorageParentObject`
back-references are filled in; `@XdStorageLoadByGetMethod` collections are resolved lazily.

### update — modify a saved object

```java
ship.setTonnage(200);
storage.update(ship, tx);
```

`update` writes a new version of the object. If the *primary search-index value* changed (e.g. the ship was
renamed), the search-engine key is transferred — the object becomes findable under the new value and the old
one stops matching, without duplicating the record. Bulk form: `update(Collection<?>, tx)`.

### delete — remove an object

```java
storage.delete(ship, tx);
```

The object, its reference entry and its search-index records are removed in the same transaction; embedded
children are removed together with the parent. Bulk form: `delete(Collection<?>, tx)`.

## Search engine

The search engine maintains a B+tree of `(primary index value → record)` plus per-field and per-child
value sets for each `@XdStorageObjectSearchIndex`. Queries are built from criteria and executed with
`load(Class, indexName, query, tx)`; an unknown `indexName` throws `XdStorageException`.

```java
XdStorageSearchQuery query = new XdStorageSearchQuery(
        primaryCriterion,        // first branch (see below); may be null for a pure scan
        criterion);              // additional filter(s) for this branch; may be null
query.or(primaryCriterion2, criterion2);   // additional OR branches
```

- **Primary criterion** — `new XdStoragePrimaryCriterion(operator, value)` evaluates against the primary
  index value (the first `indexFieldNames` entry). `EQUALS` uses the B+tree directly; the range operators
  `GT` / `GTE` / `LT` / `LTE` / `BETWEEN` / `IN` walk the ordered index with early termination; `NOT_EQUALS`
  (or a `null` primary) performs a full index scan.
- **Field criterion** — `new XdStorageCriterion("tonnage", operator, value)` filters fetched records by a
  secondary indexed field.
- **Child criterion** — `new XdStorageChildCriterion(childFieldName, ChildClass.class, "fieldName",
  operator, value)` matches objects by values in their indexed child objects.
- **Operators** — `IXdStorageCriterionOperator.{EQUALS, NOT_EQUALS, CONTAINS, IS_NULL, IS_NOT_NULL,
  GT, GTE, LT, LTE, BETWEEN, IN}` (`CONTAINS` is a substring match on strings, case-sensitive);
  `IXdStorageChildCriterionOperator.CONTAINS` for child criteria.
- **Range operators (FP-1a) — comparison contract:**
  - `BETWEEN` takes an inclusive `new XdStorageRange(from, to)`; both bounds are inclusive
    (pinned choice); `from > to` matches nothing; a single-point range matches exactly that value.
  - `IN` takes a `Collection` (or array); duplicates are harmless; an empty collection matches nothing;
    membership is `equals`-based (a foreign type simply never matches — no error).
  - **Nulls never satisfy a range operator** (`IS_NULL` / `IS_NOT_NULL` remain the null tests).
  - Comparisons are per field type: numbers numerically, strings lexicographically, `Comparable`
    contract otherwise. A comparable-type mismatch (e.g. a `String` bound on a numeric field) and a
    `null` bound fail **loudly** with `XdStorageRuntimeException` — never silently-empty.
- **Plan / fallback matrix** — the operator is API-wide; the available index decides the plan:

  | Primary criterion | Plan |
  |---|---|
  | `EQUALS` | B+tree point lookup |
  | `GT` / `GTE` / `LT` / `LTE` / `BETWEEN` / `IN` | ordered bounded walk of the index B+tree (early termination past the upper bound; `IN` = one membership-filtered walk; `GT`/`GTE` have no upper bound and cost like a scan) |
  | anything else (`NOT_EQUALS`, `CONTAINS`, `IS_NULL`, …) | full index scan (unchanged) |

  All plans return the same results for the same filter (verified by the boundary suite); results keep
  today's ordering contract. The walk starts at the first leaf — the engine deliberately avoids vertical
  descents for duplicate-bearing trees — so the win grows with selectivity of the **upper** bound.
- **Result shaping (FP-1b) — `ORDER BY`, `LIMIT`/`OFFSET`, `COUNT`:**
  - `query.orderByPrimary(descending)` orders by the primary index value. On the ordered-walk path
    this is the native key order (free); on the scan path it is an explicit collect-and-sort within
    the transaction snapshot (O(matches), stated honestly). Secondary-field ordering is not
    supported — the search index orders by key trees, not arbitrary fields. Nulls are the greatest
    value (last in ASC, first in DESC); the sort is stable for equal keys. `DESC` walks the full
    range (the leaf chain is singly linked, there is no reverse walk) — the page is exact, the walk
    cost is not reduced.
  - `query.limit(n)` / `query.offset(n)` paginate after ordering. Negative arguments fail loudly at
    query construction; `LIMIT 0` is a legal empty page; an offset past the end yields an empty
    page. On a single-criterion ascending range walk the limit is **pushed down**: the walk stops
    as soon as the page is complete (measured: `LIMIT 50` over a 100-key window runs ~25% ahead of
    the unbounded same-window range — [BENCHMARKS.md](docs/BENCHMARKS.md) § FP-1b). Without
    `ORDER BY`, pages follow the engine's evaluation order — deterministic within a snapshot, not
    an API contract. `OFFSET` re-walks the skipped prefix; keyset/cursor pagination is the filed
    follow-up if that cost matters at scale.
  - `storage.count(cl, indexName, query[, tx])` — record-level count of the snapshot-visible
    matches, without building the result collection; it ignores `ORDER BY`/`LIMIT`/`OFFSET` (SQL
    `COUNT` semantics). Every counted record passes the same snapshot-visibility read as a `load`,
    so `count(q)` is exactly `load(q).size()` on the same snapshot.
  - Snapshot semantics: a page read inside a transaction reflects that transaction's snapshot —
    concurrent inserts stay invisible; the index tree is live, so a concurrent deletion leaves the
    walk too (the documented live-index exposure, identical to FP-1a's result sets).
- **Custom predicates** — `XdStorageComplexCriterion` (`and(...)` / `or(...)`) and `XdStorageCriterionNot`
  let you compose arbitrary AND/OR/NOT filters over a record.

Examples:

```java
// exact match on the primary value
Collection<Ship> found = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, "Beta"), null), tx);

// primary + secondary field filter
Collection<Ship> byTonnage = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, "Gamma"),
                new XdStorageCriterion("tonnage", IXdStorageCriterionOperator.EQUALS, 301)), tx);

// OR of two primaries
XdStorageSearchQuery orQuery = new XdStorageSearchQuery(
        new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, "Alpha"), null);
orQuery.or(new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, "Gamma"), null);
Collection<Ship> two = storage.load(Ship.class, "ship_idx", orQuery, tx);

// range on the primary value (inclusive bounds), with a secondary-field filter
Collection<Ship> byRange = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.BETWEEN,
                        new XdStorageRange(200, 400)),          // inclusive [200, 400]
                new XdStorageCriterion("tonnage", IXdStorageCriterionOperator.GTE, 300)), tx);

// membership
Collection<Ship> inList = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.IN,
                        Arrays.asList("Alpha", "Gamma", "Delta")), null), tx);

// child criterion: ships of Alpha whose crew contains a member ranked "Pilot"
Collection<Ship> byCrew = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.EQUALS, "Alpha"),
                new XdStorageChildCriterion("crew", CrewMember.class, "rank",
                        IXdStorageChildCriterionOperator.CONTAINS, "Pilot")), tx);

// full scan: NOT_EQUALS on the primary value (all ships not named "Alpha")
Collection<Ship> notAlpha = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.NOT_EQUALS, "Alpha"), null), tx);

// result shaping (FP-1b): ordered page + count
Collection<Ship> page3 = storage.load(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.BETWEEN,
                        new XdStorageRange(200, 400)),
                new XdStorageCriterion("tonnage", IXdStorageCriterionOperator.GTE, 300))
                .orderByPrimary(false)   // ORDER BY primary ASC (true = DESC)
                .offset(100)             // skip the first 100 matches
                .limit(50), tx);         // then take 50 — the walk stops when the page is full
long howMany = storage.count(Ship.class, "ship_idx",
        new XdStorageSearchQuery(
                new XdStoragePrimaryCriterion(IXdStorageCriterionOperator.BETWEEN,
                        new XdStorageRange(200, 400)), null), tx);   // ignores ORDER BY/LIMIT/OFFSET
```

Cost model (see [Benchmarks](#performance)): an indexed `EQUALS` search costs roughly the same as a plain
id load; `OR` branches add one tree lookup each; scan paths (`NOT_EQUALS`, no primary criterion) visit the
whole index and are ~3–4× slower on typical datasets — keep an `EQUALS` primary in queries when you can.

## Triggers

Triggers are callbacks executed **inside the committing transaction**, after the corresponding operation has
been applied to the resource cache. Register them on the storage before use:

```java
public class ShipInsertTrigger extends XdStorageAbstractInsertTrigger<Ship> {

    @Override
    public XdStorageObjectOperationType getType() { return XdStorageObjectOperationType.Insert; }

    @Override
    public Class<Ship> getClazz() { return Ship.class; }

    @Override
    protected void perform(Ship object, IXdStorageTransaction tx) {
        // called with the new object on every committed insert of Ship
        // for your own storage work here, derive a transaction: storage.beginTransaction(tx)
    }
}

storage.registerTrigger(new ShipInsertTrigger());
```

- `XdStorageAbstractInsertTrigger<T>` receives the new object; `XdStorageAbstractDeleteTrigger<T>` receives
  the old (deleted) object. For update-style old+new pairs, implement `IXdStorageTrigger<T>` directly and
  return `XdStorageObjectOperationType.Update` from `getType()`.
- The operation types are `Insert`, `Update`, `Delete`.
- A trigger that touches the storage itself **must** begin and finish its own (derived) transaction.

## Identifier generation and observers

The `idGeneratorType` of the `@XdStorageObjectId` field (via `@XdStorageObjectFieldProperties`) controls who
assigns identifiers:

| Type | Behaviour |
|---|---|
| `DATABASE_GENERATOR` (default) | On insert, if the id is `null`, the storage generates it and sets it back on the object: `Long`/`Integer` — persistent per-class counters (they survive restarts); `String` — random UUID. |
| `CUSTOM_GENERATOR` | Storage calls your generator: set `idGeneratorClass = MyGenerator.class` where `MyGenerator implements IXdStorageIdGenerator` (a constructor taking `XdStorageServicesLocator` is supported for DI). |
| `UUID_GENERATOR` | The client assigns the id (e.g. `UUID.randomUUID()`) before `save`; the storage does not touch it. |

```java
public class MonthPrefixGenerator implements IXdStorageIdGenerator {
    @Override
    public Object generate(Class<?> cl, IXdStorage storage, IXdStorageTransaction tx) {
        return "ship-" + System.nanoTime();
    }
}

@XdStorageObjectId
@XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.CUSTOM_GENERATOR,
                                idGeneratorClass = MonthPrefixGenerator.class)
private String id;
```

Because generated ids are written back through generated *observable* wrappers, you can subscribe to id
assignment:

```java
XdStorageAbstractIdObserver observer = new XdStorageAbstractIdObserver() {
    @Override
    public void onNewIdIsSet(IXdStorageIdObservableWrapper wrapper, Object id) {
        System.out.println("assigned id = " + id);
    }
};
observer.observeIfPossible(ship);   // safe no-op for non-wrapper objects; throws on null
```

## Streaming reads and predicate filtering

`watch` streams every object of a class through your callback without materializing the result collection:

```java
storage.watch(Ship.class, ship -> System.out.println(ship.getName()), tx);
```

`IXdStoragePredicate<T>` is a functional interface usable both as a load filter (see
[CRUD](#crud-operations)) and in internal resource scans.

## Serialization formats

The on-disk format is chosen when the storage is created:

```java
XdStorageProvider.newOrGetFileStorage("db", "./data/db", 250, FilesFormat.YAML);  // default
XdStorageProvider.newOrGetFileStorage("db", "./data/db", 250, FilesFormat.XML);
```

The format is implemented as a pair of streaming reader/writer factories:

- `IXdStorageIOFactory` — creates a reader/writer pair per resource;
- `IXdStorageObjectsReader` / `IXdStorageObjectsWriter` — the actual (de)serialization SPI.

Built-in implementations: a hand-rolled block-based **YAML** writer/reader (`serialization.yaml.*`, the
default format, with commit-journal support) and the **XML** factory (`XdStorageDefaultIOFactory`).
Every storage folder records its on-disk format generation in `.xdstorage.meta`
(`formatVersion` — see [docs/EMBEDDING.md](docs/EMBEDDING.md) § Compatibility). To add a custom
format, implement the factory/writer/reader triple and wire it into `XdStorageServicesLocator`.

The physical naming of resource files is handled by `IXdStorageResourceNamingService` (default:
`XdStorageResourceNamingService`, which derives file names from the storage folder, format and resource ids).

## Structure updates (schema migration)

When the declared structure of a class changes (for example a search index got new fields), persisted
structures need to be rebuilt. XdStorage does this through pluggable updaters:

```java
// register a custom updater and run all pending updates for the class
storage.registerStructureUpdater(new MyStructureUpdater());
List<IXdStorageStructureUpdater> executed = storage.executeStructureUpdate(MyEntity.class);
```

- An updater implements `IXdStorageStructureUpdater<T>` (`getDataClass()`, `getUpdateName()`,
  `execute(resourcesManager, tx)`, result/error accessors); `XdStorageAbstractStructureUpdater` is a base
  class.
- The built-in `XdStorageSearchIndexStructureUpdater` re-builds a search index record set when the
  structure manager detects that the persisted index no longer matches the class annotations; it is
  registered automatically during the structure check.

## Concurrency model and internals

- **Everything is transactional and multi-versioned.** Writers stage changes in a per-transaction workspace
  and install them during the two-phase commit; readers see a consistent snapshot (`SNAPSHOT`) or the latest
  committed state (`READ_COMMITTED`), never a half-committed one.
- **Runtime code generation.** For each entity class the storage generates wrapper classes at first use:
  an *observable* wrapper (write-through + deferred-id observation) and an *unmodifiable* wrapper (the
  snapshot view handed to readers). This is why entities must be top-level classes with full getters/setters.
- **Worker threads.** Each storage instance owns a fixed thread pool (default 15, `workerPoolSize`) of named
  `xdstorage-worker-N` threads used for parallel resource I/O during load/commit, the timeout sweeper and
  triggers. Threads are non-daemon by default (pre-config behavior); with
  `XdStorageConfig.builder().daemonWorkerThreads(true)` a forgotten `shutdown()` cannot block JVM exit.
  Always call `shutdown()` when finished — it is idempotent and optionally registered as a JVM shutdown
  hook (`registerShutdownHook(true)`).
- **Resource layout.** A storage folder contains fragment files per class resource, reference files for
  `StoreAsSingleObject` classes, id-index files (hash/B+tree persisted as B+tree nodes), search-index
  trees, and structure metadata. Fragment size caps the number of objects per `StoreAsClassObjects`
  fragment.
- **Rollback safety.** Any failure during commit triggers rollback of the already-processed phases
  (including index changes), returning the database to the last valid state.

## Performance

Two measurement sources back this section: JMH facade benchmarks
([docs/BENCHMARKS.md](docs/BENCHMARKS.md)) and the configuration-tuning study
([docs/PERFORMANCE-RECOMMENDATIONS.md](docs/PERFORMANCE-RECOMMENDATIONS.md)), which swept the
structural knobs — B+tree fill factor `t`, fragment size, id-index type, search-index `t` — through the
full data lifecycle (bulk insert → warm reads → update → cold restart → delete).

Measured cost model (JMH, JDK 17, reference box; full numbers and methodology in
[docs/BENCHMARKS.md](docs/BENCHMARKS.md)):

- Indexed search through the facade costs ≈ a plain id load (~470 vs ~480 ops/s at 4 reader threads) —
  the B+tree lookup + record read adds almost nothing; secondary-field and child criteria are evaluated
  on the already-fetched record and add ~0%.
- `OR` of two primaries is near-linear in the number of branches (~460 ops/s).
- Scan paths (`NOT_EQUALS` / no primary criterion) are ~3.5–4× slower (~120–130 ops/s) — O(dataset),
  and the gap grows linearly with the data.
- Range primaries walk the ordered index with early termination: a selective upper-bounded range
  (tight `BETWEEN`, `IN`) runs ~15× the scan it replaces; lower-bound-only (`GT`/`GTE`) and wide
  ranges degrade toward scan cost by design.
- Writes are commit-bound (each commit rewrites the touched resource files): rename ~75 ops/s,
  save+delete ~39 ops/s; the facade save+load+commit flow ~127 ops/s.
- A read-only transaction (begin → load → commit) costs <1 ms/op, flat in dataset size
  (0.63–0.74 ms/op from N=5000 to N=40000, zero file write-outs) — high-rate per-op read
  transactions are safe.

### Tuning recommendations

Compiled from [docs/PERFORMANCE-RECOMMENDATIONS.md](docs/PERFORMANCE-RECOMMENDATIONS.md) (measured at
N=5000 YAML entities; the relative gaps are the signal — re-run the sweep at production volume before
locking choices, recipe in its § 9). The first three recommendations are **already the engine
defaults**:

- **Fragment size: 128–512 objects per fragment** — engine default `128` (the `newOrGetFileStorage`
  overloads without an explicit fragment size). Aim for fragment files of tens to low hundreds of KB.
  **Above ~1 MB is a red flag**: every commit rewrites each touched fragment file whole (a
  one-fragment-per-class layout with 4.5 MB files made the update pass +85% slower). **Below ~10 KB is
  a red flag too**: 626 tiny files cost ~30% insert throughput and doubled the cold scan.
- **B+tree fill factor `t`: 32–128, default 64** in both annotations. Warm lookups are `t`-insensitive —
  the penalty of a bad `t` is structural. At N=5000, `t=2` produced ~10 000 node files (12×
  index-size amplification), 129 ms commits, a 19× slower delete-all and 387 µs cold lookups, versus
  3.7 ms commits and 87 µs cold lookups at `t=64` (cold reads pay ~35 µs per tree level — deep trees
  tax every uncached lookup after a restart). **Never ship `t` below 8.**
- **Search index `t = 64`** (same annotation default): indexed saves ~5× faster than the old `t=500`
  default and the first cold query lands ~19 ms earlier; warm query latency is `t`-insensitive.
- **Id index type — choose by workload** (`@XdStorageObjectIdIndexType`):
  - **Hash** (default): best cold-read latency after restart (52 vs 87 µs/op — one fragment load vs a
    tree descent), fewest files (12 vs 101 at N=5000), simplest operations; equality-only.
  - **B+Tree**: insert-heavy pipelines (~2.1× bulk insert) and anything that needs ordered keys or
    id-range traversal.
- **Query shapes:** keep the primary criterion selective — `EQUALS`, or a range with a tight upper
  bound. Treat `NOT_EQUALS`, primary-`CONTAINS` and primary-less queries as reporting-tier operations
  (full index scan). Index child fields you actually filter on: each indexed child collection adds
  per-save tree maintenance.
- **Transactions:** the 3 s default timeout is tight for batch writes — pass explicit timeouts for
  bulk batches (the study's harness uses 120 s for 500-object batches); raising the default to 10–30 s
  is reasonable for batch-oriented services.
- **Durability:** commits are always process-crash-safe (atomic rename, journaled appends); enable
  `durableWrites(true)` when the machine can lose power — it adds one fsync per written file per
  commit, modest at the recommended fragment sizes.
- **Journaling is the default write path** (`journalWrites` on by default): data-class commits append
  O(delta) journal records instead of rewriting whole files, so its wins are on data-file-heavy write
  patterns. Id-index/search-index/B+tree infrastructure still rewrites its files per commit by design
  scope — the remaining write-latency wall for index-heavy workloads
  ([docs/WRITE-PATH-DESIGN.md](docs/WRITE-PATH-DESIGN.md)).
- **Entity layout:** `StoreAsClassObjects` (fragmented) for "many small objects of one class" domains;
  `StoreAsSingleObject` only for genuinely large top-level graphs — one file per object is what makes
  infrastructure file counts explode at small object sizes.
- **Monitor** `storage.getMetrics()` and `XdStorageMetrics.engineEvents()`: sustained growth of
  `resourceWriteOutNanos` per write-out ⇒ fragments too big or the disk saturating;
  `timeoutSweptRollbacks` / `rollbackOnlyMarks` > 0 ⇒ transaction timeouts too tight; any
  `lockAcquisitionTimeouts` / `lockWaitsOver1s` / `findRetryBudgetExhaustions` ⇒ sustained write
  contention. Healthy-range thresholds per counter: the study's § 10.

Run the benchmarks yourself:

```bash
mvn test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test)"
java -cp "$CP" io.github.xdstorage.performance.XdStorageSearchEngineBenchmark
java -cp "$CP" io.github.xdstorage.performance.XdStorageResourceCacheBenchmark
java -cp "$CP" io.github.xdstorage.performance.XdStoragePerformanceBenchmark
```

## Current limitations

Worth knowing before production use (details and remediation plan in
[docs/SRE-OPTIMIZATION-PLAN.md](docs/SRE-OPTIMIZATION-PLAN.md)):

- **One process per storage folder (enforced).** The folder is locked exclusively for the storage's
  lifetime; a second opener fails loudly instead of corrupting data.
- **Power safety is opt-in.** By default commits are process-crash-safe but not power-loss-safe
  (no `fsync`); pass `XdStorageConfig.builder().durableWrites(true)` for `fsync`-per-commit durability.
  A truncated resource file fails loudly at open (EOF sentinel); legacy pre-sentinel storages open once
  with `acceptMissingEofSentinel(true)`.
- **Data-class commits are journaled by default** (W-3): each commit appends to
  `<basefile>.journal.yaml` (O(delta)) with size-threshold checkpointing; id-index/search-index/B+Tree
  infrastructure still rewrites its files per commit (design scope — the remaining write-latency wall for
  index-heavy workloads, [docs/WRITE-PATH-DESIGN.md](docs/WRITE-PATH-DESIGN.md)). Set
  `journalWrites(false)` for the classic full-rewrite path.
- `StoreWithParentObject` classes cannot be stored top-level: only `StoreAsClassObjects` /
  `StoreAsSingleObject` classes can be passed to `save`/`load`.
- Nested entity classes are not supported (runtime codegen limitation, see above).

## Building and testing

```bash
mvn test           # full suite (~700 tests, unit + integration + stress), single surefire fork with -Xmx2g
mvn install        # build the jar and install it locally
mvn -Prelease -Dgpg.skip=true verify   # release-shaped build: binary + sources + javadoc jars
```

Notes:

- The build pins `--release 8` — post-JDK-8 API usage fails compilation even on a JDK 17 toolchain
  (the build itself enforces JDK 17+ / Maven 3.6.3+).
- Test storages are created under `./target/`; stale storage folders from interrupted runs can pollute a
  re-run, so prefer `mvn clean test` when tests misbehave.
- The full suite is run in a single fork; rare stress flakes under extreme machine load are documented
  in [docs/FLAKY.md](docs/FLAKY.md).
- Releases are cut by pushing a `v*` tag (`.github/workflows/release.yml`); the procedure is
  [docs/RELEASE-PLAN.md](docs/RELEASE-PLAN.md).

## Documentation and project layout

- [docs/BENCHMARKS.md](docs/BENCHMARKS.md) — JMH benchmark methodology and recorded results.
- [docs/PERFORMANCE-RECOMMENDATIONS.md](docs/PERFORMANCE-RECOMMENDATIONS.md) — configuration-tuning
  study (B+tree `t`, fragment size, Hash vs B+Tree id index, search-index `t`): measured data, the
  applied engine defaults, and monitoring thresholds.
- [docs/OPTIMIZATION-PLAN.md](docs/OPTIMIZATION-PLAN.md) — performance/architecture optimization backlog.
- [docs/SRE-OPTIMIZATION-PLAN.md](docs/SRE-OPTIMIZATION-PLAN.md) — operational hardening plan
  (durability, locking, metrics).
- [docs/EMBEDDING.md](docs/EMBEDDING.md) — the embedding contract (threads, statics, codegen, lifecycle).
- [docs/RELEASE-PLAN.md](docs/RELEASE-PLAN.md) — release engineering: first-release checklist,
  the recurring release procedure, and release-prep recommendations.
- [docs/MASTER-PLAN.md](docs/MASTER-PLAN.md) — the roadmap (merged 5-step plan, frozen items).
- [docs/ARCHITECT-EVALUATION.md](docs/ARCHITECT-EVALUATION.md) — outside-architect review and the
  competitive comparison.
- [docs/FLAKY.md](docs/FLAKY.md) — known flaky test families and quarantine conventions.
- [docs/WRITE-PATH-DESIGN.md](docs/WRITE-PATH-DESIGN.md) — journal-based commit design (the write-path roadmap).

Main packages (`io.github.xdstorage`):

| Package | Contents |
|---|---|
| (root) | `IXdStorage`, `IXdFileStorage`, `XdStorage` facade, `XdStorageProvider`, `XdStoragePolicy`, watcher/predicate interfaces |
| `annotations` | All entity annotations |
| `transaction` | Transaction interface, manager, 2PC commit state |
| `search`, `search.query` | Search engine, indexes, criteria query API |
| `resource`, `resource.cache` | Resource DAOs, naming, MVCC record/workspace cache, commit protocol |
| `index`, `index.hash`, `index.btree` | Hash and B+tree id-index resources |
| `serialization`, `serialization.yaml` | IO factory SPI and the YAML/XML implementations |
| `trigger`, `observing` | Insert/update/delete triggers, deferred-id observers |
| `idgeneration` | Id generator SPI, counters, UUID generator |
| `structure`, `structure.update` | Class/index structure metadata and updaters |
| `code` | Runtime wrapper class generators |
| `factories`, `utils`, `helpers`, `lock`, `btree`, `object`, `exceptions` | Cloning, reflection helpers, read-write lock, B+tree engine, generic object holders, exception hierarchy |
