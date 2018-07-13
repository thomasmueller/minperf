package org.minperf.bloom;

/**
 * A standard bloom filter. Nothing special.
 */
public class BloomFilter implements Filter {

    // See also https://hur.st/bloomfilter/?n=357212&p=0.01&m=&k=

    public static BloomFilter construct(long[] keys, int bitsPerKey) {
        int n = keys.length;
        int m = n * bitsPerKey;
        int k = getBestK(m, n);
        BloomFilter f = new BloomFilter(n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final int bits;
    private final long[] data;

    BloomFilter(int entryCount) {
        this(entryCount, 8, 6);
    }

    public long getBitCount() {
        return data.length * 64;
    }

    BloomFilter(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.bits = entryCount * bitsPerKey;
        data = new long[(int) ((bits + 63) / 64)];
    }

    void add(long key) {
        long hash = hash64(key);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = reduce(a, bits);
            data[getArrayIndex(index)] |= getBit(index);
            a += b;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = hash64(key);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = reduce(a, bits);
            if ((data[getArrayIndex(index)] & getBit(index)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private static int getArrayIndex(int index) {
        // use shift instead of division
        // the compiler will most likely do that itself,
        // it's done to be on the safe side
        // (the compiler might think index can be negative)
        // return index / 64;
        return index >>> 6;
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
