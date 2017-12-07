package org.minperf.hash;

// actually, this is not SipHash:
// it is just one iteration, and no mixing
public class FastSipHash {

    public static long hash(byte[] b, int len, long k0) {
        long v0 = k0 ^ 0x736f6d6570736575L;
        long v1 = 0x646f72616e646f6dL;
        long v2 = k0 ^ 0x6c7967656e657261L;
        long v3 = 0x7465646279746573L;
        for (int off = 0; off <= len; off += 8) {
            long m = 0;
            for (int i = 0; i < 8 && off + i < len; i++) {
                m |= ((long) b[off + i] & 255) << (8 * i);
            }
            v3 ^= m;
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
            v0 ^= m;
        }
        return v0 ^ v1 ^ v2 ^ v3;
    }

}
