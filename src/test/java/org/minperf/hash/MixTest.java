package org.minperf.hash;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

public class MixTest {

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
