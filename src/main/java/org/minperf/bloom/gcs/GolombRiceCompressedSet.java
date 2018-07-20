package org.minperf.bloom.gcs;

import org.minperf.BitBuffer;
import org.minperf.bloom.Filter;
import org.minperf.bloom.mphf.Builder;
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
    private final int bitShift;
    private final int bucketShift;
    private final MultiStageMonotoneList start;
    private final int startBuckets;

    public static GolombRiceCompressedSet construct(long[] keys, int setting) {
        return new GolombRiceCompressedSet(keys, keys.length, setting);
    }

    GolombRiceCompressedSet(long[] keys, int len, int fingerprintBits) {
        int averageBucketSize = 16;
        int bitCount = 63 - Long.numberOfLeadingZeros((long) len << fingerprintBits);
        long[] hashes = new long[len];
        for (int i = 0; i < len; i++) {
            hashes[i] = Mix.hash64(keys[i]);
        }
        Sort.parallelSortUnsigned(hashes, 0, len);
        bucketCount = Builder.getBucketCount(len, averageBucketSize);
        int bucketBitCount = 31 - Integer.numberOfLeadingZeros(bucketCount);
        bitShift = 64 - bitCount;
        bucketShift = 64 - bucketBitCount - bitShift;
        if (bucketShift <= 0 || bucketShift >= 64) {
            throw new IllegalArgumentException();
        }
        this.golombShift = fingerprintBits;
        BitBuffer buckets = new BitBuffer(10L * fingerprintBits * len);
        int[] startList = new int[bucketCount + 1];
        int bucket = 0;
        long last = 0;
        for (int i = 0; i < len; i++) {
            long x = hashes[i] >>> bitShift;
            int b = (int) (x >>> bucketShift);
            while (bucket <= b) {
                startList[bucket++] = buckets.position();
                last = ((long) b) << bucketShift;
            }
            long diff = x - last;
            last = x;
            buckets.writeGolombRice(golombShift, diff);
        }
        while (bucket <= bucketCount) {
            startList[bucket++] = buckets.position();
        }
        buff = new BitBuffer(10L * bitCount * len);
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
        long match = hashCode >>> bitShift;
        int b = (int) (match >>> bucketShift);
        long startPair = start.getPair(b);
        int p = startBuckets + (int) (startPair >>> 32);
        int startNext = startBuckets + (int) startPair;
        long x = ((long) b) << bucketShift;
        while (p < startNext) {
            long v = buff.readGolombRice(p, golombShift);
            x += v;
            p += BitBuffer.getGolombRiceSize(golombShift, v);
            if (x == match) {
                return true;
            } else if (x > match) {
                break;
            }
        }
        return false;
    }

}
