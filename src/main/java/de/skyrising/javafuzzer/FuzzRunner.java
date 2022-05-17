package de.skyrising.javafuzzer;

import de.skyrising.javafuzzer.coverage.CoverageTracker;

class FuzzRunner implements Runnable {
    private final Fuzzer fuzzer;
    private final Corpus corpus;
    private final FuzzTarget target;
    Thread thread;
    long executions;
    long crashCount;
    volatile boolean stopped;
    volatile boolean responding;

    public FuzzRunner(Fuzzer fuzzer, Corpus corpus, FuzzTarget target) {
        this.fuzzer = fuzzer;
        this.corpus = corpus;
        this.target = target;
    }

    public void run() {
        executions = 0;
        while (!stopped) {
            responding = true;
            byte[] buf = corpus.generate();
            CoverageTracker.reset();
            Throwable t = runInput(buf);
            if (t != null) {
                crashCount++;
                fuzzer.handleCrash(buf, t);
            }
            boolean[] hasNewCoverage = {false};
            CoverageTracker.collect(false, (cls, counts) -> {
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] == 0) continue;
                    hasNewCoverage[0] |= fuzzer.addCoverage(new Fuzzer.CoveragePoint(cls, i, counts[i]));
                }
            });
            if (hasNewCoverage[0]) {
                if (t == null) {
                    corpus.add(buf);
                } else {
                    corpus.addCrash(buf);
                }
            }
        }
    }

    private Throwable runInput(byte[] buf) {
        try {
            target.fuzz(buf);
            return null;
        } catch (Throwable t) {
            return t;
        } finally {
            executions++;
        }
    }
}
