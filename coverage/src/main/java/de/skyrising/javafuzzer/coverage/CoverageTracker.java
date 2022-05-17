package de.skyrising.javafuzzer.coverage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;

public class CoverageTracker {
    static boolean loaded;

    private static final List<ClassCounterContainer> CONTAINERS = new ArrayList<>();

    public static class ClassCounterContainer {
        public final int classId;
        public final ThreadLocal<int[]> count;

        public ClassCounterContainer(int classId, int probeCount) {
            this.classId = classId;
            this.count = ThreadLocal.withInitial(() -> new int[probeCount]);
            synchronized (CONTAINERS) {
                CONTAINERS.add(this);
            }
        }

        @SuppressWarnings("unused")
        public void hit(int id) {
            count.get()[id]++;
        }
    }

    @FunctionalInterface
    public interface CoverageConsumer {
        void accept(int classId, int[] probes);
    }

    public static void collect(boolean clear, CoverageConsumer consumer) {
        if (!clear && consumer == null) return;
        while (true) {
            try {
                synchronized (CONTAINERS) {
                    for (ClassCounterContainer container : CONTAINERS) {
                        int[] count = container.count.get();
                        if (consumer != null) consumer.accept(container.classId, count);
                        if (clear) Arrays.fill(count, 0);
                    }
                }
                return;
            } catch (ConcurrentModificationException ignored) {}
        }
    }

    public static long getProbeHitCount(boolean reset) {
        long[] hits = {0};
        collect(reset, (cls, counts) -> {
            for (int count : counts) {
                if (count > 0) hits[0]++;
            }
        });
        return hits[0];
    }

    public static long getTotalProbeHitCount(boolean reset) {
        long[] hits = {0};
        collect(reset, (cls, counts) -> {
            for (int count : counts) {
                hits[0] += count;
            }
        });
        return hits[0];
    }

    public static void reset() {
        collect(true, null);
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
