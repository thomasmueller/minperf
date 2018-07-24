package org.minperf.utils;

import java.util.Random;

import org.junit.Test;
import org.minperf.BitBuffer;
import org.minperf.bloom.utils.BitArray;
import org.minperf.hem.RandomGenerator;

public class BitArrayTest {

    public static void main(String... args) {
        performanceTest();
    }

    private static void performanceTest() {
        for (int bitSize = 4; bitSize < 16; bitSize++) {
            for (int size = 1_000_000; size <= 1_000_000_000; size *= 10) {
                testBitArray(bitSize, size);
                testBitBuffer(bitSize, size);
            }
        }
    }

    private static void testBitBuffer(int bitCount, int size) {
        long bits = (long) size * bitCount;
        if (bits / 64 > Integer.MAX_VALUE / 2) {
            return;
        }
        BitBuffer buff = new BitBuffer(bits);
        long[] list = buff.data;
        RandomGenerator.createRandomUniqueListFast(list, list.length);
        long time = System.nanoTime();
        long sum = 0;
        int count = 1_000_000;
        for (int i = 0, j = 0; i < count; i++) {
            long pos = reduce((int) list[j++], size);
            if (j >= list.length) {
                j = 0;
            }
            sum += buff.readNumber(pos * bitCount, bitCount);
        }
        time = (System.nanoTime() - time) / count;
        System.out.println("BitBuffer size: " + size + " bitCount: " + bitCount + " time: " + time + " ns/op dummy: " + sum);
    }

    private static void testBitArray(int bitCount, int size) {
        long bits = (long) size * bitCount;
        if (bits / 8 > Integer.MAX_VALUE / 2) {
            return;
        }
        BitBuffer buff1 = new BitBuffer(bits);
        long[] list = buff1.data;
        RandomGenerator.createRandomUniqueListFast(list, list.length);
        BitArray buff = new BitArray(size, bitCount);
        for (long i = 0; i < size; i++) {
            buff.put3(i, (int) buff1.readNumber(i * bitCount, bitCount));
        }
        long time = System.nanoTime();
        long sum = 0;
        int count = 1_000_000;
        for (long i = 0, j = 0; i < count; i++) {
            long pos = reduce((int) list[(int) j++], size);
            if (j >= list.length) {
                j = 0;
            }
            sum += buff.get2(pos);
        }
        time = (System.nanoTime() - time) / count;
        System.out.println("BitArray size: " + size + " bitCount: " + bitCount + " time: " + time + " ns/op dummy: " + sum);
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    // 2 bytes are needed for arrays with bit size: 2-10, 12, 16
    // 3 bytes are needed for arrays with bit size: 11, 13, 14, 15

    @Test
    public void test2() {
        int size = 100;
        int loop = 10000;
        for (int bits = 1; bits <= 10; bits++) {
            BitArray array = new BitArray(size, bits);
            int[] data = new int[size];
            Random r = new Random(1);
            for (int i = 0; i < loop; i++) {
                int x = r.nextInt(1 << bits);
                int p = r.nextInt(size);
                array.put2(p, x);
                data[p] = x;
                if (array.get2(p) != data[p]) {
                    throw new AssertionError();
                }
                p = r.nextInt(size);
                if (array.get2(p) != data[p]) {
                    throw new AssertionError();
                }
            }
        }
    }

    @Test
    public void test3() {
        int size = 100;
        int loop = 10000;
        for (int bits = 11; bits <= 15; bits++) {
            BitArray array = new BitArray(size, bits);
            int[] data = new int[size];
            Random r = new Random(1);
            for (int i = 0; i < loop; i++) {
                int x = r.nextInt(1 << bits);
                int p = r.nextInt(size);
                array.put3(p, x);
                data[p] = x;
                if (array.get3(p) != data[p]) {
                    throw new AssertionError();
                }
                p = r.nextInt(size);
                if (array.get3(p) != data[p]) {
                    throw new AssertionError();
                }
            }
        }
    }

}
