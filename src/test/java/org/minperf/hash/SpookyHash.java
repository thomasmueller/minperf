package org.minperf.hash;

public class SpookyHash {

    static void hash128x32(byte[] key, int offset, int len, int seed, LongPair out) {
        // 128 bit: 16 bytes
        // 256 bit: 32 bytes
        // 512 bit: 64 bytes
        int blocksOf32  = (len/32)*32;
        int end32 = offset + blocksOf32;
        // handle all complete sets of 32 bytes
        long a = seed, b = seed, c = 0xdeadbeefdeadbeefL, d = 0xdeadbeefdeadbeefL;
        for (; offset < end32; offset += 32) {
            c += readLong(key, offset);
            d += readLong(key, offset + 8);

            // shortMix(a,b,c,d);
            c = Long.rotateLeft(c,50);  c += d;  a ^= c;
            d = Long.rotateLeft(d,52);  d += a;  b ^= d;
            a = Long.rotateLeft(a,30);  a += b;  c ^= a;
            b = Long.rotateLeft(b,41);  b += c;  d ^= b;
            c = Long.rotateLeft(c,54);  c += d;  a ^= c;
            d = Long.rotateLeft(d,48);  d += a;  b ^= d;
            a = Long.rotateLeft(a,38);  a += b;  c ^= a;
            b = Long.rotateLeft(b,37);  b += c;  d ^= b;
            c = Long.rotateLeft(c,62);  c += d;  a ^= c;
            d = Long.rotateLeft(d,34);  d += a;  b ^= d;
            a = Long.rotateLeft(a,5);   a += b;  c ^= a;
            b = Long.rotateLeft(b,36);  b += c;  d ^= b;

            a += readLong(key, offset + 16);
            b += readLong(key, offset + 24);
        }

        d += len << 56;
        c += 0xdeadbeefdeadbeefL;
        d += 0xdeadbeefdeadbeefL;

        // shortEnd(a,b,c,d);
        d ^= c;  c = Long.rotateLeft(c,15);  d += c;
        a ^= d;  d = Long.rotateLeft(d,52);  a += d;
        b ^= a;  a = Long.rotateLeft(a,26);  b += a;
        c ^= b;  b = Long.rotateLeft(b,51);  c += b;
        d ^= c;  c = Long.rotateLeft(c,28);  d += c;
        a ^= d;  d = Long.rotateLeft(d,9);   a += d;
        b ^= a;  a = Long.rotateLeft(a,47);  b += a;
        c ^= b;  b = Long.rotateLeft(b,54);  c += b;
        d ^= c;  c = Long.rotateLeft(c,32);  d += c;
        a ^= d;  d = Long.rotateLeft(d,25);  a += d;
        b ^= a;  a = Long.rotateLeft(a,63);  b += a;

        out.val1 = a;
        out.val2 = b;
    }

    static void shortEnd(long a, long b, long c, long d) {
        d ^= c;  c = Long.rotateLeft(c,15);  d += c;
        a ^= d;  d = Long.rotateLeft(d,52);  a += d;
        b ^= a;  a = Long.rotateLeft(a,26);  b += a;
        c ^= b;  b = Long.rotateLeft(b,51);  c += b;
        d ^= c;  c = Long.rotateLeft(c,28);  d += c;
        a ^= d;  d = Long.rotateLeft(d,9);   a += d;
        b ^= a;  a = Long.rotateLeft(a,47);  b += a;
        c ^= b;  b = Long.rotateLeft(b,54);  c += b;
        d ^= c;  c = Long.rotateLeft(c,32);  d += c;
        a ^= d;  d = Long.rotateLeft(d,25);  a += d;
        b ^= a;  a = Long.rotateLeft(a,63);  b += a;
    }

    static void shortMix(long a, long b, long c, long d) {
        c = Long.rotateLeft(c,50);  c += d;  a ^= c;
        d = Long.rotateLeft(d,52);  d += a;  b ^= d;
        a = Long.rotateLeft(a,30);  a += b;  c ^= a;
        b = Long.rotateLeft(b,41);  b += c;  d ^= b;
        c = Long.rotateLeft(c,54);  c += d;  a ^= c;
        d = Long.rotateLeft(d,48);  d += a;  b ^= d;
        a = Long.rotateLeft(a,38);  a += b;  c ^= a;
        b = Long.rotateLeft(b,37);  b += c;  d ^= b;
        c = Long.rotateLeft(c,62);  c += d;  a ^= c;
        d = Long.rotateLeft(d,34);  d += a;  b ^= d;
        a = Long.rotateLeft(a,5);   a += b;  c ^= a;
        b = Long.rotateLeft(b,36);  b += c;  d ^= b;
    }

    private static long readLong(byte[] data, int pos) {
        return ((long) data[pos] & 0xff) +
                (((long) data[pos + 1] & 0xff) << 8) +
                (((long) data[pos + 2] & 0xff) << 16) +
                (((long) data[pos + 3] & 0xff) << 24) +
                (((long) data[pos + 4] & 0xff) << 32) +
                (((long) data[pos + 5] & 0xff) << 40) +
                (((long) data[pos + 6] & 0xff) << 48) +
                (((long) data[pos + 7] & 0xff) << 56);
    }

}
