package org.minperf.bloom;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.hash.Mix;
import org.minperf.hem.RandomGenerator;

/**
 * The Cuckoo plus filter, a new algorithm that can replace a bloom filter.
 *
 * Unlike the regular Cuckoo filter [1][2], it needs 1.23 log(1/fpp) bits per
 * key. It basically combines a Cuckoo filter with BDZ [3][4] (a minimal perfect
 * hash function algorithm). See also https://brilliant.org/wiki/cuckoo-filter/.
 *
 * [1] paper: Cuckoo Filter: Practically Better Than Bloom
 * [2] http://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf
 * [3] paper: Simple and Space-Efficient Minimal Perfect Hash Functions
 * [4] http://cmph.sourceforge.net/papers/wads07.pdf
 */
public class CuckooPlusFilter {

    public static void main(String... args) {
        for(int bitsPerKey = 4; bitsPerKey < 20; bitsPerKey++) {
            test(bitsPerKey);
        }
    }

    public static void test(int bitsPerKey) {
        int len = 4 * 1024 * 1024;
        int testCount = 1;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long time = System.nanoTime();
        CuckooPlusFilter f = new CuckooPlusFilter(list, len, bitsPerKey);
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
        System.out.println("CuckooPlus false positives: " + falsePositiveRate +
                "% " + (double) f.getBitCount() / len + " bits/key " +
                "add: " + addTime + " get: " + getTime + " ns/key");

    }

    private static final int HASHES = 3;
    private static final int FACTOR_TIMES_100 = 123;

    private final int size;
    private final int arrayLength;
    private final int blockLength;
    private final int bitsPerKey;
    private int hashIndex;
    private BitBuffer fingerprints;

    private double getBitCount() {
        return fingerprints.position();
    }

    private static int getArrayLength(int size) {
        return HASHES + FACTOR_TIMES_100 * size / 100;
    }

    CuckooPlusFilter(long[] list, int entryCount, int bitsPerKey) {
        this.size = entryCount;
        this.bitsPerKey = bitsPerKey;
        arrayLength = getArrayLength(size);
        blockLength = arrayLength / HASHES;
        int m = arrayLength;
        long[] order = new long[size];
        int orderPos;
        long[] at;
        int hashIndex = 0;
        while (true) {
            orderPos = 0;
            at = new long[m];
            long[] l2 = new long[m];
            int[] l2c = new int[m];
            for (int i = 0; i < size; i++) {
                long x = list[i];
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hashIndex, hi);
                    l2[h] ^= x;
                    l2c[h]++;
                }
            }
            int[] alone = new int[arrayLength];
            int alonePos = 0;
            for (int i = 0; i < arrayLength; i++) {
                if (l2c[i] == 1) {
                    alone[alonePos++] = i;
                }
            }
            while (alonePos > 0) {
                int i = alone[--alonePos];
                if (l2c[i] == 0) {
                    continue;
                }
                long x = l2[i];
                order[orderPos++] = x;
                boolean found = false;
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hashIndex, hi);
                    l2[h] ^= x;
                    l2c[h]--;
                    if (l2c[h] == 0) {
                        if (!found) {
                            at[h] = x;
                            found = true;
                        }
                    } else if (l2c[h] == 1) {
                        alone[alonePos++] = h;
                    }
                }
            }
            if (orderPos == size) {
                break;
            }
            hashIndex++;
        }
        this.hashIndex = hashIndex;
        BitSet visited = new BitSet();
        long[] fp = new long[m];
        for (int i = orderPos - 1; i >= 0; i--) {
            long x = order[i];
            long sum = fingerprint(x);
            int change = 0;
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(x, hashIndex, hi);
                if (visited.get(h)) {
                    sum ^= fp[h];
                } else {
                    visited.set(h);
                    if (at[h] == x) {
                        change = h;
                    }
                }
            }
            fp[change] = sum;
        }
        fingerprints = new BitBuffer(bitsPerKey * m);
        for(long f : fp) {
            fingerprints.writeNumber(f, bitsPerKey);
        }
    }

    public boolean mayContain(long key) {
        long x = fingerprint(key);
        for (int hi = 0; hi < HASHES; hi++) {
            int h = getHash(key, hashIndex, hi);
            x ^= fingerprints.readNumber(h * bitsPerKey, bitsPerKey);
        }
        return x == 0;
    }

    private int getHash(long x, int hashIndex, int index) {
        long r = supplementalHash(x, hashIndex + index);
        r = reduce((int) r, arrayLength);
        // r = reduce((int) r, blockLength);
        // r = r + index * blockLength;
        return (int) r;
    }

    private long fingerprint(long key) {
        return hash64(key) & ((1L << bitsPerKey) - 1);
    }

    private static int supplementalHash(long x, int index) {
        return Mix.supplementalHashWeyl(x, index);
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
