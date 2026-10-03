package work.archaic.culpa.example;

import java.util.ServiceLoader;
import work.archaic.service.logging.v03.*;

public final class Main implements Logging {
    public static void main(String[] args) throws Exception {
        var providers = ServiceLoader.load(Log.class).stream().toList();
        if (providers.size() != 1) throw new IllegalStateException("Exactly one logging provider required");
        var logging = providers.getFirst().get();
        var context = logging.context();
        var app = new Main();
        context.run(() -> {
            app.logImmediately("Culpa example on " + Thread.currentThread().getName());
            app.logOnDebug(() -> "Only computed with debug enabled: " + Runtime.version());
            app.logOnFailure("Reading configuration");
            app.logOnFailure("Checking remote state");
            context.fail("Example: remote state unavailable");
        });
    }
}
