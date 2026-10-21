# Codegen optimization plan (generated storage wrappers)

Scope: the runtime wrapper code-generation subsystem — `io.github.xdstorage.code`
(`XdStorageClassGenerator`, `*ClassCodeGenerator`, `XdStorageProxyCompilationEngine`)
and the generated Simple / Unmodifiable / IdObservable wrapper families.

Status legend: `[x]` applied, `[ ]` planned, `[-]` rejected with rationale.

## Baseline

Before this plan the codegen had no JUnit coverage at all: the three
`*CodeGeneratorTest` classes were `main()`-based code dumps, and the only engine-level
coverage was indirect (every storage test implicitly uses generated wrappers).
`XdStorageWrapperJavaBeansContractTest` (36 tests) now pins the JavaBeans contracts:
equals/hashCode consistency, strict wrapper-to-wrapper equality, HashSet behavior,
getter/setter delegation, toString delegation, observer notification, load-by-get
round-trips, generated-class shape, and a not-a-dummy battery over the whole model.

## Applied

### [x] 1. equals() without reflection (hot-path win)

The generated `equals()` used to unpack the other wrapper via
`getDeclaredField("object") + setAccessible(true) + get(...)` **on every invocation**.
`equals` is the hot path of the engine's `HashSet`/`HashMap` bookkeeping (snapshot
scans, cache records, pending-id workspace keys) — a reflective field access per
comparison, in loops of thousands of wrappers.

All three wrapper interfaces now expose `default Object getWrappedObject__()`
(`IXdStorageSimpleWrapper`, `IXdStorageUnmodifiableWrapper`,
`IXdStorageIdObservableWrapper`; default returns `null`, so hand-written stubs and
dummy wrappers stay source-compatible), generated classes override it, and the
generated `equals` compares `this.object.equals(other.getWrappedObject__())`.
Semantics are unchanged (strict wrapper-to-wrapper); the dummy-wrapper and
cross-family cases still return `false` (`null` inner behaves like the old
reflection-failure branch).

### [x] 2. Bug fixes found by the new contract suite

| Bug | Symptom | Fix |
|-----|---------|-----|
| Observable wrappers emitted **no setters at all** | non-id setters mutated the wrapper's own dead inherited fields — silent state loss in the deferred-id insert path (`XdStorageCacheMutator` reads the inner object on id-set) | `generateDelegatingSetters` emits delegation for every setter except the id setter |
| `getPublicMethods` collected `static`/`final` methods | generated wrapper redeclared them as instance overrides → uncompilable source → **silent dummy degradation** of the whole entity | static/final/synthetic(bridge) methods are skipped |
| `getPublicMethods` collected overridden getters twice (base + subclass declarations) | duplicate method definition → uncompilable source → silent dummy for any entity hierarchy | most-derived declaration wins (name+parameterTypes dedupe) |
| Missing `@XdStorageObjectId` → raw NPE (`idFieldName.charAt(0)`) | generation crashed with a stack trace pointing at string building | loud `IllegalStateException` naming the offending class |
| Observable `findIdSetter` matched only fields declared on the class itself | inherited id fields never produced an observing id setter | id field resolved through the hierarchy (`getIdFieldName`) |
| `XdStorageIdentifiableObject` unmodifiable wrapper **never compiled** since the 4-arg `wrapAsUnmodifiableObject` signature change (emitted a 2-arg call) | `generateUnmodifiableWrapper(XdStorageIdentifiableObject.class)` silently returned an empty map; `wrapAsUnmodifiableObject` on identifiable objects returned `null` | generated source compiles: plain method delegation instead of the bogus `getProperty("fieldName")` literal (which also read the wrong backing store for field-backed getters like `getId`); `equals` aligned with the strict wrapper-to-wrapper canon of the family |

## Planned

### [ ] 3. Batch-compile the entity graph in one javac invocation

Today every class triggers its own `javac` task (~100–300 ms first-touch, per class;
hierarchies hit the engine's recursion guard and compile per level). Collecting the
wrapper sources of a graph and passing them as a single compilation unit list cuts
cold-start cost roughly proportionally to the number of entities.
Effort: M. Risk: low (same compiler options, one task). Payoff is bounded because
generation is once-per-class-per-JVM and amortized by the engine cache; mainly
benefits short-lived processes.

### [ ] 4. Persist compiled wrappers across JVM runs

`gencode/` is written on every start but never *read* as a cache. Reusing a compiled
wrapper requires a correctness key (e.g. SHA-256 of the generated source stored next
to the `.class`), otherwise an engine or annotation change silently serves stale
classes — the failure mode the SILENT-LOSS BARRIER comments warn about.
Effort: M. Risk: **high** (staleness beats latency). Only worth it after item 3.

### [x] 5. Null-guard the unmodifiable load-by-get block

The generated unmodifiable lazy getter used to call `storage.load(object.getX(), tx)`
unconditionally; a null lazy field turned into `storage.load(null, tx)`. The generated
block now guards with `!= null` (matching the simple-wrapper family) while keeping the
loaded-flag semantics: the flag is set even when the field is null, so a permanently
empty lazy field costs exactly one check per access.

### [ ] 6. JavaBeans naming constraints — document, don't fix

Field-name derivation assumes canonical bean naming
(`getX` → field `x` via `substring(3,4).toLowerCase()`); fields like `uRL` or
overloaded `getX(int)` bean getters are outside the supported convention. All model
entities follow the convention; a general fix ( PropertyDescriptor-based discovery)
is a rewrite of method collection with its own edge cases. Documented constraint.

### [-] 7. Caching equals/toString results — rejected

Wrappers are mutable transactional views (the inner object is replaced on
materialization); any cached identity/toString snapshot would desynchronize from the
inner object mid-transaction. This is the same invariant that keeps wrapper equality
strict.

### [-] 8. Bytecode generation (ASM/classfile emission) instead of javac — rejected

Removes the JDK-compiler dependency the engine explicitly detects and warns about
(JRE silent-loss barrier), but the generated surface is small, `javac` optimizes the
delegation code identically, and source-based generation keeps generated classes
inspectable (`*CodeGeneratorTest` dumps are a debugging tool). Not worth the
toolchain risk for a once-per-class cost.

## Verification

- `XdStorageWrapperJavaBeansContractTest` — contract + regression suite (36 tests).
- Full suite (`mvn clean test`) is the gate; documented stress-family flakes per
  [FLAKY.md](FLAKY.md) are excluded from the success criterion.
