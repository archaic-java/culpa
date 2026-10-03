package work.archaic.culpa;

import java.util.ArrayDeque;
import java.util.List;
import work.archaic.service.logging.v03.Entry;
import work.archaic.service.logging.v03.FailureReport;

/** Bounded evidence for one thread-confined context. */
final class Buffer {
    private final ArrayDeque<Entry> evidence = new ArrayDeque<>();
    private final int capacity;
    private long dropped;
    private Entry failure;

    Buffer(int capacity) { this.capacity = capacity; }
    void add(Entry entry) {
        if (evidence.size() == capacity) { evidence.removeFirst(); dropped++; }
        evidence.addLast(entry);
    }
    void fail(Entry reason) { if (failure == null) failure = reason; }
    boolean failed() { return failure != null; }
    FailureReport report(Throwable cause) {
        return new FailureReport(List.copyOf(evidence), dropped, failure, cause);
    }
    void clear() { evidence.clear(); failure = null; dropped = 0; }
}
