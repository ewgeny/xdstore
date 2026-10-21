---
title: "[2.1.0][T-1] JaCoCo coverage baseline in CI: measure, publish, no percentage gate"
labels: [2.1.0-train, P1, testing, ci, chore]
status: open
---

## Summary

Introduce JaCoCo, publish the report as a CI artifact, and produce the **first coverage map**
of the project (714 tests over ~180 main classes). The map is the instrument that scopes the
gap-filling tasks (06, 07) — it is explicitly NOT a threshold gate.

## Why

The suite is large but coverage has never been measured — nobody knows which packages are dark
(ground truth: `operation` and `updaters` have zero test classes; `structure`, `trigger`,
`observing`, `lock`, `object`, `index` have 1–3 each). The project's honest-metrics culture
applies to coverage too: measure and publish, never game a percentage. Decision record:
[MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-1 (D-1 absorbed into it).

## Scope

1. **pom.xml** — `jacoco-maven-plugin` (current stable): `prepare-agent` + `report` bound to
   the test/verify phase. Always-on (no extra profile) unless it measurably slows the gate —
   if it does, gate it behind a profile and document.
2. **CI** — `.github/workflows/ci.yml` gate job: upload `target/site/jacoco/` (HTML + jacoco.xml)
   as a workflow artifact alongside the surefire reports.
3. **The baseline map** — one table in the PR description: per-package line/branch coverage
   (`io.github.xdstorage.*` at top level), explicitly calling out:
   - packages with zero/near-zero coverage (operation, updaters, structure, trigger,
     observing, lock, object, index),
   - the god classes (`XdStorage`, `XdStorageBTreeNode`, `XdStorageResourceCache`-family,
     search manager/resources),
   - codegen (`code` package) and serialization.
4. Record the numbers where the follow-up tasks can link them (PR + a short note in this
   task's issue after merge — no separate docs page yet; decide with the team whether a
   `docs/COVERAGE.md` earns its keep).

## Out of scope

- Any check-rules/threshold enforcement (explicitly forbidden for this train).
- Writing the missing tests (tasks 06/07).
- Module restructuring to game numbers.

## Acceptance criteria

- [ ] `mvn clean verify` produces the JaCoCo HTML report locally and in CI.
- [ ] CI artifact upload visible on the gate job.
- [ ] Baseline table in the PR with the packages listed above (numbers, not adjectives).
- [ ] Gate runtime impact measured and reported (suite time before/after on the same machine).
- [ ] No build failure is possible from coverage numbers (verified: a deliberately
      uncovered class changes nothing about the verdict).

## Verification

```bash
mvn clean verify
open target/site/jacoco/index.html
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-1
- Tasks 06, 07 (consumers of the map)

Estimate: ~0.5 dev-day.
