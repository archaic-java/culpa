# Culpa

Use JDK 25, explicit JPMS modules and the checked-in javac/java argument files.
Do not add Maven, Gradle, classpath dependencies or preview features.
Source dependencies are sibling service-catalog and minau checkouts, linked in lib/src.
Compile with `javac @cmd/compile`; verify with `java @cmd/test` and `java @cmd/run`.
Logging v03 is immutable and belongs to service-catalog. Preserve logging v01/v02.
Trail boundaries execute on the calling thread and must never require an executor.
