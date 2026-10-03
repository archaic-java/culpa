# Culpa

JDK-only logging contexts for Archaic Java. Each context owns its configuration, collected
failure evidence and execution outcome. Contexts run synchronously on the calling thread;
no global installation or separate thread is required.

Select a logging provider once at composition time, then create independent contexts:

```java
var providers = java.util.ServiceLoader.load(work.archaic.service.logging.v03.Log.class)
        .stream().toList();
if (providers.size() != 1) throw new IllegalStateException("Exactly one logging provider required");
var logging = providers.getFirst().get();
var context = logging.context();
context.run(() -> new Reconciler().reconcile());
```

Application objects depend only on the catalog and implement Logging:

```java
import work.archaic.service.logging.v03.Logging;

final class Reconciler implements Logging {
    void reconcile() throws java.io.IOException {
        logOnFailure("Reading current state");
        logOnDebug(() -> expensiveStateDescription());
        // Read and update remote state; let unrecovered failures escape.
        logImmediately("Reconciled");
    }
    private String expensiveStateDescription() { return "Detailed state"; }
}
```

The consumer requires `work.archaic.service.catalog` and declares
`uses work.archaic.service.logging.v03.Log`; resolve `work.archaic.culpa` at runtime.

- logImmediately(String) publishes within the active context.
- logOnDebug(Supplier<String>) computes and publishes only if debug is enabled in this context.
- logOnFailure(String) retains timestamped evidence for publication on failure.
- context.fail(String), or Logging.context().fail(String), marks a handled failure without throwing.

A successful execution discards evidence. An escaping exception/error publishes once and is
rethrown unchanged, preserving checked exceptions. An explicit mark is sticky and retains the
first reason. Each context runs once; its scope restores the enclosing context on every exit.
Nested contexts have independent configuration, evidence and outcomes. A recovered inner failure
need not fail its parent. Child threads create their own contexts; sharing active contexts across
threads is unsupported. Calls outside an active context fail explicitly.

Disabled debug never invokes its supplier. Enabled debug evaluates it exactly once on the calling
thread and rejects null results. A supplier exception escaping the scope fails the context.
Put expensive work inside the lambda body. Override loggingName() for instance names;
otherwise the source is the implementing class's full name.

## Configure a context

The provider's default context uses UTC and stdout, with debug disabled, 256 retained evidence
entries and 2048 UTF-16 units per text field. Clipped fields end with `…`; reports state how
many earlier entries were dropped. Source and message are captured when submitted. Evidence
retention bounds exclude input construction and throwable graphs.

Custom output and debug are selected through the catalog, including with a service-loaded provider:

```java
var settings = new work.archaic.service.logging.v03.Configuration(
    true,
    entry -> renderEntryToStderr(entry),
    report -> renderFailureToStderr(report));
var context = logging.context(settings);
context.run(() -> application.execute());
```

The render methods are application-defined. The full Configuration constructor also accepts a
Clock, capacity and field limit; capacity must be >= 1, field limit >= 2. Configuration is immutable
and reusable; context instances are single-use. Shared sinks must support concurrent calls.
There is no mutable provider-wide debug flag or global install step, so tests can configure
independent contexts and reuse application objects safely in one JVM.

The default renderer prints timestamp, source and message, escaping entry newlines. Failure
reports print retained evidence, loss count, first explicit reason and the original exception stack
trace. Complete default reports synchronize on their destination stream so independent contexts
do not interleave reports. Custom sinks run synchronously after scope binding restoration.
Their exceptions are suppressed onto an escaping application failure; output failure after an
explicitly marked normal return escapes. Context evidence is released even if output fails.
Default PrintStream output follows JDK PrintStream error behaviour and is not durable.

See the [logging v03 contract](https://github.com/archaic-java/service-catalog/blob/main/docs/logging-v03.md)
for precise semantics. This project does not migrate logging v01/v02 consumers.

## Build and verify

Use JDK 25. Check out service-catalog and minau beside this repository; lib/src links to their
named modules. CI uses the exact revisions in dependencies.env. No build tool, classpath,
external libraries or preview flags are required.

```sh
javac @cmd/compile
java @cmd/test
java @cmd/run
```

Minau runs reusable catalog conformance cases and provider-specific checks. The example prints
a handled failure report on the main thread. Generated classes are ignored in out/.
