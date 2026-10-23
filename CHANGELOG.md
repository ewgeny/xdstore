# Changelog

All notable changes to XdStorage are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/). The release procedure itself lives in
[RELEASE-PLAN.md](RELEASE-PLAN.md).

**On-disk format generations** (recorded per storage folder in `.xdstorage.meta`,
see `XdStorageFormatMeta`):

- **v1** — the 1.x era: YAML files without an end-of-file sentinel, no commit journal.
- **v2** — the 2.0 era: YAML EOF sentinel (S-1), optional per-transaction commit journal
  (W-1, `journalWrites` ON by default since W-3).

## [Unreleased] — the 2.1.0 train (MASTER-PLAN Step 1)

Planned scope; items land here as they merge:

### Added (planned)

- **B2** — read-only transaction commit path, verified and pinned by tests: no-change
  transactions commit through a cheap release path (no 2PC record iteration, zero file
  write-outs); measured 0.6–0.7 ms/op per read transaction at N=5000–40000 on the Hash path
  (the PERF study §5.3 ~25 ms figure no longer reproduces — BENCHMARKS.md addendum).
  Contract coverage: committed-count metrics, snapshot isolation under a concurrent writer,
  rollback-only still throws, a parent still waits for an open nested read-only transaction.
- **FP-1 phase 1** — query power pack, range primaries: `GT`/`GTE`/`LT`/`LTE`/`BETWEEN`/`IN`
  on the primary criterion, evaluated as an ordered bounded walk of the index B+tree with early
  termination (selective ranges run ~15× the scan they replace; lower-bound-only and wide ranges
  cost like a scan — README § Search engine has the operator table and plan/fallback matrix).
  Contract: `BETWEEN` bounds are inclusive (`XdStorageRange`); nulls never satisfy a range
  operator; mixed-type bounds fail loudly.
- **FP-1 phase 2 (FP-1b)** — result shaping: `ORDER BY` on the primary index value (native key
  order on the ordered walk; explicit in-snapshot sort on the scan path; ASC/DESC with nulls as
  the greatest value and a stable sort for equal keys; secondary-field ordering explicitly
  unsupported — the index orders by key trees, not arbitrary fields), `LIMIT`/`OFFSET` pagination
  after ordering (negative arguments fail loudly at construction; `LIMIT 0` is a legal empty
  page; on a single-criterion ascending range walk the limit is pushed down — the walk stops when
  the page is complete, ~25% ahead of the unbounded same-window range), and
  `count(cl, indexName, query[, tx])` — the record-level count of snapshot-visible matches
  without building the result collection (ignores the shaping, SQL `COUNT` semantics; `count(q)`
  is exactly `load(q).size()` on the same snapshot). Keyset/cursor pagination is the filed
  follow-up for the `OFFSET` re-walk cost.
- **FP-11** — decision-guide page (comparison vs MapDB/Xodus/RocksDB/Chronicle/EclipseStore)
  + runnable example app.
- **BENCH** — the benchmark estate unified: `scripts/run-benchmarks.sh [quick|full|sweep]`
  is the one entry point under a documented conditions protocol (quiet-load gate, WARN-console
  benchmark logging config, YAML format, JSON results + run manifests); the CORE scenarios
  (read-only tx, journal on/off, durableWrites on/off, commit-batch scaling, FP-1 shapes)
  live in `XdStorageCanonicalBenchmark` with machine-readable `@Param` dimensions;
  `docs/BENCHMARKS.md` is the single canonical baseline (generated, traceable to archived
  result sets); the nightly smoke asserts the final scenario list.
- **D-1/D-4** — JaCoCo coverage report in CI; JMH regression alert in the nightly.
- **Track T** (MASTER-PLAN) — formal test groups: every stress/integration class carries
  JUnit 5 `@Tag("stress")`/`@Tag("integration")`; the `release` profile excludes by tag
  (filename patterns stay as a backstop until 3.0), while `quick-gate` keeps its documented-flaky
  name list (the two structural tags cannot express it). The naming↔tag pairing is enforced in
  both directions by `XdStorageTestGroupingContractTest` in the default run. Vintage cleanup:
  JUnit 4 left the build — and the audit found the two JUnit-4-only serialization tests had been
  silently dead all along (no vintage engine was ever configured); migrated to JUnit 5, they now
  run in every suite. Gap-fill suites (T-3a) landed for the dark packages — `structure`
  8%→54%, `trigger` 73%→88%, `index` 75%→100% — pinning the trigger contour, Hi-Lo/UUID id
  contracts, index-cache and object-model semantics, and discovering four engine defects on
  the way (structure-resource journal replay NPE after reopen, id-addressability after a
  structure update, auto-generated ids unreachable by id after reopen, and the
  `UUID_GENERATOR` field property never auto-generating) — each documented with an
  acceptance-grade disabled test in docs/tasks/2.1.0/06; the facade happy/failure-path audit
  (T-3b) remains open.

### Fixed (planned)

Still open from the defect train (**a defect discovered by a train's own work ships with the
train that discovered it**; BUG-A/C/D are fixed — see [Fixed](#fixed) below):

- **BUG-B** ([16](docs/tasks/2.1.0/16-id-addressability-after-structure-update.md)) — refined:
  the codegen wrapper identity is NAME-based, so two live generations of one entity FQCN
  collide on one wrapper class; generation-aware wrapper identity is the design decision
  to make.
- **BUG-E** ([18](docs/tasks/2.1.0/18-bug-search-index-key-types-after-reopen.md)) — FIXED:
  search-index key types are restored at reopen. After a close/open cycle, range, `EQUALS` and
  `count` on a **numeric** search index crashed (`Long` bound vs raw-`String` persisted keys and
  records inside the B+Tree node files). The fix walks each search tree ONCE per reopen
  (CAS-guarded, same lock discipline as the read walk) and restores the key values to the indexed
  class's primary index field type, the object pointers to the entity id-field type, and the index
  records' `primaryIndexValue`/id — the type authority is the declared field type (the BUG-C
  lesson, now a shared `XdStorageKeyTypeRestore` utility). No migration: on-disk keys were always
  written typed. String-keyed indexes were unaffected, which is why every earlier reopen
  regression missed it. Discovered by the FP-11 example app; its quickstart asserts range + count
  after the reopen again.

### Changed (planned)

- **Track L (MASTER-PLAN) — logging through the SLF4J API; log4j2 test-only.** The library
  now logs via `slf4j-api` — the only logging artifact in its dependency tree (65 KB, no
  dependencies of its own; previously `log4j-core` 2.23.1 (~1.8 MB + CVE surface) arrived
  transitively with a bundled config that logged the engine's per-operation INFO to the
  consumer's stdout). **`log4j-core` is no longer provided** — the backend is the
  application's choice: logback, log4j2 via `log4j-slf4j2-impl`, `slf4j-jdk14`, or none.
  Example consumer configurations (log4j2 + logback, quiet and diagnostic-verbose) ship in
  `docs/examples/`; see EMBEDDING.md § Observability. **Migration:** code that relied on
  log4j2 arriving transitively declares a backend explicitly; an application without any
  backend sees at most SLF4J's single documented "no provider" notice.
- **Quiet-by-default level discipline** (Track L-2). The engine's per-operation INFO lines
  (save/delete/commit/rollback/prepare traces) are demoted to DEBUG; INFO remains for rare
  lifecycle events (storage open/close, journal checkpoint). This addresses the log spam
  observed around the 2.0.0 release: a consumer with a backend now sees lifecycle events
  only, and a consumer without one sees nothing.

## [2.0.0] — 2026-10-21 (first public release)

The 2.0 engineering campaign is journaled in detail in
[OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md) (engine, R-1..R-59),
[SRE-OPTIMIZATION-PLAN.md](SRE-OPTIMIZATION-PLAN.md) (S-1..S-5, W-1..W-3, O/C phases),
[CODEGEN-OPTIMIZATION-PLAN.md](CODEGEN-OPTIMIZATION-PLAN.md) and
[PERFORMANCE-RECOMMENDATIONS.md](PERFORMANCE-RECOMMENDATIONS.md). Highlights below.

### Breaking

- **Package and Maven coordinates renamed**: the library moves from the never-published
  `org.flib.xdstorage` namespace to **`io.github.xdstorage`** — groupId `io.github.xdstorage`,
  artifactId `xdstorage`, root Java package `io.github.xdstorage` (subpackages keep their
  relative structure). Every `import org.flib.xdstorage.…` becomes
  `import io.github.xdstorage.…`; nothing was ever published under the old coordinates,
  so there is no dependency-graph migration — only import renames.
- **`FilesFormat.JSON` removed** (A2/F-5): the constant silently wrote XML content into
  `.json`-named files (the "default" factory is an XML implementation). Code passing
  `FilesFormat.JSON` must switch to `FilesFormat.XML` or `FilesFormat.YAML`. A 1.x folder
  written through the old constant is an XML-format folder with a `.json` extension —
  migrate by renaming `*.json` → `*.xml` inside `data/` and `structure/` and reopening with
  `FilesFormat.XML` (recipe in [EMBEDDING.md](EMBEDDING.md) § Compatibility).
- **YAML readers reject files without the EOF sentinel by default** (S-1). 1.x-era YAML
  folders open with `XdStorageConfig.builder().acceptMissingEofSentinel(true)`; 2.0-created
  folders are unaffected.
- **Version scheme**: pom version moves `2.0` → `2.0.0` (strict semver from now on).

### Added

- **Commit journal** (W-1..W-3): append-only per-transaction journal with O(delta) appends,
  prepare-replay, size-threshold checkpoint, failed-commit compensation; per-transaction
  materialization budget (`txMaterializedObjectsCap`) with loud failure instead of unbounded
  heap; `journalWrites` defaults ON (DATA classes; B+tree infrastructure excluded after a
  soak-caught reopen divergence fix).
- **Operability** (S-phase): exclusive cross-process folder lock (`.xdstorage.lock`); JVM
  shutdown hook + daemon-worker option; loud `XdStorageRuntimeException` after `shutdown()`;
  stale `.tmp` sweep at open with a file-count summary; `XdStorageMetrics`
  (`storage.getMetrics()` / JVM-wide `engineEvents()`).
- **API freeze** (A2): on-disk format marker `.xdstorage.meta` + startup INFO
  ("opened/created storage X, format vN"), legacy-1.x detection and loud rejection of
  folders written by a newer library; `getVersion()` on the public storage interface and
  `XdStorageProvider.getVersion()` (semver from the Maven-filtered version resource).
- **Release engineering** (E-1..E-4, D-3): Apache-2.0 LICENSE, this CHANGELOG, semver
  `2.0.0`, pom metadata (description/url/scm/licenses), `release` profile (sources +
  javadoc jars, GPG signing, Central Portal publish, release-only enforcer rules),
  reproducible builds (`project.build.outputTimestamp`), tag-triggered release workflow,
  Dependabot.

### Fixed

- **BUG-C — numeric ids are addressable by id after a reopen** (2.1.0/17): the hash id-index
  resource restored record keys without the ENTITY's id-field type (`Long 9000` vs stored
  `"9000"`), so every numeric-id lookup missed after reopen — generated and explicit alike;
  String ids masked it for years. Keys are now typed on cache fill; no migration needed
  (on-disk records were always correct).
- **BUG-D — `UUID_GENERATOR` now generates** (2.1.0/14): the property never activated
  auto-generation (all three gates covered: both id-index resources at save time + the
  writer's four write-out sites, unified into one helper); the mux validates
  UUID_GENERATOR to String ids loudly; the README id-generation matrix replaces the
  incorrect "client assigns" text.
- **BUG-A — a structure update can no longer strand the folder** (2.1.0/15): journal/data
  records embed `java.lang.Class` values that raw `Class.forName` could not resolve for
  application-loaded entity generations → null identity → a replay NPE that made the
  folder unopenable. Class resolution now goes through the metadata registry (any
  classloader), unresolvable journal upserts are skipped loudly (documented recovery),
  and the codegen shared loader bridges application code sources.
- **BUG-B refined, open** (2.1.0/16): after the A/C fixes the remaining seam is precise —
  the codegen wrapper identity is NAME-based, so two live generations of one entity FQCN
  collide on one wrapper class. Filed with the exact reproduction; generation-aware
  wrapper identity is the design decision to make.

- **Track A flake families** (R-54..R-58): facade-storm, GraphPhantom and Xml purge flakes
  resolved — 10/10 all-stress gate, full suite green; 13 regression pins (`333e864`).
- **StackOverflowError on deep B+trees** (R-59): iterative leaf-chain walk in
  `node.read/find`.
- **Reopen divergence** (R-53): commit journal now covers DATA classes only (B+tree
  infrastructure exclusion), caught by a 24h soak.
- Full engine journal: R-1..R-59 in [OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md).

### Changed

- Engine defaults from the configuration study: worker threads **t=64**, fragment size
  **frag=128** ([PERFORMANCE-RECOMMENDATIONS.md](PERFORMANCE-RECOMMENDATIONS.md)).
- Wrapper codegen hardened to the JavaBean specification
  ([CODEGEN-OPTIMIZATION-PLAN.md](CODEGEN-OPTIMIZATION-PLAN.md)).
- Test suite: ~706 tests (up from ~120 at the 2.0 campaign start), stress/JMH families
  included; benchmarks and baselines in [BENCHMARKS.md](BENCHMARKS.md); flake ledger in
  [FLAKY.md](FLAKY.md) (one documented, tolerated full-suite-load row → Track B3).

## [1.x] — historical (never tagged)

Pre-history: no tagged releases; on-disk format v1. Notable arcs from the git log: XML
storage core, B+tree secondary indexes and hardening, search engine, YAML format
introduction, wrapper codegen (simple/unmodifiable/observable), resource cache, MVCC
snapshot isolation, PostgreSQL support removed. 1.x folders open in 2.0 per the
compatibility matrix in [EMBEDDING.md](EMBEDDING.md).

[2.0.0]: https://github.com/xdstorage/xdstorage/releases/tag/v2.0.0
