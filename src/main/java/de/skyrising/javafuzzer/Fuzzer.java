package de.skyrising.javafuzzer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Fuzzer {
    private final Corpus corpus;
    private final FuzzTarget target;
    private final Path crashPath;

    private final Set<List<StackTraceElement>> crashes = new HashSet<>();

    private final Set<CoveragePoint> coverage = new HashSet<>();

    private long lastReportTime;
    private long executionsLastReport;
    private long crashCountLastReport;

    public Fuzzer(Corpus corpus, FuzzTarget target, Path crashPath) {
        this.corpus = corpus;
        this.target = target;
        this.crashPath = crashPath;
    }

    public void start(int threads) {
        FuzzRunner[] runners = new FuzzRunner[threads];
        for (int i = 0; i < threads; i++) {
            runners[i] = startRunner(i);
        }
        System.out.println(target);
        lastReportTime = System.currentTimeMillis();
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            if (System.currentTimeMillis() - lastReportTime > 3_000L) {
                report(runners);
            }
        }
    }

    private FuzzRunner startRunner(int i) {
        FuzzRunner runner = new FuzzRunner(this, corpus, target);
        runner.thread = new Thread(runner, "Fuzz Runner " + (i + 1));
        runner.thread.start();
        return runner;
    }

    private Throwable runInput(byte[] buf) {
        try {
            target.fuzz(buf);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private void report(FuzzRunner[] runners) {
        long now = System.currentTimeMillis();
        long delta = now - lastReportTime;
        long executions = 0;
        long crashCount = 0;
        for (int i = 0; i < runners.length; i++) {
            FuzzRunner runner = runners[i];
            executions += runner.executions;
            crashCount += runner.crashCount;
            if (!runner.responding) {
                System.out.println("Restarting runner #" + i);
                runner.thread.interrupt();
                runners[i] = startRunner(i);
            }
            runner.responding = false;
        }
        double rate = (executions - executionsLastReport) * 1e3 / delta;
        double crashRate = (crashCount - crashCountLastReport) * 1e3 / delta;
        System.out.printf("coverage: %d, unique crashes: %d, total crashes: %d (%.1f/s, %.1f%%), total executions: %d (%.1f/s)\n", coverage.size(), crashes.size(), crashCount, crashRate, crashCount * 100.0 / executions, executions, rate);
        lastReportTime = now;
        executionsLastReport = executions;
        crashCountLastReport = crashCount;
    }

    private synchronized void addCrash(List<StackTraceElement> stackTrace, Throwable t, byte[] minimized) {
        if (!crashes.add(stackTrace)) return;
        String joined = stackTrace.stream().map(StackTraceElement::toString).collect(Collectors.joining("\n"));
        String name = Corpus.generateFileName("crash", joined.getBytes(StandardCharsets.UTF_8));
        try {
            Files.createDirectories(crashPath);
            Files.write(crashPath.resolve(name + ".bin"), minimized);
            String info = getFullStackTrace(t) + "\n\n" + joined;
            Files.writeString(crashPath.resolve(name + ".txt"), info);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    synchronized boolean addCoverage(CoveragePoint newCoverage) {
        return coverage.add(newCoverage);
    }

    void handleCrash(byte[] buf, Throwable t) {
        List<StackTraceElement> stackTrace = getStackTrace(t);
        if (crashes.contains(stackTrace)) return;
        byte[] minimized = buf;
        for (int length = buf.length; length > 0; length--) {
            byte[] partial = Arrays.copyOf(buf, length);
            Throwable t2 = runInput(partial);
            if (t2 != null && getStackTrace(t2).equals(stackTrace)) {
                minimized = partial;
            } else {
                break;
            }
        }
        addCrash(stackTrace, t, minimized);
    }

    private static String getFullStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static List<StackTraceElement> getStackTrace(Throwable t) {
        List<StackTraceElement> stackTrace = new ArrayList<>();
        for (Throwable t1 = t; t1 != null; t1 = t1.getCause()) {
            addStackTrace(t1, stackTrace);
        }
        return stackTrace;
    }

    private static void addStackTrace(Throwable t, List<StackTraceElement> elements) {
        for (StackTraceElement element : t.getStackTrace()) {
            if (element.getClassName().startsWith("de.skyrising.javafuzzer.")) continue;
            elements.add(element);
        }
    }

    record CoveragePoint(int classId, int probeId, int count) {}
}
