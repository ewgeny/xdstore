---
title: "[2.1.0][T-2] Formal test groups: @Tag(stress/integration) replace filename-based excludes, enforced by a lint test"
labels: [2.1.0-train, P0, testing, refactor]
status: open
---

## Summary

Replace the filename-based exclusion lists (`**/*StressTest.java`, `XmlDataStorageMultithreadsTest`,
`integration/**`) in the quick-gate and release profiles with explicit JUnit 5 tags
`@Tag("stress")` / `@Tag("integration")`, and add a lint test that makes the naming↔tag
contract unbreakable.

## Why

Today grouping is implicit: a class is excluded from the release gate only if someone remembered
the naming convention — duplicated exclusion lists live in two profiles and in
[RELEASE-PLAN.md](../../RELEASE-PLAN.md) prose. A misnamed stress test silently joins the
release gate. Ground truth (2026-10-21): 160 test classes, **zero** `@Tag`/`@Category` usage;
JUnit 5 dominates (154 classes vs 2 JUnit-4-only + 1 mixed) — tags are viable with a tiny
vintage cleanup. Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-2.

## Scope

1. **Annotate** every currently-excluded class with `@Tag("stress")` or `@Tag("integration")`:
   all `*StressTest` classes (incl. the `/performance` package's stress families —
   BTreeStress, DeleteIsolation, GraphPhantom, FacadeGiantArraySplit, GiantGraphPurge,
   NestedTransactionIsolation, PhantomReadIsolation, ResourceCache*Stress, StressTest),
   `XmlDataStorageMultithreadsTest`, and the `integration` package classes.
2. **Vintage cleanup** — migrate the 2 JUnit-4-only classes + 1 mixed-vintage class to JUnit 5
   (small: `junit-jupiter` is already a dependency) so tag semantics are uniform.
3. **Profiles** — quick-gate and release: replace `<excludes>` filename patterns with
   `<excludedGroups>stress,integration</excludedGroups>`; **keep the filename patterns as a
   backstop** for 2.1.0 (removal scheduled for 3.0 — one release cycle of belt-and-suspenders).
4. **Lint test** (plain JUnit 5 test, part of the default run): walks `src/main/test` and
   asserts the pairing in both directions —
   - class named `*StressTest` ⇒ has `@Tag("stress")`,
   - class with `@Tag("stress")` or `@Tag("integration")` in the default-run set ⇒ named per
     convention or lives in `integration/`.
   Source-walking (`Files.walk` over the test source root, resolved relative to the working
   directory — works locally and in CI checkouts) is acceptable; a broken pairing fails with a
   message naming the file.
5. **Codify the contract** — [FLAKY.md](../../FLAKY.md) and [RELEASE-PLAN.md](../../RELEASE-PLAN.md)
   § Quality bar updated: "a stress/integration class is BOTH named per convention AND tagged."

## Out of scope

- Directory/package moves (F-6 frozen — tags, not layout).
- Re-trying excluded families in the gate (quarantine policy unchanged).

## Acceptance criteria

- [ ] `-Pquick-gate` and `-Prelease` run **exactly the same test set as before** the change
      (compare surefire totals: gate was ~677 of 714 — prove equality, don't assume).
- [ ] The full suite and nightly are unchanged.
- [ ] The lint test fails on a deliberately mispaired class (verify locally, then revert).
- [ ] FLAKY.md + RELEASE-PLAN updated; the vintage classes migrated.
- [ ] `mvn clean test` green (BUILD SUCCESS verdict).

## Verification

```bash
mvn clean test                                   # full suite + lint test
mvn clean test -Pquick-gate                      # count == pre-change count
mvn clean package -Prelease                      # count == 677-equivalent set
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-2
- [FLAKY.md](../../FLAKY.md) · [RELEASE-PLAN.md](../../RELEASE-PLAN.md) § Quality bar

Estimate: ~1 dev-day.
