# XdStorage — Benchmarks

## Canonical baseline (the unified estate — BENCH, docs/tasks/2.1.0/13)

**This is the single source of truth for performance numbers.** Every table below is GENERATED
from full-run JMH JSON by `scripts/render-bench-baseline.py` — never hand-typed — and binds to
the archived result set + run manifest in
[`docs/perf/canonical-baseline-2026-10-22/`](perf/canonical-baseline-2026-10-22/) (git rev,
JDK, CPU model, loadavg, config flags inside `manifest.json`).

- **Run:** `bash scripts/run-benchmarks.sh [quick|full|sweep]` — the ONE entry point. It owns
  the conditions protocol: quiet-machine loadavg gate, WARN-console logging with no hot-path
  file I/O (`docs/perf/benchmark-log4j2.xml`, passed explicitly — immune to the suite's config
  reshuffles), YAML format, disclosed config flags, JSON results + manifest.
- **Conditions of the archived baseline:** 2026-10-22, JDK 21.0.7, 24-core box, loadavg 0.91
  (gate-enforced), WARN-console logging (the pre-2026-10-22 numbers ran under TRACE console
  logging — see PERF study §9 — and are **superseded**; kept below as labeled history).
- **Quick-mode budget:** the task's cap is ≤ 15 min for the whole quick suite; the FULL run
  measured **2m21s** wall clock (2026-10-22, the archived baseline). Quick runs are strictly
  cheaper (1 iteration per scenario).
- **Families:** `XdStorageCanonicalBenchmark` (the CORE scenarios this task introduced) and
  `XdStorageSearchEngineBenchmark` (the search family; the FP-1a/FP-1b per-feature sections
  below are ABSORBED into the canonical table). The plain-Java config sweep
  (`performance/sweep/XdStorageConfigSweep`) stays a separate harness by design (PERF §2) —
  run it via `scripts/run-benchmarks.sh sweep`; its numbers are never merged into JMH tables.

#### In context — what these numbers measure, and the field frame (BMCONTEXT, docs/tasks/2.1.0/30)

These are numbers for **embedded, transactional object persistence**: every operation runs inside an
ACID transaction committed across multiple resource files (data + id index + search index), with MVCC
snapshot reads, triggers and typed object graphs — not raw key-value ops over a map. The field frame
from public materials, order-of-magnitude and workload-dependent (origin + date for every figure in
§ Field-frame sources below): RocksDB's own wiki benchmarks measure 10⁴–10⁶ simple ops/s by workload
(~52K *sustained writes*/s in a CPU-bound in-memory test); Chronicle Map's README advertises "millions
of operations per second"; LMDB's sizing guide runs ~336k ops/s with the database memory-resident on 32
cores; MapDB lands 10³–10⁵ ops/s depending on mode. **None of those figures buys what an XdStorage
operation buys**: a single-op rate over a byte map or an off-heap map is not an ACID multi-resource
commit of a typed object graph with secondary/child indexes, in-commit triggers and snapshot isolation.
The honest use of this page is therefore the gaps WITHIN XdStorage's own scenarios (below); the
cross-product numbers frame the order of magnitude only — workload, hardware and op semantics differ,
and some figures are marketing claims (labeled as such). Where the engine deliberately does not compete
today: **sustained >10³ write-transactions/s** — the write family's reading below states the measured
gap and links the fix plan.

#### Reading the CORE scenarios (honest)

- `readOnlyTx` 63.6k ops/s — begin → load(id) → commit, everything cache-resident: the B2
  read-only short path as a permanent, re-runnable scenario (B2's original sweep numbers
  measured N=5000–40000 on the Hash path; this scenario pins the pattern into the suite).
  **In real terms:** ~16 µs per read transaction — request-scoped transactions are a safe
  default, not something to pool or amortize.
- `writeDataHeavy` journal on/off: 370 vs 348 ops/s — the journal is ~neutral on this
  fragment-heavy shape at 1s iterations (the W-3 addendum's index-heavy verdict stands;
  treat the delta as within quick-run noise, re-measure before quoting). **In real
  terms:** ~2.7 ms per data-heavy write transaction — batch small objects, pass explicit
  timeouts, and let batching carry the throughput (next bullet).
- `writeDurable` on/off: 47 vs 367 ops/s — **the fsync costs ~8×** on this shape: the S-2
  number, finally canonically recorded. `durableWrites` is off by default; turn it on when
  the durability requirement outweighs write throughput. **In real terms:** this is the
  price of surviving a power loss (crash safety is free; power-loss safety is not).
- `commitBatch` 10/100/500/1000: 116/35/15/11 tx/s — but in OBJECTS/s the curve RISES
  (1.2k → 3.5k → 7.5k → 11k): batching amortizes the commit; the per-tx fixed cost dominates
  small batches. **In real terms:** the same 11k objects/s that costs 9 seconds one-by-one
  at batch-10 costs ~1 second at batch-1000 — batch size is the single biggest write knob
  an embedder controls.

**The write gap, stated with the plan** (the same limitation the README publishes): writes are
**commit-bound at ~10²–10³ write-tx/s** — indexed save+delete ~731 ops/s, data-heavy ~370 tx/s, and at
the 5×10⁴ design point unbatched pairs collapse to ~7/s (§ Design point below). That is two to three
orders below KV stores, and it is design scope, not accident: data files journal O(delta) per commit
since W-3, while the id-index and search-index paths still rewrite their files whole. **The fix plan is
public**: the write-path analysis, the bottleneck ledger and the index-journaling design round
([docs/WRITE-PATH-DESIGN.md](WRITE-PATH-DESIGN.md); tracked as
[docs/tasks/2.1.0/31](tasks/2.1.0/31-write-path-analysis-design.md), implementation targeted at 2.2.0).

#### Nightly regression watch (D-4, docs/tasks/2.1.0/11)

Every nightly the smoke's quick scores are persisted as workflow artifacts (`jmh-scores-latest`
= last nightly wins; `jmh-scores-<date>` dated copies, 90-day retention) and compared against
the previous nightly's scores. Any scenario **>25% slower** emits a `::warning::` annotation,
and the full delta table lands in the job summary. Semantics: **detection, not gating** — the
alert never fails the run. Quick-mode single-iteration numbers on shared runners are noisy:
a warning means "re-measure on a quiet machine (`scripts/run-benchmarks.sh full`)", exactly
the discipline BENCHMARKS.md has always prescribed. A missing previous artifact (first runs,
pruned history) skips the comparison silently. The gate job is unaffected.

#### Design point — the N=50k boundary measurement (N50K, docs/tasks/2.1.0/20)

The DECISION-GUIDE publishes a design point of 10⁴–10⁵ objects per class; this is its boundary
measurement (probe-mode: plain-Java single run, task 13's conditions protocol via
`scripts/run-benchmarks.sh n50k`; scenario budget 120 s each; fragment size 128; result set
[`docs/perf/design-point-2026-10-23/`](perf/design-point-2026-10-23/)).

| N | heap bytes/object | bulk load obj/s | range window hits |
|---|---|---|---|
| 5,000 | 1,988 | 5,532 | 0 |
| 50,000 | 1,437 | 11,437 | 14,286 |

| Scenario (probe mode, single run) | N=5 000 ops/s | N=50 000 ops/s | status |
|---|---|---|---|
| `indexedEquals` | 42,182.9 | 34,508.8 | ok |
| `pointRead` | 41,526.9 | 67,788.5 | ok |
| `readOnlyTx` | 80,219.5 | 59,117.4 | ok |
| `saveDelete` | 67.5 | 7.0 | TIMEOUT at N=50k (the ceiling finding) |
| `selectiveRange` | 5,218.9 | 110.0 | ok |


**Reading (honest):** reads scale comfortably to 5×10⁴ — point reads, read-only transactions and
indexed EQUALS stay in the same 10⁴–10⁵ ops/s band as at 5×10³. The heap numbers quantify the
ceiling: **~1.4–2.0 KB of heap per object** (≈70–100 MB at 5×10⁴ for this entity) — heap ≈ working
set until W-4, exactly as DECISION-GUIDE § losses states. **The wall is WRITES**: save+delete
collapses **67.5 → 7.0 pairs/s** from 5×10³ to 5×10⁴ (a ~10× write-latency scaling, budget-timeout
recorded) — unbatched write-tx/s at the top of the claimed range is tens, not hundreds; batched
commits remain the way to bulk-write throughput (canonical `commitBatch`: objects/s RISES with
batch). Verdict: **the 10⁴ end of the design point is fully confirmed; 10⁵ is confirmed for reads
and for batched writes; unbatched per-object write tx at 10⁵ is the measured limit.** **In real
terms:** 50k objects of a typical entity cost ~70–100 MB of heap and read at tens of thousands of
ops/s — inside the envelope; the same dataset written one object per transaction is where the wall
is. Probe-mode
caveats: single-run numbers, and the N=5k phase runs first in the same JVM (the N=50k point/tx
rows benefit from extra JIT warmth — treat cross-N deltas within ~1.5× as same-band).

#### Canonical tables (generated; full mode; result set 2026-10-22-185216)

#### `XdStorageCanonicalBenchmark`

| Scenario | Params | ops/s ± error |
|---|---|---|
| `commitBatch` | batchSize=10 | 116 ± 342 |
| `commitBatch` | batchSize=100 | 35 ± 111 |
| `commitBatch` | batchSize=1000 | 11 ± 33 |
| `commitBatch` | batchSize=500 | 15 ± 47 |
| `readOnlyTx` | — | 63,574 ± 29,042 |
| `writeDataHeavy` | journalWrites=false | 348 ± 980 |
| `writeDataHeavy` | journalWrites=true | 370 ± 1,100 |
| `writeDurable` | durableWrites=false | 367 ± 1,148 |
| `writeDurable` | durableWrites=true | 47 ± 62 |

#### `XdStorageSearchEngineBenchmark`

| Scenario | Params | ops/s ± error |
|---|---|---|
| `loadById_indexBaseline` | — | 215,095 ± 34,159 |
| `saveAndDeleteIndexedShipWithCrew` | — | 731 ± 160 |
| `searchByOrTwoPrimaries` | — | 183,305 ± 32,016 |
| `searchByPrimaryBetweenPagedLimit50` | — | 15,539 ± 5,289 |
| `searchByPrimaryBetweenSelective` | — | 23,974 ± 10,520 |
| `searchByPrimaryBetweenWide` | — | 11,351 ± 6,391 |
| `searchByPrimaryEquals` | — | 228,154 ± 106,985 |
| `searchByPrimaryEqualsWithChildCriterion` | — | 223,649 ± 52,541 |
| `searchByPrimaryEqualsWithFieldCriterion` | — | 220,661 ± 52,125 |
| `searchByPrimaryGT` | — | 15,054 ± 7,031 |
| `searchByPrimaryIn` | — | 22,758 ± 8,185 |
| `searchByScanNotEqualsPrimary` | — | 1,563 ± 366 |
| `searchByScanNullPrimaryFieldCriterion` | — | 15,537 ± 10,123 |
| `updateShipRenameSearchKey` | — | 1,858 ± 399 |

**In real terms, by scenario group (the canonical search family):**

- **Point lookups** (`loadById_indexBaseline` 215k, `searchByPrimaryEquals` 228k): ~4–5 µs/op —
  request-scoped keyed access is effectively free, and a secondary/child criterion on the fetched
  record (`…WithFieldCriterion` 221k, `…WithChildCriterion` 224k) adds ~0%: index the fields you
  filter on and the filter rides along at no extra cost.
- **OR branches** (`searchByOrTwoPrimaries` 183k): near-linear — each extra `EQUALS` branch is one
  more tree lookup.
- **Selective ranges** (`searchByPrimaryBetweenSelective` 24k, `searchByPrimaryIn` 22.8k,
  `searchByPrimaryGT` 15k) and **paged ranges** (`…PagedLimit50` 15.5k): thousands of filtered pages
  per second; on an ascending single-criterion walk the limit is pushed down (the walk stops at the
  page end — ~25% ahead of the unbounded same-window range). Wide windows (`…BetweenWide` 11.4k)
  and lower-bound-only ranges degrade toward the walk's fetch cost by design.
- **Scan paths** (`searchByScanNotEqualsPrimary` 1.6k, `searchByScanNullPrimaryFieldCriterion` 15.5k):
  both are O(dataset) on the index walk — every index record is visited. The 10× gap between them is
  the matched-record fetch, not the walk: `NOT_EQUALS "@@none@@"` matches essentially every ship and
  fetches the whole dataset, while the null-primary query scans the same index but its
  `tonnage EQUALS 7` filter (tonnage = `i % 50` in the dataset) matches ~2 of each band's 100 ships
  and fetches almost nothing. Reporting-tier operations either way — fine for dashboards, wrong for
  request paths.
- **Writes through the indexed facade** (`saveAndDeleteIndexedShipWithCrew` 731,
  `updateShipRenameSearchKey` 1.9k): ~0.5–1.4 ms per write transaction including the 2PC over data +
  id index + search index — commit-bound; the gap and the fix plan are stated above.

#### Field-frame sources (the public figures cited in § In context)

Vendor/third-party published, workload-dependent, quoted for order-of-magnitude framing only — not
like-for-like comparisons (different op semantics, hardware, configurations; marketing claims labeled
as such):

- **RocksDB** — the project's own wiki, *In Memory Workload Performance Benchmarks*
  (github.com/facebook/rocksdb/wiki, page revision dated 2022-11): ~52K *sustained writes*/s in a
  CPU-bound in-memory workload; Get-path workloads run in the millions of ops/s. Band: 10⁴–10⁶ simple
  ops/s by workload and hardware.
- **Chronicle Map** — official README (github.com/OpenHFT/Chronicle-Map, accessed 2026-10): "Millions
  of operations per second, with low and stable microsecond latencies for reads and writes" (vendor
  claim); the vendor's product page adds "up to 30 million updates/sec on a 16-core server"
  (chronicle.software, marketing figure, undated).
- **LMDB** — Symas sizing guide (symas.com, accessed 2026-10): ~336k ops/s with the database resident
  in RAM on a 32-core box; the official benchmark suite is published at lmdb.tech/bench; the
  lmdbjava benchmark set (github.com/lmdbjava/benchmarks, run 2016-06) found LMDB the random-read
  leader among Java-accessible stores.
- **MapDB** — official benchmarks page (mapdb.org/benchmarks, undated six-map comparison); the same
  lmdbjava set (2016-06) measured MapDB ~1.5× slower than LMDB on random gets; community figures put
  disk-backed write throughput around 10⁴ ops/s (Stack Overflow, 2014, a single anecdote — the
  weakest of the four; treat MapDB as 10³–10⁵ ops/s by mode).

---

## Historical sections (pre-canonical conditions — kept as labeled history)

> The sections below predate the unified estate: two harness methodologies, two JDKs
> (17.0.15 here vs 21.0.7 canonical), developer-load machines, and — for the 2026-10-18
> tables — TRACE console logging on the hot path (PERF §9). Their RELATIVE scenario gaps
> remain useful; their ABSOLUTE numbers are superseded by the canonical baseline above.

# XdStorage — JMH Benchmarks (historical, recorded 2026-10-18)

Recorded: 2026-10-18. Runner: JDK **17.0.15** (OpenJDK, Ubuntu), JMH **1.37**, 24-core / 62 GB Linux box
**under normal developer load** (IDE + JPS daemon + Maven servers were running) — treat the absolute
numbers as ballpark for this class of hardware, not as lab-grade absolutes; the relative gaps between
scenarios are the useful signal.

All three benchmark classes live in `io.github.xdstorage.performance` (test sources), are driven by the
JMH annotation processor at `mvn test-compile`, and are **not** picked up by surefire (no `Test` suffix).
Each has a `main` entry point.

## How to run (historical — the canonical entry point is scripts/run-benchmarks.sh)

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
**wide** range degrade toward scan cost by design — documented in the guide (docs/GUIDE.md) § Search engine; the walk
starts at the first leaf because the engine deliberately avoids vertical descents for
duplicate-bearing trees (the R-46 invariant). Quick-mode rows: 9 → 13 (the nightly row assertion is a
lower bound and stays valid).

### FP-1b: paged range — LIMIT pushed into the walk (added 2026-10-22)

One scenario joined: `searchByPrimaryBetweenPagedLimit50` — the SAME wide window as
`searchByPrimaryBetweenWide` with `.limit(50)`. The limit is pushed down into the ordered walk
(`XdStorageBTree.WalkTerminated`): the walk stops the moment the page is complete, fetching 50
objects instead of 100. Quick mode (single iteration, same box as the FP-1a rows):

```
XdStorageSearchEngineBenchmark.searchByPrimaryBetweenPagedLimit50   thrpt  15719 ops/s
XdStorageSearchEngineBenchmark.searchByPrimaryBetweenWide           thrpt  12588 ops/s   (same window, unbounded)
```

**Reading (honest):** ~25% ahead of the unbounded same-window range — the pagination win grows with
the page/window ratio and is bounded by the per-object fetch cost (the walk itself is a minor share).
DESC pages do NOT get this win: the leaf chain is singly linked, so DESC must collect the full match
set before it can slice from the tail (documented in the guide, docs/GUIDE.md § Search engine). Quick-mode rows: 13 → 14
(the nightly row assertion is a lower bound and stays valid).

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
