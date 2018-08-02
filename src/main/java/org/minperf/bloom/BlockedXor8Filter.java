package org.minperf.bloom;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

/**
 * A blocked bloom filter. I little bit faster, but needs more space. Not that
 * useful beyond about 20 bits per key, as fpp doesn't decreased further.
 */
public class BlockedXor8Filter implements Filter {

    // max load per block is 50 (50 * 1.23 = 61, and cache line is 64 bytes)
    private static final int MAX_BLOCK_SIZE = 50;
    private static final int BITS_PER_FINGERPRINT = 8;
    private static final int SMALL_BLOCK_LENGTH = 21;

    public static BlockedXor8Filter construct(long[] keys, int bitsPerKey) {
        int n = keys.length;
        int blockSize = 64;
        for (; blockSize > 1; blockSize--) {
            int blocks = (n + blockSize - 1) / blockSize;
            int[] blockSizes = new int[blocks];
            for(long k : keys) {
                long hash = hash64(k);
                int b = reduce((int) hash, blocks);
                blockSizes[b]++;
            }
            boolean failed = false;
            for(int x : blockSizes) {
                if (x > MAX_BLOCK_SIZE) {
                    failed = true;
                    break;
                }
            }
            if (!failed) {
                break;
            }
        }
        return new BlockedXor8Filter(keys, blockSize);
    }

    private final int blocks;
    private final int cacheLineOffset;
    private byte[] fingerprints;

    public long getBitCount() {
        return fingerprints.length * 8L;
    }

    BlockedXor8Filter(long[] keys, int blockSize) {
        // System.out.println("block size: " + blockSize);
        int n = keys.length;
        this.blocks = (n + blockSize - 1) / blockSize;
        long[][] hashes = new long[blocks][MAX_BLOCK_SIZE];
        int[] blockSizes = new int[blocks];
        for(long k : keys) {
            long hash = hash64(k);
            int b = reduce((int) hash, blocks);
            hashes[b][blockSizes[b]++] = hash;
        }
        this.fingerprints = new byte[64 * blocks + 64];
        this.cacheLineOffset = getCacheLineOffset(fingerprints);
        for (int i = 0; i < blocks; i++) {
            byte[] block = constructBlock(hashes[i], blockSizes[i]);
            System.arraycopy(block, 0, fingerprints, i * 64 + cacheLineOffset, 64);
        }
    }

    static byte[] constructBlock(long[] hashes, int blockSize) {
        long[] keys = Arrays.copyOf(hashes, blockSize);
        XorFilter_8bit f = new XorFilter_8bit(keys, 63);
        byte[] data = new byte[64];
        int hashIndex = f.getHashIndex();
        if (hashIndex > 0xff) {
            throw new IllegalArgumentException();
        }
        data[0] = (byte) hashIndex;
        for (int i = 0; i < 63; i++) {
            data[i + 1] = f.getFingerprints()[i];
        }
        return data;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = hash64(key);
        int b = reduce((int) hash, blocks);
        int start = cacheLineOffset + (b << 6);
        int hashIndex = fingerprints[start] & 0xff;
        hash = hash64(hash + hashIndex);
        int f = fingerprint(hash);
        int r0 = (int) hash;
        int r1 = (int) (hash >>> 16);
        int r2 = (int) (hash >>> 32);
        int h0 = reduce(r0, SMALL_BLOCK_LENGTH);
        int h1 = reduce(r1, SMALL_BLOCK_LENGTH) + SMALL_BLOCK_LENGTH;
        int h2 = reduce(r2, SMALL_BLOCK_LENGTH) + 2 * SMALL_BLOCK_LENGTH;
        f ^= fingerprints[start + 1 + h0] ^ fingerprints[start + 1 + h1] ^ fingerprints[start + 1 + h2];
        return (f & 0xff) == 0;
    }

    private int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    private static long hash64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x = x ^ (x >>> 31);
        return x;
    }

    private int getCacheLineOffset(byte[] data) {
        for (int i = 0; i < 10; i++) {
            int x = tryGetCacheLineOffset(data, i + 3);
            if (x != -1) {
                return x;
            }
        }
        System.out.println("Cache line start not found");
        return 0;
    }

    private int tryGetCacheLineOffset(byte[] data, int testCount) {
        // assume synchronization between two threads is faster(?)
        // if each thread works on the same cache line
        int[] counters = new int[64];
        int testOffset = 8;
        for (int test = 0; test < testCount; test++) {
            for (int offset = 0; offset < 64; offset++) {
                final int o = offset;
                final Semaphore sema = new Semaphore(0);
                Thread t = new Thread() {
                    public void run() {
                        try {
                            sema.acquire();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        for (int i = 0; i < 1000000; i++) {
                            data[o + testOffset] = data[o];
                        }
                    }
                };
                t.start();
                sema.release();
                data[o] = 1;
                int counter = 0;
                byte waitfor = 1;
                for (int i = 0; i < 1000000; i++) {
                    byte x = data[o + testOffset];
                    if (x == waitfor) {
                        data[o]++;
                        counter++;
                        waitfor++;
                    }
                }
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                counters[offset] += counter;
            }
        }
        Arrays.fill(data, 0, testOffset + 64, (byte) 0);
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 64; i++) {
            // average of 3
            int avg3 = (counters[(i - 1 + 64) % 64] + counters[i] + counters[(i + 1) % 64]) / 3;
            low = Math.min(low, avg3);
            high = Math.max(high, avg3);
        }
        if (low * 1.1 > high) {
            // no significant difference between low and high
            return -1;
        }
        int lowCount = 0;
        boolean[] isLow = new boolean[64];
        for (int i = 0; i < 64; i++) {
            if (counters[i] < (low + high) / 2) {
                isLow[i] = true;
                lowCount++;
            }
        }
        if (lowCount != 8) {
            // unclear
            return -1;
        }
        for (int i = 0; i < 64; i++) {
            if (isLow[(i - 1 + 64) % 64] && !isLow[i]) {
                return i;
            }
        }
        return -1;
    }

}
