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
        LoggingV03ProviderContract.cases(cases, () -> {
            var entries = new CopyOnWriteArrayList<Entry>();
            var reports = new CopyOnWriteArrayList<FailureReport>();
            var log = new Culpa(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), entries::add, reports::add, 2, 32);
            return new LoggingV03ProviderContract.Fixture(log, entries, reports);
        });
        cases.add(new SinkFailure());
        cases.add(new Facade());
        cases.add(new Discovery());
        cases.add(new TimestampAtSubmission());
        cases.add(new ReportValidation());
    }
}

record SinkFailure() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var sinkError = new IllegalStateException("sink failed");
        var log = new Culpa(Clock.systemUTC(), entry -> {}, report -> { throw sinkError; }, 2, 32);
        var original = new java.io.IOException("original");
        try {
            log.trail(() -> { throw original; });
            assert false : "Original failure must escape";
        } catch (java.io.IOException caught) {
            assert caught == original : "Sink failure must not replace application failure";
            assert List.of(caught.getSuppressed()).contains(sinkError) : "Preserve sink failure as suppressed";
        }
        log.trail(() -> {});
        try {
            log.trail(() -> log.failure("worker", "explicit"));
            assert false : "Sink failure on normal return must escape";
        } catch (IllegalStateException caught) {
            assert caught == sinkError : "Explicit failure output error must stay observable";
        }
        log.trail(() -> {});
    }
}

record Worker(String name) implements Logging {
    public String loggingName() { return name; }
}

record Facade() implements TestCase {
    public void run(TestTrail test) throws Exception {
        try { Logging.debug(); assert false : "Use before installation must fail"; }
        catch (IllegalStateException expected) { }
        var entries = new ArrayList<Entry>();
        var reports = new ArrayList<FailureReport>();
        Logging.install(new Culpa(Clock.systemUTC(), entries::add, reports::add, 8, 100));
        var first = new Worker("first");
        var second = new Worker("second");
        Logging.trail(() -> {
            first.onFailure("first evidence");
            second.onFailure("second evidence");
            Logging.failure("handled failure");
        });
        assert reports.size() == 1 && reports.getFirst().evidence().size() == 2 : "Objects must contribute to the same trail";
        assert reports.getFirst().evidence().getFirst().source().equals("first") : "Use overridden object name";
        Logging.debug(true);
        first.onDebug("debug");
        second.immediately("immediate");
        assert entries.size() == 2 : "Default methods must delegate to shared provider";
        assert new Logging() {}.loggingName().contains("Facade") : "Default name identifies implementing class";
        try { Logging.install(new Culpa()); assert false : "Reject provider replacement"; }
        catch (IllegalStateException expected) { }
        Logging.debug(false);
    }
}

record Discovery() implements TestCase {
    public void run(TestTrail test) {
        var providers = ServiceLoader.load(Log.class).stream().toList();
        assert providers.size() == 1 : "Resolve exactly one logging v03 provider";
        assert providers.getFirst().get() instanceof Culpa : "Discover Culpa through JPMS";
    }
}

record TimestampAtSubmission() implements TestCase {
    public void run(TestTrail test) throws Exception {
        var reports = new ArrayList<FailureReport>();
        var clock = new AdvancingClock();
        var log = new Culpa(clock, entry -> {}, reports::add, 8, 100);
        log.trail(() -> {
            log.onFailure("worker", "before");
            clock.now = Instant.EPOCH.plusSeconds(10);
            log.failure("worker", "later");
        });
        var report = reports.getFirst();
        assert report.evidence().getFirst().timestamp().equals(Instant.EPOCH) : "Timestamp must be captured before publication";
        assert report.explicitFailure().timestamp().equals(clock.now) : "Capture explicit failure time independently";
    }
}

final class AdvancingClock extends Clock {
    Instant now = Instant.EPOCH;
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
}

record ReportValidation() implements TestCase {
    public void run(TestTrail test) {
        var reason = new Entry(Instant.EPOCH, "source", "reason");
        var entries = new ArrayList<Entry>(); entries.add(reason);
        var report = new FailureReport(entries, 0, reason, null);
        entries.clear();
        assert report.evidence().size() == 1 : "Reports must take an independent snapshot";
        try { new FailureReport(List.of(), 0, null, null); assert false : "Require a cause or reason"; }
        catch (IllegalArgumentException expected) { }
        try { new FailureReport(List.of(), -1, reason, null); assert false : "Reject negative loss count"; }
        catch (IllegalArgumentException expected) { }
        try { new Entry(null, "source", "message"); assert false : "Require timestamp"; }
        catch (NullPointerException expected) { }
    }
}
