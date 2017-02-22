package org.minperf;

import org.minperf.bdz.BDZ;
import org.minperf.generator.Generator;
import org.minperf.monotoneList.MonotoneList;
import org.minperf.universal.UniversalHash;

/**
 * Evaluator for the hybrid mechanism.
 *
 * @param <T> the data type
 */
public class RecSplitEvaluator<T> {

    private final Settings settings;
    private final UniversalHash<T> hash;
    private final BitBuffer buffer;
    private final long size;
    private final int bucketCount;
    private final int minStartDiff;
    private final MonotoneList startList;
    private final int minOffsetDiff;
    private final MonotoneList offsetList;
    private final int startBuckets;
    private final BDZ<T> alternative;

    public RecSplitEvaluator(BitBuffer buffer, UniversalHash<T> hash, Settings settings, boolean eliasFanoMonotoneLists) {
        this.settings = settings;
        this.hash = hash;
        this.buffer = buffer;
        this.size = (int) (buffer.readEliasDelta() - 1);
        this.bucketCount = Settings.getBucketCount(size, settings.getAverageBucketSize());
        boolean alternative = buffer.readBit() != 0;
        this.minOffsetDiff = (int) (buffer.readEliasDelta() - 1);
        this.offsetList = MonotoneList.load(buffer, eliasFanoMonotoneLists);
        this.minStartDiff = (int) (buffer.readEliasDelta() - 1);
        this.startList = MonotoneList.load(buffer, eliasFanoMonotoneLists);
        this.startBuckets = buffer.position();
        if (alternative) {
            int b = bucketCount;
            int offset = offsetList.get(b);
            int pos = startBuckets +
                    Generator.getMinBitCount(offset) +
                    startList.get(b) + b * minStartDiff;
            buffer.seek(pos);
            this.alternative = BDZ.load(hash, buffer);
        } else {
            this.alternative = null;
        }
    }

    public int evaluate(T obj) {
        int b;
        long hashCode = hash.universalHash(obj, 0);
        if (bucketCount == 1) {
            b = 0;
        } else {
            b = Settings.reduce((int) hashCode, bucketCount);
        }
        int startPos;
        long offsetPair = offsetList.getPair(b);
        int offset = (int) (offsetPair >>> 32) + b * minOffsetDiff;
        int offsetNext = ((int) offsetPair) + (b + 1) * minOffsetDiff;
        if (offsetNext == offset) {
            if (alternative == null) {
                // entry not found
                return 0;
            }
            offset = offsetList.get(bucketCount) + bucketCount * minOffsetDiff;
            return offset + alternative.evaluate(obj);
        }
        int bucketSize = offsetNext - offset;
        startPos = startBuckets +
                Generator.getMinBitCount(offset) +
                startList.get(b) + b * minStartDiff;
        return evaluate(startPos, obj, hashCode, 0, offset, bucketSize);
    }

    private int skip(int pos, int size) {
        if (size < 2) {
            return pos;
        }
        pos = buffer.skipGolombRice(pos, settings.getGolombRiceShift(size));
        if (size <= settings.getLeafSize()) {
            return pos;
        }
        int split = settings.getSplit(size);
        int firstPart, otherPart;
        if (split < 0) {
            firstPart = -split;
            otherPart = size - firstPart;
            split = 2;
        } else {
            firstPart = size / split;
            otherPart = firstPart;
        }
        int s = firstPart;
        for (int i = 0; i < split; i++) {
            pos = skip(pos, s);
            s = otherPart;
        }
        return pos;
    }

    private int evaluate(int pos, T obj, long hashCode,
            long index, int add, int size) {
        while (true) {
            if (size < 2) {
                return add;
            }
            int shift = settings.getGolombRiceShift(size);
            long q = buffer.readUntilZero(pos);
            pos += q + 1;
            long value = (q << shift) | buffer.readNumber(pos, shift);
            pos += shift;
            long oldX = Settings.getUniversalHashIndex(index);
            index += value + 1;
            long x = Settings.getUniversalHashIndex(index);
            if (x != oldX) {
                hashCode = hash.universalHash(obj, x);
            }
            if (size <= settings.getLeafSize()) {
                int h = Settings.supplementalHash(hashCode, index);
                h = Settings.reduce(h, size);
                return add + h;
            }
            int split = settings.getSplit(size);
            int firstPart, otherPart;
            if (split < 0) {
                firstPart = -split;
                otherPart = size - firstPart;
                split = 2;
            } else {
                firstPart = size / split;
                otherPart = firstPart;
            }
            int h = Settings.supplementalHash(hashCode, index);
            if (firstPart != otherPart) {
                h = Settings.reduce(h, size);
                if (h < firstPart) {
                    size = firstPart;
                    continue;
                }
                pos = skip(pos, firstPart);
                add += firstPart;
                size = otherPart;
                continue;
            }
            h = Settings.reduce(h, split);
            for (int i = 0; i < h; i++) {
                pos = skip(pos, firstPart);
                add += firstPart;
            }
            size = firstPart;
        }
    }

}
