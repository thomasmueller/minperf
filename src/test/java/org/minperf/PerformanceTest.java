package org.minperf;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;

import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * A simple micro-benchmark.
 */
public class PerformanceTest {

    private int size = 100_000;

    // CHD: 2.25 bits/key, 182 nanoseconds/key
    // GOV: 2.32 bits/key, 132 nanoseconds/key

    // RecSplit with FastLongHash:
    // 1.97 bits/key, 163 nanoseconds/key evaluation time
    private int leafSize = 11;
    private int averageBucketSize = 55;

    // RecSplit with FastLongHash:
    // 2.32 bits/key, 138 nanoseconds/key evaluation time
    // private int leafSize = 12;
    // private int averageBucketSize = 26;

    private int repeat = 100;

    public static void main(String... args) {
        new PerformanceTest().execute(args);
    }

    private void execute(String... args) {
        for (int i = 0; i < args.length; i++) {
            if ("-size".equals(args[i])) {
                size = Integer.parseInt(args[++i]);
            } else if ("-leafSize".equals(args[i])) {
                leafSize = Integer.parseInt(args[++i]);
            } else if ("-averageBucketSize".equals(args[i])) {
                averageBucketSize = Integer.parseInt(args[++i]);
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[++i]);
            } else {
                printUsage();
            }
        }
        System.out.println("Settings: leafSize=" + leafSize +
                ", averageBucketSize=" + averageBucketSize +
                ", size=" + size);
        for (int i = 0; i < 5; i++) {
            runMicroBenchmark(false);
        }
        for (int i = 0; i < 5; i++) {
            runMicroBenchmark(true);
        }
    }

    void printUsage() {
        System.out.println("Usage: java " + getClass().getName() + " [options]");
        System.out.println("Options:");
        System.out.println("-size <integer>  the number of randomly generated numbers, default " + size);
        System.out.println("-leafSize <integer>  leafSize parameter, default " + leafSize);
        System.out.println("-averageBucketSize <integer>  averageBucketSize parameter, default " + averageBucketSize);
        System.out.println("-repeat <integer>  repeat count for the evalution benchmark loop, 0 to just verify; default " + repeat);
    }

    void runMicroBenchmark(boolean fastHash) {
        System.out.println();
        HashSet<Long> set = createSet(size, 1);
        ArrayList<Long> list = new ArrayList<Long>(set);
        UniversalHash<Long> hash;
        if (fastHash) {
            hash = new FastLongHash();
        } else {
            hash = new LongHash();
        }

        long start = System.nanoTime();
        byte[] data = RecSplitBuilder.newInstance(hash).
                leafSize(leafSize).averageBucketSize(averageBucketSize).
                generate(set).toByteArray();
        long time = System.nanoTime() - start;

        int bits = data.length * 8;
        System.out.printf("Generated in %.2f seconds at %.2f bits/key, using %s\n",
                time / 1_000_000_000., (double) bits / size,
                hash);

        CountingHash<Long> count = new CountingHash<Long>(hash);
        RecSplitEvaluator<Long> eval = RecSplitBuilder.
                newInstance(count).
                leafSize(leafSize).averageBucketSize(averageBucketSize).
                buildEvaluator(new BitBuffer(data));
        BitSet bitSet = new BitSet();
        for (Long x : set) {
            int y = eval.evaluate(x);
            if (y < 0 || y >= size) {
                throw new AssertionError("y=" + y + " of " + size);
            }
            if (bitSet.get(y)) {
                throw new AssertionError();
            }
            bitSet.set(y);
        }

        for (int i = 0; i < 10; i++) {
            System.gc();
        }

        if (repeat > 0) {
            eval = RecSplitBuilder.
                    newInstance(hash).
                    leafSize(leafSize).averageBucketSize(averageBucketSize).
                    buildEvaluator(new BitBuffer(data));
            start = System.nanoTime();
            for (int i = 0; i < repeat; i++) {
                for (int j = 0; j < size; j++) {
                    Long x = list.get(j);
                    int y = eval.evaluate(x);
                    if (y < 0 || y >= size) {
                        throw new AssertionError("y=" + y + " of " + size);
                    }
                }
            }
            time = System.nanoTime() - start;
            System.out.printf("Evaluation time: %d nanoseconds/key, " +
                    "universalHash: %f calls/key \n",
                    (int) (((double) time / repeat / size)),
                    (double) count.getCount() / size);
        }
    }

    public static HashSet<Long> createSet(int size, int seed) {
        Random r = new Random(seed);
        HashSet<Long> set = new HashSet<Long>(size);
        while (set.size() < size) {
            set.add(r.nextLong());
        }
        return set;
    }

    /**
     * A fast long hash implementation. It is not recommended to use this in the
     * real world, it is just used to test the effect of using a faster hash
     * function.
     */
    static class FastLongHash implements UniversalHash<Long> {

        @Override
        public long universalHash(Long key, long index) {
            return Long.rotateLeft(key, (int) index) ^ (key >>> 16);
        }

        @Override
        public String toString() {
            return "FastLongHash (rotate&xor)";
        }

    }

    /**
     * A fast long hash implementation. It is not recommended to use this in the
     * real world, it is just used to test the effect of using a faster hash
     * function.
     */
    static class CountingHash<K> implements UniversalHash<K> {

        private final UniversalHash<K> base;
        private long count;

        CountingHash(UniversalHash<K> base) {
            this.base = base;
        }

        long getCount() {
            return count;
        }

        @Override
        public long universalHash(K key, long index) {
            count++;
            return base.universalHash(key, index);
        }

        @Override
        public String toString() {
            return "MeasuredHash " + base;
        }

    }

}
