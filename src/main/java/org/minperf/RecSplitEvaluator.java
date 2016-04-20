package org.minperf;

import org.minperf.universal.UniversalHash;

/**
 * Evaluate the minimum perfect hash function.
 *
 * @param <T> the key type
 */
public class RecSplitEvaluator<T> {

    private final Settings settings;
    private final BitBuffer in;
    private final UniversalHash<T> hash;

    private final long size;
    private final long dataBits;
    private final int bucketCount;
    private final int tableStart;
    private final int bitsPerEntry;
    private final int headerBits;

    RecSplitEvaluator(BitBuffer buffer, UniversalHash<T> hash,
            Settings settings) {
        this.settings = settings;
        this.hash = hash;
        this.in = buffer;

        in.seek(0);
        size = in.readEliasDelta() - 1;
        bucketCount = (int) (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        if (bucketCount == 1) {
            bitsPerEntry = 0;
            dataBits = 0;
        } else {
            dataBits = settings.getEstimatedBits(size) +
                    BitBuffer.unfoldSigned(in.readEliasDelta() - 1);
            bitsPerEntry = (int) in.readGolombRice(2);
        }
        tableStart = in.position();
        int tableBits = (bitsPerEntry + bitsPerEntry) * (bucketCount - 1);
        headerBits = tableStart + tableBits;

    }

    public int evaluate(T obj) {
        int x;
        if (bucketCount == 1) {
            x = 0;
        } else {
            x = hash.universalHash(obj, 0);
            x = Settings.supplementalHash(x, 0, bucketCount);
        }
        int add, start, pSize;
        if (x == 0) {
            in.seek(tableStart);
            add = 0;
            start = 0;
        } else {
            in.seek(tableStart + (bitsPerEntry + bitsPerEntry) * (x - 1));
            int expectedAdd = (int) (size * x / bucketCount);
            int expectedStart = (int) (dataBits * x / bucketCount);
            add = (int) BitBuffer.unfoldSigned(in.readNumber(bitsPerEntry)) + expectedAdd;
            start = (int) BitBuffer.unfoldSigned(in.readNumber(bitsPerEntry)) + expectedStart;
        }
        if (x < bucketCount - 1) {
            int expectedAdd = (int) (size * (x + 1) / bucketCount);
            int nextAdd = (int) BitBuffer.unfoldSigned(in.readNumber(bitsPerEntry)) + expectedAdd;
            pSize = nextAdd - add;
        } else {
            pSize = (int) (size - add);
        }
        if (bucketCount > 0) {
            in.seek(headerBits + start);
        }
        int hashCode = hash.universalHash(obj, 1);
        return add + evaluate(in, obj, hashCode, 0, 0, pSize);
    }

    private int evaluate(BitBuffer in, T obj, int hashCode,
            long startIndex, int add, int size) {
        if (size < 2) {
            return add;
        }
        long oldX = Settings.getUniversalHashIndex(startIndex);
        long index = in.readGolombRice(settings.getGolombRiceShift(size)) + startIndex + 1;
        long x = Settings.getUniversalHashIndex(index);
        if (size <= settings.getLeafSize()) {
            if (obj == null) {
                return -1;
            }
            if (x != oldX) {
                hashCode = hash.universalHash(obj, x + 1);
            }
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
        if (obj == null) {
            int s = firstPart;
            for (int i = 0; i < split; i++) {
                evaluate(in, null, 0, 0, 0, s);
                s = otherPart;
            }
            return -1;
        }
        if (x != oldX) {
            hashCode = hash.universalHash(obj, x + 1);
        }
        if (firstPart != otherPart) {
            int h = Settings.supplementalHash(hashCode, index, size);
            if (h < firstPart) {
                return evaluate(in, obj, hashCode, index, add, firstPart);
            }
            evaluate(in, null, 0, 0, 0, firstPart);
            add += firstPart;
            return evaluate(in, obj, hashCode, index, add, otherPart);
        }
        int h = Settings.supplementalHash(hashCode, index, split);
        for (int i = 0; i < h; i++) {
            evaluate(in, null, 0, 0, 0, firstPart);
            add += firstPart;
        }
        return evaluate(in, obj, hashCode, index, add, firstPart);
    }

}
