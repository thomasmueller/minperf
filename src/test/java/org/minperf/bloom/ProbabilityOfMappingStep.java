package org.minperf.bloom;

import java.util.Random;

import org.minperf.hem.RandomGenerator;

public class ProbabilityOfMappingStep {

    private static final int HASHES = 3;

    public static void main(String... args) {
        int seed = 0;
        for (int size = 1; size < 10000000; size *= 10) {
            long[] list = new long[size];
            for (int add = 2;; add *= 2) {
                int totalRetries = 0;
                for (int test = 0; test < 1000; test++) {
                    RandomGenerator.createRandomUniqueListFast(list, seed);
                    seed += list.length;
                    int arrayLength = (int) ((size * 1.23) + add) / 3 * 3;
                    int retryCount = getRetryCount(list, arrayLength);
                    totalRetries += retryCount;
                }
                // System.out.println("    size " + size + " add " + add + " totalRetries " + totalRetries);
                if (totalRetries == 0) {
                    System.out.println("size " + size + " add " + add);
                    break;
                }
            }
        }
    }

    public static int getRetryCount(long[] keys, int arrayLength) {
        int size = keys.length;
        int blockLength = arrayLength / HASHES;
        int m = arrayLength;
        int retryCount = 0;
        Random random = new Random(1);
        while (true) {
            if (retryCount > 1000) {
                break;
            }
            int seed = random.nextInt();
            byte[] t2count = new byte[m];
            long[] t2 = new long[m];
            for (long k : keys) {
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(k, seed, hi, blockLength);
                    t2[h] ^= k;
                    if (t2count[h] > 120) {
                        // probably something wrong with the hash function
                        throw new IllegalArgumentException();
                    }
                    t2count[h]++;
                }
            }
            int reverseOrderPos = 0;
            int[][] alone = new int[HASHES][blockLength];
            int[] alonePos = new int[HASHES];
            for (int nextAlone = 0; nextAlone < HASHES; nextAlone++) {
                for (int i = 0; i < blockLength; i++) {
                    if (t2count[nextAlone * blockLength + i] == 1) {
                        alone[nextAlone][alonePos[nextAlone]++] = nextAlone * blockLength + i;
                    }
                }
            }
            int found = -1;
            while (true) {
                int i = -1;
                for (int hi = 0; hi < HASHES; hi++) {
                    if (alonePos[hi] > 0) {
                        i = alone[hi][--alonePos[hi]];
                        found = hi;
                        break;
                    }
                }
                if (i == -1) {
                    // no entry found
                    break;
                }
                if (t2count[i] <= 0) {
                    continue;
                }
                long k = t2[i];
                if (t2count[i] != 1) {
                    throw new AssertionError();
                }
                --t2count[i];
                // which index (0, 1, 2) the entry was found
                for (int hi = 0; hi < HASHES; hi++) {
                    if (hi != found) {
                        int h = getHash(k, seed, hi, blockLength);
                        int newCount = --t2count[h];
                        if (newCount == 1) {
                            // we found a key that is _now_ alone
                            alone[hi][alonePos[hi]++] = h;
                        }
                        // remove this key from the t2 table, using xor
                        t2[h] ^= k;
                    }
                }
                reverseOrderPos++;
            }
            // this means there was no cycle
            if (reverseOrderPos == size) {
                break;
            }
            retryCount++;
        }
        return retryCount;
    }

    private static int getHash(long key, long seed, int index, int blockLength) {
        long hash = hash(key, seed);
        int r;
        switch (index) {
        case 0:
            r = (int) (hash);
            break;
        case 1:
            r = (int) Long.rotateLeft(hash, 21);
            break;
        default:
            r = (int) Long.rotateLeft(hash, 42);
            break;
        }
        r = reduce((int) r, blockLength);
        r = r + index * blockLength;
        return (int) r;
    }

    static long hash(long key, long seed) {
        long h = key + seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

}
