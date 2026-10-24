---
title: "[2.1.0][JRNLNAME] Journal file naming: <base>-journal.<ext> with legacy read-compatibility"
labels: [2.1.0-train, P1, engine, bug]
status: done
---

## Summary

Journal files must be named with a **suffix**, not a stacked extension: `Order-1-journal.yaml`,
not `Order-1.yaml.journal.yaml`. The current scheme (`baseFileName + ".journal.yaml"`,
`XdStorageYamlCommitJournal.JOURNAL_SUFFIX`) produces confusing double-extension names. The
rename must carry **read-compatibility**: 2.0.0 has shipped with `journalWrites` ON since
2026-10-21, so existing folders hold un-checkpointed commits in legacy-named files — a naive
rename would silently orphan them (silent loss of commits since the last checkpoint: exactly
the loss class this engine exists to prevent).

## Why (ground truth, verified in code 2026-10-23)

- The name is built at a **single choke point**: `XdStorageYamlCommitJournal.JOURNAL_SUFFIX`
  + `journalFileFor()`; `XdStorageResource` resolves it once in the constructor. Mechanical
  change.
- **The journal is always YAML content** — even for XML-format storages:
  `yamlJournalFactory` is wired only when the IO factory is a YAML factory, and
  `journalFileFor` appends `.yaml` unconditionally. Today an XML storage's `Order.xml`
  already carries an `Order.xml.journal.yaml` (YAML content) — a triple-mixed name. The
  user-visible ask ("-journal.xml for XML") therefore needs a decision, not just a rename:

| Option | Verdict | Reason |
|---|---|---|
| (a) **`<base-without-ext>-journal.yaml` uniformly** — content-matched extension, YAML journal for every format | **recommended** | The journal content IS YAML today; the name should describe the content. One serializer surface (WRITE-PATH-DESIGN §3 already rejected a non-YAML journal for exactly this reason) |
| (b) format-matched `-journal.xml` with XML journal content | rejected for v1 | A second journal serializer (writer + reader + sentinel + tests) for a file the user is told never to open; revisit only if XML storages become first-class for the journal |

- **Collision-safe**: `-journal` contains a dash; Java class names (which form the
  `data/<Class>/` file names) cannot. `structure/XdStorageClassStructure.yaml` →
  `XdStorageClassStructure-journal.yaml` — no ambiguity with base files.

## Scope

1. **New naming**: `journalFileFor` strips the base's format extension and appends
   `-journal.<ext-of-the-journal-content>` — `Order-1.yaml` → `Order-1-journal.yaml`;
   `Order.xml` → `Order-journal.yaml` (option (a)). Update the
   `XdStorageYamlCommitJournal` javadoc layout note.
2. **Read-compatibility (the P0 heart of this P1 task)**: at journal resolution/replay —
   new-name file absent → look for the **legacy name** (`<base>.journal.yaml`); found →
   replay it, then **migrate** (rename to the new name) after a successful replay;
   **both present** → new wins, legacy removed after the new file replays clean, with a
   WARN (both-present is an anomaly worth naming). Legacy journals that were already
   empty/checkpointed migrate as empty files (or are simply renamed — define once, test it).
3. **Tests**: round-trip under the new name; **legacy file replayed + migrated** (the
   2.0.0-upgrade pin: un-checkpointed commits survive the upgrade); both-present
   precedence; XML-storage journal name (`Order.xml` → `Order-journal.yaml`); the S-5
   `.tmp` sweep still covers journal temp files under the new name; checkpoint reset writes
   the new name.
4. **Docs**: WRITE-PATH-DESIGN.md §2.1 layout (per-resource journal naming),
   EMBEDDING.md § Files and durability (the folder is engine-owned — the names changed and
   old names are auto-migrated), CHANGELOG "Changed" (user-facing on-disk layout).

## Out of scope

- Format-matched journal content (option (b)) — recorded, not built;
- journal layout/content changes of any kind (the record format, sentinel, torn-tail policy
  are untouched — this task renames a file);
- the recovery-matrix cells from task 23 keep working (the matrix names journal files via
  the same choke point — it does not exist yet, so no double work; land order irrelevant).

## Acceptance criteria

- [x] New storages write `Order-1-journal.yaml`-style names (no double extensions anywhere).
- [x] A 2.0.0-era folder with a legacy-named journal opens with its un-checkpointed commits
      replayed, and the file migrates to the new name (the upgrade test pins this — the fixture
      empties the base so only the legacy journal can restore the objects).
- [x] Both-present anomaly → new wins + WARN, no data loss (the twin is removed after the clean
      replay; the test discriminates by an update record that exists only in the new-name file).
- [x] Full suite green ×2 (BUILD SUCCESS verdict); no new FLAKY rows.
- [x] CHANGELOG "Changed" entry (on-disk naming + automatic migration).

Estimate: ~0.5–1 dev-day (single choke point; the compat test is the careful part).

## References

- `XdStorageYamlCommitJournal` (the choke point), `XdStorageResource` constructor
  (the resolution site), S-5 `.tmp` sweep (the name-adjacent machinery)
- [WRITE-PATH-DESIGN.md](../../WRITE-PATH-DESIGN.md) §2.1 (layout to update)
- [EMBEDDING.md](../../EMBEDDING.md) § Files and durability (the contract to update)

## Implementation notes (2026-10-24)

- Single choke point as planned: `XdStorageYamlCommitJournal` (`JOURNAL_SUFFIX` = `-journal.yaml`,
  `LEGACY_JOURNAL_SUFFIX` = the 2.0.0 `.journal.yaml`), `Resolution{file, legacyPresent}` +
  `resolveFor(base)` + `settleAfterReplay(...)`; wired into `XdStorageResource` and
  `XdStorageHashIndexResource` (non-final journal fields, ctor resolution, settle after a clean
  replay). Checkpoint `reset` deletes the resolved file — the size checkpoint and the W4a
  close-checkpoint both run under the new name.
- **Lazy migration is the honest contract**: replay (and therefore settle) runs at a resource's
  PREPARE, not at folder open — a read-only session over an upgraded folder migrates only the
  resources it actually touches (the id index; the entity fragment at its first write) and the
  close-checkpoint covers the prepared set. A never-prepared resource (e.g. the structure
  counter) keeps its legacy name until its next prepare; its appends land INSIDE the legacy
  file meanwhile, so nothing is lost. `XdStorageJournalNamingTest` pins exactly this scoping.
- The XML acceptance item resolved differently than the task assumed: XML storages cannot
  journal at all (the W-1 guard refuses `journalWrites` for non-YAML formats loudly at open —
  the task's "an XML storage's Order.xml already carries a .journal.yaml" premise predates that
  guard). The naming rule is still pinned for every base extension as a pure function
  (`Order.xml` → `Order-journal.yaml` if a journal ever rides one).
- Test-infra discovery (pre-existing engine trait, NOT touched here): the per-class structure
  metadata persists ABSOLUTE data-resource paths, so a folder COPY opened mid-session redirects
  its data resources to the original path (index/structure resources re-derive correctly; the
  close-time path re-derives correctly too). The naming tests therefore let the fixture take
  over the original path before reopening. Filed for a future task if folder relocation ever
  becomes a supported flow.

## Addendum (2026-10-24, post-implementation directive): journals move into a subdirectory

User directive after the suffix naming shipped within this train: journals must live in a
SUBDIRECTORY of their resource's directory, so a class folder holds only bases. Final layout:
`data/<Class>/journal/<base>-journal.yaml` and `structure/journal/<Class>-journal.yaml`
(`XdStorageYamlCommitJournal.JOURNAL_DIR`). The resolution chain is now three levels, priority
order: the journal-directory file wins → the sibling spelling (`<base>-journal.yaml` next to
the base, this train's intermediate naming) is a replay source → the 2.0.0-era stacked name is
a replay source; after a clean replay everything settles into the journal directory and
additional superseded twins are removed (WARN). `Resolution{file, superseded: List<Path>}`
replaced the single-legacy shape; `XdStorageFolderVerifier` matches base-less journals across
the subdirectory and keeps the per-class count consistency aware of `journal/`.
`XdStorageJournalNamingTest` pins all three migrations (stacked, sibling, both-present) + the
placement invariant.
