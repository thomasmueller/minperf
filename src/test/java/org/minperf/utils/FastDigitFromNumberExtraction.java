package org.minperf.utils;

import java.util.Random;

/**
 * Utility class to quickly extract a single base-3 digit from an integer. This
 * can be useful to build an array of numbers ranging 0..2. This could help
 * writing a fast and space-saving BDZ implementation (BDZ needs an array of
 * numbers 0..2). Could be changed to other bases.
 */
public class FastDigitFromNumberExtraction {

    public static void main(String... args) {
        test();
        test();
        test();
        test();
    }

    private static void test() {
        int base = 3;
        int max = getMaxArraySize(base);
        int[] data = new int[max];
        Random r = new Random(1);
        int size = 1000000;
        int[] testData = new int[size];
        int[] testIndexes = new int[size];
        
        for (int i = 0; i < max; i++) {
            data[i] = BASE - 1;
        }
        int maxValue = convertToBase3(data);
        for(int test = 0; test <= maxValue; test++) {
            for(int i=0; i<max; i++) {
                if (extractDivMod(test, i) != extractMulMul(test, i)) {
                    System.out.println(i + " x=" + test + " got " + extractMulMul(test, i) + 
                            " expected " + extractDivMod(test, i));
                }
            }
            if ((test & 0xffffffff) == 0) {
                System.out.println(test + " " + maxValue);
            }
        }
        
        for (int test = 0; test < size; test++) {
            for (int i = 0; i < max; i++) {
                data[i] = r.nextInt(base);
            }
            testIndexes[test] = (r.nextInt(size) << 8) + r.nextInt(max);
            int x = convertToBase3(data);
            testData[test] = x;
            for (int i = 0; i < max; i++) {
                if (extractDivLoop(x, i) != data[i]) {
                    System.out.println(i + " got " + extractDivLoop(x, i) + " expected " + data[i]);
                    throw new AssertionError();
                }
                if (extractDivMod(x, i) != data[i]) {
                    System.out.println(i + " got " + extractDivMod(x, i) + " expected " + data[i]);
                    throw new AssertionError();
                }
                if (extractMulMod(x, i) != data[i]) {
                    System.out.println(i + " x=" + x + " got " + extractMulMod(x, i) + " expected " + data[i]);
                    throw new AssertionError();
                }
                if (extractMulMul(x, i) != data[i]) {
                    System.out.println(i + " x=" + x + " got " + extractMulMul(x, i) + " expected " + data[i]);
                    throw new AssertionError();
                }
            }
        }
        long time;
        int dummy;

//        time = System.nanoTime();
//        dummy = 0;
//        for(int ix : testIndexes) {
//            int x = testData[ix >>> 8];
//            dummy += getLoop(x, ix & 0xff);
//        }
//        time = System.nanoTime() - time;
//        System.out.println("loop " + time / size + " ns/key dummy " + dummy);

        time = System.nanoTime();
        dummy = 0;
        for (int ix : testIndexes) {
            int x = testData[ix >>> 8];
            dummy += extractDivMod(x, ix & 0xff);
        }
        time = System.nanoTime() - time;
        System.out.println("divMod " + time / size + " ns/key dummy " + dummy);

        time = System.nanoTime();
        dummy = 0;
        for (int ix : testIndexes) {
            int x = testData[ix >>> 8];
            dummy += extractMulMod(x, ix & 0xff);
        }
        time = System.nanoTime() - time;
        System.out.println("multiplyMod " + time / size + " ns/key dummy " + dummy);

        time = System.nanoTime();
        dummy = 0;
        for (int ix : testIndexes) {
            int x = testData[ix >>> 8];
            dummy += extractMulMul(x, ix & 0xff);
        }
        time = System.nanoTime() - time;
        System.out.println("multiplyMultiply " + time / size + " ns/key dummy " + dummy);

        System.out.println();
    }

    final static int BASE = 3;
    final static int MAX = 20;
    final static long[] DIV;
    final static long[] MX;
    final static int[] S2S;

    static {
        int max = 19;
        DIV = new long[max + 1];
        MX = new long[max + 1];
        S2S = new int[max + 1];
        long x = 1;
        for (int i = 0; i <= max; i++) {
            DIV[i] = x;
            int l = 64 - Long.numberOfLeadingZeros(x);
            long mx = ((1L << 32) * ((1L << l) - (int) x) / (int) x) + 1;
            int s2 = Math.max(l - 1, 0);
            S2S[i] = s2;
            MX[i] = mx; // 0xffffffffL / x + 1;
            x *= BASE;
        }
    }

    static int getMaxArraySize(int base) {
        long x = base;
        for (int i = 1;; i++) {
            long x2 = x * base;
            if (x2 > (1L << 32)) {
                return i;
            }
            x = x2;
        }
    }

    static int convertToBase3(int[] data) {
        int x = 0;
        for (int i = 0; i < MAX; i++) {
            x *= BASE;
            x += data[i];
        }
        return x;
    }

    /**
     * Extract a base-3 number using a loop with division operations, and one modulo
     * operation.
     * 
     * @param x     he number
     * @param index the digit id
     * @return the number
     */
    static int extractDivLoop(int x, int index) {
        long y = x & ((1L << 32) - 1);
        for (int i = 0; i < MAX - index - 1; i++) {
            y /= BASE;
        }
        return (int) (y % BASE);
    }

    /**
     * Extract a base-3 number using one division and one modulo operation.
     * 
     * @param x he number
     * @param index the digit id
     * @return the number
     */
    static int extractDivMod(int x, int index) {
        long y = x & ((1L << 32) - 1);
        y /= DIV[MAX - index - 1];
        return (int) (y % BASE);
    }

    static long div3(long y, int index) {
        long mx = MX[index];
        int s1 = 1;
        int s2 = S2S[index];
        long t1 = (y * mx) >>> 32;
        long q = ((t1 + ((y - t1) >> s1)) >> s2);
        return q;
    }

    /**
     * Extract a base-3 number using one multiplication and one modulo operation.
     * 
     * @param x the number
     * @param index the digit id
     * @return the number
     */
    static int extractMulMod(int x, int index) {
        long d = div3(x & 0xffffffffL, MAX - index - 1);
        return (int) (d % BASE);
    }

    private static final long[] MULTIPLY_TWICE = new long[20];

    static {
        long m = 1;
        for (int i = 0; i < 20; i++) {
            m *= 3;
            MULTIPLY_TWICE[19 - i] = (0x3fffffffffffffffL / m) + 1;
        }
    }

    /**
     * Extract a base-3 number using two multiplications.
     * 
     * @param x the number
     * @param index the digit id
     * @return the number
     */
    static int extractMulMul(int x, int index) {
        long xx = x & 0xffffffffL;
        xx *= MULTIPLY_TWICE[index];
        xx = xx & 0x3fffffffffffffffL;
        xx *= 3;
        xx >>>= 62;
        return (int) xx;
    }

}
