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

import org.minperf.hash.Mix;

/**
 * This is a Cuckoo Filter implementation.
 * It uses log(1/fpp)+3 bits per key.
 *
 * See "Cuckoo Filter: Practically Better Than Bloom".
 */
public class CuckooFilter_8bit_4entries implements Filter {

    private static final int FINGERPRINT_BITS = 8;
    private static final int ENTRIES_PER_BUCKET = 4;
    private static final int FINGERPRINT_MASK = 0xff;

    private final int[] data;
    private final int bucketCount;
    private final Random random = new Random(1);

    public static CuckooFilter_8bit_4entries construct(long[] keys) {
        int len = keys.length;
        CuckooFilter_8bit_4entries f = new CuckooFilter_8bit_4entries((int) (len / 0.95));
        for (long k : keys) {
            f.insert(k);
        }
        return f;
    }

    public CuckooFilter_8bit_4entries(int capacity) {
        // bucketCount needs to be even for bucket2 to work
        bucketCount = (int) Math.ceil((double) capacity / ENTRIES_PER_BUCKET) / 2 * 2;
        this.data = new int[bucketCount];
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

    public boolean mayContainReallyAtOnce(long key) {
        long hash = Mix.hash64(key);
        int bucket = getBucket(hash);
        int fingerprint = getFingerprint(hash);
        int bucket2 = getBucket2(bucket, fingerprint);
        return bucketsContains(bucket, bucket2, fingerprint);
    }

    private int getBucket(long hash) {
        return reduce((int) hash, bucketCount);
    }

    private int getFingerprint(long hash) {
        int fingerprint =  (int) (hash & FINGERPRINT_MASK);
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
        int allFingerprints = data[bucket];
        // from https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
        int v = allFingerprints ^ (fingerprint * 0x01010101);
        int hasZeroByte = ~((((v & 0x7f7f7f7f) + 0x7f7f7f7f) | v) | 0x7f7f7f7f);
        return hasZeroByte != 0;
    }

    private boolean bucketsContains(int bucket, int bucket2, int fingerprint) {
        long allFingerprints = ((long) data[bucket] << 32) | (data[bucket2] & 0xffffffffL);
        // from https://graphics.stanford.edu/~seander/bithacks.html#ZeroInWord
        long v = allFingerprints ^ (fingerprint * 0x0101010101010101L);
        long hasZeroByte = ~((((v & 0x7f7f7f7f7f7f7f7fL) + 0x7f7f7f7f7f7f7f7fL) | v) | 0x7f7f7f7f7f7f7f7fL);
        return hasZeroByte != 0;
    }

    private int getFingerprintAt(int bucket, int entry) {
        return (data[bucket] >>> (FINGERPRINT_BITS * entry)) & FINGERPRINT_MASK;
    }

    private void setFingerprintAt(int bucket, int entry, int fingerprint) {
        data[bucket] &= ~(FINGERPRINT_MASK << (FINGERPRINT_BITS * entry));
        data[bucket] |= fingerprint << (FINGERPRINT_BITS * entry);
    }

    private boolean bucketInsert(int bucket, int fingerprint) {
        for (int entry = 0; entry < ENTRIES_PER_BUCKET; entry++) {
            long fp = getFingerprintAt(bucket, entry);
            if (fp == 0) {
                setFingerprintAt(bucket, entry, fingerprint);
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
            int entry = random.nextInt() & (ENTRIES_PER_BUCKET - 1);
            fingerprint = bucketsSwap(bucket, entry, fingerprint);
            bucket = getBucket2(bucket, fingerprint);
            if (bucketInsert(bucket, fingerprint)) {
                return;
            }
        }
        throw new IllegalStateException("Table full");
    }

    private int bucketsSwap(int bucket, int entry, int fingerprint) {
        int old = getFingerprintAt(bucket, entry);
        setFingerprintAt(bucket, entry, fingerprint);
        return old;
    }

    public long getBitCount() {
        return FINGERPRINT_BITS * ENTRIES_PER_BUCKET * bucketCount;
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
