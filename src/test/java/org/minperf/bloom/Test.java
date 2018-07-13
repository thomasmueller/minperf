package org.minperf.bloom;

import org.minperf.hem.RandomGenerator;

public class Test {

    // TODO test with low-entropy keys

    public static void main(String... args) {
        System.out.println("Warmup");
        test(true, 100_000, 100_000, 0.001, 0.01);
        System.out.println();
        System.out.println("Test fpp versus bits/key at 1 million keys");
        test(false, 1_000_000, 1_000_000, 0.001, 0.01);
        System.out.println("Test speed at 0.01 fpp with 1 - 64 million keys");
        for (Filter.Type type : Filter.Type.values()) {
            int setting = 8;
            switch(type) {
            case BLOOM:
                setting = 10;
                break;
            case XOR8:
                setting = 7;
                break;
            case XOR16:
                setting = 7;
                break;
            case XOR:
                setting = 7;
                break;
            case CUCKOO:
                setting = 9;
                break;
            case CUCKOO8_4:
                setting = 0;
                break;
            case CUCKOO16_4:
                setting = 0;
                break;
            }
            test(type, false, 1_000_000, 128_000_000, setting, setting, 0.001, 0.1);
        }
    }

    private static void test(boolean warmup, int minLen, int maxLen, double minFpp, double maxFpp) {
        for (Filter.Type type : Filter.Type.values()) {
            if (warmup) {
                System.out.print(type.name().toLowerCase());
            }
            test(type, warmup, minLen, maxLen, 10, 10, minFpp, maxFpp);
        }
    }

    private static void test(Filter.Type type, boolean warmup,
            int minLen, int maxLen,
            int minSetting, int maxSetting,
            double minFpp, double maxFpp) {
        int validFpp = 0;
        for(int len = minLen; len <= maxLen; len *= 2) {
            // the list of entries: the first half is keys in the filter,
            // the second half is _not_ in the filter, but used to calculate false
            // positives
            long[] list = new long[len * 2];

            RandomGenerator.createRandomUniqueListFast(list, len);

            // the keys
            long[] keys = new long[len];

            // the list of non-keys, used to calculate false positives
            long[] nonKeys = new long[len];

            for(int setting = minSetting; setting <= maxSetting; setting++) {

                // first half is keys, second half is non-keys
                for(int i = 0; i<len; i++) {
                    keys[i] = list[i];
                    nonKeys[i] = list[i + len];
                }

                // construction
                if (!warmup) {
                    sleep(100);
                }
                long time = System.nanoTime();
                Filter f = type.construct(keys, setting);
                long constructTime = (System.nanoTime() - time) / len;

                // lookup
                if (!warmup) {
                    sleep(100);
                }
                time = System.nanoTime();
                // each key in the set needs to be found
                for (int i = 0; i < len; i++) {
                    if (!f.mayContain(keys[i])) {
                        throw new AssertionError();
                    }
                }
                long lookupInSetTime = (System.nanoTime() - time) / len;

                if (!warmup) {
                    sleep(100);
                }
                time = System.nanoTime();
                // non keys _may_ be found - this is used to calculate false
                // positives
                int falsePositives = 0;
                for (int i = 0; i < len; i++) {
                    if (f.mayContain(nonKeys[i])) {
                        falsePositives++;
                    }
                }
                long lookupNotInSetTime = (System.nanoTime() - time) / len;
                double fpp = (double) falsePositives / len;
                if (fpp > maxFpp) {
                    // skip: fpp too large
                    continue;
                }
                if (fpp < minFpp && validFpp >= 1) {
                    // stop: fpp too low
                    break;
                }
                validFpp++;
                double bitsPerKeyUsed = (double) f.getBitCount() / len;
                if (warmup) {
                    System.out.print(".");
                } else {
                    System.out.println(
                            f.getClass().getSimpleName() +
                            " fpp " + fpp +
                            " bits/key " + bitsPerKeyUsed +
                            " construct " + constructTime +
                            " lookup-in-set " + lookupInSetTime +
                            " lookup-not-in-set " + lookupNotInSetTime +
                            " count " + len +
                            " setting " + setting);
                }
            }
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
