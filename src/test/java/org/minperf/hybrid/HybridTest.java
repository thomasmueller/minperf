package org.minperf.hybrid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.HashSet;

import org.junit.Test;
import org.minperf.BitBuffer;
import org.minperf.RandomizedTest;
import org.minperf.Settings;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Tests the hybrid algorithm.
 */
public class HybridTest {

    public static void main(String... args) {
        new HybridTest().test();
    }

    @Test
    public void test() {
        for (int size = 10; size < 10000000; size *= 10) {
            test(size);
        }
    }

    private static void test(int size) {
        HashSet<Long> set = RandomizedTest.createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        Settings settings = new Settings(11, 1024);
        HybridGenerator<Long> generator = new HybridGenerator<Long>(hash, settings, true);
        BitBuffer buffer = generator.generate(set);
        int bitCount = buffer.position();
        System.out.println("size " + size + " bits/key: " +
                (double) bitCount / size);
        buffer.seek(0);
        HybridEvaluator<Long> evaluator = new HybridEvaluator<Long>(hash, settings, buffer);
        System.out.println(evaluator);
        BitSet test = new BitSet();
        for (long x : set) {
            int i = evaluator.get(x);
            assertTrue(i >= 0 && i < size);
            assertFalse(test.get(i));
            test.set(i);
        }
    }

}
