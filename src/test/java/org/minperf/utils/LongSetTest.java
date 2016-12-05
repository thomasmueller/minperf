package org.minperf.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.TreeSet;

import org.junit.Test;

/**
 * Tests for the long set.
 */
public class LongSetTest {

    public static void main(String... args) {
        for (int len = 1000000; len <= 1000000000; len *= 10) {
            System.out.println("size1 " + len);
            LongSet set = createSet(len, 1);
            int size = 0;
            long sum = 0;
            for (long x : set) {
                sum += x;
                size++;
            }
            System.out.println("size " + size + " sum " + sum);
        }
    }

    @Test
    public void randomSet() {
        for (int i = 0; i < 100; i++) {
            Random r = new Random(i);
            LongSet set = new LongSet(10);
            TreeSet<Long> s2 = new TreeSet<Long>();
            while (set.size() < 10) {
                long x = r.nextInt(100);
                set.add(x);
                s2.add(x);
                assertEquals(s2.size(), set.size());
                assertEquals(s2.toString(), toString(set));
            }
        }
    }

    @Test
    public void smallSet() {
        LongSet set = new LongSet(10);
        assertEquals("[]", toString(set));
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertTrue(set.add(0L));
        assertFalse(set.isEmpty());
        assertEquals("[0]", toString(set));
        assertEquals(1, set.size());
        assertFalse(set.add(0L));
        assertEquals("[0]", toString(set));
        assertEquals(1, set.size());
        assertTrue(set.add(1L));
        assertEquals("[0, 1]", toString(set));
        assertEquals(2, set.size());
        assertFalse(set.add(1L));
        assertEquals(2, set.size());
        for (int i = 2; i < 10; i++) {
            assertTrue(set.add(i * 10L));
            assertEquals(i + 1, set.size());
        }
        assertEquals("[0, 1, 20, 30, 40, 50, 60, 70, 80, 90]", toString(set));
    }

    private static String toString(LongSet set) {
        TreeSet<Long> s2 = new TreeSet<Long>();
        for (long x : set) {
            s2.add(x);
        }
        return s2.toString();
    }

    public static LongSet createSet(int size, int seed) {
        Random r = new Random(seed);
        LongSet set = new LongSet(size);
        long i = 0;
        while (set.size() < size) {
            set.add(r.nextLong());
            if ((i++ & 0xffffff) == 0) {
                System.out.println("size " + set.size());
            }
        }
        return set;
    }

}
