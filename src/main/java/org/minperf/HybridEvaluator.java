package org.minperf;

import org.minperf.bdz.BDZ;
import org.minperf.eliasFano.EliasFanoMonotoneList;
import org.minperf.generator.HybridGenerator;
import org.minperf.universal.UniversalHash;

/**
 * Evaluator for the hybrid mechanism.
 *
 * @param <T> the data type
 */
public class HybridEvaluator<T> extends RecSplitEvaluator<T> {

    private final int minStartDiff;
    private final EliasFanoMonotoneList startList;
    private final int minOffsetDiff;
    private final EliasFanoMonotoneList offsetList;
    private final int startBuckets;
    private final int maxBits;
    private final boolean alternativeHashOption;

    public HybridEvaluator(BitBuffer buffer, UniversalHash<T> hash, Settings settings) {
        super(buffer, hash, settings);
        this.alternativeHashOption = buffer.readBit() != 0;
        this.minOffsetDiff = (int) (buffer.readEliasDelta() - 1);
        this.offsetList = EliasFanoMonotoneList.load(buffer);
        this.minStartDiff = (int) (buffer.readEliasDelta() - 1);
        this.startList = EliasFanoMonotoneList.load(buffer);
        this.startBuckets = buffer.position();
        if (bucketCount > 0) {
            int averageBucketSize = (int) size / bucketCount;
            int maxBucketSize = averageBucketSize * HybridGenerator.MAX_FILL;
            this.maxBits = maxBucketSize * HybridGenerator.MAX_BITS_PER_ENTRY;
        } else {
            this.maxBits = 0;
        }
    }

    @Override
    public void init() {
        // TODO only needed because this is a superclass
    }

    @Override
    public int evaluate(T obj) {
        int b;
        long hashCode = hash.universalHash(obj, 0);
        if (bucketCount == 1) {
            b = 0;
        } else {
            long h = hash.universalHash(obj, 0);
            b = Settings.scaleLong(h, bucketCount);
        }
        int offset = 0;

        long offsetPair = offsetList.getPair(b);
        int o = (int) (offsetPair >>> 32) + b * minOffsetDiff;
        offset += o;
        int offsetNext = ((int) offsetPair) + (b + 1) * minOffsetDiff;
        int bucketSize = offsetNext - o;
        int startPos;
        if (alternativeHashOption) {
            long startPair = startList.getPair(b);
            int start = (int) (startPair >>> 32);
            int startNext = (int) startPair;
            startPos = startBuckets +
                    HybridGenerator.scaleSize(offset) +
                    start + b * minStartDiff;
            int startNextPos = startBuckets +
                    HybridGenerator.scaleSize(offsetNext) +
                    startNext + (b + 1) *
                    minStartDiff;
            int bitCount = startNextPos - startPos;
            if (bitCount > maxBits) {
                BitBuffer b2 = new BitBuffer(buffer);
                b2.seek(startPos);
                return offset + BDZ.load(hash, b2).get(obj);
            }
        } else {
            startPos = startBuckets +
                    HybridGenerator.scaleSize(offset) +
                    startList.get(b) + b * minStartDiff;
        }
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
                int h = Settings.supplementalHash(hashCode, index, size);
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
            if (firstPart != otherPart) {
                int h = Settings.supplementalHash(hashCode, index, size);
                if (h < firstPart) {
                    size = firstPart;
                    continue;
                }
                pos = skip(pos, firstPart);
                add += firstPart;
                size = otherPart;
                continue;
            }
            int h = Settings.supplementalHash(hashCode, index, split);
            for (int i = 0; i < h; i++) {
                pos = skip(pos, firstPart);
                add += firstPart;
            }
            size = firstPart;
        }
    }

}
