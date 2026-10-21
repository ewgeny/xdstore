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
  `markRollbackOnly`.
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
- **Pure Java** — bytecode stays Java 8 compatible; the development/test runtime is JDK 17; logging via log4j2.

## Requirements and installation

- Java 8 or newer to run the library (the bytecode target is 1.8; wrapper codegen needs a JDK at
  runtime — see [docs/EMBEDDING.md](docs/EMBEDDING.md)); JDK 17 is used for development and tests.
- Runtime dependencies: `org.apache.logging.log4j:log4j-core` (2.23.1) and
  `org.apache.commons:commons-lang3` (3.8.1).

Until 2.0.0 is tagged, install from source; after the release the artifact is on Maven Central:

```bash
mvn install
```

then add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.xdstorage</groupId>
    <artifactId>xdstorage</artifactId>
    <version>2.0.0</version>
</dependency>
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
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 4)  // t = B+tree minimum degree
public class Ship { ... }
```

`XdStorageIndexType.Hash` (default) gives O(1) point lookups; `BTree` adds ordered keys and range-friendly
structure. `t` is the B+tree minimum degree (default 500).

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
- `idGeneratorType` — `DATABASE_GENERATOR` (default, storage assigns the id), `UUID_GENERATOR`
  (the client assigns the id itself), or `CUSTOM_GENERATOR` with `idGeneratorClass`.

### Parent references and lazy children

- `@XdStorageParentObject` on a field marks the back-reference to the owning object; it is populated when
  the child is loaded.
- `@XdStorageLoadByGetMethod` on a child-collection field makes the generated getter **lazy**: the first
  call loads the collection from the storage (`storage.load(getter result, tx)`) and caches it. The first
  access must happen inside a transaction; if that load fails, the transaction is marked rollback-only and
  a `XdStorageRuntimeException` is thrown.

## Opening and closing a storage

```java
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
- `fragmentSize` — number of objects per fragment file for the `StoreAsClassObjects` policy.
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
  index value (the first `indexFieldNames` entry). `EQUALS` uses the B+tree directly; `NOT_EQUALS` (or a
  `null` primary) performs a full index scan.
- **Field criterion** — `new XdStorageCriterion("tonnage", operator, value)` filters fetched records by a
  secondary indexed field.
- **Child criterion** — `new XdStorageChildCriterion(childFieldName, ChildClass.class, "fieldName",
  operator, value)` matches objects by values in their indexed child objects.
- **Operators** — `IXdStorageCriterionOperator.{EQUALS, NOT_EQUALS, CONTAINS, IS_NULL, IS_NOT_NULL}`
  (`CONTAINS` is a substring match on strings, case-sensitive); `IXdStorageChildCriterionOperator.CONTAINS`
  for child criteria.
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

JMH benchmarks (JDK 17, recorded 2026-10-18, full numbers and methodology in
[docs/BENCHMARKS.md](docs/BENCHMARKS.md)):

- Indexed search through the facade costs ≈ a plain id load (~470 vs ~480 ops/s at 4 reader threads on the
  reference box) — B+tree lookup + record read adds almost nothing.
- `OR` of two primaries is near-linear in the number of branches (~460 ops/s).
- Scan paths (`NOT_EQUALS` / no primary) are ~3.5–4× slower (~120–130 ops/s) — O(dataset).
- Writes are commit-bound (each commit rewrites the touched resource files): rename ~75 ops/s,
  save+delete ~39 ops/s; the facade save+load+commit flow ~127 ops/s.

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
