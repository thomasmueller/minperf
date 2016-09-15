package org.minperf.cuckoo;

import java.util.Set;

/**
 * A cuckoo hash set for long entries.
 */
public class CuckooLongKeyHashSet {

    private final int size;
    private int hashIndex;
    private int maxLoop;
    private long[] entries;
    private int blockLength;

    public CuckooLongKeyHashSet(Set<Long> set) {
        size = set.size();
        double e = 0.05;
        this.blockLength = (int) (size * (1 + e) + 1);
        maxLoop = (int) ((3 * Math.log(blockLength) / Math.log(1 + e)) + 1);
        for (hashIndex = 0;; hashIndex += 2) {
            entries = new long[blockLength * 2];
            if (tryAddAll(set)) {
                return;
            }
        }
    }

    public int index(long key, int id) {
        int offset;
        int x;
        if (id == 0) {
            offset = 0;
            x = hashIndex;
        } else {
            offset = blockLength;
            x = hashIndex + 1;
        }
        return offset + supplementalHash(key, x, blockLength);
    }

    public boolean contains(long key) {
        if (entries[index(key, 0)] == key) {
            return true;
        }
        if (entries[index(key, 1)] == key) {
            return true;
        }
        return false;
    }

    public int index(long key) {
        for (int i = 0; i < 2; i++) {
            int h = index(key, i);
            long k = entries[h];
            if (k == key) {
                return h;
            }
        }
        return -1;
    }

    public int arrayLength() {
        return entries.length;
    }

    private boolean tryAddAll(Set<Long> set) {
        for (long x : set) {
            if (!tryAdd(x)) {
                return false;
            }
        }
        return true;
    }

    private boolean tryAdd(long x) {
        for (int count = 0; count < 2 * maxLoop; count++) {
            int h = index(x, count & 1);
            long old = entries[h];
            if (old == x) {
                return true;
            }
            entries[h] = x;
            if (old == 0) {
                return true;
            }
            x = old;
        }
        return false;
    }

    public static int supplementalHash(long hash, long index, int size) {
        // it would be better to use long,
        // but with some processors, 32-bit multiplication
        // seem to be much faster
        // (about 1200 ms for 32 bit, about 2000 ms for 64 bit)
        int x = (int) (Long.rotateLeft(hash, (int) index) ^ index);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return scaleInt(x, size);
    }

    private static int scaleInt(int x, int size) {
        // this is actually not completely uniform,
        // there is a small bias towards smaller numbers
        // possible speedup for the 2^n case:
        // return x & (size - 1);
        // division would also be faster
        // return x & (size - 1);
        return (x & (-1 >>> 1)) % size;
    }

}
