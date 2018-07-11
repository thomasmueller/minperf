package org.minperf.bloom;

import org.minperf.hem.RandomGenerator;

public class BloomFilter {

    // See also https://hur.st/bloomfilter/?n=357212&p=0.01&m=&k=

    public static void main(String... args) {
        for(int bitsPerKey = 4; bitsPerKey < 20; bitsPerKey++) {
            test(bitsPerKey);
        }
    }
    public static void test(int bitsPerKey) {
        int len = 4 * 1024 * 1024;
        int n = 1024 * 1024;
        int m = n * bitsPerKey;
        int k = getBestK(m, n);
        int testCount = 1;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        BloomFilter f = new BloomFilter(len, bitsPerKey, k);
        long time = System.nanoTime();
        for (int test = 0; test < testCount; test++) {
            for (int i = 0; i < len; i++) {
                f.add(list[i]);
            }
        }
        long addTime = (System.nanoTime() - time) / len;
        time = System.nanoTime();
        int falsePositives = 0;
        for (int test = 0; test < testCount; test++) {
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(list[i])) {
                    f.mayContain(list[i]);
                    throw new AssertionError();
                }
            }
            for (int i = len; i < len * 2; i++) {
                if (f.mayContain(list[i])) {
                    falsePositives++;
                }
            }
        }
        long getTime = (System.nanoTime() - time) / len / testCount;
        double falsePositiveRate = (100. / testCount / len * falsePositives);
        System.out.println("Bloom false positives: " + falsePositiveRate +
                "% " + (double) f.getBitCount() / len + " bits/key " +
                "add: " + addTime + " get: " + getTime + " ns/key " + len + " count");

    }

    static int getBestK(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final int bits;
    private final long[] data;

    BloomFilter(int entryCount) {
        this(entryCount, 8, 6);
    }

    private double getBitCount() {
        return data.length * 64;
    }

    BloomFilter(int entryCount, int bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.bits = entryCount * bitsPerKey;
        data = new long[(int) ((bits + 63) / 64)];
    }

    void add(long hashCode) {
        long h = hash64(hashCode);
        int a = (int) (h >>> 32);
        int b = (int) h;
        for (int i = 0; i < k; i++) {
            int index = reduce(a, bits);
            data[getArrayIndex(index)] |= getBit(index);
            a += b;
        }
    }

    boolean mayContain(long hashCode) {
        long h = hash64(hashCode);
        int a = (int) (h >>> 32);
        int b = (int) h;
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
        return 1L << (index & 63);
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
