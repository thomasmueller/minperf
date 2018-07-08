package org.minperf.bloom;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.hash.Mix;
import org.minperf.hem.RandomGenerator;

/**
 * The Xor Filter, a new algorithm that can replace a bloom filter.
 *
 * It needs 1.23 log(1/fpp) bits per key. It is related to the BDZ algorithm [1]
 * (a minimal perfect hash function algorithm).
 *
 * [1] paper: Simple and Space-Efficient Minimal Perfect Hash Functions -
 * http://cmph.sourceforge.net/papers/wads07.pdf
 */
public class CuckooPlusFilter {

    /**
     * Tests the filter with fingerprint length 4..19 bits.
     */
    public static void main(String... args) {
        for(int bitsPerFingerprint = 4; bitsPerFingerprint < 20; bitsPerFingerprint++) {
            test(bitsPerFingerprint);
        }
    }

    /**
     * Tests the filter.
     *
     * @param bitsPerFingerprint the number of bits for each fingerprint.
     */
    public static void test(int bitsPerFingerprint) {
        // the number of entries
        int len = 4 * 1024 * 1024;
        // the number of tests to run
        int testCount = 1;
        // the list of entries: the first half is keys in the filter,
        // the second half is _not_ in the filter, but used to calculate false
        // positives
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        // the keys
        long[] keys = new long[len];
        // the list of non-keys, used to calculate false positives
        long[] nonKeys = new long[len];
        for(int i = 0; i<len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }

        // construct the filter with the first half of the list
        long time = System.nanoTime();
        CuckooPlusFilter f = new CuckooPlusFilter(keys, bitsPerFingerprint);
        long addTime = (System.nanoTime() - time) / len;

        // test the filter, that is: lookups
        time = System.nanoTime();
        int falsePositives = 0;
        for (int test = 0; test < testCount; test++) {
            // each key (the first half) needs to be found
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(keys[i])) {
                    throw new AssertionError();
                }
            }
            // non keys _may_ be found - this is used to calculate false
            // positives
            for (int i = 0; i < len; i++) {
                if (f.mayContain(nonKeys[i])) {
                    falsePositives++;
                }
            }
        }
        long getTime = (System.nanoTime() - time) / len / testCount;

        // print results (timing data, false positive rate)
        double falsePositiveRate = (100. / testCount / len * falsePositives);
        System.out.println("CuckooPlus false positives: " + falsePositiveRate +
                "% " + (double) f.getBitCount() / len + " bits/key " +
                "add: " + addTime + " get: " + getTime + " ns/key");

    }

    // the number of hashes per key (see the BDZ algorithm)
    private static final int HASHES = 3;

    // the table needs to be 1.23 times the number of entries
    private static final int FACTOR_TIMES_100 = 123;

    // the number of entries in the filter
    private final int size;

    // the table (array) length, that is size * 1.23
    private final int arrayLength;

    // if the table is divided into 3 blocks (one block for each hash)
    // this would allow to better compress the filter
    private final int blockLength;

    // the fingerprint size
    private final int bitsPerFingerprint;

    // usually 0, but in case the table can't be constructed (which is very
    // unlikely), then the table is rebuilt with hash index 1, and so on.
    private int hashIndex;

    // the fingerprints (internally an array of long)
    private BitBuffer fingerprints;

    /**
     * The size of the filter, in bits.
     *
     * @return the size
     */
    private double getBitCount() {
        return fingerprints.position();
    }

    /**
     * Calculate the table (array) length. This is 1.23 times the size.
     *
     * @param size the number of entries
     * @return the table length
     */
    private static int getArrayLength(int size) {
        return HASHES + FACTOR_TIMES_100 * size / 100;
    }

    /**
     * Construct the filter. This is basically the BDZ algorithm. The
     * implementation is overly complicated I think. The algorithm itself is
     * basically the same as BDZ, except that xor is used to store the
     * fingerprints.
     *
     * @param keys the list of entries (keys)
     * @param bitsPerFingerprint the fingerprint size in bits
     */
    CuckooPlusFilter(long[] keys, int bitsPerFingerprint) {
        this.size = keys.length;
        this.bitsPerFingerprint = bitsPerFingerprint;
        arrayLength = getArrayLength(size);
        blockLength = arrayLength / HASHES;
        int m = arrayLength;
        // the order in which the fingerprints are calculated
        // keys[reverseOrder[0]] is the last entry to insert,
        // keys[reverseOrder[1]] the second to last
        long[] reverseOrder = new long[size];
        // current index in the reverseOrder list
        int reverseOrderPos;
        long[] at;
        int hashIndex = 0;
        while (true) {
            reverseOrderPos = 0;
            at = new long[m];
            long[] l2 = new long[m];
            int[] l2c = new int[m];
            for (int i = 0; i < size; i++) {
                long x = keys[i];
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
                reverseOrder[reverseOrderPos++] = x;
                boolean found = false;
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(x, hashIndex, hi);
                    // this is yet another xor trick
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
            if (reverseOrderPos == size) {
                break;
            }
            hashIndex++;
        }
        this.hashIndex = hashIndex;
        BitSet visited = new BitSet();
        // fingerprints (array, then converted to a bit buffer)
        long[] fp = new long[m];
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            long x = reverseOrder[i];
            long xor = fingerprint(x);
            int change = 0;
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(x, hashIndex, hi);
                if (visited.get(h)) {
                    // this is different from BDZ: using xor to calculate the
                    // fingerprint
                    xor ^= fp[h];
                } else {
                    visited.set(h);
                    if (at[h] == x) {
                        change = h;
                    }
                }
            }
            fp[change] = xor;
        }
        fingerprints = new BitBuffer(bitsPerFingerprint * m);
        for(long f : fp) {
            fingerprints.writeNumber(f, bitsPerFingerprint);
        }
    }

    /**
     * Whether the filter _may_ contain a key.
     *
     * @param key the key to test
     * @return true if the key may be in the filter
     */
    public boolean mayContain(long key) {
        long x = fingerprint(key);
        for (int hi = 0; hi < HASHES; hi++) {
            int h = getHash(key, hashIndex, hi);
            x ^= fingerprints.readNumber(h * bitsPerFingerprint, bitsPerFingerprint);
        }
        return x == 0;
    }

    /**
     * Calculate the hash for a key.
     *
     * @param key the key
     * @param hashIndex the hash index (almost always 0)
     * @param index the index (0..2)
     * @return the hash (0..arrayLength)
     */
    private int getHash(long key, int hashIndex, int index) {
        long r = supplementalHash(key, hashIndex + index);
        r = reduce((int) r, arrayLength);
        // the following would use one distinct block of entries for each hash
        // index:
        // r = reduce((int) r, blockLength);
        // r = r + index * blockLength;
        return (int) r;
    }

    /**
     * Calculate the fingerprint.
     *
     * @param key the key
     * @return the fingerprint
     */
    private long fingerprint(long key) {
        return hash64(key) & ((1L << bitsPerFingerprint) - 1);
    }

    /**
     * Calculate a supplemental hash. Kind of like a universal hash.
     *
     * @param key the key
     * @param index the index (0..2)
     * @return the hash
     */
    private static int supplementalHash(long key, int index) {
        return Mix.supplementalHashWeyl(key, index);
    }

    /**
     * Shrink the hash to a value 0..n. Kind of like modulo, but using
     * multiplication.
     *
     * @param hash the hash
     * @param n the maximum of the result
     * @return the reduced value
     */
    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

    /**
     * Hash the key.
     *
     * @param key the key
     * @return the hash
     */
    private static long hash64(long key) {
        key = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        key = (key ^ (key >>> 27)) * 0x94d049bb133111ebL;
        key = key ^ (key >>> 31);
        return key;
    }

}
