package org.minperf.hash;

import java.util.Random;

public class HashPerformanceTest {

    public static void main(String... args) throws InterruptedException {
        test();
        test();
        test();
    }

    private static void test() throws InterruptedException {
        Thread.sleep(1000);
        long dummy = 0;
        for (int avgSize = 16; avgSize < 128; avgSize *= 2) {
            byte[][] data = new byte[1024 * 1024][avgSize];
            Random r = new Random(1);
            for (int i = 0; i < data.length; i++) {
                r.nextBytes(data[i]);
            }
            long time = System.nanoTime();
            LongPair pair = new LongPair();
            for (int j = 0; j < avgSize; j++) {
                for (int i = 0; i < data.length; i++) {

                    // dummy += Murmur2.hash64(data[i], avgSize, j);
                    // bits: 128 10 ns/key
                    // bits: 256 17 ns/key
                    // bits: 512 34 ns/key

                    // dummy += Murmur2.hash64x8(data[i], avgSize, j);
                    // bits: 128 8 ns/key
                    // bits: 256 16 ns/key
                    // bits: 512 31 ns/key

                    // dummy += XXHash64.hash64(data[i], 0, avgSize, i);
                    // bits: 128 16 ns/key
                    // bits: 256 29 ns/key
                    // bits: 512 51 ns/key

                    // SpookyHash.hash128x32(data[i], 0, avgSize, i, pair);
                    // dummy += pair.val1;
                    // dummy += pair.val2;
                    // bits: 256 34 ns/key
                    // bits: 512 53 ns/key

                    // dummy += Murmur3.hash64x8(data[i], avgSize, j);
                    // bits: 128 8 ns/key
                    // bits: 256 16 ns/key
                    // bits: 512 31 ns/key

                    Murmur3.hash128x16(data[i], 0, avgSize, i, pair);
                    dummy += pair.val1;
                    dummy += pair.val2;
                    // bits: 128 16 ns/key
                    // bits: 256 25 ns/key
                    // bits: 512 42 ns/key

                }
            }
            time = System.nanoTime() - time;
            System.out.println("bits: " + avgSize * 8 + " " + time / data.length / avgSize + " ns/key");
        }
        System.out.println("dummy " + dummy);
    }

}
