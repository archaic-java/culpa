package work.archaic.culpa;

import java.util.Objects;
import work.archaic.service.logging.v03.Configuration;
import work.archaic.service.logging.v03.Context;
import work.archaic.service.logging.v03.Entry;

/** Evidence belongs to this execution; no provider-global mutable state. */
final class CulpaContext extends Context {
    private final Buffer buffer;

    CulpaContext(Configuration configuration) {
        super(configuration);
        buffer = new Buffer(configuration.capacity());
    }

    @Override public void immediately(String source, String message) {
        requireActive();
        configuration().entries().accept(entry(source, message));
    }
    @Override public void onFailure(String source, String message) {
        requireActive();
        buffer.add(entry(source, message));
    }
    @Override public void fail(String reason) {
        requireActive();
        buffer.fail(entry(Context.class.getName(), reason));
    }
    @Override protected void finish(Throwable cause) {
        try {
            if (cause != null || buffer.failed())
                configuration().failures().accept(buffer.report(cause));
        } finally {
            buffer.clear();
        }
    }
    private Entry entry(String source, String message) {
        return new Entry(configuration().clock().instant(), clip(source), clip(message));
    }
    private String clip(String text) {
        Objects.requireNonNull(text, "text");
        int limit = configuration().fieldLimit();
        if (text.length() <= limit) return text;
        int end = limit - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1)) &&
                Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end) + "…";
    }
}
