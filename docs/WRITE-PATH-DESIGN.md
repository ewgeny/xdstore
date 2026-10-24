# Write-path design: journal-based commit (Phase W)

Status: **W-1..W-3 IMPLEMENTED (2026-10-19; W-3: `journalWrites` defaults ON)**. W-2 shipped as the per-transaction MATERIALIZATION BUDGET (`txMaterializedObjectsCap`, default 0=unlimited) — NOT record-cache eviction: eviction without disk-anchored scans silently drops rows from `load(Class)`/predicate scans and breaks snapshot reads (prev-versions are not on disk); disk-anchored scans are the follow-up design round (W-4). W-1 note: the first soak caught journaling the B+Tree's own node/tree/reference resources (they ride the generic XdStorageResource machinery) — journal-replaying canonical live graphs diverged from the canonical registry at reopen (XdStorageW2ReopenBTreeTest went red); fixed by the scope exclusion the design always mandated (`!cache.isInfrastructureCache() && !cache.isStructuralNodeCache()`). W-3 soak: full suite 655 tests journalized (2 failures = the documented flake families); benchmark honest verdict below. — journal append/replay/checkpoint behind the flag; W-2 cache bounds and W-3 default flip remain. Two implemented deviations from the sketch below (simpler and crash-safer): (1) the journal file is per-RESOURCE, in the JOURNAL SUBDIRECTORY of the resource's directory (`<dir>/journal/<base-without-ext>-journal.yaml` since JRNLNAME, docs/tasks/2.1.0/24; the 2.0.0-era stacked name and the pre-subdirectory sibling name are read-compatible and auto-migrate) rather than per-class — fragments journal independently, same semantics; (2) there is NO `journal.checkpoint` marker file — replay is idempotent (UPSERT/DELETE by id, last-writer-wins), so a crash anywhere between "base rewritten" and "journal deleted" is safe and the marker is unnecessary; checkpoint = existing full-file write + journal reset. Failed-commit recovery appends COMPENSATION records (the restored pre-tx state per changed id, taken from the prepareCommit changes collector) — deleting the journal would lose earlier un-checkpointed commits. Torn-tail policy: records after the LAST sentinel are discarded with a WARN (the interrupted commit was never reported durable) — unlike a truncated BASE file which fails loudly (S-1). (SRE-OPTIMIZATION-PLAN Phase W; supersedes
the parked D-3 "batching" item). One design round is mandatory before any code: this changes
crash/commit semantics, the hardest class of change in this engine.

## 1. The wall, quantified

Every commit rewrites the ENTIRE file of every touched resource — data fragment(s), id-index tree
file, search-index file — serially in phase 1 (`XdStorageResource.performFirstPhaseCommit` →
`writeObjects` → `XdStorageAtomicFile.writeReplacing`). The cost of one small-object commit is
O(size of every file it touches), and it is I/O on the commit's critical path:

| Benchmark (BENCHMARKS.md, 2026-10-18) | ops/s | dominant cost |
|---|---|---|
| save+delete indexed ship w/ crew (2 commits) | ~39 | 2 × full rewrite of fragment + id-index + search-index |
| update/rename search key (1 commit) | ~75 | full rewrite incl. key transfer |
| facade save+load+commit | ~127 | full rewrite of one fragment + index |
| loadById / indexed search (~480) | — | reads are fine; the wall is writes |

10× on writes ⇒ commit cost must become O(delta), not O(file).

## 2. Chosen design: per-class append journal + checkpointing

### 2.1 Layout

```
<folder>/data/<Class>/journal/<base>-journal.yaml  — redo log, append-only (JRNLNAME: the
                                                     journal/ subdirectory of the resource's
                                                     directory; same for structure/journal/)
<folder>/data/<Class>/<base>.yaml                  — base files (today's format, unchanged)
```

Implemented deviations from the sketch below: the per-resource journal lives in the
JOURNAL SUBDIRECTORY of its resource's directory (`data/<Class>/journal/`,
`structure/journal/`), named with a SUFFIX on the extension-less base
(`Order-1.yaml` → `journal/Order-1-journal.yaml`; JRNLNAME, docs/tasks/2.1.0/24 —
the 2.0.0-era stacked name `<base>.journal.yaml` and the pre-subdirectory sibling name
`<base>-journal.yaml` are both read-compatible and auto-migrate into the subdirectory at
the file's next replay), and there is NO `journal.checkpoint` marker file — replay is
idempotent (UPSERT/DELETE by id, last-writer-wins), so a crash anywhere between "base
rewritten" and "journal deleted" is safe and the marker is unnecessary; checkpoint =
existing full-file write + journal reset (deletion).

The base files and the existing full-file writer stay exactly as they are — they become the
*checkpoint artifact* instead of the per-commit artifact.

### 2.2 Commit path (phase 1 rewrite)

1. Serialize the transaction's changes for each touched class as redo records:
   `(commitSerial, txId, op=UPSERT|DELETE, id, payload)` — payload is the object body in the
   existing YAML object-block format; `DELETE` carries id only.
2. Append records to `journal.yaml` in commit-order priority (same order as today's resource
   locking), then **one fsync** (`durableWrites=false` → flush only, contract unchanged).
3. In-memory: apply to caches exactly as today (phase 2 untouched).
4. Truncate-on-success is NOT done per commit — the journal grows until checkpoint.

Failure semantics: an append interrupted mid-record leaves the journal without the EOF sentinel
(S-1) → on recovery the torn tail is discarded (base files remain authoritative — same guarantee
class as today's torn multi-file commit, now confined to the journal instead of data files).

### 2.3 Read/prepare path

`prepare()` = load base files (today's `readObjects`) **+ replay journal records with
`commitSerial > checkpointSerial`** (id-keyed, last-writer-wins — commit-serial ordering already
exists via R-45's `assignCommitSerial`). The reader stops at the sentinel; a torn tail is dropped
with a loud WARN naming the discarded serial range.

### 2.4 Checkpointing

- Trigger: journal bytes > threshold (default 64 MB) OR record count > threshold — checked after
  commit, executed by the storage's worker pool (never on the committing thread's critical path).
- Procedure: run today's full-file `writeObjects` for the class (existing writer, existing
  serialization guard R-43, atomic rename R-11) from the committed cache view; then atomically
  rename `journal.checkpoint` containing `{serial: maxAppliedCommitSerial}`; then truncate the
  journal to zero and rewrite the sentinel. Crash between steps is safe: worst case the next
  recovery replays records the base already covers — replay must be **idempotent** (UPSERT by id
  is; DELETE of an already-absent id is).
- The existing "empty resource = no file" semantics move to the checkpoint: a class whose base
  state is empty deletes its base files as today.

### 2.5 What this preserves

- 2PC shape (prepare/perform across resources) — the journal append *replaces* the phase-1 file
  rewrite, the phase-2 cache install is untouched.
- MVCC/serial semantics (R-45 single commit serial per tx drives journal ordering).
- S-1 sentinel + S-2 durable flags (the journal is just another resource file to them).
- The legacy migration story: a storage without a journal has checkpoint serial 0 and empty
  journal — reads behave exactly as today.

## 3. Alternatives considered (and why not chosen)

| Option | Verdict | Reason |
|---|---|---|
| (b) Group commit only: accumulate N txs → one full rewrite | rejected as the end state, kept as a fallback | ~N× amplification reduction but still O(file) latency spike per flush; no asymptotic fix; simplest to build (≈2 days) — the designated fallback if the journal stalls |
| (c) Copy-on-write fragment rotation (write new fragment file, drop old at checkpoint) | rejected | many small files per class, directory churn, breaks the one-file-per-resource contract the canonical registry and prepare() rely on |
| Binary journal (non-YAML) | deferred | faster but a second serializer to guard (R-43 snapshots, S-1 sentinel); YAML reuses the existing object-block writer verbatim — revisit only if profiled as the bottleneck |

## 4. Risk register

1. **Recovery correctness** — the journal is new persisted state. Gate: kill-9 mid-journal
   recovery tests (torn tail, checkpoint crash windows) + full suite.
2. **Idempotent replay under in-place infra mutations** — B+tree node graphs are NOT journalable
   as records; **scope: journal covers data + search-index classes only; id-index/B+tree infra
   keeps the full-file write-out** until structural versioning exists (Phase 3b's frozen core).
   This caps the win for index-heavy writes but keeps the tree out of the blast radius.
3. **Journal growth without checkpoints** (read-only workload appending forever) — size-threshold
   checkpoint bounds it; plus `journal.yaml` is a resource file, so S-5 sweeps its `.tmp`s.
4. **Threading** — appends serialize per class (the commit-order lock already serializes phase-1
   per resource today; unchanged).

## 5. Sequencing

1. **W-0 (prerequisite, DONE 2026-10-18):** S-1 sentinel + S-2 durable — the journal leans on both.
2. **W-1: DONE 2026-10-19** — journal writer/reader + prepare-replay behind a config flag
   (`journalWrites=false` default); tests: journal-only round-trip, delete/update replay,
   checkpoint reset, torn-tail discard, flag-off default, YAML-only validation
   (`XdStorageW1JournalTest`, 6/6). FULL-FILE write-out remains for id-index/search B+tree
   infra (unchanged, by design).
3. **W-2:** checkpointing + cache-bounds ship together (cache caps: cold committed-record LRU,
   re-loadable from base+journal on demand).
4. **W-3: DONE 2026-10-19** — `journalWrites` defaults ON (per-resource journal append lock is
   per-file, not JVM-wide). Soak: full suite 655 tests green with the journalized default (2
   failures = the pre-existing flake families, signatures unchanged). HONEST benchmark verdict
   (quick-mode): write scenarios UNCHANGED (~38 ops/s save+delete, ~73 rename) — the benchmark
   is index-heavy and id-index/search-index files still full-rewrite BY DESIGN SCOPE; the win
   applies to data-file-heavy write patterns and crash semantics (per-append atomicity +
   sentinel vs torn multi-file rewrites). The ≥10× acceptance target requires journaling the
   index paths — the deferred structural work (W-4 with disk-anchored scans).
