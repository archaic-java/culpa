package work.archaic.culpa.example;

import java.util.ServiceLoader;
import work.archaic.service.logging.v03.*;

public final class Main implements Logging {
    public static void main(String[] args) throws Exception {
        var providers = ServiceLoader.load(Log.class).stream().toList();
        if (providers.size() != 1) throw new IllegalStateException("Exactly one logging provider required");
        Logging.install(providers.getFirst().get());
        var app = new Main();
        app.logImmediately("Culpa example on " + Thread.currentThread().getName());
        Logging.trail(() -> {
            app.logOnFailure("Reading configuration");
            app.logOnFailure("Checking remote state");
            Logging.failure("Example: remote state unavailable");
        });
    }
}
