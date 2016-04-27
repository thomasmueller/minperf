package org.minperf;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

/**
 * Tests for Rice-Golomb codes, Elias Delta codes, and the FastBitBuffer class.
 */
public class BitCodes {

    public static void main(String... args) {
        for (int i = 0; i < 10; i++) {
            testPerformance();
        }
    }

    private static void testPerformance() {
        BitBuffer buff = new BitBuffer(8 * 1024 * 1024);
        int len = 10000;
        for (int i = 0; i < len; i++) {
            buff.writeGolombRice(10, i);
        }
        long time = System.currentTimeMillis();
        for (int j = 0; j < 1000; j++) {
            buff.seek(0);
            for (int i = 0; i < len; i++) {
                assertEquals(i, buff.readGolombRice(10));
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("time: " + time);
    }

    public static void printRiceExamples() {
        for (int i = 0; i < 20; i++) {
            System.out.println("  " + i + " & " +
                    getRice(i, 0) + " & " +
                    getRice(i, 1) + " & " +
                    getRice(i, 2) + " & " +
                    getRice(i, 3) + " & " +
                    getRice(i, 4) + " & " +
                    getRice(i, 5) + " \\\\");
        }
    }

    @Test
    public void testGolombRiceCoding() {
        assertEquals("0", getRice(0, 0));
        assertEquals("10", getRice(1, 0));
        assertEquals("110", getRice(2, 0));
        assertEquals("11..10", getRice(15, 0));
        assertEquals("00", getRice(0, 1));
        assertEquals("01", getRice(1, 1));
        assertEquals("100", getRice(2, 1));
        assertEquals("11..101", getRice(15, 1));
        assertEquals("000", getRice(0, 2));
        assertEquals("001", getRice(1, 2));
        assertEquals("010", getRice(2, 2));
        assertEquals("111011", getRice(15, 2));
        assertEquals("0000", getRice(0, 3));
        assertEquals("0001", getRice(1, 3));
        assertEquals("0010", getRice(2, 3));
        assertEquals("10111", getRice(15, 3));
        for (int shift = 1; shift < 60; shift++) {
            for (int i = 1; i < 100; i++) {
                getRice(i, shift);
            }
            for (int i = 10; i < 100000; i *= 4) {
                getRice(i, shift);
            }
        }
        Random r = new Random();
        for (int i = 0; i < 1000; i++) {
            getRice(r.nextLong() & 0x7fffffffL, 60);
        }
    }

    private static String getRice(long value, int shift) {
        BitBuffer buff = new BitBuffer(128 * 1024);
        buff.writeGolombRice(shift, value);
        int size = BitBuffer.getGolombRiceSize(shift, value);
        buff.seek(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < size; i++) {
            b.append((char) ('0' + buff.readBit()));
        }
        String s = b.toString();
        s = s.replaceFirst("^111111*", "11..1");
        return s;
    }

    static double calcEntropy(double p) {
        // On the Determination of Optimal Parameterized Prefix Codes
        // for Adaptive Entropy Coding
        // Amir Said
        // 2.13, page 12
        double m = 1 - p;
        return -Math.log(1 - m) / Math.log(2) - (m / (1 - m)) *
                Math.log(m) / Math.log(2);
    }

    static double calcEstimatedBits(int k, double p) {
        // alpha = p - 1
        return k + (1 / (1 - Math.pow(1 - p, Math.pow(2, k))));
    }

    static int calcBestGolombRiceShift(double p) {
        double goldenRatio = (Math.sqrt(5) + 1) / 2;

        // variant a
        double mean = (1 - p) / p;
        double logGoldenRatioMinus1 = Math.log(goldenRatio - 1);
        double k = 1 + (Math.log(logGoldenRatioMinus1 /
                Math.log(mean / (mean + 1))) / Math.log(2));

        // variant b
        // from "On the Determination of Optimal Parameterized
        // Prefix Codes for Adaptive Entropy Coding"
        // double k2 = 1 + Math.log(Math.log(goldenRatio) /
        // Math.log(1 / (1 - p))) / Math.log(2);

        return Math.max(0, (int) k);
    }

    public static void printEliasDeltaExample() {
        System.out.println("Elias Delta code examples");
        for (int i = 1; i < 10; i++) {
            System.out.println("  " + i + " & " +
                    getEliasDelta(i) + " \\\\");
        }
        for (int i = 10; i < 1000000; i *= 10) {
            System.out.println("  " + i + " & " +
                    getEliasDelta(i) + " \\\\");
        }
    }

    @Test
    public void testEliasDeltaRoundtrip() {
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            BitBuffer buff = new BitBuffer(8 * 1024 * 1024);
            long val = (r.nextLong() & 0xfffffffL) + 1;
            buff.writeEliasDelta(val);
            buff.writeNumber(123, 10);
            int pos = buff.position();
            byte[] data = buff.toByteArray();
            assertEquals((pos + 7) / 8, data.length);
            buff = new BitBuffer(buff.toByteArray());
            assertEquals(val, buff.readEliasDelta());
            assertEquals(123, buff.readNumber(10));
            assertEquals(pos, buff.position());
        }
    }

    @Test
    public void testEliasDeltaCoding() {
        assertEquals("1", getEliasDelta(1));
        assertEquals("0100", getEliasDelta(2));
        assertEquals("0101", getEliasDelta(3));
        assertEquals("01100", getEliasDelta(4));
        assertEquals("01111", getEliasDelta(7));
        for (int i = 1; i < 100; i++) {
            getEliasDelta(i);
        }
        for (int i = 10; i < 1000000000; i *= 1.1) {
            getEliasDelta(i);
        }
    }

    static String getEliasDelta(int value) {
        BitBuffer buff = new BitBuffer(8 * 1024 * 1024);
        buff.writeEliasDelta(value);
        int size = buff.position();
        buff.seek(0);
        long test = buff.readEliasDelta();
        assertEquals(value, test);
        assertEquals(size, buff.position());
        buff.seek(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < size; i++) {
            b.append((char) ('0' + buff.readBit()));
        }
        String s = b.toString();
        return s;
    }

    @Test
    public void testWriteBuffer() {
        BitBuffer buff = new BitBuffer(8000);
        for (int i = 1; i < 100; i++) {
            BitBuffer b = new BitBuffer(160);
            b.writeEliasDelta(i);
            buff.write(b);
        }
        buff.seek(0);
        for (int i = 1; i < 100; i++) {
            assertEquals(i, buff.readEliasDelta());
        }
    }

    @Test
    public void testSeek() {
        BitBuffer buff = new BitBuffer(8000);
        for (int i = 0; i < 100; i++) {
            buff.seek(10 * i);
            buff.writeNumber(i, 8);
        }
        buff = new BitBuffer(buff.toByteArray());
        for (int i = 0; i < 100; i++) {
            buff.seek(10 * i);
            assertEquals(i, buff.readNumber(8));
        }
    }

    @Test
    public void testFoldUnfold() {
        assertEquals(0, BitBuffer.foldSigned(0));
        assertEquals(1, BitBuffer.foldSigned(1));
        assertEquals(2, BitBuffer.foldSigned(-1));
        testFoldUnfold(0);
        testFoldUnfold(1);
        testFoldUnfold(-1);
        testFoldUnfold(2);
        testFoldUnfold(-2);
        testFoldUnfold(Long.MAX_VALUE / 2);
        testFoldUnfold(Long.MIN_VALUE / 2 + 1);
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long x = r.nextLong() & 0xfffffffL;
            testFoldUnfold(x);
            testFoldUnfold(-x);
        }
    }

    private static void testFoldUnfold(long x) {
        assertEquals(x, BitBuffer.unfoldSigned(BitBuffer.foldSigned(x)));
    }

    @Test
    public void testGolombRice() {
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            BitBuffer buff = new BitBuffer(8 * 1024 * 1024);
            int shift = r.nextInt(8);
            int val = r.nextInt(100000);
if(val == 64588) {
;    System.out.println("test");
}
            buff.writeGolombRice(shift, val);
            buff.writeGolombRice(1, 10);
            int len = buff.position();
            assertEquals(len, BitBuffer.getGolombRiceSize(shift, val) +
                    BitBuffer.getGolombRiceSize(1, 10));
            buff = new BitBuffer(buff.toByteArray());
            assertEquals(val, buff.readGolombRice(shift));
            assertEquals(10, buff.readGolombRice(1));
        }
    }

    public static void printPositiveMapping() {
        StringBuilder b1 = new StringBuilder();
        StringBuilder b2 = new StringBuilder();
        b1.append(0);
        b2.append(1);
        for (int i = 1; i <= 3; i++) {
            b1.append(", ").append(i);
            b1.append(", ").append(-i);
            b2.append(", ").append(BitBuffer.foldSigned(i) + 1);
            b2.append(", ").append(BitBuffer.foldSigned(-i) + 1);
        }
        System.out.println("(" + b1 + ", ...) is mapped to (" + b2 + ", ...)");
    }

}
