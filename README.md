# Culpa

Small, JDK-only logging for Archaic Java. Implement `Logging` to log without owning a logger.
Collect a trail around ordinary execution and publish its evidence when the execution fails.
**Trails run synchronously on the calling thread. No separate thread or executor is required.**

```java
import work.archaic.service.logging.v03.Logging;

final class Reconciler implements Logging {
    void reconcile() throws java.io.IOException {
        onFailure("Reading current state");
        // Read and update remote state here. Let failures escape.
        immediately("Reconciled");
    }
}
```

Select a provider once at startup, then establish the boundary:

```java
var providers = java.util.ServiceLoader.load(work.archaic.service.logging.v03.Log.class)
        .stream().toList();
if (providers.size() != 1) throw new IllegalStateException("Exactly one logging provider required");
Logging.install(providers.getFirst().get());
Logging.trail(() -> new Reconciler().reconcile());
```

The consuming module requires `work.archaic.service.catalog` and declares
`uses work.archaic.service.logging.v03.Log`. Resolve `work.archaic.culpa` at launch.

- `immediately(String)` publishes now.
- `onDebug(String)` publishes now when `Logging.debug(true)` is enabled; debug starts disabled.
- `onFailure(String)` retains timestamped evidence for the current trail.
- `Logging.failure(String)` marks a handled failure without throwing.

Normal completion discards evidence. An escaping exception/error publishes once and is
rethrown unchanged, including checked exceptions. Nested trails join the outer execution;
handled and recovered exceptions do not trigger publication. Explicit failure retains the
first reason and publishes at outer completion. Calls that need a trail fail explicitly outside
one. Independent concurrent executions are isolated; child threads establish their own trails.
Override `loggingName()` for instance names; the default is the implementing class's full name.

## Output and configuration

The service provider defaults to UTC timestamps and stdout. Immediate lines contain timestamp,
source and message. Failure reports contain retained evidence, dropped-entry count, the first
explicit failure reason and the original exception stack trace. Complete reports are serialized
by the default sink, with newlines escaped in entry text. Evidence retains the newest 256 entries;
source/message fields are limited to 2048 UTF-16 units, with `…` indicating clipping. Input
construction and throwable graphs are outside the retention bounds.

Applications needing custom output can explicitly construct `Culpa(Clock, Consumer<Entry>,
Consumer<FailureReport>, capacity, fieldLimit)` at composition time. Capacity must be >= 1
and field limit >= 2. Sinks run synchronously and must support concurrent calls. Application
objects still depend only on the catalog. Sink exceptions are attached as suppressed to an
escaping application failure; an output error after an explicitly marked normal return escapes.
The default PrintStream follows the JDK's PrintStream error behaviour; output is not durable.

See the [logging v03 contract](https://github.com/archaic-java/service-catalog/blob/main/docs/logging-v03.md)
for precise semantics. Logging v01/v02 remain available; this project does not migrate Peep consumers.

## Build and verify

Use JDK 25. Check out `service-catalog` and `minau` beside this repository. The source links
in `lib/src` point to their named modules. Dependency revisions used by CI are in `dependencies.env`.
No build tool, classpath, external libraries or preview flags are needed.

```sh
javac @cmd/compile
java @cmd/test
java @cmd/run
```

Tests use Minau and the catalog's reusable logging v03 conformance cases, plus provider checks.
The example prints a handled failure report on the main thread. Generated classes live in `out/`.
