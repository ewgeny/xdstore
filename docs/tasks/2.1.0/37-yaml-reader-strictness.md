---
title: "[2.1.0][YAMLREAD] YAML reader strictness — corruption must fail loudly, never masquerade as empty data"
labels: [2.1.0-train, P2, engine, bug]
status: open
---

## Summary

Four findings of the 28-javadoc-coverage campaign in the serialization readers. The shared
theme: a malformed or unexpected input is silently absorbed instead of failing loudly — exactly
the failure class the S-1 EOF-sentinel work (2.0) was built to eliminate.

1. **`XdStorageDefaultObjectsReader.readReferences`** accepts both `object` and `reference`
   start elements, but its `readElement(reader, field)` overload only processes `reference` —
   an `object` element in a references file silently yields nothing (references vanish, no
   error). Accept one format, refuse the other loudly.
2. **`XdStorageYamlBlockObjectsReader.parseAndSetField`** silently skips `null` values even for
   known properties (the field keeps its default instead of being assigned null — a loaded
   record can differ from the stored one), and its fallback `idField.set(...)` branch is dead
   (reachable only for field names unknown to the class — which should be a loud refusal, not
   an id overwrite).
3. **`XdStorageYamlBlockObjectsReader.readReference`** returns `null` when the id value is
   absent — callers cannot distinguish "no reference" from a corrupt block (corruption
   masking).
4. **`XdStorageIOFactory.instanceIOFactory`** NPEs at the `switch` on a `null` format — guard
   with an actionable message (`FilesFormat` values only).

## Scope

- Per case: refuse loudly (`XdStorageIOException`/`XdStorageRuntimeException` with the file
  name and the offending element/field) or assign the value honestly — pick per the reader's
  contract, then pin it with a corruption-detection test (truncated field line, `object` in a
  references file, reference block without an id, null-valued property round-trip).
- The null-property question needs one decision recorded in the task: is `null` a legal stored
  value for a known property (then assign it) or not (then refuse)? Round-trip test either way.

## Out of scope

- `IXdStorageObjectsReader.read(Reader, tx)`'s unused transaction parameter (carried for cache
  identity per the in-code comment; documented in 28);
- `appendCompensation` writing serial `0` (by design; documented).

## Acceptance criteria

- [ ] A corruption test per case, red on the old reader, green after.
- [ ] The null-property decision recorded; the round-trip test pins it.
- [ ] Full suite green ×2.

Estimate: 1–1.5 d.

## References

- [28-javadoc-coverage](28-javadoc-coverage.md) — the campaign that surfaced all four
- `serialization/XdStorageDefaultObjectsReader.java`,
  `serialization/yaml/XdStorageYamlBlockObjectsReader.java`, `serialization/XdStorageIOFactory.java`
