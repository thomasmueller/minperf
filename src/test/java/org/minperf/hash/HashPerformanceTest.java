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
        for (int avgSize = 4; avgSize < 128; avgSize *= 2) {
            byte[][] data = new byte[1024 * 1024][avgSize];
            Random r = new Random(1);
            for (int i = 0; i < data.length; i++) {
                r.nextBytes(data[i]);
            }
            long time = System.nanoTime();
            for (int j = 0; j < avgSize; j++) {
                for (int i = 0; i < data.length; i++) {

                    // dummy += Murmur2.hash64(data[i], avgSize, j);

                    dummy += Murmur2.hash64_blocksOf8(data[i], avgSize, j);

                    // dummy += MurmurHash3.murmurhash3_x86_32(data[i], 0, avgSize, i);

                    // LongPair pair = new LongPair();
                    // MurmurHash3.murmurhash3_x64_128(data[i], 0, avgSize, i, pair);
                    // dummy += pair.val1;

                    // dummy += XXHash64.hash(data[i], 0, avgSize, i);

                    // dummy += FastSipHash.hash(data[i], avgSize, i);

                }
            }
            // Murmur2.hash64
//            bits: 32 6 ns/key
//            bits: 64 9 ns/key
//            bits: 128 12 ns/key
//            bits: 256 18 ns/key
//            bits: 512 33 ns/key
            // Murmur2.hash64_blocksOf8
//            bits: 32 1 ns/key
//            bits: 64 7 ns/key
//            bits: 128 10 ns/key
//            bits: 256 16 ns/key
//            bits: 512 33 ns/key
            // MurmurHash3.murmurhash3_x86_32
//            bits: 32 5 ns/key
//            bits: 64 9 ns/key
//            bits: 128 13 ns/key
//            bits: 256 21 ns/key
//            bits: 512 40 ns/key
            // MurmurHash3.murmurhash3_x64_128(data[i], 0, avgSize, i, pair);
//            bits: 32 10 ns/key
//            bits: 64 13 ns/key
//            bits: 128 19 ns/key
//            bits: 256 30 ns/key
//            bits: 512 48 ns/key
            // FastSipHash.hash(data[i], avgSize, i);
//            bits: 32 5 ns/key
//            bits: 64 11 ns/key
//            bits: 128 17 ns/key
//            bits: 256 25 ns/key
//            bits: 512 47 ns/key
            //  XXHash64.hash(data[i], 0, avgSize, i);
//            bits: 32 7 ns/key
//            bits: 64 13 ns/key
//            bits: 128 17 ns/key
//            bits: 256 30 ns/key
//            bits: 512 50 ns/key


            time = System.nanoTime() - time;
            System.out.println("bits: " + avgSize * 8 + " " + time / data.length / avgSize + " ns/key");
        }
        System.out.println("dummy " + dummy);
    }

}
