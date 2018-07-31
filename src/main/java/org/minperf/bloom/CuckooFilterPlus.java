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
 * It uses (1/0.94) * (log(1/fpp)+2) bits per key.
 * Space and speed should be between the regular cuckoo filter and the semi-sort variant.
 *
 * See "Cuckoo Filter: Practically Better Than Bloom".
 */
public class CuckooFilterPlus implements Filter {

    private static final int SHIFTED = 1;
    private static final int SECOND = 2;

    private final BitBuffer buffer;
    private final int bucketCount;
    private final int bitsPerEntry;
    private final long fingerprintMask;
    private final Random random = new Random(1);

    public static CuckooFilterPlus construct(long[] keys, int bitsPerKey) {
        int len = keys.length;
        CuckooFilterPlus f = new CuckooFilterPlus((int) (len / 0.94), bitsPerKey);
        for (long k : keys) {
            f.insert(k);
        }
        return f;
    }

    public CuckooFilterPlus(int capacity, int fingerprintBits) {
        if (fingerprintBits > 60) {
            throw new AssertionError("Too many fingerprint bits for the BitBuffer");
        }
        this.bitsPerEntry = fingerprintBits + 2;
        this.fingerprintMask = (1L << fingerprintBits) - 1;
        // bucketCount needs to be even for bucket2 to work
        bucketCount = (int) Math.ceil((double) capacity) / 2 * 2;
        this.buffer = new BitBuffer(bitsPerEntry * (bucketCount + 1));
    }

    public void insert(long key) {
        long hash = Mix.hash64(key);
        int bucket = getBucket(hash);
        long fingerprint = getFingerprint(hash);
        long x = fingerprint << 2;
        if (bucketInsert(bucket, x)) {
            return;
        }
        int bucket2 = getBucket2(bucket, x);
        if (bucketInsert(bucket2, x | SECOND)) {
            return;
        }
        if (random.nextBoolean()) {
            swap(bucket, x);
        } else {
            swap(bucket2, x | SECOND);
        }
    }

    private void set(int index, long x) {
        int pos = (int) (index * bitsPerEntry);
        buffer.seek(pos);
        // overwrite is not supported, so we first have to clear
        buffer.clearBits(bitsPerEntry);
        buffer.seek(pos);
        buffer.writeNumber(x, bitsPerEntry);
    }

    private long get(int index) {
        int pos = (int) (index * bitsPerEntry);
        return buffer.readNumber(pos, bitsPerEntry);
    }

    private boolean bucketInsert(int index, long x) {
        long fp = get(index);
        if (fp == 0) {
            set(index, x);
            return true;
        } else if (fp == x) {
            // already inserted
            return true;
        }
        index++;
        x |= SHIFTED;
        fp = get(index);
        if (fp == 0) {
            set(index, x);
            return true;
        } else if (fp == x) {
            // already inserted
            return true;
        }
        return false;
    }

    private void swap(int index, long x) {
        for (int n = 0; n < 10000; n++) {
            if (random.nextBoolean()) {
                index++;
                x |= SHIFTED;
            }
            long old = get(index);
            set(index, x);
            if (old == 0) {
                throw new AssertionError();
            }
            index = getBucket2(index, old);
            old ^= SECOND;
            old &= ~SHIFTED;
            if (bucketInsert(index, old)) {
                return;
            }
            x = old;
        }
        throw new IllegalStateException("Table full");
    }

    private int getBucket2(int index, long x) {
        if ((x & SHIFTED) != 0) {
            index--;
        }
        // TODO we know whether this was the second or the first,
        // and could use that info - would it make sense to use it?
        long fingerprint = x >> 2;
        // from the Murmur hash algorithm
        // some mixing (possibly not that great, but should be fast)
        long hash = fingerprint * 0xc4ceb9fe1a85ec53L;
        // we don't use xor; instead, we ensure bucketCount is even,
        // and bucket2 = bucketCount - bucket - reduce(hash(fingerprint)),
        // and if negative add the bucketCount,
        // where y is 1..bucketCount - 1 and odd -
        // that way, bucket2 is never the original bucket,
        // and running this twice will give the original bucket, as needed
        int r = reduce((int) hash, bucketCount);
        int b2 = bucketCount - index - r;
        // not sure how to avoid this branch
        if (b2 < 0) {
            b2 += bucketCount;
        }
        return b2;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Mix.hash64(key);
        int bucket = getBucket(hash);
        long fingerprint = getFingerprint(hash);
        long x = fingerprint << 2;
        if (get(bucket) == x) {
            return true;
        }
        if (get(bucket + 1) == (x | SHIFTED)) {
            return true;
        }
        int bucket2 = getBucket2(bucket, x);
        x |= SECOND;
        if (get(bucket2) == x) {
            return true;
        }
        if (get(bucket2 + 1) == (x | SHIFTED)) {
            return true;
        }
        return false;
    }

    private int getBucket(long hash) {
        return reduce((int) hash, bucketCount);
    }

    private long getFingerprint(long hash) {
        // TODO is this needed?
        hash = Mix.hash64(hash);
        long fingerprint =  (int) (hash & fingerprintMask);
        // fingerprint 0 is not allowed -
        // an alternative, with a slightly lower false positive rate with a
        // small fingerprint, would be: shift until it's not zero (but it
        // doesn't sound like it would be faster)
        // assume that this doesn't use branching
        return Math.max(1, fingerprint);
    }

    public long getBitCount() {
        return bitsPerEntry * (bucketCount + 1);
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
