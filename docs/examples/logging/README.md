# XdStorage logging examples

XdStorage logs through the **SLF4J API** — `org.slf4j:slf4j-api` is the only logging artifact
in its dependency tree; the jar ships **no backend and no logging configuration**. The backend
and its configuration are entirely your application's choice, and any SLF4J backend works:
log4j2, logback, `slf4j-jdk14`, or none at all (a backend-less application sees at most
SLF4J's single documented "no provider" notice — never engine output).

| File | Backend | Behavior |
|---|---|---|
| `log4j2-quiet.xml` | log4j2 (`log4j-core` + `log4j-slf4j2-impl`) | Engine lifecycle events (storage open/shutdown, journal checkpoint) at INFO to a rolling `xdstorage.log`; console shows WARN+ only |
| `log4j2-diagnostic.xml` | log4j2 | The full per-operation trace at DEBUG on the console |
| `logback-quiet.xml` | logback (`logback-classic`) | The logback twin of the quiet setup |

To use one: add the backend dependency to your build, then either rename the file to
`log4j2.xml` / `logback.xml` and put it at your classpath root, or point the backend at it
explicitly (`-Dlog4j.configurationFile=…`, `-Dlogback.configurationFile=…`).

Level discipline: INFO is reserved for rare lifecycle events (storage open/shutdown, journal
checkpoint); the per-operation trace (save/delete/commit/rollback/prepare, B+tree events) is
DEBUG — enable logger `io.github.xdstorage` at DEBUG (see the diagnostic example) when you
need it. See `EMBEDDING.md` § Observability.
