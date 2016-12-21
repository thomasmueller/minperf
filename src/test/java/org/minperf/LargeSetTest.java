package org.minperf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.Random;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;
import org.minperf.universal.LongHash;
import org.minperf.utils.LargeLongList;
import org.minperf.utils.LongSet;

/**
 * Tests with large sets.
 */
public class LargeSetTest {

    public static void main(String... args) {
        System.out.println("Calculated " + SpaceEstimator.getExpectedSpace(8, 128));
        test(true);
        test(false);
    }

    private static void test(boolean eliasFano) {
        int leafSize = 8, loadFactor = 128;
        System.out.println("leafSize " + leafSize + ", loadFactor " + loadFactor +
                ", calcualted " +
                SpaceEstimator.getExpectedSpace(8, 128) + " bits/key");
        for (int len = 10; len <= 1_000_000_000; len *= 10) {
            LongSet set = createSet(len, 1);
            System.out.println("...set created, size " + set.size());
            LargeLongList list = LargeLongList.create(set);
            set = null;
            System.out.println("...list created");
            LongHash hash = new LongHash();
            long time = System.nanoTime();
            BitBuffer buff = RecSplitBuilder.
                    newInstance(hash).
                    leafSize(leafSize).loadFactor(loadFactor).
                    eliasFanoMonotoneLists(eliasFano).
                    generate(list);
            time = System.nanoTime() - time;
            int bitCount = buff.position();
            buff.seek(0);
            System.out.println("Size " + len +
                    " " + (double) bitCount / len + " bits/key, " +
                    " " + (double) time / len + " ns/key, " +
                    " " + (eliasFano ? "EliasFano" : "Fast"));
            RecSplitEvaluator<Long> eval = RecSplitBuilder.
                    newInstance(hash).
                    leafSize(leafSize).loadFactor(loadFactor).
                    eliasFanoMonotoneLists(eliasFano).
                    buildEvaluator(buff);
            BitSet known = new BitSet();
            int i = 0;
            for (long x : list) {
                int index = eval.evaluate(x);
                if (index > len || index < 0) {
                    Assert.fail("wrong entry: " + x + " " + index);
                }
                if (known.get(index)) {
                    eval.evaluate(x);
                    Assert.fail("duplicate entry: " + x + " " + index);
                }
                known.set(index);
                if ((i++ & 0xffffff) == 0) {
                    System.out.println("...evaluated " + i);
                }
            }
            System.out.println("...done");
            list.dispose();
            list = null;
        }
    }

    public static LongSet createSet(int size, int seed) {
        Random r = new Random(seed);
        LongSet set = new LongSet(size);
        long i = 0;
        while (set.size() < size) {
            set.add(r.nextLong());
            if ((i++ & 0xffffff) == 0) {
                System.out.println("...createSet size " + set.size() + " of " + size);
            }
        }
        return set;
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

}
