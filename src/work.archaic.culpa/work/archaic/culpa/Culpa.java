package work.archaic.culpa;

import java.lang.ScopedValue;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;
import work.archaic.service.logging.v03.*;

/** JDK-only logging v03 provider. Every operation executes on its calling thread. */
public final class Culpa implements Log {
    private final ScopedValue<Buffer> current = ScopedValue.newInstance();
    private final Clock clock;
    private final Consumer<Entry> entries;
    private final Consumer<FailureReport> failures;
    private final int capacity;
    private final int messageLimit;
    private volatile boolean debug;

    /** Defaults: stdout, UTC, 256 retained entries, 2048 UTF-16 units per field. */
    public Culpa() { this(new Output(System.out)); }
    private Culpa(Output output) {
        this(Clock.systemUTC(), output::entry, output::failure, 256, 2048);
    }

    /** Configure sinks and retention at startup. Sinks must support concurrent callers. */
    public Culpa(Clock clock, Consumer<Entry> entries, Consumer<FailureReport> failures,
                 int capacity, int messageLimit) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.entries = Objects.requireNonNull(entries, "entries");
        this.failures = Objects.requireNonNull(failures, "failures");
        if (capacity < 1 || messageLimit < 2)
            throw new IllegalArgumentException("Capacity >= 1 and field limit >= 2 required");
        this.capacity = capacity;
        this.messageLimit = messageLimit;
    }

    @Override public void debug(boolean enabled) { debug = enabled; }
    @Override public boolean debug() { return debug; }
    @Override public void immediately(String source, String message) {
        entries.accept(entry(source, message));
    }
    @Override public void onDebug(String source, String message) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(message, "message");
        if (debug) immediately(source, message);
    }
    @Override public void onFailure(String source, String message) {
        buffer().add(entry(source, message));
    }
    @Override public void failure(String source, String message) {
        buffer().fail(entry(source, message));
    }

    @Override public <E extends Exception> void trail(Work<E> work) throws E {
        Objects.requireNonNull(work, "work");
        if (current.isBound()) {
            current.get().requireOwner();
            work.run();
            return;
        }
        Buffer buffer = new Buffer(capacity);
        // call's exception type is inferred as E, without casts or sneaky throws.
        ScopedValue.where(current, buffer).call(() -> {
            try {
                work.run();
            } catch (Exception | Error cause) {
                try {
                    failures.accept(buffer.report(cause));
                } catch (Exception | Error sinkFailure) {
                    if (sinkFailure != cause) cause.addSuppressed(sinkFailure);
                }
                throw cause;
            }
            if (buffer.failed()) failures.accept(buffer.report(null));
            return null;
        });
    }

    private Buffer buffer() {
        if (!current.isBound()) throw new IllegalStateException("No active logging trail");
        Buffer buffer = current.get();
        buffer.requireOwner();
        return buffer;
    }
    private Entry entry(String source, String message) {
        return new Entry(clock.instant(), clip(source), clip(message));
    }
    private String clip(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() <= messageLimit) return text;
        int end = messageLimit - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1)) &&
                Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end) + "…";
    }
}
