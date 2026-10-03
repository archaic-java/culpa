package work.archaic.culpa;

import java.util.ArrayDeque;
import java.util.List;
import work.archaic.service.logging.v03.*;

/** Mutable evidence confined to one thread and one outer trail. */
final class Buffer {
    private final ArrayDeque<Entry> evidence = new ArrayDeque<>();
    private final int capacity;
    private final Thread owner = Thread.currentThread();
    private long dropped;
    private Entry failure;

    Buffer(int capacity) { this.capacity = capacity; }
    void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Trail belongs to another thread");
    }
    void add(Entry entry) {
        if (evidence.size() == capacity) { evidence.removeFirst(); dropped++; }
        evidence.addLast(entry);
    }
    void fail(Entry reason) { if (failure == null) failure = reason; }
    boolean failed() { return failure != null; }
    FailureReport report(Throwable cause) {
        return new FailureReport(List.copyOf(evidence), dropped, failure, cause);
    }
}
