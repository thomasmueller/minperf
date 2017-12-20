package org.minperf.hash;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.util.UUID;

import org.junit.Test;

public class MixTest {

    public static void main(String... args) {
        measureSupplementalHashCollisions128(1);
        measureSupplementalHashCollisions128(2);
        measureSupplementalHashSpeed128();

        measureSupplementalHashCollisions(1);
        measureSupplementalHashCollisions(2);
        measureSupplementalHashSpeed();

    }

    @Test
    public void inverse64() {
        Random r = new Random(1);
        for (int i = 0; i < 10000; i++) {
            long x = r.nextLong();
            if ((x & 1) == 0) {
                continue;
            }
            long inverse = findInverse64(x);
            for (int j = 0; j < 10; j++) {
                long y = r.nextLong();
                assertEquals("*" + x + " *" + inverse, y, y * x * inverse);
            }
        }
    }

    @Test
    public void random32() {
        Random r = new Random(1);
        for (int i = 0; i < 1000000; i++) {
            int x = r.nextInt();
            assertEquals(x, Mix.unhash32(Mix.hash32(x)));
        }
    }

    @Test
    public void random64() {
        Random r = new Random(1);
        for (int i = 0; i < 1000000; i++) {
            long x = r.nextLong();
            assertEquals(x, Mix.unhash64(Mix.hash64(x)));
        }
    }

    private static long modify(long x, int bit) {
        return x ^ (1L << bit);
    }

    private static UUID modify128(UUID x, int bit) {
        if (bit < 64) {
            return new UUID(x.getMostSignificantBits() ^ (1L << bit), x.getLeastSignificantBits());
        }
        return new UUID(x.getMostSignificantBits(), x.getLeastSignificantBits() ^ (1L << (bit - 64)));
    }

    private static long findFirstSupplementalHashDifference(long a, long b, int dbit) {
        for (long index = 0; index < 10000; index++) {
            int xa = (int) supplementalHash(a, index);
            int xb = (int) supplementalHash(b, index);
            if ((xa & (1L << dbit)) != (xb & (1L << dbit))) {
                return index;
            }
        }
        return -1;
    }

    private static long findFirstSupplementalHashDifference128(UUID a, UUID b, int dbit) {
        for (long index = 0; index < 10000; index++) {
            int xa = (int) supplementalHash128(a, index);
            int xb = (int) supplementalHash128(b, index);
            if ((xa & (1L << dbit)) != (xb & (1L << dbit))) {
                return index;
            }
        }
        return -1;
    }

    private static void measureSupplementalHashCollisions(int changedBitsCount) {
        Random r = new Random(1);
        long sum = 0;
        long max = 0;
        int count = 10000;
        for (int i = 0; i < count; i++) {
            long a = r.nextLong();
            for (int bit = 0; bit < 64; bit++) {
                int b1, b2;
                do {
                    b1 = r.nextInt(64);
                    b2 = r.nextInt(64);
                } while (b1 == b2);
                long b = modify(a, b1);
                if (changedBitsCount == 2) {
                    b = modify(b, b2);
                }
                for (int dbit = 0; dbit < 64; dbit++) {
                    long x = findFirstSupplementalHashDifference(a, b, dbit);
                    if (x < 0) {
                        System.out.println(
                                "no difference for i=" + i + " dbit " + dbit + " change " + bit + " " + a + " " + b);
                    }
                    max = Math.max(max, x);
                    sum += x;
                }
            }
        }
        System.out.println("bits: 64 changed: " + changedBitsCount +
                " average: " + (double) sum / count / 64 / 64 + " max: " + max);
    }

    private static void measureSupplementalHashCollisions128(int changedBitsCount) {
        Random r = new Random(1);
        long sum = 0;
        long max = 0;
        int count = 10000;
        for (int i = 0; i < count; i++) {
            UUID a = new UUID(r.nextLong(), r.nextLong());
            for (int bit = 0; bit < 128; bit++) {
                int b1, b2;
                do {
                    b1 = r.nextInt(128);
                    b2 = r.nextInt(128);
                } while (b1 == b2);
                UUID b = modify128(a, b1);
                if (changedBitsCount == 2) {
                    b = modify128(b, b2);
                }
                for (int dbit = 0; dbit < 128; dbit++) {
                    long x = findFirstSupplementalHashDifference128(a, b, dbit);
                    if (x < 0) {
                        System.out.println(
                                "no difference for i=" + i + " dbit " + dbit + " change " + bit + " " + a + " " + b);
                    }
                    max = Math.max(max, x);
                    sum += x;
                }
            }
        }
        System.out.println("bits: 128 changed: " + changedBitsCount +
                " average: " + (double) sum / count / 128 / 128 + " max: " + max);
    }

    private static void measureSupplementalHashSpeed() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Random r = new Random(1);
        long sum = 0;
        long time = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            long x = r.nextLong();
            for (int j = 0; j < 1000; j++) {
                sum += supplementalHash(x, j);
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("bits: 64 time: " + time + " dummy: " + sum);
    }

    private static void measureSupplementalHashSpeed128() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Random r = new Random(1);
        long sum = 0;
        long time = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            long a = r.nextLong();
            long b = r.nextLong();
            UUID signature = new UUID(a, b);
            for (int j = 0; j < 1000; j++) {
                sum += supplementalHash128(signature, j);
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println("bits: 128 time: " + time + " dummy: " + sum);
    }

    private static long supplementalHash(long a, long index) {
        // bits: 64 changed: 1 average: 0.9989029052734375 max: 22
        // bits: 64 changed: 2 average: 1.00027041015625 max: 23
        // bits: 64 time: 7476 dummy: 6254326271697254890
        // return LongHash.universalHash(a, index);

        // bits: 64 changed: 1 average: 1.05794130859375 max: 52
        // bits: 64 changed: 2 average: 1.0269772216796875 max: 42
        // bits: 64 time: 1293 dummy: -49479994468383
        return Mix.supplementalHashWeyl(a, index);
    }

    private static long supplementalHash128(UUID signature, long index) {
        long a = signature.getMostSignificantBits();
        long b = signature.getLeastSignificantBits();

        // bits: 128 changed: 1 average: 1.0002647827148436 max: 25
        // bits: 128 changed: 2 average: 1.0001899780273438 max: 24
        // bits: 128 time: 14855 dummy: 4522406346767388702
        // return LongHash.universalHash(a, index) ^ LongHash.universalHash(b, index);

        // bits: 128 changed: 1 average: 1.00045244140625 max: 23
        // bits: 128 changed: 2 average: 1.0158296752929687 max: 28
        // bits: 128 time: 2450 dummy: -5820939404663996947
        // long x = a ^ Long.rotateLeft(b, (int) index) ^ index;
        // x = (x ^ (x >>> 32)) * 0x94d049bb133111ebL;
        // x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
        // x = x ^ (x >>> 32);
        // return x;

        // bits: 128 changed: 1 average: 1.0089444580078124 max: 32
        // bits: 128 changed: 2 average: 1.0058574096679687 max: 27
        // bits: 128 time: 1616 dummy: 6194542897375344905
        long x = (a * (index + 1)) + b;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x = x ^ (x >>> 31);
        return x;

    }

    private static long findInverse64(long x) {
        long y = x;
        y = f64(x, y);
        y = f64(x, y);
        y = f64(x, y);
        y = f64(x, y);
        y = f64(x, y);
        return y;
    }

    private static long f64(long x, long y) {
        return y * (2 - y * x);
    }

}
