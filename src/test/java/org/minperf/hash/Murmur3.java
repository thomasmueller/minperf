/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.minperf.hash;

public class Murmur3 {

    private static long UINT_MASK = 0xFFFFFFFFl;

    public static long hash64(byte[] data, int length, long seed) {
        long m = 0xc6a4a7935bd1e995L;
        int r = 47;
        long h = (seed & UINT_MASK) ^ (length * m);
        int length8 = length >> 3;
        for (int i = 0; i < length8; i++) {
            int i8 = i << 3;
            long k = ((long) data[i8] & 0xff) + (((long) data[i8 + 1] & 0xff) << 8)
                    + (((long) data[i8 + 2] & 0xff) << 16) + (((long) data[i8 + 3] & 0xff) << 24)
                    + (((long) data[i8 + 4] & 0xff) << 32) + (((long) data[i8 + 5] & 0xff) << 40)
                    + (((long) data[i8 + 6] & 0xff) << 48) + (((long) data[i8 + 7] & 0xff) << 56);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h ^= k;
            h *= m;
        }
        switch (length & 7) {
        case 7:
            h ^= (long) (data[(length & ~7) + 6] & 0xff) << 48;
        case 6:
            h ^= (long) (data[(length & ~7) + 5] & 0xff) << 40;
        case 5:
            h ^= (long) (data[(length & ~7) + 4] & 0xff) << 32;
        case 4:
            h ^= (long) (data[(length & ~7) + 3] & 0xff) << 24;
        case 3:
            h ^= (long) (data[(length & ~7) + 2] & 0xff) << 16;
        case 2:
            h ^= (long) (data[(length & ~7) + 1] & 0xff) << 8;
        case 1:
            h ^= (long) (data[length & ~7] & 0xff);
            h *= m;
        }
        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;
        return h;
    }

    public static long hash64x8(byte[] data, int length, long seed) {
        long m = 0xc6a4a7935bd1e995L;
        int r = 47;
        long h = seed;
        int length8 = length >> 3;
        for (int i = 0; i < length8; i++) {
            int i8 = i << 3;
            long k = ((long) data[i8] & 0xff) +
                    (((long) data[i8 + 1] & 0xff) << 8) +
                    (((long) data[i8 + 2] & 0xff) << 16) +
                    (((long) data[i8 + 3] & 0xff) << 24) +
                    (((long) data[i8 + 4] & 0xff) << 32) +
                    (((long) data[i8 + 5] & 0xff) << 40) +
                    (((long) data[i8 + 6] & 0xff) << 48) +
                    (((long) data[i8 + 7] & 0xff) << 56);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h ^= k;
            h *= m;
        }
        h ^= h >>> r;
        h *= m;
        h ^= h >>> r;
        return h;
    }

    public static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    public static int hash32(byte[] data, int offset, int len, int seed) {
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int h1 = seed;
        // round down to 4 byte block
        int roundedEnd = offset + (len & 0xfffffffc);
        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);
            k1 *= c1;
            // ROTL32(k1,15);
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= c2;
            h1 ^= k1;
            // ROTL32(h1,13);
            h1 = (h1 << 13) | (h1 >>> 19);
            h1 = h1 * 5 + 0xe6546b64;
        }
        // tail
        int k1 = 0;
        switch (len & 0x03) {
        case 3:
            k1 = (data[roundedEnd + 2] & 0xff) << 16;
            // fallthrough
        case 2:
            k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            // fallthrough
        case 1:
            k1 |= (data[roundedEnd] & 0xff);
            k1 *= c1;
            // ROTL32(k1,15);
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 *= c2;
            h1 ^= k1;
        }
        // finalization
        h1 ^= len;
        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }

    // https://github.com/yonik/java_util/blob/master/src/util/hash/MurmurHash3.java
    // This java port was authored by Yonik Seeley and also placed into the
    // public domain.

    public static void hash128(byte[] key, int offset, int len, int seed, LongPair out) {
        // The original algorithm does have a 32 bit unsigned seed.
        // We have to mask to match the behavior of the unsigned types and
        // prevent sign extension.
        long h1 = seed & 0x00000000FFFFFFFFL;
        long h2 = seed & 0x00000000FFFFFFFFL;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;
        // round down to 16 byte block
        int roundedEnd = offset + (len & 0xFFFFFFF0);
        for (int i = offset; i < roundedEnd; i += 16) {
            long k1 = getLongLittleEndian(key, i);
            long k2 = getLongLittleEndian(key, i + 8);
            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;
            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }
        long k1 = 0;
        long k2 = 0;
        switch (len & 15) {
        case 15:
            k2 = (key[roundedEnd + 14] & 0xffL) << 48;
        case 14:
            k2 |= (key[roundedEnd + 13] & 0xffL) << 40;
        case 13:
            k2 |= (key[roundedEnd + 12] & 0xffL) << 32;
        case 12:
            k2 |= (key[roundedEnd + 11] & 0xffL) << 24;
        case 11:
            k2 |= (key[roundedEnd + 10] & 0xffL) << 16;
        case 10:
            k2 |= (key[roundedEnd + 9] & 0xffL) << 8;
        case 9:
            k2 |= (key[roundedEnd + 8] & 0xffL);
            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
        case 8:
            k1 = ((long) key[roundedEnd + 7]) << 56;
        case 7:
            k1 |= (key[roundedEnd + 6] & 0xffL) << 48;
        case 6:
            k1 |= (key[roundedEnd + 5] & 0xffL) << 40;
        case 5:
            k1 |= (key[roundedEnd + 4] & 0xffL) << 32;
        case 4:
            k1 |= (key[roundedEnd + 3] & 0xffL) << 24;
        case 3:
            k1 |= (key[roundedEnd + 2] & 0xffL) << 16;
        case 2:
            k1 |= (key[roundedEnd + 1] & 0xffL) << 8;
        case 1:
            k1 |= (key[roundedEnd] & 0xffL);
            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;
        }
        // finalization
        h1 ^= len;
        h2 ^= len;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        h2 += h1;
        out.val1 = h1;
        out.val2 = h2;
    }

    public static void hash128x16(byte[] key, int offset, int len, long seed, LongPair out) {
        long h1 = seed;
        long h2 = seed;
        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;
        int roundedEnd = offset + len;
        if (roundedEnd > key.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = offset; i < roundedEnd; i += 16) {
            long k1 = getLongLittleEndian(key, i);
            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;
            long k2 = getLongLittleEndian(key, i + 8);
            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }
        h1 ^= len;
        h2 ^= len;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        h2 += h1;
        out.val1 = h1;
        out.val2 = h2;
    }

    private static long getLongLittleEndian(byte[] data, int pos) {
        return ((long) data[pos + 7] << 56) |
                ((data[pos + 6] & 0xffL) << 48) |
                ((data[pos + 5] & 0xffL) << 40) |
                ((data[pos + 4] & 0xffL) << 32) |
                ((data[pos + 3] & 0xffL) << 24) |
                ((data[pos + 2] & 0xffL) << 16) |
                ((data[pos + 1] & 0xffL) << 8) |
                ((data[pos] & 0xffL));
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

}
