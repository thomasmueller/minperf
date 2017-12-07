package org.minperf.hash;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

public class Hash128 {

    public static void main(String... args) {
        measure64UniversalHashCollisions();
        measureSupplementalHash();
        measureSupplementalHash();
        measureSupplementalHash();
        measureSupplementalHash();


//        measure128UniversalHashCollisions();
    }

    public static int supplementalHash2(long hash, long index) {
        // average 4.874351879882813 max 54, 2936 ms
//        return supplementalHash32(hash, index);

        // average 0.5154049560546875 max 37, 1685 ms
//      return (int) supplementalHash64(hash, index);

        // average 0.531073095703125 max 50, 1255 ms
      return (int) supplementalHashWeyl(hash, index);
    }

    public static int supplementalHashWeyl(long hash, long index) {
        // it would be better to use long,
        // but with some processors, 32-bit multiplication
        // seem to be much faster
        // (about 1200 ms for 32 bit, about 2000 ms for 64 bit)
        long x = hash + (index * 0xbf58476d1ce4e5b9L);
         x = (x  ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
        x = ((x >>> 32) ^ x);
        return (int)x;
    }

    public static int supplementalHash32(long hash, long index) {
        // it would be better to use long,
        // but with some processors, 32-bit multiplication
        // seem to be much faster
        // (about 1200 ms for 32 bit, about 2000 ms for 64 bit)
        int x = (int) (Long.rotateLeft(hash, (int) index) ^ index);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }

    static long supplementalHash64(long hash, long index) {
//        long x = (int) (Long.rotateLeft(hash, (int) index) ^ index);
//        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
//        x = x ^ (x >>> 32);

        long x = hash ^ index; //  ^ (index << 32);
        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
//        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
        x = x ^ (x >>> 32);
//        x = (x >>> 16) ^ x;

        // from http://zimbry.blogspot.it/2011/09/better-bit-mixing-improving-on.html

//        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
//        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
//        x = x ^ (x >>> 31);

        return x;
    }





    static void measure64UniversalHashCollisions() {
        Random r = new Random(1);
        long sum = 0;
        long max = 0;
        int count = 10000;
        for(int i=0; i<count; i++) {
            long a = r.nextLong();
            for(int bit = 0; bit < 64; bit++) {
                int b1, b2;
                do {
                    b1 = r.nextInt(64);
                    b2 = r.nextInt(64);
                } while(b1 == b2);
                long b = modify(a, b1);
                b = modify(b, b2);

//                long b = modify(a, bit);

                for(int dbit = 0; dbit < 32; dbit++) {
                    long x = findFirstDifference(a, b, dbit);
                    if (x < 0) {
    //                    System.out.println("no difference for i=" + i + " dbit " + dbit + " change " + b1 + "+" + b2 + " " + a + " " + b);
                        System.out.println("no difference for i=" + i + " dbit " + dbit + " change " + bit + " " + a + " " + b);
                    }
                    max = Math.max(max, x);
                    sum += x;
                }
            }
        }
        System.out.println("average " + (double) sum / count / 128 / 32 + " max " + max);
    }

    static long modify(long x, int bit) {
        return x ^ (1L << bit);
    }

    private static long findFirstDifference(long a, long b, int dbit) {
        for(long index = 0; index < 10000; index++) {
            int xa = (int) supplementalHash2(a, index);
            int xb = (int) supplementalHash2(b, index);
            if ((xa & (1L << dbit)) != (xb & (1L << dbit))) {
                return index;
            }
        }
        return -1;
    }

    private static void measureSupplementalHash() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Random r = new Random(1);
        long sum = 0;
        long time = System.currentTimeMillis();
        for(int i=0; i<1000000; i++) {
            long x = r.nextLong();
            for(int j=0; j<1000; j++) {
                // 2637 ms; dummy -9451949456244
                // 2489 ms; dummy -17737189771045 (1 mul)
//                sum += supplementalHash32(x, j);
                // 2273 ms; dummy -6640029473342721882
                sum += supplementalHash2(x, j);
            }
        }
        time = System.currentTimeMillis() - time;
        System.out.println(time + " ms; dummy " + sum);
    }

    static long[] universalHash(UUID[] list) {
        int len = list.length;
        long[] result = new long[len + 1];
        outer: for (int index = 0;; index++) {
            result[len] = index;
            for (int i = 0; i < len; i++) {
                UUID x = list[i];
                result[i] = universalHash(x.getMostSignificantBits(), x.getLeastSignificantBits(), index);
            }
            long[] test = Arrays.copyOf(result, len - 1);
            Arrays.sort(test);
            for (int i = 1; i < len; i++) {
                if (test[i - 1] == test[i]) {
                    continue outer;
                }
            }
            return result;
        }
    }

    private static void measure128UniversalHashCollisions() {
        Random r = new Random(1);
        long sum = 0;
        long max = 0;
        int count = 10000;
        for(int i=0; i<count; i++) {
            UUID a = new UUID(r.nextLong(), r.nextLong());
            for(int bit = 0; bit < 128; bit++) {
                int b1, b2;
                do {
                    b1 = r.nextInt(128);
                    b2 = r.nextInt(128);
                } while(b1 == b2);
                UUID b = modify(a, b1);
                b = modify(b, b2);

//                UUID b = modify(a, bit);

//                UUID b = new UUID(r.nextLong(), r.nextLong());

//                System.out.println(a + " " + b);
                for(int dbit = 0; dbit < 32; dbit++) {
                    long x = findFirstDifference(a, b, dbit);
                    if (x < 0) {
//                        System.out.println("no difference for i=" + i + " dbit " + dbit + " change " + b1 + "+" + b2 + " " + a + " " + b);
                        System.out.println("no difference for i=" + i + " dbit " + dbit + " change " + bit + " " + a + " " + b);
                    }
                    max = Math.max(max, x);
                    sum += x;
                }
            }
        }
        System.out.println("average " + (double) sum / count / 128 / 32 + " max " + max);
    }

    private static long findFirstDifference(UUID a, UUID b, int dbit) {
        for(long index = 0; index < 10000; index++) {
            int xa = (int) supplementalHash(a, index);
            int xb = (int) supplementalHash(b, index);
            if ((xa & (1L << dbit)) != (xb & (1L << dbit))) {
                return index;
            }
        }
        return -1;
    }

    static UUID modify(UUID x, int bit) {
        if (bit < 64) {
            return new UUID(x.getMostSignificantBits() ^ (1L << bit), x.getLeastSignificantBits());
        }
        return new UUID(x.getMostSignificantBits(), x.getLeastSignificantBits() ^ (1L << (bit - 64)));
    }

    public static int supplementalHash(UUID signature, long index) {
        long a = signature.getMostSignificantBits();
        long b = signature.getLeastSignificantBits();

        // average 0.999060546875 max 22
//if(true)
//        return (int) (LongHash.universalHash(a, index) ^ LongHash.universalHash(b, index));


        // average 1.0294208984375
//        long x = a ^ b ^ index;
        // average 1.105444091796875
//        long x = a ^ Long.rotateLeft(b, (int) index) ^ index;

//        long x = Long.rotateLeft(a, (int) index) ^ b ^ index;

//        xorshift128+
//        uint64_t s1 = s[0];
//        const uint64_t s0 = s[1];
//        const uint64_t result = s0 + s1;
//        s[0] = s0;
//        s1 ^= s1 << 23; // a
//        s[1] = s1 ^ s0 ^ (s1 >> 18) ^ (s0 >> 5); // b, c
//        return result;

        // xoroshiro128+
//            const uint64_t s0 = s[0];
//            uint64_t s1 = s[1];
//            const uint64_t result = s0 + s1;
//            s1 ^= s0;
//            s[0] = rotl(s0, 55) ^ s1 ^ (s1 << 14); // a, b
//            s[1] = rotl(s1, 36); // c
//            return result;

        // 2: average 0.99999365234375 max 23
//        long x = a ^ Long.rotateLeft(b, (int) index) ^ index;
//      x = (x ^ (x >>> 32)) * 0x94d049bb133111ebL;
//        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
//        x = x ^ (x >>> 32);
//      return (int) x;

//      splitmix64
        // 2: average 0.99991943359375 max 21
        // 1: fail at bit 0 & 1
//        long x = a ^ Long.rotateLeft(b, (int) index) ^ index;
//        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
//        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
//        x = x ^ (x >>> 31);
//        return (int) x;

//      2:  average 1.000197265625 max 20
//         1: average 1.81192724609375 max 396
        // fails if 2 bits are changed
        long x = a ^ Long.rotateLeft(b, (int) index) ^ index;
        int y = (int) ((x >>> 32) ^ x);
        y = ((y >>> 16) ^ y) * 0x45d9f3b;
        y = ((y >>> 16) ^ y) * 0x45d9f3b;
        y = (y >>> 16) ^ y;
        return y;

        // average 0.988885986328125 max 22
//      long x = Long.rotateLeft(a, (int) index) + Long.rotateLeft(b, (int) index);
//      x = (x ^ (x >>> 32)) * 0x94d049bb133111ebL;
//      int y = (int) ((x >>> 32) ^ x) * 0x45d9f3b;
//      return y;

      // average 0.957058349609375 max 20
//    long x = Long.rotateLeft(a, (int) index) + Long.rotateLeft(b, (int) index);
//    x = (x ^ (x >>> 32)) * 0x94d049bb133111ebL;
//    int y = (int) ((x >>> 32) ^ x);
//    return y;

        // average 0.963127685546875 max 19
//    long x = Long.rotateLeft(a, (int) index) + Long.rotateRight(b, (int) index);
//    x = (x ^ (x >>> 32)) * 0x94d049bb133111ebL;
//    int y = (int) ((x >>> 32) ^ x);
//    return y;

        // average 1.6323201171875 max 960
//        long x = Long.rotateLeft(a, (int) index) ^ (b >>> ((int)index & 63)) ^ index;
//        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
//        return (int) ((x >>> 32) ^ x);

        // average 0.8640225830078125 max 21
//      long x = Long.rotateLeft(a, (int) index) ^ Long.rotateRight(b, (int) index) ^ index;
//      x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
//      return (int) ((x >>> 32) ^ x);



    }

    static long universalHash(long a, long b, long index) {
        if (index == 0) {
            // normal, fast case;
            return a ^ b;
        }
        long x = a ^ Long.rotateLeft(b, (int) index) ^ index;
        int y = (int) ((x >>> 32) ^ x);
        y = ((y >>> 16) ^ y) * 0x45d9f3b;
        y = ((y >>> 16) ^ y) * 0x45d9f3b;
        y = (y >>> 16) ^ y;
        return y;
    }

    int universalHash2(long a, long b, long index) {
        long x = Long.rotateLeft(a, (int) index) ^ Long.rotateRight(b, (int) index) ^ index;
        x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
        return (int) ((x >>> 32) ^ x);
    }

    // indexed hashing of 128 bit long
//
//    return x;

    private static void measureSipHash128Speed() {
        for(int test=0; test<10; test++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            long sum = 0;
            byte[] data = new byte[8];
            Random r = new Random(1);
            long time = System.currentTimeMillis();
            for(int i=0; i<10000000; i++) {
                r.nextBytes(data);
                LongLong x = getSipHash24B(data, 0, 8, 0, 0);
                sum += x.x;
                sum += x.y;
//                UUID x = getSipHash24C(data, 0, 8, 0, 0);
//                sum += x.getMostSignificantBits();
//                sum += x.getLeastSignificantBits();
//                long a = getSipHash24(data, 0, 8, 0, 0);
//                long b = getSipHash24(data, 0, 8, 1, 1);
//                sum += a;// + b;
            }
            time = System.currentTimeMillis() - time;
            System.out.println("time: " + time + " dummy: " + sum);
            // 418 (long)
            // 623 (twice long)
            // 450 (LongLong)
            // 441 (UUID)
        }
    }

    public final static class LongLong {
        public final long x, y;
        LongLong(long x, long y) {
            this.x = x;
            this.y = y;
        }
    }

    public static long getSipHash24(byte[] b, int start, int end, long k0,
            long k1) {
        long v0 = k0 ^ 0x736f6d6570736575L;
        long v1 = k1 ^ 0x646f72616e646f6dL;
        long v2 = k0 ^ 0x6c7967656e657261L;
        long v3 = k1 ^ 0x7465646279746573L;
        int repeat;
        for (int off = start; off <= end + 8; off += 8) {
            long m;
            if (off <= end) {
                m = 0;
                int i = 0;
                for (; i < 8 && off + i < end; i++) {
                    m |= ((long) b[off + i] & 255) << (8 * i);
                }
                if (i < 8) {
                    m |= ((long) end - start) << 56;
                }
                v3 ^= m;
                repeat = 2;
            } else {
                m = 0;
                v2 ^= 0xff;
                repeat = 4;
            }
            for (int i = 0; i < repeat; i++) {
                v0 += v1;
                v2 += v3;
                v1 = Long.rotateLeft(v1, 13);
                v3 = Long.rotateLeft(v3, 16);
                v1 ^= v0;
                v3 ^= v2;
                v0 = Long.rotateLeft(v0, 32);
                v2 += v1;
                v0 += v3;
                v1 = Long.rotateLeft(v1, 17);
                v3 = Long.rotateLeft(v3, 21);
                v1 ^= v2;
                v3 ^= v0;
                v2 = Long.rotateLeft(v2, 32);
            }
            v0 ^= m;
        }
        return v0 ^ v1 ^ v2 ^ v3;
    }

    public static LongLong getSipHash24B(byte[] b, int start, int end, long k0,
            long k1) {
        long v0 = k0 ^ 0x736f6d6570736575L;
        long v1 = k1 ^ 0x646f72616e646f6dL;
        long v2 = k0 ^ 0x6c7967656e657261L;
        long v3 = k1 ^ 0x7465646279746573L;
        int repeat;
        for (int off = start; off <= end + 8; off += 8) {
            long m;
            if (off <= end) {
                m = 0;
                int i = 0;
                for (; i < 8 && off + i < end; i++) {
                    m |= ((long) b[off + i] & 255) << (8 * i);
                }
                if (i < 8) {
                    m |= ((long) end - start) << 56;
                }
                v3 ^= m;
                repeat = 2;
            } else {
                m = 0;
                v2 ^= 0xff;
                repeat = 4;
            }
            for (int i = 0; i < repeat; i++) {
                v0 += v1;
                v2 += v3;
                v1 = Long.rotateLeft(v1, 13);
                v3 = Long.rotateLeft(v3, 16);
                v1 ^= v0;
                v3 ^= v2;
                v0 = Long.rotateLeft(v0, 32);
                v2 += v1;
                v0 += v3;
                v1 = Long.rotateLeft(v1, 17);
                v3 = Long.rotateLeft(v3, 21);
                v1 ^= v2;
                v3 ^= v0;
                v2 = Long.rotateLeft(v2, 32);
            }
            v0 ^= m;
        }
        return new LongLong(v0 ^ v1, v2 ^ v3);
    }

    public static UUID getSipHash24C(byte[] b, int start, int end, long k0,
            long k1) {
        long v0 = k0 ^ 0x736f6d6570736575L;
        long v1 = k1 ^ 0x646f72616e646f6dL;
        long v2 = k0 ^ 0x6c7967656e657261L;
        long v3 = k1 ^ 0x7465646279746573L;
        int repeat;
        for (int off = start; off <= end + 8; off += 8) {
            long m;
            if (off <= end) {
                m = 0;
                int i = 0;
                for (; i < 8 && off + i < end; i++) {
                    m |= ((long) b[off + i] & 255) << (8 * i);
                }
                if (i < 8) {
                    m |= ((long) end - start) << 56;
                }
                v3 ^= m;
                repeat = 2;
            } else {
                m = 0;
                v2 ^= 0xff;
                repeat = 4;
            }
            for (int i = 0; i < repeat; i++) {
                v0 += v1;
                v2 += v3;
                v1 = Long.rotateLeft(v1, 13);
                v3 = Long.rotateLeft(v3, 16);
                v1 ^= v0;
                v3 ^= v2;
                v0 = Long.rotateLeft(v0, 32);
                v2 += v1;
                v0 += v3;
                v1 = Long.rotateLeft(v1, 17);
                v3 = Long.rotateLeft(v3, 21);
                v1 ^= v2;
                v3 ^= v0;
                v2 = Long.rotateLeft(v2, 32);
            }
            v0 ^= m;
        }
        return new UUID(v0 ^ v1, v2 ^ v3);
    }

}
