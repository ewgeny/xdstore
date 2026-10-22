---
title: "[2.1.0][T-2] Formal test groups: @Tag(stress/integration) replace filename-based excludes, enforced by a lint test"
labels: [2.1.0-train, P0, testing, refactor]
status: done
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

- [x] **`-Prelease` runs exactly the same test set as before the change** — proven, not assumed:
      before = 702 tests / 128 classes, after = 702 + the 3 resurrected vintage tests, with the
      class set byte-identical apart from them (see Implementation notes). **`-Pquick-gate`
      deliberately keeps its 4-name excludes** — deviation from scope item 3, rationale below.
- [x] The full suite and nightly are unchanged in semantics: 743 tests green (739 + the 3
      resurrected vintage tests + the lint test; the skipped set is unchanged).
- [x] The lint test fails on a deliberately mispaired class (verified locally in BOTH
      directions — a stress tag on a non-`*StressTest` class and an untagged `*StressTest`
      class, each reported with the file name and the rule), then reverted; lint green after.
- [x] FLAKY.md + RELEASE-PLAN updated; the vintage classes migrated; `junit:junit` 4.13.2
      removed from the pom.
- [x] `mvn clean test` green (BUILD SUCCESS verdict, 743 tests / 0 failures).

## Implementation notes

- **Tags**: 18 `*StressTest` classes + `XmlDataStorageMultithreadsTest` → `@Tag("stress")`;
  both `integration` package classes → `@Tag("integration")`. The lint test
  (`XdStorageTestGroupingContractTest`, part of the default run) enforces the pairing in both
  directions; it carries one grandfathered naming exception with rationale in source
  (`XmlDataStorageMultithreadsTest` — the name is frozen by the FLAKY.md ledger and the pom
  backstop patterns), and its own source assembles the tag literals from pieces so the
  reverse-direction scan never matches itself.
- **Vintage discovery — the two JUnit-4 classes were silently dead**: `MapStoringTest` and
  `EnumStoringTest` (plus the mixed-vintage marker in the latter) never executed — the build has
  `junit-jupiter-engine` but no `junit-platform-vintage-engine`, so the JUnit Platform provider
  skipped them silently. The migration to JUnit 5 (annotations + assertions, plus R-50-unique
  storage names/temp dirs — they shared a fixed `./teststorage`) **resurrects them into every
  run**: full suite 739 → 743 (+1 Map test, +2 Enum tests, +1 lint test). This is the one
  intentional delta to "unchanged suite"; `junit:junit` 4.13.2 left the pom.
  `XmlDataStorageMultithreadsTest` lost its JUnit-4 `@FixMethodOrder(MethodSorters.JVM)` — JVM
  reflection order was never a guarantee; JUnit 5's deterministic default applies.
- **quick-gate deviation (scope item 3)**: the profile's contract is "documented flaky families
  only" — 4 named classes — which the two STRUCTURAL tags cannot express: tagging everything
  `stress` would pull 16 non-flaky stress classes out of the dev-loop gate and change its set
  (733/145 before). The release profile is the tag-driven one since 2.1.0; quick-gate's
  mechanism is reconsidered together with the 3.0 backstop removal (a dedicated flaky tag would
  be the way). Documented in the pom profile comment and FLAKY.md conventions.
- **T-3 input found during the walk** (out of scope here, filed for the gap-fill/litter audit):
  3 stray source files whose `package` declaration belongs to another directory's copy of the
  same FQCN (`index/XdStorageHashIndexRecordTest`, `structure/XdStorageUpdateStructureRecordTest`,
  `idgeneration/XdStorageSubGeneratorsTest` — the compiled winners live in `index/hash/`,
  `structure/update/`, `code/`), plus non-test scratch classes matching `*Test.java`
  (`temp/SpecificTest`, `code/SimpleCodeGeneratorTest`, `code/IdObservableCodeGeneratorTest`,
  `code/UnmodifiableCodeGeneratorTest` — `main()`-only) and an empty
  `idgeneration/XdStorageDefaultIdGeneratorTest`.

## Verification

```bash
mvn clean test                      # 743 tests, 0 failures — full suite + lint test
mvn clean test -Pquick-gate         # 737 = before(733) + 4 (Map + Enum + lint); class set + those 3 classes only
mvn clean verify -Prelease -Dgpg.skip=true   # 706 = before(702) + 4; class set + those 3 classes only
```

Set equality was checked by diffing the sorted `Running io.github.xdstorage.*` class lists
before/after (before-lists captured on the pre-change tree).

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track T-2
- [FLAKY.md](../../FLAKY.md) · [RELEASE-PLAN.md](../../RELEASE-PLAN.md) § Quality bar

Estimate: ~1 dev-day.
