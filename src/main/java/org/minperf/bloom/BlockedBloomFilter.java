package org.minperf.bloom;

/**
 * A blocked bloom filter. I little bit faster, but needs more space. Not that
 * useful beyond about 20 bits per key, as fpp doesn't decreased further.
 */
public class BlockedBloomFilter implements Filter {

    // TODO how to make it cache line _aligned_ ?

    // Should match the size of a cache line
    private static final int BITS_PER_BLOCK = 64 * 8;
    private static final int LONGS_PER_BLOCK = BITS_PER_BLOCK / 64;
    private static final int BLOCK_MASK = BITS_PER_BLOCK - 1;

    public static BlockedBloomFilter construct(long[] keys, int bitsPerKey) {
        long n = keys.length;
        long m = n * bitsPerKey;
        int k = getBestK(m, n);
        BlockedBloomFilter f = new BlockedBloomFilter((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(long m, long n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final int blocks;
    private final long[] data;

    BlockedBloomFilter(int entryCount) {
        this(entryCount, 8, 6);
    }

    public long getBitCount() {
        return data.length * 64L;
    }

    BlockedBloomFilter(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        long bits = (long) entryCount * bitsPerKey;
        this.blocks = (int) (bits + BITS_PER_BLOCK - 1) / BITS_PER_BLOCK;
        data = new long[(int) (blocks * LONGS_PER_BLOCK) + 8];
    }

    void add(long key) {
        long hash = hash64(key);
        int start = reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            data[start + ((a & BLOCK_MASK) >>> 6)] |= getBit(a);
            a += b;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = hash64(key);
        int start = reduce((int) hash, blocks) * LONGS_PER_BLOCK;
        int a = (int) hash;
        int b = (int) (hash >>> 32);
        for (int i = 0; i < k; i++) {
            if ((data[start + ((a & BLOCK_MASK) >>> 6)] & getBit(a)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private static long getBit(int index) {
        return 1L << index;
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

}
