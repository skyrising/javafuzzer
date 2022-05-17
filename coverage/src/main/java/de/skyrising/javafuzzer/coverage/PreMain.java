package de.skyrising.javafuzzer.coverage;

import java.lang.instrument.Instrumentation;

public class PreMain {
    public static void premain(String options, Instrumentation inst) {
        inst.addTransformer(new CoverageTransformer());
        CoverageTracker.loaded = true;
    }
}
