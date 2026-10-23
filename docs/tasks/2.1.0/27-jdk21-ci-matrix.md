---
title: "[2.1.0][JDK21] CI matrix: prove the suite on JDK 21, not just 17"
labels: [2.1.0-train, P2, ci, chore]
status: open
---

## Summary

The canonical benchmark baseline runs on **JDK 21**; the test suite only ever proves itself
on **17**. Add the second leg to CI: a matrix (or a parallel job) running the suite on 21,
so "works on my JDK" — the exact class D-2 was defined for — cannot come back.

## Why

D-2 (the original Track D) was defined for this and never given a train task; FP-12's
"extends D-2" framing wrongly tied the JDK matrix to FP-3 — the matrix is meaningful *now*:
the perf study and the canonical baseline (BENCHMARKS.md, 2026-10-22) are JDK-21 numbers
while the gate is JDK-17-only. The library is release=8 bytecode; the risk is not bytecode,
it is runtime behavior differences (GC timing under the stress families, NIO behavior in
the atomic-rename/sentinel paths, codegen under the 21 compiler). Cheap, bounded, and it
makes every published benchmark number reproducible on a tested runtime.

## Scope

1. `.github/workflows/ci.yml`: the gate gains a JDK **21** leg. Shape (decide in the PR):
   - (a) matrix on the gate job — both legs run the FULL suite (~+6 min per push); or
   - (b) gate stays 17-full, a new `jdk21` job runs `-Pquick-gate` on pushes and the FULL
     suite nightly.
   Constraint either way: **JDK 21 runs the FULL suite at least nightly**.
2. The stress families run on both legs — they are exactly where runtime timing differs;
   the documented flake ledger applies to both legs equally (a flake on 21 is a FLAKY.md
   row like any other).
3. README § Building and testing: the tested-JDK statement updates ("17 (gate) and 21
   (matrix); bytecode remains release=8").

## Out of scope

- Virtual-thread scenarios (FP-12, post-FP-3); JDK 25/ea legs; Windows/macOS runners
  (the atomic-rename/sentinel paths are POSIX-flavored today — a portability matrix is a
  separate decision with separate risk).

## Acceptance criteria

- [ ] Both legs green on a push to main (the known-flake ledger honored on both).
- [ ] A nightly has run the FULL suite on 21.
- [ ] README states the tested runtimes; the pom/bytecode policy unchanged (release=8).
- [ ] Gate runtime impact measured and acceptable (stated in the PR).

Estimate: ~0.5 dev-day.

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) — D-2 (the original definition), FP-12 (the
  post-FP-3 extension this deliberately does NOT pull)
- [BENCHMARKS.md](../../BENCHMARKS.md) § Canonical (the JDK-21 conditions this makes
  testable)
- [FLAKY.md](../../FLAKY.md) (the ledger both legs feed)
