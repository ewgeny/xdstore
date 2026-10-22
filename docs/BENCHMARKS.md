# XdStorage — JMH Benchmarks

Recorded: 2026-10-18. Runner: JDK **17.0.15** (OpenJDK, Ubuntu), JMH **1.37**, 24-core / 62 GB Linux box
**under normal developer load** (IDE + JPS daemon + Maven servers were running) — treat the absolute
numbers as ballpark for this class of hardware, not as lab-grade absolutes; the relative gaps between
scenarios are the useful signal.

All three benchmark classes live in `io.github.xdstorage.performance` (test sources), are driven by the
JMH annotation processor at `mvn test-compile`, and are **not** picked up by surefire (no `Test` suffix).
Each has a `main` entry point.

## How to run

```bash
mvn test-compile
CP="target/test-classes:target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=test)"
java -cp "$CP" io.github.xdstorage.performance.XdStorageSearchEngineBenchmark          # search engine
java -cp "$CP" io.github.xdstorage.performance.XdStorageResourceCacheBenchmark         # resource cache (in-memory)
java -cp "$CP" io.github.xdstorage.performance.XdStoragePerformanceBenchmark           # facade write+read flow
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

### FP-1a: range operators (added 2026-10-22)

Four scenarios joined the benchmark: `searchByPrimaryBetweenSelective` (lexicographic window over a
small prefix of the thread's names — the walk terminates past the upper bound),
`searchByPrimaryBetweenWide` (upper bound at the end of the band — approaches scan cost),
`searchByPrimaryGT` (lower bound only — no early termination by design) and `searchByPrimaryIn`
(one membership-filtered walk). Quick mode (single iteration — ballpark, same box as the table above):

```
XdStorageSearchEngineBenchmark.searchByPrimaryBetweenSelective   thrpt  22907 ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryBetweenWide        thrpt  12095 ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryGT                 thrpt  15538 ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryIn                 thrpt  23911 ops/s
XdStorageSearchEngineBenchmark.searchByScanNotEqualsPrimary      thrpt   1506 ops/s   (the scan these replace)
```

**Reading (honest):** a selective upper-bounded range runs ~15× the scan it replaces — the walk
terminates past the upper bound and fetches only the matching objects, while the scan visits and
reads every record. `IN` behaves like a selective walk. A **lower-bound-only** range (`GT`/`GTE`) and a
**wide** range degrade toward scan cost by design — documented in README § Search engine; the walk
starts at the first leaf because the engine deliberately avoids vertical descents for
duplicate-bearing trees (the R-46 invariant). Quick-mode rows: 9 → 13 (the nightly row assertion is a
lower bound and stays valid).

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

## Addendum: journalized defaults (2026-10-19, W-3)

`XdStorageConfig.journalWrites` defaults ON since W-3: data-class commits append to a
per-resource journal (`O(delta)`) instead of full-file rewrites; id-index/search-index/B+Tree
infrastructure still full-rewrite BY DESIGN SCOPE. Quick-mode re-run of the search benchmark
on the same box, same day (single-iteration quick mode — treat as ballpark):

```
loadById_indexBaseline                   ~443   (was 480 full-mode / 493 quick; quick-mode noise band)
saveAndDeleteIndexedShipWithCrew         ~38    (was 38.7)  — UNCHANGED
searchByOrTwoPrimaries                   ~431   (was 459)
searchByPrimaryEquals                    ~466   (was 469)
searchByScanNotEqualsPrimary             ~98    (was 117)
updateShipRenameSearchKey                ~73    (was 74.8)  — UNCHANGED
```

**Honest reading:** the write scenarios did NOT get faster on this benchmark — it is
index-heavy (every save/delete rewrites the id-index + search-index files, which the journal
deliberately does not cover). The journal's measured wins are for data-file-heavy write
patterns (fragment rewrites dominate there) and crash semantics (per-append atomicity + EOF
sentinel instead of torn multi-file rewrites). The ≥10× write acceptance target requires
journaling the index paths — the deferred W-4 work (see WRITE-PATH-DESIGN.md).

## Addendum: read-only transaction commit path (2026-10-22, B2)

The PERF study §5.3 (2026-10-19 edition) measured a begin → load → commit read-only cycle at
~25 ms/op (Hash, N=5000), scaling with the resource cache size, and named it the top
read-heavy optimization candidate (task B2). **That figure no longer reproduces at the 2.1.0
HEAD — measured before making any code change for B2** (same harness,
`performance/sweep/SweepFindTxVerify`, same box, serial, `resourceWriteOuts` delta watched):

```
hash  N=5000   0.69 ms/op   writeOuts=0
hash  N=20000  0.63 ms/op   writeOuts=0
hash  N=40000  0.74 ms/op   writeOuts=0     <- flat: no cache-size scaling
btree N=5000   0.70 ms/op   writeOuts=0
```

**Honest reading (the W-3 precedent):** the ≤1 ms/op acceptance target is already met — not by
a dedicated B2 patch, but as a side effect of the commit-path rework the campaign already
shipped (R-45 commit atomicity + R-46 `XdStorageResourceCache` collaborator split; the
per-resource `hasChanges` filter in `XdStorageTransaction.commitInternal` routes no-change
resources around the 2PC iteration). B2's shipped deliverable is therefore the *contract*:
a dedicated test suite (`XdStorageReadOnlyCommitPathTest` — committed-count +1 / write-outs +0,
snapshot isolation under a concurrent writer, rollback-only still throws, a parent still waits
for an open nested read-only tx, concurrent readers commit clean) plus this evidence. The old
N=40000 scaling check guards against the cost silently returning with dataset growth.

JMH facade flow (write+read scenario, quick mode, same box): 132.9 ± 27.6 ops/s vs the
documented 127.3 ± 24.6 — same band, no write-path regression.

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
