package org.minperf.bloom;

import org.minperf.BitBuffer;
import org.minperf.hem.RandomGenerator;
import org.minperf.hem.Sort;
import org.minperf.monotoneList.MultiStageMonotoneList;

public class GolombCompressedSet {

    public static void main(String... args) {
        for(int bitsPerKey = 3; bitsPerKey < 15; bitsPerKey++) {
            test(bitsPerKey);
        }
    }

    public static void test(int bitsPerKey) {
        int testCount = 1;
        int len = 4 * 1024 * 1024;
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long time = System.nanoTime();
        GolombCompressedSet f = new GolombCompressedSet(list, len, bitsPerKey);
        long addTime = (System.nanoTime() - time) / len;
        time = System.nanoTime();
        int falsePositives = 0;
        for (int test = 0; test < testCount; test++) {
            for (int i = 0; i < len; i++) {
                if (!f.mayContain(list[i])) {
                    f.mayContain(list[i]);
                    throw new AssertionError();
                }
            }
            for (int i = len; i < len * 2; i++) {
                if (f.mayContain(list[i])) {
                    falsePositives++;
                }
            }
        }
        long getTime = (System.nanoTime() - time) / len / testCount;
        double falsePositiveRate = (100. / testCount / len * falsePositives);
        System.out.println("GCS false positives: " + falsePositiveRate +
                "% " + (double) f.getBitCount() / len + " bits/key " +
                "add: " + addTime + " get: " + getTime + " ns/key");
    }

    private final BitBuffer buff;
    private final int golombShift;
    private final int bufferSize;
    private final int bucketCount;
    private final int bitShift;
    private final int bucketShift;
    private final MultiStageMonotoneList start;
    private final int startBuckets;

    GolombCompressedSet(long[] hashes, int len, int bits) {
        int averageBucketSize = 16;
        int bitCount = 63 - Long.numberOfLeadingZeros((long) len << bits);
        Sort.parallelSortUnsigned(hashes, 0, len);
        bucketCount = Builder.getBucketCount(len, averageBucketSize);
        int bucketBitCount = 31 - Integer.numberOfLeadingZeros(bucketCount);
        bitShift = 64 - bitCount;
        bucketShift = 64 - bucketBitCount - bitShift;
        if (bucketShift <= 0 || bucketShift >= 64) {
            throw new IllegalArgumentException();
        }
        this.golombShift = bits - 1;
        BitBuffer buckets = new BitBuffer(10 * bits * len);
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
        buff = new BitBuffer(10 * bitCount * len);
        buff.writeEliasDelta(len + 1);
        start = MultiStageMonotoneList.generate(startList, buff);
        startBuckets = buff.position();
        buff.write(buckets);
        bufferSize = buff.position();
    }

    int getBitCount() {
        return bufferSize;
    }

    boolean mayContain(long hashCode) {
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
