package org.minperf.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.universal.UniversalHash;

/**
 * A generator for hash function descriptions.
 *
 * @param <T> the type
 */
public class Generator<T> {

    static final int PARALLELISM = Runtime.getRuntime().availableProcessors();

    final Settings settings;
    final UniversalHash<T> hash;
    final Processor<T> processor;
    
    public Generator(UniversalHash<T> hash, Settings settings, boolean multiThreaded) {
        this.settings = settings;
        this.hash = hash;
        if (multiThreaded) {
            processor = new MultiThreadedProcessor<T>(this);
        } else {
            processor = new SingleThreadedProcessor<T>(this);
        }
    }

    @SuppressWarnings("unchecked")
    void generate(T[] data, int[] hashes, long startIndex, Processor<T> p) {
        int size = data.length;
        if (size < 2) {
            return;
        }
        if (size <= settings.getLeafSize()) {
            long index = getIndex(data, hashes, startIndex);
            int shift = settings.getGolombRiceShift(size);
            long value = index - startIndex - 1;
            p.writeLeaf(shift, value);
            return;
        }
        long index = startIndex + 1;
        while (true) {
            if (Settings.needNewUniversalHashIndex(index)) {
                long x = Settings.getUniversalHashIndex(index);
                for (int i = 0; i < size; i++) {
                    hashes[i] = hash.universalHash(data[i], x + 1);
                }
            }
            if (trySplitEvenly(hashes, index)) {
                break;
            }
            index++;
        }
        int writeK = settings.getGolombRiceShift(size);
        long writeIndex = index - startIndex - 1;
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
        T[][] data2;
        int[][] hashes2;
        if (firstPart != otherPart) {
            data2 = (T[][]) new Object[][] { (T[]) new Object[firstPart],
                    (T[]) new Object[otherPart] };
            hashes2 = new int[][] { new int[firstPart], new int[otherPart] };
        } else {
            data2 = (T[][]) new Object[split][firstPart];
            hashes2 = new int[split][firstPart];
        }
        splitEvenly(data, hashes, index, data2, hashes2);
        p.split(writeK, writeIndex, index, data2, hashes2);
    }

    public BitBuffer generate(Collection<T> collection) {
        int size = collection.size();
        int bucketCount = (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        BitBuffer all = new BitBuffer(new byte[10 + size / 2]);
        all.writeEliasDelta(size + 1);
        if (size > 1) {
            generateBuckets(collection, size, bucketCount, all);
        }
        return all;
    }

    private void generateBuckets(Collection<T> collection, long size, int bucketCount,
            BitBuffer d) {
        ArrayList<ArrayList<T>> subList = new ArrayList<ArrayList<T>>(
                bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            subList.add(new ArrayList<T>((int) (size / bucketCount)));
        }
        int maxBucketSize  = (int) (20 * (size / bucketCount));
        int[] sizes = new int[bucketCount];
        // doing this in parallel probably wouldn't to save much time
        for (T t : collection) {
            int b;
            if (bucketCount == 1) {
                b = 0;
            } else {
                int h = hash.universalHash(t, 0);
                b = Settings.supplementalHash(h, 0, bucketCount);
            }
            sizes[b]++;
            if (sizes[b] > maxBucketSize) {
                throw new IllegalArgumentException(
                        "Bucket size too large, most likely due to bad universal hash function");
            }
            subList.get(b).add(t);
        }
        ArrayList<BitBuffer> outList = new ArrayList<BitBuffer>();
        @SuppressWarnings("unchecked")
        T[][] data = (T[][]) new Object[bucketCount][];
        int[][] hashes = new int[bucketCount][];
        for (int i = 0; i < bucketCount; i++) {
            ArrayList<T> list = subList.get(i);
            int len = list.size();
            @SuppressWarnings("unchecked")
            T[] x = (T[]) list.toArray(new Object[len]);
            data[i] = x;
            int[] h = new int[len];
            for (int j = 0; j < len; j++) {
                h[j] = hash.universalHash(list.get(j), 1);
            }
            hashes[i] = h;
        }
        generate(data, hashes, outList);
        // assuming 4 bits per key worst case
        BitBuffer bitOut = new BitBuffer(new byte[10 + (int) (size / 2)]);
        int add = 0;
        int[] startList = new int[outList.size()];
        int[] addList = new int[outList.size()];
        for (int i = 0; i < outList.size(); i++) {
            addList[i] = add;
            startList[i] = bitOut.position();
            BitBuffer out = outList.get(i);
            if (out != null) {
                bitOut.write(out);
            }
            add += sizes[i];
        }
        long dataBits = bitOut.position();
        int maxOffset = 0;
        for (int i = 0; i < outList.size(); i++) {
            int expectedAdd = (int) (size * i / bucketCount);
            int offsetAdd = addList[i] - expectedAdd;
            maxOffset = Math.max(maxOffset, (int) BitBuffer.foldSigned(offsetAdd));
            int expectedStart = (int) (dataBits * i / bucketCount);
            int offsetStart = startList[i] - expectedStart;
            maxOffset = Math.max(maxOffset, (int) BitBuffer.foldSigned(offsetStart));
        }
        int bitsPerEntry = 32 - Integer.numberOfLeadingZeros(maxOffset);
        if (bucketCount > 1) {
            d.writeEliasDelta(BitBuffer.foldSigned(
                    dataBits - settings.getEstimatedBits(size)) + 1);
            d.writeGolombRice(2, bitsPerEntry);
        }
        for (int i = 1; i < outList.size(); i++) {
            int expectedAdd = (int) (size * i / bucketCount);
            int offsetAdd = addList[i] - expectedAdd;
            d.writeNumber(BitBuffer.foldSigned(offsetAdd), bitsPerEntry);
            int start = (int) (dataBits * i / bucketCount);
            d.writeNumber(BitBuffer.foldSigned(startList[i] - start),
                    bitsPerEntry);
        }
        d.write(bitOut);
    }

    private void generate(final T[][] lists,
            final int[][] hashLists,
            final ArrayList<BitBuffer> outList) {
        processor.process(lists, hashLists, outList);
    }

    private long getIndex(T[] data, int[] hashes, long startIndex) {
        int size = data.length;
        long index = startIndex + 1;
        outer: while (true) {
            if (Settings.needNewUniversalHashIndex(index)) {
                long x = Settings.getUniversalHashIndex(index);
                for (int i = 0; i < size; i++) {
                    hashes[i] = hash.universalHash(data[i], x + 1);
                }
                Arrays.sort(hashes);
                for (int i = 1; i < size; i++) {
                    if (hashes[i - 1] == hashes[i]) {
                        index++;
                        while (!Settings.needNewUniversalHashIndex(index)) {
                            index++;
                        }
                        continue outer;
                    }
                }
            }
            if (tryUnique(hashes, index)) {
                return index;
            }
            index++;
        }
    }

    private boolean trySplitEvenly(int[] hashes, long index) {
        int size = hashes.length;
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
            int limit = firstPart;
            for (int i = 0; i < size; i++) {
                int x = hashes[i];
                int h = Settings.supplementalHash(x, index, size);
                if (h < limit) {
                    if (--firstPart < 0) {
                        return false;
                    }
                } else {
                    if (--otherPart < 0) {
                        return false;
                    }
                }
            }
            return true;
        }
        int[] count = new int[split];
        Arrays.fill(count, firstPart);
        for (int i = 0; i < size; i++) {
            int x = hashes[i];
            int h = Settings.supplementalHash(x, index, split);
            if (--count[h] < 0) {
                return false;
            }
        }
        return true;
    }

    private void splitEvenly(T[] data, int[] hashes, long index, T[][] data2,
            int[][] hashes2) {
        int size = data.length;
        int split = data2.length;
        int firstPartSize = data2[0].length;
        int otherPartSize = data2[1].length;
        if (firstPartSize != otherPartSize) {
            int i0 = 0, i1 = 0;
            int limit = firstPartSize;
            for (int i = 0; i < size; i++) {
                T t = data[i];
                int h = hashes[i];
                int bucket = Settings.supplementalHash(h, index, size);
                if (bucket < limit) {
                    data2[0][i0] = t;
                    hashes2[0][i0] = h;
                    i0++;
                } else {
                    data2[1][i1] = t;
                    hashes2[1][i1] = h;
                    i1++;
                }
            }
            return;
        }
        int[] pos = new int[split];
        for (int i = 0; i < size; i++) {
            T t = data[i];
            int h = hashes[i];
            int bucket = Settings.supplementalHash(h, index, split);
            int p = pos[bucket]++;
            data2[bucket][p] = t;
            hashes2[bucket][p] = h;
        }
    }

    static <T> boolean tryUnique(int[] hashes, long index) {
        int bits = 0;
        int size = hashes.length;
        for (int i = 0; i < size; i++) {
            int x = hashes[i];
            int h = Settings.supplementalHash(x, index, size);
            if ((bits & (1 << h)) != 0) {
                return false;
            }
            bits |= 1 << h;
        }
        return true;
    }

    public void dispose() {
        processor.dispose();
    }

}
