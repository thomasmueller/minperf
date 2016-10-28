package org.minperf.eliasFano;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;
import org.minperf.BitBuffer;

/**
 * Test the EliasFanoMonotoneList.
 */
public class EliasFanoMonotoneListTest {

    public static void main(String... args) {
        new EliasFanoMonotoneListTest().testSaving();
    }

    @Test
    public void test() {
        for (int bucketSize = 8; bucketSize < 256; bucketSize *= 2) {
            for (int size = 100; size <= 1000000; size *= 10) {
                if (size >= bucketSize) {
                    test(bucketSize, size);
                }
            }
        }
    }

    public void testSaving() {
        for (int bucketSize = 8; bucketSize < 256; bucketSize *= 2) {
            for (int size = 100; size <= 100000000; size *= 10) {
                if (size >= bucketSize) {
                    test(bucketSize, size);
                }
            }
        }
    }

    private static void test(int bucketSize, int size) {
        int bucketCount = size / bucketSize;
        int[] sizes = randomSizes(size, bucketCount);
        int[] posList = posList(sizes);
        int[] expected = expectedPosList(size, bucketCount);
        int[] offsets = offsets(posList, expected);
        int min = min(offsets), max = max(offsets);
        int entryBits = 32 - Integer.numberOfLeadingZeros(-min + max);
        int diff = min(sizes);
        int[] sizeOffsets = plus(sizes, -diff);
        int[] posList2 = posList(sizeOffsets);
        // System.out.println(size + " avg gap: " + (double) posList2[posList2.length - 1] / posList2.length);
        for (int i = 0; i < bucketCount; i++) {
            assertEquals(posList[i], posList2[i] + i * diff);
        }
        BitBuffer buffer = new BitBuffer(10 * size);
        MonotoneList list = MonotoneList.generate(posList2, buffer);
        int bitCount = buffer.position();
        double oldBits = (double) (entryBits * bucketCount) / size;
        double newBits =  (double) bitCount / size;
        System.out.println("bucketSize " + bucketSize + " bucketCount " + bucketCount
                + " old " + oldBits + " (" + entryBits + "*" + bucketCount + ") new " + newBits
                + " saving " + (oldBits - newBits));
        for (int i = 0; i < bucketCount; i++) {
            assertEquals("i: " + i, posList2[i], list.get(i));
        }
        buffer.seek(0);
        list = MonotoneList.load(buffer);
        assertEquals(bitCount, buffer.position());
        for (int i = 0; i < bucketCount; i++) {
            assertEquals(posList2[i], list.get(i));
        }
        for (int i = 0; i < bucketCount - 1; i++) {
            int a = list.get(i);
            int b = list.get(i + 1);
            long ab = list.getPair(i);
            assertEquals(((long) a << 32) + b, ab);
        }
        int dummy = 0;
        long time = System.nanoTime();
        for (int test = 0; test < 100; test++) {
            for (int i = 0; i < bucketCount - 1; i++) {
                int a = list.get(i);
                dummy += a;
            }
        }
        time = System.nanoTime() - time;
        if (bucketCount > 1) {
            System.out.println("Time: " + time / 1000000 + " ms; " + time / 100 / (bucketCount - 1) + " ns/key dummy " + dummy);
        }
    }

    private static int[] randomSizes(int size, int bucketCount) {
        Random r = new Random(size + bucketCount);
        int[] sizes = new int[bucketCount];
        for (int i = 0; i < size; i++) {
            sizes[r.nextInt(bucketCount)]++;
        }
        return sizes;
    }

    private static int[] expectedPosList(int size, int bucketCount) {
        int[] sizes = new int[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            sizes[i] = (int) ((long) size * i / bucketCount);
        }
        return sizes;
    }

    private static int[] offsets(int[] got, int[] expected) {
        int[] offsets = new int[got.length];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = got[i] - expected[i];
        }
        return offsets;
    }

    private static int min(int[] sizes) {
        int min = Integer.MAX_VALUE;
        for (int x : sizes) {
            min = Math.min(min, x);
        }
        return min;
    }

    private static int max(int[] sizes) {
        int max = Integer.MIN_VALUE;
        for (int x : sizes) {
            max = Math.max(max, x);
        }
        return max;
    }

    private static int[] plus(int[] list, int plus) {
        int[] list2 = new int[list.length];
        for (int i = 0; i < list.length; i++) {
            list2[i] = list[i] + plus;
        }
        return list2;
    }

    private static int[] posList(int[] sizes) {
        int[] posList = new int[sizes.length];
        int p = 0;
        for (int i = 0; i < sizes.length; i++) {
            posList[i] = p;
            p += sizes[i];
        }
        return posList;
    }

}
