package work.archaic.culpa;

import java.util.Objects;
import work.archaic.service.logging.v03.Configuration;
import work.archaic.service.logging.v03.Context;
import work.archaic.service.logging.v03.Log;

/** JDK-only factory for independent logging contexts. No installation or executor required. */
public final class Culpa implements Log {
    public Culpa() {}

    /** Defaults: stdout, UTC, debug disabled, 256 entries and 2048 UTF-16 units per field. */
    @Override public Context context() {
        return context(Configuration.text(false, System.out));
    }

    @Override public Context context(Configuration configuration) {
        return new CulpaContext(Objects.requireNonNull(configuration, "configuration"));
    }
}
