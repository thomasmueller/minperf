package org.minperf.bdz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.HashSet;

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
        new BDZTest().test();
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
        BitBuffer data = BDZ.generate(hash, set);
        int bitCount = data.position();
        System.out.println("size " + size + " bits/key: " +
                (double) bitCount / size);
        data.seek(0);
        BDZ<Long> bdz = BDZ.load(hash, data);
        assertEquals(bitCount, data.position());
        BitSet test = new BitSet();
        for (long x : set) {
            int i = bdz.get(x);
            assertTrue(i >= 0 && i < size);
            assertFalse(test.get(i));
            test.set(i);
        }
    }

}
