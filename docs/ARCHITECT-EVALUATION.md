# XdStorage — Architect's Evaluation & Feature Proposal

Perspective: an outside Java architect discovers XdStorage on GitHub while choosing an embedded
store for a JVM application, reads the documentation, compares it against the usual candidates,
and writes down the verdict — including what would have to change for XdStorage to win more of
those evaluations. Compiled 2026-10-20, branch `xdstorage-2.0`.

Sources reviewed: [README.md](../README.md), [EMBEDDING.md](EMBEDDING.md),
[WRITE-PATH-DESIGN.md](WRITE-PATH-DESIGN.md), [BENCHMARKS.md](BENCHMARKS.md),
[PERFORMANCE-RECOMMENDATIONS.md](PERFORMANCE-RECOMMENDATIONS.md),
[MASTER-PLAN.md](MASTER-PLAN.md), [FLAKY.md](FLAKY.md), `pom.xml`, CI workflow.

---

## 1. Executive summary

**Verdict: XdStorage is a genuinely differentiated product occupying a niche nobody else fills —
but today it is a niche product with a deployment story and a throughput ceiling that cap where
it can be recommended.** Fix three things and the addressable audience widens considerably:

1. **The JDK-at-runtime requirement** (runtime `javac` codegen into `./gencode`, silent JRE
   degradation) is the single biggest adoption blocker. It fails containers, app servers, and
   Graal native-image, and it fails *silently*.
2. **The unbounded-heap ceiling** (the resource cache holds the working set; no eviction until
   disk-anchored scans exist — Track B1/W-4) means heap ≈ DB. Fine at 10⁴–10⁵ objects,
   disqualifying beyond that.
3. **The release hygiene gap** — no LICENSE, no CHANGELOG, not on Maven Central, version `2.0`
   instead of `2.0.0`. No architect can put an unlicensed artifact on a dependency list.

The distinctive idea — *plain annotated JavaBeans with object-graph storage policies, a
criteria search engine, triggers, and a human-readable on-disk format (YAML/XML/JSON), all
transactional and pure Java* — is real and well executed. The engineering discipline visible in
the docs (benchmark honesty, flake ledger, design-before-code gates, frozen-items inventory with
thaw conditions) is better than most commercial products. That discipline is the project's
second asset after the feature set; the feature plan below is designed to spend it wisely.

---

## 2. What the documentation says (analysis)

### 2.1 What the product is

One storage = one folder; entities are top-level JavaBeans annotated with `@XdStorageObjectId`,
`@XdStorageObjectPolicy`, `@XdStorageObjectSearchIndex`. Three placement policies (embedded in
parent / one file per object / per-class fragments); a primary id index (Hash or B+tree, `t`
configurable); declarative secondary indexes including child-object indexes; criteria queries
(`EQUALS`, `NOT_EQUALS`, `CONTAINS`, `IS_NULL`, `IS_NOT_NULL`, AND/OR/NOT composition, child
criteria); ACID 2PC transactions with MVCC snapshot/READ_COMMITTED reads; insert/update/delete
triggers running inside the committing transaction; persistent id generators (counter, UUID,
custom SPI); lazy collections and parent back-references; pluggable serialization SPI (YAML
default, XML, JSON); structure-update (schema migration) hooks.

### 2.2 Strengths of the documentation itself

- **Honest benchmark reporting** (BENCHMARKS.md): the W-3 addendum states outright that
  journaling did *not* speed up the index-heavy benchmark instead of spinning the result. Wide
  error bars are flagged; the box load is disclosed. This builds trust.
- **The embedding contract** (EMBEDDING.md) states every operational landmine up front: thread
  binding, static registries, the pinned `./gencode` classloader, lazy timeout enforcement,
  non-daemon threads. This is the document most projects never write.
- **Design-before-code culture**: WRITE-PATH-DESIGN.md records rejected alternatives with
  reasons; MASTER-PLAN.md keeps a frozen-items inventory with explicit thaw conditions so
  nobody re-attempts a proven-dead approach blindly.
- **A quantified cost model** for queries: indexed `EQUALS` ≈ a plain id load; scans ~3.5–4×
  slower; per-commit cost = O(bytes of every touched file).

### 2.3 Gaps in the documentation (from a first-time reader's view)

| Gap | Why it matters |
|---|---|
| No LICENSE / no Maven Central artifact | A dependency decision cannot even be written down. |
| No "when to use / when not to use" page | The reader must reverse-engineer the positioning from benchmarks. |
| No comparison against MapDB/Xodus/RocksDB/EclipseStore | The niche is unique but invisible; nobody searching "Java embedded object database" finds this combination of features described in their vocabulary. |
| No runnable example app / tutorial beyond snippets | Quick-start is good but ends at ~30 lines; a small end-to-end sample (entities + repo + tests) would compress evaluation time to minutes. |
| No page-size/data-size guidance | "How many objects is this designed for?" must be inferred from benchmark N (2500–5000). State it: the design point is ~10⁴–10⁵ objects/class until W-4 ships. |
| No EXPLAIN/plan visibility for queries | The docs *tell* you scans are 4× slower; the engine could *show* you when you wrote one. (→ FP-2) |
| The JRE-degradation failure mode is documented but silent at runtime | One loud `ERROR` + permanent dummy wrappers is a production incident discovered weeks later. (→ FP-3) |
| README says "~630 tests" but the suite is ~706 | Small staleness; auto-generate this number or drop it. |

---

## 3. Comparison with the popular embedded stores

Ballpark throughputs are order-of-magnitude figures from public materials; XdStorage numbers are
from its own JMH runs (BENCHMARKS.md / PERFORMANCE-RECOMMENDATIONS.md, JDK 17/21 Linux box).
All such numbers are workload-dependent — the point is the *gaps*, not the digits.

| Product | Model | Transactions / MVCC | Query capability | Durability story | Throughput ballpark | Footprint / constraints |
|---|---|---|---|---|---|---|
| **XdStorage 2.0** | Annotated POJO graphs; 3 placement policies | ACID 2PC; MVCC snapshots; triggers; per-resource journals (W-3) | Criteria engine + secondary/child indexes; EQUALS-indexed, scans O(N); **no range/ORDER BY/LIMIT yet** | Atomic rename + EOF sentinel; opt-in fsync; journaled data commits | Reads ~480/s (4 threads, full tx + object hydration); warm point lookup 12–16 µs; writes ~40–130 tx/s (index paths still O(file) — W-4 pending) | Pure Java 8 bytecode; **full JDK required at runtime**; cache = working set (heap ≈ DB); single process per folder; human-readable files |
| **MapDB 3** | `Map`/`Set`/lists over pluggable store | Optional ACID tx; MVCC store | None built-in (DIY secondary maps) | WAL + append; mature | 10⁴–10⁶ simple ops/s depending on cache | Pure Java; engine-level, no domain model |
| **JetBrains Xodus** | Key-value snapshots + (DNQ) entity store | ACID, MVCC, snapshot isolation | KV scans; rich queries only via DNQ layer | WAL, crash-safe | 10⁴–10⁵+ ops/s | Pure Java; production-proven (YouTrack); no object-graph policies |
| **RocksDB** | LSM key-value, `byte[]` | Atomic WriteBatch only; no cross-key tx/queries | None (get/iterate) | Tunable fsync; industry-grade crash semantics | 10⁵–10⁶+ ops/s writes | JNI native library; you build the entire object/serialization/query layer |
| **LMDB (lmdbjava)** | Memory-mapped B+tree KV | Single writer, MVCC readers | Cursor scans only | Copy-on-write pages, crash-safe | ~10⁶ reads/s, ~10⁵ writes/s | Native lib; DB size vs address-space limits |
| **Chronicle Map** | Off-heap concurrent `Map` | No transactions | None | Persistence on close/periodic | 10⁶+ reads/s, µs latency | Multi-process shared memory (unique); marshalling DIY; latency-oriented, not ACID |
| **H2 MVStore** | B+tree maps, MVCC | Per-map commit, R/W versions | None (that is what H2 SQL adds) | Compaction, WAL-ish | 10⁵–10⁶ in-mem ops/s | Pure Java; foundation of H2; no indexes/search of its own |
| **H2 / SQLite / HSQLDB (SQL)** | Relational, SQL | Full ACID | SQL (the benchmark everything is measured against) | Mature, tunable | SQLite 10⁴–10⁵ simple ops/s; H2 similar order | JDBC mapping layer required (schema + DTOs); SQLite is native; tooling is unmatched |
| **EclipseStore (ex-MicroStream)** | Native Java object-graph persistence | Lazy/storing semantics, not MVCC queries | None — data lives in heap, query in memory | Delta persists, very fast | 10⁵–10⁶ objects/s persists | Heap holds *all* data (same ceiling XdStorage has, without its indexes/queries); pure Java |

### 3.1 Where XdStorage wins outright

1. **The only one with a typed object-domain model *plus* a query engine *plus* triggers.**
   MapDB/RocksDB/MVStore give you bytes and maps — you write the serialization, indexing, and
   query layers. EclipseStore gives you the object graph but no indexes or queries. SQL engines
   give you queries but take your object model (schema + mapping layer). XdStorage is the only
   store where the annotated domain class *is* the schema, and search indexes/triggers/lazy
   loading are declarative annotations on it.
2. **Human-readable, pluggable on-disk format.** YAML/XML/JSON storage is inspectable with
   `cat`, diffable in code review, exportable with any tool, and custom formats are an SPI.
   Nobody else in this class offers that; for tools, fixtures, small config stores, and
   compliance-conscious deployments it is a real differentiator.
3. **True embedded ACID + MVCC semantics at the object level** — 2PC across resources, snapshot
   reads, triggers inside the commit, deferred-id observers. Xodus has transactions but no
   object model; EclipseStore has the model but not MVCC queries.
4. **Zero native dependencies, Java 8 bytecode.** Deploys anywhere a JVM goes, including
   legacy estates.
5. **Engineering transparency** (see §2.2) — rare and worth advertising.

### 3.2 Where XdStorage loses today

1. **Raw throughput: two to three orders of magnitude** vs RocksDB/MapDB/Chronicle on simple
   ops. Partly inherent (full object hydration + transaction protocol per op), partly the
   still-O(file) index write path (W-4 pending). The honest target domain is ≤ a few hundred
   write-transactions/s and ≤ a few thousand reads/s per class.
2. **The JDK-at-runtime codegen** (§1, blocker #1). MapDB/Xodus/MVStore/EclipseStore run on a
   bare JRE; Chronicle/RocksDB need only their native lib. XdStorage silently degrades without
   a JDK and a writable CWD.
3. **Heap ≈ DB ceiling** (§1, blocker #2). RocksDB/LMDB are designed for datasets ≫ RAM;
   XdStorage (like EclipseStore) is not — yet.
4. **Query expressiveness**: no range operators (`GT`/`LT`/`BETWEEN`), no `ORDER BY`, no
   `LIMIT`/`COUNT` — despite the B+tree already being ordered, so these are cheap wins (FP-1).
5. **Single process per folder.** Chronicle Map's multi-process IPC is the exact inverse;
   XdStorage should at least make *read-only tooling on a copy* excellent (FP-6/FP-7) rather
   than pretend the constraint away.
6. **Adoption infrastructure**: not on Central, no license, single-maintainer bus factor, no
   native-image story. For an architect, "can my team get support and patches in 3 years?"
   is part of the score.

### 3.3 Positioning statement the project should adopt

> *XdStorage is for JVM applications that want to persist their actual domain objects —
> graphs, not rows or bytes — with ACID transactions, declarative indexes and triggers,
> in a human-readable folder, with zero native dependencies and zero servers. The design
> point is 10⁴–10⁵ objects per class and hundreds of write-transactions per second;
> beyond that, choose RocksDB/MapDB and build the object layer yourself.*

---

## 4. When XdStorage is the right choice (decision guidance)

**Choose XdStorage when most of these hold:**

- The data is naturally an **object graph of JavaBeans** (documents with children, catalogs,
  configs, sessions, work queues, domain events) and you do not want a mapping layer.
- Scale is **10⁴–10⁵ objects per class**, a few GB of folder at most.
- Write rate is **≤ ~100–300 tx/s**, read rate ≤ a few thousand/s — typical for
  control-plane, UI-backing, desktop, edge, and tooling workloads.
- **Operability matters**: files you can `cat`/`grep`/`diff`, a folder you can back up with
  `cp -r` while closed, no server to run, no native library to ship.
- You need **ACID + snapshot reads** without a DBA, and triggers/id-generation/lazy-loading
  semantics that ORM gives you — without ORM's runtime cost and schema.
- **Java 8 bytecode compatibility** or a no-native-lib constraint rules out SQLite/RocksDB/LMDB.
- The data benefits from being **readable by non-JVM tools** (auditors, scripts, editors) —
  YAML/JSON files make the store self-describing.

**Concretely good fits:** desktop/rich-client apps, IDE plugins and developer tools, edge/IoT
gateways, k8s operators' local state, microservice-local domain stores below the SQL line,
build/CI tooling state, test fixtures and golden-data sets (readable diffs!), staging areas for
migrations, embedded configuration/feature-flag stores with queries and triggers.

**Do not choose XdStorage (yet) when:**

- You need **>10³ writes/s**, multi-GB data, or heap-independent datasets → RocksDB / MapDB.
- Multiple processes must share the store → Chronicle Map, or put a server in front.
- You need **SQL, joins, analytics, full-text search** → H2/SQLite/Lucene.
- You run **JRE-only containers, app servers with redeploys, or Graal native-image** → blocked
  by runtime codegen until FP-3 ships; after FP-3 this line moves.
- You cannot accept a single-maintainer, unlicensed artifact — until Track E ships (§6, step 0).

---

## 5. Feature wishlist (the architect's asks)

Priorities: **P0** = adoption blocker or cheap differentiator; **P1** = expands the addressable
domain; **P2** = ecosystem/polish. Items already inside MASTER-PLAN.md are endorsed by
reference, not re-planned.

| # | Feature | Why | Pri |
|---|---|---|---|
| FP-0 | **Track E release hygiene** (LICENSE Apache-2.0, CHANGELOG, semver `2.0.0`, Maven Central via OSSRH, release workflow) | Without this the product cannot appear on any dependency list. Endorses MASTER-PLAN E-1..E-6 + A2. | **P0** |
| FP-1 | **Query power pack**: `GT/GTE/LT/LTE/BETWEEN/IN/STARTS_WITH` primaries, `ORDER BY`, `LIMIT/OFFSET`, `COUNT`, query-aware `watch` | The B+tree primary index is *already ordered* — range scans and ordered iteration are nearly free, yet their absence forces O(N) scans today. This single feature pack is the largest query-expressiveness gap vs SQL engines, and it deepens the project's own differentiator. | **P0** |
| FP-2 | **`explain()` for queries**: plan (index used, seek vs scan, records visited, elapsed) returned with results or via a debug API | Turns the documented cost model into runtime visibility; protects users from accidental scans; feeds the metrics story. Small, high-leverage. | **P1** |
| FP-3 | **AOT wrapper generation** — maven/gradle plugin (or annotation processor) generating the observable/unmodifiable wrappers at *build* time; runtime codegen stays as fallback; loud startup failure when neither is possible | Removes all three deployment landmines at once: JRE-only runtimes, `./gencode` CWD pinning, cold-start javac cost; unlocks Graal native-image; supersedes MASTER-PLAN C1/C2 with a strictly better end state. | **P0** |
| FP-4 | **Heap-ceiling removal** — W-4 index journaling + disk-anchored scans + record-cache eviction (MASTER-PLAN B1), then B3 lock striping | The gate from "10⁵ objects" to "disk-bound, like a real engine". Endorses B1/B2/B3 as written; B2 (read-only commit skip) first as the quick win. | **P0 (B2) / P1 (B1, B3)** |
| FP-5 | **Backup & verify API**: `storage.backupTo(folder)` producing a consistent point-in-time copy (MVCC makes freeze-copy plausible), plus `verify()` integrity walk (sentinels, index-vs-data consistency) | The first question every ops engineer asks an embedded DB is "how do I back it up and how do I know it is sane". cp-while-closed is not an answer for long-running processes. | **P1** |
| FP-6 | **Admin/inspection CLI** (`xdstorage-tools.jar`): `inspect` (classes, counts, index stats, journal size), `dump <class>`, `verify`, `compact` (forced checkpoint) | The readable format makes this natural and cheap; it also serves as the support/diagnosis story (a user can attach `inspect` output to a bug). Operates on the folder or on an FP-5 backup — the single-writer lock stays intact. | **P1** |
| FP-7 | **Spring Boot starter** (autoconfiguration, `XdStorageConfig` properties, lifecycle bean, optional tx template); Quarkus/Micronaut later | The default adoption path in the Java world; an order-of-magnitude adoption lever for one module of glue code. | **P2** |
| FP-8 | **Metrics recipe**: a tiny `xdstorage-micrometer` module binding `getMetrics()`/`engineEvents()` to Micrometer | F-7 decided "embedder-side"; shipping the recipe as a module costs days and closes the question. | **P2** |
| FP-9 | **Binary compact format** as a non-default SPI implementation (post-W-4, if profiling justifies) | Kills the YAML parse cost on cold paths (80–87 µs per tree level today). The SPI already exists; WRITE-PATH-DESIGN §3 already anticipated revisiting. Keep readable formats default. | **P2** |
| FP-10 | **Encryption-at-rest SPI** (stream-cipher wrapper over the IO factory) | Compliance checkbox; SPI-shaped, so third parties could also do it. | **P3** |
| FP-11 | **Comparison & decision-guide page + runnable example app** | §2.3 gaps; the niche is invisible in the vocabulary users search for. Include the §3/§4 tables. | **P1** |
| FP-12 | **Graal/native-image + JDK 21 virtual-threads jobs in CI** | Extends MASTER-PLAN D-2; becomes meaningful the day FP-3 lands. | **P2** |

Explicitly *not* asked for (consistent with MASTER-PLAN §4 and the project's own verdicts):
network/server mode, multi-process write access, SQL layer, full-text engine, structural MVCC
without a W-4-class design round, the rejected RW-lock wait-rescheduling variants.

---

## 6. Step-by-step proposal (the roadmap I would follow)

This layers the feature plan onto MASTER-PLAN.md without contradicting it: Track A (flake
families) is closed as of 2026-10-19; what remains of the master plan is absorbed into the
steps below.

### Step 0 — Make it adoptable at all (≈1–2 sessions) → **tag 2.0.0**

A2 API freeze (JSON enum decision, format-version marker, `getVersion()`) + E-1/E-2
(LICENSE Apache-2.0 recommended over MIT for the patent grant + CHANGELOG backfilled from the
R-journals + semver + source/javadoc plugins) + E-3/E-4 (Central via OSSRH + tag-triggered
release workflow) + E-5/E-6 (RC protocol, compatibility statement). *Exit criterion: a stranger
can add the dependency from Central and read a license.*

### Step 1 — Quick wins on the two biggest axes (≈1–2 sessions)

1. **B2 read-only commit skip** (MASTER-PLAN): 25 ms → near-zero on no-change commits; the top
   read-heavy win, small and safe.
2. **FP-1 query power pack, phase 1**: range primaries on the B+tree (`GT/GTE/LT/LTE/BETWEEN/
   IN`) + `ORDER BY` + `LIMIT/OFFSET` on the primary path. Acceptance: JMH shows range queries
   scaling sub-linearly vs the O(N) scan they replace; regression tests for boundary semantics
   (nulls, mixed types); README query section updated.
3. **FP-11 decision-guide page** — publish §3/§4 of this document as README/docs pages once
   Step 0 makes the claims publishable.

### Step 2 — The engine's 2.x maturity (3–5 sessions, design round first)

1. **W-4 design round → index journaling + disk-anchored scans + eviction** (B1/F-3 thaw) —
   removes the heap ≈ DB ceiling and the index-write amplification; the ≥10× write acceptance
   target lives here.
2. **B3 lock striping / shallower chains** for the residual BTreeStress contention family
   (the only FLAKY.md row still open).
3. Gate: nightly ×3 clean, JMH write scenarios ≥10× vs the 2026-10-18 baseline, new flake
   ledger rows = zero.

### Step 3 — Deployment story (2–3 sessions)

1. **FP-3 AOT wrapper generation** (maven plugin first): build-time wrapper compilation into
   the consumer jar, runtime fallback preserved, loud failure when neither path is available.
   Acceptance: a JRE-only container runs the full suite-based demo without `./gencode`;
   cold-start first-save latency measured before/after in BENCHMARKS.md; native-image smoke
   job (FP-12) goes green.
2. **FP-5 backup/verify API** + **FP-6 CLI tools** (they share the verification core).
   Acceptance: backup of an actively-committing storage replays clean; `verify` detects an
   artificially corrupted folder; `inspect` output documented in EMBEDDING.md.

### Step 4 — Ecosystem (2.x→3.0 train, demand-driven)

FP-7 Spring Boot starter → FP-2 `explain()` → FP-8 Micrometer module → FP-9 binary format →
FP-10 encryption SPI → FP-12 CI matrix. Each independently shippable; nothing here gates
anything else.

### Sequencing summary

| Order | Work | Effort | Unlocks |
|---|---|---|---|
| 0 | Step 0 (release hygiene) → **2.0.0** | 1–2 sess | Everything public |
| 1 | B2 + FP-1 + decision guide | 1–2 sess | Query expressiveness; read-heavy workloads |
| 2 | W-4 + B3 (2.x engine maturity) | 3–5 sess | Large datasets; ≥10× writes; zero stress flakes |
| 3 | FP-3 AOT + FP-5/FP-6 ops tooling | 2–3 sess | JRE/native-image deployments; operability |
| 4 | FP-2/7/8/9/10/12 ecosystem train | 1–2 sess each | Mainstream adoption surface |

### Risks

- **W-4 is the hardest class of change** (persisted-state semantics). The project's own rule —
  mandatory design round, kill-9 recovery tests, honest benchmark verdicts — is the right
  mitigation; do not let Step 1 enthusiasm pull it forward.
- **AOT codegen must stay secondary to runtime codegen** until proven (annotation drift,
  incremental builds); the fallback path is the safety net.
- **Scope discipline on the ecosystem train** — each FP-7..FP-10 item is only worth doing when
  there is one concrete demand signal (a GitHub issue, an embedder); the frozen-items inventory
  exists precisely to prevent speculative growth.

---

## 7. Bottom line

As the evaluating architect, I would adopt XdStorage *today* for a tool, desktop app, or
service-local domain store with ≤10⁵ objects, ≤ ~10² writes/s, where readable files and
transactional object graphs are worth more than raw throughput — and I would put it on the
shortlist for much more the day three things land: a license on Central, range queries on the
ordered index, and build-time wrappers. All three are in sight of the roadmap above; none
requires re-architecting the engine. That is the definition of a project worth investing in.
