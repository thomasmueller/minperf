/*
 * Copyright 2017 Randall Jones
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.minperf.bloom;

import java.util.Random;

import org.minperf.BitBuffer;
import org.minperf.hash.Mix;

/**
 * This is a Cuckoo Filter implementation.
 * It uses log(1/fpp)+3 bits per key.
 *
 * See "Cuckoo Filter: Practically Better Than Bloom".
 */
public class CuckooFilter implements Filter {

    private final BitBuffer buffer;
    private final int bucketCount;
    private final int fingerprintBits;
    private final int fingerprintBitsPerBucket;
    private final int fingerprintMask;
    private final int entriesPerBucket;
    private final Random random = new Random(1);

    public static CuckooFilter construct(long[] keys, int bitsPerKey) {

        int len = keys.length;
        CuckooFilter f = new CuckooFilter((int) (len / 0.95), bitsPerKey, 4);
        for (long k : keys) {
            f.insert(k);
        }
        return f;
    }

    public CuckooFilter(int capacity, int fingerprintBits, int entriesPerBucket) {
        // bucketCount needs to be even for bucket2 to work
        bucketCount = (int) Math.ceil((double) capacity / entriesPerBucket) / 2 * 2;
        this.entriesPerBucket = entriesPerBucket;
        this.fingerprintBits = fingerprintBits;
        this.fingerprintBitsPerBucket = fingerprintBits * entriesPerBucket;
        if (fingerprintBitsPerBucket > 63) {
            throw new AssertionError("too many fingerprint bits for the BitBuffer; max is 15 sorry");
        }
        this.buffer = new BitBuffer(fingerprintBits * bucketCount * entriesPerBucket);
        this.fingerprintMask = (1 << fingerprintBits) - 1;
    }

    public void insert(long key) {
        long hash = Mix.hash64(key);
        insertFingerprint(getBucket(hash), getFingerprint(hash));
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Mix.hash64(key);
        int bucket = getBucket(hash);
        int fingerprint = getFingerprint(hash);
        if (bucketContains(bucket, fingerprint)) {
            return true;
        }
        int bucket2 = getBucket2(bucket, fingerprint);
        return bucketContains(bucket2, fingerprint);
    }
    public boolean mayContainAtOnce(long key) {
        long hash = Mix.hash64(key);
        int bucket = getBucket(hash);
        int fingerprint = getFingerprint(hash);
        int bucket2 = getBucket2(bucket, fingerprint);
        return (bucketContains(bucket, fingerprint) || bucketContains(bucket2, fingerprint));
    }


    private int getBucket(long hash) {
        return reduce((int) hash, bucketCount);
    }

    private int getFingerprint(long hash) {
        int fingerprint =  (int) (hash & fingerprintMask);
        // fingerprint 0 is not allowed -
        // an alternative, with a slightly lower false positive rate with a
        // small fingerprint, would be: shift until it's not zero (but it
        // doesn't sound like it would be faster)
        // assume that this doesn't use branching
        return Math.max(1, fingerprint);
    }

    private int getBucket2(int bucket, int fingerprint) {
        // from the Murmur hash algorithm
        // some mixing (possibly not that great, but should be fast)
        long hash = fingerprint * 0xc4ceb9fe1a85ec53L;
        // we don't use xor; instead, we ensure bucketCount is even,
        // and bucket2 = bucketCount - bucket - y,
        // and if negative add the bucketCount,
        // where y is 1..bucketCount - 1 and odd -
        // that way, bucket2 is never the original bucket,
        // and running this twice will give the original bucket, as needed
        int r = (reduce((int) hash, bucketCount >> 1) << 1) + 1;
        int b2 = bucketCount - bucket - r;
        // not sure how to avoid this branch
        if (b2 < 0) {
            b2 += bucketCount;
        }
        return b2;
    }

    private boolean bucketContains(int bucket, int fingerprint) {
        // read all fingerprints at once - with 4 entries per bucket, this only
        // works up to 15 bits per fingerprint, with the current BitBuffer

        //////////////////////////
        // buffer.readNumber is probably not reasonable in a high performance setting
        /////////////////////////
        // casting to long to avoid overflow
        long allFingerprints = buffer.readNumber((long) bucket * fingerprintBitsPerBucket,
                fingerprintBitsPerBucket);
        for (int entry = 0; entry < entriesPerBucket; entry++) {
            if ((allFingerprints & fingerprintMask) == fingerprint) {
                return true;
            }
            allFingerprints >>>= fingerprintBits;
        }
        // this would be one fingerprint at a time
        // for (int entry = 0; entry < entriesPerBucket; entry++) {
        //     long fp = buffer.readNumber((bucket * entriesPerBucket + entry) * fingerprintBits, fingerprintBits);
        //     if (fp == fingerprint) {
        //         return true;
        //     }
        // }
        return false;
    }

    private boolean bucketInsert(int bucket, int fingerprint) {
        for (int entry = 0; entry < entriesPerBucket; entry++) {
            long fp = buffer.readNumber(((long)bucket * entriesPerBucket + entry) * fingerprintBits, fingerprintBits);
            if (fp == 0) {
                buffer.seek((int) ((bucket * entriesPerBucket + entry) * fingerprintBits));
                buffer.writeNumber(fingerprint, fingerprintBits);
                fp = buffer.readNumber(((long)bucket * entriesPerBucket + entry) * fingerprintBits, fingerprintBits);
                if (fp != fingerprint) {
                    throw new AssertionError();
                }
                return true;
            } else if (fp == fingerprint) {
                return true;
            }
        }
        return false;
    }

    private void insertFingerprint(int bucket, int fingerprint) {
        if (bucketInsert(bucket, fingerprint)) {
            return;
        }
        int bucket2 = getBucket2(bucket, fingerprint);
        if (bucketInsert(bucket2, fingerprint)) {
            return;
        }
        swap(bucket2, fingerprint);
    }

    private void swap(int bucket, int fingerprint) {
        for (int n = 0; n < 1000; n++) {
            int entry = random.nextInt() & (entriesPerBucket - 1);
            fingerprint = bucketsSwap(bucket, entry, fingerprint);
            bucket = getBucket2(bucket, fingerprint);
            if (bucketInsert(bucket, fingerprint)) {
                return;
            }
        }
        throw new IllegalStateException("Table full");
    }

    private int bucketsSwap(int bucket, int entry, int fingerprint) {
        int fp = (int) buffer.readNumber(((long)bucket * entriesPerBucket + entry) * fingerprintBits, fingerprintBits);
        buffer.seek((int) ((bucket * entriesPerBucket + entry) * fingerprintBits));
        // overwrite is not supported, so we first have to clear
        buffer.clearBits(fingerprintBits);
        buffer.seek((int) ((bucket * entriesPerBucket + entry) * fingerprintBits));
        buffer.writeNumber(fingerprint, fingerprintBits);
        return fp;
    }

    public long getBitCount() {
        return fingerprintBits * entriesPerBucket * bucketCount;
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
