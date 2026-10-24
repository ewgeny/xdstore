---
title: "[2.1.0][JAVADOC] Cover the public methods of the whole project with javadoc — and turn doclint on"
labels: [2.1.0-train, P1, docs, chore]
status: done
---

## Summary

Every public method of the main tree gets javadoc — real contract prose on the published API
tier, honest one-liners on the machinery tier — and the release profile's javadoc plugin
finally tightens (`doclint=none` → enforced), closing the promise deferred at 2.0.0
(RELEASE-PLAN §3). The javadoc jar ships on Central; today it documents ~310 blocks against
~895 public methods across 193 classes. The task's own principle: **the javadoc is where an
embedder meets the EMBEDDING.md contract first** — the gotchas belong on the methods, not
only in a doc page nobody opened yet.

## Why (ground truth, 2026-10-23)

- 193 main classes; **~895 public methods** (including JavaBean accessors); **~310 javadoc
  blocks** exist — the published javadoc jar is mostly empty method signatures.
- `pom.xml` (release profile) says it outright: `<doclint>none</doclint>`,
  `failOnError=false`, *«doclint stays off for 2.0.0, tightening is a follow-up
  (docs/RELEASE-PLAN.md §3)»*. This task IS that follow-up — without it the promise rots
  like the README test count did before DOCSLINT.
- The language is mixed (engine-internals javadoc is largely Russian, the R-campaign
  heritage) while the audience on Central is international.

## Scope

### Tier 1 — the published API (full contract prose, English)

Every public method gets real javadoc documenting the **contract, not the implementation** —
including the known gotchas at the exact method they apply to (`save/update`: the
thread-bound transaction requirement and the no-auto-rollback rule; `load` by id after
reopen; `count(q) == load(q).size()`; `verify`: read-only, closed-folder; config flags and
their measured costs). Targets (parallelizable between developers):

1. `IXdStorage` / `IXdFileStorage` / `XdStorage` facade + `XdStorageProvider`
2. `XdStorageConfig` (+ builder) and `XdStoragePolicy`
3. `annotations/` — every annotation documents itself as the user-facing contract
   (id, policies, search indexes, id-index type, parent reference, load-by-get, field
   properties)
4. `search/query/` — the query model and every criterion operator (semantics pinned by the
   FP-1 suites: inclusive BETWEEN, nulls never match, mixed types fail loudly)
5. `exceptions/`, `metrics/`, `idgeneration/` SPI surfaces, `IXdStorageWatcher`/
   `IXdStoragePredicate`
6. `package-info.java` for every Tier-1 package (the package-level overview — what belongs
   here, what is internal)

### Tier 2 — the machinery packages (`resource`, `btree`, `index`, `search` internals,
`serialization`, `transaction`, `structure`, `trigger`, `observing`, `code`, `factories`,
`object`, `lock`, `utils`, `helpers`, `services`)

- **Class-level javadoc on every class** (what the collaborator does, its thread-safety
  stance, and its `internal`/public standing);
- every public method gets **at least a one-line contract**; non-trivial machinery (2PC
  phases, latch/arbitration, replay, guard sites) gets the real explanation;
- existing Russian javadoc **may stay** on methods not being rewritten (documented decision
  — translating the whole internals corpus doubles the effort for no embedder value; the
  class-level docs are written in English). Tier-1 legacy Russian is translated as part of
  its rewrite.

### The enforcement (what makes this permanent)

- `mvn javadoc:javadoc -Ddoclint=missing` green on the whole main tree (the coverage gate —
  trivial accessors get their one-liner, so `missing` passes everywhere);
- the release profile tightens: `<doclint>missing,syntax</doclint>` (or `all` if the HTML
  strictness allows) + `failOnWarnings=true` — the javadoc jar can never silently regress;
- `mvn clean test` unaffected; the quickstart/example contract test (DOCSLINT) unchanged.

## Out of scope

- Behavior/refactoring changes of any signature (javadoc mismatches found while writing
  become issues, not silent fixes — the T-3b rule);
- translating the entire legacy Russian internals corpus (decision recorded above; revisit
  if the javadoc jar's Russian share bothers consumers);
- private/protected internals, test sources;
- `Xdoc`-style HTML polish beyond doclint compliance.

## Acceptance criteria

- [ ] `mvn javadoc:javadoc -Ddoclint=missing` green (zero missing public members).
- [ ] Release profile: doclint tightened + `failOnWarnings=true`; `mvn clean verify
      -Prelease -Dgpg.skip=true` still produces the javadoc jar.
- [ ] Quality spot-check (the reviewer's bar, named in the PR): the ten most-used facade
      methods document their gotchas (thread binding, rollback-only marks, deferred-id
      timing, lock/closed-folder rules) — javadoc mirrors EMBEDDING.md, not the code.
- [ ] Full suite green ×2 (javadoc changes nothing — prove it).
- [ ] README links the published javadoc as the API reference; `CHANGELOG.md` notes the
      documented API surface.

Estimate: ~3–5 dev-days (parallelizable per Tier-1 package; the Tier-2 one-liners are
mechanical). Priority note: lane-parallel with everything; shippable incrementally, but the
doclint tightening is the train's deliverable, not the prose alone.

## References

- [RELEASE-PLAN.md](../../RELEASE-PLAN.md) §3 — the deferred doclint promise this closes
- [EMBEDDING.md](../../EMBEDDING.md) — the contract the Tier-1 javadoc mirrors
- Task 12/21 precedent — the docs-rot lesson (the javadoc jar had the same rot)
- [DOCSLINT](21-adoption-docs-contract.md) — the house style of mechanical docs gates
