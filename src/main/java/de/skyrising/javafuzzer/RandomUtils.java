package de.skyrising.javafuzzer;

import java.util.Random;

class RandomUtils {

    static int triangular(Random random, int n) {
        return random.nextInt(n) - random.nextInt(n);
    }

    static int bitsLogarithmic(Random random) {
        return Integer.numberOfLeadingZeros(random.nextInt());
    }

    static int pickLength(Random random, int max) {
        if (max <= 0) return 0;
        int chance = random.nextInt(100);
        if (chance == 0) {
            return random.nextInt(max) + 1;
        } else if (chance <= 10) {
            return random.nextInt(Math.min(32, max)) + 1;
        } else {
            return random.nextInt(Math.min(8, max)) + 1;
        }
    }
}
