package work.archaic.culpa.test;

import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import work.archaic.culpa.Culpa;
import work.archaic.service.catalog.test.LoggingV03ProviderContract;
import work.archaic.service.logging.v03.*;
import work.archaic.service.test.v02.*;

public record CulpaTests() implements TestSuite {
    public void cases(Collection<TestCase> cases) {
        LoggingV03ProviderContract.cases(cases, () -> new LoggingV03ProviderContract.Fixture(
            new Culpa(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>()));
        cases.add(new ContextSinkFailure());
        cases.add(new ContextDiscovery());
        cases.add(new ContextTimestamp());
        cases.add(new ContextConfiguration());
        cases.add(new ValueSinkFailure());
    }
}

record ContextSinkFailure() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var sinkError = new IllegalStateException("sink failed");
        var log = new Culpa();
        var settings = new Configuration(false, entry -> {}, report -> { throw sinkError; });
        var original = new java.io.IOException("original");
        var context = log.context(settings);
        try {
            context.run(() -> { throw original; });
            assert false : "Original failure must escape";
        } catch (java.io.IOException caught) {
            assert caught == original : "Sink failure must not replace application failure";
            assert List.of(caught.getSuppressed()).contains(sinkError) : "Preserve sink failure as suppressed";
        }
        try { Logging.context(); assert false : "Failed sink must not leave a context binding"; }
        catch (IllegalStateException expected) { }
        var explicit = log.context(settings);
        try {
            explicit.run(() -> explicit.fail("explicit"));
            assert false : "Sink error after normal completion must escape";
        } catch (IllegalStateException caught) {
            assert caught == sinkError : "Keep publication failures observable";
        }
        try { explicit.run(() -> {}); assert false : "Publication error still completes the context"; }
        catch (IllegalStateException expected) { }
        log.context(settings).run(() -> {});
    }
}

record ContextDiscovery() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var providers = ServiceLoader.load(Log.class).stream().toList();
        assert providers.size() == 1 : "Resolve exactly one logging v03 provider";
        var log = providers.getFirst().get();
        assert log instanceof Culpa : "Discover Culpa through JPMS";
        var context = log.context();
        assert !context.configuration().debug() : "Default debug is disabled";
        assert context.configuration().capacity() == 256 : "Default evidence capacity is documented";
        context.run(() -> {});
    }
}

record ContextTimestamp() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var reports = new ArrayList<FailureReport>(); var clock = new AdvancingClock();
        var context = new Culpa().context(new Configuration(false, clock, entry -> {}, reports::add, 8, 100));
        context.run(() -> {
            context.onFailure("worker", "before");
            clock.now = Instant.EPOCH.plusSeconds(10);
            context.fail("later");
        });
        var report = reports.getFirst();
        assert report.evidence().getFirst().timestamp().equals(Instant.EPOCH) : "Timestamp is captured at submission";
        assert report.explicitFailure().timestamp().equals(clock.now) : "Capture explicit failure time independently";
    }
}

final class AdvancingClock extends Clock {
    Instant now = Instant.EPOCH;
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
}

record ContextConfiguration() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var log = new Culpa();
        var firstEntries = new ArrayList<Entry>(); var secondEntries = new ArrayList<Entry>();
        var first = log.context(new Configuration(false, firstEntries::add, report -> {}));
        var second = log.context(new Configuration(true, secondEntries::add, report -> {}));
        var worker = new Worker();
        first.run(() -> { worker.logImmediately("first"); worker.logOnDebug(() -> { throw new AssertionError("disabled"); }); });
        second.run(() -> worker.logOnDebug(() -> "second"));
        assert firstEntries.size() == 1 && firstEntries.getFirst().message().equals("first") : "First context uses its own sink";
        assert secondEntries.size() == 1 && secondEntries.getFirst().message().equals("second") : "Reuse application object with independently configured context";
        try { new Configuration(false, Clock.systemUTC(), firstEntries::add, report -> {}, 0, 32); assert false : "Reject unbounded or empty evidence capacity"; }
        catch (IllegalArgumentException expected) { }
        try { new Configuration(false, Clock.systemUTC(), firstEntries::add, report -> {}, 2, 1); assert false : "Field limit must fit clipping marker safely"; }
        catch (IllegalArgumentException expected) { }
    }
}
record Worker() implements Logging {}

record ValueSinkFailure() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var outputFailure = new IllegalStateException("value sink failed");
        var log = new Culpa();
        var configuration = new Configuration(false, entry -> {}, report -> { throw outputFailure; });
        var marked = log.context(configuration);
        try {
            marked.call(() -> { marked.fail("returning failure"); return 42; });
            assert false : "A return value must not hide failed publication";
        } catch (IllegalStateException caught) {
            assert caught == outputFailure : "Preserve output failure before returning a value";
        }
        var original = new java.io.IOException("checked call");
        try { log.context(configuration).call(() -> { throw original; }); assert false : "Checked call failure must escape"; }
        catch (java.io.IOException caught) {
            assert caught == original && List.of(caught.getSuppressed()).contains(outputFailure)
                    : "Failed returning work must preserve original failure and suppressed sink error";
        }
    }
}
