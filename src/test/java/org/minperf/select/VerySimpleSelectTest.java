package org.minperf.select;

import static org.junit.Assert.assertEquals;

import java.util.BitSet;
import java.util.Random;

import org.junit.Test;
import org.minperf.BitBuffer;
import org.minperf.rank.VerySimpleRank;

/**
 * Test the simple select implementation.
 */
public class VerySimpleSelectTest {

    public static void main(String... args) {
        System.out.println("FastSelect performance test");
        for (int i = 0; i < 4; i++) {
            testPerformance();
        }
        System.out.println("Select int/long performance test");
        for (int i = 0; i < 4; i++) {
            testLongSelectPerformance();
        }
    }

    @Test
    public void testBitInInt() {
        Random r = new Random(1);
        for (int n = 0; n < 64; n++) {
            for (int i = 0; i < 100; i++) {
                if (Long.bitCount(i) < n + 1) {
                    continue;
                }
                int a = VerySimpleSelect.selectBitSlow(i, n);
                int b = VerySimpleSelect.selectBitLong(i, n);
                int c = VerySimpleSelect.selectBit(i, n);
                int d = VerySimpleSelect.selectBitReverse(Integer.reverse(i), n);
                assertEquals(a, b);
                assertEquals(a, c);
                assertEquals(a, d);
            }
            for (int i = 0; i < 100; i++) {
                int x = r.nextInt();
                if (Integer.bitCount(i) < n + 1) {
                    continue;
                }
                int a = VerySimpleSelect.selectBitSlow(x, n);
                int b = VerySimpleSelect.selectBitLong(x & 0xffffffffL, n);
                int c = VerySimpleSelect.selectBitLongReverse(
                        Long.reverse(x & 0xffffffffL), n);
                int d = VerySimpleSelect.selectBit(x, n);
                int e = VerySimpleSelect.selectBitReverse(Integer.reverse(x), n);
                assertEquals(a, b);
                assertEquals(a, c);
                assertEquals(a, d);
                assertEquals(a, e);
            }
            for (int i = 0; i < 100; i++) {
                long x = r.nextLong();
                if (Long.bitCount(x) < n + 1) {
                    continue;
                }
                int a = VerySimpleSelect.selectBitSlow(x, n);
                int b = VerySimpleSelect.selectBitLong(x, n);
                assertEquals(a, b);
            }
        }
    }

    private static void testLongSelectPerformance() {
        Random r = new Random(1);
        int[] values = new int[1024 * 1024];
        int[] list = new int[1024 * 1024];
        int tests = 100;
        for (int i = 0; i < 1024 * 1024; i++) {
            values[i] = r.nextInt();
            list[i] = r.nextInt(Integer.bitCount(values[i]));
        }
        int sum = 0;
        long time = System.currentTimeMillis();
        for (int test = 0; test < tests; test++) {
            for (int i = 0; i < 1024 * 1024; i++) {
                sum += VerySimpleSelect.selectBit(values[i], list[i]);
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("new sum: " + sum + " " + time);
    }

    @Test
    public void testSelect() {
        for (int fill = 0; fill <= 100; fill++) {
            Random r = new Random(fill);
            BitSet set = new BitSet();
            int size = 10000;
            int count = fill * size / 100;
            for (int i = 0; i < count; i++) {
                int x = r.nextInt(size);
                set.set(x);
            }
            BitBuffer buffer = new BitBuffer(10 * set.size());
            VerySimpleSelect select = VerySimpleSelect.generate(set, buffer);
            int p1 = buffer.position();
            buffer.seek(0);
            select = VerySimpleSelect.load(buffer);
            int p2 = buffer.position();
            assertEquals(p1, p2);

            BitBuffer buffer2 = new BitBuffer(10 * set.size());
            VerySimpleRank rank = VerySimpleRank.generate(set, buffer2);
            p1 = buffer.position();
            buffer2.seek(0);
            rank = VerySimpleRank.load(buffer2);
            p2 = buffer.position();
            assertEquals(p1, p2);

            for (int i = 0, j = 0; i < set.length(); i++) {
                if (set.get(i)) {
                    int x = (int) select.select(j);
                    assertEquals(i, x);
                    x = (int) rank.select(j);
                    assertEquals(i, x);
                    j++;
                }
            }
            int cardinality = set.cardinality();
            for (int j = 0; j < cardinality - 1; j++) {
                int x1 = (int) select.select(j);
                int x2 = (int) select.select(j + 1);
                long x12 = select.selectPair(j);
                assertEquals(x1, x12 >>> 32);
                assertEquals(x2, (int) x12);
            }

        }

    }

    private static void testPerformance() {
        int size = 1000000;
        int blockSize = 16;
        int[] count = new int[size];
        Random r = new Random(1);
        for (int i = 0; i < size * blockSize; i++) {
            count[r.nextInt(size)]++;
        }
        int[] sumCount = new int[size];
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sumCount[i] = sum;
            sum += count[i];
        }
        int max = sum;
        int len = size;
        int lowBitCount = 32 - Integer.numberOfLeadingZeros(Integer
                .highestOneBit(max / len));
        BitSet set = new BitSet();
        for (int i = 0; i < len; i++) {
            int x = i + (sumCount[i] >>> lowBitCount);
            set.set(x);
        }
        // System.out.println("set: " + set);
        BitBuffer buffer = new BitBuffer(10 * set.size());
        VerySimpleSelect select = VerySimpleSelect.generate(set, buffer);
        int bitCount = buffer.position();
        System.out.println("bits/key fast: " + ((double) bitCount / size));
        long time;
        time = System.nanoTime();
        // Profiler prof = new Profiler().startCollecting();
        for (int k = 0; k < 10; k++) {
            for (int i = 0, j = 0; i < set.length(); i++) {
                if (set.get(i)) {
                    int x = (int) select.select(j);
                    // System.out.println("select(" + j + ")=" + x);
                    if (x != i) {
                        System.out.println("WRONG: select(" + j + ") = " + x +
                                " ; expected i = " + i + " j=" + j);
                        // x = (int) select.select(j);
                        // throw new AssertionError("exp " + i + " got " + x +
                        // " at select(" + j + ")");
                    }
                    j++;
                }
            }
        }
        // System.out.println(prof.getTop(5));
        time = System.nanoTime() - time;
        System.out.println("time: " + (time / 10 / set.length()));

        buffer = new BitBuffer(10 * set.size());
        VerySimpleRank rank = VerySimpleRank.generate(set, buffer);
        bitCount = buffer.position();
        System.out.println("bits/key rank: " + ((double) bitCount / size));
        time = System.nanoTime();
        for (int k = 0; k < 10; k++) {
            for (int i = 0, j = 0; i < set.length(); i++) {
                if (set.get(i)) {
                    int x = (int) rank.select(j);
                    if (x != i) {
                        throw new AssertionError();
                        // System.out.println("wrong: select(" + j + ") = " + x
                        // + " ; expected i=" + i + " j=" + j);
                    }
                    j++;
                }
            }
        }
        time = System.nanoTime() - time;
        System.out.println("time: " + (time / 10 / set.length()));

    }

}
