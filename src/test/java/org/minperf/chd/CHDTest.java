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
        // 8/8: 2.21
        // 16/16: 2.04
        // 16/64: 1.786
        int leafSize = 18;
        int bucketSize = 1024;
        Settings s = new Settings(leafSize, bucketSize);
        HashMap<Integer, Double> cache = new HashMap<>();
        double space = SpaceEstimator.getExpectedBucketSpace(s, bucketSize, 0, cache);
        double bitsPerKey = space / bucketSize;
        System.out.println("space: " + bitsPerKey + " (just one bucket)");
        for (int size = 1024; size <= 100000; size *= 2) {
            double p = testSizeK(size, bucketSize);
            System.out.println("space: " + (bitsPerKey + p) + " bits/key");
        }
        for (int size = 10; size <= 100000; size *= 10) {
            testSize(size);
        }
    }

    private static double testSizeK(int size, int k) {
        Set<Long> set = RandomizedTest.createSet(size, 1);
        return testK(set, k, Long.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> double testK(Set<T> set, int k, Class<T> clazz) {
        int lambda = 10;
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

    private static void testSize(int size) {
        Set<Long> set = RandomizedTest.createSet(size, 1);
        test(set, Long.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> void test(Set<T> set, Class<T> clazz) {
        int size = set.size();
        BitBuffer buff = new BitBuffer(1000 + size * 1000);
        UniversalHash<T> hash = null;
        if (clazz == Long.class) {
            hash = (UniversalHash<T>) new LongHash();
        }
        CHD<T> chd = new CHD<T>(hash, buff);
        chd.generate(set);
        long totalBits = buff.position();
        System.out.println("size " + size + " bits/key " + (double) totalBits / size);
        buff.seek(0);
        chd = new CHD<T>(hash, buff);
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
                Assert.fail("duplicate entry: " + x + " " + index);
            }
            count[index]++;
        }
    }

}
