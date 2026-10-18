# XdStorage — JMH Benchmarks

Recorded: 2026-10-18. Runner: JDK **17.0.15** (OpenJDK, Ubuntu), JMH **1.37**, 24-core / 62 GB Linux box
**under normal developer load** (IDE + JPS daemon + Maven servers were running) — treat the absolute
numbers as ballpark for this class of hardware, not as lab-grade absolutes; the relative gaps between
scenarios are the useful signal.

All three benchmark classes live in `org.flib.xdstorage.performance` (test sources), are driven by the
JMH annotation processor at `mvn test-compile`, and are **not** picked up by surefire (no `Test` suffix).
Each has a `main` entry point.

## How to run

```bash
mvn test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test)"
java -cp "$CP" org.flib.xdstorage.performance.XdStorageSearchEngineBenchmark          # search engine
java -cp "$CP" org.flib.xdstorage.performance.XdStorageResourceCacheBenchmark         # resource cache (in-memory)
java -cp "$CP" org.flib.xdstorage.performance.XdStoragePerformanceBenchmark           # facade write+read flow
```

Append `quick` to the search-engine benchmark for a 1-iteration smoke (~1 min).
All modes are `Throughput` (ops/s), 3 measurement iterations. The engine currently runs in its
**YAML** on-disk format (the standing default in `XdStorage`).

---

## 1. Search engine through the XdStorage facade — `XdStorageSearchEngineBenchmark`

Every operation goes strictly through the facade (`save` / `load(cl, id, tx)` / `update` / `delete` /
`load(cl, indexName, query, tx)`). Dataset: 4 ranges × 101 ships, each with a captain + 2-child crew
(StoreWithParentObject children), preloaded **serially** before workers start; per-trial unique storage
name+directory. Reads at 4 threads; scans and writes single-threaded (commits full-file-rewrite the
resource files — parallel writers measure lock contention, which is the stress tests' domain).

```
Benchmark                                                                Mode  Cnt    Score     Error  Units
XdStorageSearchEngineBenchmark.loadById_indexBaseline                   thrpt    3  480.471 ± 201.287  ops/s
XdStorageSearchEngineBenchmark.saveAndDeleteIndexedShipWithCrew         thrpt    3   38.725 ±  10.675  ops/s
XdStorageSearchEngineBenchmark.searchByOrTwoPrimaries                   thrpt    3  458.752 ± 127.406  ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryEquals                    thrpt    3  468.815 ± 159.740  ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryEqualsWithChildCriterion  thrpt    3  481.610 ± 257.937  ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryEqualsWithFieldCriterion  thrpt    3  485.883 ± 496.181  ops/s
XdStorageSearchEngineBenchmark.searchByScanNotEqualsPrimary             thrpt    3  117.061 ±  39.466  ops/s
XdStorageSearchEngineBenchmark.searchByScanNullPrimaryFieldCriterion    thrpt    3  132.715 ±  28.714  ops/s
XdStorageSearchEngineBenchmark.updateShipRenameSearchKey                thrpt    3   74.847 ±  29.508  ops/s
```

**Reading:**

- **Indexed search costs ≈ a plain id load** (~470 vs ~480 ops/s): the B+tree key lookup + record read
  adds almost nothing over the id-index path on a hit. Secondary field criteria and child criteria
  (evaluated on the already-fetched index record) are free in this configuration.
- **OR of two primaries** (~459) pays roughly one extra tree lookup — near-linear in the number of
  EQUALS branches.
- **Scan paths are ~3.5–4× slower** (~117–133 ops/s): every index record is visited and every surviving
  entity is read. This is the O(dataset) path — keep primary EQUALS criteria in queries when possible.
- **Writes are commit-bound**: rename (1 commit ≈ 13 ms, includes the key-transfer
  keyTree.update + delete/insert) runs ~75 ops/s; save+delete (2 commits, child indexing) ~39 ops/s.
  The dominant cost is the full-file resource rewrite per commit, not the index maintenance itself.
- Under 4-thread read load the engine occasionally logs
  `infra record ... stayed invisible past the wait budget — serving absent` — the documented
  infra-invisibility family; occasional degraded (empty) reads are included in the numbers.

## 2. Resource cache, in-memory — `XdStorageResourceCacheBenchmark`

Isolated `XdStorageResourceCache` over mocks (no disk, no facade): 1000 cached entities.

```
Benchmark                                               Mode  Cnt    Score     Error  Units
XdStorageResourceCacheBenchmark.runMixedReadWriteLoad  thrpt    3  431.528 ± 234.337  ops/s
XdStorageResourceCacheBenchmark.runPurePredicateReads  thrpt    3  258.626 ± 195.426  ops/s
```

Both at 8 threads. `runPurePredicateReads` is a full 1000-record predicate scan per op;
`runMixedReadWriteLoad` interleaves scans with delete/insert mutations plus a full 2PC cycle
per op. The wide error bars reflect the loaded box; re-measure on a quiet machine before
using these as regression baselines.

## 3. Facade transactional flow — `XdStoragePerformanceBenchmark`

Real file storage, per-op: begin tx → `save` (UUID-keyed entity) → `load` by id → commit, 4 threads,
2 forks (10 measurement iterations total).

```
Benchmark                                                            Mode  Cnt    Score     Error  Units
XdStoragePerformanceBenchmark.benchmarkTransactionWriteAndReadFlow  thrpt   10  127.340 ±  24.568  ops/s
```

A full transactional write+read cycle through the facade costs ~8 ms — consistent with the
search benchmark's per-commit cost (~13 ms for the heavier ship graph with children).

---

## Gotchas learned while writing these benchmarks

1. **Preload serially, in the shared `@Setup(Level.Trial)`.** Parallel per-thread preload trips the
   known "рассинхрон структуры" validator flake, and JMH **silently skips** the failed benchmark
   instead of failing the run — always count the rows in the final table.
2. A benchmark that does not reference the `@State(Scope.Thread)` class gets **no thread setup** —
   the scan scenarios originally measured an empty database (21k ops/s of nothing).
3. Query criteria must **match the sampled entity**, otherwise the benchmark measures the no-hit
   filter path (~20× faster, meaningless).
4. Storage name + directory must be **unique per trial** (provider registry returns the old instance
   by name until `shutdown()`; stale on-disk data poisons caches — see R-50).
5. Search-engine entities must be **top-level classes** (wrapper codegen cannot resolve
   `Outer$Inner` binary names in generated source).
