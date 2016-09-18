package org.minperf.hybrid;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.bdz.BDZ;
import org.minperf.eliasFano.EliasFanoMonotoneList;
import org.minperf.universal.UniversalHash;

/**
 * Evaluator for the hybrid mechanism.
 *
 * @param <T> the data type
 */
public class HybridEvaluator<T> {

    private final UniversalHash<T> hash;
    private final Settings settings;
    private final BitBuffer buffer;
    private final int size;
    private final int bucketCount;
    private final int startAlternativeBits;
    private final BDZ<T> bdz;
    private final EliasFanoMonotoneList startList;
    private final EliasFanoMonotoneList offsetList;
    private final int startBuckets;
    private final int mainOffset;

    public HybridEvaluator(UniversalHash<T> hash, Settings settings, BitBuffer buffer) {
        this.hash = hash;
        this.settings = settings;
        this.buffer = buffer;
        this.size = (int) (buffer.readEliasDelta() - 1);
        this.bucketCount = (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        this.startAlternativeBits = buffer.position();
        buffer.seek(startAlternativeBits + bucketCount);
        this.bdz = BDZ.load(hash, buffer);
        this.mainOffset = bdz.getSize();
        this.startList = EliasFanoMonotoneList.load(buffer);
        this.offsetList = EliasFanoMonotoneList.load(buffer);
        this.startBuckets = buffer.position();
    }

    @Override
    public String toString() {
        return "size " + size + " additional " + bdz.getSize();
    }

    public int get(T obj) {
        int b;
        long hashCode = hash.universalHash(obj, 0);
        if (bucketCount == 1) {
            b = 0;
        } else {
            long h = hash.universalHash(obj, 0);
            b = Settings.scaleLong(h, bucketCount);
        }
        if (buffer.readNumber(startAlternativeBits + b, 1) == 0) {
            return bdz.get(obj);
        }
        int offset = mainOffset;
        int o = offsetList.get(b);
        offset += o;
        int bucketSize;
        if (b == bucketCount - 1) {
            bucketSize = size - mainOffset - o;
        } else {
            bucketSize = offsetList.get(b + 1) - o;
        }
        int pos = startBuckets + startList.get(b);
        return evaluate(pos, obj, hashCode, 0, offset, bucketSize);
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
