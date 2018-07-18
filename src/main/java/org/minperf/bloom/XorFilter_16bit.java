package org.minperf.bloom;

import org.minperf.hash.Mix;

/**
 * The Xor Filter, a new algorithm that can replace a bloom filter.
 *
 * It needs 1.23 log(1/fpp) bits per key. It is related to the BDZ algorithm [1]
 * (a minimal perfect hash function algorithm).
 *
 * [1] paper: Simple and Space-Efficient Minimal Perfect Hash Functions -
 * http://cmph.sourceforge.net/papers/wads07.pdf
 */
public class XorFilter_16bit implements Filter {

    private static final int BITS_PER_FINGERPRINT = 16;

    private static final boolean SHOW_ZERO_RATE = false;

    // TODO how to construct from a larger, mutable data structure
    // (GolombCompressedSet, Cuckoo filter,...)?
    // how many additional bits are needed to support merging?
    // Multi-layered design as described in "Don’t Thrash: How to Cache Your Hash on Flash"?

    // TODO could multiple entries for a key be in the same cache line (64 bytes)?
    // maybe with a blocked approach?
    // the number of hashes per key (see the BDZ algorithm)

    private static final int HASHES = 3;

    // the table needs to be 1.23 times the number of keys to store
    // with 2 hashes, we would need 232 (factor 2.32) for a 50% chance,
    // 240 for 55%, 250 for a 60%, 264 for 65%, 282 for 67%, as for
    // 2 hashes, p = sqrt(1 - ((2/factor)^2));
    private static final int FACTOR_TIMES_100 = 123;

    // the number of keys in the filter
    private final int size;

    // the table (array) length, that is size * 1.23
    private final int arrayLength;

    // if the table is divided into 3 blocks (one block for each hash)
    // this allows to better compress the filter,
    // because the last block contains more zero entries than the first two
    private final int blockLength;

    // usually 0, but in case the table can't be constructed (which is very
    // unlikely), then the table is rebuilt with hash index 1, and so on.
    private int hashIndex;

    // the fingerprints (internally an array of long)
    private short[] fingerprints;

    private final int bitCount;

    /**
     * The size of the filter, in bits.
     *
     * @return the size
     */
    public long getBitCount() {
        return bitCount;
    }

    /**
     * Calculate the table (array) length. This is 1.23 times the size.
     *
     * @param size the number of entries
     * @return the table length
     */
    private static int getArrayLength(int size) {
        return (int) (HASHES + (long) FACTOR_TIMES_100 * size / 100);
    }

    public static XorFilter_16bit construct(long[] keys) {
        return new XorFilter_16bit(keys);
    }

    public XorFilter_16bit(int size, int hashIndex, short[] fingerprints) {
        this.size = size;
        this.arrayLength = getArrayLength(size);
        bitCount = arrayLength * BITS_PER_FINGERPRINT;
        this.blockLength = arrayLength / HASHES;
        this.fingerprints = fingerprints;
    }

    /**
     * Construct the filter. This is basically the BDZ algorithm. The algorithm
     * itself is basically the same as BDZ, except that xor is used to store the
     * fingerprints.
     *
     * We use cuckoo hashing, so that each key is stored in one entry in the
     * hash table. We use 3 hash functions: h0, h1, h2. But we don't want to use
     * any additional bits per entry to calculate which of the entries in the
     * table contains the key. For this, we ensure that the fingerprint of each
     * key can be calculated as table[h0(key)] xor table[h1(key)] xor
     * table[h2(key)]. If we insert the entries in the right order, this is
     * possible, as one the 3 possible entries for the key can be set as we
     * like. So we first need to find the right order to insert the keys. Once
     * we have that, we can insert the data.
     *
     * @param keys the list of entries (keys)
     * @param bitsPerFingerprint the fingerprint size in bits
     */
    public XorFilter_16bit(long[] keys) {
        this.size = keys.length;
        arrayLength = getArrayLength(size);
        bitCount = arrayLength * BITS_PER_FINGERPRINT;
        blockLength = arrayLength / HASHES;
        int m = arrayLength;

        // the order in which the fingerprints are inserted, where
        // reverseOrder[0] is the last key to insert,
        // reverseOrder[1] the second to last
        long[] reverseOrder = new long[size];
        // when inserting fingerprints, whether to set fp[h0], fp[h1] or fp[h2]
        byte[] reverseH = new byte[size];
        // current index in the reverseOrder list
        int reverseOrderPos;

        // == mapping step ==
        // hashIndex is usually 0; only if we detect a cycle
        // (which is extremely unlikely) we would have to use a larger hashIndex
        int hashIndex = 0;
        while (true) {
            // we use an second table t2 to keep the list of all keys that map
            // to a given entry (with a broken hash function, all keys could map
            // to entry zero).
            // t2count: the number of keys in a given location
            byte[] t2count = new byte[m];
            // t2 is the table - but we don't store each key, only the xor of
            // keys this is possible as when removing a key, we simply xor
            // again, and once only one is remaining, we know which one it was
            long[] t2 = new long[m];
            // now we loop over all keys and insert them into the t2 table
            for(long k : keys) {
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(k, hashIndex, hi);
                    t2[h] ^= k;
                    if (t2count[h] > 120) {
                        // probably something wrong with the hash function
                        throw new IllegalArgumentException();
                    }
                    t2count[h]++;
                }
            }
            // == generate the queue ==
            // the list of indexes in the table that are "alone", that is,
            // only have one key pointing to them - those are the simple cases
            int[] alone = new int[arrayLength];
            int alonePos = 0;
            // for each entry that is alone,
            // we remove it from t2, and add it to the reverseOrder list
            reverseOrderPos = 0;
            // nextAloneCheck loops over all entries, to find an entry that is alone
            // once we found one, we remove it, and while removing it, we check
            // if this resulted in yet another entry that is alone -
            // the BDZ algorithm loops over _all_ entries in the beginning,
            // but this results in adding more entries to the alone list multiple times
            for(int nextAloneCheck = 0; nextAloneCheck < arrayLength;) {
                while (nextAloneCheck < arrayLength) {
                    if (t2count[nextAloneCheck] == 1) {
                        alone[alonePos++] = nextAloneCheck;
                        // break;
                    }
                    nextAloneCheck++;
                }
                while (alonePos > 0) {
                    int i = alone[--alonePos];
                    if (t2count[i] == 0) {
                        continue;
                    }
                    long k = t2[i];
                    // which index (0, 1, 2) the entry was found
                    byte found = -1;
                    for (int hi = 0; hi < HASHES; hi++) {
                        int h = getHash(k, hashIndex, hi);
                        int newCount = --t2count[h];
                        if (newCount == 0) {
                            found = (byte) hi;
                        } else {
                            if (newCount == 1) {
                            // if (newCount == 1 && h < nextAloneCheck) {
                                // we found a key that is _now_ alone
                                alone[alonePos++] = h;
                            }
                            // remove this key from the t2 table, using xor
                            t2[h] ^= k;
                        }
                    }
                    reverseOrder[reverseOrderPos] = k;
                    reverseH[reverseOrderPos] = found;
                    reverseOrderPos++;
                }
            }
            // this means there was no cycle
            if (reverseOrderPos == size) {
                break;
            }
            hashIndex++;
        }
        this.hashIndex = hashIndex;
        // == assignment step ==
        // fingerprints (array, then converted to a bit buffer)
        short[] fp = new short[m];
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            // the key we insert next
            long k = reverseOrder[i];
            int found = reverseH[i];
            // which entry in the table we can change
            int change = -1;
            // we set table[change] to the fingerprint of the key,
            // unless the other two entries are already occupied
            long hash = Mix.hash64(k + hashIndex);
            int xor = fingerprint(hash);
            for (int hi = 0; hi < HASHES; hi++) {
                int h = getHash(k, hashIndex, hi);
                if (found == hi) {
                    change = h;
                } else {
                    // this is different from BDZ: using xor to calculate the
                    // fingerprint
                    xor ^= fp[h];
                }
            }
            fp[change] = (short) xor;
        }
        if (SHOW_ZERO_RATE) {
            int zeros = 0;
            for (int f : fp) {
                if (f == 0) {
                    zeros++;
                }
            }
            int zeros0 = 0;
            int zeros1 = 0;
            int zeros2 = 0;
            for (int i = 0; i < blockLength; i++) {
                if (fp[i] == 0)
                    zeros0++;
                if (fp[blockLength + i] == 0)
                    zeros1++;
                if (fp[2 * blockLength + i] == 0)
                    zeros2++;
            }
            if (Math.abs(zeros0 + zeros1 + zeros2 - zeros) > 2) {
                System.out.println("incorrect " + (zeros0 + zeros1 + zeros2) + " / " + zeros);
            }
            System.out.println("zeros block 0 " + 100. * zeros0 / blockLength +
                    " block 1 " + 100. * zeros1 / blockLength +
                    " block 2 " + 100. * zeros2 / blockLength +
                    " total " + (100. / fp.length * zeros) + "%");
        }

        fingerprints = new short[m];
        for(int i=0; i<fp.length; i++) {
            fingerprints[i] = (short) fp[i];
        }
    }

    @Override
    public int getConstructionLoopCount() {
        return 1 + hashIndex;
    }

    /**
     * Whether the filter _may_ contain a key.
     *
     * @param key the key to test
     * @return true if the key may be in the filter
     */
    @Override
    public boolean mayContain(long key) {
        long hash = Mix.hash64(key + hashIndex);
        int f = fingerprint(hash);
        int r0 = (int) hash;
        int r1 = (int) (hash >>> 16);
        int r2 = (int) (hash >>> 32);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        f ^= fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2];
        return (f & 0xffff) == 0;
    }

    /**
     * Batch version of whether the filter _may_ contain a key.
     *
     * @param keys  the keys to test
     * @param startpos starting point in keys
     * @param length how many data points to process
     * @param accumulator should have at least "length" capacity
     * @param buffer should have at least 4*length capacity
     * @return number of matching keys
     */
    public int mayContainBatch(long[] keys, int startpos, int length, long[] accumulator, int[] buffer) {
      for(int k = 0; k < length; k++) {
        long hash = Mix.hash64(keys[k] + hashIndex);
        int f = fingerprint(hash);
        int r0 = (int) hash;
        int r1 = (int) (hash >>> 16);
        int r2 = (int) (hash >>> 32);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        buffer[4 * k] = h0;
        buffer[4 * k + 1] = h1;
        buffer[4 * k + 2] = h2;
        buffer[4 * k + 3] = f;
      }
      for(int k = 0; k < length; k++) {
        accumulator[k] = (buffer[4 * k + 3] ^ fingerprints[buffer[4 * k]] ^ fingerprints[buffer[4 * k + 1]] ^ fingerprints[buffer[4 * k + 2]]);
      }
      int pos = 0;
      for(int k = 0; k < length; k++) {
        long val = accumulator[k] & 0xFFFF;
        accumulator[pos] = keys[k];
        if(val != 0) pos++;
      }
      return pos;
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
        long hash = Mix.hash64(key + hashIndex);
        int r;
        switch(index) {
        case 0:
            r = (int) (hash);
            break;
        case 1:
            r = (int) (hash >>> 16);
            break;
        default:
            r = (int) (hash >>> 32);
            break;
        }

        // this would be slightly faster, but means we only have one range
        // also, there is a small risk that for the same key and different index,
        // the same value is returned
        // r = reduce((int) r, arrayLength);

        // use one distinct block of entries for each hash index
        r = reduce((int) r, blockLength);
        r = r + index * blockLength;

        return (int) r;
    }

    /**
     * Calculate the fingerprint.
     *
     * @param hash the hash of the key
     * @return the fingerprint
     */
    private int fingerprint(long hash) {
        return (int) (hash & ((1 << BITS_PER_FINGERPRINT) - 1));
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

}
