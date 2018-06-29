package org.minperf.bloom;

import org.minperf.BitBuffer;
import org.minperf.hem.RandomGenerator;

public class MPHFilter {

    public static void main(String... args) {
        for(int bitsPerKey = 3; bitsPerKey < 20; bitsPerKey++) {
            test(bitsPerKey);
        }
    }

    public static void test(int bitsPerKey) {
        int testCount = 1;
        int len = 1 * 1024 * 1024;
        // System.out.println("MPHFilter " + len);
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long time = System.nanoTime();
        MPHFilter f = new MPHFilter(list, len, bitsPerKey);
        time = System.nanoTime() - time;
        // System.out.println("add: " + time / len + " ns/key");
        time = System.nanoTime();
        int falsePositives = 0, falseNegatives = 0;
        for (int test = 0; test < testCount; test++) {
            for (int i = len; i < len * 2; i++) {
                if (f.mayContain(list[i])) {
                    falsePositives++;
                }
            }
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(list[i])) {
                    f.mayContain(list[i]);
                    falseNegatives++;
                }
            }
        }
        time = System.nanoTime() - time;
        if (falseNegatives > 0) {
            throw new AssertionError("false negatives: " + falseNegatives);
        }
        // System.out.println("get: " + time / len / testCount + " ns/key");
        System.out.println("false positives: " + (100. / testCount / len * falsePositives) +
                "% " + (double) f.getBitCount() / len + " bits/key");
    }

    private final int mask;
    private final FastEvaluator eval;
    private final int bitCount;

    MPHFilter(long[] hashes, int len, int bits) {
        int averageBucketSize = 10;
        int leafSize = 6;
        mask = (1 << bits) - 1;
        BitBuffer fingerprints = new BitBuffer(bits * len);
        // long time = System.nanoTime();
        BitBuffer buff = new Builder().
                averageBucketSize(averageBucketSize).
                leafSize(leafSize).
                fingerprintBits(bits).
                generate(hashes, len, fingerprints);
        // time = System.nanoTime() - time;
        bitCount = len * bits + buff.position();
        // System.out.println("    generate: " + ((double) time / len) + " ns/key");
        buff.seek(0);
        eval = new Builder().
                averageBucketSize(averageBucketSize).
                leafSize(leafSize).
                fingerprintBits(bits).
                evaluator(buff, fingerprints);
    }

    int getBitCount() {
        return bitCount;
    }

    boolean mayContain(long hashCode) {
        int h = eval.getFingerprint(hashCode);
        long h2 = hashCode & mask;
        return h == h2;
    }

}
