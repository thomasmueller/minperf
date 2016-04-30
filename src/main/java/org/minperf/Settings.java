package org.minperf;


/**
 * The settings used to generate the hash function.
 */
public class Settings {

    /**
     * Some space could be saved by making the bucket header more complex. One
     * way is to adjust the dataBits by the start offset, before calculating the
     * start offset. This saves about 1 bit per bucket. Another option is to use
     * multiple levels for the bucket header, for example level 1 bucket each
     * pointing to 100 level 2 buckets. What needs to be analyzed is whether
     * such tricks improve evaluation time for a given MPHF size, versus just
     * increasing the bucket size, which also saves space.
     *
     * Another approach, which doesn't complicate the header, is to add skip
     * offsets in some levels of the bucket itself. This would allow using
     * larger buckets, without affecting evaluation cost much. And this data
     * could be variable size, which probably needs less space.
     */
    public static final boolean COMPLEX_BUCKET_HEADER = false;

    /**
     * Could be increased to reduce the number of universal hash function calls,
     * which also speeds up evaluation time.
     */
    private static final int SUPPLEMENTAL_HASH_SHIFT = 18;

    /**
     * The number of times the same universal hash is mixed using the
     * supplemental hash function. Must be a power of 2.
     */
    private static final long SUPPLEMENTAL_HASH_CALLS = 1 << SUPPLEMENTAL_HASH_SHIFT;

    /**
     * The estimated space used for leaves of size = array index, in bits for
     * 1000 entries.
     */
    private static final int[] ESTIMATED_SPACE = { 0, 3571, 2574, 2322, 2124,
            1920, 1848, 1785, 1717, 1692, 1635, 1620, 1608, 1587, 1581, 1564,
            1554, 1552, 1543, 1534, 1528, 1524, 1522, 1517, 1517, 1495 };

    /**
     * The Rice parameter k to use for leaves of size = array index.
     */
    private static final int[] RICE_LEAF = { 0, 0, 0, 1, 3, 4, 5, 7, 8, 10,
            11, 12, 14, 15, 16, 18, 19, 21, 22, 23, 25, 26, 28, 29, 30, 32 };

    /**
     * How to split medium sized sets. There are 4 values in each tuple:
     * the leaf size, size, split, Rice parameter k.
     */
    private static final int[][] RICE_SPLIT_MORE = {
        // 0 .. 6
        { }, { }, { }, { }, { }, { 4}, { 4},
        // 7 .. 13
        { 4}, { 7}, { 7}, { 10, 7}, { 11, 7}, { 11, 7}, { 14, 8},
        // 14 .. 18
        { 14, 8}, { 15, 8}, { 18, 8}, { 18, 8}, { 19, 8},
        // 19 .. 23
        { 22, 13}, { 22, 13}, { 23, 14}, { 26, 14}, { 27, 14},
        // 24 .. 25
        { 27, 14}, { 31, 20, 12}};

    /**
     * When splitting a set evenly into two subsets, the minimum size of the
     * set where k = array index should be used for the Rice parameter k.
     */
    private static final int[] RICE_SPLIT_2 = { 0, 4, 14, 50, 188, 726, 2858,
            11346, 45214, 180512 };

    private static final int CACHE_SPLITS = 256;

    private final int leafSize;
    private final int loadFactor;

    private final int[] splits = new int[CACHE_SPLITS];
    private final int[] rice = new int[CACHE_SPLITS];

    /**
     * @param leafSize
     * @param loadFactor the load factor, at most 65536
     */
    Settings(int leafSize, int loadFactor) {
        if (leafSize < 1 || leafSize > 25) {
            throw new IllegalArgumentException("leafSize out of range: " + leafSize);
        }
        if (loadFactor < 2 || loadFactor > 65536) {
            throw new IllegalArgumentException("loadFactor out of range: " + loadFactor);
        }
        this.leafSize = leafSize;
        this.loadFactor = loadFactor;
        for (int i = 0; i < CACHE_SPLITS; i++) {
            splits[i] = calcSplit(i, leafSize);
            rice[i] = calcGolombRiceShift(i, leafSize);
        }
    }

    private static int calcRiceParamSplitByTwo(int size) {
        // this will throw an exception for sizes >= 180172
        for (int i = 0;; i++) {
            if (RICE_SPLIT_2[i] > size) {
                return i - 1;
            }
        }
    }

    static int calcNextSplit(int factor) {
        return Math.max(2,  (int) (1.5 + factor * .35));
    }

    private static int calcSplit(int size, int leafSize) {
        if (size <= leafSize * 2) {
            return -(size - size / 2);
        }
        for (int x = leafSize, f = x;;) {
            if (size < x) {
                return -(x / f);
            } else if (size == x) {
                return f;
            }
            f = calcNextSplit(f);
            x *= f;
        }
    }

    public long getEstimatedBits(long size) {
        return ESTIMATED_SPACE[leafSize] * size / 1000;
    }

    public int getSplit(int size) {
        if (size < CACHE_SPLITS) {
            return splits[size];
        }
        return calcSplit(size, leafSize);
    }

    private static int calcGolombRiceShift(int size, int leafSize) {
        if (size <= leafSize) {
            return RICE_LEAF[size];
        }
        int index = 0;
        for (int x = leafSize, f = x;;) {
            f = Settings.calcNextSplit(f);
            if (f <= 2) {
                break;
            }
            x *= f;
            if (size < x) {
                break;
            } else if (size == x) {
                return RICE_SPLIT_MORE[leafSize][index];
            }
            index++;
        }
        return calcRiceParamSplitByTwo(size);
    }

    public int getGolombRiceShift(int size) {
        if (size < CACHE_SPLITS) {
            return rice[size];
        }
        return calcGolombRiceShift(size, leafSize);
    }

    public static boolean needNewUniversalHashIndex(long index) {
        return (index & (SUPPLEMENTAL_HASH_CALLS - 1)) == 0;
    }

    public static long getUniversalHashIndex(long index) {
        return index >>> SUPPLEMENTAL_HASH_SHIFT;
    }

    public int getLeafSize() {
        return leafSize;
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public static int scale(long x, int size) {
        // this is actually not completely uniform,
        // there is a small bias towards smaller numbers
        // possible speedup for the 2^n case:
        // return x & (size - 1);
        // division would also be faster
        return (int) ((x & (-1L >>> 1)) % size);
    }

    private static int scale(int x, int size) {
        // this is actually not completely uniform,
        // there is a small bias towards smaller numbers
        // possible speedup for the 2^n case:
        // return x & (size - 1);
        // division would also be faster
        return (x & (-1 >>> 1)) % size;
    }

    public static int supplementalHash(long hash, long index, int size) {
        // it would be better to use long,
        // but not sure what the best constant would be then
        int x = (int) (Long.rotateLeft(hash, (int) index) + index);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return scale(x, size);
    }

}