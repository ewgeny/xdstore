# Known flaky test families (quarantine ledger)

The full suite is the default gate (`mvn clean test`). The families below are **documented,
rare flakes under peak load** — each has been A/B-verified as pre-existing and characterized in
[OPTIMIZATION-PLAN.md](OPTIMIZATION-PLAN.md) (R-45 residual / R-50). They are NOT quarantined from
the default run; `-Pquick-gate` excludes them for a fast low-noise gate, and the nightly CI job
tracks their flake rate (full suite ×3).

| Test class | Family / signature | Rate | Evidence | Status |
|------------|--------------------|------|----------|--------|
| `XdStorageGraphPhantomReadStressTest` | tearDown "Разрушен вложенный спутник" — R-44 fixed the read-path null-satellite; rare residual of the same concurrent purge-vs-rollback family | ~1/11 isolated | R-45, R-44 | open residual, needs dumps (R-45 next-session note) |
| `XdStorageFacadeStressTest` | "рассинхрон структуры" — the R-32 validator's two traversals are not atomic under 25 workers; false-positive suspicion | rare in-suite, green isolated ×2 | R-45, R-46 | open suspicion: validator-side, not engine |
| `XdStoragePhantomReadIsolationStressTest` | read-lock contention under full-suite load; stable green isolated since R-13 | flaky in-suite only | R-13, R-18 | known family |
| `XmlDataStorageMultithreadsTest` | concurrent purge vs rolled-back tx ("cannot load by reference" in afterEach) | flaky ~1/3 in-suite | R-18, R-21, R-31 | known family |

## Conventions

- A run is judged by Maven's own `BUILD SUCCESS`/`Tests run: N, Failures: M` line — **never by a
  piped grep's exit code** (the R-52 gotcha: `mvn test | grep ERROR; echo $?` reports grep's status
  and a failed build masqueraded as success until launch).
- Storage name + directory must be **unique per test run** (R-50 stale-dir contamination):
  `File.delete` legally fails on IDE-held files → the storage parses stale disk state → confusing
  first-save failures. Never gate test logic on `deleteDir` success.
- JMH trials that fail are **silently skipped** — count the rows in the final table
  ([BENCHMARKS.md](BENCHMARKS.md) gotcha #1; the nightly CI job asserts 9 rows).
- Un-quarantine a family only after its R-entry records a fix with A/B evidence.
