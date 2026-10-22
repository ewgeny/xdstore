# 2.1.0 train — work packages for Senior Java Developers

One markdown file = one GitHub issue. The frontmatter (`title`, `labels`) is read by
[`scripts/sync-issues.sh`](../../../scripts/sync-issues.sh); everything below it becomes
the issue body. Scope and priorities derive from
[MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1 (the 2.1.0 train) — read Track L / Track T
there for the full decision record before starting an item.

## Index

| # | File | Track | Labels | Est. | Depends on |
|---|---|---|---|---|---|
| 01 | [slf4j-migration](01-slf4j-migration.md) | L-0/L-1 | P0, logging, refactor | 1 d | — |
| 02 | [quiet-by-default-logging](02-quiet-by-default-logging.md) | L-2 | P0, logging, refactor | 0.5 d | 01 |
| 03 | [logging-build-guards](03-logging-build-guards.md) | L-3 | P0, logging, ci | 0.5 d | 01 |
| 04 | [jacoco-coverage-baseline](04-jacoco-coverage-baseline.md) | T-1 | P1, testing, ci, chore | 0.5 d | — |
| 05 | [tag-based-test-groups](05-tag-based-test-groups.md) | T-2 | P0, testing, refactor | 1 d | — |
| 06 | [test-gap-undercovered-packages](06-test-gap-undercovered-packages.md) | T-3a | P1, testing, tech-debt | 2 d | 04, 05 |
| 07 | [facade-audit-and-litter-cleanup](07-facade-audit-and-litter-cleanup.md) | T-3b | P1, testing, chore | 1 d | 04, 05 (06 partially) |
| 08 | [read-only-commit-path](08-read-only-commit-path.md) | B2 | P0, engine, performance, feature | 1 d | — |
| 09 | [query-range-operators](09-query-range-operators.md) | FP-1a | P0, search, engine, feature | 2 d | — |
| 10 | [query-order-limit-offset](10-query-order-limit-offset.md) | FP-1b | P1, search, engine, feature | 1–2 d | 09 |
| 11 | [jmh-regression-alert](11-jmh-regression-alert.md) | D-4 | P2, ci, performance, chore | 0.5–1 d | 13 (scenario list) |
| 12 | [decision-guide-and-example-app](12-decision-guide-and-example-app.md) | FP-11 | P1, docs, chore | 1 d | 09, 10 (describes shipped capabilities) |
| 13 | [benchmark-suite-unification](13-benchmark-suite-unification.md) | BENCH | P0, performance, ci, chore | 2 d | — (**ASAP: before 08–11 record/assert numbers**) |
| 14 | [uuid-generator-field-property](14-uuid-generator-field-property.md) | BUG-D | P2, engine, bug | 0.5 d | — (decision + small fix; discovered by 06) |

## Execution lanes (independent — can run in parallel)

- **Lane A (logging):** 01 → {02, 03}
- **Lane B (testing):** {04, 05} → {06, 07}
- **Lane C (engine/search):** **13 first (ASAP — the canonical benchmark suite; tasks 08/09/10
  record their numbers into it, 11 asserts on its scenario list)** → 08 ∥ 09 → 10
- **Lane D (docs, last):** 12 — after the query pack lands, so it describes what ships.

## Label glossary

| Label | Meaning |
|---|---|
| `2.1.0-train` | Work package of the 2.1.0 release train (all issues carry it) |
| `P0` / `P1` / `P2` | Must / should / nice-to-have land in 2.1.0 |
| `logging` `testing` `engine` `search` `docs` `ci` | Subsystem |
| `refactor` `feature` `chore` `tech-debt` `performance` | Work type |

## Working conventions (binding for every task)

- A run is judged by Maven's own `BUILD SUCCESS` / `Tests run: N, Failures: M` line — never by a
  piped grep's exit code ([FLAKY.md](../../FLAKY.md), the R-52 gotcha).
- Storage name + directory unique per test run; never gate test logic on `deleteDir` success.
- New tests follow the flake-ledger discipline: anything load-sensitive gets the `stress` tag
  (task 05 formalizes this) and lands in [FLAKY.md](../../FLAKY.md) if it flakes.
- User-facing changes update [CHANGELOG.md](../../../CHANGELOG.md) (`[Unreleased]` section —
  entries for 01, 02, 08, 09, 10 are already drafted there; adjust, don't re-invent).
- Performance claims in PRs cite [BENCHMARKS.md](../../BENCHMARKS.md) /
  [PERFORMANCE-RECOMMENDATIONS.md](../../PERFORMANCE-RECOMMENDATIONS.md) baselines; JMH numbers
  recorded as addenda, honestly (the W-3 precedent: report "unchanged" when it is unchanged).

## Automatic issue sync (GitHub)

Issues are managed automatically by the **issues-sync workflow**
([.github/workflows/issues-sync.yml](../../../.github/workflows/issues-sync.yml)): it runs on
**every push to `main`** (and on demand via its *Run workflow* button) and executes
[`scripts/sync-issues.sh`](../../../scripts/sync-issues.sh) — the same script runs locally
(`gh auth login && bash scripts/sync-issues.sh [--dry-run]`).

**Status marks — the developer's side.** Each task file's frontmatter carries a `status` mark:

- `status: open` *(default)* — the work package is open.
- `status: done` — set **by the developer** when the task's acceptance criteria are met and
  merged. The next push then closes the GitHub issue automatically (with a comment naming the
  file that marked it done).

**The sync's state machine** (idempotent; source of truth = the task files):

- It scans **every** `docs/tasks/<version>/` directory — a new train's directory is picked up
  automatically; the GitHub milestone = the directory name (attached at creation only if it
  exists). Task titles MUST carry the `[x.y.z]` train prefix: title matching is global across
  trains, the prefix is what prevents collisions.

| File says | GitHub says | Action |
|---|---|---|
| `open`, no issue | — | create the issue (labels + milestone `2.1.0` if it exists) |
| `open` | issue open | nothing — already in sync |
| `open` | issue closed | **WARN** — left closed (a machine never reopens; fix file or issue by hand) |
| `done`, no issue | — | create the issue, then close it (history preserved) |
| `done` | issue open | close it with an explanatory comment |
| `done` | issue closed | nothing — already in sync |

**Token setup (one-time).** The workflow authenticates with the repository secret
**`XDSTORAGE_ISSUES_TOKEN`** — *Settings → Secrets and variables → Actions → New repository
secret*: a token (fine-grained PAT or classic) with **Issues: read & write** on this
repository. The built-in `GITHUB_TOKEN` is deliberately not used. Locally the script accepts
the same name (`GH_TOKEN=…` takes precedence if both are set; `gh auth login` works too):

```bash
export XDSTORAGE_ISSUES_TOKEN=ghp_…   # or: gh auth login
bash scripts/sync-issues.sh --dry-run
```

Notes: matching is by **exact title** (fetched once per run, open + closed); feature branches
never publish or close issues (the workflow is gated to `main`); concurrent runs are
serialized by a workflow concurrency group; every run writes an action-count table to the job
summary. To preview locally without touching GitHub: `bash scripts/sync-issues.sh --dry-run`.

The milestone `2.1.0` is optional — create it in the repo UI (or
`gh api repos/:owner/:repo/milestones -f title=2.1.0`) before the first sync if you want
issues pre-assigned. Cross-links between issues should use `#N` numbers, which exist only
after creation — edit the first comment if you prefer issue-links over file links.
