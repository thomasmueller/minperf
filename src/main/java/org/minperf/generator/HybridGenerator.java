package org.minperf.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
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
public class HybridGenerator<T> extends Generator<T> {

    public static final int MAX_FILL = 8;
    public static final int MAX_BITS_PER_ENTRY = 8;
    public static final int MAX_CHUNK_SIZE = 100_000_000;

    static final ForkJoinPool POOL = new ForkJoinPool(Generator.PARALLELISM);

    private final boolean eliasFanoMonotoneLists;

    public HybridGenerator(UniversalHash<T> hash, Settings settings, boolean eliasFanoMonotoneLists) {
        super(hash, settings);
        this.eliasFanoMonotoneLists = eliasFanoMonotoneLists;
    }

    @Override
    public BitBuffer generate(Collection<T> collection) {
        long size = collection.size();
        int bucketCount = (int) (size + (settings.getLoadFactor() - 1)) /
                settings.getLoadFactor();
        ArrayList<Bucket> buckets = new ArrayList<Bucket>(bucketCount);
        int loadFactor = settings.getLoadFactor();
        long bucketScaleFactor = Settings.scaleFactor(bucketCount);
        int bucketScaleShift = Settings.scaleShift(bucketCount);
        if (size <= MAX_CHUNK_SIZE || bucketCount == 1) {
            for (int i = 0; i < bucketCount; i++) {
                buckets.add(new Bucket(loadFactor));
            }
            for (T t : collection) {
                int b;
                if (bucketCount == 1) {
                    b = 0;
                } else {
                    long h = hash.universalHash(t, 0);
                    b = Settings.scaleLong(h, bucketScaleFactor, bucketScaleShift);
                    if (b >= bucketCount || b < 0) {
                        throw new AssertionError();
                    }
                }
                buckets.get(b).add(t);
            }
            generateParallel(size, bucketCount, buckets);
        } else {
            // split into chunks
            int bucketsPerChunk = Math.max(1, MAX_CHUNK_SIZE / loadFactor);
            for (int bucketOffset = 0; bucketOffset < bucketCount; bucketOffset += bucketsPerChunk) {
                ArrayList<Bucket> buckets2 = new ArrayList<Bucket>(bucketsPerChunk);
                for (int i = 0; i < bucketsPerChunk; i++) {
                    buckets2.add(new Bucket(loadFactor));
                }
                for (T t : collection) {
                    int b;
                    long h = hash.universalHash(t, 0);
                    b = Settings.scaleLong(h, bucketScaleFactor, bucketScaleShift);
                    if (b >= bucketCount || b < 0) {
                        throw new AssertionError();
                    }
                    if (b >= bucketOffset && b < bucketOffset + bucketsPerChunk) {
                        buckets2.get(b - bucketOffset).add(t);
                    }
                }
                generateParallel(size, bucketCount, buckets2);
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
        bitCount += MonotoneList.getSize(offsetList, eliasFanoMonotoneLists);
        bitCount += BitBuffer.getEliasDeltaSize(minStartDiff + 1);
        bitCount += MonotoneList.getSize(startList, eliasFanoMonotoneLists);
        bitCount += start;
        if (alt != null) {
            bitCount += alt.position();
        }

        BitBuffer all = new BitBuffer(bitCount);
        all.writeEliasDelta(size + 1);
        all.writeBit(alternativeList.isEmpty() ? 0 : 1);
        all.writeEliasDelta(minOffsetDiff + 1);
        MonotoneList.generate(offsetList, all, eliasFanoMonotoneLists);
        all.writeEliasDelta(minStartDiff + 1);
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

    private void generateParallel(long size, int bucketCount, final ArrayList<Bucket> buckets) {

        int averageBucketSize = (int) (size / bucketCount);
        final int maxBucketSize = averageBucketSize * MAX_FILL;
        final int maxBits = maxBucketSize * MAX_BITS_PER_ENTRY;

        ForkJoinPool pool = new ForkJoinPool();

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
                invokeAll(list);

            }
        });
        pool.shutdown();
    }
    void shrinkList(int[] targetList, int[] sourceList) {
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

    int shrinkList(int[] list) {
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
        // assume at least 1.375 bits per key
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

        Bucket(int loadFactor) {
            list = new ArrayList<T>((int) (loadFactor * 1.1));
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
            for (int i = 0; i < size; i++) {
                hashes[i] = hash.universalHash(data[i], 0);
            }
            Processor<T> p = new Processor<T>(
                    HybridGenerator.this, data, hashes, 0);
            generate(data, hashes, 0, p);
            buff = p.out;
            if (buff.position() < minSize) {
                buff = new BitBuffer(minSize);
                buff.write(p.out);
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
