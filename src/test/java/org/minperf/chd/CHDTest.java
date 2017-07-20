package org.minperf.chd;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Set;

import org.junit.Assert;
import org.minperf.BitBuffer;
import org.minperf.RandomizedTest;
import org.minperf.Settings;
import org.minperf.SpaceEstimator;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

public class CHDTest<T> {

    public static void main(String... args) {
        for(int lambda = 3; lambda <= 6; lambda++) {
            for (int size = 1000; size <= 100000; size *= 10) {
                for(double factor = 1.0; factor < 1.11; factor += 0.5) {
                    testSize(size, lambda, factor);
                }
            }
        }
        // space for CHD-k + RecSplit
        int leafSize = 18;
        int bucketSize = 1024;
        Settings s = new Settings(leafSize, bucketSize);
        HashMap<Integer, Double> cache = new HashMap<>();
        double space = SpaceEstimator.getExpectedBucketSpace(s, bucketSize, 0, cache);
        double bitsPerKey = space / bucketSize;
        System.out.println("space: " + bitsPerKey + " (just one bucket)");
        for (int size = 1024; size <= 100000; size *= 2) {
            double p = testSizeK(size, 6, bucketSize);
            System.out.println("space for CHD-k + RecSplit: " + (bitsPerKey + p) + " bits/key");
        }
    }

    private static double testSizeK(int size, int lambda, int k) {
        Set<Long> set = RandomizedTest.createSet(size, 1);
        return testK(set, k, lambda, Long.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> double testK(Set<T> set, int k, int lambda, Class<T> clazz) {
        int size = set.size();
        BitBuffer buff = new BitBuffer(1000 + size * 1000);
        UniversalHash<T> hash = null;
        if (clazz == Long.class) {
            hash = (UniversalHash<T>) new LongHash();
        }
        CHD2<T> chd = new CHD2<T>(hash, buff, lambda, k);
        chd.generate(set);
        long totalBits = buff.position();
        double bitsPerKey = (double) totalBits / size;
        // System.out.println("size " + size + " bits/key " + bitsPerKey);
        buff.seek(0);
        chd = new CHD2<T>(hash, buff, lambda, k);
        chd.load();
        verifyK(chd, set, k);
        return bitsPerKey;
    }

    private static void testSize(int size, int lambda, double factor) {
        Set<Long> set = RandomizedTest.createSet(size, 1);
        test(set, lambda, factor, Long.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> void test(Set<T> set, int lambda, double factor, Class<T> clazz) {
        int size = set.size();
        BitBuffer buff = new BitBuffer(1000 + size * 1000);
        UniversalHash<T> hash = null;
        if (clazz == Long.class) {
            hash = (UniversalHash<T>) new LongHash();
        }
        CHD<T> chd = new CHD<T>(hash, buff, lambda, factor);
        long time = System.nanoTime();
        chd.generate(set);
        time = System.nanoTime() - time;
        long totalBits = buff.position();
        System.out.println("size: " + size + " lambda: " + lambda +
                " factor: " + factor +
                " bits/key: " + (double) totalBits / size + // " hashCalls: " + chd.hashCalls +
                " ns/key: " + time / size);
        buff.seek(0);
        chd = new CHD<T>(hash, buff, lambda, factor);
        chd.load();
        verify(chd, set);
    }

    private static <T> void verify(CHD<T> eval, Set<T> set) {
        BitSet known = new BitSet();
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > set.size() || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index);
            }
            if (known.get(index)) {
                eval.evaluate(x);
                Assert.fail("duplicate entry: " + x + " " + index);
            }
            known.set(index);
        }
    }

    private static <T> void verifyK(CHD2<T> eval, Set<T> set, int k) {
        int[] count = new int[set.size() / k];
        for (T x : set) {
            int index = eval.evaluate(x);
            if (index > count.length || index < 0) {
                Assert.fail("wrong entry: " + x + " " + index);
            }
            if (count[index] > k) {
                eval.evaluate(x);
                Assert.fail("too often: " + x + " " + index + " count=" + count[index]);
            }
            count[index]++;
        }
    }

}
