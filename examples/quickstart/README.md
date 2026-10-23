# XdStorage Quickstart

A runnable, end-to-end sample of [XdStorage](https://github.com/evgeny-korolev/xdstore) —
the embedded, file-backed object store for Java. One test class walks the entire lifecycle:

**open → transaction → save → indexed query (`EQUALS`) → range query (`BETWEEN`, `GTE` +
`ORDER BY DESC` + `LIMIT`) → `count()` → commit → shutdown → reopen from disk → load → shutdown**

The domain model is two plain JavaBeans — [`Customer`](src/main/java/quickstart/Customer.java)
(indexed by country) and [`Account`](src/main/java/quickstart/Account.java) (indexed by
balance) — and the whole story is asserted in
[`QuickstartTest`](src/test/java/quickstart/QuickstartTest.java). That test is the code to
copy: ~130 lines, no configuration, no server.

## Run it

Prerequisites: **JDK 8+** (a JDK, not a JRE — the engine generates wrapper classes at
runtime, see below) and **Maven 3.8+**.

```bash
cd examples/quickstart
mvn test          # compiles the entities + runs the lifecycle test — that's the whole demo
```

Then open the two entity classes and `QuickstartTest` in your IDE and start editing —
every save/query primitive the engine has is exercised there with comments.

## Where the dependency comes from

The pom depends on exactly one production artifact — `io.github.xdstorage:xdstorage`
(currently `2.1.0`; bump `<xdstorage.version>` in the pom):

- **from Maven Central** once the release is published — nothing else to do;
- **from a local build** while the release is in flight: in the *repository root* run
  `mvn install -DskipTests`, then `mvn test` here resolves `2.1.0` from your local `~/.m2`.

## Notes you will hit in real use

- **Storage = one folder.** The test gives every run a unique folder (`@TempDir`) and a
  unique storage name — the provider keeps a per-JVM registry by name, and a folder is
  exclusively locked while open ([EMBEDDING.md](../../docs/EMBEDDING.md) § Lifecycle).
- **Transactions are explicit and thread-bound.** Every CRUD call takes a transaction (or
  uses the current thread's binding); commit/rollback belongs in a `catch`/`finally`.
- **Top-level entities only.** The engine compiles wrapper classes from your beans at
  runtime — nested classes cannot be resolved; a **JRE silently degrades to broken
  wrappers** ([EMBEDDING.md](../../docs/EMBEDDING.md) § Codegen requirements).
- **Logging is yours.** The library logs via SLF4J and ships no backend or config — this
  example therefore prints nothing from the engine. Add any SLF4J backend to see the
  lifecycle `INFO` lines (EMBEDDING.md § Observability).
- **Durability** is process-crash-safe by default (atomic rename); power-loss safety is
  `XdStorageConfig.builder().durableWrites(true)` at ~8× write cost (BENCHMARKS.md).
- **Known defect (BUG-E, [docs/tasks/2.1.0/18](../../docs/tasks/2.1.0/18-bug-search-index-key-types-after-reopen.md)):**
  range/`EQUALS` seeks on a **numeric** search index crash after a storage reopen (the
  persisted keys come back as raw Strings). The quickstart therefore asserts its range
  query before the close/open cycle and re-queries the String-keyed index after it; the
  disabled `XdStorageSearchRangeReopenTest` in the library suite is the fix's acceptance
  test. Writing, reading by id and String-keyed queries across a reopen are unaffected.

## Where to go next

- [README.md](../../README.md) — the full guide (data model, transactions, search engine).
- [docs/DECISION-GUIDE.md](../../docs/DECISION-GUIDE.md) — when XdStorage is the right choice.
- [docs/EMBEDDING.md](../../docs/EMBEDDING.md) — the operational contract.
- [docs/BENCHMARKS.md](../../docs/BENCHMARKS.md) — what the engine costs, measured honestly.
