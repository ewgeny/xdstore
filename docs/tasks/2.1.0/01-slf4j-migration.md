---
title: "[2.1.0][L-0/L-1] Migrate main sources to the SLF4J API; log4j2 becomes test-only"
labels: [2.1.0-train, P0, logging, refactor]
status: open
---

## Summary

Replace the direct log4j2 dependency in `src/main/java` with the SLF4J API
(`org.slf4j.Logger` / `LoggerFactory`). `slf4j-api` becomes the **only** main-scope logging
dependency; `log4j-core` moves to test scope together with the `log4j-slf4j2-impl` binding;
the bundled `log4j2.xml` leaves the published jar.

## Why

The released 2.0.0 jar ships `log4j-core` (~1.8 MB, CVE surface) as a required dependency and
bundles a `log4j2.xml` whose **Root-level INFO Console appender** puts the engine's
per-operation log on the consumer's stdout (the log spam observed during the release). The
decision record — including the rejected alternatives (optional scope, Netty-style internal
facade) — lives in [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track L; read it before deviating.

## Scope

1. **Mechanical migration** — measured: 39 files / ~40 `LogManager.getLogger` sites, all
   logger fields uniformly named `log`, 141 call sites (`debug` 34, `error` 29, `info` 21,
   `warn` 57), no `trace`. Verified prerequisites: no log4j2-specific API in main sources
   (no ThreadContext, Markers, FluentLogger, Supplier overloads, `throwing`/`catching`);
   both APIs use `{}` placeholders — **log messages and arguments stay verbatim**; only the
   imports and the `getLogger` lines change.
2. **pom.xml** — add `org.slf4j:slf4j-api` (2.0.x latest stable; Java 8 bytecode — the
   library builds with `release=8`); move `org.apache.logging.log4j:log4j-core` to
   **test** scope; add `org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1` (must match
   log4j-core) in **test** scope.
3. **Config** — move `src/main/resources/log4j2.xml` out of the jar: the suite's file-sink
   config lands as `src/main/test/resources/log4j2.xml` (rolling file, INFO — CI
   diagnosability unchanged; console appender at WARN so build logs stay readable).
4. **docs/examples/logging/** — consumer configs: `log4j2-quiet.xml` (console WARN, rolling
   file INFO) and `log4j2-diagnostic.xml` (the full per-operation trace at DEBUG) + a
   logback twin of the quiet one; a one-paragraph README stating any SLF4J backend works.
5. **EMBEDDING.md § Observability** — rewrite for the new contract: the library logs through
   SLF4J; the backend and its configuration are the application's; `xdstorage.log.file` and
   the bundled-config paragraphs are removed; point to the examples.

## Out of scope

- Log-level changes (INFO → DEBUG demotion): separate issue (L-2).
- Enforcer guards / artifact checks: separate issue (L-3).
- Any behavior change beyond logging wiring.

## Acceptance criteria

- [ ] `grep -r "org.apache.logging.log4j" src/main/java` returns nothing.
- [ ] `mvn clean test` green — verdict from Maven's own `BUILD SUCCESS` line (the R-52 rule).
- [ ] On a fresh consumer pom: `mvn dependency:tree` shows exactly **one** logging artifact in
      compile scope (`slf4j-api`), none in runtime scope.
- [ ] `unzip -l target/xdstorage-*.jar` contains no `log4j2.xml` and no
      `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`.
- [ ] The suite still writes its log file (CI diagnosability preserved).
- [ ] `CHANGELOG.md` `[Unreleased]` "Changed" entries reflect the final state (drafted
      already — adjust, don't duplicate).
- [ ] `docs/tasks/2.1.0/README.md` conventions followed (unique storage names, no
      deleteDir-gated logic in any touched test).

## Verification

```bash
mvn clean test                                   # BUILD SUCCESS verdict
mvn -q dependency:tree -Dincludes=org.slf4j,org.apache.logging.log4j
unzip -l target/xdstorage-*.jar | grep -E "log4j2.xml|SLF4JServiceProvider" || echo CLEAN
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Step 1, Track L (decision record L-0/L-1)
- [EMBEDDING.md](../../EMBEDDING.md) § Observability (to rewrite)
- [CHANGELOG.md](../../../CHANGELOG.md) `[Unreleased]` § Changed (drafts live there)

Estimate: ~1 dev-day.
