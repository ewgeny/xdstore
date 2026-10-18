# XdStorage — SRE Analysis & Whole-Project Optimization Plan

Written 2026-10-18, branch `xdstorage-2.0`, working tree after R-52.
Scope: the **whole project** — engine, build, tests, observability, lifecycle, docs — from an
SRE standpoint (data integrity → availability → performance → operability → hygiene).
The engine-internal campaign lives in [OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md) (R-1..R-52);
this document complements it and does not repeat it. Benchmarks: [BENCHMARKS.md](BENCHMARKS.md).

Every finding below is grounded in the current source (file:line refs). The plan is phased;
each phase lists its acceptance gate. Priorities: **P0** = data-loss / hang risks,
**P1** = capacity & the dominant performance wall, **P2** = observability & config,
**P3** = build/test/docs hygiene.

---

## 1. Executive summary

XdStorage is an embedded transactional object store (~20.8k LOC main / ~23.9k LOC test, 180 main
classes) with MVCC snapshot/RC isolation, B+tree & hash indexes, a search engine, runtime wrapper
codegen, and YAML/XML file persistence (one whole-file rewrite per commit). The 2026-10-16..18
campaign hardened the engine's concurrency and MVCC semantics (R-1..R-52) and left the full suite
at 471 tests with only two documented rare flakes.

**The honest SRE verdict:** the engine's *logical* concurrency is now far ahead of its
*operational* guarantees. The four risks that would page an SRE on day one are all in the
persistence/lifecycle layer, not in the MVCC machinery:

| # | Risk | Severity | Where |
|---|------|----------|-------|
| 1 | A truncated (crash-cut) **YAML** resource — the default format — loads **silently as a partial database**; the next commit rewrites the file and makes the loss permanent | P0 data loss | `XdStorageYamlTokenizer.java:96`, `XdStorageYamlObjectsReader.java:27-55` |
| 2 | **No fsync anywhere**: commits are process-crash-safe (atomic rename, R-11) but not power-loss-safe; recent commits can vanish | P0 durability | `XdStorageAtomicFile.java:47,63-73` |
| 3 | **No cross-process locking**: two OS processes (or two same-name mishaps) opening one folder silently clobber each other file-by-file | P0 corruption | `XdStorageProvider.java:18-46` |
| 4 | **JVM hang by default**: 15 non-daemon worker threads per storage, no shutdown hook, no `closed` guard — an app that forgets `shutdown()` never exits; a caller after `shutdown()` dies with raw `RejectedExecutionException` | P0 availability | `XdStorageServicesLocator.java:56,126-129` |

Plus the one structural performance wall the benchmarks already quantify: **every commit rewrites
every touched resource file in full** (write amplification) — the direct cause of ~39 ops/s
save+delete and ~127 ops/s facade tx flow, and the reason D-3 batching was parked. There are also
**zero metrics hooks** (observability = logs only) and **unbounded in-memory caches** (heap
ceiling = full database contents).

What is already solid and should not be re-litigated: atomic-rename write-out (R-11), buffered IO
(R-25), the R-24 logging discipline (INFO/WARN/ERROR with quiet hot paths), Hi-Lo id safety
(counter pre-persisted by a full batch — crash burns ≤99 ids, never duplicates), the B+Tree
retry/invariant tripwires (R-17/R-18/R-33/R-36), the R-46 cache split with 57 invariant tests,
and the benchmark documentation discipline.

---

## 2. Findings by area (ground truth)

### 2.1 Reliability & data safety

- **Silent partial load of truncated YAML (P0).** The tokenizer returns `null` at EOF with no
  block-completeness check and the reader just ends its loop — a truncated file yields a
  *quietly partial* cache (`XdStorageYamlTokenizer.java:96`,
  `XdStorageYamlObjectsReader.java:27-55`). For XML the parse throws (loud), and the
  truncation-tolerant `read(Reader, tx)` variant (`XdStorageDefaultObjectsReader.java:100-105`)
  currently has no resource-layer callers. There are no checksums, no startup integrity pass,
  no journal replay; recovery = trust the files (`XdStorageResource.prepare:181-203`).
- **Durability stops at the page cache (P0).** `XdStorageAtomicFile` buffers to `<file>.tmp`,
  closes, `Files.move(ATOMIC_MOVE)` — and never calls `FileChannel.force`/`sync` (verified:
  zero hits project-wide); the rename is not followed by a directory sync. Contract today:
  process-crash-safe, **not** power-loss-safe. The non-atomic-move fallback even documents
  possible file loss (`XdStorageAtomicFile.java:63-73`).
- **No inter-process exclusion (P0).** No `FileLock` anywhere; the provider registry is per-JVM
  only (`XdStorageProvider.java:18-20`). Concurrent openers of one folder = last-rename-wins
  per file: lost commits, torn index-vs-data pairs.
- **Hi-Lo ids are crash-safe (KEEP).** The counter record is advanced by the whole batch in its
  own committed tx before ids are issued (`XdStorageLongIdGenerator.java:81-107,126-127`); a
  post-crash counter is always ≥ max issued id. Cost: ≤99 burned ids per crash. No action.
- **Stale `.tmp` recovery is lazy-only.** Orphaned temp files are cleaned at the *next write* of
  the same resource (`XdStorageAtomicFile.java:30-34`); there is no startup sweep, so a crash
  leaves `.tmp` litter until each file happens to be rewritten.

### 2.2 Lifecycle & concurrency contract

- **No shutdown hook; non-daemon pool.** `shutdown()` = unregister + `executor.shutdown()`
  (`XdStorage.java:1260-1263`, `XdStorageServicesLocator.java:126-129`); the 15 fixed threads are
  non-daemon (`XdStorageServicesLocator.java:56`), zero `addShutdownHook` in the tree. Forgot
  `shutdown()` → JVM hangs at exit with committed data safe but in-flight tx abandoned.
- **No closed-state guard.** The instance stays callable after `shutdown()`; internal
  `executor.submit` sites (`XdStorageTransactionManager.java:204,276`) would surface as raw
  `RejectedExecutionException` instead of a clear "storage is closed".
- **Provider aliasing gotcha.** Re-requesting a known name returns the existing instance and
  **ignores the folder argument** (`XdStorageProvider.java:41-45`, in-code WARN exists). This is
  the R-50 test-contamination family's root; it is documented but not *preventable* at the API
  level.
- **Thread-bound transactions + pools.** Binding is by `thread.getName() + getId()`
  (`XdStorageTransactionManager.java:345-348`); a pool thread that abandons its tx leaves the
  next task on that thread inheriting it as "current". Contract works (the facade stress test
  exercises it) but is undocumented at the API level.
- **Timeouts fire lazily.** There is no independent expiry timer; a timed-out tx rolls back only
  when a lock wait, record latch, or nested-tx sweep trips (`XdStorageCacheRecord.java:379-388`,
  `XdStorageTransactionManager.java:275-339`). The R-31 sweeper fixed the *stuck rollback*, not
  the *late detection*.

### 2.3 Performance & capacity

- **Full-file rewrite per commit — the wall (P1).** Phase 1 of every commit calls
  `writeObjects` for each touched resource (data fragment + id-index tree file + search-index
  file), serially, in commit-order priority (`XdStorageResource.java:205-211`,
  `XdStorageTransaction.java:324-406`, R-25 measured the quadratic amplification). BENCHMARKS.md:
  writes are commit-bound (~13 ms/commit), reads are fine (~470 ops/s indexed). No WAL, no
  append, no group commit. This is D-3's bigger sibling and the single biggest lever in the
  project (10–100× on write-heavy workloads).
- **Unbounded memory (P1).** The record cache is a plain `ConcurrentHashMap` filled with every
  parsed object on open (`XdStorageResourceCache.java:54,169-175`); per-tx materialized clones
  are uncapped (`XdStorageResourceCache.java:205,329-337`); "eviction" is resource-refcount only
  (`XdStorageAbstractResourcesManager.java:313-325`). Heap requirement ≈ whole DB + working-set
  clones. The `-Xmx2g` surefire bump (R-42) is a symptom of exactly this.
- **Scan paths 3.5–4× slower than indexed** (BENCHMARKS.md §1) — acceptable, documented; keep
  as guidance (prefer EQUALS criteria) rather than a work item until the write wall falls.
- **Runtime codegen hazards (P2).** Wrappers compile via the system JavaCompiler on first use,
  serialized by a static lock, output to `./gencode` relative to process CWD
  (`XdStorageClassGenerator.java:19,42-89`). A JRE or read-only CWD → **permanent** dummy
  wrappers with broken equals/hashCode cached for the JVM's lifetime
  (`XdStorageProxyCompilationEngine.java:35-36`); ERROR log only, no retry, no pre-generation
  tool, no config switch. The shared static `URLClassLoader` pins the gencode dir JVM-wide
  (app-server redeploy hazard).
- **Global statics cross instances (note).** `COMMIT_SEQUENCE` is JVM-global by design (correct
  for cross-storage MVCC ordering); codegen/wrapper/metadata caches are static and never
  evicted; the observer service is a JVM-global wrapper registry (`XdStorageObserverService.java:18`).
  Fine for an embedded library; must be documented as an embedding constraint.

### 2.4 Observability

- **Metrics: none (P2).** Zero JMX/Micrometer/counter hits project-wide. The only numbers are
  ad-hoc internal methods (`getObjectsCount()`). After R-24/R-46 the *log* channel is genuinely
  good (anomaly WARNs, per-commit INFO, quiet hot paths) — but capacity planning, latency
  attribution, and lock-contention trends need counters, not grep.
- **Logging config hazards (P2).** The bundled `log4j2.xml` writes `xdstorage.log` in the process
  CWD with `append="false"` — truncated on every start, no rotation, no size cap (storm-era runs
  produced multi-GB logs; the R-25-era root file is still 4 MB after one run). Worse: a config
  inside the jar can hijack the *host application's* log4j2 configuration if none is loaded
  earlier.

### 2.5 Build, dependencies, tests, docs

- **JDK split-brain.** pom targets 1.8 (`jdk.version=1.8`) via **compiler-plugin 2.3.2 (2010)**,
  while the runtime is JDK 17 and surefire already passes JVM GC-log flags. No
  `maven.compiler.release`; no `project.build.sourceEncoding` (platform-dependent compile
  encoding). Works today by luck and IDE goodwill.
- **Non-reproducible/dead dependencies.** `junit:RELEASE` (floating version — builds not
  reproducible), `postgresql:9.4.1212` (2016, CVE-laden, **unused** — only a comment in
  `XdStorageSecurityUtils.java:30` mentions SQL), `commons-lang3:3.8.1` (old but harmless).
- **Fake repository URL.** `<url>https://mvnrepository.com</url>` is a documentation site, not a
  Maven repository; resolution works only because Maven Central is implicit.
- **No CI at all.** No `.github/`, no Jenkinsfile, nothing. Every suite run is manual; the flake
  families (GraphPhantom residual ~1/11, facade "рассинхрон структуры" validator, R-50
  stale-dir contamination) are documented in prose but not quarantined mechanically; surefire
  reports are not archived between runs (target/ is cleaned).
- **JMH runs are manual** with hand-counted tables (BENCHMARKS.md gotcha #1: JMH *silently skips*
  failed trials — the row count is the check).
- **Docs.** README.md is a 2019 changelog — no build/run/usage/troubleshooting. Project-level
  documentation exists as `XdStore.docx` (untracked, Word). `facade_stress_db/` (benchmark
  storage litter) is untracked and not gitignored. The real knowledge base is
  docs/OPTIMIZATION-PLAN.md — excellent, but it is a campaign journal, not onboarding material.
- **Test layout quirk.** `testSourceDirectory = src/main/test` and test resources under
  `src/main/test/resources` — nonstandard but working; renaming is churn without SRE payoff
  (document, don't move).

---

## 3. Optimization plan

Ordering principle: stop the bleeding (data safety) before tuning (performance), make it
observable before making it faster at scale, and mechanize (CI) what is today discipline.

### Phase 0 — Hygiene quick wins (half a session, zero engine risk)

| # | Item | How |
|---|------|-----|
| H-1 | Remove the dead `postgresql` dependency; pin `junit` to 4.13.2 (or drop in favor of JUnit 5 usage already present) | pom.xml |
| H-2 | `maven-compiler-plugin` → 3.13.0 with `<release>8</release>`; add `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>` | pom.xml; gate: full suite green + BENCHMARKS run unchanged |
| H-3 | Delete the fake `<repositories>` block (Central is implicit) | pom.xml |
| H-4 | `.gitignore`: add `facade_stress_db/`, `*.log` (or at least `xdstorage.log` is already covered), benchmark storage dirs pattern `*_db/` | .gitignore; commit XdStore.docx or convert to `docs/` first |
| H-5 | Decide JDK policy explicitly and codify it: library bytecode stays 1.8-compatible (`release=8`), test/dev runtime is 17 — record in README; this closes the R-50 class of "works on my JDK" surprises | README + pom |

**Gate:** `mvn clean test` green, one JMH smoke run still produces the expected 9-row table.

### Phase S — Data safety (P0; the items an SRE would block a release on)

| # | Item | Design sketch | Verification |
|---|------|---------------|--------------|
| S-1 | **Loud truncation detection** for the default YAML format | (a) writer appends an end-of-stream sentinel line (or per-file trailing `checksum: <crc>` line) in `XdStorageYamlObjectsWriter`; (b) reader validates sentinel on `prepare()` and throws `XdStorageConnectionException("resource truncated: N objects read, file incomplete")` instead of silently ending; (c) optional startup flag `validateOnOpen=true` walks all resource files once | New tests: truncate a storage file mid-object → open must throw, not serve partial data; full suite green (sentinel must be backward-compatible or a one-time migration WARN) |
| S-2 | **Durability option**: `XdStorageProvider.newOrGetFileStorage(..., durable=true)` or config flag | In `XdStorageAtomicFile.writeReplacing`, when durable: open via `FileChannel`, `force(true)` before close, then move + fsync parent directory (channel on directory). Default **off** preserves current throughput; javadoc states the exact contract: default = process-crash-safe; durable = power-loss-safe at ~fsync cost per commit | Durability test: kill -9 survives already (R-11 covers); power-loss can't be tested in CI — assert `force` is invoked on the channel (mock/byte-buddy or refactor for injectable sync strategy); benchmark delta recorded in BENCHMARKS.md |
| S-3 | **Cross-process lock**: `<folder>/.xdstorage.lock` held via `FileChannel.lock` for the storage's lifetime | Acquire in provider after the name check, release in `shutdown()`; failure = loud `XdStorageException("storage folder is locked by another process (pid file: …)")`. Write pid+timestamp into the lock file for diagnosis | Two-process test (fork a child holding the lock → parent open must fail; child exits → parent opens) |
| S-4 | **Lifecycle guards**: `closed` flag → all public methods throw `XdStorageException("storage is closed")`; optional `shutdownHook(boolean)` registering `Runtime.addShutdownHook` that rolls back live txs and shuts the executor; executor threads become daemon-by-option | `XdStorage.java` + `XdStorageServicesLocator.java` | Test: use-after-shutdown throws the clear exception; app without `shutdown()` exits (daemon mode); hook runs once |
| S-5 | **Startup `.tmp` sweep + open INFO summary** | At first `prepare()` of a storage folder: delete stale `*.tmp`, log INFO "opened storage X: C classes, N objects, K indexes" | Test with planted `.tmp` files; log assertion |

**Gate:** the four P0 rows of the summary table each have a failing-without-fix test; full suite
green; BENCHMARKS.md re-measured with S-2 on and off.

### Phase W — Write-path performance (P1; the big lever, needs its own design round)

This is D-3's full scope. The campaign deliberately parked it because it changes crash/commit
semantics — so it gets a design document before code.

1. **Design first (one doc):** append-only commit journal per data class
   (`data/<Class>/journal.<fmt>`): phase-1 appends redo records (op, id, payload) + fsync-able
   marker instead of rewriting files; checkpoints rewrite base files at interval or on
   size/threshold; recovery = base + journal replay (the S-1 sentinel gives replay a hard stop).
   Alternatives to evaluate honestly: (a) journal per commit with async checkpointing —
   highest win, most new state; (b) batch/group-commit only (accumulate N txs → one write-out)
   — small win, minimal semantics change; (c) copy-on-write fragmenting (rotate fragment files,
   drop old on checkpoint) — middle ground, reuses the existing full-file writer as the
   checkpoint mechanism.
2. **Decision criteria (from BENCHMARKS.md):** target ≥10× on `saveAndDelete` (38.7 → 400+ ops/s)
   and `facade tx flow` (127 → 1000+ ops/s) without regressing `loadById` (480) or the
   crash-recovery contract; memory overhead bounded; journal truncation must be crash-safe
   (sentinel + S-2 force).
3. **Prerequisite:** S-1 (sentinel) and S-2 (durable force) land first — the journal's recovery
   story is built on them.
4. **Cache bounds ship in the same phase (W-2):** max-records/estimated-bytes cap per resource
   with cold-committed-record eviction (re-loadable from disk on demand — the read path already
   handles record misses via file re-prepare); LRU of committed, unlocked, non-infra records
   only. Config knob with the Phase O config object. Test: capped cache over a dataset > cap →
   suite green + heap bounded (assert via MXBean in test).

**Gate:** new journal/eviction invariant suites (the R-46 pattern: collaborators + stub context);
full suite green; before/after JMH table in BENCHMARKS.md; a kill -9 mid-journal recovery test.

### Phase O — Observability & config (P2)

1. **Metrics facade (O-1):** an `IXdStorageMetrics` interface — counters for
   commits/rollbacks/rollbackOnly marks, lock-acquisition timeouts & 1s+ waits (the R-24 WARN
   signals, now as numbers), find-retry-budget exhaustions, records per cache, per-resource
   write-out counts and nanos, tx begin/active count. Implementation: increment-in-place
   `LongAdder`s on existing hot-path sites (zero allocation); expose via
   `storage.getMetrics()`; optional JMX/Micrometer adapter left to the embedder. The R-24 log
   sites become the metric sites — same choke points.
2. **Logging hygiene (O-2):** `RollingFile` appender (size + count caps, `append="true"`);
   stop shipping an unconditional `log4j2.xml` in the main jar (move to a `log4j2.component.properties`-guarded default or document that embedders must supply config); log file location configurable via system property `xdstorage.log.dir`.
3. **Config surface (O-3):** a `XdStorageConfig` object on the provider: tx timeout default,
   lock budgets, worker-pool size + daemon flag, isolation level (currently unreachable through
   the public API — `XdStorageResourceCache.java:149-155` is internal-only), cache caps (W-2),
   durable flag (S-2), validate-on-open (S-1). Defaults = today's values; no behavior change.
4. **Document the embedding contract (O-4):** thread-bound txs, static caches, JVM-global
   `COMMIT_SEQUENCE`, codegen JDK requirement, gencode dir, provider name-aliasing — one
   "Embedding XdStorage" page in `docs/`.

**Gate:** metrics unit tests (counter increments at injected sites); config object defaults
snapshot-tested against today's constants; full suite green (no hot-path regression in the JMH
smoke).

### Phase C — CI, tests, docs (P3)

1. **CI pipeline (C-1):** one job `mvn clean test` (the R-52 shell gotcha — grep's exit code
   masquerading as build success — is exactly what CI prevents: gate on `BUILD SUCCESS`, archive
   surefire reports as artifacts); nightly job: full suite ×3 (flake-rate tracking) + JMH smoke
   with an **assertion on the table row count** (the documented silent-skip failure mode).
2. **Flake quarantine (C-2):** a `FLAKY.md`/list consumed by a surefire excluded-groups profile;
   known families (GraphPhantom residual ~1/11, facade "рассинхрон структуры" validator) stay
   visible as nightly-optional instead of poisoning the main gate. Un-quarantine as R-45-residual
   work lands.
3. **Test-infra discipline (C-3):** enforce unique-storage-name/dir per run (R-50) via a shared
   test rule; surefire reports archived per CI run.
4. **Docs (C-4):** README rewrite — what it is, build (`mvn test` expectations incl. runtime
   JDK), 15-line usage example (open → tx → save → search → commit → shutdown), operational
   notes (log location, gencode dir, JDK-not-JRE, storage folder = engine-owned, don't open twice
   — pre-S-3), troubleshooting (R-50 stale-dir, lock-timeout WARNs meaning, flake families).
   Convert `XdStore.docx` content into `docs/USER-GUIDE.md` (docx is unversionable).

**Gate:** CI green twice in a row on a clean checkout; README verified by a fresh-clone run of
its instructions.

### Explicitly out of scope (recorded so they are not re-proposed)

- Structural MVCC versioning of the B+Tree topology (Phase 3b's frozen cloner pass-through) —
  the R-19/R-20 canonical-registry approach is the landed compromise; revisit only if the
  residual flakes (R-45) prove to be topology tears.
- RW-lock redesign (R-28's rejected variants are documented in-source; do not re-attempt blind).
- JSON format implementation (`FilesFormat.JSON` is an enum constant with no serializer — either
  remove it from the public enum or file it as a feature, not an optimization).
- Moving tests out of `src/main/test` — churn without operational payoff; document instead.

---

## 4. Sequencing & effort

| Phase | Sessions (est.) | Depends on | Risk |
|-------|-----------------|------------|------|
| 0 Hygiene | 0.5 | — | none |
| S Data safety | 1.5–2 | 0 | low (additive, gated by new failing-first tests) |
| O Observability/config | 1 | 0 (config object precedes W-2/S-2 knobs) | low |
| C CI/docs | 0.5–1 | 0 | none |
| W Write-path | 3–5 (design doc first) | S-1, S-2 | medium-high (crash semantics) |

The single most valuable next session: **Phase 0 + S-1 + S-3** — one half-session removes the
silent-partial-load and concurrent-processes data-loss classes entirely.
