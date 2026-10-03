package work.archaic.culpa;

import java.io.PrintStream;
import work.archaic.service.logging.v03.*;

/** Serialize a complete failure report so concurrent reports do not interleave. */
final class Output {
    private final PrintStream stream;
    Output(PrintStream stream) { this.stream = stream; }
    void entry(Entry entry) { synchronized (stream) { stream.println(render(entry)); } }
    void failure(FailureReport report) {
        synchronized (stream) {
            stream.println("--- failed logging context ---");
            if (report.dropped() != 0) stream.println("[" + report.dropped() + " earlier entries dropped]");
            report.evidence().forEach(entry -> stream.println(render(entry)));
            if (report.explicitFailure() != null)
                stream.println("Failure: " + render(report.explicitFailure()));
            if (report.cause() != null) report.cause().printStackTrace(stream);
            stream.println("--- end context ---");
        }
    }
    private String render(Entry entry) {
        return entry.timestamp() + " " + escape(entry.source()) + " " + escape(entry.message());
    }
    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
    }
}
