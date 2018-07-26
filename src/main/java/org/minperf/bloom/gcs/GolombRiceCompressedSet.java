package org.minperf.bloom.gcs;

import org.minperf.BitBuffer;
import org.minperf.bloom.Filter;
import org.minperf.hash.Mix;
import org.minperf.hem.Sort;
import org.minperf.monotoneList.MultiStageMonotoneList;

/**
 * Sometimes called "Golomb Coded Sets". This implementation uses Golomb-Rice
 * coding, which is faster than Golomb coding, but uses slightly more space.
 *
 * See here on how much space it uses: https://github.com/0xcb/Golomb-coded-map
 * log2(1/e) + 1/(1-(1-e)^(1/e)) So the overhead is about 1.5 bits/key (the pure
 * Golomb coding overhead is about 0.5 bits).
 */
public class GolombRiceCompressedSet implements Filter {

    private final BitBuffer buff;
    private final int golombShift;
    private final int bufferSize;
    private final int bucketCount;
    private final int fingerprintMask;
    private final MultiStageMonotoneList start;
    private final int startBuckets;

    public static GolombRiceCompressedSet construct(long[] keys, int setting) {
        return new GolombRiceCompressedSet(keys, keys.length, setting);
    }

    GolombRiceCompressedSet(long[] keys, int len, int fingerprintBits) {
        // this was found experimentally
        golombShift = fingerprintBits - 1;
        int averageBucketSize = 64;
        // due to average bucket size of 64
        fingerprintBits += 6;
        long[] data = new long[len];
        fingerprintMask = (1 << fingerprintBits) - 1;
        bucketCount = (int) ((len + averageBucketSize - 1) / averageBucketSize);
        for (int i = 0; i < len; i++) {
            long h = Mix.hash64(keys[i]);
            long b = reduce((int) (h >>> 32), bucketCount);
            data[i] = (b << 32) | (h & fingerprintMask);
        }
        Sort.parallelSortUnsigned(data, 0, len);
        BitBuffer buckets = new BitBuffer(10L * fingerprintBits * len);
        int[] startList = new int[bucketCount + 1];
        int bucket = 0;
        long last = 0;
        for (int i = 0; i < len; i++) {
            long x = data[i];
            int b = (int) (x >>> 32);
            while (bucket <= b) {
                startList[bucket++] = buckets.position();
                last = 0;
            }
            x &= fingerprintMask;
            long diff = x - last;
            last = x;
            buckets.writeGolombRice(golombShift, diff);
        }
        while (bucket <= bucketCount) {
            startList[bucket++] = buckets.position();
        }
        buff = new BitBuffer(10L *  fingerprintBits * len);
        buff.writeEliasDelta(len + 1);
        start = MultiStageMonotoneList.generate(startList, buff);
        startBuckets = buff.position();
        buff.write(buckets);
        bufferSize = buff.position();
    }

    @Override
    public long getBitCount() {
        return bufferSize;
    }

    @Override
    public boolean mayContain(long key) {
        long hashCode = Mix.hash64(key);
        int b = reduce((int) (hashCode >>> 32), bucketCount);
        long fingerprint = hashCode & fingerprintMask;
        long startPair = start.getPair(b);
        int p = startBuckets + (int) (startPair >>> 32);
        int startNext = startBuckets + (int) startPair;
        long x = 0;
        while (p < startNext) {
            long q = buff.readUntilZero(p);
            p += q + 1;
            x += (q << golombShift) | buff.readNumber(p, golombShift);
            if (x == fingerprint) {
                return true;
            } else if (x > fingerprint) {
                break;
            }
            p += golombShift;
        }
        return false;
    }

    private static int reduce(int hash, int n) {
        // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        return (int) (((hash & 0xffffffffL) * n) >>> 32);
    }

}
