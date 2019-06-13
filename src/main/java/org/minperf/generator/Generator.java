package org.minperf.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.RecursiveAction;

import org.minperf.BitBuffer;
import org.minperf.Settings;
import org.minperf.bdz.BDZ;
import org.minperf.monotoneList.MonotoneList;
import org.minperf.universal.UniversalHash;

/**
 * Generator of a hybrid MPHF. It is guaranteed to use linear space, because
 * large buckets are encoded using an alternative algorithm.
 *
 * @param <T> the type
 */
public class Generator<T> {

    public static final int MAX_FILL = 8;
    public static final int MAX_BITS_PER_ENTRY = 8;

    final ConcurrencyTool pool;
    final UniversalHash<T> hash;
    private final Settings settings;
    private final boolean eliasFanoMonotoneLists;
    private final int maxChunkSize;

    public Generator(ConcurrencyTool pool,
            UniversalHash<T> hash,
            Settings settings,
            boolean eliasFanoMonotoneLists,
            int maxChunkSize) {
        this.pool = pool;
        this.settings = settings;
        this.hash = hash;
        this.eliasFanoMonotoneLists = eliasFanoMonotoneLists;
        this.maxChunkSize = maxChunkSize;
    }

    @SuppressWarnings("unchecked")
    public void generate(T[] data, long[] hashes, long startIndex, BitBuffer buff) {
        int size = data.length;
        if (size < 2) {
            return;
        }
        if (size <= settings.getLeafSize()) {
            long index = getIndex(data, hashes, startIndex);
            int shift = settings.getGolombRiceShift(size);
            long value = index - startIndex - 1;
            buff.writeGolombRice(shift, value);
            return;
        }
        long index = startIndex + 1;
//        num_split_count++;
        while (true) {
            if (Settings.needNewUniversalHashIndex(index)) {
                long x = Settings.getUniversalHashIndex(index);
                for (int i = 0; i < size; i++) {
                    hashes[i] = hash.universalHash(data[i], x);
                }
            }
            if (trySplitEvenly(hashes, index)) {
                break;
            }
            index++;
        }
        int writeK = settings.getGolombRiceShift(size);
        long writeIndex = index - startIndex - 1;
        buff.writeGolombRice(writeK, writeIndex);
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
        long[][] hashes2;
        if (firstPart != otherPart) {
            data2 = (T[][]) new Object[][] { (T[]) new Object[firstPart],
                    (T[]) new Object[otherPart] };
            hashes2 = new long[][] { new long[firstPart], new long[otherPart] };
        } else {
            data2 = (T[][]) new Object[split][firstPart];
            hashes2 = new long[split][firstPart];
        }
        splitEvenly(data, hashes, index, data2, hashes2);
        for (int i = 0; i < data2.length; i++) {
            generate(data2[i], hashes2[i], index, buff);
        }
    }

    private long getIndex(T[] data, long[] hashes, long startIndex) {
        int size = data.length;
        long index = startIndex + 1;
//        num_bij_counts[size]++;
        outer: while (true) {
            if (Settings.needNewUniversalHashIndex(index)) {
                long x = Settings.getUniversalHashIndex(index);
                for (int i = 0; i < size; i++) {
                    hashes[i] = hash.universalHash(data[i], x);
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

    private boolean trySplitEvenly(long[] hashes, long index) {
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
                long h = hashes[i];
                int x = Settings.supplementalHash(h, index);
//                num_split_evals++;
                x = Settings.reduce(x, size);
                if (x < limit) {
                    firstPart--;
                }
            }
            return firstPart == 0;
        }
        int[] count = new int[split];
        Arrays.fill(count, firstPart);
        for (int i = 0; i < size; i++) {
            long h = hashes[i];
            int x = Settings.supplementalHash(h, index);
            x = Settings.reduce(x, split);
//            num_split_evals++;
            count[x]--;
        }
        for (int i = 0; i < split; i++) {
            if (count[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private void splitEvenly(T[] data, long[] hashes, long index, T[][] data2,
            long[][] hashes2) {
        int size = data.length;
        int split = data2.length;
        int firstPartSize = data2[0].length;
        int otherPartSize = data2[1].length;
        if (firstPartSize != otherPartSize) {
            int i0 = 0, i1 = 0;
            int limit = firstPartSize;
            for (int i = 0; i < size; i++) {
                T t = data[i];
                long h = hashes[i];
                int x = Settings.supplementalHash(h, index);
                x = Settings.reduce(x, size);
                if (x < limit) {
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
            long h = hashes[i];
            int x = Settings.supplementalHash(h, index);
            int bucket = Settings.reduce(x, split);
            int p = pos[bucket]++;
            data2[bucket][p] = t;
            hashes2[bucket][p] = h;
        }
    }
    
//    public static long num_split_count;
//    public static long num_split_evals;
//    public static long[] num_bij_evals = new long[20];
//    public static long[] num_bij_counts = new long[20];

    static <T> boolean tryUnique(long[] hashes, long index) {
        int bits = 0;
        int size = hashes.length;
        int found = (1 << size) - 1;
        for (int i = 0; i < size; i++) {
            long x = hashes[i];
            int h = Settings.supplementalHash(x, index);
            h = Settings.reduce(h, size);
//            ++num_bij_evals[size];            
//            if ((bits & (1 << h)) != 0) {
//                return false;
//            }
            bits |= 1 << h;
        }
        return bits == found;
//        return true;
    }

    public BitBuffer generate(Collection<T> collection) {
        long size = collection.size();
        int bucketCount = Settings.getBucketCount(size, settings.getAverageBucketSize());
        ArrayList<Bucket> buckets = new ArrayList<Bucket>(bucketCount);
        int averageBucketSize = settings.getAverageBucketSize();
        if (size <= maxChunkSize || bucketCount == 1) {
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(new Bucket(averageBucketSize));
            }
            for (T t : collection) {
                int b;
                if (bucketCount == 1) {
                    b = 0;
                } else {
                    long h = hash.universalHash(t, 0);
                    b = Settings.reduce((int) h, bucketCount);
                    if (b >= bucketCount || b < 0) {
                        throw new AssertionError();
                    }
                }
                buckets.get(b).add(t);
            }
            processBuckets(size, bucketCount, buckets);
        } else {
            // split into chunks
            int bucketsPerChunk = Math.max(1, maxChunkSize / averageBucketSize);
            int remaining = bucketCount;
            for (int bucketOffset = 0; bucketOffset < bucketCount; bucketOffset += bucketsPerChunk) {
                int chunkSize = Math.min(bucketsPerChunk, remaining);
                remaining -= chunkSize;
                ArrayList<Bucket> buckets2 = new ArrayList<Bucket>(chunkSize);
                for (int i = 0; i < chunkSize; i++) {
                    buckets2.add(new Bucket(averageBucketSize));
                }
                for (T t : collection) {
                    int b;
                    long h = hash.universalHash(t, 0);
                    b = Settings.reduce((int) h, bucketCount);
                    if (b >= bucketCount || b < 0) {
                        throw new AssertionError();
                    }
                    if (b >= bucketOffset && b < bucketOffset + bucketsPerChunk) {
                        buckets2.get(b - bucketOffset).add(t);
                    }
                }
                processBuckets(size, bucketCount, buckets2);
                for (Bucket b2 : buckets2) {
                    buckets.add(b2);
                }
                buckets2.clear();
            }
        }
        ArrayList<T> alternativeList = new ArrayList<T>();
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            // move all buckets first, so overlap is not affected
            b.moveToAlternative(alternativeList);
        }

        int[] startList = new int[buckets.size() + 1];
        int[] offsetList = new int[buckets.size() + 1];
        int start = 0, offset = 0;
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            if (start - offset < 0) {
                throw new AssertionError();
            }
            int pos = b.buff.position();
            // possible overlap
            if (i < buckets.size() - 1) {
                Bucket next = buckets.get(i + 1);
                int maxOverlap = Math.min(16, next.buff.position());
                // at least one bit per entry
                int minBitCount = getMinBitCount(b.entryCount);
                maxOverlap = Math.min(maxOverlap, b.buff.position() - minBitCount);
                int overlap = 0;
                for (; overlap < maxOverlap; overlap++) {
                    if (next.buff.readNumber(0, overlap + 1) !=
                            b.buff.readNumber(pos - overlap - 1, overlap + 1)) {
                        break;
                    }
                }
                pos -= overlap;
                b.buff.seek(pos);
            }
            start += pos;
            offset += b.entryCount;
            startList[i + 1] = start;
            offsetList[i + 1] = offset;
        }
        shrinkList(startList, offsetList);
        int minOffsetDiff = shrinkList(offsetList);
        int minStartDiff = shrinkList(startList);
        if (minStartDiff < 0) {
            throw new AssertionError();
        }

        BitBuffer alt = null;
        if (!alternativeList.isEmpty()) {
            alt = BDZ.generate(hash, alternativeList);
        }

        int bitCount = BitBuffer.getEliasDeltaSize(size + 1);
        bitCount += 1;
        bitCount += BitBuffer.getEliasDeltaSize(minOffsetDiff + 1);
        bitCount += BitBuffer.getEliasDeltaSize(minStartDiff + 1);
        bitCount += MonotoneList.getSize(offsetList, eliasFanoMonotoneLists);
        bitCount += MonotoneList.getSize(startList, eliasFanoMonotoneLists);
        bitCount += start;
        if (alt != null) {
            bitCount += alt.position();
        }

        BitBuffer all = new BitBuffer(bitCount);
        all.writeEliasDelta(size + 1);
        all.writeBit(alternativeList.isEmpty() ? 0 : 1);
        all.writeEliasDelta(minOffsetDiff + 1);
        all.writeEliasDelta(minStartDiff + 1);
        MonotoneList.generate(offsetList, all, eliasFanoMonotoneLists);
        MonotoneList.generate(startList, all, eliasFanoMonotoneLists);
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            all.write(b.buff);
        }
        if (alt != null) {
            all.write(alt);
        }
        if (bitCount != all.position()) {
            throw new AssertionError();
        }
        return all;
    }

    private void processBuckets(long size, int bucketCount, final ArrayList<Bucket> buckets) {

        int averageBucketSize = (int) (size / bucketCount);
        final int maxBucketSize = averageBucketSize * MAX_FILL;
        final int maxBits = maxBucketSize * MAX_BITS_PER_ENTRY;

        pool.invoke(new RecursiveAction() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void compute() {
                RecursiveAction[] list = new RecursiveAction[buckets.size()];
                for (int i = 0; i < buckets.size(); i++) {
                    final Bucket b = buckets.get(i);
                    list[i] = new RecursiveAction() {

                        private static final long serialVersionUID = 1L;

                        @Override
                        protected void compute() {
                            b.generateBucket(hash, maxBucketSize, maxBits);
                        }

                    };
                }
                pool.invokeAll(list);
            }
        });
    }

    public static void shrinkList(int[] targetList, int[] sourceList) {
        int sum = 0;
        for (int i = 1; i < sourceList.length; i++) {
            int d = sourceList[i] - sourceList[i - 1];
            sum += d;
            targetList[i] -= getMinBitCount(sum);
            if (targetList[i] < targetList[i - 1]) {
                throw new IllegalArgumentException("");
            }
        }
    }

    public static int shrinkList(int[] list) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < list.length - 1; i++) {
            int d = list[i + 1] - list[i];
            min = Math.min(min, d);
        }
        for (int i = 1; i < list.length; i++) {
            list[i] -= i * min;
        }
        return min;
    }

    public static int getMinBitCount(int size) {
        // at least 1.375 bits per key (if it is less, fill with zeroes)
        return (size  * 11 + 7) >> 3;
    }

    /**
     * A bucket.
     */
    class Bucket {
        ArrayList<T> list;
        BitBuffer buff;
        int entryCount;
        boolean alternative;

        Bucket(int averageBucketSize) {
            list = new ArrayList<T>(averageBucketSize * 11 / 10);
        }

        @Override
       public String toString() {
            return "" + entryCount;
        }

        public void moveToAlternative(ArrayList<T> alternativeList) {
            if (alternative) {
                alternativeList.addAll(list);
                list = null;
                entryCount = 0;
                buff = new BitBuffer(0);
            }
        }

        void add(T obj) {
            list.add(obj);
        }

        void generateBucket(UniversalHash<T> hash, int maxBucketSize, int maxBits) {
            int size = list.size();
            entryCount = size;
            int minSize = getMinBitCount(size);
            if (size <= 1) {
                // zero or one entry
                buff = new BitBuffer(minSize);
                while (buff.position() < minSize) {
                    buff.writeBit(1);
                }
                return;
            }
            if (size > maxBucketSize) {
                alternative = true;
                buff = new BitBuffer(0);
                return;
            }
            @SuppressWarnings("unchecked")
            T[] data = list.toArray((T[]) new Object[0]);
            list = null;
            long[] hashes = new long[size];
            long startIndex = 0;
            for (int i = 0; i < size; i++) {
                hashes[i] = hash.universalHash(data[i],
                        Settings.getUniversalHashIndex(startIndex));
            }
            // this is very conservative; less memory could be allocated
            int bufferSize = 8 * size;
            if (settings.getLeafSize() < 6) {
                bufferSize *= 4;
            }
            buff = new BitBuffer(bufferSize);
            generate(data, hashes, startIndex, buff);
            if (buff.position() < minSize) {
                while (buff.position() < minSize) {
                    buff.writeBit(1);
                }
            }
            if (buff.position() > maxBits) {
                alternative = true;
            }
        }

    }

}
