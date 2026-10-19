# XdStorage — Master Roadmap

Compiled 2026-10-19, branch `xdstorage-2.0`. This document consolidates the existing plans into
one prioritized working plan and adds the two tracks no old plan covers: engineering additions
and **production/release preparation**. The detailed journals stay authoritative for their scope:

| Plan | Scope | State |
|---|---|---|
| [OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md) | Engine campaign journal R-1..R-53 + phased engine plan | Phases A/B/D-1/D-2 done; residuals tracked here |
| [SRE-OPTIMIZATION-PLAN.md](SRE-OPTIMIZATION-PLAN.md) | Whole-project SRE plan (Phases 0/S/W/O/C) | 0, S, O, C **DONE**; W-1..W-3 **DONE**; W-4 open |
| [WRITE-PATH-DESIGN.md](WRITE-PATH-DESIGN.md) | Phase W design (journal + checkpoint) | W-1..W-3 implemented; W-4 = the deferred design round |
| [CODEGEN-OPTIMIZATION-PLAN.md](CODEGEN-OPTIMIZATION-PLAN.md) | Wrapper codegen | Items 1/2/5 applied; 3/4 planned |
| [PERFORMANCE-RECOMMENDATIONS.md](PERFORMANCE-RECOMMENDATIONS.md) | Config study (2nd ed, post-R-53) | Recommendations APPLIED (t=64, frag=128); §5.3 open |
| [BENCHMARKS.md](BENCHMARKS.md) / [FLAKY.md](FLAKY.md) | Baselines / flake ledger | Current |

**Verified state (2026-10-19):** suite ~692 tests, green except the documented flake families;
working tree clean except the staged historical `XdStore.docx`; CI gate + nightly live; journal
writes default ON (W-3); engine defaults t=64 / frag=128; README + EMBEDDING.md current.

---

## 1. Frozen / parked items — inventory and thaw conditions

Everything below is deliberately NOT being worked on; each entry records why it is frozen and
what would justify thawing it. Do not re-attempt these blind — the evidence is in the linked
R-entries.

| # | Item | Why frozen | Thaw condition |
|---|---|---|---|
| F-1 | **Phase 3b structural MVCC of B+Tree topology** (+ the frozen cloner pass-through, R-19/R-20) | Canonical-registry compromise landed; topology versioning is the largest structural change in the engine | Only if R-45 residuals prove to be topology tears (see A1) |
| F-2 | **RW-lock redesign** (R-26/R-28/R-29 wait-rescheduling variants) | 3 variants rejected with evidence; in-source comments forbid blind re-attempts | Contention-removal design (B3), never wait-rescheduling |
| F-3 | **Record-cache eviction** (the original W-2 half) | Eviction without disk-anchored scans silently drops rows from scans / breaks snapshot prev-versions | W-4 design round (B1) ships disk-anchored scans first |
| F-4 | **D-3 batching** | Superseded by Phase W; fallback design recorded in WRITE-PATH-DESIGN.md §3 | Only if the journal approach stalls |
| F-5 | **JSON format** | Enum constant with no serializer | Decide at release: remove from the public enum or implement as a feature (A2 decision) |
| F-6 | **Test layout rename** (`src/main/test`) | Churn without operational payoff | Never (documented constraint) |
| F-7 | **JMX/Micrometer adapter** | Deliberately embedder-side (O-phase verdict) | Only on concrete embedder demand |
| F-8 | **R-35/R-33 second leak path** (outside B+Tree retry loops) | Unidentified; watchdog + correlation toolkit ready | If leak events re-appear in A1 dumps or nightly logs |

---

## 2. The general working plan, by priority

### Track A — Correctness residuals (release blockers) — P0

**A1. Resolve the residual flake family before calling 2.0 stable** (~1–2 sessions).
The facade-storm flake ("рассинхрон структуры") is no longer isolated-green (A/B ×2 on clean
HEAD, FLAKY.md) — the R-32 validator's two traversals are the prime suspect, not the engine.
Plan: one dedicated dumps session (the in-JVM watchdog + the R-37 correlation recipe), then
either fix the validator (make its two traversals atomic / sample-consistent) or prove engine
involvement and fix that. Same session: run GraphPhantom ×11 isolated with dumps for the ~1/11
residual (R-45). Gate: nightly ×3 clean twice in a row, FLAKY.md rows updated, families either
fixed or moved to enforced `-Pquick-gate`-style quarantine with a documented rate.

**A2. Public-API surface freeze decisions** (half session, cheap, must precede the 2.0.0 tag):
- JSON enum constant: remove or implement (F-5) — a public enum is API.
- On-disk format compatibility: verify what `structure/*.yaml` records today; add a format
  version marker + a startup INFO line ("opened storage X, format vN") if absent; document the
  compatibility matrix (pre-sentinel 1.x folders → `acceptMissingEofSentinel(true)`; journal
  files are additive and safely absent on old folders).
- Add `XdStorage.getVersion()` (or constant) — embedders and bug reports need it.

### Track B — Performance completion (the remaining big levers) — P1

**B1. W-4 design round, then implement** (3–5 sessions, the same risk class as W — design doc
first). Scope: journal the id-index/search-index paths (the benchmark's honest verdict: the ≥10×
write target needs this) + **disk-anchored scans**, which unlock F-3 (record-cache eviction) and
remove the heap≈DB ceiling. Acceptance criteria already written in WRITE-PATH-DESIGN.md §5 and
SRE-OPTIMIZATION-PLAN.md Phase W gate.

**B2. Read-only transaction commit path** (0.5–1 session, quick win). PERF study §5.3: a
begin→load→commit cycle costs ~25 ms/op at N=5000 on the Hash path with **zero file write-outs**
— commit-protocol CPU scaling with resource size. Fix: skip 2PC record iteration for
no-change transactions. The study names it the top candidate for read-heavy workloads.

**B3. R-29 contention removal** (focused sessions, after B1 or in parallel on a quiet branch).
Per-node striping / shallower deep-lock chains / FIFO ticket latches for tree-node latching —
the only approaches not yet rejected (F-2). Drives the remaining stress flakes toward zero and
raises concurrent-writer throughput.

### Track C — Codegen cold start — P2

**C1. Batch-compile the entity graph in one javac invocation** (CODEGEN item 3; M effort, low
risk). Mainly benefits short-lived processes. **C2 (wrapper persistence across JVMs, item 4)
stays deferred** — high staleness risk, only after C1, and probably never for 2.0.

### Track D — Engineering additions (new, not in any old plan) — P2/P3

| # | Item | Why | Effort |
|---|---|---|---|
| D-1 | **JaCoCo coverage report** in CI (site/artifact, no gate at first) | 692 tests, zero coverage visibility; informs where Track B/C hardening pays | S |
| D-2 | **JDK 21 matrix job** in CI (library bytecode stays release=8) | Dev runtime is 17; 21 is already the perf-study runner — test both | S |
| D-3 | **Dependabot** (or weekly `versions:display-dependency-updates` job) | log4j 2.11→2.23 history is the argument; pin, don't chase | S |
| D-4 | **JMH regression alert**: nightly smoke stores per-scenario numbers; fail/warn on >25% drop vs the 7-day baseline | BENCHMARKS.md is manual today; the nightly already runs the smoke — persist and compare | M |
| D-5 | Commit the staged `XdStore.docx` (historical 1.x docs) | It is sitting staged; closes the H-4 note | XS |

### Track E — Production & release preparation (the ADDITION) — P0 for the release train

Nothing release-shaped exists today: no LICENSE, no CHANGELOG, no `scm`/`url`/`distributionManagement`
in the pom, no release workflow, version is bare `2.0`.

| # | Item | What |
|---|---|---|
| E-1 | **Project meta** | LICENSE (pick one — MIT/Apache-2.0), CHANGELOG.md (backfill from the plan journals: R-1..R-53 highlights, S/W/O/C deliverables), pom `<name>/<description>/<url>/<scm>/<licenses>` |
| E-2 | **Build-for-release hygiene** | `maven-source-plugin` + `maven-javadoc-plugin` (facade API only) bound to a `release` profile; `maven-enforcer-plugin` (require JDK 17+ build, dependency bounds); reproducible builds (`project.build.outputTimestamp`); semver version `2.0.0` |
| E-3 | **Distribution channel** — decide | (a) Maven Central via Sonatype OSSRH (the standard for a library: groupId ownership, GPG signing, sources+javadoc jars required) or (b) GitHub Packages (simpler, no groupId verification, consumer needs auth). Recommendation: Central for 2.0.0; GitHub Packages as the fallback. Add `distributionManagement` + credentials via CI secrets |
| E-4 | **Release workflow** (`.github/workflows/release.yml`) | Trigger: tag push `v*`. Steps: full gate (BUILD SUCCESS verdict, per FLAKY.md convention) → JMH smoke row assertion → `mvn deploy` with signing → GitHub Release with CHANGELOG section + surefire/JMH artifacts attached |
| E-5 | **Pre-release verification protocol** (one session, repeatable) | (1) full suite ×3 green on the release candidate; (2) kill -9 mid-commit recovery test + torn-journal recovery (the W-1 suite covers — run it against the RC); (3) `durableWrites=true` spot benchmark recorded in BENCHMARKS.md; (4) 1.x-folder migration walkthrough (`acceptMissingEofSentinel`) documented in EMBEDDING.md; (5) JMH comparison vs BENCHMARKS.md baselines (no unexplained regression); (6) fresh-clone README walkthrough (the C-4 gate, re-run) |
| E-6 | **Compatibility & support statement** | EMBEDDING.md section: supported JDK runtimes (8-bytecode / 17+-dev), storage-folder compatibility matrix (see A2), flake ledger link, upgrade path 1.x→2.0, and the deprecation policy for the on-disk format |

**Release-train decision (recommended):** ship **2.0.0 after Track A + E-1..E-6** — do NOT wait
for Track B (performance) or C; those become the 2.1 train. The engine is in the best verified
state of its history; the remaining performance work is additive and flag/config-scoped.

---

## 3. Sequencing

| Order | Work | Est. | Depends on |
|---|---|---|---|
| 1 | D-5 (commit docx) + A2 (API freeze decisions, format version) | 0.5–1 sess | — |
| 2 | E-1 + E-2 (LICENSE, CHANGELOG, pom meta, release profile) | 0.5–1 sess | A2 (JSON decision) |
| 3 | A1 (flake dumps + fix/verdict) | 1–2 sess | — (parallel with 2) |
| 4 | D-1..D-4 (CI additions) | 1 sess | — (parallel) |
| 5 | B2 (read-only commit path quick win) | 0.5–1 sess | — |
| 6 | E-3 + E-4 (channel + release workflow) | 1 sess | E-2 |
| 7 | E-5 + E-6 (RC protocol + compat statement) → **tag 2.0.0** | 1 sess | A1 done, nightly clean ×2 |
| 8 | B1 (W-4 design → implementation) | 3–5 sess | after 2.0.0 (or parallel on a branch) |
| 9 | C1 (codegen batch compile), B3 (contention removal) | 1–2 sess each | 2.1 train |

---

## 4. Explicitly out of scope (carried over — do not re-propose)

Structural MVCC without a W-4-class design round (F-1); the rejected RW-lock waiting variants
(F-2); JSON implementation if A2 removes it (F-5); test-directory rename (F-6); JMX adapter
(F-7); caching equals/toString in wrappers and ASM bytecode generation (CODEGEN items 7/8,
rejected with rationale).
