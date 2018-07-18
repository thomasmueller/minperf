package org.minperf.rank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.BitSet;
import java.util.Random;

import org.junit.Test;
import org.minperf.hash.Mix;

/**
 * Test the simple rank/select implementation.
 */
public class Rank9Test {

    public static void main(String... args) {
        new Rank9Test().test();
        new Rank9Test().testPerformance();
    }

    private void testPerformance() {
        for (int size = 1 << 20; size > 0; size *= 2) {
            System.out.println();
            System.out.println("Size: " + size);

            long time = System.nanoTime();
            BitSet set = new BitSet(size);
            for (int i = 0; i < size;) {
                long x = Mix.hash64(i);
                for (int j = 0; j < 64; j++, i++) {
                    set.set(i, (x & 1) == 1);
                    x >>>= 1;
                }
            }
            time = (System.nanoTime() - time) / size;
            System.out.println("Construct bitset: " + time + " ns/bit cardinality: " + set.cardinality());

            time = System.nanoTime();
            Rank9 rank = new Rank9(set, size);
            time = (System.nanoTime() - time) / size;
            System.out.println("Construct rank: " + time + " ns/bit");

            int[] list = new int[size];
            for (int i = 0; i < size; i++) {
                list[i] = (int) Mix.hash64(i * 0xc4ceb9fe1a85ec53L) & (size - 1);
            }

            time = System.nanoTime();
            int count = 0;
            for (int x : list) {
                count += rank.get(x) != 0 ? 1 : 0;
            }
            time = (System.nanoTime() - time) / size;
            System.out.println("Randomized get: " + time + " ns/bit; results: " +
                    count);

            time = System.nanoTime();
            long sum = 0;
            for (int x : list) {
                sum += rank.rank(x);
            }
            time = (System.nanoTime() - time) / size;
            System.out.println("Randomized rank: " + time + " ns/bit; result: " + sum);

            time = System.nanoTime();
            count = 0;
            sum = 0;
            for (int x : list) {
                count += rank.get(x) != 0 ? 1 : 0;
                sum += rank.rank(x);
            }
            time = (System.nanoTime() - time) / size;
            System.out.println("Randomized rank, then get: " + time + " ns/bit; results: " +
                    count + " " + sum);

            time = System.nanoTime();
            count = 0;
            sum = 0;
            for (int x : list) {
                long rankAndGet = rank.rankAndGet(x);
                count += rankAndGet & 1;
                sum += rankAndGet >>> 1;
            }
            time = (System.nanoTime() - time) / size;
            System.out.println("Randomized rankAndGet: " + time + " ns/bit; results: " +
                    count + " " + sum);

        }
    }

    @Test
    public void test() {
        for (int size = 0; size < 2000; size++) {
            test(size);
        }
        for (int size = 64; size < 1024 * 1024; size *= 2) {
            test(size);
        }
    }

    private static void test(int size) {
        testFull(size);
        testRandom(size);
        testEmpty(size);
    }

    private static void testEmpty(int size) {
        BitSet set = new BitSet();
        set.set(0, size, false);
        Rank9 rank = new Rank9(set, size);
        assertEquals(0, rank.rank(0));
        for (int j = 0; j < size; j++) {
            assertEquals(0L, rank.rank(j));
        }
    }

    private static void testRandom(int size) {
        BitSet set = new BitSet();
        Random r = new Random(size);
        for (int i = 0; i < size / 10; i++) {
            while (true) {
                int x = r.nextInt(size);
                if (!set.get(x)) {
                    set.set(x);
                    break;
                }
            }
        }
        Rank9 rank = new Rank9(set, size);
        assertEquals(0, rank.rank(0));
        int x = 0;
        for (int j = 0; j < size; j++) {
            assertEquals(x, rank.rank(j));
            assertEquals(x, rank.rankAndGet(j) >>> 1);
            if (set.get(j)) {
                assertTrue(rank.get(j) != 0);
                assertTrue((rank.rankAndGet(j) & 1) != 0);
                x++;
            } else {
                assertFalse(rank.get(j) != 0);
                assertFalse((rank.rankAndGet(j) & 1) != 0);
            }
        }
    }

    private static void testFull(int size) {
        BitSet set = new BitSet();
        set.set(0, size, true);
        Rank9 rank = new Rank9(set, size);
        assertEquals(0, rank.rank(0));
        for (int j = 1; j < size; j++) {
            assertEquals(j, rank.rank(j));
        }
    }

}
