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
