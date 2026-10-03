# Culpa

Use JDK 25, explicit JPMS modules and the checked-in javac/java argument files.
Do not add Maven, Gradle, classpath dependencies or preview features.
Source dependencies are sibling service-catalog and minau checkouts, linked in lib/src.
Compile with `javac @cmd/compile`; verify with `java @cmd/test` and `java @cmd/run`.
Logging v03 is immutable and belongs to service-catalog. Preserve logging v01/v02.
Logging contexts are configured at creation, single-use and thread-confined.
Use Context.run on the calling thread; no global install, executor or preview features.
Debug messages take suppliers and must not be evaluated when debug is disabled.
