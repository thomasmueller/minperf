package org.minperf.bloom.gcs;

import org.minperf.BitBuffer;
import org.minperf.bloom.Filter;
import org.minperf.bloom.mphf.Builder;
import org.minperf.hash.Mix;
import org.minperf.hem.Sort;
import org.minperf.monotoneList.MultiStageMonotoneList;

/**
 * Sometimes called "Golomb Coded Sets". This implementation uses Golomb coding,
 * which is slower than Golomb-Rice coding, but in theory could use slightly
 * less space (depending on the parameters).
 *
 * See here on how much space it uses: https://github.com/0xcb/Golomb-coded-map
 * log2(1/e) + 1/(1-(1-e)^(1/e)) So the overhead is about 1.5 bits/key (the pure
 * Golomb coding overhead is about 0.5 bits).
 */
public class GolombCompressedSet implements Filter {

    private final BitBuffer buff;
    private final int golombDivisor;
    private final int bufferSize;
    private final int bucketCount;
    private final int bitShift;
    private final int bucketShift;
    private final MultiStageMonotoneList start;
    private final int startBuckets;

    public static GolombCompressedSet construct(long[] keys, int setting) {
        return new GolombCompressedSet(keys, keys.length, setting);
    }

    GolombCompressedSet(long[] keys, int len, int fingerprintBits) {
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
        this.golombDivisor = getBestGolombDivisor(1L << fingerprintBits);
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
            writeGolomb(buckets, golombDivisor, diff);
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
        buff.seek(p);
        while (p < startNext) {
            long v = readGolomb(buff, golombDivisor);
            x += v;
            if (x == match) {
                return true;
            } else if (x > match) {
                break;
            }
            p = buff.position();
        }
        return false;
    }

    public static int getBestGolombDivisor(long average) {
        double p = 1. / average;
        return (int) (-1 / Math.log(1 - p));
    }

    /**
     * Write the Golomb code of a value.
     *
     * @param divisor the divisor
     * @param value the value
     */
    public static void writeGolomb(BitBuffer buff, int divisor, long value) {
        long q = value / divisor;
        for (long i = 0; i < q; i++) {
            buff.writeBit(1);
        }
        buff.writeBit(0);
        long r = value - q * divisor;
        int bit = 63 - Long.numberOfLeadingZeros(divisor - 1);
        if (r < ((2 << bit) - divisor)) {
            bit--;
        } else {
            r += (2 << bit) - divisor;
        }
        for (; bit >= 0; bit--) {
            buff.writeBit((r >>> bit) & 1);
        }
    }

    /**
     * Read a value that is stored as a Golomb code.
     *
     * @param divisor the divisor
     * @return the value
     */
    public static long readGolomb(BitBuffer buff, int divisor) {
        long q = 0;
        while (buff.readBit() == 1) {
            q++;
        }
        int bit = 63 - Long.numberOfLeadingZeros(divisor - 1);
        long r = 0;
        if (bit >= 0) {
            long cutOff = (2L << bit) - divisor;
            for (; bit > 0; bit--) {
                r = (r << 1) + buff.readBit();
            }
            if (r >= cutOff) {
                r = (r << 1) + buff.readBit() - cutOff;
            }
        }
        return q * divisor + r;
    }

    /**
     * Get the size of the Golomb code for this value.
     *
     * @param divisor the divisor
     * @param value the value
     * @return the number of bits
     */
    public static long getGolombSize(int divisor, long value) {
        long q = value / divisor;
        long r = value - q * divisor;
        int bit = 63 - Long.numberOfLeadingZeros(divisor - 1);
        if (r < ((2L << bit) - divisor)) {
            bit--;
        }
        return bit + q + 2;
    }

}
