package org.minperf.bloom;

import java.util.BitSet;

import org.minperf.BitBuffer;
import org.minperf.bloom.utils.BinaryArithmeticBuffer;
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
public class XorFilter implements Filter {

    private static final boolean SHOW_ZERO_RATE = false;

    // TODO how to construct from a larger, mutable data structure
    // (GolombCompressedSet, Cuckoo filter,...)?
    // how many additional bits are needed to support merging?
    // Multi-layered design as described in "Don’t Thrash: How to Cache Your Hash on Flash"?

    // TODO could multiple entries for a key be in the same cache line (64 bytes)?
    // maybe with a blocked approach?
    // the number of hashes per key (see the BDZ algorithm)

    // TODO the xor filter can be initialized with random data if this is needed
    // 91.3% of the entries are hit by keys
    // 81.3% of the entries are set (so 10% are never hit, and 10% can be any value)

    // in 20% of the cases, fp[h1] ^ fp[h2] == fingerprint,
    // so we could check this and don't have to read fp[h0],
    // at the expense of 80% higher fpp

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

    // the fingerprint size in bits
    private final int bitsPerFingerprint;

    // usually 0, but in case the table can't be constructed (which is very
    // unlikely), then the table is rebuilt with hash index 1, and so on.
    private int hashIndex;

    // the fingerprints (internally an array of long)
    private BitBuffer fingerprints;

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

    public static XorFilter construct(long[] keys, int bitsPerKey) {
        return new XorFilter(keys, bitsPerKey);
    }

    public XorFilter(int size, int bitsPerFingerprint, int hashIndex, BitBuffer fingerprints) {
        this.size = size;
        this.bitsPerFingerprint = bitsPerFingerprint;
        this.arrayLength = getArrayLength(size);
        bitCount = arrayLength * bitsPerFingerprint;
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
    public XorFilter(long[] keys, int bitsPerFingerprint) {
        this.size = keys.length;
        this.bitsPerFingerprint = bitsPerFingerprint;
        arrayLength = getArrayLength(size);
        bitCount = arrayLength * bitsPerFingerprint;
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
        int[] fp = new int[m];
        for (int i = reverseOrderPos - 1; i >= 0; i--) {
            // the key we insert next
            long k = reverseOrder[i];
            int found = reverseH[i];
            // which entry in the table we can change
            int change = -1;
            // we set table[change] to the fingerprint of the key,
            // unless the other two entries are already occupied
            int xor = fingerprint(k);
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
            fp[change] = xor;
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

        fingerprints = new BitBuffer((long)bitsPerFingerprint * m);
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
    @Override
    public boolean mayContain(long key) {
        int f = fingerprint(key);
        key = Mix.hash64(key + hashIndex);
        int r0 = (int) (key >>> 32);
        int r1 = (int) (key);
        int r2 = (int) ((key >>> 32) ^ key);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        // todo: these calls to fingerprints.readNumber are not reasonable in a high
        // performance setting. Need to write special cases for various bitsPerFingerprint values
        // such as 8 and 16.
        // must cast to long to avoid overflow.
        f ^= fingerprints.readNumber((long)h0 * bitsPerFingerprint, bitsPerFingerprint);
        f ^= fingerprints.readNumber((long)h1 * bitsPerFingerprint, bitsPerFingerprint);
        f ^= fingerprints.readNumber((long)h2 * bitsPerFingerprint, bitsPerFingerprint);

        return f == 0;
    }

    // special case where bitsPerFingerprint == 32, could be
    // even faster, should special case all relevant bit widths
    // UNTESTED
     public boolean mayContain32(long key) {
        int f = fingerprint(key);
        key = Mix.hash64(key + hashIndex);
        int r0 = (int) (key >>> 32);
        int r1 = (int) (key);
        int r2 = (int) ((key >>> 32) ^ key);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        f ^= fingerprints.data[h0>>>1] >>> ((key & 1)<<5);
        f ^= fingerprints.data[h1>>>1] >>> ((key & 1)<<5);
        f ^= fingerprints.data[h2>>>1] >>> ((key & 1)<<5);
        return f == 0;
    }

    // special case where bitsPerFingerprint == 16, could be
    // even faster, should special case all relevant bit widths
    // UNTESTED
    public boolean mayContain16(long key) {
        int f = fingerprint(key);
        key = Mix.hash64(key + hashIndex);
        int r0 = (int) (key >>> 32);
        int r1 = (int) (key);
        int r2 = (int) ((key >>> 32) ^ key);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        f ^= fingerprints.data[h0>>>2] >>> ((key & 3)<<4);
        f ^= fingerprints.data[h1>>>2] >>> ((key & 3)<<4);
        f ^= fingerprints.data[h2>>>2] >>> ((key & 3)<<4);
        return (f & 0xFFFF) == 0;
    }

    // special case where bitsPerFingerprint == 8, could be
    // even faster, should special case all relevant bit widths
    // UNTESTED
     public boolean mayContain8(long key) {
        int f = fingerprint(key);
        key = Mix.hash64(key + hashIndex);
        int r0 = (int) (key >>> 32);
        int r1 = (int) (key);
        int r2 = (int) ((key >>> 32) ^ key);
        int h0 = reduce(r0, blockLength);
        int h1 = reduce(r1, blockLength) + blockLength;
        int h2 = reduce(r2, blockLength) + 2 * blockLength;
        f ^= fingerprints.data[h0>>>3] >>> ((key & 7)<<3);
        f ^= fingerprints.data[h1>>>3] >>> ((key & 7)<<3);
        f ^= fingerprints.data[h2>>>3] >>> ((key & 7)<<3);
        return (f & 0xFF) == 0;
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
        key = Mix.hash64(key + hashIndex);
        int r;
        switch(index) {
        case 0:
            r = (int) (key >>> 32);
            break;
        case 1:
            r = (int) (key);
            // r = (int) ((key >>> 32) + key);
            break;
        default:
            r = (int) ((key >>> 32) ^  key);
            // r = (int) ((key >>> 32) + 2 * key);
            break;
        }

        // long r = supplementalHash(key, hashIndex + index);

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
     * @param key the key
     * @return the fingerprint
     */
    private int fingerprint(long key) {
        return (int) (key & ((1 << bitsPerFingerprint) - 1));
        // return (int) hash64(key) & ((1 << bitsPerFingerprint) - 1);
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
     * Write compressed. There is an overhead of 0.8 bits per entry.
     *
     * @param target the target buffer
     */
    public void writeCompressed(BitBuffer target) {
        target.writeEliasDelta(size + 1);
        target.writeEliasDelta(bitsPerFingerprint);
        target.writeEliasDelta(hashIndex + 1);
        BinaryArithmeticBuffer.Out out = new BinaryArithmeticBuffer.Out(target);
        int count = 0;
        for (int i = 0; i < arrayLength; i++) {
            int x = (int) fingerprints.readNumber((long)i * bitsPerFingerprint, bitsPerFingerprint);
            if (x != 0) {
                count++;
            }
        }
        target.writeEliasDelta(count + 1);
        for (int i = 0; i < arrayLength; i++) {
            int x = (int) fingerprints.readNumber((long)i * bitsPerFingerprint, bitsPerFingerprint);
            if (x != 0) {
                target.writeNumber(x, bitsPerFingerprint);
            }
        }
        for (int i = 0; i < arrayLength; i++) {
            int x = (int) fingerprints.readNumber((long)i * bitsPerFingerprint, bitsPerFingerprint);
            int prob = i < blockLength ? PROB_ONE_0 : i < 2 * blockLength ? PROB_ONE_1 : PROB_ONE_2;
            if (x == 0) {
                out.writeBit(false, prob);
            } else {
                out.writeBit(true, prob);
            }
        }
        out.flush();
    }

    public static XorFilter read(BitBuffer source) {
        int size = (int) source.readEliasDelta() - 1;
        int arrayLength = getArrayLength(size);
        int blockLength = arrayLength / HASHES;
        int bitsPerFingerprint = (int) source.readEliasDelta();
        int hashIndex = (int) source.readEliasDelta() - 1;
        int count = (int) source.readEliasDelta() - 1;
        int[] fp = new int[count];
        for (int i = 0; i < count; i++) {
            fp[i] =  (int) source.readNumber(bitsPerFingerprint);
        }
        BitBuffer fingerprints = new BitBuffer(arrayLength * bitsPerFingerprint);
        BitSet set = new BitSet();
        BinaryArithmeticBuffer.In in = new BinaryArithmeticBuffer.In(source);
        for (int i = 0; i < arrayLength; i++) {
            int prob = i < blockLength ? PROB_ONE_0 : i < 2 * blockLength ? PROB_ONE_1 : PROB_ONE_2;
            if (in.readBit(prob)) {
                set.set(i);
            }
        }
        for (int i = 0, j = 0; i < arrayLength; i++) {
            if (set.get(i)) {
                long x = fp[j++];
                fingerprints.writeNumber(x, bitsPerFingerprint);
            } else {
                fingerprints.writeNumber(0, bitsPerFingerprint);
            }
        }
        return new XorFilter(size, bitsPerFingerprint, hashIndex, fingerprints);
    }

    // private final static int PROB_ONE = (int) (BinaryArithmeticBuffer.MAX_PROBABILITY * (1.0 - 0.18));
    private final static int PROB_ONE_0 = (int) (BinaryArithmeticBuffer.MAX_PROBABILITY * (1.0 - 0.325));
    private final static int PROB_ONE_1 = (int) (BinaryArithmeticBuffer.MAX_PROBABILITY * (1.0 - 0.148));
    private final static int PROB_ONE_2 = (int) (BinaryArithmeticBuffer.MAX_PROBABILITY * (1.0 - 0.087));

}
