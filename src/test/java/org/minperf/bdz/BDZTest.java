package org.minperf.bdz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Test;
import org.minperf.BitBuffer;
import org.minperf.RandomizedTest;
import org.minperf.universal.LongHash;
import org.minperf.universal.UniversalHash;

/**
 * Tests the BDZ implementation.
 */
public class BDZTest {

    public static void main(String... args) {
        for (int size = 10; size < 10000000; size *= 10) {
            test(size);
        }
        testPerformance(1000000);
    }

    @Test
    public void test() {
        test(100000);
    }

    private static void testPerformance(int size) {
        HashSet<Long> set = RandomizedTest.createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        BitBuffer data = BDZ.generate(hash, set);
        int bitCount = data.position();
        data.seek(0);
        BDZ<Long> bdz = BDZ.load(hash, data);
        assertEquals(bitCount, data.position());
        BitSet test = new BitSet();
        for (long x : set) {
            int i = bdz.evaluate(x);
            assertTrue(i >= 0 && i < size);
            assertFalse(test.get(i));
            test.set(i);
        }
        int measureCount = 10;
        long evaluateNanos = System.nanoTime();
        for (int i = 0; i < measureCount; i++) {
            for (Long x : set) {
                int index = bdz.evaluate(x);
                if (index > set.size() || index < 0) {
                    Assert.fail("wrong entry: " + x + " " + index);
                }
            }
        }
        // System.out.println(prof.getTop(5));
        evaluateNanos = (System.nanoTime() - evaluateNanos) / measureCount;
        System.out.println("size " + size + " bits/key: " +
                (double) bitCount / size + " evaluate " + (evaluateNanos / size) + " ns/key");
    }

    private static void test(int size) {
        HashSet<Long> set = RandomizedTest.createSet(size, 1);
        UniversalHash<Long> hash = new LongHash();
        BitBuffer data = BDZ.generate(hash, set);
        int bitCount = data.position();
        System.out.println("size " + size + " bits/key: " +
                (double) bitCount / size);
        data.seek(0);
        BDZ<Long> bdz = BDZ.load(hash, data);
        assertEquals(bitCount, data.position());
        BitSet test = new BitSet();
        for (long x : set) {
            int i = bdz.evaluate(x);
            assertTrue(i >= 0 && i < size);
            assertFalse(test.get(i));
            test.set(i);
        }

    }

}
