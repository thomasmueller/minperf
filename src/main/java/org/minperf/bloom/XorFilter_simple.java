package org.minperf.bloom;

import java.util.Random;

import org.minperf.hash.Mix;

public class XorFilter_simple implements Filter {

    private long seed;
    private byte[] data;

    public long getBitCount() {
        return data.length * 8;
    }

    public static XorFilter_simple construct(long[] keys) {
        return new XorFilter_simple(keys);
    }

    public XorFilter_simple(long[] keys) {
        data = new byte[(int) (1.23 * keys.length)];
        while (true) {
            seed = new Random().nextLong();
            long[] stack = new long[keys.length * 2];
            if (map(keys, seed, stack)) {
                assign(stack, data);
                return;
            }
        }
    }

    private boolean map(long[] keys, long seed, long[] stack) {
        int[] C = new int[(int) (1.23 * keys.length)];
        long[] H = new long[(int) (1.23 * keys.length)];
        for (long k : keys) {
            long x = hash(k, seed);
            for (int i = 0; i < 3; i++) {
                C[h(x, i)]++;
                H[h(x, i)] ^= x;
            }
        }
        int stackPos = 0;
        while (stackPos < 2 * keys.length) {
            int old = stackPos;
            for (int index = 0; index < C.length; index++) {
                if (C[index] == 1) {
                    long x = H[index];
                    stack[stackPos++] = x;
                    stack[stackPos++] = index;
                    for (int i = 0; i < 3; i++) {
                        C[h(x, i)]--;
                        H[h(x, i)] ^= x;
                    }
                }
            }
            if (stackPos == old) {
                return false;
            }
        }
        return true;
    }

    private void assign(long[] stack, byte[] b) {
        for(int stackPos = stack.length; stackPos > 0;) {
            int index = (int) stack[--stackPos];
            long x = stack[--stackPos];
            b[index] = (byte) (fingerprint(x) ^ b[h(x, 0)] ^ b[h(x, 1)] ^ b[h(x, 2)]);
        }
    }

    static long hash(long key, long seed) {
        return Mix.hash64(key + seed);
    }

    int h(long x, int index) {
        return reduce((int) Long.rotateLeft(x, 22 * index), data.length / 3) + index * data.length / 3;
    }

    @Override
    public boolean mayContain(long key) {
        long x = hash(key, seed);
        byte f = fingerprint(x);
        return f == (data[h(x, 0)] ^ data[h(x, 1)] ^ data[h(x, 2)]);
    }

    private byte fingerprint(long x) {
        return (byte) x;
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

}
