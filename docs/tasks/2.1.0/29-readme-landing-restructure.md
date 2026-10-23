---
title: "[2.1.0][LANDING] README first screens: what is it / when to take / when not — and a 10-line example"
labels: [2.1.0-train, P0, docs]
status: open
---

## Summary

Restructure the README as a **landing page**. The feedback from the field (2026-10-23,
translated): *"47 KB — I drowned"*; *"the first screens must answer: what is it? when to
take it? when NOT to take it?"*; *"need a ~10-line example"*; *"a comparison table vs
H2/MapDB and close relatives — it's unclear when to pick which"*. All of that content
already exists — in DECISION-GUIDE.md, the quickstart, the changelog — and evaluators
cannot find it, because the README's first screens are a feature-list reference manual.
The failure is the entry surface, and this task rebuilds it.

## Why this is P0 and runs FIRST

This is more important than new features (the feedback, verbatim): every other asset —
the decision guide, the benchmarks, the recovery tooling — loses its value if the evaluator
drowns before reaching it. Everything else in the docs track links *from* this surface, so
it lands before 30 and alongside 31.

## Scope

1. **The first screen** (before any scroll) answers, in order:
   - **What is it** — one paragraph (embedded, file-backed, ACID, annotated POJOs, no
     server, no native deps);
   - **A runnable example in ≤10 lines** — distilled from
     [examples/quickstart](../../examples/quickstart/README.md) (open → save → query →
     shutdown; it must compile as written — copied from the green test, not paraphrased);
   - **When to take it / when not** — three to five bullets each, distilled from
     DECISION-GUIDE § checklists (design point 10⁴–10⁵ objects/class, ≤10²–10³ write-tx/s);
   - **The comparison mini-table** vs the close relatives (H2/SQLite, MapDB, Xodus,
     EclipseStore, RocksDB — one row each: model / transactions / what you build yourself),
     linking DECISION-GUIDE for the full version.
2. **The honest-limitations block, above the fold**: the write-throughput numbers from the
   canonical baseline (~10²–10³ write-tx/s, commit-bound) stated as a current limitation —
   **with the plan**: a link to the write-path analysis/design (task 31) and its status.
   An evaluator must see "we know, here is the number, here is how we fix it" without
   digging (the feedback: *"very low write numbers are a project failure — surface them in
   the README with the fix plan"*).
3. **The 47 KB reference gets out of the way**: the deep reference sections (data-model
   details, concurrency internals, serialization formats, structure updates) move to
   `docs/` pages or below a clearly-marked reference divider — target: the README halves,
   and nothing an evaluator needs sits below a screen of feature list. All cross-links
   (task files, DOCSLINT's link test, CHANGELOG) updated in the same PR.
4. **Language check**: the first screen is English-first (the Central audience); no
   project-internal jargon ("train", "R-numbers") above the fold.

## Out of scope

- New content beyond the distillation (the decision guide owns the depth);
- translating the README (a separate decision);
- touching BENCHMARKS.md (task 30's scope).

## Acceptance criteria

- [ ] The first screen answers all three questions without scrolling past ~40 lines; the
      example is ≤10 lines and copied verbatim from the passing quickstart test.
- [ ] The comparison mini-table is on the first screen; the full guide is one click away.
- [ ] The write-throughput limitation + fix-plan link are visible above the fold.
- [ ] The README is at most half its current size; every moved section is reachable from
      the README; DOCSLINT (21) stays green.
- [ ] Fresh-eyes review: someone who never saw the project answers the three questions
      after one screen (record the reviewer in the PR).

Estimate: ~1 dev-day (restructuring + link hygiene, the content exists).

## References

- [DECISION-GUIDE.md](../../DECISION-GUIDE.md) (the content being surfaced)
- [examples/quickstart](../../examples/quickstart/README.md) (the 10-line source)
- Task 31 (the write-performance plan the limitations block links to)
- Task 21 DOCSLINT (the link contract to keep green)
