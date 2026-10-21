# 2.1.0 train — work packages for Senior Java Developers

One markdown file = one GitHub issue. The frontmatter (`title`, `labels`) is read by
[`scripts/publish-issues.sh`](../../../scripts/publish-issues.sh); everything below it becomes
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
| 11 | [jmh-regression-alert](11-jmh-regression-alert.md) | D-4 | P2, ci, performance, chore | 0.5–1 d | — |
| 12 | [decision-guide-and-example-app](12-decision-guide-and-example-app.md) | FP-11 | P1, docs, chore | 1 d | 09, 10 (describes shipped capabilities) |

## Execution lanes (independent — can run in parallel)

- **Lane A (logging):** 01 → {02, 03}
- **Lane B (testing):** {04, 05} → {06, 07}
- **Lane C (engine/search):** 08 ∥ 09 → 10 ∥ 11
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

## Publishing to GitHub

Prerequisites: `gh` CLI installed and authenticated (`gh auth login`), pushed branch.

```bash
bash scripts/publish-issues.sh
```

The script is idempotent (skips exact-title matches), creates the labels if missing, and
attaches the GitHub milestone `2.1.0` **if it exists** — create it in the repo UI (or
`gh api repos/:owner/:repo/milestones -f title=2.1.0`) before publishing if you want issues
pre-assigned. Review each issue after import: cross-links between issues should use `#N`
numbers, which only exist after creation — replace the file-reference links in the first
comment if you prefer issue-links.
