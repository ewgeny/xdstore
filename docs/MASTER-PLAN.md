# XdStorage — Master Roadmap

Compiled 2026-10-19; **updated 2026-10-20** to integrate the architect's evaluation
([ARCHITECT-EVALUATION.md](ARCHITECT-EVALUATION.md)): Track A is closed (R-54..R-59), the FP
feature plan (FP-0..FP-12) is adopted as Track F, codegen Track C is superseded by FP-3, and the
sequencing below is the merged 5-step roadmap. Detailed journals stay authoritative per scope:

| Plan | Scope | State |
|---|---|---|
| [OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md) | Engine campaign journal R-1..R-59 | Phases A/B/D done; **Track A family FIXED (R-54..R-58)**; R-59 SOE fix |
| [SRE-OPTIMIZATION-PLAN.md](SRE-OPTIMIZATION-PLAN.md) | Whole-project SRE plan (Phases 0/S/W/O/C) | 0, S, O, C **DONE**; W-1..W-3 **DONE**; W-4 open |
| [WRITE-PATH-DESIGN.md](WRITE-PATH-DESIGN.md) | Phase W design (journal + checkpoint) | W-1..W-3 implemented; W-4 = the deferred design round |
| [CODEGEN-OPTIMIZATION-PLAN.md](CODEGEN-OPTIMIZATION-PLAN.md) | Wrapper codegen | Items 1/2/5 applied; **items 3/4 superseded by FP-3 (AOT)** |
| [PERFORMANCE-RECOMMENDATIONS.md](PERFORMANCE-RECOMMENDATIONS.md) | Config study (2nd ed, post-R-53) | Recommendations APPLIED (t=64, frag=128); §5.3 open (= B2) |
| [ARCHITECT-EVALUATION.md](ARCHITECT-EVALUATION.md) | Outside-architect review, competitive comparison, FP-0..FP-12 feature plan | Adopted; FP-0..FP-12 tracked here as Track F |
| [BENCHMARKS.md](BENCHMARKS.md) / [FLAKY.md](FLAKY.md) | Baselines / flake ledger | Current; **one open row** (BTreeStress contention → B3) |

**Verified state (2026-10-20):** suite ~706 tests green (commits `30c1e90` Track A fixes
R-54..R-58, `333e864` 13 regression pins, `4313b30` R-59 StackOverflowError fix, `423baf1`
evaluation); working tree clean; CI gate + nightly live; journal writes default ON (W-3);
engine defaults t=64 / frag=128. The facade-storm, GraphPhantom, and Xml purge flake families
are **FIXED** (10/10 all-stress gate); the single open flake row is the BTreeStress
contention family (Track B3, full-suite-load only).

---

## 1. Frozen / parked items — inventory and thaw conditions

Everything below is deliberately NOT being worked on; each entry records why it is frozen and
what would justify thawing it. Do not re-attempt these blind — the evidence is in the linked
R-entries.

| # | Item | Why frozen | Thaw condition |
|---|---|---|---|
| F-1 | **Phase 3b structural MVCC of B+Tree topology** (+ the frozen cloner pass-through, R-19/R-20) | Canonical-registry compromise landed; topology versioning is the largest structural change in the engine | Only if W-4 (Step 2) evidence proves topology tears remain |
| F-2 | **RW-lock redesign** (R-26/R-28/R-29 wait-rescheduling variants) | 3 variants rejected with evidence; in-source comments forbid blind re-attempts | Contention-removal design (B3), never wait-rescheduling |
| F-3 | **Record-cache eviction** (the original W-2 half) | Eviction without disk-anchored scans silently drops rows from scans / breaks snapshot prev-versions | W-4 design round (Step 2) ships disk-anchored scans first |
| F-4 | **D-3 batching** | Superseded by Phase W; fallback design recorded in WRITE-PATH-DESIGN.md §3 | Only if the journal approach stalls |
| F-5 | ~~**JSON format**~~ — **DECIDED at Step 0 (2026-10-20): the enum constant is REMOVED in 2.0.0** — it silently wrote XML content into `.json` files; real JSON support, if demand appears, is a Step-4 feature (SPI implementation, no enum lie) | Closed | Implement JSON as a real format on demand (Step 4); a 1.x `.json` folder is an XML folder — migration recipe in EMBEDDING.md |
| F-6 | **Test layout rename** (`src/main/test`) | Churn without operational payoff | Never (documented constraint) |
| F-7 | **JMX/Micrometer adapter** | Deliberately embedder-side (O-phase verdict) | FP-8 (`xdstorage-micrometer` module) — build it only on a concrete demand signal (Step 4) |
| F-8 | **R-35/R-33 second leak path** (outside B+Tree retry loops) | Unidentified; watchdog + correlation toolkit ready | If leak events re-appear in nightly logs or B3 dumps |

**Superseded (was planned, no longer the plan):** Track C codegen items — C1 batch-compile and
C2 wrapper persistence are **superseded by FP-3 AOT wrapper generation** (build-time generation
removes cold-start javac cost and the JRE/`./gencode` landmines in one move; runtime codegen
stays as fallback). See ARCHITECT-EVALUATION.md FP-3.

---

## 2. The working plan — merged 5-step roadmap

Track statuses: **A done** (A1 fixed R-54..R-58/R-59; A2 folded into Step 0), **B open**
(absorbed into Steps 1–2), **C superseded** (FP-3, Step 3), **D open** (small items slotted
into Steps 0–2), **E open** (= Step 0), **F new** (the FP feature plan, Steps 1–4).

### Step 0 — Make it adoptable at all → **tag 2.0.0** (≈1–2 sessions) — NEXT

The release train: Track E in full + the A2 API-freeze decisions. Nothing here waits on
anything anymore (Track A is done).

**Status 2026-10-20 (release-engineering prep landed):** A2 DONE (JSON removed, format marker
`.xdstorage.meta` + startup INFO + loud newer-format rejection, `getVersion()`), E-1 DONE
(LICENSE Apache-2.0, CHANGELOG.md, semver `2.0.0`, pom metadata), E-2 DONE (`release` profile:
sources/javadoc/GPG/enforcer/reproducible builds), E-4 DONE (`release.yml`, activation = CI
secrets), E-6 DONE (EMBEDDING.md § Compatibility & support statement), D-3 DONE (Dependabot).
E-3 is scaffolding-complete (Central Portal plugin, server id `central`) — activation needs the
namespace registration + secrets; E-5 is documented in [RELEASE-PLAN.md](RELEASE-PLAN.md) and
executes at RC time. What remains to tag 2.0.0 is the §1 checklist of RELEASE-PLAN.md.

| # | Item | What |
|---|---|---|
| A2 | **API surface freeze** — **DONE** | JSON enum constant REMOVED (F-5 decision above); on-disk format version marker `.xdstorage.meta` + startup INFO ("created/opened storage X, format vN") + newer-format loud rejection; compatibility matrix in EMBEDDING.md; `getVersion()` on `IXdFileStorage` + `XdStorageProvider.getVersion()` |
| E-1 | **Project meta** — **DONE** | LICENSE (**Apache-2.0**), CHANGELOG.md backfilled from the R-journals, pom `<name>/<description>/<url>/<scm>/<licenses>`; semver `2.0.0` |
| E-2 | **Build-for-release hygiene** — **DONE** | `release` profile: source + javadoc plugins (doclint off at 2.0.0, tighten later), GPG signing, enforcer (JDK 17+/Maven 3.6.3+ always-on; no-SNAPSHOT-deps + no-duplicate-versions in release), reproducible builds (`project.build.outputTimestamp`) |
| E-3 | **Distribution channel** — scaffolding DONE, activation open | Maven Central via the Central Portal (`central-publishing-maven-plugin`, server id `central`; supersedes the legacy OSSRH plan for a first-time publisher); needs namespace verification + CI secrets |
| E-4 | **Release workflow** — **DONE** | `.github/workflows/release.yml`: tag push `v*` → version/tag sanity → full gate (BUILD SUCCESS verdict) → JMH smoke row assertion → signed `mvn -Prelease deploy` → GitHub Release with the CHANGELOG section + artifacts |
| E-5 | **Pre-release verification protocol** — documented, executes at RC | See RELEASE-PLAN.md §1/§2: suite ×3 green; kill-9 + torn-journal recovery vs the RC; `durableWrites=true` spot benchmark; 1.x migration walkthrough (`acceptMissingEofSentinel` + the JSON recipe); JMH vs BENCHMARKS.md baselines; fresh-clone README walkthrough |
| E-6 | **Compatibility & support statement** — **DONE** | EMBEDDING.md § Compatibility: JDK matrix, on-disk format compatibility matrix, JSON migration recipe, semver/deprecation policy, flake-ledger + benchmark-regression disclosure rules |
| D-3 | Dependabot — **DONE** | `.github/dependabot.yml` (maven + github-actions, weekly); log4j 2.11→2.23 history is the argument; pin, don't chase |
| D-5 | ~~Commit the staged `XdStore.docx`~~ | **DONE** (historical docs committed) |

*Exit criterion (from the evaluation): a stranger can add the dependency from Central and read
a license.*

### Step 1 — Quick wins on the two biggest axes (≈1–2 sessions)

1. **B2 — read-only commit path** (PERF study §5.3, FP-4's quick win): skip 2PC record
   iteration for no-change transactions; ~25 ms → near-zero per read tx at N=5000 on the Hash
   path. Small, safe, the top read-heavy lever.
2. **FP-1 — query power pack, phase 1** (`GT/GTE/LT/LTE/BETWEEN/IN` primaries, `ORDER BY`,
   `LIMIT/OFFSET`): the B+tree primary index is already ordered — range scans and ordered
   iteration are nearly free, and today their absence forces O(N) scans. Acceptance: JMH shows
   range queries scaling sub-linearly vs the scan they replace; boundary-semantics regression
   tests (nulls, mixed types); README query section updated.
3. **FP-11 — decision-guide page** (comparison vs MapDB/Xodus/RocksDB/Chronicle/EclipseStore +
   "when to choose XdStorage"): publish the evaluation's §3/§4 as docs pages; add a runnable
   example app; state the design point (10⁴–10⁵ objects/class, ≤10²–10³ writes/s) until Step 2
   ships. Also fixes the README test-count staleness.
4. **D-1 — JaCoCo coverage report** in CI (artifact, no gate at first) and **D-4 — JMH
   regression alert** (nightly smoke persists per-scenario numbers; warn on >25% drop) slot in
   here cheaply.

### Step 2 — The engine's 2.x maturity (3–5 sessions, design round first)

1. **B1 / FP-4 / F-3 thaw — W-4 design round → index journaling + disk-anchored scans +
   record-cache eviction.** Removes the heap ≈ DB ceiling and the index-write amplification;
   the ≥10× write acceptance target lives here. The project's own rule holds: mandatory design
   doc, kill-9 recovery tests, honest benchmark verdicts — do not let Step 1 enthusiasm pull
   it forward.
2. **B3 — lock striping / shallower deep-lock chains** (R-29 contention removal; the only
   approaches not yet rejected — F-2). Now also closes the **last open FLAKY.md row**
   (BTreeStress anomalies under full-suite load).
3. Gate: nightly ×3 clean, JMH write scenarios ≥10× vs the 2026-10-18 baseline, zero new flake
   ledger rows.

### Step 3 — Deployment story (2–3 sessions)

1. **FP-3 — AOT wrapper generation** (Maven plugin first, Gradle later): build-time wrapper
   compilation into the consumer jar; runtime codegen preserved as fallback; **loud** startup
   failure when neither path is available (kills the silent-JRE degradation). Unlocks JRE-only
   containers, app-server redeploys, Graal native-image, and removes the cold-start javac cost
   (supersedes Track C). Acceptance: JRE-only container runs the demo without `./gencode`;
   cold-start first-save measured before/after in BENCHMARKS.md.
2. **FP-5 — backup & verify API**: `storage.backupTo(folder)` (consistent point-in-time copy —
   MVCC freeze-copy) + `verify()` integrity walk (sentinels, index-vs-data consistency).
3. **FP-6 — admin/inspection CLI** (`xdstorage-tools.jar`): `inspect` / `dump` / `verify` /
   `compact`; shares FP-5's verification core; operates on a folder or an FP-5 backup (the
   single-writer lock stays intact). Doubles as the support/diagnosis story.

### Step 4 — Ecosystem train (2.x→3.0, demand-driven)

Each item independently shippable; **build only on a concrete demand signal** (a GitHub issue,
an embedder) — the frozen-items inventory exists precisely to prevent speculative growth.

- **FP-2** `explain()` for queries (plan + records visited + elapsed; P1)
- **FP-7** Spring Boot starter (autoconfiguration, config properties, lifecycle bean; P2)
- **FP-8** `xdstorage-micrometer` module (amends F-7; P2)
- **FP-9** binary compact format as non-default SPI (post-W-4, if profiling justifies; P2)
- **FP-10** encryption-at-rest SPI (P3)
- **FP-12** native-image + JDK 21 virtual-threads CI jobs (extends D-2; meaningful the day
  FP-3 lands)

---

## 3. Sequencing summary

| Order | Work | Effort | Unlocks | Release |
|---|---|---|---|---|
| 0 | Step 0: A2 + E-1..E-6 + D-3 → **2.0.0** — **prep landed 2026-10-20; remaining = RELEASE-PLAN.md §1 (namespace, secrets, RC, tag)** | 1–2 sess | Everything public | **2.0.0** |
| 1 | B2 + FP-1 (phase 1) + FP-11 + D-1/D-4 | 1–2 sess | Query expressiveness; read-heavy workloads | 2.0.x / 2.1 |
| 2 | W-4 (B1/F-3) + B3 | 3–5 sess | Large datasets; ≥10× writes; zero stress flakes | 2.1 |
| 3 | FP-3 AOT + FP-5/FP-6 ops tooling | 2–3 sess | JRE/native-image deployments; operability | 2.2 |
| 4 | FP-2/7/8/9/10/12 ecosystem train | 1–2 sess each | Mainstream adoption surface | 2.x→3.0 |

**Release-train decision:** 2.0.0 ships at Step 0 — the correctness bar is already met (suite
~706 green, Track A closed, one tolerated full-suite-load flake row tracked for B3). Steps 1–3
are the 2.1/2.2 trains; Step 4 is demand-driven.

## 4. Explicitly out of scope (carried over — do not re-propose)

Structural MVCC without a W-4-class design round (F-1); the rejected RW-lock waiting variants
(F-2); JSON implementation if A2 removes it (F-5); test-directory rename (F-6); JMX adapter
outside the FP-8 module shape (F-7); caching equals/toString in wrappers and ASM bytecode
generation (CODEGEN items 7/8); and — per the evaluation, consistent with the project's own
verdicts — network/server mode, multi-process write access, SQL layer, full-text engine.
