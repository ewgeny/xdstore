---
title: "[2.1.0][L-3] Build guards: keep logging backends and bindings out of main scope, forever"
labels: [2.1.0-train, P0, logging, ci]
---

## Summary

Make the L-0 outcome structurally permanent: an enforcer rule that fails the build if a
logging backend or an SLF4J binding ever re-enters main scope, plus an artifact check that the
published jar ships neither a binding service entry nor log4j classes.

## Why

Task 01 fixes the dependency tree once; this task prevents regression. A future "just add this
dependency" PR could quietly re-bundle a backend (that is how the 2.0.0 log4j-core situation
arose historically). Decision record: [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track L-3.

## Scope

1. **maven-enforcer-plugin, `bannedDependencies`** (release profile — or always-on with
   scope-qualified rules):
   - Ban `org.apache.logging.log4j:*` in `compile`/`runtime` scope (test scope stays legal —
     the suite binds log4j2 on purpose).
   - Ban every SLF4J **binding** in `compile`/`runtime` scope:
     `org.apache.logging.log4j:log4j-slf4j2-impl`, `ch.qos.logback:logback-classic`,
     `org.slf4j:slf4j-simple`, `org.slf4j:slf4j-jdk14`, `org.slf4j:slf4j-nop`,
     `org.slf4j:slf4j-reload4j`, `org.slf4j:slf4j-log4j12`.
   - `org.slf4j:slf4j-api` must remain **legal** in compile scope — the rule bans backends,
     not the API.
   - Failure message names the rule and points at this issue's rationale (enforcer supports a
     custom message).
2. **Artifact check** — a CI step (gate job in `.github/workflows/ci.yml` + the release
   workflow) asserting on the built jar:
   - no `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` entry,
   - no `org/apache/logging/log4j` classes.
   Implement as a small shell step after `mvn package` (`unzip -l` + grep), not as a unit test
   reading `target/` (fragile).
3. **Verify the release profile produces no console spam**: `mvn clean package -Prelease` on a
   quiet machine shows no engine INFO lines on stdout during the test phase (the test-scope
   config from task 01 routes them to the file sink).

## Out of scope

- Dependabot config (exists since Step 0); other dependency-policy rules — out of this train.

## Acceptance criteria

- [ ] Temporary local experiment (add `log4j-core` back to compile scope, run, **revert**):
      the build fails with the enforcer message. Same for one SLF4J binding.
- [ ] `mvn clean verify -Pquick-gate` and the CI gate stay green with the rule active.
- [ ] CI gate + release workflow contain the artifact check; a run proves it executes
      (log line in the job output).
- [ ] `-Prelease` package build shows no engine INFO on console.

## Verification

```bash
mvn clean verify -Pquick-gate          # green with rules active
mvn clean package -Prelease            # quiet console
unzip -l target/xdstorage-*.jar | grep -E "log4j|SLF4JServiceProvider" || echo CLEAN
```

## References

- [MASTER-PLAN.md](../../MASTER-PLAN.md) § Track L-3
- Task 01 (the pom/config layout being guarded)

Estimate: ~0.5 dev-day. Depends on: 01.
