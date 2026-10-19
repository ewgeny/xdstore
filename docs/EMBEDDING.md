# Embedding XdStorage — the contract

Operational contract for applications embedding XdStorage 2.0. Everything here is behavior of the
current code, stated so embedding surprises are design-time, not production-time.

## Lifecycle

- **Open** via `XdStorageProvider.newOrGetFileStorage(name, folder[, format][, XdStorageConfig])`.
  The provider keeps a per-JVM registry keyed by **name**: a repeat call with the same name returns
  the *existing* instance and **ignores the folder argument** (a WARN is logged). Call `shutdown()`
  to unregister before reopening under changed parameters.
- **The folder is exclusively locked** (`.xdstorage.lock`, OS file lock + same-JVM registry) for the
  storage's lifetime. A second opener — another process or another instance in this JVM — gets
  `XdStorageFolderLock.XdStorageInUseException` (unchecked; the provider API predates the lock and
  cannot add a checked exception).
- **`shutdown()` is idempotent.** After it every storage call throws
  `XdStorageRuntimeException("storage ... is shut down")`. In-flight transactions are NOT waited for —
  quiesce your workers before shutting down.
- **Threads:** a fixed pool (default 15) of non-daemon `xdstorage-worker-N` threads per storage.
  A forgotten `shutdown()` therefore blocks JVM exit. Options:
  `XdStorageConfig.builder().daemonWorkerThreads(true)` (workers can't block exit) and/or
  `.registerShutdownHook(true)` (best-effort close on JVM exit — does NOT substitute for daemon
  workers: hooks run only after the last non-daemon thread exits).
- **Timeouts are enforced lazily:** a timed-out transaction is actually rolled back when a lock wait,
  record latch, or the rollback-only sweeper trips — not by an independent expiry timer.

## Transactions and threads

- **Transactions are bound to the owning thread** (`getCurrentTransaction()` by thread identity).
  Every CRUD call without an explicit transaction uses the current thread's binding and throws
  `"method must be executed in transaction"` when absent.
- **Thread pools inherit bindings:** a pool thread that abandons its transaction leaves it attached —
  the next task on that thread sees it as "current". Always commit/rollback in a `finally`, or pass
  transactions explicitly.
- **One transaction may be used from many threads** (parallel streams): the engine counts active
  threads per transaction and defers rollback until they all leave.
- A failed CRUD operation throws but does NOT auto-rollback the transaction; wrapper-generated
  accessors and search errors do mark it rollback-only. Commit of a rollback-only transaction throws.
- Nested transactions: `beginTransaction(parentTx, timeout)`; a nested rollback does not poison the
  parent (downward-only rollback-only cascade).

## JVM-wide static state (per-JVM, not per-storage)

- `XdStorageProvider` registry (name → instance).
- Wrapper-codegen caches (`XdStorageClassMetadataRegistry`, proxy compilation engine): per-class
  metadata computed once, never evicted — warm them once, share across storages.
- **`COMMIT_SEQUENCE` is a single global counter** shared by all storages in the JVM — required for
  consistent cross-storage MVCC ordering.
- The deferred-id observer registry is JVM-global and keyed by class.
- The generated-class loader is a static `URLClassLoader` over `./gencode` — **created relative to
  the process working directory at first use and pinned for the JVM's life**. App-server redeploys
  that expect classloader isolation will not work with engine-generated wrappers.

## Codegen requirements

- The engine generates wrapper classes **at runtime** via the system Java compiler: a **JDK is
  required at runtime — a JRE silently degrades to dummy wrappers** (broken equals/hashCode; an
  ERROR is logged once, the failure is cached for the JVM lifetime, there is no retry).
- The write target `./gencode/` must be **writable**; a read-only working directory has the same
  permanent-dummy-wrapper failure mode.
- Entity classes must be **top-level** (nested classes cannot be compiled on JDK 9+).

## Files and durability

- The storage folder is engine-owned: `structure/*.yaml`, `data/<Class>/*.yaml` (YAML default) plus
  `.xdstorage.lock`. Do not edit files while the storage is open.
- Default write-out is process-crash-safe (atomic rename), NOT power-loss-safe. Enable
  `XdStorageConfig.builder().durableWrites(true)` for fsync-per-commit power-loss safety.
- Resource files carry an EOF sentinel; a truncated file fails loudly at open. Pre-sentinel legacy
  storages: open once with `acceptMissingEofSentinel(true)` and let the first commit rewrite the files.
- Stale `*.tmp` files from a previous crash are swept at open.

## Observability

- Logging: log4j2. The jar bundles a default config (rolling file `xdstorage.log`, 3×50 MB, append;
  `-Dxdstorage.log.file=` to relocate). Host applications should load their own log4j2 configuration
  first to keep control.
- Metrics: `storage.getMetrics()` — per-storage counters (transactions begun/committed/rolled back,
  rollback-only marks, timeout sweeps, resource write-outs with nanos, active gauge). JVM-wide
  anomaly counters (lock-acquisition timeouts, 1s+ lock waits, find-retry budget exhaustions):
  `XdStorageMetrics.engineEvents()`. JMX/Micrometer export is left to the embedder — read the
  snapshot periodically.

## Configuration recap

`XdStorageConfig` (all defaults = pre-config behavior): `durableWrites=false`,
`acceptMissingEofSentinel=false`, `daemonWorkerThreads=false`, `registerShutdownHook=false`,
`workerPoolSize=15`, `defaultTransactionTimeoutMillis=3000`, `isolationLevel=SNAPSHOT`.
