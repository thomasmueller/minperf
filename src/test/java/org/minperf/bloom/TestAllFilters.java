package org.minperf.bloom;

import org.minperf.hem.RandomGenerator;

public class TestAllFilters {

    public static void main(String... args) {
        for (Filter.Type type : Filter.Type.values()) {
            test(type);
        }
    }

    private static void test(Filter.Type type) {
        int len = 1000000;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long[] keys = new long[len];
        long[] nonKeys = new long[len];
        // first half is keys, second half is non-keys
        for (int i = 0; i < len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }
        Filter f = type.construct(keys, 8);
        // each key in the set needs to be found
        for (int i = 0; i < len; i++) {
            if (!f.mayContain(keys[i])) {
                f.mayContain(keys[i]);
                throw new AssertionError();
            }
        }
        // non keys _may_ be found - this is used to calculate false
        // positives
        int falsePositives = 0;
        for (int i = 0; i < len; i++) {
            if (f.mayContain(nonKeys[i])) {
                falsePositives++;
            }
        }
        double fpp = (double) falsePositives / len;
        System.out.println(type + " fpp " + fpp);
    }

}
